package com.maclink.android.network

import com.maclink.android.proto.MacLinkProto.Envelope
import com.maclink.android.proto.MacLinkProto.Handshake
import com.maclink.android.proto.MacLinkProto.Heartbeat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.UUID

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Raw TCP client using 4-byte big-endian length-prefix framing + Protobuf.
 * Avoids WebSocket handshake incompatibilities with macOS Network.framework.
 */
class MacLinkClient(
    private val deviceName: String,
    private val deviceId: String
) {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    var onEnvelopeReceived: ((Envelope) -> Unit)? = null

    // true = użytkownik ręcznie rozłączył → nie auto-reconnektuj
    var manuallyDisconnected: Boolean = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private var lastHost: String? = null
    private var lastPort: Int = 0
    private var reconnectAttempt = 0

    // MARK: - Connect / Disconnect

    fun connect(host: String, port: Int) {
        manuallyDisconnected = false   // ręczne połączenie resetuje flagę
        lastHost = host
        lastPort = port
        reconnectJob?.cancel()
        receiveJob?.cancel()               // anuluj poprzednie połączenie
        runCatching { socket?.close() }    // zamknij stary socket
        socket = null
        doConnect(host, port)
    }

    fun disconnect() {
        manuallyDisconnected = true    // zapamiętaj że użytkownik rozłączył ręcznie
        lastHost = null
        reconnectJob?.cancel()
        receiveJob?.cancel()
        stopJobs()
        runCatching { socket?.close() }
        socket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    private fun doConnect(host: String, port: Int) {
        _state.value = ConnectionState.CONNECTING
        println("[TCP] Connecting to $host:$port (attempt ${reconnectAttempt + 1})")

        receiveJob = scope.launch {
            runCatching {
                val s = Socket()
                s.soTimeout = 35_000        // 35s read timeout — wykryje martwy Mac szybko
                s.keepAlive = true          // OS wykryje martwe połączenie
                s.tcpNoDelay = true         // bez Nagle — mniejsze opóźnienia
                s.connect(java.net.InetSocketAddress(host, port), 10_000)
                socket = s
                output = DataOutputStream(s.getOutputStream())
                val input = DataInputStream(s.getInputStream())

                _state.value = ConnectionState.CONNECTED
                reconnectAttempt = 0
                println("[TCP] Connected")

                sendHandshake()
                startHeartbeat()

                // Read loop: 4-byte length + body
                while (isActive && !s.isClosed) {
                    val length = try {
                        input.readInt()
                    } catch (e: java.net.SocketTimeoutException) {
                        // soTimeout przekroczony — Mac nie odpowiedział, reconnect
                        println("[TCP] Socket read timeout — reconnecting")
                        break
                    } catch (e: Exception) {
                        break
                    }
                    if (length <= 0 || length > 10_000_000) break
                    val body = ByteArray(length)
                    input.readFully(body)
                    try {
                        val envelope = Envelope.parseFrom(body)
                        onEnvelopeReceived?.invoke(envelope)
                    } catch (e: Exception) {
                        println("[TCP] Parse error: $e")
                    }
                }
            }.onFailure { e ->
                println("[TCP] Error connecting to $host:$port — ${e.javaClass.simpleName}: ${e.message}")
            }

            stopJobs()
            socket = null
            if (_state.value != ConnectionState.DISCONNECTED) {
                _state.value = ConnectionState.ERROR
                scheduleReconnect()
            }
        }
    }

    // MARK: - Send

    fun send(envelope: Envelope) {
        scope.launch {
            runCatching {
                val body = envelope.toByteArray()
                output?.let { out ->
                    out.writeInt(body.size)   // 4-byte big-endian length
                    out.write(body)
                    out.flush()
                }
            }.onFailure { println("[TCP] Send error: $it") }
        }
    }

    private fun sendHandshake() {
        val hs = Handshake.newBuilder()
            .setDeviceName(deviceName)
            .setDeviceId(deviceId)
            .setVersion("1.0")
            .build()
        send(Envelope.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTimestamp(System.currentTimeMillis())
            .setHandshake(hs)
            .build())
    }

    // MARK: - Heartbeat

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(10_000)  // co 10s — częściej żeby Mac nie dropował połączenia
                val hb = Heartbeat.newBuilder().setSentAt(System.currentTimeMillis()).build()
                send(Envelope.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setTimestamp(System.currentTimeMillis())
                    .setHeartbeat(hb)
                    .build())
            }
        }
    }

    // MARK: - Reconnect (exponential backoff)

    private fun scheduleReconnect() {
        val host = lastHost ?: return
        // Szybki reconnect: 1s, 2s, 4s, 8s, max 15s (nie 60s)
        val delayMs = minOf(1000L * (1 shl reconnectAttempt), 15_000L)
        reconnectAttempt++
        println("[TCP] Reconnect in ${delayMs}ms (attempt $reconnectAttempt)")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (lastHost != null) doConnect(host, lastPort)
        }
    }

    private fun stopJobs() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
