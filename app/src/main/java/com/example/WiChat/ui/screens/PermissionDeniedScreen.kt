package com.example.WiChat.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.WiChat.ui.components.FullscreenMessage

@Composable
fun PermissionDeniedScreen(onRetry: () -> Unit) {
    FullscreenMessage(
        title = "Permissions required",
        subtitle = "WiChat needs Wifi and location permissions to function properly.",
        icon = Icons.Default.Security,
        iconColor = MaterialTheme.colorScheme.error,
        buttonText = "Grant permissions",
        onButtonClick = onRetry,
    )
}