package com.privacy.faraday.network

import android.content.Context
import android.util.Log
import com.privacy.faraday.crypto.SignalKeyManager
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
                mgr.onSessionEstablished = { peerAddress ->
                    Log.d(TAG, "onSessionEstablished callback for: $peerAddress")
                    scope.launch(Dispatchers.IO) {
                        try {
                            db.contactDao().updateSessionState(peerAddress, "ESTABLISHED")
                            Log.d(TAG, "Room updated: ESTABLISHED for $peerAddress")
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
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(2000)
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

                } catch (e: Exception) {
                    Log.e(TAG, "Polling error", e)
                }
            }
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
