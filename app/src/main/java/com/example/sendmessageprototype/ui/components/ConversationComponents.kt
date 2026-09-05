package com.example.sendmessageprototype.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sendmessageprototype.core.PeerStatus

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

