package com.privacy.faraday.crypto

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import org.signal.libsignal.protocol.util.KeyHelper

object SignalKeyManager {

    data class GeneratedKeys(
        val identityKeyPair: IdentityKeyPair,
        val registrationId: Int,
        val preKeys: List<PreKeyRecord>,
        val signedPreKey: SignedPreKeyRecord,
        val kyberPreKey: KyberPreKeyRecord,
        val store: InMemorySignalProtocolStore
    )

    fun generateIdentity(): GeneratedKeys {
        // 1. Identity key pair
        val identityKeyPair = IdentityKeyPair.generate()

        // 2. Registration ID
        val registrationId = KeyHelper.generateRegistrationId(false)

        // 3. Pre-keys (100)
        val preKeys = (1..100).map { id ->
            PreKeyRecord(id, ECKeyPair.generate())
        }

        // 4. Signed pre-key
        val signedPreKeyId = 1
        val signedPreKeyPair = ECKeyPair.generate()
        val signedPreKeySignature = identityKeyPair.privateKey
            .calculateSignature(signedPreKeyPair.publicKey.serialize())
        val signedPreKey = SignedPreKeyRecord(
            signedPreKeyId,
            System.currentTimeMillis(),
            signedPreKeyPair,
            signedPreKeySignature
        )

        // 5. Kyber pre-key (post-quantum, required by libsignal 0.86.5)
        val kyberPreKeyId = 1
        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = identityKeyPair.privateKey
            .calculateSignature(kyberKeyPair.publicKey.serialize())
        val kyberPreKey = KyberPreKeyRecord(
            kyberPreKeyId,
            System.currentTimeMillis(),
            kyberKeyPair,
            kyberSignature
        )

        // 6. Create store and populate
        val store = InMemorySignalProtocolStore(identityKeyPair, registrationId)
        preKeys.forEach { store.storePreKey(it.id, it) }
        store.storeSignedPreKey(signedPreKey.id, signedPreKey)
        store.storeKyberPreKey(kyberPreKey.id, kyberPreKey)

        return GeneratedKeys(
            identityKeyPair = identityKeyPair,
            registrationId = registrationId,
            preKeys = preKeys,
            signedPreKey = signedPreKey,
            kyberPreKey = kyberPreKey,
            store = store
        )
    }

    fun createPreKeyBundle(keys: GeneratedKeys): PreKeyBundle {
        val firstPreKey = keys.preKeys.first()
        return PreKeyBundle(
            keys.registrationId,
            1, // deviceId
            firstPreKey.id,
            firstPreKey.keyPair.publicKey,
            keys.signedPreKey.id,
            keys.signedPreKey.keyPair.publicKey,
            keys.signedPreKey.signature,
            keys.identityKeyPair.publicKey,
            keys.kyberPreKey.id,
            keys.kyberPreKey.keyPair.publicKey,
            keys.kyberPreKey.signature
        )
    }

    fun fingerprint(identityKey: IdentityKey): String {
        val bytes = identityKey.serialize()
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }
}
