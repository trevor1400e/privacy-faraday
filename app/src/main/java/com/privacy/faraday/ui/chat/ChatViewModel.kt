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
import kotlinx.coroutines.flow.update
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

    init {
        viewModelScope.launch {
            loadContact()
        }
        // Poll contact state periodically for key exchange updates
        viewModelScope.launch {
            ChatManager.getDatabase().contactDao().getAll().collect { contacts ->
                _contact.value = contacts.find { it.lxmfAddress == conversationId }
            }
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
                // Error is stored in message status
            } finally {
                _isSending.value = false
            }
        }
    }

    fun initiateKeyExchange() {
        viewModelScope.launch {
            try {
                ChatManager.initiateKeyExchange(conversationId)
                loadContact()
            } catch (_: Exception) {
                // Logged in ChatManager
            }
        }
    }

    private suspend fun loadContact() {
        _contact.value = ChatManager.getDatabase().contactDao().getByAddress(conversationId)
    }
}
