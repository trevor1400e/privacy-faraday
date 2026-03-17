package com.privacy.faraday.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onNavigateToChat: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    scannedAddress: String? = null
) {
    var peerAddress by remember { mutableStateOf(scannedAddress ?: "") }
    LaunchedEffect(scannedAddress) {
        if (!scannedAddress.isNullOrBlank()) {
            peerAddress = scannedAddress
        }
    }
    var displayName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val myAddress by ChatManager.lxmfAddress.collectAsState()
    var isStarting by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val cleanMyAddress = myAddress.replace(":", "").replace("<", "").replace(">", "").trim()

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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Your address card with QR code
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your LXMF Address",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (cleanMyAddress.isNotBlank()) {
                        QrCodeImage(
                            content = cleanMyAddress,
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = cleanMyAddress.ifBlank { "Initializing..." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(cleanMyAddress))
                        },
                        enabled = cleanMyAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Address")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Peer address input with scan button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = peerAddress,
                    onValueChange = { peerAddress = it },
                    label = { Text("Peer LXMF Address") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onNavigateToScanner) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Scan QR Code",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

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
