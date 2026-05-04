package com.example.wavebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class WaveBridgeService : Service() {
    private lateinit var receiver: WaveBridgeReceiver
    private var lastNotificationStatus = ""
    private var lastNotificationUpdateMs = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ReceiverState.loadSettings(applicationContext)
        receiver = WaveBridgeReceiver(applicationContext) { next ->
            ReceiverState.stats = next
            maybeUpdateNotification(next)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(ReceiverState.stats.copy(running = true, status = "Starting")))
                receiver.start()
            }
            ACTION_APPLY_SETTINGS -> {
                receiver.applySettings(ReceiverState.settings)
            }
            ACTION_STOP -> {
                receiver.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        receiver.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun maybeUpdateNotification(stats: ReceiverStats) {
        val now = System.currentTimeMillis()
        if (stats.status == lastNotificationStatus && now - lastNotificationUpdateMs < 2000) {
            return
        }
        lastNotificationStatus = stats.status
        lastNotificationUpdateMs = now
        if (stats.running) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(stats))
        }
    }

    private fun buildNotification(stats: ReceiverStats): Notification {
        val text = when {
            stats.error != null -> stats.error
            stats.lastSender != "-" -> "${stats.status} from ${stats.lastSender}"
            else -> stats.status
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wavebridge)
            .setContentTitle("WaveBridge receiver")
            .setContentText(text)
            .setOngoing(stats.running)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WaveBridge receiver",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps WaveBridge audio receiving while the app is active."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "wavebridge_receiver"
        private const val NOTIFICATION_ID = 37021
        const val ACTION_START = "com.example.wavebridge.START"
        const val ACTION_STOP = "com.example.wavebridge.STOP"
        const val ACTION_APPLY_SETTINGS = "com.example.wavebridge.APPLY_SETTINGS"

        fun start(context: Context) {
            val intent = Intent(context, WaveBridgeService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WaveBridgeService::class.java).setAction(ACTION_STOP))
        }

        fun applySettings(context: Context) {
            context.startService(Intent(context, WaveBridgeService::class.java).setAction(ACTION_APPLY_SETTINGS))
        }
    }
}
