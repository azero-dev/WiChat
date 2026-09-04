package com.example.sendmessageprototype.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sendmessageprototype.core.PeerStatus
import com.example.sendmessageprototype.domain.ChatSession
import com.example.sendmessageprototype.persistence.Converters
import com.example.sendmessageprototype.persistence.MessageDAO
import com.example.sendmessageprototype.persistence.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChatViewModel(
    val session: ChatSession,
    private val messageDAO: MessageDAO,
    val conversationID: String,
) : ViewModel() {
    val messages: Flow<List<MessageEntity>> = messageDAO.getMessagesOf(conversationID)

    private val peerID: String = conversationID.split("_")
        .firstOrNull { it != session.localUser?.userID } ?: ""

    val peerStatus: StateFlow<PeerStatus> = session.getPeerStatus(peerID)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PeerStatus.ABSENT
        )

    fun sendMessage(text: String) {
        if (text.isNotBlank() && peerID.isNotEmpty()) {
            session.sendMessage(text, peerID)
        }
    }

    fun connectManually() {
        if (peerID.isNotEmpty()) {
            session.requestChatConnection(peerID)
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