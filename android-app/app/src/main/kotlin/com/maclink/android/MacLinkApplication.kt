package com.maclink.android

import android.app.Application
import android.provider.Settings
import com.maclink.android.network.ConnectionState
import com.maclink.android.network.MacLinkClient
import com.maclink.android.network.NsdDiscovery
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceName = android.os.Build.MODEL

        client = MacLinkClient(deviceName = deviceName, deviceId = deviceId)
        discovery = NsdDiscovery(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            callDetector = CallDetectorService(this)
            callDetector.init { envelope -> client.send(envelope) }
        }

        client.onEnvelopeReceived = { envelope -> handleIncoming(envelope) }

        // Uruchom discovery od razu (nie czekamy na otwarcie UI)
        discovery.startDiscovery()

        // Auto-connect: gdy NSD wykryje urządzenie i nie jesteśmy połączeni → łącz
        // (tylko jeśli użytkownik NIE rozłączył się ręcznie)
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    ::callDetector.isInitialized) {
                    when (callEvent.state) {
                        com.maclink.android.proto.MacLinkProto.CallEvent.State.ACCEPTED -> callDetector.answerCall()
                        com.maclink.android.proto.MacLinkProto.CallEvent.State.REJECTED -> callDetector.rejectCall()
                        else -> println("[App] CallEvent state: ${callEvent.state}")
                    }
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
