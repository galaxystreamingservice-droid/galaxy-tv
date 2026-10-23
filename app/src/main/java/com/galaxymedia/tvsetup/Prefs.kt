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

    var paired: Boolean
        get() = sp.getBoolean("paired", false)
        set(v) = sp.edit().putBoolean("paired", v).apply()

    var lastOpenedApp: String
        get() = sp.getString("lastApp", "") ?: ""
        set(v) = sp.edit().putString("lastApp", v).apply()

    fun getAppVersion(pkg: String) = sp.getInt("ver_$pkg", 0)
    fun setAppVersion(pkg: String, v: Int) = sp.edit().putInt("ver_$pkg", v).apply()
}
