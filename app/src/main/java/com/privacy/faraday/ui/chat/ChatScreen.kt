package com.privacy.faraday.ui.chat

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val contact by viewModel.contact.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val listState = rememberLazyListState()

    val showDisappearingDialog by viewModel.showDisappearingDialog.collectAsState()
    val showNicknameDialog by viewModel.showNicknameDialog.collectAsState()
    val showSoundDialog by viewModel.showSoundDialog.collectAsState()
    val showSafetyNumberDialog by viewModel.showSafetyNumberDialog.collectAsState()
    val safetyFingerprints by viewModel.safetyFingerprints.collectAsState()

    val showAttachmentBar by viewModel.showAttachmentBar.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val fullScreenImageUri by viewModel.fullScreenImageUri.collectAsState()

    val sessionState = contact?.sessionState ?: "UNKNOWN"
    val displayName = contact?.nickname?.takeIf { it.isNotBlank() }
        ?: contact?.displayName?.takeIf { it.isNotBlank() }
        ?: viewModel.conversationId.take(12) + "..."

    val context = LocalContext.current

    // Photo capture
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoUri?.let { viewModel.sendImage(it) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val photoFile = File(context.cacheDir, "photos").also { it.mkdirs() }
                .let { File(it, "photo_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.sendImage(it) }
    }

    // File picker
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.sendFile(it) }
    }

    // Audio permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording()
    }

    // Location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.sendLocation(location.latitude, location.longitude, location.accuracy)
                    }
                }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (sessionState == "ESTABLISHED") {
                            Text(
                                text = "Encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    ChatOverflowMenu(
                        onDisappearingMessages = viewModel::openDisappearingMessages,
                        onNickname = viewModel::openNicknameEditor,
                        onSoundNotifications = viewModel::openSoundSettings,
                        onViewSafetyNumber = viewModel::openSafetyNumber
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Column {
                if (isRecording) {
                    VoiceRecorderOverlay(
                        durationSeconds = recordingSeconds,
                        onCancel = viewModel::cancelRecording,
                        onSend = viewModel::stopRecordingAndSend
                    )
                } else {
                    AttachmentBar(
                        visible = showAttachmentBar,
                        onCamera = {
                            viewModel.hideAttachmentBar()
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onGallery = {
                            viewModel.hideAttachmentBar()
                            galleryLauncher.launch("image/*")
                        },
                        onFile = {
                            viewModel.hideAttachmentBar()
                            fileLauncher.launch("*/*")
                        },
                        onVoice = {
                            viewModel.hideAttachmentBar()
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onLocation = {
                            viewModel.hideAttachmentBar()
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    )
                    ChatInputBar(
                        text = messageText,
                        onTextChanged = viewModel::onMessageTextChanged,
                        onSend = viewModel::sendMessage,
                        enabled = !isSending,
                        onAttachmentClick = viewModel::toggleAttachmentBar
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sessionState != "ESTABLISHED") {
                KeyExchangeBanner(
                    sessionState = sessionState,
                    onAccept = viewModel::acceptKeyExchange
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onImageClick = viewModel::openImageFullScreen
                    )
                }
            }
        }
    }

    // Dialogs
    if (showDisappearingDialog) {
        DisappearingMessagesDialog(
            currentDuration = contact?.disappearingMessagesDuration ?: 0L,
            onDurationSelected = viewModel::setDisappearingDuration,
            onDismiss = viewModel::dismissDialog
        )
    }
    if (showNicknameDialog) {
        NicknameEditDialog(
            currentNickname = contact?.nickname ?: "",
            onSave = viewModel::saveNickname,
            onDismiss = viewModel::dismissDialog
        )
    }
    if (showSoundDialog) {
        SoundNotificationsDialog(
            isMuted = contact?.isMuted ?: false,
            onToggleMute = viewModel::toggleMuted,
            onDismiss = viewModel::dismissDialog
        )
    }
    if (showSafetyNumberDialog) {
        val fingerprints = safetyFingerprints
        if (fingerprints != null) {
            SafetyNumberDialog(
                localFingerprint = fingerprints.first,
                remoteFingerprint = fingerprints.second,
                onDismiss = viewModel::dismissDialog
            )
        }
    }

    // Full screen image viewer
    fullScreenImageUri?.let { uri ->
        FullScreenImageViewer(
            uri = uri,
            onDismiss = viewModel::closeImageFullScreen
        )
    }
}
