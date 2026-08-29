package com.example.sendmessageprototype.core

class Conversation(
    val conversationID: String,
    val peerID: String,
    private val messages: MutableMap<String, Message> = mutableMapOf(),
    var lastMessageAt: Long = 0L,
) {
    fun addMessage(message: Message) {
        messages[message.getMessageID()] = message
        updateLastMessageAt(message.getTimestamp())
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

//    getters
    fun getConversationID(): String = conversationID
    fun getPeerID(): String = peerID
    fun getMessages(): Map<String, Message> = messages
    fun getLastMessageAt(): Long = lastMessageAt

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