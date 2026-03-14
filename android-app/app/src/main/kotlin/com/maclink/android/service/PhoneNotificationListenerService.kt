package com.maclink.android.service

import android.app.RemoteInput
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.protobuf.ByteString
import com.maclink.android.proto.MacLinkProto.Envelope
import com.maclink.android.proto.MacLinkProto.Notification
import com.maclink.android.proto.MacLinkProto.NotificationAction
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Captures all Android notifications and forwards them to the connected macOS device.
 * Handles notification actions including inline replies.
 */
class PhoneNotificationListenerService : NotificationListenerService() {

    companion object {
        var client: com.maclink.android.network.MacLinkClient? = null
        var instance: PhoneNotificationListenerService? = null

        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.maclink.android"
        )

        const val REPLY_KEY = "maclink_reply_text"
    }

    override fun onListenerConnected() {
        instance = this
        println("[NotifListener] Service connected")
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in IGNORED_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

        // App icon → PNG bytes (compressed, max 48x48px)
        val iconBytes = runCatching {
            val drawable = packageManager.getApplicationIcon(sbn.packageName)
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, 48, 48)
                drawable.draw(canvas)
                bmp
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 85, out)
            out.toByteArray()
        }.getOrNull()

        // Notification actions
        val actions = sbn.notification.actions?.mapNotNull { action ->
            val label = action.title?.toString() ?: return@mapNotNull null
            NotificationAction.newBuilder()
                .setNotificationKey(sbn.key)
                .setActionKey(label)
                .setLabel(label)
                .build()
        } ?: emptyList()

        val builder = Notification.newBuilder()
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

        if (iconBytes != null) {
            builder.iconPng = ByteString.copyFrom(iconBytes)
        }

        val envelope = Envelope.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTimestamp(System.currentTimeMillis())
            .setNotification(builder.build())
            .build()

        client?.send(envelope)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // TODO: send dismiss event to macOS in faza 1 finalization
    }

    /**
     * Triggered by macOS when user clicks a reply action.
     * Finds the original notification and sends the reply via RemoteInput.
     */
    fun performReply(notificationKey: String, actionKey: String, replyText: String) {
        val sbn = activeNotifications?.find { it.key == notificationKey } ?: return
        val action = sbn.notification.actions?.find {
            it.title?.toString() == actionKey
        } ?: return

        val remoteInputs = action.remoteInputs ?: return
        val intent = action.actionIntent ?: return

        val inputBundle = android.os.Bundle()
        for (ri in remoteInputs) {
            inputBundle.putCharSequence(ri.resultKey, replyText)
        }

        RemoteInput.addResultsToIntent(remoteInputs, intent.intent, inputBundle)
        runCatching { intent.send(applicationContext, 0, intent.intent) }
    }
}
