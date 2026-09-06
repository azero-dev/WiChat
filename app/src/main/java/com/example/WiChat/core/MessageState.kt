package com.example.WiChat.core

import kotlinx.serialization.Serializable

@Serializable
enum class MessageState {
    SENDING,
    DELIVERED,
    FAILED
}