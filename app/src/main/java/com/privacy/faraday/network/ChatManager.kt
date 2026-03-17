package com.privacy.faraday.network

import android.content.Context
import android.util.Log
import com.privacy.faraday.crypto.MessageProtocol
import com.privacy.faraday.crypto.SignalKeyManager
import org.signal.libsignal.protocol.SignalProtocolAddress
import com.privacy.faraday.data.db.AppDatabase
import com.privacy.faraday.data.db.ContactEntity
import com.privacy.faraday.data.db.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ChatManager {
    private const val TAG = "ChatManager"

    private lateinit var db: AppDatabase
    private var messageManager: MessageManager? = null
    private var localKeys: SignalKeyManager.GeneratedKeys? = null
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _lxmfAddress = MutableStateFlow("")
    val lxmfAddress: StateFlow<String> = _lxmfAddress.asStateFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    fun getDatabase(): AppDatabase = db

    fun initialize(context: Context) {
        db = AppDatabase.getInstance(context)

        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Generating Signal keys...")
                val keys = SignalKeyManager.generateIdentity()
                localKeys = keys

                Log.d(TAG, "Starting Reticulum...")
                val address = try {
                    ReticulumManager.initialize(context)
                } catch (_: Exception) {
                    ReticulumManager.getAddress()
                }
                _lxmfAddress.value = address
                Log.d(TAG, "Reticulum running. LXMF: $address")

                val mgr = MessageManager(keys)
                mgr.onLog = { msg -> Log.d(TAG, msg) }
                mgr.onMessageDecrypted = { senderAddress, plaintext, _ ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            handleIncomingMessage(senderAddress, plaintext)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to handle incoming message", e)
                        }
                    }
                }
                mgr.onKeyExchangeReceived = { senderAddress ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            val cleanSender = cleanAddress(senderAddress)
                            getOrCreateContact(cleanSender)
                            db.contactDao().updateSessionState(cleanSender, "KEY_EXCHANGE_RECEIVED")
                            Log.d(TAG, "Contact created for incoming key exchange from $cleanSender")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to create contact for key exchange", e)
                        }
                    }
                }
                mgr.onReceiptReceived = { senderAddress, receiptType ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            handleIncomingReceipt(senderAddress, receiptType)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to handle receipt", e)
                        }
                    }
                }
                mgr.onDisappearingSettingReceived = { senderAddress, durationMillis ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            handleIncomingDisappearingSetting(senderAddress, durationMillis)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to handle disappearing setting", e)
                        }
                    }
                }
                mgr.onSessionEstablished = { peerAddress ->
                    Log.d(TAG, "onSessionEstablished callback for: $peerAddress")
                    scope.launch(Dispatchers.IO) {
                        try {
                            db.contactDao().updateSessionState(peerAddress, "ESTABLISHED")
                            Log.d(TAG, "Room updated: ESTABLISHED for $peerAddress")
                            sendQueuedMessages(peerAddress)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update session state in Room", e)
                        }
                    }
                }
                messageManager = mgr

                ReticulumManager.announce()
                Log.d(TAG, "Announced on network")

                _initialized.value = true
                startPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
            }
        }
    }

    suspend fun sendMessage(peerAddress: String, plaintext: String) {
        val cleanAddr = cleanAddress(peerAddress)
        val mgr = messageManager ?: throw IllegalStateException("Not initialized")

        val state = mgr.getContactState(cleanAddr)
        if (state != MessageManager.ContactState.ESTABLISHED) {
            // Queue the message and initiate key exchange if needed
            db.messageDao().insert(
                MessageEntity(
                    conversationId = cleanAddr,
                    content = plaintext,
                    isOutgoing = true,
                    status = "QUEUED"
                )
            )
            if (state == MessageManager.ContactState.UNKNOWN) {
                try {
                    initiateKeyExchange(cleanAddr)
                } catch (e: Exception) {
                    Log.e(TAG, "Auto key exchange failed", e)
                }
            }
            return
        }

        val msgId = db.messageDao().insert(
            MessageEntity(
                conversationId = cleanAddr,
                content = plaintext,
                isOutgoing = true,
                status = "SENDING"
            )
        )

        try {
            mgr.sendEncryptedMessage(cleanAddr, plaintext)
            db.messageDao().updateStatus(msgId, "SENT")
        } catch (e: Exception) {
            db.messageDao().updateStatus(msgId, "FAILED")
            Log.e(TAG, "Send failed", e)
            throw e
        }
    }

    suspend fun acceptKeyExchange(peerAddress: String) {
        val cleanAddr = cleanAddress(peerAddress)
        val mgr = messageManager ?: throw IllegalStateException("Not initialized")
        mgr.acceptKeyExchange(cleanAddr)
    }

    suspend fun initiateKeyExchange(peerAddress: String) {
        val cleanAddr = cleanAddress(peerAddress)
        val mgr = messageManager ?: throw IllegalStateException("Not initialized")

        getOrCreateContact(cleanAddr)
        mgr.initiateKeyExchange(cleanAddr)
        // Sync actual state from MessageManager (might be UNKNOWN if send failed)
        val actualState = mgr.getContactState(cleanAddr).name
        db.contactDao().updateSessionState(cleanAddr, actualState)
        Log.d(TAG, "Key exchange initiated with $cleanAddr, state: $actualState")
    }

    suspend fun getOrCreateContact(address: String, displayName: String = ""): ContactEntity {
        val cleanAddr = cleanAddress(address)
        val existing = db.contactDao().getByAddress(cleanAddr)
        if (existing != null) return existing

        val contact = ContactEntity(
            lxmfAddress = cleanAddr,
            displayName = displayName.ifBlank { cleanAddr.take(12) + "..." }
        )
        db.contactDao().upsert(contact)
        return contact
    }

    fun getSafetyFingerprints(peerAddress: String): Pair<String, String?> {
        val keys = localKeys ?: return Pair("Not initialized", null)
        val localFp = SignalKeyManager.fingerprint(keys.identityKeyPair.publicKey)
        val cleanAddr = cleanAddress(peerAddress)
        val remoteIdentity = try {
            keys.store.getIdentity(SignalProtocolAddress(cleanAddr, 1))
        } catch (_: Exception) { null }
        val remoteFp = remoteIdentity?.let { SignalKeyManager.fingerprint(it) }
        return Pair(localFp, remoteFp)
    }

    fun getContactState(peerAddress: String): MessageManager.ContactState {
        return messageManager?.getContactState(cleanAddress(peerAddress))
            ?: MessageManager.ContactState.UNKNOWN
    }

    private suspend fun handleIncomingMessage(senderAddress: String, plaintext: String) {
        val cleanSender = cleanAddress(senderAddress)

        // Ensure contact exists
        getOrCreateContact(cleanSender)

        db.messageDao().insert(
            MessageEntity(
                conversationId = cleanSender,
                content = plaintext,
                isOutgoing = false,
                status = "SENT"
            )
        )

        // Send DELIVERED receipt back to sender
        try {
            messageManager?.sendReceipt(cleanSender, MessageProtocol.RECEIPT_DELIVERED)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send delivery receipt", e)
        }
    }

    private suspend fun handleIncomingReceipt(senderAddress: String, receiptType: Byte) {
        val cleanSender = cleanAddress(senderAddress)
        when (receiptType) {
            MessageProtocol.RECEIPT_DELIVERED -> {
                db.messageDao().updateOutgoingStatus(cleanSender, "SENT", "DELIVERED")
                Log.d(TAG, "Marked messages to $cleanSender as DELIVERED")
            }
            MessageProtocol.RECEIPT_READ -> {
                db.messageDao().updateOutgoingStatus(cleanSender, "SENT", "READ")
                db.messageDao().updateOutgoingStatus(cleanSender, "DELIVERED", "READ")
                Log.d(TAG, "Marked messages to $cleanSender as READ")
            }
        }
    }

    suspend fun sendReadReceipt(peerAddress: String) {
        val cleanAddr = cleanAddress(peerAddress)
        try {
            // Mark incoming messages as read for disappearing message timer
            db.messageDao().markIncomingAsRead(cleanAddr, System.currentTimeMillis())
            messageManager?.sendReceipt(cleanAddr, MessageProtocol.RECEIPT_READ)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send read receipt", e)
        }
    }

    suspend fun setDisappearingMessages(peerAddress: String, durationMillis: Long) {
        val cleanAddr = cleanAddress(peerAddress)
        // Update local DB
        db.contactDao().updateDisappearingDuration(cleanAddr, durationMillis)
        // Insert system message locally
        val label = formatDurationLabel(durationMillis)
        db.messageDao().insert(
            MessageEntity(
                conversationId = cleanAddr,
                content = "Disappearing messages set to $label",
                isOutgoing = true,
                isSystem = true,
                status = "SENT"
            )
        )
        // Send setting to peer
        try {
            messageManager?.sendDisappearingSetting(cleanAddr, durationMillis)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send disappearing setting", e)
        }
    }

    private suspend fun handleIncomingDisappearingSetting(senderAddress: String, durationMillis: Long) {
        val cleanSender = cleanAddress(senderAddress)
        // Update local DB
        db.contactDao().updateDisappearingDuration(cleanSender, durationMillis)
        // Insert system message
        val label = formatDurationLabel(durationMillis)
        db.messageDao().insert(
            MessageEntity(
                conversationId = cleanSender,
                content = "Disappearing messages set to $label",
                isOutgoing = false,
                isSystem = true,
                status = "SENT"
            )
        )
        Log.d(TAG, "Disappearing messages for $cleanSender set to $durationMillis ms")
    }

    private fun formatDurationLabel(millis: Long): String = when (millis) {
        0L -> "off"
        5 * 60 * 1000L -> "5 minutes"
        60 * 60 * 1000L -> "1 hour"
        24 * 60 * 60 * 1000L -> "1 day"
        7 * 24 * 60 * 60 * 1000L -> "1 week"
        else -> "${millis / 1000} seconds"
    }

    private suspend fun sendQueuedMessages(peerAddress: String) {
        val cleanAddr = cleanAddress(peerAddress)
        val mgr = messageManager ?: return
        val queued = db.messageDao().getQueuedMessages(cleanAddr)
        Log.d(TAG, "Sending ${queued.size} queued messages to $cleanAddr")
        for (msg in queued) {
            try {
                db.messageDao().updateStatus(msg.id, "SENDING")
                mgr.sendEncryptedMessage(cleanAddr, msg.content)
                db.messageDao().updateStatus(msg.id, "SENT")
            } catch (e: Exception) {
                db.messageDao().updateStatus(msg.id, "FAILED")
                Log.e(TAG, "Failed to send queued message ${msg.id}", e)
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            var pollCount = 0
            while (true) {
                delay(2000)
                pollCount++
                try {
                    val messages = ReticulumManager.pollMessages()
                    val mgr = messageManager ?: continue

                    // Process each message individually so one failure doesn't block others
                    for (msg in messages) {
                        try {
                            Log.d(TAG, "Processing message from ${msg.sourceHash}")
                            mgr.processIncomingMessage(msg.sourceHash, msg.content)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process message from ${msg.sourceHash}", e)
                        }

                        // Always sync state after each message, even if processing failed
                        try {
                            val cleanSender = cleanAddress(msg.sourceHash)
                            val contact = db.contactDao().getByAddress(cleanSender)
                            if (contact != null) {
                                val newState = mgr.getContactState(cleanSender).name
                                if (contact.sessionState != newState) {
                                    db.contactDao().updateSessionState(cleanSender, newState)
                                    Log.d(TAG, "Synced state for $cleanSender: ${contact.sessionState} -> $newState")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to sync contact state", e)
                        }
                    }

                    // Periodic sync: check ALL contacts against MessageManager state
                    // This catches updates from callbacks that may have raced or failed
                    syncAllContactStates(mgr)

                    // Clean up expired disappearing messages every ~30 seconds
                    if (pollCount % 15 == 0) {
                        cleanupExpiredMessages()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Polling error", e)
                }
            }
        }
    }

    private suspend fun cleanupExpiredMessages() {
        try {
            val contacts = db.contactDao().getAllOnce()
            val now = System.currentTimeMillis()
            for (contact in contacts) {
                if (contact.disappearingMessagesDuration > 0) {
                    val cutoff = now - contact.disappearingMessagesDuration
                    // Outgoing: timer starts from send time (timestamp)
                    db.messageDao().deleteExpiredOutgoing(contact.lxmfAddress, cutoff)
                    // Incoming: timer starts from read time (readAt)
                    db.messageDao().deleteExpiredIncoming(contact.lxmfAddress, cutoff)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup expired messages", e)
        }
    }

    private suspend fun syncAllContactStates(mgr: MessageManager) {
        try {
            val contacts = db.contactDao().getAllOnce()
            for (contact in contacts) {
                val mgrState = mgr.getContactState(contact.lxmfAddress).name
                if (contact.sessionState != mgrState && mgrState != "UNKNOWN") {
                    db.contactDao().updateSessionState(contact.lxmfAddress, mgrState)
                    Log.d(TAG, "Periodic sync: ${contact.lxmfAddress} ${contact.sessionState} -> $mgrState")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed periodic state sync", e)
        }
    }

    private fun cleanAddress(hex: String): String =
        hex.replace(":", "").replace("<", "").replace(">", "").trim().lowercase()
}
