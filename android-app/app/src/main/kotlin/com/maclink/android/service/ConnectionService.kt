package com.maclink.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.maclink.android.MacLinkApplication
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
        const val CHANNEL_ID = "maclink_connection"
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

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification(ConnectionState.DISCONNECTED))

        val app = application as MacLinkApplication
        // Obserwuj stan połączenia i aktualizuj powiadomienie
        stateJob = scope.launch {
            app.client.state.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY   // restartuj jeśli system ubije serwis

    override fun onDestroy() {
        stateJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // MARK: - Notification

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stan połączenia MacLink",
            NotificationManager.IMPORTANCE_LOW   // LOW = bez dźwięku, widoczne w pasku
        ).apply {
            description = "Pokazuje czy MacLink jest połączony z komputerem Mac"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (icon, title, text) = when (state) {
            ConnectionState.CONNECTED    ->
                Triple(android.R.drawable.stat_sys_data_connected, "MacLink połączony", "Powiadomienia są przesyłane do Maca ✓")
            ConnectionState.CONNECTING   ->
                Triple(android.R.drawable.stat_sys_warning, "MacLink — łączenie…", "Trwa nawiązywanie połączenia z Makiem")
            ConnectionState.ERROR        ->
                Triple(android.R.drawable.stat_notify_error, "MacLink — błąd połączenia", "Dotknij aby otworzyć aplikację")
            ConnectionState.DISCONNECTED ->
                Triple(android.R.drawable.stat_sys_warning, "MacLink rozłączony", "Dotknij aby otworzyć aplikację")
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification(state: ConnectionState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(state))
    }
}
