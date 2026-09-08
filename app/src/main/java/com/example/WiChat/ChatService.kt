package com.example.WiChat

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
import com.example.WiChat.domain.ChatSession
import com.example.WiChat.persistence.AppDatabase
import com.example.WiChat.transport.WiFiDirectTransport
import android.net.wifi.p2p.WifiP2pManager
import com.example.WiChat.core.TransportEvent
import com.example.WiChat.persistence.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.SupervisorJob

class ChatService : Service() {
    private val binder = ChatServiceBinder()
    var chatSession: ChatSession? = null
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var transport: WiFiDirectTransport
//    queue for transport events while DB not loaded
    private val pendingEvents = mutableListOf<TransportEvent>()
    private var isUnlocked = false
//    ---
    private val CHANNEL_ID = "p2p_chat_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        transport = WiFiDirectTransport(this, manager, channel)
        startTransportListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class ChatServiceBinder : Binder() {
        fun getService(): ChatService = this@ChatService
    }

//    This also starts chat after unlock db
    fun unlockDatabase(password: String) {
        if (isUnlocked) return
        val db = DatabaseProvider.getDatabase(this, password.toByteArray())
        try {
            val session = ChatSession(
                transport = transport,
                userDAO = db.userDAO(),
                messageDAO = db.messageDAO(),
                outboxDAO = db.outboxDAO(),
                configDAO = db.configDAO(),
                scope = scope,
            )
            chatSession = session
            session.start()
            isUnlocked = true
//    process pending event after unlocking
            synchronized(pendingEvents) {
                pendingEvents.forEach { event ->
                    session.processManualEvent(event)
                }
                pendingEvents.clear()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startTransportListening() {
        transport.events()
            .onEach { event ->
                if (!isUnlocked) {
                    synchronized(pendingEvents) {
                        pendingEvents.add(event)
                    }
                }
            }
            .launchIn(scope)
    }

    //    TODO: notifications doesn't work yet
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
                CHANNEL_ID, "WiChat Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        chatSession?.stop()
        super.onDestroy()
    }
}