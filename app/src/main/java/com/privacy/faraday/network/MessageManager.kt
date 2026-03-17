package com.privacy.faraday.network

import com.privacy.faraday.crypto.MessageProtocol
import com.privacy.faraday.crypto.SignalKeyManager
import com.privacy.faraday.crypto.SignalSessionManager
import org.signal.libsignal.protocol.SignalProtocolAddress
import java.util.Base64

class MessageManager(
    private val localKeys: SignalKeyManager.GeneratedKeys
) {

    enum class ContactState {
        UNKNOWN,
        KEY_EXCHANGE_SENT,
        KEY_EXCHANGE_RECEIVED,
        ESTABLISHED
    }

    data class ContactSession(
        val lxmfAddress: String,
        val signalAddress: SignalProtocolAddress,
        var state: ContactState = ContactState.UNKNOWN
    )

    private val contacts = mutableMapOf<String, ContactSession>()

    var onMessageDecrypted: ((senderAddress: String, plaintextBytes: ByteArray, ciphertextHex: String) -> Unit)? = null
    var onSessionEstablished: ((peerAddress: String) -> Unit)? = null
    var onReceiptReceived: ((senderAddress: String, receiptType: Byte) -> Unit)? = null
    var onKeyExchangeReceived: ((senderAddress: String) -> Unit)? = null
    var onDisappearingSettingReceived: ((senderAddress: String, durationMillis: Long) -> Unit)? = null
    var onLog: ((message: String) -> Unit)? = null

    suspend fun initiateKeyExchange(peerLxmfAddress: String) {
        val cleanAddr = cleanAddress(peerLxmfAddress)
        onLog?.invoke("Initiating key exchange with $cleanAddr")

        // Announce first so the peer can resolve our identity for the reply
        try {
            ReticulumManager.announce()
        } catch (_: Exception) { /* best effort */ }

        val keyExchangeData = MessageProtocol.keyExchangeFromKeys(localKeys)
        val payload = MessageProtocol.serializeKeyExchange(keyExchangeData)
        val envelope = MessageProtocol.wrapEnvelope(MessageProtocol.TYPE_KEY_EXCHANGE, payload)
        val base64 = Base64.getEncoder().encodeToString(envelope)

        val result = ReticulumManager.sendMessage(cleanAddr, base64.toByteArray(Charsets.UTF_8))
        val status = result["status"] ?: "unknown"
        onLog?.invoke("Key exchange send result: $status")

        if (status == "ok" || status == "sent") {
            val contact = getOrCreateContact(cleanAddr)
            if (contact.state == ContactState.UNKNOWN) {
                contact.state = ContactState.KEY_EXCHANGE_SENT
            }
        }
    }

    suspend fun sendEncryptedMessage(peerLxmfAddress: String, plaintext: String) {
        sendEncryptedBytes(peerLxmfAddress, plaintext.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendEncryptedBytes(peerLxmfAddress: String, plaintextBytes: ByteArray) {
        val cleanAddr = cleanAddress(peerLxmfAddress)
        val contact = contacts[cleanAddr]
            ?: throw IllegalStateException("No session for $cleanAddr")
        if (contact.state != ContactState.ESTABLISHED) {
            throw IllegalStateException("Session not established (state: ${contact.state})")
        }

        val ciphertext = SignalSessionManager.encrypt(
            localKeys.store, contact.signalAddress, plaintextBytes
        )
        val cipherBytes = ciphertext.serialize()

        val msgData = MessageProtocol.EncryptedMessageData(
            signalMessageType = ciphertext.type,
            ciphertext = cipherBytes
        )
        val payload = MessageProtocol.serializeEncryptedMessage(msgData)
        val envelope = MessageProtocol.wrapEnvelope(MessageProtocol.TYPE_ENCRYPTED_MESSAGE, payload)
        val base64 = Base64.getEncoder().encodeToString(envelope)

        val cipherHex = cipherBytes.take(16).joinToString("") { "%02x".format(it) } + "..."
        onLog?.invoke("Encrypted message (${cipherBytes.size} bytes): $cipherHex")

        val result = ReticulumManager.sendMessage(cleanAddr, base64.toByteArray(Charsets.UTF_8))
        val status = result["status"] ?: "unknown"
        onLog?.invoke("Send result: $status")
    }

    suspend fun processIncomingMessage(senderAddress: String, contentString: String) {
        val cleanSender = cleanAddress(senderAddress)

        val envelopeBytes: ByteArray
        try {
            envelopeBytes = Base64.getDecoder().decode(contentString)
        } catch (_: IllegalArgumentException) {
            // Not a protocol message (e.g., plain text from Phase 2 test screen)
            return
        }

        val (type, payload) = try {
            MessageProtocol.parseEnvelope(envelopeBytes)
        } catch (_: Exception) {
            return
        }

        when (type) {
            MessageProtocol.TYPE_KEY_EXCHANGE -> handleKeyExchange(cleanSender, payload)
            MessageProtocol.TYPE_ENCRYPTED_MESSAGE -> handleEncryptedMessage(cleanSender, payload)
            MessageProtocol.TYPE_RECEIPT -> handleReceipt(cleanSender, payload)
            MessageProtocol.TYPE_DISAPPEARING_SETTING -> handleDisappearingSetting(cleanSender, payload)
            else -> onLog?.invoke("Unknown message type: $type from $cleanSender")
        }
    }

    fun getContactState(peerAddress: String): ContactState {
        return contacts[cleanAddress(peerAddress)]?.state ?: ContactState.UNKNOWN
    }

    private suspend fun handleKeyExchange(senderAddress: String, payload: ByteArray) {
        onLog?.invoke("Received KEY_EXCHANGE from $senderAddress")

        val keyExchangeData = MessageProtocol.parseKeyExchange(payload)
        val preKeyBundle = MessageProtocol.preKeyBundleFromKeyExchange(keyExchangeData)
        val contact = getOrCreateContact(senderAddress)

        SignalSessionManager.buildSession(localKeys.store, contact.signalAddress, preKeyBundle)
        onLog?.invoke("Signal session built with $senderAddress")

        when (contact.state) {
            ContactState.UNKNOWN -> {
                contact.state = ContactState.KEY_EXCHANGE_RECEIVED
                onLog?.invoke("Key exchange received from $senderAddress — waiting for user to accept")
                onKeyExchangeReceived?.invoke(senderAddress)
            }
            ContactState.KEY_EXCHANGE_SENT -> {
                contact.state = ContactState.ESTABLISHED
                onSessionEstablished?.invoke(senderAddress)
                onLog?.invoke("Session ESTABLISHED with $senderAddress (mutual)")
            }
            ContactState.KEY_EXCHANGE_RECEIVED, ContactState.ESTABLISHED -> {
                // Already have session, this is a re-exchange — update session
                onLog?.invoke("Session refreshed with $senderAddress")
                if (contact.state != ContactState.ESTABLISHED) {
                    contact.state = ContactState.ESTABLISHED
                    onSessionEstablished?.invoke(senderAddress)
                }
            }
        }
    }

    private fun handleEncryptedMessage(senderAddress: String, payload: ByteArray) {
        val msgData = MessageProtocol.parseEncryptedMessage(payload)
        val contact = contacts[senderAddress]
        if (contact == null) {
            onLog?.invoke("Received encrypted message from unknown contact: $senderAddress")
            return
        }

        val cipherHex = msgData.ciphertext.take(16).joinToString("") { "%02x".format(it) } + "..."
        onLog?.invoke("Received encrypted msg (${msgData.ciphertext.size} bytes): $cipherHex")

        val decryptedBytes = SignalSessionManager.decrypt(
            localKeys.store,
            contact.signalAddress,
            msgData.ciphertext,
            msgData.signalMessageType
        )
        onLog?.invoke("Decrypted ${decryptedBytes.size} bytes")

        // Transition to ESTABLISHED if we were in an intermediate state
        if (contact.state != ContactState.ESTABLISHED) {
            contact.state = ContactState.ESTABLISHED
            onSessionEstablished?.invoke(senderAddress)
        }

        onMessageDecrypted?.invoke(senderAddress, decryptedBytes, cipherHex)
    }

    suspend fun acceptKeyExchange(peerLxmfAddress: String) {
        val cleanAddr = cleanAddress(peerLxmfAddress)
        val contact = contacts[cleanAddr]
            ?: throw IllegalStateException("No contact for $cleanAddr")

        onLog?.invoke("Accepting key exchange from $cleanAddr")

        // Announce ourselves first so the peer can resolve our identity
        try {
            ReticulumManager.announce()
            onLog?.invoke("Announced before replying")
        } catch (_: Exception) { /* best effort */ }

        // Send our key exchange back
        val ourData = MessageProtocol.keyExchangeFromKeys(localKeys)
        val ourPayload = MessageProtocol.serializeKeyExchange(ourData)
        val envelope = MessageProtocol.wrapEnvelope(MessageProtocol.TYPE_KEY_EXCHANGE, ourPayload)
        val base64 = Base64.getEncoder().encodeToString(envelope)
        val result = ReticulumManager.sendMessage(cleanAddr, base64.toByteArray(Charsets.UTF_8))
        val status = result["status"] ?: "unknown"
        onLog?.invoke("Accept key exchange send result: $status")

        contact.state = ContactState.ESTABLISHED
        onSessionEstablished?.invoke(cleanAddr)
        onLog?.invoke("Session ESTABLISHED with $cleanAddr")
    }

    suspend fun sendReceipt(peerLxmfAddress: String, receiptType: Byte) {
        val cleanAddr = cleanAddress(peerLxmfAddress)
        val payload = MessageProtocol.serializeReceipt(receiptType)
        val envelope = MessageProtocol.wrapEnvelope(MessageProtocol.TYPE_RECEIPT, payload)
        val base64 = Base64.getEncoder().encodeToString(envelope)
        val result = ReticulumManager.sendMessage(cleanAddr, base64.toByteArray(Charsets.UTF_8))
        val status = result["status"] ?: "unknown"
        val typeName = if (receiptType == MessageProtocol.RECEIPT_DELIVERED) "DELIVERED" else "READ"
        onLog?.invoke("Sent $typeName receipt to $cleanAddr: $status")
    }

    suspend fun sendDisappearingSetting(peerLxmfAddress: String, durationMillis: Long) {
        val cleanAddr = cleanAddress(peerLxmfAddress)
        val payload = MessageProtocol.serializeDisappearingSetting(durationMillis)
        val envelope = MessageProtocol.wrapEnvelope(MessageProtocol.TYPE_DISAPPEARING_SETTING, payload)
        val base64 = Base64.getEncoder().encodeToString(envelope)
        val result = ReticulumManager.sendMessage(cleanAddr, base64.toByteArray(Charsets.UTF_8))
        val status = result["status"] ?: "unknown"
        onLog?.invoke("Sent disappearing setting ($durationMillis ms) to $cleanAddr: $status")
    }

    private fun handleDisappearingSetting(senderAddress: String, payload: ByteArray) {
        val duration = MessageProtocol.parseDisappearingSetting(payload)
        onLog?.invoke("Received disappearing setting from $senderAddress: $duration ms")
        onDisappearingSettingReceived?.invoke(senderAddress, duration)
    }

    private fun handleReceipt(senderAddress: String, payload: ByteArray) {
        val receiptType = MessageProtocol.parseReceipt(payload)
        val typeName = if (receiptType == MessageProtocol.RECEIPT_DELIVERED) "DELIVERED" else "READ"
        onLog?.invoke("Received $typeName receipt from $senderAddress")
        onReceiptReceived?.invoke(senderAddress, receiptType)
    }

    private fun getOrCreateContact(address: String): ContactSession {
        return contacts.getOrPut(address) {
            ContactSession(
                lxmfAddress = address,
                signalAddress = SignalProtocolAddress(address, 1)
            )
        }
    }

    private fun cleanAddress(hex: String): String =
        hex.replace(":", "").replace("<", "").replace(">", "").trim().lowercase()
}
