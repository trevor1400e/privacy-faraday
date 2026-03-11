package com.privacy.faraday.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReticulumTestScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ReticulumTestViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reticulum/LXMF Test") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            NetworkControlCard(
                state = state,
                onStart = viewModel::initializeReticulum,
                onStop = viewModel::shutdownReticulum,
                onAnnounce = viewModel::announce
            )

            IdentityCard(state = state)

            SendMessageCard(
                state = state,
                onDestHashChanged = viewModel::onDestHashChanged,
                onMessageContentChanged = viewModel::onMessageContentChanged,
                onSend = viewModel::sendMessage
            )

            ReceivedMessagesCard(state = state)

            LogCard(
                logMessages = state.logMessages,
                onClear = viewModel::clearLog
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NetworkControlCard(
    state: ReticulumTestViewModel.UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAnnounce: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Network Control")
            Spacer(modifier = Modifier.height(8.dp))

            if (!state.isRunning) {
                Button(
                    onClick = onStart,
                    enabled = !state.isInitializing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isInitializing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Start Reticulum")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onStop,
                        enabled = !state.isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stop")
                    }
                    Button(
                        onClick = onAnnounce,
                        enabled = !state.isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Announce")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(label = "Status", value = "Running", success = true)
            }
        }
    }
}

@Composable
private fun IdentityCard(state: ReticulumTestViewModel.UiState) {
    if (state.lxmfAddress.isBlank()) return

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Identity")
            Spacer(modifier = Modifier.height(8.dp))

            MonoLabel("LXMF Address", state.lxmfAddress)
            Spacer(modifier = Modifier.height(4.dp))
            MonoLabel("Identity Hash", state.identityHash)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(state.lxmfAddress.replace(":", "")))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy Address")
            }
        }
    }
}

@Composable
private fun SendMessageCard(
    state: ReticulumTestViewModel.UiState,
    onDestHashChanged: (String) -> Unit,
    onMessageContentChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Send Message")
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.destHash,
                onValueChange = onDestHashChanged,
                label = { Text("Destination Hash") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.messageContent,
                onValueChange = onMessageContentChanged,
                label = { Text("Message Content") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSend,
                enabled = state.isRunning && state.destHash.isNotBlank()
                        && state.messageContent.isNotBlank() && !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send")
                }
            }

            if (state.lastSendResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Result",
                    value = state.lastSendResult,
                    success = state.lastSendResult.startsWith("ok")
                )
            }
        }
    }
}

@Composable
private fun ReceivedMessagesCard(state: ReticulumTestViewModel.UiState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Received Messages (${state.receivedMessages.size})")
            Spacer(modifier = Modifier.height(8.dp))

            if (state.receivedMessages.isEmpty()) {
                Text(
                    text = if (state.isRunning) "Listening for messages..." else "Start Reticulum to receive messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.receivedMessages.takeLast(10).forEach { msg ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MonoLabel("From", msg.sourceHash)
                        MonoLabel("Content", "\"${msg.content}\"")
                        Text(
                            text = "at ${msg.timestamp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
