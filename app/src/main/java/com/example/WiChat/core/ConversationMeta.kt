package com.example.WiChat.core

data class ConversationMeta(
    val conversationID: String,
    val peerID: String,
    val lastMessageAt: Long,
    val lastMessageText: ByteArray,
)
