package com.example.sendmessageprototype

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.example.sendmessageprototype.ui.theme.SendMessagePrototypeTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalDensity

class MainActivity : ComponentActivity() {
    private val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private val viewModel by viewModels<WifiP2pViewModel>()
    //Intents
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
//     Request permissions from user
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
//            Toast.makeText(this, "permissions granted", Toast.LENGTH_SHORT).show()
//            Iniciar la busqueda automaticamente al recibir permisos
            discoverPeers()
        } else {
            Toast.makeText(this, "permission are needed to search for peers", Toast.LENGTH_SHORT)
                .show()
        }
    }
//    ConnectivityManager
//    private val connectivityManager: ConnectivityManager by lazy {
//        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//    }
//    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
//        override fun onAvailable(network: Network) {
//            super.onAvailable(network)
//            manager?.requestConnectionInfo(channel) { info ->
//                viewModel.updateConnectionInfo(info)
//                if (info.groupFormed) {
//                    if (info.isGroupOwner) {
//                        chatManager.startServer()
//                    } else {
//                        chatManager.startClient(info.groupOwnerAddress.hostAddress)
//                    }
//                }
//            }
//        }
//        override fun onLost(network: Network) {
//            super.onLost(network)
//            viewModel.updateConnectionInfo(null)
//        }
//    }
//    Chat manager
    private lateinit var chatManager: WifiP2pChatManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatManager = WifiP2pChatManager(viewModel)
        // initiate channel and receiver
        channel = manager?.initialize(this, mainLooper, null)
        channel?.also { channel ->
            receiver = WifiP2pBroadcastReceiver(manager, channel, viewModel, this, chatManager)
        }
        checkAndRequestPermissions()
        enableEdgeToEdge()
        setContent {
            SendMessagePrototypeTheme {
                SendMessagePrototypeApp(
                    viewModel = viewModel,
                    onDiscoverPeers = { checkAndRequestPermissions() },
                    onConnect = { device -> connectToDevice(device) },
                    onSendMessage = { chatManager.sendMessage("Test") }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
//         Starting the BroadcastReceiver
        receiver?.also { registerReceiver(it, intentFilter) }
//        val request = NetworkRequest.Builder()
//            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
//            .addCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P)
//            .build()
//        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun onPause() {
        super.onPause()
        receiver?.also { unregisterReceiver(it) }
//        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    @RequiresPermission(anyOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ])
    private fun discoverPeers() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(
                    this@MainActivity,
                    "Searching for peers (on success)",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onFailure(reasonCode: Int) {
                Toast.makeText(
                    this@MainActivity,
                    "Searching for peers (on failure)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Permissions for android 13+
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            // permissions for android 12 and lower
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missingPermissions = permissionsToRequest.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            discoverPeers()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    @RequiresPermission(anyOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ])
    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(
                    this@MainActivity,
                    "Connected to ${device.deviceName}, ${device.deviceAddress}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            override fun onFailure(reason: Int) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to connect to ${device.deviceName}. Reason: $reason",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}

//@PreviewScreenSizes
@Composable
fun SendMessagePrototypeApp(
    viewModel: WifiP2pViewModel,
    onDiscoverPeers: () -> Unit = {},
    onConnect: (WifiP2pDevice) -> Unit,
    onSendMessage: () -> Unit,
) {
//    Menu navegation detection
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
//    Peers
    val peers by viewModel.peers.collectAsState()
    val selectedPeer = viewModel.selectedPeer
//    UI: detect keyboard
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
//    Send data
    val messages by viewModel.messages.collectAsState()

    NavigationSuiteScaffold(
        layoutType = if (isKeyboardOpen) NavigationSuiteType.None else NavigationSuiteType.NavigationBar,
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize(),
//            contentWindowInsets = WindowInsets(0, 8.dp, 0, 0)
        ) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> {
                    Greeting(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .imePadding(),
                        peers = peers,
                        selectedPeer = selectedPeer,
                        onPeerClick = { device ->
                            viewModel.selectPeer(device)
                            onConnect(device)
                        },
                        onDiscoverPeers = onDiscoverPeers,
                        messages = messages,
                        onSendMessage = onSendMessage
                    )
                }

                AppDestinations.FAVORITES -> {
//                    Favorites(innerPadding)
                }

                AppDestinations.PROFILE -> {
//                    Profile(innerPadding)
                }
            }

        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Messages", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    peers: List<WifiP2pDevice>,
    selectedPeer: WifiP2pDevice?,
    onPeerClick: (WifiP2pDevice) -> Unit,
    onDiscoverPeers: () -> Unit = {},
    messages: List<Message>,
    onSendMessage: () -> Unit,
) {
    var messageText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WiChat",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onDiscoverPeers) {
                Text("Discover peers")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "Available Peers to connect (${peers.size})",
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(peers) { device ->
                    Text(
                        text = if (device.deviceName.isNullOrEmpty()) "Unnamed device" else device.deviceName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPeerClick(device) }
                            .padding(8.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
//                    .fillMaxSize()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Selected peer: ${selectedPeer?.deviceName ?: "None"}",
//                    text = "No peers found",
//                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.titleMedium
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
//                Text(
//                    text = "Select user to start chating",
//                    modifier = Modifier.align(Alignment.Center),
//                    style = MaterialTheme.typography.bodyMedium
//                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
//                        .weight(1f)
                ) {
                    items(messages) { message ->
                        Text(
                            text = "${if (message.isMine) "Me: " else "Peer: "}${message.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type message...") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onSendMessage()
                        messageText = ""
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SendMessagePrototypeTheme {
        Greeting(
            peers = emptyList(),
            selectedPeer = null,
            onPeerClick = {},
            onDiscoverPeers = {},
            messages = emptyList(),
            onSendMessage = {}
        )
    }
}