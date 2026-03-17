package com.privacy.faraday.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.faraday.data.db.ConversationPreview
import com.privacy.faraday.data.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(message: MessageEntity) {
    if (message.isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        return
    }

    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOutgoing)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOutgoing)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isOutgoing) 16.dp else 4.dp,
        bottomEnd = if (isOutgoing) 4.dp else 16.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            "FAILED" -> Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Failed",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            "DELIVERED" -> Row {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Delivered",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            "READ" -> Text(
                                text = "R",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                style = MaterialTheme.typography.labelSmall
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Sent",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun KeyExchangeBanner(
    sessionState: String,
    onAccept: () -> Unit
) {
    val backgroundColor = when (sessionState) {
        "ESTABLISHED" -> return
        "KEY_EXCHANGE_RECEIVED" -> MaterialTheme.colorScheme.tertiaryContainer
        "KEY_EXCHANGE_SENT" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> return // UNKNOWN — no banner until a message is sent
    }
    val textColor = MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (sessionState) {
                    "KEY_EXCHANGE_RECEIVED" -> "Wants to start a secure chat"
                    "KEY_EXCHANGE_SENT" -> "Waiting for them to accept..."
                    else -> ""
                },
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (sessionState == "KEY_EXCHANGE_RECEIVED") {
                TextButton(onClick = onAccept) {
                    Text("Accept", color = textColor)
                }
            }
        }
    }
}

@Composable
fun ConversationRow(
    preview: ConversationPreview,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle with first letter
            val resolvedName = preview.nickname.takeIf { it.isNotBlank() }
                ?: preview.displayName.takeIf { it.isNotBlank() }
                ?: preview.lxmfAddress.take(12) + "..."
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = (resolvedName.firstOrNull() ?: '?').uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = resolvedName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (preview.lastTimestamp != null) {
                        Text(
                            text = formatTime(preview.lastTimestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preview.sessionState != "ESTABLISHED") {
                        Text(
                            text = when (preview.sessionState) {
                                "KEY_EXCHANGE_RECEIVED" -> "Wants to chat — tap to accept"
                                "KEY_EXCHANGE_SENT" -> "Waiting for them to accept..."
                                else -> "Tap to start"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = preview.lastMessage ?: "No messages yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
    }
}

@Composable
fun ChatOverflowMenu(
    onDisappearingMessages: () -> Unit,
    onNickname: () -> Unit,
    onSoundNotifications: () -> Unit,
    onViewSafetyNumber: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options"
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Disappearing messages") },
            onClick = { expanded = false; onDisappearingMessages() },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Nickname") },
            onClick = { expanded = false; onNickname() },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Sound & notifications") },
            onClick = { expanded = false; onSoundNotifications() },
            leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Safety number") },
            onClick = { expanded = false; onViewSafetyNumber() },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
        )
    }
}

@Composable
fun DisappearingMessagesDialog(
    currentDuration: Long,
    onDurationSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "Off" to 0L,
        "5 minutes" to 5 * 60 * 1000L,
        "1 hour" to 60 * 60 * 1000L,
        "1 day" to 24 * 60 * 60 * 1000L,
        "1 week" to 7 * 24 * 60 * 60 * 1000L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disappearing messages") },
        text = {
            Column {
                Text(
                    text = "Messages will be deleted after the selected time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { (label, duration) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDurationSelected(duration) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentDuration == duration,
                            onClick = { onDurationSelected(duration) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun NicknameEditDialog(
    currentNickname: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentNickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit nickname") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (currentNickname.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun SoundNotificationsDialog(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound & notifications") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mute conversation")
                    Switch(
                        checked = isMuted,
                        onCheckedChange = { onToggleMute() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "When muted, you won't receive alerts for new messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {}
    )
}

@Composable
fun SafetyNumberDialog(
    localFingerprint: String,
    remoteFingerprint: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Safety number") },
        text = {
            Column {
                Text(
                    text = "Your key:",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = localFingerprint,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Their key:",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = remoteFingerprint ?: "No session established yet",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (remoteFingerprint != null)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "If these numbers match what your contact sees, your conversation is secure. Keys change each session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {}
    )
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val format = if (diff < 24 * 60 * 60 * 1000) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }
    return format.format(Date(timestamp))
}
