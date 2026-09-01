package com.example.sendmessageprototype.domain

import com.example.sendmessageprototype.core.Message
import com.example.sendmessageprototype.core.MessageInTransit
import com.example.sendmessageprototype.core.MessageState
import com.example.sendmessageprototype.core.MessageType
import com.example.sendmessageprototype.core.User

class IncomingMessageHandler(
    private val cache: SeenMessagesCache,
    private val peers: PeersManager,
    private val conversations: ConversationsManager,
    private val outbox: OutboxProcessor,
    private val localUser: User,
) {
    suspend fun handleIncoming(envelope: MessageInTransit, fromPeer: String) {
        val payload = envelope.getPayload()
        if (!cache.markIfNew(envelope.getMessageID())) {
            if (payload.getType() == MessageType.TEXT && isAddressedToLocalUser(envelope)) {
                sendAck(payload)
            }
            return
        }
        when (payload.getType()) {
            MessageType.ACK -> handleAck(payload)
            MessageType.TEXT -> handleText(envelope, fromPeer)
            MessageType.IDENTITY -> {}
        }
    }

    private fun isAddressedToLocalUser(envelope: MessageInTransit): Boolean {
        return envelope.getPayload().getReceiverID() == localUser.userID
    }

    private suspend fun handleText(envelope: MessageInTransit, fromPeer: String) {
        val payload = envelope.getPayload()
        if (!peers.isSaved(payload.getSenderID())) return
        if (isAddressedToLocalUser(envelope)) {
            conversations.addMessage(payload)
            sendAck(payload)
        } else {
            relayMessage(envelope, fromPeer)
        }
    }

    private suspend fun handleAck(ackPayload: Message) {
        val ackedMessageID = String(ackPayload.getContent())
        val removedEnvelope = outbox.remove(ackedMessageID)
        removedEnvelope?.let { envelope ->
            conversations.updateMessageState(
                messageID = ackedMessageID,
                conversationID = envelope.getPayload().getConversationID(),
                newState = MessageState.DELIVERED
            )
        }
    }

    private fun relayMessage(envelope: MessageInTransit, fromPeer: String) {
        if (envelope.getTtl() <= 0) return
        envelope.decrementTtl()
        envelope.getAlreadySentTo().add(fromPeer)
        envelope.getAlreadySentTo().add(localUser.userID)
        outbox.enqueue(envelope)
    }

    private fun sendAck(originalMessage: Message) {
        val ack = Message(
            type = MessageType.ACK,
            conversationID = originalMessage.getConversationID(),
            senderID = localUser.userID,
            receiverID = originalMessage.getSenderID(),
            content = originalMessage.getMessageID().toByteArray(),
            state = MessageState.SENDING,
        )
        outbox.enqueue(ack)
    }
}