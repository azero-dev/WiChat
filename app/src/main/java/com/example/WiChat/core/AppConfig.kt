package com.example.WiChat.core

data class AppConfig(
    val notificationsEnabled: Boolean = false,
    val isInactiveMode: Boolean = false,
    val isAdvancedCleanupEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeout: Long = 60000L,
)
