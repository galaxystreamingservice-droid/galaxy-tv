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

    var sports: String
        get() = sp.getString("sports", "all") ?: "all"
        set(v) = sp.edit().putString("sports", v).apply()

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
