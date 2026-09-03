package com.example.sendmessageprototype.domain

import com.example.sendmessageprototype.core.ConversationMeta
import com.example.sendmessageprototype.core.DiscoveredPeer
import com.example.sendmessageprototype.core.Message
import com.example.sendmessageprototype.core.MessageType
import com.example.sendmessageprototype.core.TransportEvent
import com.example.sendmessageprototype.core.User
import com.example.sendmessageprototype.persistence.MessageDAO
import com.example.sendmessageprototype.persistence.OutboxDAO
import com.example.sendmessageprototype.persistence.UserDAO
import com.example.sendmessageprototype.persistence.UserEntity
import com.example.sendmessageprototype.transport.WiFiDirectTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.UUID

class ChatSession(
    private val transport: WiFiDirectTransport,
    private val userDAO: UserDAO,
    private val messageDAO: MessageDAO,
    private val outboxDAO: OutboxDAO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    sealed class SessionState {
        object Loading : SessionState()
        object IdentityRequired : SessionState()
        data class Ready(val localUser: User) : SessionState()
    }
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private var localUser: User? = null
    private var peersManager: PeersManager? = null
    private var conversationsManager: ConversationsManager? = null
    private var outbox: OutboxProcessor? = null
    private var incoming: IncomingMessageHandler? = null
    private var handshaker: Handshaker? = null
    private var cache = SeenMessagesCache()
    private var discoveryJob: Job? = null
    private var activeUserConnection: Boolean = false
    private val _connectingAddress = MutableStateFlow<String?>(null)
    val connectingAddress: StateFlow<String?> = _connectingAddress.asStateFlow()

    fun start() {
        scope.launch {
            val entity = userDAO.getLocalUser()
            if (entity != null) {
                setupFullSession(entity.toDomain())
            } else {
                _state.value = SessionState.IdentityRequired
            }
        }
    }

    fun stop() {
        stopDiscoveryCycle()
        transport.disconnect()
        scope.cancel()
    }

    fun initialiseIdentity(userName: String) {
        val newUser = User(
            userID = UUID.randomUUID().toString(),
            userName = userName,
        )
        scope.launch {
            userDAO.save(newUser.toEntity(isLocal = true))
            setupFullSession(newUser)
        }
    }

    private suspend fun setupFullSession(user: User) {
        localUser = user
        val pm = PeersManager(userDAO, user.userID)
        val cm = ConversationsManager(messageDAO, user.userID)
        val op = OutboxProcessor(transport, pm, outboxDAO, messageDAO, cm)
        peersManager = pm
        conversationsManager = cm
        outbox = op
        incoming = IncomingMessageHandler(cache, pm, cm, op, user)
        handshaker = Handshaker(transport, user, pm)
        pm.loadSavedPeers()
        op.loadPending()
        launchEventCollectors()
        _state.value = SessionState.Ready(user)
        startDiscoveryCycle()
    }

    private fun launchEventCollectors() {
        transport.events()
            .onEach { event -> onTransportEvent(event) }
            .launchIn(scope)
        handshaker?.identities()
            ?.onEach { user -> onPeerIdentified(user) }
            ?.launchIn(scope)
    }

    private suspend fun onTransportEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.PeerConnected -> {
                handshaker?.sendIdentity()
                peersManager?.userIDOf(event.deviceAddress)?.let { userID ->
                    peersManager?.addReachablePeer(userID)
                    _connectingAddress.value = null
                }
            }
            is TransportEvent.PeerDisconnected -> {
                peersManager?.userIDOf(event.deviceAddress)?.let {
                    peersManager?.removeReachablePeer(it)
                }
                peersManager?.unbind(event.deviceAddress)
                activeUserConnection = false
                _connectingAddress.value = null
            }
            is TransportEvent.EnvelopeReceived -> {
                val fromDevice = event.fromDevice
                if(event.envelope.payload.type == MessageType.IDENTITY) {
                    handshaker?.handleIncomingIdentity(
                        event.envelope.payload.content,
                        fromDevice,
                    )
                    scope.launch { handshaker?.sendIdentity() }
                } else {
                    peersManager?.userIDOf(fromDevice)?.let { userID ->
                        incoming?.handleIncoming(event.envelope, userID)
                    }
                }
            }
        }
    }

    fun discoverPeers(): Flow<List<DiscoveredPeer>> = transport.discoverPeers()

    fun connectToDevice(deviceAddress: String) {
        activeUserConnection = true
        _connectingAddress.value = deviceAddress
        transport.connect(deviceAddress, onFailure = {
            _connectingAddress.value = null
            activeUserConnection = false
        })
    }

    private fun onPeerIdentified(user: User) {
        peersManager?.addReachablePeer(user.userID)
        _connectingAddress.value = null
        scope.launch {
            val isPersistent = transport.isCurrentConnectionPersistent()
            peersManager?.updateIsPersistent(user.userID, isPersistent)
            outbox?.trySend()
        }
    }

    fun startDiscoveryCycle() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            while (isActive) {
                if (!activeUserConnection && transport.connectedDevice() == null) {
                    performDiscoveryTick()
                }
                delay(30000)
            }
        }
    }

    fun stopDiscoveryCycle() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    private suspend fun performDiscoveryTick() {
        val discoveredList = transport.discoverPeers().firstOrNull() ?: return
        for (discovered in discoveredList) {
            val savedUser = peersManager?.savedByAddress(discovered.deviceAddress)
            if (savedUser != null && savedUser.isPersistent) {
                transport.connect(discovered.deviceAddress)
                break
            }
        }
    }

    fun sendMessage(content: String, receiverID: String) {
        val user = localUser ?: return
        val convID = generateConversationID(user.userID, receiverID)
        val message = Message(
            type = MessageType.TEXT,
            conversationID = convID,
            senderID = user.userID,
            receiverID = receiverID,
            content = content.toByteArray(),
        )
        scope.launch {
            conversationsManager?.addMessage(message)
            outbox?.enqueue(message)
            outbox?.trySend()
        }
    }

    private fun generateConversationID(userA: String, userB: String): String {
        return if (userA < userB) "${userA}_${userB}" else "${userB}_${userA}"
    }

    fun getConversationMetas(): Flow<List<ConversationMeta>> = conversationsManager?.getConversationMetas() ?: flowOf(emptyList())
    fun getSavedPeers(): StateFlow<Set<User>> = peersManager?.savedPeers ?: MutableStateFlow(emptySet())

//    identity
    private fun UserEntity.toDomain() = User(userID, userName, createdAt, lastKnownDeviceAddress, isPersistent)
    private fun User.toEntity(isLocal: Boolean) = UserEntity(userID, userName, createdAt, lastKnownDeviceAddress, isLocal, isPersistent)
}