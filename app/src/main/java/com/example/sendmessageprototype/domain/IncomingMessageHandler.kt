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
        val payload = envelope.payload
//        resend ack just in case previous one was lost
        if (!cache.markIfNew(envelope.messageID)) {
            if (payload.type == MessageType.TEXT && isAddressedToLocalUser(envelope)) {
                sendAck(payload)
            }
            return
        }
        when (payload.type) {
            MessageType.ACK -> handleAck(payload)
            MessageType.TEXT -> handleText(envelope, fromPeer)
            MessageType.IDENTITY -> {}
        }
    }

    private fun isAddressedToLocalUser(envelope: MessageInTransit): Boolean {
        return envelope.payload.receiverID == localUser.userID
    }

    private suspend fun handleText(envelope: MessageInTransit, fromPeer: String) {
        val payload = envelope.payload
        if (!peers.isSaved(payload.senderID)) return
        if (isAddressedToLocalUser(envelope)) {
            conversations.addMessage(payload)
            sendAck(payload)
        } else {
            relayMessage(envelope, fromPeer)
        }
    }

    private suspend fun handleAck(ackPayload: Message) {
        val ackedMessageID = String(ackPayload.content)
        val removedEnvelope = outbox.remove(ackedMessageID)
        removedEnvelope?.let { envelope ->
            conversations.updateMessageState(
                messageID = ackedMessageID,
                conversationID = envelope.payload.conversationID,
                newState = MessageState.DELIVERED
            )
        }
    }

    private suspend fun relayMessage(envelope: MessageInTransit, fromPeer: String) {
        if (envelope.ttl <= 0) return
        envelope.decrementTtl()
        envelope.alreadySentTo.add(fromPeer)
        envelope.alreadySentTo.add(localUser.userID)
        outbox.enqueueRelay(envelope)
    }

    private suspend fun sendAck(originalMessage: Message) {
        val ack = Message(
            type = MessageType.ACK,
            conversationID = originalMessage.conversationID,
            senderID = localUser.userID,
            receiverID = originalMessage.senderID,
            content = originalMessage.messageID.toByteArray(),
            state = MessageState.SENDING,
        )
        outbox.enqueue(ack)
        outbox.trySend()
    }
}