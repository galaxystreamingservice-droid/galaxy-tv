package com.galaxymedia.tvsetup.model

import org.json.JSONObject

/** One app entry from the settings file. */
data class AppEntry(
    val name: String,
    val packageName: String,
    val version: Int,
    val apkUrl: String,
    val autoUpdate: Boolean,
    val pinned: Boolean,
    val managedByStore: Boolean
)

data class BoxingEvent(val date: String, val title: String, val platform: String)

/** The per-device settings file (one GitHub Gist per TV). */
data class DeviceSettings(
    val schemaVersion: Int,
    val deviceName: String,
    val checkIntervalHours: Int,
    val maintenanceEnabled: Boolean,
    val maintenanceDay: String,
    val maintenanceHour: Int,
    val disableAlexa: Boolean,
    val blockOtaUpdates: Boolean,
    val launcherPackage: String,
    val launcherApkUrl: String,
    val shelfPosition: String,
    val killPreviousApp: String, // "auto" | "on" | "off"
    val backgroundSource: String,
    val backgroundImage: String,
    val backgroundEffect: String,
    val apps: List<AppEntry>,
    val leagues: List<String>,
    val teams: Map<String, String>,
    val boxing: List<BoxingEvent>,
    val iptvExpires: String = "", // "YYYY-MM-DD", optional
    val notice: String = "" // optional family notice shown as a banner on the home screen
) {
    companion object {
        fun parse(raw: String): DeviceSettings {
            val o = JSONObject(raw)
            val sync = o.optJSONObject("sync") ?: JSONObject()
            val maint = sync.optJSONObject("maintenance") ?: JSONObject()
            val debloat = o.optJSONObject("debloat") ?: JSONObject()
            val launcher = o.optJSONObject("launcher") ?: JSONObject()
            val perf = o.optJSONObject("performance") ?: JSONObject()
            val bg = o.optJSONObject("background") ?: JSONObject()
            val sports = o.optJSONObject("sports") ?: JSONObject()

            val apps = mutableListOf<AppEntry>()
            o.optJSONArray("apps")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    apps += AppEntry(
                        name = a.optString("name"),
                        packageName = a.optString("package"),
                        version = a.optInt("version", 0),
                        apkUrl = a.optString("apkUrl", ""),
                        autoUpdate = a.optBoolean("autoUpdate", true),
                        pinned = a.optBoolean("pinned", true),
                        managedByStore = a.optBoolean("managedByStore", false)
                    )
                }
            }

            val leagues = mutableListOf<String>()
            sports.optJSONArray("leagues")?.let { for (i in 0 until it.length()) leagues += it.getString(i) }

            val teams = mutableMapOf<String, String>()
            sports.optJSONObject("teams")?.let { t -> t.keys().forEach { k -> teams[k] = t.getString(k) } }

            val boxing = mutableListOf<BoxingEvent>()
            sports.optJSONArray("boxing")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    boxing += BoxingEvent(b.optString("date"), b.optString("title"), b.optString("platform"))
                }
            }

            return DeviceSettings(
                schemaVersion = o.optInt("schemaVersion", 1),
                deviceName = o.optString("deviceName", "Fire TV"),
                checkIntervalHours = sync.optInt("checkIntervalHours", 24),
                maintenanceEnabled = maint.optBoolean("enabled", true),
                maintenanceDay = maint.optString("dayOfWeek", "SUNDAY"),
                maintenanceHour = maint.optInt("hour", 3),
                disableAlexa = debloat.optBoolean("disableAlexa", false),
                blockOtaUpdates = debloat.optBoolean("blockOtaUpdates", false),
                launcherPackage = launcher.optString("package", "com.spocky.projengmenu"),
                launcherApkUrl = launcher.optString("apkUrl", ""),
                shelfPosition = launcher.optString("shelfPosition", "bottom"),
                killPreviousApp = perf.optString("killPreviousApp", "auto"),
                backgroundSource = bg.optString("source", "gallery"),
                backgroundImage = bg.optString("galleryImage", "ring-cyan"),
                backgroundEffect = bg.optString("effect", "beam"),
                apps = apps,
                leagues = leagues,
                teams = teams,
                boxing = boxing,
                iptvExpires = o.optString("iptvExpires", ""),
                notice = o.optString("notice", "")
            )
        }
    }
}
