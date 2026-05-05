package com.seniorhotspot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews

/**
 * The home screen App Widget.
 * Displays a big ON/OFF button. When tapped, it toggles the hotspot.
 */
class HotspotWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.seniorhotspot.widget.TOGGLE_HOTSPOT"
        const val ACTION_UPDATE = "com.seniorhotspot.widget.WIDGET_UPDATE"
        private const val TAG = "HotspotWidget"

        /** Call this from anywhere to refresh all widget instances */
        fun refreshAllWidgets(context: Context) {
            val intent = Intent(context, HotspotWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, HotspotWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        /** Build the RemoteViews for the widget based on current hotspot state */
        fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val state = HotspotManager.getHotspotState(context)
            val isOn = state == HotspotManager.HotspotState.ON

            // --- Button appearance ---
            if (isOn) {
                views.setInt(R.id.btn_toggle, "setBackgroundResource", R.drawable.btn_on_background)
                views.setTextViewText(R.id.tv_power_label, "ON")
                views.setTextColor(R.id.tv_power_label, 0xFFFFFFFF.toInt())
                views.setTextViewText(R.id.tv_tap_hint, "Tap to turn OFF")
                views.setTextColor(R.id.tv_tap_hint, 0xCCFFFFFF.toInt())
            } else {
                views.setInt(R.id.btn_toggle, "setBackgroundResource", R.drawable.btn_off_background)
                views.setTextViewText(R.id.tv_power_label, "OFF")
                views.setTextColor(R.id.tv_power_label, 0xFF666666.toInt())
                views.setTextViewText(R.id.tv_tap_hint, "Tap to turn ON")
                views.setTextColor(R.id.tv_tap_hint, 0xFF888888.toInt())
            }

            // --- Status dot ---
            views.setInt(
                R.id.status_dot, "setBackgroundResource",
                if (isOn) R.drawable.dot_on else R.drawable.dot_off
            )

            // --- Status text ---
            views.setTextViewText(
                R.id.tv_status_text,
                if (isOn) "Hotspot is ON" else "Hotspot is OFF"
            )
            views.setTextColor(
                R.id.tv_status_text,
                if (isOn) 0xFF15803D.toInt() else 0xFF555555.toInt()
            )

            // --- Network name ---
            val ssid = if (isOn) HotspotManager.getHotspotName(context) else "–"
            views.setTextViewText(R.id.tv_network_name, ssid)

            // --- Devices / Data (placeholder — full tracking needs foreground service) ---
            views.setTextViewText(R.id.tv_devices, if (isOn) "See Settings" else "–")
            views.setTextViewText(R.id.tv_data_used, if (isOn) "See Settings" else "–")

            // --- Toggle click intent ---
            val toggleIntent = Intent(context, HotspotWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT

            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent, flags
            )
            views.setOnClickPendingIntent(R.id.btn_toggle, pendingIntent)

            return views
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (id in appWidgetIds) {
            val views = buildRemoteViews(context, id)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TOGGLE -> {
                Log.d(TAG, "Toggle action received")
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                // Show "working" state immediately
                showLoadingState(context, appWidgetId)

                // Attempt toggle
                val success = HotspotManager.toggleHotspot(context)

                if (!success) {
                    // Fallback: open system hotspot settings
                    Log.w(TAG, "Toggle failed — opening system settings")
                    openSystemHotspotSettings(context)
                }

                // Delay update to let the system state change propagate
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    refreshAllWidgets(context)
                }, 2000)
            }

            ACTION_UPDATE -> {
                refreshAllWidgets(context)
            }
        }
    }

    /** Show a "busy" state on the widget while the toggle is working */
    private fun showLoadingState(context: Context, appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.tv_power_label, "…")
        views.setTextViewText(R.id.tv_tap_hint, "Please wait")
        views.setTextViewText(R.id.tv_status_text, "Changing…")
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    /** Opens the system tethering/hotspot settings as a fallback */
    private fun openSystemHotspotSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Older fallback
            val fallback = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }
}
