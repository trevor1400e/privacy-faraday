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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationTestScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: IntegrationTestViewModel = viewModel()
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
                title = { Text("Integration Test") },
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

            SystemStatusCard(state = state, onInitialize = viewModel::initializeAll)
            KeyExchangeCard(
                state = state,
                onPeerAddressChanged = viewModel::onPeerAddressChanged,
                onInitiateKeyExchange = viewModel::initiateKeyExchange
            )
            EncryptedMessagingCard(
                state = state,
                onMessageInputChanged = viewModel::onMessageInputChanged,
                onSend = viewModel::sendMessage
            )
            MessageLogCard(state = state)
            LogCard(
                logMessages = state.logMessages,
                onClear = viewModel::clearLog
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SystemStatusCard(
    state: IntegrationTestViewModel.UiState,
    onInitialize: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("System Status")
            Spacer(modifier = Modifier.height(8.dp))

            if (!state.reticulumRunning) {
                Button(
                    onClick = onInitialize,
                    enabled = !state.isInitializing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isInitializing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Initialize (Signal + Reticulum)")
                    }
                }
            } else {
                StatusRow(label = "Reticulum", value = "Running", success = true)
                Spacer(modifier = Modifier.height(4.dp))
                MonoLabel("LXMF Address", state.lxmfAddress)
                Spacer(modifier = Modifier.height(4.dp))
                MonoLabel("Signal Fingerprint", state.signalFingerprint)
            }
        }
    }
}

@Composable
private fun KeyExchangeCard(
    state: IntegrationTestViewModel.UiState,
    onPeerAddressChanged: (String) -> Unit,
    onInitiateKeyExchange: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Key Exchange")
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.peerAddress,
                onValueChange = onPeerAddressChanged,
                label = { Text("Peer LXMF Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onInitiateKeyExchange,
                    enabled = state.reticulumRunning && state.peerAddress.isNotBlank()
                            && !state.isProcessing
                            && state.peerSessionState != "ESTABLISHED",
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Key Exchange")
                    }
                }
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(state.lxmfAddress.replace(":", "").replace("<", "").replace(">", ""))
                        )
                    },
                    enabled = state.lxmfAddress.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy My Addr")
                }
            }

            if (state.peerSessionState != "UNKNOWN" || state.peerAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Session",
                    value = state.peerSessionState,
                    success = state.peerSessionState == "ESTABLISHED"
                )
            }
        }
    }
}

@Composable
private fun EncryptedMessagingCard(
    state: IntegrationTestViewModel.UiState,
    onMessageInputChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Encrypted Messaging")
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.messageInput,
                onValueChange = onMessageInputChanged,
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSend,
                enabled = state.peerSessionState == "ESTABLISHED"
                        && state.messageInput.isNotBlank()
                        && !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send Encrypted")
                }
            }
        }
    }
}

@Composable
private fun MessageLogCard(state: IntegrationTestViewModel.UiState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Messages (${state.messages.size})")
            Spacer(modifier = Modifier.height(8.dp))

            if (state.messages.isEmpty()) {
                Text(
                    text = "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.messages.takeLast(20).forEach { msg ->
                    val arrow = if (msg.direction == "OUT") "\u2192" else "\u2190"
                    val color = if (msg.direction == "OUT")
                        MaterialTheme.colorScheme.primary
                    else
                        Color(0xFF4CAF50)

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "$arrow ${msg.plaintext}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = color
                        )
                        Text(
                            text = "${msg.timestamp} | ${msg.ciphertextHex}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
