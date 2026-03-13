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
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null

    // MARK: - Connect / Disconnect

    fun connect(host: String, port: Int) {
        _state.value = ConnectionState.CONNECTING
        val url = "ws://$host:$port"
        println("[WS] Connecting to $url")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                println("[WS] Connected")
                _state.value = ConnectionState.CONNECTED
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
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                println("[WS] Closed: $reason")
                _state.value = ConnectionState.DISCONNECTED
                stopHeartbeat()
            }
        })
    }

    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    // MARK: - Sending

    fun send(envelope: Envelope) {
        val bytes = envelope.toByteArray().toByteString()
        webSocket?.send(bytes)
    }

    private fun sendHandshake() {
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
