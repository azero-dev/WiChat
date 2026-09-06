package com.example.sendmessageprototype.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sendmessageprototype.core.ConversationMeta
import com.example.sendmessageprototype.core.PeerStatus
import com.example.sendmessageprototype.core.User
import com.example.sendmessageprototype.domain.ChatSession
import com.example.sendmessageprototype.ui.components.BaseItemCard
import com.example.sendmessageprototype.ui.components.BaseOptionsSheet
import com.example.sendmessageprototype.ui.components.ConfirmationDialog
import com.example.sendmessageprototype.ui.components.InfoField
import com.example.sendmessageprototype.ui.components.SheetAction
import com.example.sendmessageprototype.ui.screens.ConnectionStatusIcon
import com.example.sendmessageprototype.ui.screens.discovery.DiscoveryBottomSheet
import com.example.sendmessageprototype.ui.screens.discovery.DiscoveryViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    session: ChatSession,
    onConversationClick: (String) -> Unit,
) {
    val conversations by session.getConversationMetas().collectAsState(initial = emptyList())
    val savedPeers by session.getSavedPeers().collectAsState()
    var showDiscovery by remember { mutableStateOf(false) }
    val connectingAddress by session.connectingAddress.collectAsState()
    var showProfile by remember { mutableStateOf(false) }
    var selectedMeta by remember { mutableStateOf<ConversationMeta?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val sessionState by session.state.collectAsState()
    val isHibernating = sessionState is ChatSession.SessionState.Hibernating
    var showAdvancedCleanupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiChat") },
                actions = {
                    IconButton(onClick = { showProfile = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isHibernating) {
                FloatingActionButton(onClick = { showDiscovery = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New chat")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isHibernating) {
                HibernationBanner(onProfileClick = { showProfile = true })
            } else {
                if (connectingAddress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { session.cancelConnectAttempt() }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Connecting... (Tap to cancel)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                if (conversations.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) {
                        Text("Tap on + to start a new one")
                    }
                } else {
                    LazyColumn(modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                    ) {
                        items(conversations) { meta ->
                            val peer = savedPeers.find { it.userID == meta.peerID }
                            val status by session.getPeerStatus(meta.peerID).collectAsState(initial = PeerStatus.ABSENT)
                            ConversationCard(
                                name = peer?.userName ?: "Unknown (${meta.peerID.take(5)})",
                                lastMessageText = meta.lastMessageText,
                                lastTime = meta.lastMessageAt,
                                status = status,
                                isPersistent = peer?.isPersistent ?: false,
                                onClick = { onConversationClick(meta.conversationID) },
                                onLongClick = { selectedMeta = meta }
                            )
                        }
                    }
                }
            }
        }
    }
    if (showDiscovery) {
        DiscoveryBottomSheet(
            session = session,
            viewModel = viewModel(
                factory = DiscoveryViewModel.Factory(session)
            ),
            onNavigateToChat = { convID -> onConversationClick(convID) },
            onDismiss = {
                showDiscovery = false
                if (!isHibernating) {
                    session.startDiscoveryCycle()
                }
            }
        )
    }
    if (showProfile) {
        ProfileBottomSheet(
            session = session,
            onAdvanceCleanupClick = { showAdvancedCleanupDialog = true },
            onDismiss = { showProfile = false },
        )
    }
    selectedMeta?.let { meta ->
        val peer = savedPeers.find { it.userID == meta.peerID } ?: return@let
        ConversationOptionsSheet(
            peer = peer,
            onClearHistory = { showClearDialog = true },
            onDeleteContact = { showDeleteDialog = true},
            onDismiss = { selectedMeta = null }
        )
    }
    if (showClearDialog) {
        ConfirmationDialog(
            title = "Clear history",
            textBody = "Are you sure you want to clear this conversation's history? It won't be deleted on the other device.",
            onConfirm = {
                selectedMeta?.let { session.deleteConversation(it.conversationID) }
                showClearDialog = false
                selectedMeta = null
            },
            onDismiss = { showClearDialog = false }
        )
    }
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete contact",
            textBody = "Remove this contact and all messages? It won't be deleted on the other device.",
            onConfirm = {
                selectedMeta?.let { session.deleteConversation(it.conversationID, it.peerID) }
                showDeleteDialog = false
                selectedMeta = null
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
    if (showAdvancedCleanupDialog) {
        ConfirmationDialog(
            title = "Warning: Advanced fix",
            textBody = "This mode uses experimental features to clear Android Wifi cache. It may be used to fix reconnection issues, but it will delete ALL remembered Wifi Direct Persistent groups on your device.",
            onConfirm = {
                session.toggleAdvancedCleanup(true)
                showAdvancedCleanupDialog = false
            },
            onDismiss = { showAdvancedCleanupDialog = false }
        )
    }
}

@Composable
fun ConversationCard(
    name: String,
    lastMessageText: ByteArray,
    lastTime: Long,
    status: PeerStatus,
    isPersistent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fullText = String(lastMessageText)
    val textLength = 60
    val previewText = when {
        fullText.isEmpty() -> "No messages yet"
        fullText.length > textLength -> fullText.take(textLength) + "..."
        else -> fullText
    }
    BaseItemCard(
        title = name,
        subtitle = previewText,
        icon = Icons.Default.AccountCircle,
        onClick = onClick,
        onLongClick = onLongClick,
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(lastTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                ConnectionStatusIcon(status, isPersistent)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    session: ChatSession,
    onAdvanceCleanupClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sessionState by session.state.collectAsState()
    val localUser = when (val state = sessionState) {
        is ChatSession.SessionState.Ready -> state.localUser
        is ChatSession.SessionState.Hibernating -> state.localUser
        else -> return
    }
    val config by session.config.collectAsState()
    var newName by remember { mutableStateOf(localUser.userName) }

    BaseOptionsSheet(
        title = "Profile",
        onDismiss = onDismiss,
        content = {
            InfoField(label = "User ID", value = localUser.userID, isItalic = true)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (newName != localUser.userName && newName.isNotBlank()) {
                        IconButton(onClick = { session.updateLocalUserName(newName) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            ProfileToggle(
                "Notifications",
                "Alert for new messages",
                config.notificationsEnabled
            ) {
                session.toggleNotifications(it)
            }
            Spacer(Modifier.height(16.dp))
            ProfileToggle(
                "Inactive mode",
                "Pause network activity",
                config.isInactiveMode
            ) {
                session.toggleInactiveMode(it)
            }
            Spacer(Modifier.height(16.dp))
            ProfileToggle(
                title = "Advanced connection fix",
                subtitle = "Delete Android's Persistent Groups. CAUTION: If you have devices connected to your phone via Wifi Direct, this will delete them!",
                checked = config.isAdvancedCleanupEnabled,
            ) { enabled ->
                if (enabled) {
                    onAdvanceCleanupClick()
                } else {
                    session.toggleAdvancedCleanup(false)
                }
            }
        }
    )
}

@Composable
fun ConversationOptionsSheet(
    peer: User,
    onClearHistory: () -> Unit,
    onDeleteContact: () -> Unit,
    onDismiss: () -> Unit,
) {
    BaseOptionsSheet(
        title = peer.userName,
        onDismiss = onDismiss,
        content = {
            InfoField("User ID", peer.userID)
            InfoField("Last known Address", peer.lastKnownDeviceAddress ?: "Unknown")
        },
        actions = listOf(
            SheetAction(
                icon = Icons.Default.Refresh,
                label = "Clear history",
                onClick = onClearHistory,
            ),
            SheetAction(
                icon = Icons.Default.Delete,
                label = "Delete contact and chat",
                isDanger = true,
                onClick = onDeleteContact,
            ),
        )
    )
}

@Composable
fun ProfileToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
