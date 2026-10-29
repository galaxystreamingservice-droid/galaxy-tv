package com.galaxymedia.tvsetup

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("galaxy", Context.MODE_PRIVATE)

    var settingsUrl: String
        get() = sp.getString("settingsUrl", "") ?: ""
        set(v) = sp.edit().putString("settingsUrl", v).apply()

    var lastSettingsJson: String
        get() = sp.getString("lastSettings", "") ?: ""
        set(v) = sp.edit().putString("lastSettings", v).apply()

    var lastSync: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) = sp.edit().putLong("lastSync", v).apply()

    var userName: String
        get() = sp.getString("userName", "") ?: ""
        set(v) = sp.edit().putString("userName", v).apply()

    var namePrompted: Boolean
        get() = sp.getBoolean("namePrompted", false)
        set(v) = sp.edit().putBoolean("namePrompted", v).apply()

    var accent: String
        get() = sp.getString("accent", "cyan") ?: "cyan"
        set(v) = sp.edit().putString("accent", v).apply()

    var wallpaper: String
        get() = sp.getString("wallpaper", "stars") ?: "stars"
        set(v) = sp.edit().putString("wallpaper", v).apply()

    var team: String
        get() = sp.getString("team", "cowboys") ?: "cowboys"
        set(v) = sp.edit().putString("team", v).apply()

    /** "off", "my" (my teams only) or "all" (all games today). */
    var sports: String
        get() = sp.getString("sports", "all") ?: "all"
        set(v) = sp.edit().putString("sports", v).apply()

    /** "bottom" (row along the bottom) or "left" (rail down the left side). */
    var shelf: String
        get() = sp.getString("shelf", "bottom") ?: "bottom"
        set(v) = sp.edit().putString("shelf", v).apply()

    /** Favorite teams, one entry each like "NFL:DAL|Cowboys" (league:abbreviation|name). */
    var myTeams: Set<String>
        get() = sp.getStringSet("myTeams", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("myTeams", v.toSet()).apply()

    /** Package names of shelf apps this person chose to hide (per stick, not in the Gist). */
    var hiddenApps: Set<String>
        get() = sp.getStringSet("hiddenApps", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("hiddenApps", v.toSet()).apply()

    /** Just the "NFL:DAL" part of each favorite team, for matching against games. */
    val myTeamKeys: Set<String>
        get() = myTeams.map { it.substringBefore('|') }.toSet()

    /** "normal", "large" or "xlarge". */
    var textSize: String
        get() = sp.getString("textSize", "normal") ?: "normal"
        set(v) = sp.edit().putString("textSize", v).apply()

    val textScale: Float
        get() = when (textSize) { "large" -> 1.2f; "xlarge" -> 1.4f; else -> 1f }

    var highContrast: Boolean
        get() = sp.getBoolean("highContrast", false)
        set(v) = sp.edit().putBoolean("highContrast", v).apply()

    /** Quiet mode: chips and sports strip dim after about 30 seconds without a key press. */
    var quietMode: Boolean
        get() = sp.getBoolean("quietMode", true)
        set(v) = sp.edit().putBoolean("quietMode", v).apply()

    /** Night dimming start hour (18 to 23), or -1 for off. Ends at 6 AM. */
    var nightStart: Int
        get() = sp.getInt("nightStart", -1)
        set(v) = sp.edit().putInt("nightStart", v).apply()

    /** Starfield motion: "off" (still), "reduced" (slow, subtle twinkle) or "normal". */
    var motion: String
        get() = sp.getString("motion", "off") ?: "off"
        set(v) = sp.edit().putString("motion", v).apply()

    /** Idle screensaver (a slow-moving clock) after about 5 minutes with no key press. */
    var screensaver: Boolean
        get() = sp.getBoolean("screensaver", true)
        set(v) = sp.edit().putBoolean("screensaver", v).apply()

    /** Weather city for the greeting line. Blank = off. Lat/lon are saved as text. */
    var weatherName: String
        get() = sp.getString("weatherName", "") ?: ""
        set(v) = sp.edit().putString("weatherName", v).apply()

    var weatherLat: String
        get() = sp.getString("weatherLat", "") ?: ""
        set(v) = sp.edit().putString("weatherLat", v).apply()

    var weatherLon: String
        get() = sp.getString("weatherLon", "") ?: ""
        set(v) = sp.edit().putString("weatherLon", v).apply()

    /** Puts the look back to defaults. Keeps name, teams and settings link. */
    fun resetLook() {
        sp.edit().remove("accent").remove("wallpaper").remove("team").remove("sports")
            .remove("shelf").remove("textSize").remove("highContrast")
            .remove("quietMode").remove("nightStart").remove("motion").remove("screensaver").apply()
    }

    /** How often the "Cleanup due" chip comes back: "off", "weekly" or "monthly". */
    var cleanupEvery: String
        get() = sp.getString("cleanupEvery", "monthly") ?: "monthly"
        set(v) = sp.edit().putString("cleanupEvery", v).apply()

    var lastCleanup: Long
        get() = sp.getLong("lastCleanup", 0L)
        set(v) = sp.edit().putLong("lastCleanup", v).apply()

    var cleanupSnoozeUntil: Long
        get() = sp.getLong("cleanupSnoozeUntil", 0L)
        set(v) = sp.edit().putLong("cleanupSnoozeUntil", v).apply()

    fun cleanupDue(now: Long = System.currentTimeMillis()): Boolean {
        val days = when (cleanupEvery) { "weekly" -> 7; "monthly" -> 30; else -> return false }
        if (now < cleanupSnoozeUntil) return false
        return lastCleanup == 0L || now - lastCleanup >= days * 86_400_000L
    }

    fun getAppVersion(pkg: String) = sp.getInt("ver_$pkg", 0)
    fun setAppVersion(pkg: String, v: Int) = sp.edit().putInt("ver_$pkg", v).apply()

    // Installs we handed to the system installer and are waiting to confirm.
    fun pendingPackages(): Set<String> = sp.getStringSet("pend_set", emptySet()) ?: emptySet()

    fun markPending(pkg: String, version: Int, startedAt: Long) {
        val set = pendingPackages().toMutableSet().apply { add(pkg) }
        sp.edit()
            .putStringSet("pend_set", set)
            .putInt("pend_ver_$pkg", version)
            .putLong("pend_ts_$pkg", startedAt)
            .apply()
    }

    fun pendingVersion(pkg: String) = sp.getInt("pend_ver_$pkg", 0)
    fun pendingStartedAt(pkg: String) = sp.getLong("pend_ts_$pkg", 0L)

    fun clearPending(pkg: String) {
        val set = pendingPackages().toMutableSet().apply { remove(pkg) }
        sp.edit().putStringSet("pend_set", set).apply()
    }
}
