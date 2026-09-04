package com.example.sendmessageprototype

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.example.sendmessageprototype.domain.ChatSession
import com.example.sendmessageprototype.persistence.AppDatabase
import com.example.sendmessageprototype.transport.WiFiDirectTransport
import android.net.wifi.p2p.WifiP2pManager

class ChatService : Service() {
    private val binder = ChatServiceBinder()
    lateinit var chatSession: ChatSession
    private val CHANNEL_ID = "p2p_chat_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        val database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "WiChat_db")
            .fallbackToDestructiveMigration(true)
            .build()
        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        val transport = WiFiDirectTransport(applicationContext, manager, channel)

        chatSession = ChatSession(
            transport = transport,
            userDAO = database.userDAO(),
            messageDAO = database.messageDAO(),
            outboxDAO = database.outboxDAO(),
            configDAO = database.configDAO(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    inner class ChatServiceBinder : Binder() {
        fun getService(): ChatService = this@ChatService
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiChat is active")
            .setContentText("Retrieving messages")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "P2P Messaging channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        chatSession.stop()
        super.onDestroy()
    }
}