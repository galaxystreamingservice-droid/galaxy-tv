package com.galaxymedia.tvsetup

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo

/** Asks Android to close other apps' background processes. A normal app cannot force-stop anything else. */
object AppCloser {

    /** Never closed: the VPN has to stay connected. */
    private val keep = setOf("io.world.vpn")

    fun closeOthers(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (ai in ctx.packageManager.getInstalledApplications(0)) {
            if (ai.packageName == ctx.packageName || (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || ai.packageName in keep) continue
            try { am.killBackgroundProcesses(ai.packageName) } catch (e: Exception) { }
        }
    }

    /** "auto" closes apps on sticks with little memory (under about 2.5 GB). */
    fun enabled(ctx: Context, mode: String): Boolean = when (mode) {
        "on" -> true
        "off" -> false
        else -> {
            val mi = ActivityManager.MemoryInfo()
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            mi.totalMem < 2_560L * 1024 * 1024 || DeviceProfile.detect(ctx).killPreviousDefault
        }
    }
}
