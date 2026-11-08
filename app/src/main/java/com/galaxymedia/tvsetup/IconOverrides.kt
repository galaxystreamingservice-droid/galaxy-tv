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
