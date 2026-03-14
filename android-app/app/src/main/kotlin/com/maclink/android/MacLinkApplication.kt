package com.maclink.android

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.maclink.android.audio.AudioStreamManager
import com.maclink.android.network.ConnectionState
import com.maclink.android.network.MacLinkClient
import com.maclink.android.network.NsdDiscovery
import com.maclink.android.proto.MacLinkProto.CallEvent
import com.maclink.android.proto.MacLinkProto.Envelope
import com.maclink.android.service.CallDetectorService
import com.maclink.android.service.PhoneNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MacLinkApplication : Application() {
    lateinit var client: MacLinkClient
    lateinit var discovery: NsdDiscovery
    lateinit var callDetector: CallDetectorService
    lateinit var audioStream: AudioStreamManager
    var callDetectorInitialized = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceName = android.os.Build.MODEL

        client = MacLinkClient(deviceName = deviceName, deviceId = deviceId)
        discovery = NsdDiscovery(this)
        audioStream = AudioStreamManager(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            callDetector = CallDetectorService(this)
            // Init zostanie wywołany z MainActivity po przyznaniu READ_PHONE_STATE
        }

        client.onEnvelopeReceived = { envelope -> handleIncoming(envelope) }

        discovery.startDiscovery()

        scope.launch {
            discovery.discoveredDevices.collect { devices ->
                val device = devices.firstOrNull() ?: return@collect
                val state = client.state.value
                if (!client.manuallyDisconnected &&
                    (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR)) {
                    println("[AutoConnect] Odkryto ${device.name} → łączę automatycznie")
                    client.connect(device.host, device.port)
                }
            }
        }
    }

    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MacLink:AnswerCall"
            )
            wl.acquire(5000L)
            println("[App] Screen woken for call answer")
        } catch (e: Exception) {
            println("[App] wakeScreen failed: $e")
        }
    }

    private fun handleIncoming(envelope: Envelope) {
        when (envelope.payloadCase) {
            Envelope.PayloadCase.NOTIFICATION_ACTION -> {
                val action = envelope.notificationAction
                val service = PhoneNotificationListenerService.instance ?: return

                if (action.actionKey == "dismiss") {
                    service.cancelNotification(action.notificationKey)
                } else if (action.replyText.isNotBlank()) {
                    service.performReply(
                        notificationKey = action.notificationKey,
                        actionKey = action.actionKey,
                        replyText = action.replyText
                    )
                } else {
                    service.performReply(
                        notificationKey = action.notificationKey,
                        actionKey = action.actionKey,
                        replyText = ""
                    )
                }
            }

            Envelope.PayloadCase.CALL_EVENT -> {
                val callEvent = envelope.callEvent
                when (callEvent.state) {
                    CallEvent.State.ACCEPTED -> {
                        // Mac user clicked "Odbierz" → answer phone only.
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                            ::callDetector.isInitialized) {
                            wakeScreen()
                            scope.launch(Dispatchers.Main) {
                                delay(600) // wait for dialer window to render
                                callDetector.answerCall()
                                // WhatsApp VoIP często nie triggeruje CALL_STATE_OFFHOOK,
                                // więc watchdog może nie wystartować. Uruchom go ręcznie.
                                delay(1000)
                                callDetector.startWatchdogIfNeeded()
                            }
                        }
                    }
                    CallEvent.State.REJECTED -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                            ::callDetector.isInitialized) {
                            wakeScreen()
                            scope.launch(Dispatchers.Main) {
                                delay(400)
                                callDetector.rejectCall()
                            }
                        }
                        audioStream.stopStreaming()
                    }
                    CallEvent.State.ENDED -> {
                        audioStream.stopStreaming()
                        // Rozłącz aktywną rozmowę (Mac kliknął "Zakończ rozmowę")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                            ::callDetector.isInitialized) {
                            scope.launch(Dispatchers.Main) {
                                callDetector.rejectCall()
                            }
                        }
                    }
                    else -> println("[App] CallEvent state: ${callEvent.state}")
                }
            }

            Envelope.PayloadCase.AUDIO_FRAME -> {
                val pcm = envelope.audioFrame.pcmData.toByteArray()
                if (pcm.isEmpty()) {
                    // Pusty frame = sygnał stopu z Maca
                    audioStream.stopStreaming()
                } else {
                    // Pierwsze dane = auto-start nagrywania mikrofonu i odtwarzacza
                    if (!audioStream.isStreaming) {
                        audioStream.startStreaming(envelope.audioFrame.callId) { env -> client.send(env) }
                    }
                    audioStream.playIncoming(pcm)
                }
            }

            Envelope.PayloadCase.HANDSHAKE_ACK -> {
                val ack = envelope.handshakeAck
                println("[App] HandshakeAck: accepted=${ack.accepted}, mac=${ack.macName}")
                if (!ack.accepted) {
                    client.disconnect()
                }
            }

            Envelope.PayloadCase.HEARTBEAT -> { /* keepalive */ }
            else -> println("[App] Unhandled envelope: ${envelope.payloadCase}")
        }
    }
}
