package com.example.sendmessageprototype

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiP2pViewModel : ViewModel() {
//    List of devices found
    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()
//    Device selected for chat
    var selectedPeer by mutableStateOf<WifiP2pDevice?>(null)
    private set
//    Connection info
    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()
//    data transfer
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

//    Peer managing
    fun updatePeerList(newList: List<WifiP2pDevice>) {
        _peers.value = newList
    }
    fun selectPeer(device: WifiP2pDevice) {
        selectedPeer = device
    }

//    Connection managing
    fun updateConnectionInfo(info: WifiP2pInfo?) {
        _connectionInfo.value = info
    }

//    Data transfer manager
    fun addMessage(message: Message) {
        _messages.value += message
    }
}