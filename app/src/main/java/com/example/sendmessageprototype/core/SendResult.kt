package com.example.sendmessageprototype.core

sealed class SendResult {
    data class Success(val sentToDevice: String): SendResult()
    object NotConnected: SendResult()
    object Error: SendResult()
}