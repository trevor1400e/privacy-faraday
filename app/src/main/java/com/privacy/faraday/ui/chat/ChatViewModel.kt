package com.privacy.faraday.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.privacy.faraday.data.db.ContactEntity
import com.privacy.faraday.data.db.MessageEntity
import com.privacy.faraday.network.ChatManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val conversationId: String = savedStateHandle["address"] ?: ""

    val messages: Flow<List<MessageEntity>> =
        ChatManager.getDatabase().messageDao().getMessagesForConversation(conversationId)

    private val _contact = MutableStateFlow<ContactEntity?>(null)
    val contact: StateFlow<ContactEntity?> = _contact.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    // Dialog visibility states
    private val _showDisappearingDialog = MutableStateFlow(false)
    val showDisappearingDialog: StateFlow<Boolean> = _showDisappearingDialog.asStateFlow()

    private val _showNicknameDialog = MutableStateFlow(false)
    val showNicknameDialog: StateFlow<Boolean> = _showNicknameDialog.asStateFlow()

    private val _showSoundDialog = MutableStateFlow(false)
    val showSoundDialog: StateFlow<Boolean> = _showSoundDialog.asStateFlow()

    private val _showSafetyNumberDialog = MutableStateFlow(false)
    val showSafetyNumberDialog: StateFlow<Boolean> = _showSafetyNumberDialog.asStateFlow()

    private val _safetyFingerprints = MutableStateFlow<Pair<String, String?>?>(null)
    val safetyFingerprints: StateFlow<Pair<String, String?>?> = _safetyFingerprints.asStateFlow()

    init {
        viewModelScope.launch {
            loadContact()
        }
        viewModelScope.launch {
            ChatManager.getDatabase().contactDao().getAll().collect { contacts ->
                _contact.value = contacts.find { it.lxmfAddress == conversationId }
            }
        }
        viewModelScope.launch {
            ChatManager.sendReadReceipt(conversationId)
        }
    }

    fun onMessageTextChanged(text: String) {
        _messageText.value = text
    }

    fun sendMessage() {
        val text = _messageText.value.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                _isSending.value = true
                ChatManager.sendMessage(conversationId, text)
                _messageText.value = ""
            } catch (_: Exception) {
            } finally {
                _isSending.value = false
            }
        }
    }

    fun acceptKeyExchange() {
        viewModelScope.launch {
            try {
                ChatManager.acceptKeyExchange(conversationId)
                loadContact()
            } catch (_: Exception) {
            }
        }
    }

    // --- Menu actions ---

    fun openDisappearingMessages() {
        _showDisappearingDialog.value = true
    }

    fun setDisappearingDuration(duration: Long) {
        viewModelScope.launch {
            ChatManager.setDisappearingMessages(conversationId, duration)
            loadContact()
            _showDisappearingDialog.value = false
        }
    }

    fun openNicknameEditor() {
        _showNicknameDialog.value = true
    }

    fun saveNickname(nickname: String) {
        viewModelScope.launch {
            ChatManager.getDatabase().contactDao().updateNickname(conversationId, nickname)
            loadContact()
            _showNicknameDialog.value = false
        }
    }

    fun openSoundSettings() {
        _showSoundDialog.value = true
    }

    fun toggleMuted() {
        viewModelScope.launch {
            val current = _contact.value?.isMuted ?: false
            ChatManager.getDatabase().contactDao().updateMuted(conversationId, !current)
            loadContact()
        }
    }

    fun openSafetyNumber() {
        _safetyFingerprints.value = ChatManager.getSafetyFingerprints(conversationId)
        _showSafetyNumberDialog.value = true
    }

    fun dismissDialog() {
        _showDisappearingDialog.value = false
        _showNicknameDialog.value = false
        _showSoundDialog.value = false
        _showSafetyNumberDialog.value = false
    }

    private suspend fun loadContact() {
        _contact.value = ChatManager.getDatabase().contactDao().getByAddress(conversationId)
    }
}
