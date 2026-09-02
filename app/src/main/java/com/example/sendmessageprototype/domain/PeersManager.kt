package com.example.sendmessageprototype.domain

import com.example.sendmessageprototype.core.User
import com.example.sendmessageprototype.persistence.UserDAO
import com.example.sendmessageprototype.persistence.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PeersManager(
    private val userDAO: UserDAO,
    private val localUserID: String,
) {
    private val _reachablePeers = MutableStateFlow<Set<String>>(emptySet())
    val reachablePeers: StateFlow<Set<String>> = _reachablePeers.asStateFlow()
    private val _savedPeers = MutableStateFlow<Set<User>>(emptySet())
    val savedPeers: StateFlow<Set<User>> = _savedPeers.asStateFlow()
    private val bindings = mutableMapOf<String, String>()

    suspend fun loadSavedPeers() {
        val entities = userDAO.getAllUsers()
        val users = entities.map{ it.toDomain() }.toSet()
        _savedPeers.value = users
    }

    fun addReachablePeer(userID: String) {
        _reachablePeers.value += userID
    }

    fun removeReachablePeer(userID: String) {
        _reachablePeers.value -= userID
    }

    fun isReachable(userID: String): Boolean = _reachablePeers.value.contains(userID)

    fun clearReachable() {
        _reachablePeers.value = emptySet()
    }

    suspend fun addSavedPeer(user: User) {
        if (!isSaved(user.userID)) {
            userDAO.save(user.toEntity())
            _savedPeers.value += user
        }
    }

    suspend fun removeSavedPeer(userID: String) {
        userDAO.remove(userID)
        _savedPeers.value = _savedPeers.value.filter { it.userID != userID }.toSet()
        _reachablePeers.value -= userID
    }

    fun isSaved(userID: String): Boolean = _savedPeers.value.any {it.userID == userID}

    suspend fun updatePeerName(userID: String, newName: String) {
        _savedPeers.value.find {it.userID == userID }?.let { user ->
            user.updateUserName(newName)
            userDAO.update(user.toEntity())
            _savedPeers.value = _savedPeers.value.toSet()
        }
    }

    suspend fun updateIsPersistent(userID: String, isPersistent: Boolean) {
        _savedPeers.value.find { it.userID == userID }?.let { user ->
            user.updateIsPersistent(isPersistent)
            userDAO.updateIsPersistent(userID, isPersistent)
            _savedPeers.value = _savedPeers.value.toSet()
        }
    }

    suspend fun bind(userID: String, deviceAddress: String) {
        bindings[deviceAddress] = userID
        _savedPeers.value.find { it.userID == userID }?.let { user ->
            user.updateLastKnownDeviceAddress(deviceAddress)
            userDAO.update(user.toEntity())
        }
    }

    fun unbind(deviceAddress: String) {
        bindings.remove(deviceAddress)
    }

    fun userIDOf(deviceAddress: String): String? = bindings[deviceAddress]

    fun savedByAddress(deviceAddress: String): User? {
        return _savedPeers.value.find { it.lastKnownDeviceAddress == deviceAddress }
    }

    private fun UserEntity.toDomain() = User(
        userID = userID,
        userName = userName,
        createdAt = createdAt,
        lastKnownDeviceAddress = lastKnownDeviceAddress,
        isPersistent = isPersistent,
    )

    private fun User.toEntity() = UserEntity(
        userID = userID,
        userName = userName,
        createdAt = createdAt,
        lastKnownDeviceAddress = lastKnownDeviceAddress,
        isLocal = (userID == localUserID),
        isPersistent = isPersistent,
    )
}