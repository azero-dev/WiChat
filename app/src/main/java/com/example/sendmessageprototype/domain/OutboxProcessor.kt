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
    private val MAX_LIFETIME_MS = 24 * 60 * 60 * 1000
    private val MAX_TRIES = 10

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
                    createdAt = entity.createdAt,
                    alreadySentTo = Json.decodeFromString<MutableSet<String>>(entity.alreadySentTo)
                )
                messages[entity.messageID] = envelope
            }
        }
    }

    suspend fun enqueue(message: Message) {
        val envelope = MessageInTransit(
            messageID = message.messageID,
            payload = message,
            ttl = 20,
        )
        messages[message.messageID] = envelope
        outboxDAO.save(envelope.toEntity())
    }

    fun enqueueRelay(envelope: MessageInTransit) {
        relayMessages[envelope.messageID] = envelope
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
        val now = System.currentTimeMillis()
//        drop expired messages
        messages.values.filter { now - it.createdAt > MAX_LIFETIME_MS }.forEach { expired ->
            handlePermanentFailure(expired)
        }
        val allPending = (messages.values + relayMessages.values).toList()
        allPending.forEach { envelope ->
            if (shouldAttemptSend(envelope, connectedUserID)) {
                val result = transport.send(envelope)
                when (result) {
                    is SendResult.Success -> {
                        envelope.alreadySentTo.add(connectedUserID)
                        if (messages.containsKey(envelope.messageID)) {
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
        if (envelope.alreadySentTo.contains(connectedUserID)) return false
        val now = System.currentTimeMillis()
        val waitTime = 1000L * (envelope.retryCounter +1)
        if (now - envelope.lastAttemptAt < waitTime && envelope.payload.type == MessageType.TEXT) {
            return false
        }
        return true
    }

    private suspend fun handlePermanentFailure(envelope: MessageInTransit) {
        remove(envelope.messageID)
        conversations.updateMessageState(
            envelope.messageID,
            envelope.payload.conversationID,
            MessageState.FAILED
        )
    }

    private suspend fun handleSendError(envelope: MessageInTransit) {
        envelope.recordFailedAttempt()
        if (envelope.retryCounter >= MAX_TRIES) {
            remove(envelope.messageID)
            conversations.updateMessageState(
                envelope.messageID,
                envelope.payload.conversationID,
                MessageState.FAILED
            )
        } else {
            if (messages.containsKey(envelope.messageID)) {
                outboxDAO.update(envelope.toEntity())
            }
        }
    }

    fun getMessageInTransit(messageID: String): MessageInTransit? {
        return messages[messageID]
    }

    private fun MessageInTransit.toEntity() = OutboxEntity(
        messageID = this.messageID,
        ttl = this.ttl,
        retryCounter = this.retryCounter,
        lastAttemptAt = this.lastAttemptAt,
        createdAt = this.createdAt,
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