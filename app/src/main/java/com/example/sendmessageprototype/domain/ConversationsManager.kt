package com.example.sendmessageprototype.domain

import com.example.sendmessageprototype.core.Conversation
import com.example.sendmessageprototype.core.ConversationMeta
import com.example.sendmessageprototype.core.Message
import com.example.sendmessageprototype.core.MessageState
import com.example.sendmessageprototype.persistence.MessageDAO
import com.example.sendmessageprototype.persistence.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConversationsManager(
    private val messageDAO: MessageDAO,
    private val localUserID: String,
) {
    private val _activeConversations = MutableStateFlow<Map<String, Conversation>>(emptyMap())
    val activeConversations: StateFlow<Map<String, Conversation>> = _activeConversations.asStateFlow()

    fun getOrCreate(conversationID: String, peerID: String): Conversation {
        val current = _activeConversations.value[conversationID]
        if (current != null) return current
        val newConversation = Conversation(conversationID, peerID)
        _activeConversations.value += (conversationID to newConversation)
        return newConversation
    }

    fun getConversationMetas(): Flow<List<ConversationMeta>> = messageDAO.getConversationMetas(localUserID)

    suspend fun addMessage(message: Message) {
        messageDAO.save(message.toEntity())
        val peerID = if (message.senderID == localUserID) {
            message.receiverID
        } else {
            message.senderID
        }
        val conv = getOrCreate(message.conversationID, peerID)
        conv.addMessage(message)
        _activeConversations.value = _activeConversations.value.toMap()
    }

    suspend fun updateMessageState(messageID: String, conversationID: String, newState: MessageState) {
        _activeConversations.value[conversationID]?.let { conv ->
            conv.updateMessageState(messageID, newState)
            conv.messages[messageID]?.let { updatedMessage ->
                messageDAO.update(updatedMessage.toEntity())
            }
        }
        _activeConversations.value = _activeConversations.value.toMap()
    }

    suspend fun removeConversation(conversationID: String) {
        messageDAO.removeConversation(conversationID)
        _activeConversations.value -= conversationID
    }

    private fun Message.toEntity() = MessageEntity(
        messageID = messageID,
        type = type.name,
        conversationID = conversationID,
        senderID = senderID,
        receiverID = receiverID,
        content = content,
        timestamp = timestamp,
        state = state.name,
    )
}