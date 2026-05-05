# Keep reflection methods used for hotspot toggling
-keep class android.net.wifi.WifiManager { *; }
-keep class android.net.wifi.WifiConfiguration { *; }
-keepclassmembers class * {
    public void setWifiApEnabled(android.net.wifi.WifiConfiguration, boolean);
    public boolean isWifiApEnabled();
    public android.net.wifi.WifiConfiguration getWifiApConfiguration();
}
