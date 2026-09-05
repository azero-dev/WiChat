package com.example.sendmessageprototype.domain

import com.example.sendmessageprototype.core.AppConfig
import com.example.sendmessageprototype.core.ConversationMeta
import com.example.sendmessageprototype.core.DiscoveredPeer
import com.example.sendmessageprototype.core.Message
import com.example.sendmessageprototype.core.MessageInTransit
import com.example.sendmessageprototype.core.MessageType
import com.example.sendmessageprototype.core.PeerStatus
import com.example.sendmessageprototype.core.TransportEvent
import com.example.sendmessageprototype.core.User
import com.example.sendmessageprototype.persistence.ConfigDAO
import com.example.sendmessageprototype.persistence.ConfigEntity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val configDAO: ConfigDAO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    sealed class SessionState {
        object Loading : SessionState()
        object IdentityRequired : SessionState()
        data class Ready(val localUser: User) : SessionState()
    }
    var localUser: User? = null
        private set
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()
    private var isStarted = false
    private var heartbeat: Job? = null
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
        if (isStarted) return
        isStarted = true
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
        stopHeartbeat()
        transport.disconnect()
        scope.cancel()
        isStarted = false
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
        configDAO.getConfig().onEach { entity ->
            entity?.let {
                _config.value = AppConfig(it.notificationsEnabled, it.isInactiveMode)
            }
        }.launchIn(scope)
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
                    scope.launch { outbox?.trySend() }
                }
            }
            is TransportEvent.PeerDisconnected -> {
                stopHeartbeat()
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
                    val wasReachable = peersManager?.userIDOf(fromDevice)?.let {
                        peersManager?.isReachable(it)
                    } ?: false
                    handshaker?.handleIncomingIdentity(
                        event.envelope.payload.content,
                        fromDevice,
                    )
//                    to avoid loops, only responds if first contact
                    if (!wasReachable) {
                        scope.launch { handshaker?.sendIdentity() }
                    }
                } else {
                    peersManager?.userIDOf(fromDevice)?.let { userID ->
                        incoming?.handleIncoming(event.envelope, userID)
                        scope.launch { outbox?.trySend() }
                    }
                }
            }
        }
    }

    fun discoverPeers(): Flow<List<DiscoveredPeer>> {
        stopDiscoveryCycle()
        return transport.discoverPeers()
    }

    fun connectToDevice(deviceAddress: String): Boolean {
//        checks if already connected
        if (transport.connectedDevice() == deviceAddress) {
            return false
        }
//        checks if handshake exists
        val existingUser = peersManager?.userIDOf(deviceAddress)
        if (existingUser != null && peersManager?.isReachable(existingUser) == true) {
            return false
        }
//        otherwise, connects
        activeUserConnection = true
        _connectingAddress.value = deviceAddress
        transport.connect(deviceAddress, onFailure = {
            _connectingAddress.value = null
            activeUserConnection = false
        })
        return true
    }

    fun cancelConnectAttempt() {
        transport.cancelConnect()
        activeUserConnection = false
        _connectingAddress.value = null
        startDiscoveryCycle()
    }

    fun getPeerStatus(userID: String): Flow<PeerStatus> = combine(
        peersManager!!.reachablePeers,
        transport.discoverPeers(),
    ) { reachableSet, discoveredList ->
        if (reachableSet.contains(userID)) return@combine PeerStatus.CONNECTED
        val user = peersManager?.savedPeers?.value?.find { it.userID == userID }
        val isNearby = discoveredList.any { peer ->
            peer.deviceAddress == user?.lastKnownDeviceAddress
        }
        if (isNearby) PeerStatus.NEARBY else PeerStatus.ABSENT
    }

    private fun onPeerIdentified(user: User) {
        startHeartbeat()
        scope.launch {
            outbox?.trySend()
            if (peersManager?.isReachable(user.userID) == false) {
                peersManager?.addReachablePeer(user.userID)
                val isPersistent = transport.isCurrentConnectionPersistent()
                peersManager?.updateIsPersistent(user.userID, isPersistent)
            }
            _connectingAddress.value = null
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

    private fun startHeartbeat() {
//        Wifi Direct seems unstable while not in use
//        this may help to keep connected
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive) {
                delay(20000)
                if (transport.connectedDevice() != null) {
                    handshaker?.sendIdentity()
                } else {
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeat?.cancel()
        heartbeat = null
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

    suspend fun deleteMessageLocal(messageID: String) {
        outbox?.remove(messageID)
        messageDAO.remove(messageID)
    }

//    used to retrieve info like ttl for the message info panel
    fun getMessageInTransit(messageID: String): MessageInTransit? {
        return outbox?.getMessageInTransit(messageID)
    }

    fun requestChatConnection(peerID: String) {
        if (peersManager?.isReachable(peerID) == true) return
        peersManager?.savedPeers?.value?.find { it.userID == peerID }?.let { user ->
            user.lastKnownDeviceAddress?.let { address ->
                connectToDevice(address)
            }
        }
    }

    fun updateLocalUserName(newName: String) {
        val user = localUser ?: return
        user.updateUserName(newName)
        scope.launch {
            userDAO.update(user.toEntity(isLocal = true))
            _state.value = SessionState.Ready(user)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        scope.launch {
            configDAO.saveConfig(ConfigEntity(
                notificationsEnabled = enabled,
                isInactiveMode = _config.value.isInactiveMode
            ))
        }
    }

    fun toggleInactiveMode(enabled: Boolean) {
        scope.launch {
            configDAO.saveConfig(ConfigEntity(
                notificationsEnabled = _config.value.notificationsEnabled,
                isInactiveMode = enabled
            ))
        }
    }

    fun generateConversationID(userA: String, userB: String): String {
        return if (userA < userB) "${userA}_${userB}" else "${userB}_${userA}"
    }

//    getters
    fun userIDOf(deviceAddress: String): String? = peersManager?.userIDOf(deviceAddress)
    fun getConversationMetas(): Flow<List<ConversationMeta>> = conversationsManager?.getConversationMetas() ?: flowOf(emptyList())
    fun getSavedPeers(): StateFlow<Set<User>> = peersManager?.savedPeers ?: MutableStateFlow(emptySet())
    fun getMessageDAO(): MessageDAO = messageDAO
    fun getConnectedDevice(): String? = transport.connectedDevice()

//    identity
    private fun UserEntity.toDomain() = User(userID, userName, createdAt, lastKnownDeviceAddress, isPersistent)
    private fun User.toEntity(isLocal: Boolean) = UserEntity(userID, userName, createdAt, lastKnownDeviceAddress, isLocal, isPersistent)
}