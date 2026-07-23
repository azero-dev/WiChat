package com.example.sendmessageprototype

class Message(
    val content: String,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
}