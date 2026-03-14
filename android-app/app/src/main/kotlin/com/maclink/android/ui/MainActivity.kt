package com.maclink.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.maclink.android.MacLinkApplication
import com.maclink.android.service.ConnectionService
import com.maclink.android.service.PhoneNotificationListenerService

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        println("[MainActivity] POST_NOTIFICATIONS granted=$granted")
        ConnectionService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MacLinkApplication
        PhoneNotificationListenerService.client = app.client

        if (!isNotificationListenerEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        requestNotificationsAndStart()

        setContent {
            MacLinkTheme {
                MainScreen(client = app.client, discovery = app.discovery)
            }
        }
    }

    private fun requestNotificationsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                ConnectionService.start(this)
            } else {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            ConnectionService.start(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Nie zatrzymujemy discovery — działa w tle przez Application
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(packageName)
    }
}
