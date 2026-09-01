package com.example.sendmessageprototype.domain

import com.example.sendmessageprototype.core.Conversation
import com.example.sendmessageprototype.core.Message
import com.example.sendmessageprototype.core.MessageInTransit
import com.example.sendmessageprototype.core.MessageState
import com.example.sendmessageprototype.core.MessageType
import com.example.sendmessageprototype.core.SendResult
import com.example.sendmessageprototype.persistence.MessageDAO
import com.example.sendmessageprototype.persistence.MessageEntity
import com.example.sendmessageprototype.persistence.OutboxDAO
import com.example.sendmessageprototype.persistence.OutboxEntity
import com.example.sendmessageprototype.transport.WiFiDirectTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class OutboxProcessor(
    private val transport: WiFiDirectTransport,
    private val peers: PeersManager,
    private val outboxDAO: OutboxDAO,
    private val messageDAO: MessageDAO,
    private val conversations: ConversationsManager,
) {
    private val messages = mutableMapOf<String, MessageInTransit>()
    private val relayMessages = mutableMapOf<String, MessageInTransit>()

    suspend fun loadPending() {
        val pendingEntities = outboxDAO.getAllPending()
        pendingEntities.forEach { entity ->
            val messageEntity = messageDAO.getMessageByID(entity.messageID)
            messageEntity?.let { mEntity ->
                val domainMessage = mEntity.toDomain()
                val envelope = MessageInTransit(
                    messageID = entity.messageID,
                    payload = domainMessage,
                    ttl = entity.ttl,
                    retryCounter = entity.retryCounter,
                    lastAttemptAt = entity.lastAttemptAt,
                    alreadySentTo = Json.decodeFromString<MutableSet<String>>(entity.alreadySentTo)
                )
                messages[entity.messageID] = envelope
            }
        }
    }

    suspend fun enqueue(message: Message) {
        val envelope = MessageInTransit(
            messageID = message.getMessageID(),
            payload = message,
            ttl = 20,
        )
        messages[message.getMessageID()] = envelope
        outboxDAO.save(envelope.toEntity())
    }

    fun enqueueRelay(envelope: MessageInTransit) {
        relayMessages[envelope.getMessageID()] = envelope
    }

    suspend fun remove(messageID: String): MessageInTransit? {
        val removed = messages.remove(messageID) ?: relayMessages.remove(messageID)
        if (removed != null) {
            outboxDAO.remove(messageID)
        }
        return removed
    }

    suspend fun trySend() {
        val deviceAddress = transport.connectedDevice() ?: return
        val connectedUserID = peers.userIDOf(deviceAddress) ?: return
        val allPending = (messages.values + relayMessages.values)
        allPending.forEach { envelope ->
            if (shouldAttemptSend(envelope, connectedUserID)) {
                val result = transport.send(envelope)
                when (result) {
                    is SendResult.Success -> {
                        envelope.getAlreadySentTo().add(connectedUserID)
                        if (messages.containsKey(envelope.getMessageID())) {
                            outboxDAO.update(envelope.toEntity())
                        }
                    }
                    is SendResult.Error -> handleSendError(envelope)
                    is SendResult.NotConnected -> return@forEach
                }
            }
        }
    }

    private fun shouldAttemptSend(envelope: MessageInTransit, connectedUserID: String): Boolean {
        if (envelope.getAlreadySentTo().contains(connectedUserID)) return false
        val now = System.currentTimeMillis()
        if (now - envelope.getLastAttemptAt() < 20000) return false
        return true
    }

    private suspend fun handleSendError(envelope: MessageInTransit) {
        envelope.recordFailedAttempt()
        if (envelope.getRetryCounter() >= 10) {
            remove(envelope.getMessageID())
            conversations.updateMessageState(
                envelope.getMessageID(),
                envelope.getPayload().getConversationID(),
                MessageState.FAILED
            )
        } else {
            if (messages.containsKey(envelope.getMessageID())) {
                outboxDAO.update(envelope.toEntity())
            }
        }
    }

    private fun MessageInTransit.toEntity() = OutboxEntity(
        messageID = this.messageID,
        ttl = this.ttl,
        retryCounter = this.retryCounter,
        lastAttemptAt = this.lastAttemptAt,
        alreadySentTo = Json.encodeToString(this.alreadySentTo)
    )

    private fun MessageEntity.toDomain() = Message(
        messageID = messageID,
        type = MessageType.valueOf(type),
        conversationID = conversationID,
        senderID = senderID,
        receiverID = receiverID,
        content = content,
        timestamp = timestamp,
        state = MessageState.valueOf(state),
    )
}