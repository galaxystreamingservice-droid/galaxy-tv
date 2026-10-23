package com.galaxymedia.tvsetup

import android.content.Context
import com.galaxymedia.tvsetup.model.DeviceSettings
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the per-device settings file (a raw GitHub Gist URL).
 * Falls back to the last-known-good copy when offline so nothing breaks.
 */
class SettingsRepository(private val ctx: Context) {
    private val prefs = Prefs(ctx)

    fun fetch(log: (String) -> Unit): DeviceSettings? {
        val url = prefs.settingsUrl
        if (url.isBlank()) { log("no settings URL configured"); return cached(log) }
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 15000
                setRequestProperty("Cache-Control", "no-cache")
            }
            val raw = conn.inputStream.bufferedReader().readText()
            prefs.lastSettingsJson = raw
            log("settings fetched (${raw.length} bytes)")
            DeviceSettings.parse(raw)
        } catch (e: Exception) {
            log("fetch failed: ${e.message} — using last known good")
            cached(log)
        }
    }

    private fun cached(log: (String) -> Unit): DeviceSettings? {
        val raw = prefs.lastSettingsJson
        if (raw.isBlank()) { log("no cached settings yet"); return null }
        return DeviceSettings.parse(raw)
    }
}
