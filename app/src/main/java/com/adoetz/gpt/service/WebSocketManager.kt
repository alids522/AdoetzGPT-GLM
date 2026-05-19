package com.adoetz.gpt.service

import android.content.Context
import com.adoetz.gpt.models.BackendConfig
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

/**
 * WebSocket manager for handling real-time communication with the backend
 */
class WebSocketManager(private val context: Context) {
    private var socket: Socket? = null
    private var socketUri: String? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)

    /**
     * Initialize WebSocket connection with the backend
     */
    fun initializeWebSocket(config: BackendConfig) {
        try {
            socketUri = config.getWebSocketUrl()
            val uri = URI.create(socketUri)
            socket = IO.socket(uri)
            
            // Set up event listeners
            setupEventListeners()
            
            // Connect to the WebSocket
            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Set up event listeners for the WebSocket
     */
    private fun setupEventListeners() {
        socket?.on(Socket.EVENT_CONNECT) { args ->
            mainScope.launch {
                // Handle connection event
                onConnected()
            }
        }?.on(Socket.EVENT_DISCONNECT) { args ->
            mainScope.launch {
                // Handle disconnection event
                onDisconnected()
            }
        }?.on("message") { args ->
            mainScope.launch {
                // Handle message from server
                onMessageReceived(args)
            }
        }?.on("error") { args ->
            mainScope.launch {
                // Handle error
                onError(args)
            }
        }
    }

    /**
     * Send audio data to the server
     */
    fun sendAudioData(data: ByteArray) {
        socket?.emit("audio_data", data)
    }

    /**
     * Send text message to the server
     */
    fun sendMessage(message: String) {
        socket?.emit("message", message)
    }

    /**
     * Handle connection event
     */
    private fun onConnected() {
        // Connection established
    }

    /**
     * Handle disconnection event
     */
    private fun onDisconnected() {
        // Disconnected from server
    }

    /**
     * Handle message received from server
     */
    private fun onMessageReceived(args: Array<out Any>) {
        // Process received message
        if (args.isNotEmpty()) {
            val message = args[0]
            // Handle the message
        }
    }

    /**
     * Handle error event
     */
    private fun onError(args: Array<out Any>) {
        // Handle error
        if (args.isNotEmpty()) {
            val error = args[0]
            // Process error
        }
    }

    /**
     * Disconnect the WebSocket
     */
    fun disconnect() {
        socket?.disconnect()
        socket?.close()
    }

    /**
     * Check if WebSocket is connected
     */
    fun isConnected(): Boolean {
        return socket?.connected() ?: false
    }
}