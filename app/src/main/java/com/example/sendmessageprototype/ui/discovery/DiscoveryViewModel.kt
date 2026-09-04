package com.example.sendmessageprototype.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sendmessageprototype.core.DiscoveredPeer
import com.example.sendmessageprototype.domain.ChatSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DiscoveryViewModel(
    private val session: ChatSession
) : ViewModel() {
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = session.discoverPeers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun connectToDevice(address: String) {
        session.connectToDevice(address)
    }

    fun refreshScan() {
        session.discoverPeers()
    }

    class Factory(private val session: ChatSession) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiscoveryViewModel(session) as T
        }
    }
}