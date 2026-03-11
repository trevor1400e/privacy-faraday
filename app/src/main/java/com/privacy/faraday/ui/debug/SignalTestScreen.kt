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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalTestScreen(
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

@Composable
private fun LogCard(
    logMessages: List<String>,
    onClear: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Log")
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }

            if (logMessages.isEmpty()) {
                Text(
                    text = "No operations yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(logMessages.size) {
                    if (logMessages.isNotEmpty()) {
                        listState.animateScrollToItem(logMessages.lastIndex)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.height(200.dp)
                ) {
                    items(logMessages) { msg ->
                        Text(
                            text = msg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun MonoLabel(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String, success: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (success) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (success) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            color = if (success) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}
