package com.galaxymedia.tvsetup

import android.content.Context
import com.galaxymedia.tvsetup.model.AppEntry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Makes "what's installed" match "what the settings file says".
 * Same-package newer version → update. Package in file but missing → install.
 * Uses the ADB shell's `pm install` so there is NO install confirmation screen.
 */
class AppReconciler(private val ctx: Context) {

    private val prefs = Prefs(ctx)

    fun reconcile(apps: List<AppEntry>, log: (String) -> Unit) {
        val installed = AdbShell.exec(ctx, "pm list packages")
            .lineSequence().map { it.removePrefix("package:").trim() }.toSet()

        for (app in apps) {
            if (app.managedByStore) { log("skip  ${app.name} (store-managed)"); continue }
            if (app.apkUrl.isBlank() || app.apkUrl.startsWith("REPLACE")) { log("skip  ${app.name} (no apkUrl yet)"); continue }

            val isInstalled = app.packageName in installed
            val knownVersion = prefs.getAppVersion(app.packageName)

            val needsWork = !isInstalled || (app.autoUpdate && app.version > knownVersion)
            if (!needsWork) { log("ok    ${app.name} v$knownVersion"); continue }

            log("get   ${app.name} ← ${app.apkUrl}")
            val apk = download(app.apkUrl, "${app.packageName}.apk") ?: run { log("FAIL  ${app.name}: download error"); continue }

            // pm install reads a path the shell user can see; /data/local/tmp is the standard spot.
            val tmp = "/data/local/tmp/${apk.name}"
            AdbShell.exec(ctx, "cp '${apk.absolutePath}' $tmp 2>/dev/null || cat '${apk.absolutePath}' > $tmp")
            val out = AdbShell.exec(ctx, "pm install -r -d $tmp").trim()
            AdbShell.exec(ctx, "rm -f $tmp")
            apk.delete()

            if (out.contains("Success", ignoreCase = true)) {
                prefs.setAppVersion(app.packageName, app.version)
                log("done  ${app.name} v${app.version}")
            } else log("FAIL  ${app.name}: $out")
        }
    }

    fun uninstall(packageName: String, log: (String) -> Unit) {
        val out = AdbShell.exec(ctx, "pm uninstall --user 0 $packageName").trim()
        log("rm    $packageName → $out")
    }

    private fun download(url: String, fileName: String): File? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15000; readTimeout = 60000
        }
        val file = File(ctx.cacheDir, fileName)
        conn.inputStream.use { inp -> file.outputStream().use { inp.copyTo(it) } }
        file
    } catch (e: Exception) { null }
}
