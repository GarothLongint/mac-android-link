package com.maclink.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.maclink.android.MacLinkApplication
import com.maclink.android.R
import com.maclink.android.network.ConnectionState
import com.maclink.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service — utrzymuje połączenie z Makiem i pokazuje ikonkę w pasku statusu.
 */
class ConnectionService : Service() {

    companion object {
        const val CHANNEL_ID = "maclink_connection_v3"   // v3 = IMPORTANCE_LOW + FLAG_NO_CLEAR
        const val NOTIF_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var stateJob: Job? = null

    // WiFi Lock — zapobiega throttlingowi WiFi gdy ekran wyłączony
    private var wifiLock: WifiManager.WifiLock? = null
    // CPU WakeLock — korutyny działają nawet gdy ekran wyłączony
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        println("[ConnectionService] onCreate — starting foreground")
        createChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(ConnectionState.DISCONNECTED),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        println("[ConnectionService] startForeground called")

        // Trzymaj WiFi aktywne gdy ekran wyłączony
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MacLink:WiFi")
        wifiLock?.acquire()

        // Trzymaj CPU aktywne (korutyny heartbeat)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MacLink:CPU")
        wakeLock?.acquire()

        val app = application as MacLinkApplication
        stateJob = scope.launch {
            app.client.state.collect { state ->
                println("[ConnectionService] State changed: $state")
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY   // restartuj jeśli system ubije serwis

    override fun onDestroy() {
        stateJob?.cancel()
        wifiLock?.release()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // MARK: - Notification

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stan połączenia MacLink",
            NotificationManager.IMPORTANCE_LOW  // LOW = widoczna, bez dźwięku, nie można wyłączyć z poziomu swipe
        ).apply {
            description = "Pokazuje czy MacLink jest połączony z komputerem Mac"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title, text) = when (state) {
            ConnectionState.CONNECTED    ->
                "MacLink połączony" to "Powiadomienia są przesyłane do Maca ✓"
            ConnectionState.CONNECTING   ->
                "MacLink — łączenie…" to "Trwa nawiązywanie połączenia z Makiem"
            ConnectionState.ERROR        ->
                "MacLink — błąd połączenia" to "Dotknij aby otworzyć aplikację"
            ConnectionState.DISCONNECTED ->
                "MacLink rozłączony" to "Dotknij aby otworzyć aplikację"
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_maclink_status)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        // FLAG_NO_CLEAR — dodatkowa ochrona przed usunięciem przez użytkownika
        notification.flags = notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR

        return notification
    }

    private fun updateNotification(state: ConnectionState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(state))
    }
}
