package com.example.sendmessageprototype.core

import kotlinx.serialization.Serializable

@Serializable
data class IdentityData(
    val userID: String,
    val userName: String,
    val protocolVersion: Int = 1,
)