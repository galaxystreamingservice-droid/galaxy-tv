package com.galaxymedia.tvsetup

import android.content.Context
import com.galaxymedia.tvsetup.model.AppEntry

/** Installs Projectivy (or whatever the settings file names) and makes it the HOME app. */
class LauncherInstaller(private val ctx: Context) {

    private val stockFireHome = "com.amazon.tv.launcher"

    fun install(launcherPackage: String, apkUrl: String, log: (String) -> Unit) {
        if (apkUrl.isBlank()) { log("skip  launcher (no apkUrl in settings)"); return }
        AppReconciler(ctx).reconcile(
            listOf(AppEntry("Launcher", launcherPackage, 1, apkUrl, autoUpdate = true, pinned = true, managedByStore = false)),
            log
        )
    }

    /** Fire OS: disabling the stock launcher makes the HOME button fall through to the next launcher. */
    fun makeDefault(launcherPackage: String, log: (String) -> Unit) {
        val installed = AdbShell.exec(ctx, "pm list packages $launcherPackage")
        if (!installed.contains(launcherPackage)) { log("FAIL  launcher not installed, cannot set default"); return }
        AdbShell.exec(ctx, "pm disable-user --user 0 $stockFireHome")
        log("home  stock launcher disabled → $launcherPackage is now default")
    }

    fun restoreStock(log: (String) -> Unit) {
        AdbShell.exec(ctx, "pm enable $stockFireHome")
        log("home  stock launcher restored")
    }
}
