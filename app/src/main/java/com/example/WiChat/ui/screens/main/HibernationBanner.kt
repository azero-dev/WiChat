package com.example.WiChat.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.WiChat.ui.components.FullscreenMessage

@Composable
fun HibernationBanner(onProfileClick: () -> Unit) {
    FullscreenMessage(
        title = "WiChat is hibernating",
        subtitle = "Network activity is paused and WiChat is off.",
        icon = Icons.Default.PowerSettingsNew,
        buttonText = "Open profile settings",
        onButtonClick = onProfileClick,
    )
}




















