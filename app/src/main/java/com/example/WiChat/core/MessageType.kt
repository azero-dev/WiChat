package com.example.WiChat.core

import kotlinx.serialization.Serializable

@Serializable
enum class MessageType {
    TEXT,
    ACK,
    IDENTITY
}