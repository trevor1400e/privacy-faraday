package com.privacy.faraday.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.privacy.faraday.data.db.ContactEntity
import com.privacy.faraday.data.db.MessageEntity
import com.privacy.faraday.network.ChatManager
import com.privacy.faraday.util.AudioRecorder
import com.privacy.faraday.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // Media state
    private val _showAttachmentBar = MutableStateFlow(false)
    val showAttachmentBar: StateFlow<Boolean> = _showAttachmentBar.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _fullScreenImageUri = MutableStateFlow<String?>(null)
    val fullScreenImageUri: StateFlow<String?> = _fullScreenImageUri.asStateFlow()

    private val audioRecorder = AudioRecorder()
    private var recordingTimerJob: Job? = null

    init {
        viewModelScope.launch { loadContact() }
        viewModelScope.launch {
            ChatManager.getDatabase().contactDao().getAll().collect { contacts ->
                _contact.value = contacts.find { it.lxmfAddress == conversationId }
            }
        }
        viewModelScope.launch { ChatManager.sendReadReceipt(conversationId) }
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
            } catch (_: Exception) { }
        }
    }

    // --- Attachment actions ---

    fun toggleAttachmentBar() {
        _showAttachmentBar.value = !_showAttachmentBar.value
    }

    fun hideAttachmentBar() {
        _showAttachmentBar.value = false
    }

    fun sendImage(uri: Uri) {
        _showAttachmentBar.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSending.value = true
                val context = getApplication<Application>()
                val compressed = ImageCompressor.compressImage(context, uri)
                ChatManager.sendImage(conversationId, compressed, "image/jpeg", "")
            } catch (_: Exception) {
            } finally {
                _isSending.value = false
            }
        }
    }

    fun sendFile(uri: Uri) {
        _showAttachmentBar.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSending.value = true
                val context = getApplication<Application>()
                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(uri)?.readBytes() ?: return@launch
                val cursor = resolver.query(uri, null, null, null, null)
                val name = cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) it.getString(idx) else null
                    } else null
                } ?: "file"
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                ChatManager.sendFile(conversationId, bytes, name, mime)
            } catch (_: Exception) {
            } finally {
                _isSending.value = false
            }
        }
    }

    fun startRecording() {
        _showAttachmentBar.value = false
        try {
            val context = getApplication<Application>()
            audioRecorder.start(context)
            _isRecording.value = true
            _recordingSeconds.value = 0
            recordingTimerJob = viewModelScope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingSeconds.value += 1
                    if (_recordingSeconds.value >= 120) {
                        stopRecordingAndSend()
                    }
                }
            }
        } catch (_: Exception) {
            _isRecording.value = false
        }
    }

    fun stopRecordingAndSend() {
        recordingTimerJob?.cancel()
        _isRecording.value = false
        val durationMs = _recordingSeconds.value * 1000
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSending.value = true
                val bytes = audioRecorder.stop()
                if (bytes.isNotEmpty()) {
                    ChatManager.sendVoice(conversationId, bytes, durationMs)
                }
            } catch (_: Exception) {
            } finally {
                _isSending.value = false
            }
        }
    }

    fun cancelRecording() {
        recordingTimerJob?.cancel()
        _isRecording.value = false
        audioRecorder.cancel()
    }

    fun sendLocation(lat: Double, lng: Double, accuracy: Float) {
        _showAttachmentBar.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSending.value = true
                ChatManager.sendLocation(conversationId, lat, lng, accuracy)
            } catch (_: Exception) {
            } finally {
                _isSending.value = false
            }
        }
    }

    fun openImageFullScreen(uri: String) {
        _fullScreenImageUri.value = uri
    }

    fun closeImageFullScreen() {
        _fullScreenImageUri.value = null
    }

    // --- Menu actions ---

    fun openDisappearingMessages() { _showDisappearingDialog.value = true }

    fun setDisappearingDuration(duration: Long) {
        viewModelScope.launch {
            ChatManager.setDisappearingMessages(conversationId, duration)
            loadContact()
            _showDisappearingDialog.value = false
        }
    }

    fun openNicknameEditor() { _showNicknameDialog.value = true }

    fun saveNickname(nickname: String) {
        viewModelScope.launch {
            ChatManager.getDatabase().contactDao().updateNickname(conversationId, nickname)
            loadContact()
            _showNicknameDialog.value = false
        }
    }

    fun openSoundSettings() { _showSoundDialog.value = true }

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
