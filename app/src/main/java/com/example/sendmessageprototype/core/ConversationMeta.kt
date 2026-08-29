package com.example.sendmessageprototype.core

data class ConversationMeta(
    val conversationID: String,
    val peerID: String,
    val lastMessageAt: Long,
)
