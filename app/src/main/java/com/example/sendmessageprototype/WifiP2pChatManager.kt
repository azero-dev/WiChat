package com.example.sendmessageprototype

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class WifiP2pChatManager(private val viewModel: WifiP2pViewModel) {
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    // Check what's the best port for this use case. For now, 8880.
    private val PORT = 8880

    fun startServer() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(PORT)
                socket = serverSocket?.accept()
                startListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startClient(hostAddress: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                var connected = false
                while (!connected) {
                    try {
                        socket = Socket(hostAddress, PORT)
                        connected = true
                    } catch (e: Exception) {
                        Thread.sleep(1000)
                    }
                }
                startListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun startListening() {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    viewModel.addMessage(Message(content = line, isMine = false))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(text: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (socket == null || socket?.isClosed == true) {
                    viewModel.addMessage(Message(content = "Error: not connected", isMine = true))
                    return@launch
                }
                val writer = PrintWriter(socket?.getOutputStream(), true)
                writer.println(text)
                viewModel.addMessage(Message(content = text, isMine = true))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun close() {
        socket?.close()
        serverSocket?.close()
    }
}