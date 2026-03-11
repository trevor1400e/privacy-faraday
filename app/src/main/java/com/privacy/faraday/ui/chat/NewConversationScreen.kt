package com.privacy.faraday.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.faraday.network.ChatManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    var peerAddress by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val myAddress by ChatManager.lxmfAddress.collectAsState()
    var isStarting by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Conversation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Your address card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your LXMF Address",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = myAddress.ifBlank { "Initializing..." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(
                                    myAddress.replace(":", "").replace("<", "").replace(">", "")
                                )
                            )
                        },
                        enabled = myAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Address")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Peer address input
            OutlinedTextField(
                value = peerAddress,
                onValueChange = { peerAddress = it },
                label = { Text("Peer LXMF Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isStarting = true
                    scope.launch {
                        try {
                            val cleanAddr = peerAddress
                                .replace(":", "").replace("<", "").replace(">", "")
                                .trim().lowercase()
                            ChatManager.getOrCreateContact(cleanAddr, displayName.trim())
                            ChatManager.initiateKeyExchange(cleanAddr)
                            onNavigateToChat(cleanAddr)
                        } catch (_: Exception) {
                            isStarting = false
                        }
                    }
                },
                enabled = peerAddress.isNotBlank() && !isStarting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Conversation")
            }
        }
    }
}
