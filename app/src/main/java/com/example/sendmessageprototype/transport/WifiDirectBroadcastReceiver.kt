package com.example.sendmessageprototype.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WifiDirectBroadcastReceiver(
    private val onIntent: (Intent) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        onIntent(intent)
    }
}