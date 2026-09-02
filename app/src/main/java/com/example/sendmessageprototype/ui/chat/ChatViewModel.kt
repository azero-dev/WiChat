package com.example.sendmessageprototype.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sendmessageprototype.domain.ChatSession
import com.example.sendmessageprototype.persistence.Converters
import com.example.sendmessageprototype.persistence.MessageDAO
import com.example.sendmessageprototype.persistence.MessageEntity
import kotlinx.coroutines.flow.Flow

class ChatViewModel(
    private val session: ChatSession,
    private val messageDAO: MessageDAO,
    val conversationID: String,
) : ViewModel() {
    val messages: Flow<List<MessageEntity>> = messageDAO.getMessagesOf(conversationID)

    fun sendMessage(text: String, receiverID: String) {
        if (text.isNotBlank()) {
            session.sendMessage(text, receiverID)
        }
    }

    class Factory(
        private val session: ChatSession,
        private val messageDAO: MessageDAO,
        private val conversationID: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(session, messageDAO, conversationID) as T
        }
    }
}