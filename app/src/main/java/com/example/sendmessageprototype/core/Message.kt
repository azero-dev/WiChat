package com.example.sendmessageprototype.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
class Message(
    val messageID: String = UUID.randomUUID().toString(),
    val type: MessageType,
    val conversationID: String,
    val senderID: String,
    val receiverID: String,
    val content: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    var state: MessageState = MessageState.SENDING
) {
    fun changeState(newState: MessageState) {
        val isValid = when (state) {
            MessageState.SENDING -> newState == MessageState.DELIVERED || newState == MessageState.FAILED
            MessageState.FAILED -> newState == MessageState.SENDING
            MessageState.DELIVERED -> false
        }
        if (isValid) {
            state = newState
        }
    }

//    overrides
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return messageID == other.messageID
    }

    override fun hashCode(): Int {
        return messageID.hashCode()
    }

    override fun toString(): String {
        return "Message(messageID='$messageID', type=$type, state=$state, from='$senderID', to='$receiverID', timestamp=$timestamp"
    }
}