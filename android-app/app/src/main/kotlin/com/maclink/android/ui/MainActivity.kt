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

    private val requestPhonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        println("[MainActivity] READ_PHONE_STATE granted=$granted")
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (application as MacLinkApplication).callDetector.init {
                (application as MacLinkApplication).client.send(it)
            }
        }
    }

    private val requestRecordAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        println("[MainActivity] RECORD_AUDIO granted=$granted")
    }

    private val requestAnswerCallsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        println("[MainActivity] ANSWER_PHONE_CALLS granted=$granted")
    }

    private val requestCallPhonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        println("[MainActivity] CALL_PHONE granted=$granted")
    }

    // Poproś o wszystkie niebezpieczne uprawnienia związane z telefonią
    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            println("[MainActivity] Permission $perm granted=$granted")
        }
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
        requestAllPhonePermissions()

        setContent {
            MacLinkTheme {
                MainScreen(client = app.client, discovery = app.discovery)
            }
        }
    }

    private fun requestAllPhonePermissions() {
        val needed = mutableListOf<String>()
        val phonePerms = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO
        )
        for (perm in phonePerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm)
            }
        }
        if (needed.isNotEmpty()) {
            requestMultiplePermissions.launch(needed.toTypedArray())
        }
        // Init call detector if READ_PHONE_STATE already granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED) {
            val app = application as MacLinkApplication
            if (!app.callDetectorInitialized) {
                app.callDetector.init { app.client.send(it) }
                app.callDetectorInitialized = true
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
