package com.example.sendmessageprototype.core

class Conversation(
    val conversationID: String,
    val peerID: String,
    val messages: MutableMap<String, Message> = mutableMapOf(),
    var lastMessageAt: Long = 0L,
) {
    fun addMessage(message: Message) {
        messages[message.messageID] = message
        updateLastMessageAt(message.timestamp)
    }

    fun removeMessage(messageID: String) {
        messages.remove(messageID)
    }

    private fun updateLastMessageAt(timestamp: Long) {
        if (timestamp > lastMessageAt) {
            lastMessageAt = timestamp
        }
    }
    fun updateMessageState(messageID: String, newState: MessageState) {
        messages[messageID]?.changeState(newState)
    }

//    overrides
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Conversation) return false
        return conversationID == other.conversationID
    }

    override fun hashCode(): Int {
        return conversationID.hashCode()
    }

    override fun toString(): String {
        return "Conversation(id='$conversationID', peer='$peerID', messageCount=${messages.size})"
    }
}