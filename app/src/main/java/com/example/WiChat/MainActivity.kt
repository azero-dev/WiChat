package com.example.WiChat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.WiChat.core.security.BiometricAuthHandler
import com.example.WiChat.core.security.SecurityManager
import com.example.WiChat.domain.ChatSession
import com.example.WiChat.persistence.MessageDAO
import com.example.WiChat.ui.screens.chat.ChatViewModel
import com.example.WiChat.ui.theme.WiChatTheme
import com.example.WiChat.ui.screens.chat.ChatScreen
import com.example.WiChat.ui.screens.main.MainScreen
import com.example.WiChat.ui.screens.welcome.WelcomeScreen

class MainActivity : FragmentActivity() {
    private var chatService: ChatService? = null
    private var isBound = false
    private var serviceReady by mutableStateOf(false)
//    biometrics
    private lateinit var securityManager: SecurityManager
    private var isUnlocked by mutableStateOf(false)
    private var isAuthInProgress by mutableStateOf(false)

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ChatService.ChatServiceBinder
            chatService = binder.getService()
            isBound = true
            serviceReady = true
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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        securityManager = SecurityManager(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        splashScreen.setKeepOnScreenCondition { !serviceReady }

        Intent(this, ChatService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        enableEdgeToEdge()
        setContent {
            WiChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (serviceReady && chatService != null) {
                        val session = chatService!!.chatSession
                        val config by session.config.collectAsState()
                        LaunchedEffect(config, serviceReady) {
                            if (config.biometricEnabled) {
                                if (!isUnlocked && !isAuthInProgress) {
                                    checkBiometric(config.lockTimeout)
                                }
                            } else {
                                isUnlocked = true
                                checkAndRequestPermissions()
                            }
                        }
                        if (isUnlocked) {
                            AppNavigation(session, session.getMessageDAO())
                        }
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
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

    private fun checkBiometric(timeout: Long) {
        if (securityManager.isSessionValid(timeout)) {
            isUnlocked = true
            checkAndRequestPermissions()
            return
        }
        if (isAuthInProgress) return
        isAuthInProgress = true
        val authHandler = BiometricAuthHandler(
            activity = this,
            onAuthSuccess = {
                securityManager.updateUnlockTimestamp()
                isUnlocked = true
                isAuthInProgress = false
                checkAndRequestPermissions()
            },
            onAuthError = { error ->
                isAuthInProgress = false
                Toast.makeText(this, "Authentication required: $error", Toast.LENGTH_SHORT).show()
                finish()
            }
        )
        authHandler.authenticate()
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
