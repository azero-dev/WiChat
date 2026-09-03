package com.example.sendmessageprototype.domain

class SeenMessagesCache {
    private val seenMessages = mutableMapOf<String, Long>()
    private val TTL_MS = 24 * 60 * 60 * 1000

    fun markIfNew(messageID: String): Boolean {
        cleanup()
        if (seenMessages.containsKey(messageID)) return false
        seenMessages[messageID] = System.currentTimeMillis()
        return true
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val iterator = seenMessages.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > TTL_MS) {
                iterator.remove()
            }
        }
    }
}