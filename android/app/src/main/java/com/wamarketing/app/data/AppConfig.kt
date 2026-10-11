package com.wamarketing.app.data

import android.content.SharedPreferences
import java.net.HttpURLConnection

object AppConfig {
    const val DEFAULT_BACKEND_URL = "https://wamarketing-production.up.railway.app"

    fun getBackendUrl(prefs: SharedPreferences?): String {
        val stored = prefs?.getString("backend_api_url", null)
        if (stored.isNullOrBlank() || stored.contains("192.168.") || stored.contains("loca.lt") || stored.contains("trycloudflare.com") || stored.contains("themes-mil-coordinator-discover") || stored.contains("perfectly-adaptor-schemes-forge") || stored.contains("des-frontpage") || stored.contains("successful-recipients") || stored.contains("reliable-screw-prague-conduct") || stored.contains("premises-share-bottom-seal") || stored.contains("milwaukee-bunny-monster-permit")) {
            prefs?.edit()?.putString("backend_api_url", DEFAULT_BACKEND_URL)?.apply()
            return DEFAULT_BACKEND_URL
        }
        return stored.trim().removeSuffix("/")
    }

    fun applyStandardHeaders(conn: HttpURLConnection) {
        conn.setRequestProperty("Bypass-Tunnel-Reminder", "true")
        conn.setRequestProperty("User-Agent", "WAMarketingApp/1.0 (Android)")
    }
}
