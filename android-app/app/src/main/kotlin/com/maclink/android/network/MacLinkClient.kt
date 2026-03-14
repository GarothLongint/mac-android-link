package com.maclink.android.network

import com.maclink.android.proto.MacLinkProto.Envelope
import com.maclink.android.proto.MacLinkProto.Handshake
import com.maclink.android.proto.MacLinkProto.Heartbeat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * WebSocket client that connects to the MacLink server on macOS.
 * All messages are binary Protobuf [Envelope] frames.
 * Automatically reconnects with exponential backoff on failure.
 */
class MacLinkClient(
    private val deviceName: String,
    private val deviceId: String
) {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    var onEnvelopeReceived: ((Envelope) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    // Last known address for auto-reconnect
    private var lastHost: String? = null
    private var lastPort: Int = 0

    // MARK: - Connect / Disconnect

    fun connect(host: String, port: Int) {
        lastHost = host
        lastPort = port
        reconnectJob?.cancel()
        doConnect(host, port)
    }

    fun disconnect() {
        lastHost = null  // clear so auto-reconnect won't trigger
        stopHeartbeat()
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    private fun doConnect(host: String, port: Int) {
        _state.value = ConnectionState.CONNECTING
        val url = "ws://$host:$port"
        println("[WS] Connecting to $url")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                println("[WS] Connected")
                _state.value = ConnectionState.CONNECTED
                reconnectJob?.cancel()
                sendHandshake()
                startHeartbeat()
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                try {
                    val envelope = Envelope.parseFrom(bytes.toByteArray())
                    onEnvelopeReceived?.invoke(envelope)
                } catch (e: Exception) {
                    println("[WS] Parse error: $e")
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                println("[WS] Unexpected text frame: $text")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                println("[WS] Failure: $t")
                _state.value = ConnectionState.ERROR
                stopHeartbeat()
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                println("[WS] Closed: $reason")
                stopHeartbeat()
                if (code != 1000) {
                    // Unexpected close — try to reconnect
                    _state.value = ConnectionState.DISCONNECTED
                    scheduleReconnect()
                } else {
                    _state.value = ConnectionState.DISCONNECTED
                }
            }
        })
    }

    // MARK: - Auto-reconnect (exponential backoff: 2s, 4s, 8s … max 60s)

    private var reconnectAttempt = 0

    private fun scheduleReconnect() {
        val host = lastHost ?: return  // no target — don't reconnect
        reconnectJob?.cancel()
        val delayMs = minOf(2000L * (1 shl reconnectAttempt), 60_000L)
        reconnectAttempt++
        println("[WS] Reconnect in ${delayMs}ms (attempt $reconnectAttempt)")

        reconnectJob = scope.launch {
            delay(delayMs)
            if (lastHost != null) doConnect(host, lastPort)
        }
    }

    // MARK: - Sending

    fun send(envelope: Envelope) {
        val bytes = envelope.toByteArray().toByteString()
        webSocket?.send(bytes)
    }

    private fun sendHandshake() {
        reconnectAttempt = 0  // reset backoff on successful connect
        val hs = Handshake.newBuilder()
            .setDeviceName(deviceName)
            .setDeviceId(deviceId)
            .setVersion("1.0")
            .build()

        val envelope = Envelope.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTimestamp(System.currentTimeMillis())
            .setHandshake(hs)
            .build()

        send(envelope)
    }

    // MARK: - Heartbeat (every 30s)

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000)
                val hb = Heartbeat.newBuilder()
                    .setSentAt(System.currentTimeMillis())
                    .build()
                val env = Envelope.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setTimestamp(System.currentTimeMillis())
                    .setHeartbeat(hb)
                    .build()
                send(env)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
