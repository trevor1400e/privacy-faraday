package com.privacy.faraday.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalTestScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: SignalTestViewModel = viewModel()
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
                title = { Text("Signal Protocol Test") },
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

            // Key Generation Card
            KeyGenerationCard(
                state = state,
                onGenerate = viewModel::generateKeys
            )

            // Session Establishment Card
            SessionCard(
                state = state,
                onEstablish = viewModel::establishSession
            )

            // Encrypt/Decrypt Card
            EncryptDecryptCard(
                state = state,
                onPlaintextChanged = viewModel::onPlaintextChanged,
                onEncryptDecrypt = viewModel::encryptAndDecrypt
            )

            // Bidirectional Test Card
            BidirectionalCard(
                state = state,
                onTest = viewModel::testBidirectional
            )

            // Log Card
            LogCard(
                logMessages = state.logMessages,
                onClear = viewModel::clearLog
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KeyGenerationCard(
    state: SignalTestViewModel.UiState,
    onGenerate: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Key Generation")
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onGenerate,
                enabled = !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isProcessing && !state.keysGenerated) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Generate Keys")
                }
            }

            if (state.keysGenerated) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(label = "Status", value = "Keys generated", success = true)
                Spacer(modifier = Modifier.height(4.dp))
                MonoLabel("Alice", state.aliceFingerprint)
                MonoLabel("Bob", state.bobFingerprint)
            }
        }
    }
}

@Composable
private fun SessionCard(
    state: SignalTestViewModel.UiState,
    onEstablish: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Session Establishment")
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onEstablish,
                enabled = state.keysGenerated && !state.sessionEstablished && !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Establish Session")
            }

            if (state.sessionEstablished) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(label = "Status", value = "Session active", success = true)
            }
        }
    }
}

@Composable
private fun EncryptDecryptCard(
    state: SignalTestViewModel.UiState,
    onPlaintextChanged: (String) -> Unit,
    onEncryptDecrypt: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Encrypt & Decrypt")
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.plaintext,
                onValueChange = onPlaintextChanged,
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onEncryptDecrypt,
                enabled = state.sessionEstablished && state.plaintext.isNotBlank() && !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Encrypt & Decrypt")
            }

            if (state.encryptedHex.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                MonoLabel("Encrypted", state.encryptedHex)
                MonoLabel("Decrypted", "\"${state.decryptedText}\"")
                Spacer(modifier = Modifier.height(4.dp))
                state.match?.let { isMatch ->
                    StatusRow(
                        label = "Match",
                        value = if (isMatch) "YES" else "NO",
                        success = isMatch
                    )
                }
                Text(
                    text = "Messages sent: ${state.messageCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BidirectionalCard(
    state: SignalTestViewModel.UiState,
    onTest: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Bidirectional Test")
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onTest,
                enabled = state.encryptedHex.isNotBlank() && !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Bob -> Alice Reply")
            }

            if (state.bidirectionalResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Result",
                    value = state.bidirectionalResult,
                    success = state.bidirectionalResult.contains("MATCH")
                )
            }
        }
    }
}
