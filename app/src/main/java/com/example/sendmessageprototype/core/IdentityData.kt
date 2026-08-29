package com.example.sendmessageprototype.core

data class IdentityData(
    val userID: String,
    val userName: String,
    val protocolVersion: Int = 1,
)