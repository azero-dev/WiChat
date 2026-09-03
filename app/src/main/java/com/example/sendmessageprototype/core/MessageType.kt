package com.example.sendmessageprototype.core

import kotlinx.serialization.Serializable

@Serializable
enum class MessageType {
    TEXT,
    ACK,
    IDENTITY
}