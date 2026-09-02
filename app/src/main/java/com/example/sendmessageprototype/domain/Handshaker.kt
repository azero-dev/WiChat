package com.example.sendmessageprototype.domain

import com.example.sendmessageprototype.core.IdentityData
import com.example.sendmessageprototype.core.Message
import com.example.sendmessageprototype.core.MessageInTransit
import com.example.sendmessageprototype.core.MessageType
import com.example.sendmessageprototype.core.User
import com.example.sendmessageprototype.transport.WiFiDirectTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

class Handshaker(
    private val transport: WiFiDirectTransport,
    private val localUser: User,
    private val peers: PeersManager,
) {
    private val _identities = MutableSharedFlow<User>()
    fun identities(): Flow<User> = _identities.asSharedFlow()

    suspend fun sendIdentity() {
        val identity = IdentityData(
            userID = localUser.userID,
            userName = localUser.userName,
        )
        val message = Message(
            type = MessageType.IDENTITY,
            conversationID = "SYSTEM",
            senderID = localUser.userID,
            receiverID = "BROADCAST",
            content = Json.encodeToString(identity).toByteArray(),
        )
        val envelope = MessageInTransit(
            messageID = message.getMessageID(),
            payload = message,
            ttl = 1,
        )
        transport.send(envelope)
    }

    suspend fun handleIncomingIdentity(data: ByteArray, fromDevice: String) {
        try {
            val identity = Json.decodeFromString<IdentityData>(String(data))
            if (identity.userID == localUser.userID) return
            val peer = User(
                userID = identity.userID,
                userName = identity.userName,
                lastKnownDeviceAddress = fromDevice,
            )
            if (!peers.isSaved(peer.userID)) {
                peers.addSavedPeer(peer)
            } else {
                peers.updatePeerName(peer.userID, peer.userName)
            }
            peers.bind(peer.userID, fromDevice)
            _identities.emit(peer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}