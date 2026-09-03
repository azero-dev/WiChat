package com.example.sendmessageprototype.core

import kotlinx.serialization.Serializable

@Serializable
enum class MessageState {
    SENDING,
    DELIVERED,
    FAILED
}