package com.example.sendmessageprototype.core

class User(
    val userID: String,
    var userName: String,
    val createdAt: Long = System.currentTimeMillis(),
    var lastKnownDeviceAddress: String? = null,
    var isPersistent: Boolean = false,
) {
    fun updateUserName(newName: String) {
        userName = newName
    }

    fun updateLastKnownDeviceAddress(newAddress: String) {
        lastKnownDeviceAddress = newAddress
    }

    fun updateIsPersistent(value: Boolean) {
        isPersistent = value
    }

//    overrides
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return userID == other.userID
    }

    override fun hashCode(): Int {
        return userID.hashCode()
    }

    override fun toString(): String {
        return "User(userID='$userID', userName='$userName', lastKnownDeviceAddress=$lastKnownDeviceAddress)"
    }
}