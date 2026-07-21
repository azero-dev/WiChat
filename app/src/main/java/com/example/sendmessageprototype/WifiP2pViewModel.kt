package com.example.sendmessageprototype

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiP2pViewModel : ViewModel() {
//    Lista de devices encontrados
    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

//    dispositivo seleccionado para chat
    var selectedPeer by mutableStateOf<WifiP2pDevice?>(null)
    private set

    fun updatePeerList(newList: List<WifiP2pDevice>) {
        _peers.value = newList
    }

    fun selectPeer(device: WifiP2pDevice) {
        selectedPeer = device
    }
}