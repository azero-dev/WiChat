package com.example.sendmessageprototype.ui.screens.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sendmessageprototype.core.DiscoveredPeer
import com.example.sendmessageprototype.domain.ChatSession
import com.example.sendmessageprototype.ui.components.BaseItemCard

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

@Composable
fun DiscoveryPeerCard(
    peer: DiscoveredPeer,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    BaseItemCard(
        title = if (isConnected) "Already connected" else "Device found",
        subtitle = "${peer.deviceName} (${peer.deviceAddress})",
        icon = if (isConnected) Icons.Default.DoneAll else Icons.Default.Wifi,
        backgroundColor = if
                                  (isConnected) MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
        trailingContent = {
            if (isConnected) Icon(Icons.Default.Check, contentDescription = null)
        }
    )
}

