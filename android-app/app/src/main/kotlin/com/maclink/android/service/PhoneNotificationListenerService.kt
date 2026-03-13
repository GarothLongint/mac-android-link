package com.maclink.android.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.maclink.android.proto.MacLinkProto.Envelope
import com.maclink.android.proto.MacLinkProto.Notification
import com.maclink.android.proto.MacLinkProto.NotificationAction
import java.util.UUID

/**
 * Captures all Android notifications and forwards them to the connected macOS device.
 * Must be declared in AndroidManifest.xml with BIND_NOTIFICATION_LISTENER_SERVICE permission.
 */
class PhoneNotificationListenerService : NotificationListenerService() {

    companion object {
        // Set by the main application when connected
        var client: com.maclink.android.network.MacLinkClient? = null

        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in IGNORED_PACKAGES) return
        if (sbn.isOngoing && sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

        val actions = sbn.notification.actions?.map { action ->
            NotificationAction.newBuilder()
                .setNotificationKey(sbn.key)
                .setActionKey(action.title?.toString() ?: "")
                .setLabel(action.title?.toString() ?: "")
                .build()
        } ?: emptyList()

        val notification = Notification.newBuilder()
            .setNotificationKey(sbn.key)
            .setPackageName(sbn.packageName)
            .setAppName(appName)
            .setTitle(title)
            .setText(text)
            .setSubText(subText)
            .setPostedAt(sbn.postTime)
            .setIsOngoing(sbn.isOngoing)
            .setGroupKey(sbn.groupKey ?: "")
            .addAllActions(actions)
            .build()

        val envelope = Envelope.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTimestamp(System.currentTimeMillis())
            .setNotification(notification)
            .build()

        client?.send(envelope)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // TODO: send dismissal event to macOS
    }
}
