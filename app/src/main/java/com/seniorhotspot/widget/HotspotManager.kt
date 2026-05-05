package com.seniorhotspot.widget

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * Manages hotspot (tethering) state.
 *
 * Android 8.0+ no longer allows apps to directly toggle the hotspot via
 * WifiManager.setWifiApEnabled (it was removed from the public API).
 * We use startLocalOnlyHotspot for a "local only" mode, or for full
 * internet-sharing hotspot we use the hidden API via reflection.
 *
 * NOTE: On Android 10+ some OEMs (Samsung, Xiaomi, etc.) may still block
 * this without device-owner privileges. The app will open system Settings
 * as a fallback in those cases.
 */
object HotspotManager {

    private const val TAG = "HotspotManager"

    enum class HotspotState {
        ON, OFF, UNKNOWN
    }

    /** Returns the current hotspot state */
    fun getHotspotState(context: Context): HotspotState {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use reflection to call the hidden isWifiApEnabled method
                val method: Method = wifiManager.javaClass.getMethod("isWifiApEnabled")
                val enabled = method.invoke(wifiManager) as Boolean
                if (enabled) HotspotState.ON else HotspotState.OFF
            } else {
                @Suppress("DEPRECATION")
                val method: Method = wifiManager.javaClass.getMethod("isWifiApEnabled")
                val enabled = method.invoke(wifiManager) as Boolean
                if (enabled) HotspotState.ON else HotspotState.OFF
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read hotspot state: ${e.message}")
            HotspotState.UNKNOWN
        }
    }

    /**
     * Toggles the hotspot on or off.
     * Returns true if the toggle command was issued successfully.
     * Returns false if permission is required or not supported.
     */
    fun toggleHotspot(context: Context): Boolean {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val currentState = getHotspotState(context)
        val targetEnabled = currentState != HotspotState.ON

        return try {
            // Method 1: Reflection on setWifiApEnabled (works on many devices)
            val method: Method = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(wifiManager, null, targetEnabled)
            Log.d(TAG, "Hotspot toggle issued via reflection. Target: $targetEnabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reflection toggle failed: ${e.message}")
            // Method 2: On Android 10+, try the newer approach
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tryToggleViaTetheringManager(context, targetEnabled)
            } else {
                false
            }
        }
    }

    /** Fallback for Android 10+ using TetheringManager hidden API */
    private fun tryToggleViaTetheringManager(context: Context, enable: Boolean): Boolean {
        return try {
            val tetheringManager = context.getSystemService("tethering")
                ?: return false

            if (enable) {
                val startMethod = tetheringManager.javaClass.getMethod(
                    "startTethering",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Class.forName("android.net.TetheringManager\$OnTetheringEntitlementResultListener")
                )
                // TETHERING_WIFI = 0
                startMethod.invoke(tetheringManager, 0, true, null)
            } else {
                val stopMethod = tetheringManager.javaClass.getMethod(
                    "stopTethering",
                    Int::class.javaPrimitiveType
                )
                stopMethod.invoke(tetheringManager, 0)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "TetheringManager toggle failed: ${e.message}")
            false
        }
    }

    /** Gets the hotspot network name (SSID) */
    fun getHotspotName(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method: Method = wifiManager.javaClass.getMethod("getWifiApConfiguration")
            @Suppress("DEPRECATION")
            val config = method.invoke(wifiManager) as? android.net.wifi.WifiConfiguration
            config?.SSID ?: android.os.Build.MODEL
        } catch (e: Exception) {
            android.os.Build.MODEL
        }
    }
}
