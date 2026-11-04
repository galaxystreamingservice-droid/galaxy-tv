package com.galaxymedia.tvsetup

import android.content.Context

/** The WorldVPN app has a different package name on Fire sticks and on Google TV boxes. */
object Vpn {
    val packages = listOf("io.world.vpn", "ai.world.tv")

    /** The one installed on this box, or null. */
    fun installed(ctx: Context): String? =
        packages.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
}
