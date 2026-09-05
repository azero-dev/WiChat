package com.example.sendmessageprototype

import android.Manifest
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.room.util.TableInfo
import com.example.sendmessageprototype.core.DiscoveredPeer
import com.example.sendmessageprototype.core.PeerStatus
import com.example.sendmessageprototype.domain.ChatSession
import com.example.sendmessageprototype.persistence.AppDatabase
import com.example.sendmessageprototype.persistence.MessageDAO
import com.example.sendmessageprototype.persistence.MessageEntity
import com.example.sendmessageprototype.transport.WiFiDirectTransport
import com.example.sendmessageprototype.ui.chat.ChatViewModel
import com.example.sendmessageprototype.ui.discovery.DiscoveryViewModel
import com.example.sendmessageprototype.ui.theme.SendMessagePrototypeTheme
import com.example.sendmessageprototype.core.MessageInTransit
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var chatService: ChatService? = null
    private var isBound = false
    private var serviceReady by mutableStateOf(false)

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ChatService.ChatServiceBinder
            chatService = binder.getService()
            isBound = true
//            chatService?.chatSession?.start()
            serviceReady = true
            checkAndRequestPermissions()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            chatService = null
            isBound = false
            serviceReady = false
        }
    }

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            chatService?.chatSession?.start()
        } else {
            checkAndRequestPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Intent(this, ChatService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        enableEdgeToEdge()
        setContent {
            SendMessagePrototypeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (serviceReady && chatService != null) {
                        val session = chatService!!.chatSession
                        AppNavigation(session, session.getMessageDAO())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            chatService?.chatSession?.start()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun AppNavigation(
    session: ChatSession,
    messageDAO: MessageDAO,
) {
    val navController = rememberNavController()
    val sessionState by session.state.collectAsState()
    val savedPeers by session.getSavedPeers().collectAsState()
    LaunchedEffect(savedPeers) {
        val connectingAddress = session.connectingAddress.value
        if (connectingAddress != null) {
            val identifiedUser = savedPeers.find { it.lastKnownDeviceAddress == connectingAddress }
            identifiedUser?.let { user ->
                val convID = if (session.state.value is ChatSession.SessionState.Ready) {
                    val localID = (session.state.value as ChatSession.SessionState.Ready).localUser.userID
                    if (localID < user.userID) "${localID}_${user.userID}" else "${user.userID}_${localID}"
                } else ""
                if (convID.isNotEmpty()) {
                    navController.navigate("chat/$convID")
                }
            }
        }
    }
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is ChatSession.SessionState.IdentityRequired -> {
                navController.navigate("welcome") {
                    popUpTo("welcome") { inclusive = true }
                }
            }
            is ChatSession.SessionState.Ready -> {
                navController.navigate("main") {
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> {}
        }
    }
    NavHost(navController = navController, startDestination = "loading") {
        composable("loading") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable("welcome") {
            WelcomeScreen(nameEntered = { name ->
                session.initialiseIdentity(name)
            })
        }
        composable("main") {
            MainScreen(
                session = session,
                onConversationClick = { convID ->
                    navController.navigate("chat/$convID")
                }
            )
        }
        composable(
            route = "chat/{conversationID}",
            arguments = listOf(navArgument("conversationID") { type = NavType.StringType })
        ) { backStackEntry ->
            val convID = backStackEntry.arguments?.getString("conversationID") ?: ""
            val readyState = sessionState as? ChatSession.SessionState.Ready
            LaunchedEffect(convID) {
                if (readyState != null) {
                    val peerID = convID.split("_").firstOrNull { it != readyState.localUser.userID }
                    peerID?.let { session.requestChatConnection(it) }
                }
            }
            if (readyState != null) {
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(session, messageDAO, convID)
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    localUserID = readyState.localUser.userID,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

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
            FloatingActionButton(onClick = { showDiscovery = true }) {
                Icon(Icons.Default.Add, contentDescription = "New chat")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                            onClick = { onConversationClick(meta.conversationID) }
                        )
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
            onNavigateToChat = { convID -> onConversationClick(convID)},
            onDismiss = {
                showDiscovery = false
                session.startDiscoveryCycle()
            }
        )
    }
    if (showProfile) {
        ProfileBottomSheet(
            session = session,
            onDismiss = { showProfile = false }
        )
    }
}

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
        DeleteConfirmationDialog(
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
                        text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(message.timestamp),
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

@Composable
fun WelcomeScreen(nameEntered: (String) -> Unit) {
    var name by remember { mutableStateOf("")}
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to WiChat",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Enter your username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { if (name.isNotBlank()) nameEntered(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryBottomSheet(
    session: ChatSession,
    viewModel: DiscoveryViewModel,
    onNavigateToChat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val peers by viewModel.discoveredPeers.collectAsState()
    val modalBottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
    ) { 
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Searching nearby devices",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                IconButton(onClick = { viewModel.refreshScan() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan again")
                }
            }
            if (peers.isEmpty()) {
                Text("No devices found yet")
            } else {
                LazyColumn { 
                    items(peers) { peer ->
                        val isConnected = session.getConnectedDevice() == peer.deviceAddress
                        DiscoveryPeerCard(peer, isConnected) {
                            val started = viewModel.connectToDevice(peer.deviceAddress)
                            if (started) {
                                onDismiss()
                            } else {
                                val userID = session.userIDOf(peer.deviceAddress)
                                val localUser = session.localUser
                                if (userID != null && localUser != null) {
                                    val convID = session.generateConversationID(localUser.userID, userID)
                                    onNavigateToChat(convID)
                                    onDismiss()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    session: ChatSession,
    onDismiss: () -> Unit,
) {
    val sessionState by session.state.collectAsState()
    val localUser = (sessionState as? ChatSession.SessionState.Ready)?.localUser ?: return
    val config by session.config.collectAsState()
    var newName by remember { mutableStateOf(localUser.userName) }
    val modalBottomSheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
        ) {
            Text("My profile", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(
                "User ID",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                localUser.userID,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = {newName = it },
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
                config.notificationsEnabled,
            ) {
                session.toggleNotifications(it)
            }
            Spacer(Modifier.height(16.dp))
            ProfileToggle(
                "Inactive Mode",
                "Pause network activity",
                config.isInactiveMode
            ) {
                session.toggleInactiveMode(it)
            }
        }
    }
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

@Composable
fun ConversationCard(
    name: String,
    lastMessageText: ByteArray,
    lastTime: Long,
    status: PeerStatus,
    isPersistent: Boolean,
    onClick: () -> Unit
) {
    val fullText = String(lastMessageText)
    val previewText = when {
        fullText.isEmpty() -> "No messages yet"
        fullText.length > 30 -> fullText.take(30) + "..."
        else -> fullText
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    ConnectionStatusIcon(status, isPersistent)
                }
                Text(
                    text = previewText,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(lastTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun DiscoveryPeerCard(
    peer: DiscoveredPeer,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.DoneAll else Icons.Default.Wifi,
                contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column { 
                Text(
                    text = if (isConnected) "Already connected" else "Device found",
                    style = MaterialTheme.typography.titleMedium)
                Text("${peer.deviceName} (${peer.deviceAddress})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ConnectionStatusIcon(status: PeerStatus, isPersistent: Boolean) {
    val icon = when {
        status == PeerStatus.CONNECTED -> Icons.Default.Wifi
        status == PeerStatus.NEARBY -> Icons.Default.Wifi
        !isPersistent -> Icons.Default.LinkOff
        else -> Icons.Default.Wifi
    }

    val color = when {
        status == PeerStatus.CONNECTED -> MaterialTheme.colorScheme.primary
        status == PeerStatus.NEARBY -> MaterialTheme.colorScheme.tertiary
        !isPersistent -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = color
    )
}

@Composable
fun ProximityBanner(status: PeerStatus, onClick: () -> Unit) {
    if (status == PeerStatus.CONNECTED) return

    val backgroundColor = if (status == PeerStatus.NEARBY)
        MaterialTheme.colorScheme.tertiaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val text = if (status == PeerStatus.NEARBY)
        "Peer is in range. Tap to connect"
    else
        "Peer not in range"

    Surface(
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = status == PeerStatus.NEARBY) { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageOptionSheet(
    message: MessageEntity,
    transitInfo: MessageInTransit?,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismiss) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)) {
            Text("Message options", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            if (transitInfo != null) {
                Text(
                    "Transport info",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "TTL: ${transitInfo.ttl}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Retry counter: ${transitInfo.retryCounter}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }
            ListItem(
                headlineContent = { Text("Copy text") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.clickable { onCopy() },
            )
            ListItem(
                headlineContent = { Text("Delete message", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                ) },
                modifier = Modifier.clickable { onDelete() },
            )
        }
    }
}

@Composable

fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete message") },
        text = { Text("Are you sure you want to delete this message? It won't be deleted on the other device.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


















