package com.privacy.faraday.ui.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.privacy.faraday.crypto.SignalKeyManager
import com.privacy.faraday.network.MessageManager
import com.privacy.faraday.network.ReticulumManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntegrationTestViewModel(application: Application) : AndroidViewModel(application) {

    data class MessageEntry(
        val direction: String,
        val peerAddress: String,
        val plaintext: String,
        val ciphertextHex: String,
        val timestamp: String,
        val messageType: String
    )

    data class UiState(
        val reticulumRunning: Boolean = false,
        val isInitializing: Boolean = false,
        val lxmfAddress: String = "",
        val signalFingerprint: String = "",
        val peerAddress: String = "",
        val peerSessionState: String = "UNKNOWN",
        val messageInput: String = "",
        val messages: List<MessageEntry> = emptyList(),
        val logMessages: List<String> = emptyList(),
        val errorMessage: String? = null,
        val isProcessing: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var localKeys: SignalKeyManager.GeneratedKeys? = null
    private var messageManager: MessageManager? = null
    private var pollingJob: Job? = null

    fun initializeAll() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isInitializing = true, errorMessage = null) }

                // 1. Generate Signal keys
                addLog("Generating Signal keys...")
                val keys = SignalKeyManager.generateIdentity()
                localKeys = keys
                val fingerprint = SignalKeyManager.fingerprint(keys.identityKeyPair.publicKey)
                addLog("Signal keys generated. Fingerprint: ${fingerprint.take(16)}...")

                // 2. Initialize Reticulum
                addLog("Starting Reticulum...")
                val address = try {
                    ReticulumManager.initialize(getApplication())
                } catch (_: Exception) {
                    // Already initialized — get address instead
                    addLog("Reticulum already running, getting address...")
                    ReticulumManager.getAddress()
                }
                addLog("Reticulum running. LXMF: $address")

                // 3. Create MessageManager
                val mgr = MessageManager(keys)
                mgr.onLog = { msg -> addLog(msg) }
                mgr.onMessageDecrypted = { sender, plaintextBytes, cipherHex ->
                    val plaintext = String(plaintextBytes, Charsets.UTF_8)
                    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + MessageEntry(
                                direction = "IN",
                                peerAddress = sender,
                                plaintext = plaintext,
                                ciphertextHex = cipherHex,
                                timestamp = ts,
                                messageType = "ENCRYPTED"
                            )
                        )
                    }
                }
                mgr.onSessionEstablished = { peerAddr ->
                    _uiState.update { state ->
                        val currentPeer = state.peerAddress.replace(":", "").replace("<", "").replace(">", "").trim().lowercase()
                        if (peerAddr == currentPeer) {
                            state.copy(peerSessionState = "ESTABLISHED")
                        } else {
                            state
                        }
                    }
                }
                messageManager = mgr

                // 4. Announce
                addLog("Announcing on network...")
                ReticulumManager.announce()
                addLog("Announce sent")

                _uiState.update {
                    it.copy(
                        reticulumRunning = true,
                        isInitializing = false,
                        lxmfAddress = address,
                        signalFingerprint = fingerprint
                    )
                }

                // 5. Start polling
                startPolling()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        errorMessage = "Init failed: ${e.message}"
                    )
                }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun onPeerAddressChanged(value: String) {
        _uiState.update {
            val cleanAddr = value.replace(":", "").replace("<", "").replace(">", "").trim().lowercase()
            val state = messageManager?.getContactState(cleanAddr)?.name ?: "UNKNOWN"
            it.copy(peerAddress = value, peerSessionState = state)
        }
    }

    fun onMessageInputChanged(value: String) {
        _uiState.update { it.copy(messageInput = value) }
    }

    fun initiateKeyExchange() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                val mgr = messageManager ?: throw IllegalStateException("Not initialized")
                val peerAddr = _uiState.value.peerAddress
                mgr.initiateKeyExchange(peerAddr)
                _uiState.update {
                    val cleanAddr = peerAddr.replace(":", "").replace("<", "").replace(">", "").trim().lowercase()
                    val state = mgr.getContactState(cleanAddr).name
                    it.copy(isProcessing = false, peerSessionState = state)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "Key exchange failed: ${e.message}")
                }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun sendMessage() {
        viewModelScope.launch {
            try {
                val mgr = messageManager ?: throw IllegalStateException("Not initialized")
                val peerAddr = _uiState.value.peerAddress
                val plaintext = _uiState.value.messageInput.trim()
                if (plaintext.isBlank()) return@launch

                _uiState.update { it.copy(isProcessing = true) }
                mgr.sendEncryptedMessage(peerAddr, plaintext)

                val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        messageInput = "",
                        messages = state.messages + MessageEntry(
                            direction = "OUT",
                            peerAddress = peerAddr.replace(":", "").replace("<", "").replace(">", "").trim().lowercase(),
                            plaintext = plaintext,
                            ciphertextHex = "(encrypted)",
                            timestamp = ts,
                            messageType = "ENCRYPTED"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "Send failed: ${e.message}")
                }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun clearLog() {
        _uiState.update { it.copy(logMessages = emptyList()) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                try {
                    val messages = ReticulumManager.pollMessages()
                    val mgr = messageManager ?: continue
                    for (msg in messages) {
                        mgr.processIncomingMessage(msg.sourceHash, msg.content)
                        // Update peer session state if it changed
                        val currentPeer = _uiState.value.peerAddress
                            .replace(":", "").replace("<", "").replace(">", "").trim().lowercase()
                        if (currentPeer.isNotBlank()) {
                            val newState = mgr.getContactState(currentPeer).name
                            _uiState.update { it.copy(peerSessionState = newState) }
                        }
                    }
                } catch (_: Exception) {
                    // Polling errors are non-fatal
                }
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        _uiState.update { it.copy(logMessages = it.logMessages + "[$timestamp] $message") }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
