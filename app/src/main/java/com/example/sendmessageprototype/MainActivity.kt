package com.example.sendmessageprototype

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.sendmessageprototype.core.User
import com.example.sendmessageprototype.domain.ChatSession
import com.example.sendmessageprototype.persistence.MessageDAO
import com.example.sendmessageprototype.ui.screens.chat.ChatViewModel
import com.example.sendmessageprototype.ui.theme.SendMessagePrototypeTheme
import com.example.sendmessageprototype.ui.screens.chat.ChatScreen
import com.example.sendmessageprototype.ui.screens.main.MainScreen
import com.example.sendmessageprototype.ui.screens.welcome.WelcomeScreen
import com.example.sendmessageprototype.ui.components.BaseOptionsSheet
import com.example.sendmessageprototype.ui.components.InfoField
import com.example.sendmessageprototype.ui.components.SheetAction

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

    LaunchedEffect(Unit) {
        session.navigationEvents.collect { convID ->
            navController.navigate("chat/$convID")
        }
    }

    LaunchedEffect(sessionState) {
        when (sessionState) {
            is ChatSession.SessionState.IdentityRequired -> {
                navController.navigate("welcome") {
                    popUpTo("welcome") { inclusive = true }
                }
            }
            is ChatSession.SessionState.Ready,
            is ChatSession.SessionState.Hibernating -> {
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
