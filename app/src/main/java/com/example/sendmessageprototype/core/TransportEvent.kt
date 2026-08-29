package com.example.sendmessageprototype.core

sealed class TransportEvent {
    data class PeerConnected(val deviceAddress: String): TransportEvent()
    data class PeerDisconnected(val deviceAddress: String): TransportEvent()
    data class EnvelopeReceived(
        val envelope: MessageInTransit,
        val fromDevice: String,
    ): TransportEvent()
}