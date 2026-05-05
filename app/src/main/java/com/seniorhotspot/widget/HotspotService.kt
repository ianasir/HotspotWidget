package com.seniorhotspot.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Optional foreground service that polls hotspot state every 10 seconds
 * and refreshes the widget when the state changes.
 * This keeps the widget in sync if the user toggles hotspot from Settings.
 */
class HotspotService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastState = HotspotManager.HotspotState.UNKNOWN
    private val CHANNEL_ID = "hotspot_widget_channel"
    private val NOTIFICATION_ID = 1001

    private val pollRunnable = object : Runnable {
        override fun run() {
            val currentState = HotspotManager.getHotspotState(this@HotspotService)
            if (currentState != lastState) {
                lastState = currentState
                HotspotWidgetProvider.refreshAllWidgets(this@HotspotService)
            }
            handler.postDelayed(this, 10_000) // poll every 10 seconds
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        lastState = HotspotManager.getHotspotState(this)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hotspot Widget")
            .setContentText("Monitoring hotspot status")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hotspot Widget Status",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Used to keep the hotspot widget updated"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
