package com.example.sendmessageprototype

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
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

    // Request permissions from user
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "permissions granted", Toast.LENGTH_SHORT).show()
            // Iniciar la busqueda automaticamente al recibir permisos
            discoverPeers()
        } else {
            Toast.makeText(this, "permission are needed to search for peers", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // initiate channel and receiver
        channel = manager?.initialize(this, mainLooper, null)
        channel?.also { channel ->
            receiver = WifiP2pBroadcastReceiver(manager, channel, viewModel, this)
        }
        checkAndRequestPermissions()
        // Dejar registerReceiver aqui ademas del de onResume?
        // registerReceiver(receiver, intentFilter)
        enableEdgeToEdge()
        setContent {
            SendMessagePrototypeTheme {
                SendMessagePrototypeApp(
                    viewModel = viewModel,
                    onDiscoverPeers = { checkAndRequestPermissions() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Starting the BroadcastReceiver
        receiver?.also { receiver ->
            registerReceiver(receiver, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        receiver?.also { receiver ->
            unregisterReceiver(receiver)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
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
}

//@PreviewScreenSizes
@Composable
fun SendMessagePrototypeApp(
    viewModel: WifiP2pViewModel,
    onDiscoverPeers: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val peers by viewModel.peers.collectAsState()
    val selectedPeer = viewModel.selectedPeer

    NavigationSuiteScaffold(
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
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                        onPeerClick = { device -> viewModel.selectPeer(device)},
                        onDiscoverPeers = onDiscoverPeers
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
    onDiscoverPeers: () -> Unit = {}
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
                Text(
                    text = "Select user to start chating",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )
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
//                        TODO: Send message to selected peer
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
            onPeerClick = {}
        )
    }
}