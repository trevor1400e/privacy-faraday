package com.privacy.faraday.crypto

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MessageProtocol {

    const val TYPE_KEY_EXCHANGE: Byte = 0x01
    const val TYPE_ENCRYPTED_MESSAGE: Byte = 0x02

    data class KeyExchangeData(
        val registrationId: Int,
        val deviceId: Int,
        val identityKey: ByteArray,
        val signedPreKeyId: Int,
        val signedPreKey: ByteArray,
        val signedPreKeySignature: ByteArray,
        val preKeyId: Int,
        val preKey: ByteArray,
        val kyberPreKeyId: Int,
        val kyberPreKey: ByteArray,
        val kyberPreKeySignature: ByteArray
    )

    data class EncryptedMessageData(
        val signalMessageType: Int,
        val ciphertext: ByteArray
    )

    // --- Envelope ---

    fun wrapEnvelope(type: Byte, payload: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(1 + 4 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(type)
        buf.putInt(payload.size)
        buf.put(payload)
        return buf.array()
    }

    fun parseEnvelope(data: ByteArray): Pair<Byte, ByteArray> {
        require(data.size >= 5) { "Envelope too short: ${data.size} bytes" }
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val type = buf.get()
        val length = buf.getInt()
        require(data.size >= 5 + length) { "Payload truncated: expected $length, have ${data.size - 5}" }
        val payload = ByteArray(length)
        buf.get(payload)
        return Pair(type, payload)
    }

    // --- KEY_EXCHANGE serialization ---

    fun serializeKeyExchange(data: KeyExchangeData): ByteArray {
        val kyberKeyLen = data.kyberPreKey.size
        val kyberSigLen = data.kyberPreKeySignature.size
        val totalSize = 4 + 4 + 33 + 4 + 33 + 64 + 4 + 33 + 4 + 2 + kyberKeyLen + 2 + kyberSigLen
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(data.registrationId)
        buf.putInt(data.deviceId)
        buf.put(data.identityKey)
        buf.putInt(data.signedPreKeyId)
        buf.put(data.signedPreKey)
        buf.put(data.signedPreKeySignature)
        buf.putInt(data.preKeyId)
        buf.put(data.preKey)
        buf.putInt(data.kyberPreKeyId)
        buf.putShort(kyberKeyLen.toShort())
        buf.put(data.kyberPreKey)
        buf.putShort(kyberSigLen.toShort())
        buf.put(data.kyberPreKeySignature)
        return buf.array()
    }

    fun parseKeyExchange(payload: ByteArray): KeyExchangeData {
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val registrationId = buf.getInt()
        val deviceId = buf.getInt()
        val identityKey = ByteArray(33).also { buf.get(it) }
        val signedPreKeyId = buf.getInt()
        val signedPreKey = ByteArray(33).also { buf.get(it) }
        val signedPreKeySignature = ByteArray(64).also { buf.get(it) }
        val preKeyId = buf.getInt()
        val preKey = ByteArray(33).also { buf.get(it) }
        val kyberPreKeyId = buf.getInt()
        val kyberKeyLen = buf.getShort().toInt() and 0xFFFF
        val kyberPreKey = ByteArray(kyberKeyLen).also { buf.get(it) }
        val kyberSigLen = buf.getShort().toInt() and 0xFFFF
        val kyberPreKeySignature = ByteArray(kyberSigLen).also { buf.get(it) }
        return KeyExchangeData(
            registrationId, deviceId, identityKey, signedPreKeyId,
            signedPreKey, signedPreKeySignature, preKeyId, preKey,
            kyberPreKeyId, kyberPreKey, kyberPreKeySignature
        )
    }

    // --- ENCRYPTED_MESSAGE serialization ---

    fun serializeEncryptedMessage(data: EncryptedMessageData): ByteArray {
        val buf = ByteBuffer.allocate(1 + data.ciphertext.size)
        buf.put(data.signalMessageType.toByte())
        buf.put(data.ciphertext)
        return buf.array()
    }

    fun parseEncryptedMessage(payload: ByteArray): EncryptedMessageData {
        require(payload.size >= 2) { "Encrypted message too short" }
        val signalMessageType = payload[0].toInt() and 0xFF
        val ciphertext = payload.copyOfRange(1, payload.size)
        return EncryptedMessageData(signalMessageType, ciphertext)
    }

    // --- Convenience: GeneratedKeys → KeyExchangeData ---

    fun keyExchangeFromKeys(keys: SignalKeyManager.GeneratedKeys): KeyExchangeData {
        val firstPreKey = keys.preKeys.first()
        return KeyExchangeData(
            registrationId = keys.registrationId,
            deviceId = 1,
            identityKey = keys.identityKeyPair.publicKey.serialize(),
            signedPreKeyId = keys.signedPreKey.id,
            signedPreKey = keys.signedPreKey.keyPair.publicKey.serialize(),
            signedPreKeySignature = keys.signedPreKey.signature,
            preKeyId = firstPreKey.id,
            preKey = firstPreKey.keyPair.publicKey.serialize(),
            kyberPreKeyId = keys.kyberPreKey.id,
            kyberPreKey = keys.kyberPreKey.keyPair.publicKey.serialize(),
            kyberPreKeySignature = keys.kyberPreKey.signature
        )
    }

    // --- Convenience: KeyExchangeData → PreKeyBundle ---

    fun preKeyBundleFromKeyExchange(data: KeyExchangeData): PreKeyBundle {
        val identityKey = IdentityKey(data.identityKey)
        val signedPreKey = ECPublicKey(data.signedPreKey)
        val preKey = ECPublicKey(data.preKey)
        val kyberPreKey = KEMPublicKey(data.kyberPreKey)

        return PreKeyBundle(
            data.registrationId,
            data.deviceId,
            data.preKeyId,
            preKey,
            data.signedPreKeyId,
            signedPreKey,
            data.signedPreKeySignature,
            identityKey,
            data.kyberPreKeyId,
            kyberPreKey,
            data.kyberPreKeySignature
        )
    }
}
