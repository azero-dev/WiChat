package com.example.WiChat.core

sealed class SendResult {
    data class Success(val sentToDevice: String): SendResult()
    object NotConnected: SendResult()
    object Error: SendResult()
}