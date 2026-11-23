package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Galaxy-style shelf icons. A picture named after the app's package in assets/icons is shown on the shelf instead of the app's own icon,
 * so the shelf always looks the same, whatever the app ships. Apps without a picture keep their own icon.
 */
object IconOverrides {
    private val cache = HashMap<String, Drawable?>()

    /**
     * v0.14 gold emblem icons, picked by what the app is (so a renamed or re-packaged app still gets its emblem):
     * live TV with a guide (TiviMate), live TV the easy way (XC7 / Galaxy 2.0), video (SmartTube, YouTube), movies (Galaxy Theater).
     */
    fun gold(ctx: Context, name: String, pkg: String): Drawable? {
        val n = (name + " " + pkg).lowercase()
        val key = when {
            "tivimate" in n || "ar.tvplayer" in n -> "gold_tv"
            "xc7" in n || "haxapps" in n || "galaxy 2" in n -> "gold_live"
            "smarttube" in n || "youtube" in n -> "gold_play"
            "theater" in n || "theatre" in n || "moviebox" in n -> "gold_reel"
            else -> return null
        }
        return get(ctx, key)
    }

    fun get(ctx: Context, pkg: String): Drawable? {
        if (cache.containsKey(pkg)) return cache[pkg]
        val d: Drawable? = try {
            ctx.applicationContext.assets.open("icons/$pkg.webp").use { BitmapFactory.decodeStream(it) }
                ?.let { BitmapDrawable(ctx.applicationContext.resources, it) }
        } catch (e: Exception) { null }
        cache[pkg] = d
        return d
    }
}
