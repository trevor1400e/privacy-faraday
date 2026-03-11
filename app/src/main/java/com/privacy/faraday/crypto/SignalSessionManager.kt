package com.privacy.faraday.crypto

import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignalProtocolStore

object SignalSessionManager {

    fun buildSession(
        store: SignalProtocolStore,
        remoteAddress: SignalProtocolAddress,
        preKeyBundle: PreKeyBundle
    ) {
        val builder = SessionBuilder(store, remoteAddress)
        builder.process(preKeyBundle)
    }

    fun encrypt(
        store: SignalProtocolStore,
        remoteAddress: SignalProtocolAddress,
        plaintext: ByteArray
    ): CiphertextMessage {
        val cipher = SessionCipher(store, remoteAddress)
        return cipher.encrypt(plaintext)
    }

    fun decrypt(
        store: SignalProtocolStore,
        remoteAddress: SignalProtocolAddress,
        serializedMessage: ByteArray,
        messageType: Int
    ): ByteArray {
        val cipher = SessionCipher(store, remoteAddress)
        return when (messageType) {
            CiphertextMessage.PREKEY_TYPE ->
                cipher.decrypt(PreKeySignalMessage(serializedMessage))
            CiphertextMessage.WHISPER_TYPE ->
                cipher.decrypt(SignalMessage(serializedMessage))
            else -> throw IllegalArgumentException("Unknown message type: $messageType")
        }
    }

    fun hasSession(
        store: SignalProtocolStore,
        remoteAddress: SignalProtocolAddress
    ): Boolean {
        return store.containsSession(remoteAddress)
    }
}
