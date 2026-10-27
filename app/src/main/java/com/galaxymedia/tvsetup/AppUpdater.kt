package com.galaxymedia.tvsetup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.galaxymedia.tvsetup.model.AppEntry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppStatus(val app: AppEntry, val state: String) {
    fun line(): String = state.padEnd(8) + app.name
}

/**
 * No special access needed: it compares the settings file to what is installed,
 * downloads APKs, and hands them to Android's normal installer (one tap per app).
 */
class AppUpdater(private val ctx: Context, private val prefs: Prefs) {

    fun isInstalled(pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= 26) ctx.packageManager.canRequestPackageInstalls() else true

    fun statuses(apps: List<AppEntry>): List<AppStatus> = apps.map { a ->
        val installed = isInstalled(a.packageName)
        val state = when {
            a.managedByStore -> "store"
            a.apkUrl.isBlank() || a.apkUrl.startsWith("REPLACE") -> "no-link"
            !installed -> "MISSING"
            prefs.getAppVersion(a.packageName) == 0 -> {
                // Already on the TV before we tracked it: adopt it at the current version.
                prefs.setAppVersion(a.packageName, a.version); "ok"
            }
            a.autoUpdate && a.version > prefs.getAppVersion(a.packageName) -> "UPDATE"
            else -> "ok"
        }
        AppStatus(a, state)
    }

    fun download(app: AppEntry): File? = try {
        val conn = (URL(app.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15000; readTimeout = 60000
        }
        val file = File(ctx.cacheDir, "${app.packageName}.apk")
        conn.inputStream.use { inp -> file.outputStream().use { inp.copyTo(it) } }
        file
    } catch (e: Exception) { null }

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun markPending(app: AppEntry) = prefs.markPending(app.packageName, app.version, System.currentTimeMillis())

    /** Call when the app comes back to the front: records installs that really happened. */
    fun reconcilePending(log: (String) -> Unit) {
        for (pkg in prefs.pendingPackages()) {
            val started = prefs.pendingStartedAt(pkg)
            val updatedAt = try { ctx.packageManager.getPackageInfo(pkg, 0).lastUpdateTime } catch (e: Exception) { 0L }
            if (updatedAt >= started && updatedAt > 0L) {
                prefs.setAppVersion(pkg, prefs.pendingVersion(pkg))
                log("installed $pkg")
            } else {
                log("not installed (cancelled?) $pkg")
            }
            prefs.clearPending(pkg)
        }
    }
}
