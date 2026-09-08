package com.example.WiChat.ui.screens.chat

import android.content.ClipData
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.WiChat.core.MessageInTransit
import com.example.WiChat.persistence.MessageEntity
import com.example.WiChat.ui.components.BaseOptionsSheet
import com.example.WiChat.ui.components.ConfirmationDialog
import com.example.WiChat.ui.components.InfoField
import com.example.WiChat.ui.components.SheetAction
import com.example.WiChat.ui.screens.ProximityBanner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    localUserID: String,
    onBack: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val status by viewModel.peerStatus.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val savedPeers by viewModel.session.getSavedPeers().collectAsState()
    val peerID = viewModel.conversationID.split("_").firstOrNull { it != localUserID } ?: "Unknown"
    val peer = savedPeers.find { it.userID == peerID }
    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peer?.userName ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type...") },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            ProximityBanner(status = status) { viewModel.connectManually() }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isMine = message.senderID == localUserID,
                        onLongClick = { selectedMessage = message },
                    )
                }
            }
        }
    }
    selectedMessage?.let { message ->
        MessageOptionSheet(
            message = message,
            transitInfo = viewModel.getTransitInfo(message.messageID),
            onCopy = {
                val content = String(message.content)
                scope.launch {
                    val clipData = ClipData.newPlainText("WiChat message", content)
                    clipboard.setClipEntry(ClipEntry(clipData))
                }
                selectedMessage = null
            },
            onDelete = { showDeleteDialog = true },
            onDismiss = { selectedMessage = null }
        )
    }
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete message",
            textBody = "Are you sure you want to delete this message? It won't be deleted on the other device.",
            onConfirm = {
                selectedMessage?.let { viewModel.deleteMessage(it.messageID) }
                showDeleteDialog = false
                selectedMessage = null
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMine: Boolean,
    onLongClick: () -> Unit,
) {
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val shape = if (isMine) {
        MaterialTheme.shapes.medium.copy(bottomEnd = CornerSize(0.dp))
    } else {
        MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp))
    }

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = alignment)
    {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = color),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text = String(message.content), style = MaterialTheme.typography.bodyLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (isMine) {
                        Spacer(Modifier.width(4.dp))
                        MessageStatusIcon(message.state)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageOptionSheet(
    message: MessageEntity,
    transitInfo: MessageInTransit?,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    BaseOptionsSheet(
        title = "Message options",
        onDismiss = onDismiss,
        content = {
            transitInfo?.let {
                InfoField("TTL", it.ttl.toString())
                InfoField("Retry counter", "${it.retryCounter.toString()}/10")
            }
        },
        actions = listOf(
            SheetAction(
                icon = Icons.Default.ContentCopy,
                label = "Copy message",
                onClick = onCopy,
            ),
            SheetAction(
                icon = Icons.Default.Delete,
                label = "Delete message",
                isDanger = true,
                onClick = onDelete,
            ),
        )
    )
}

@Composable
fun MessageStatusIcon(state: String) {
    val icon = when (state) {
        "SENDING" -> Icons.Default.Done
        "DELIVERED" -> Icons.Default.DoneAll
        "FAILED" -> Icons.Default.ErrorOutline
        else -> Icons.Default.Done
    }
    val color = if (state == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Icon(
        imageVector = icon,
        contentDescription = state,
        modifier = Modifier.size(16.dp),
        tint = color,
    )
}

