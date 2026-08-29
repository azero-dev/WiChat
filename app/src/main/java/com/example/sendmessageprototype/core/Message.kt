package com.example.sendmessageprototype.core

import java.util.UUID

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

//    getters
    fun getMessageID(): String = messageID
    fun getType(): MessageType = type
    fun getConversationID(): String = conversationID
    fun getSenderID(): String = senderID
    fun getReceiverID(): String = receiverID
    fun getTimestamp(): Long = timestamp
    fun getContent(): ByteArray = content
    fun getState(): MessageState = state

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