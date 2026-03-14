package com.maclink.android

import android.app.Application
import android.provider.Settings
import com.maclink.android.network.MacLinkClient
import com.maclink.android.network.NsdDiscovery
import com.maclink.android.proto.MacLinkProto.Envelope
import com.maclink.android.service.PhoneNotificationListenerService

class MacLinkApplication : Application() {
    lateinit var client: MacLinkClient
    lateinit var discovery: NsdDiscovery

    override fun onCreate() {
        super.onCreate()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceName = android.os.Build.MODEL

        client = MacLinkClient(deviceName = deviceName, deviceId = deviceId)
        discovery = NsdDiscovery(this)

        // Handle incoming messages from macOS
        client.onEnvelopeReceived = { envelope -> handleIncoming(envelope) }
    }

    private fun handleIncoming(envelope: Envelope) {
        when (envelope.payloadCase) {
            Envelope.PayloadCase.NOTIFICATION_ACTION -> {
                val action = envelope.notificationAction
                val service = PhoneNotificationListenerService.instance ?: return

                if (action.actionKey == "dismiss") {
                    // Cancel notification on Android side
                    service.cancelNotification(action.notificationKey)
                } else if (action.replyText.isNotBlank()) {
                    // Send inline reply
                    service.performReply(
                        notificationKey = action.notificationKey,
                        actionKey = action.actionKey,
                        replyText = action.replyText
                    )
                } else {
                    // Regular action tap (no reply)
                    service.performReply(
                        notificationKey = action.notificationKey,
                        actionKey = action.actionKey,
                        replyText = ""
                    )
                }
            }
            Envelope.PayloadCase.HANDSHAKE_ACK -> {
                val ack = envelope.handshakeAck
                println("[App] HandshakeAck: accepted=${ack.accepted}, mac=${ack.macName}")
                if (!ack.accepted) {
                    client.disconnect()
                }
            }
            Envelope.PayloadCase.HEARTBEAT -> { /* ignore — OkHttp ping handles keepalive */ }
            else -> println("[App] Unhandled envelope: ${envelope.payloadCase}")
        }
    }
}
