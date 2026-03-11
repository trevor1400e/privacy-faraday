package com.privacy.faraday.ui.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class ReticulumTestViewModel(application: Application) : AndroidViewModel(application) {

    data class ReceivedMessageUi(
        val sourceHash: String,
        val content: String,
        val timestamp: String,
        val hash: String
    )

    data class UiState(
        val isRunning: Boolean = false,
        val isInitializing: Boolean = false,
        val lxmfAddress: String = "",
        val identityHash: String = "",
        val destHash: String = "",
        val messageContent: String = "",
        val lastSendResult: String = "",
        val receivedMessages: List<ReceivedMessageUi> = emptyList(),
        val logMessages: List<String> = emptyList(),
        val errorMessage: String? = null,
        val isProcessing: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    fun initializeReticulum() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isInitializing = true, errorMessage = null) }
                addLog("Starting Reticulum...")

                val address = ReticulumManager.initialize(getApplication())
                val identityHash = ReticulumManager.getIdentityHash()

                _uiState.update {
                    it.copy(
                        isRunning = true,
                        isInitializing = false,
                        lxmfAddress = address,
                        identityHash = identityHash
                    )
                }
                addLog("Reticulum started. LXMF address: $address")
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

    fun shutdownReticulum() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                pollingJob?.cancel()
                pollingJob = null
                ReticulumManager.shutdown()
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isProcessing = false,
                        lxmfAddress = "",
                        identityHash = ""
                    )
                }
                addLog("Reticulum shut down")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Shutdown failed: ${e.message}"
                    )
                }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun announce() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                ReticulumManager.announce()
                _uiState.update { it.copy(isProcessing = false) }
                addLog("Announce sent")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Announce failed: ${e.message}"
                    )
                }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun sendMessage() {
        viewModelScope.launch {
            try {
                val destHash = _uiState.value.destHash.trim()
                val content = _uiState.value.messageContent.trim()
                if (destHash.isBlank() || content.isBlank()) return@launch

                _uiState.update { it.copy(isProcessing = true) }
                addLog("Sending to $destHash...")

                val result = ReticulumManager.sendMessage(destHash, content.toByteArray(Charsets.UTF_8))
                val status = result["status"] ?: "unknown"
                val message = result["message"] ?: ""

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        lastSendResult = "$status: $message"
                    )
                }
                addLog("Send result: $status - $message")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Send failed: ${e.message}"
                    )
                }
                addLog("ERROR: ${e.message}")
            }
        }
    }

    fun onDestHashChanged(value: String) {
        _uiState.update { it.copy(destHash = value) }
    }

    fun onMessageContentChanged(value: String) {
        _uiState.update { it.copy(messageContent = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearLog() {
        _uiState.update { it.copy(logMessages = emptyList()) }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                try {
                    val messages = ReticulumManager.pollMessages()
                    if (messages.isNotEmpty()) {
                        val uiMessages = messages.map { msg ->
                            val ts = SimpleDateFormat("HH:mm:ss", Locale.US)
                                .format(Date((msg.timestamp * 1000).toLong()))
                            ReceivedMessageUi(
                                sourceHash = msg.sourceHash,
                                content = msg.content,
                                timestamp = ts,
                                hash = msg.hash
                            )
                        }
                        _uiState.update {
                            it.copy(receivedMessages = it.receivedMessages + uiMessages)
                        }
                        uiMessages.forEach { msg ->
                            addLog("Received from ${msg.sourceHash}: \"${msg.content}\"")
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
