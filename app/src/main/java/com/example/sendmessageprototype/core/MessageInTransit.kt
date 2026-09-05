package com.example.sendmessageprototype.core

import kotlinx.serialization.Serializable

@Serializable
class MessageInTransit(
    val messageID: String,
    val payload: Message,
    var ttl: Int = 20,
    var retryCounter: Int = 0,
    var lastAttemptAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val alreadySentTo: MutableSet<String> = mutableSetOf(),
) {
    fun decrementTtl() {
        if (ttl > 0) {
            ttl--
        }
    }

    fun recordFailedAttempt() {
        retryCounter++
        lastAttemptAt = System.currentTimeMillis()
    }

//    overrides
    override  fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageInTransit) return false
        return messageID == other.messageID
    }

    override fun hashCode(): Int {
        return messageID.hashCode()
    }

    override fun toString(): String {
        return "MessageInTransit(id='$messageID', ttl=$ttl, retries=$retryCounter, seenBy=${alreadySentTo.size} peers)"
    }
}