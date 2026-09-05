package com.example.sendmessageprototype.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

data class SheetAction(
    val icon : ImageVector,
    val label: String,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)
