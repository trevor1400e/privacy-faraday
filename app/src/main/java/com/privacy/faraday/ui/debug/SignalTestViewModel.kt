package com.privacy.faraday.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacy.faraday.crypto.SignalKeyManager
import com.privacy.faraday.crypto.SignalSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SignalTestViewModel : ViewModel() {

    data class UiState(
        val aliceFingerprint: String = "",
        val bobFingerprint: String = "",
        val keysGenerated: Boolean = false,
        val sessionEstablished: Boolean = false,
        val plaintext: String = "Hello from Alice!",
        val encryptedHex: String = "",
        val decryptedText: String = "",
        val match: Boolean? = null,
        val bidirectionalResult: String = "",
        val messageCount: Int = 0,
        val logMessages: List<String> = emptyList(),
        val errorMessage: String? = null,
        val isProcessing: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var aliceKeys: SignalKeyManager.GeneratedKeys? = null
    private var bobKeys: SignalKeyManager.GeneratedKeys? = null

    private val aliceAddress = SignalProtocolAddress("alice", 1)
    private val bobAddress = SignalProtocolAddress("bob", 1)

    fun generateKeys() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

                val alice = SignalKeyManager.generateIdentity()
                val bob = SignalKeyManager.generateIdentity()
                aliceKeys = alice
                bobKeys = bob

                val aliceFp = SignalKeyManager.fingerprint(alice.identityKeyPair.publicKey)
                val bobFp = SignalKeyManager.fingerprint(bob.identityKeyPair.publicKey)

                _uiState.update {
                    it.copy(
                        keysGenerated = true,
                        aliceFingerprint = aliceFp,
                        bobFingerprint = bobFp,
                        sessionEstablished = false,
                        encryptedHex = "",
                        decryptedText = "",
                        match = null,
                        bidirectionalResult = "",
                        messageCount = 0,
                        isProcessing = false
                    )
                }
                addLog("Keys generated - Alice: ${aliceFp.take(16)}... Bob: ${bobFp.take(16)}...")
                addLog("Alice has ${alice.preKeys.size} pre-keys, 1 signed pre-key, 1 Kyber pre-key")
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Key generation failed: ${e.message}", isProcessing = false) }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun establishSession() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

                val bobBundle = SignalKeyManager.createPreKeyBundle(bobKeys!!)
                SignalSessionManager.buildSession(aliceKeys!!.store, bobAddress, bobBundle)

                _uiState.update { it.copy(sessionEstablished = true, isProcessing = false) }
                addLog("Session established: Alice -> Bob")
                addLog("Alice has session for Bob: ${SignalSessionManager.hasSession(aliceKeys!!.store, bobAddress)}")
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Session failed: ${e.message}", isProcessing = false) }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun onPlaintextChanged(text: String) {
        _uiState.update { it.copy(plaintext = text) }
    }

    fun encryptAndDecrypt() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

                val plaintext = _uiState.value.plaintext
                val plainBytes = plaintext.toByteArray(Charsets.UTF_8)

                // Alice encrypts to Bob
                val ciphertext = SignalSessionManager.encrypt(
                    aliceKeys!!.store, bobAddress, plainBytes
                )
                val cipherBytes = ciphertext.serialize()
                val cipherHex = cipherBytes.joinToString("") { "%02x".format(it) }
                val msgType = if (ciphertext.type == CiphertextMessage.PREKEY_TYPE) "PreKey" else "Signal"
                addLog("Alice encrypted: $msgType message, ${cipherBytes.size} bytes")

                // Bob decrypts from Alice
                val decryptedBytes = SignalSessionManager.decrypt(
                    bobKeys!!.store, aliceAddress, cipherBytes, ciphertext.type
                )
                val decryptedText = String(decryptedBytes, Charsets.UTF_8)
                val isMatch = decryptedText == plaintext
                val count = _uiState.value.messageCount + 1

                addLog("Bob decrypted: \"$decryptedText\" (match: $isMatch)")
                addLog("Messages exchanged: $count")

                _uiState.update {
                    it.copy(
                        encryptedHex = if (cipherHex.length > 64) cipherHex.take(64) + "..." else cipherHex,
                        decryptedText = decryptedText,
                        match = isMatch,
                        messageCount = count,
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Encrypt/decrypt failed: ${e.message}", isProcessing = false) }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun testBidirectional() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

                // Bob encrypts reply to Alice
                val replyText = "Hello back from Bob!"
                val replyBytes = replyText.toByteArray(Charsets.UTF_8)

                val bobHasSession = SignalSessionManager.hasSession(bobKeys!!.store, aliceAddress)
                addLog("Bob has session for Alice: $bobHasSession")

                val ciphertext = SignalSessionManager.encrypt(
                    bobKeys!!.store, aliceAddress, replyBytes
                )
                val cipherBytes = ciphertext.serialize()
                val msgType = if (ciphertext.type == CiphertextMessage.PREKEY_TYPE) "PreKey" else "Signal"
                addLog("Bob encrypted: $msgType message, ${cipherBytes.size} bytes")

                // Alice decrypts Bob's reply
                val decryptedBytes = SignalSessionManager.decrypt(
                    aliceKeys!!.store, bobAddress, cipherBytes, ciphertext.type
                )
                val decryptedText = String(decryptedBytes, Charsets.UTF_8)
                val isMatch = decryptedText == replyText

                addLog("Alice decrypted Bob's reply: \"$decryptedText\" (match: $isMatch)")

                _uiState.update {
                    it.copy(
                        bidirectionalResult = if (isMatch) "Bob -> Alice: \"$decryptedText\" (MATCH)" else "MISMATCH: \"$decryptedText\"",
                        messageCount = it.messageCount + 1,
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Bidirectional test failed: ${e.message}", isProcessing = false) }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearLog() {
        _uiState.update { it.copy(logMessages = emptyList()) }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        _uiState.update { it.copy(logMessages = it.logMessages + "[$timestamp] $message") }
    }
}
