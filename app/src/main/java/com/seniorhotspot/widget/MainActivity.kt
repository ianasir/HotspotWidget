package com.seniorhotspot.widget

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Main launcher activity.
 * Shows hotspot status, a large toggle button, instructions for adding
 * the widget to the home screen, and a button to grant system settings permission.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvMainStatus: TextView
    private lateinit var btnMainToggle: Button
    private lateinit var btnGrantPermission: Button
    private lateinit var tvPermissionStatus: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMainStatus = findViewById(R.id.tv_main_status)
        btnMainToggle = findViewById(R.id.btn_main_toggle)
        btnGrantPermission = findViewById(R.id.btn_grant_permission)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)

        btnMainToggle.setOnClickListener {
            val success = HotspotManager.toggleHotspot(this)
            if (!success) {
                // Open system hotspot settings as fallback
                openHotspotSettings()
            }
            // Update UI after short delay
            handler.postDelayed({ updateUI() }, 1500)
        }

        btnGrantPermission.setOnClickListener {
            requestWriteSettingsPermission()
        }

        // Start the background service to keep the widget in sync
        startMonitoringService()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun updateUI() {
        val state = HotspotManager.getHotspotState(this)
        val isOn = state == HotspotManager.HotspotState.ON

        tvMainStatus.text = if (isOn) "✅  Hotspot is ON" else "⭕  Hotspot is OFF"
        tvMainStatus.setTextColor(
            if (isOn) 0xFF15803D.toInt() else 0xFF333333.toInt()
        )

        btnMainToggle.text = if (isOn) "Turn OFF Hotspot" else "Turn ON Hotspot"
        btnMainToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (isOn) 0xFFEF4444.toInt() else 0xFF22C55E.toInt()
        )

        // Check write settings permission
        val hasPermission = Settings.System.canWrite(this)
        tvPermissionStatus.text = if (hasPermission)
            "✅  Permission granted — widget is ready!"
        else
            "⚠  Permission needed for the widget button to work"

        tvPermissionStatus.setTextColor(
            if (hasPermission) 0xFF15803D.toInt() else 0xFFB45309.toInt()
        )

        // Refresh widget too
        HotspotWidgetProvider.refreshAllWidgets(this)
    }

    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                tvPermissionStatus.text = "✅  Permission is already granted!"
            }
        }
    }

    private fun openHotspotSettings() {
        try {
            val intent = Intent("android.intent.action.MAIN").apply {
                setClassName(
                    "com.android.settings",
                    "com.android.settings.TetherSettings"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun startMonitoringService() {
        try {
            val serviceIntent = Intent(this, HotspotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Service start failure is non-critical
        }
    }
}
