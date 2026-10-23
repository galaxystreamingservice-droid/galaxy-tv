package com.galaxymedia.tvsetup

import android.content.Context

object MaintenanceTasks {

    /** Trim app caches across the device. Safe: no logins or settings are touched. */
    fun clearCache(ctx: Context, log: (String) -> Unit) {
        // Ask the system to free up to 2 GB of cache; it trims oldest-first.
        val out = AdbShell.exec(ctx, "pm trim-caches 2000M").trim()
        log("cache trimmed → ${out.ifEmpty { "ok" }}")
        AdbShell.exec(ctx, "sync")
    }

    /** Fully stop an app so it frees RAM/CPU. Used by the kill-previous-app feature. */
    fun forceStop(ctx: Context, packageName: String) {
        AdbShell.exec(ctx, "am force-stop $packageName")
    }

    /**
     * Full Amazon factory reset. IRREVERSIBLE. Also turns ADB Debugging OFF,
     * so the user must re-enable it from scratch to ever set this up again.
     * Only call after an explicit "Are you sure?" confirmation.
     */
    fun factoryReset(ctx: Context) {
        AdbShell.exec(ctx, "am broadcast -a android.intent.action.MASTER_CLEAR")
    }
}
