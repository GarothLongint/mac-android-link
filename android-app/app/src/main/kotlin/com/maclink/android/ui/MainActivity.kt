package com.maclink.android.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maclink.android.MacLinkApplication
import com.maclink.android.network.NsdDiscovery
import com.maclink.android.service.PhoneNotificationListenerService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MacLinkApplication
        PhoneNotificationListenerService.client = app.client

        // Ask for notification access if not granted
        if (!isNotificationListenerEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        app.discovery.startDiscovery()

        setContent {
            MacLinkTheme {
                MainScreen(client = app.client, discovery = app.discovery)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val app = application as MacLinkApplication
        app.discovery.stopDiscovery()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(packageName)
    }
}
