package com.example.sendmessageprototype.transport

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import com.example.sendmessageprototype.core.DiscoveredPeer
import com.example.sendmessageprototype.core.MessageInTransit
import com.example.sendmessageprototype.core.SendResult
import com.example.sendmessageprototype.core.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.handleCoroutineException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.coroutines.resume
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

@SuppressLint("MissingPermission")
class WiFiDirectTransport(
    private val context: Context,
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val PORT = 8880
    private var activePort: Int = 8880
    private val PORT_RANGE = 60
    private val _events = MutableSharedFlow<TransportEvent>()
    fun events(): Flow<TransportEvent> = _events.asSharedFlow()
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private var isGroupOwner: Boolean = false
    private var connectedGroupOwnerAddress: String? = null
    private var remoteClientIP: String? = null
    private var remotePeerMac: String? = null
    private var serverSocket: ServerSocket? = null
    private var receiver: WifiDirectBroadcastReceiver? = null
    private var lastConnectionIsPersistent: Boolean = false

    init {
        startSocketServer()
        registerReceiver()
    }

    fun discoverPeers(): Flow<List<DiscoveredPeer>> {
        manager.discoverPeers(channel, null)
        return _discoveredPeers.asStateFlow()
    }

    fun connect(deviceAddress: String, onFailure: (() -> Unit)? = null) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
            }
            override fun onFailure(reason: Int) {
                onFailure?.invoke()
            }
        })
    }

    fun disconnect() {
        val macToDisconnect = remotePeerMac ?: "unknown"
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        })
        closeResources()
        remotePeerMac = null
        remoteClientIP = null
        connectedGroupOwnerAddress = null
        lastConnectionIsPersistent = false
//        notify chatsession to clean
        scope.launch {
            _events.emit(TransportEvent.PeerDisconnected(macToDisconnect))
        }
    }

    fun cancelConnect() {
        manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { }
            override fun onFailure(reason: Int) { }
        })
        disconnect()
    }

    private fun closeResources() {
        try {
            serverSocket?.close()
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun send(envelope: MessageInTransit): SendResult = withContext(Dispatchers.IO) {
        val targetIP = if (isGroupOwner) remoteClientIP else connectedGroupOwnerAddress
        if (targetIP == null) return@withContext SendResult.NotConnected
        for (port in PORT until PORT + PORT_RANGE) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetIP, port), 5000)
                    val output = DataOutputStream(socket.getOutputStream())
                    val data = Json.encodeToString(envelope).toByteArray()
                    output.writeInt(data.size)
                    output.write(data)
                    output.flush()
                    return@withContext SendResult.Success(remotePeerMac?: "unknown")
                }
            } catch (e: Exception) {
//                if port is busy, try next port
            }
        }
//        if connection not successfull, then disconnect
        disconnect()
        SendResult.Error
    }

    private fun startSocketServer() {
        scope.launch {
            var port = 8880
            while (port < PORT + PORT_RANGE) {
                try {
                    val ss = ServerSocket(port)
                    serverSocket = ss
                    activePort = port
                    while (isActive) {
                        val client = ss.accept()
                        handleIncomingConnection(client)
                    }
                    break
                } catch (e: Exception) {
                    port++
                }
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        scope.launch {
            try {
                if (isGroupOwner) {
                    remoteClientIP = socket.inetAddress.hostAddress
                } else {
                    connectedGroupOwnerAddress = socket.inetAddress.hostAddress
                }
                socket.use { sock ->
                    val input = DataInputStream(sock.getInputStream())
                    val length = input.readInt()
                    if (length > 0) {
                        val buffer = ByteArray(length)
                        input.readFully(buffer)
                        val envelope = Json.decodeFromString<MessageInTransit>(String(buffer))
                        _events.emit(TransportEvent.EnvelopeReceived(envelope, remotePeerMac ?: "unknown"))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        receiver = WifiDirectBroadcastReceiver { intent ->
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager.requestPeers(channel) { peers ->
                        _discoveredPeers.value = peers.deviceList.map {
                            DiscoveredPeer(it.deviceAddress, it.deviceName)
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }
                    if (info?.isConnected == true) {
                        manager.requestConnectionInfo(channel) { connectionInfo ->
                            isGroupOwner = connectionInfo.isGroupOwner
                            connectedGroupOwnerAddress = connectionInfo.groupOwnerAddress.hostAddress
                            manager.requestGroupInfo(channel) { group ->
                                if (group != null) {
                                    try {
                                        val isPersistentMethod = group.javaClass.getMethod("isPersistent")
                                        lastConnectionIsPersistent = isPersistentMethod.invoke(group) as Boolean
                                    } catch (e: Exception) {
                                        lastConnectionIsPersistent = false
                                    }
                                    remotePeerMac = if (isGroupOwner) {
                                        group.clientList?.firstOrNull()?.deviceAddress
                                    } else {
                                        group.owner?.deviceAddress
                                    }
                                    remotePeerMac?.let { mac ->
                                        scope.launch {
                                            _events.emit(TransportEvent.PeerConnected(mac))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val oldMac = remotePeerMac
                        remotePeerMac = null
                        remoteClientIP = null
                        oldMac?.let { scope.launch { _events.emit(TransportEvent.PeerDisconnected(it)) }}
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

//    getters
    fun isCurrentConnectionPersistent(): Boolean = lastConnectionIsPersistent
    fun connectedDevice(): String? = remotePeerMac
}