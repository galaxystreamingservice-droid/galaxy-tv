package com.galaxymedia.tvsetup

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Opens a system settings screen safely on any box.
 *
 * Some boxes answer a generic settings request with a placeholder. Fire OS 8, for example, answers with
 * "CTSDummySettingsHandler", which just sends the person to the home screen. So we only start a screen that
 * resolves to something real. We try the exact screen first, then the main Settings page, and if nothing real is
 * available we show the steps in words. A person is never dropped on the home screen by mistake.
 */
object SystemSettings {
    enum class Target { MAIN, WIFI, APPS, APP_INFO, UNKNOWN_SOURCES }
    enum class Result { EXACT, GENERAL, NONE }

    private val settingsPackages = listOf("com.amazon.tv.settings.v2", "com.amazon.tv.settings", "com.android.tv.settings", "com.android.settings")
    private val placeholderWords = listOf("dummy", "cts", "compliance")

    private fun isReal(ri: ResolveInfo?): Boolean {
        val name = ri?.activityInfo?.name?.lowercase() ?: return false
        return placeholderWords.none { name.contains(it) }
    }

    private fun tryStart(ctx: Context, i: Intent): Boolean {
        val ri = try { ctx.packageManager.resolveActivity(i, 0) } catch (e: Exception) { null }
        if (!isReal(ri)) return false
        return try { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    /** hint = the path to show in words when no shortcut works, for example "Settings › Network". */
    fun open(activity: AppCompatActivity, target: Target, pkg: String? = null, hint: String = "Settings"): Result {
        val pm = activity.packageManager
        val exact = ArrayList<Intent>()
        val general = ArrayList<Intent>()
        general += Intent(Settings.ACTION_SETTINGS)
        for (p in settingsPackages) {
            val li = try { pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p) } catch (e: Exception) { null }
            if (li != null) general += li
        }
        when (target) {
            Target.MAIN -> { exact.addAll(general); general.clear() }
            Target.WIFI -> exact += Intent(Settings.ACTION_WIFI_SETTINGS)
            Target.APPS -> exact += Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            Target.APP_INFO -> if (pkg != null) exact += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            Target.UNKNOWN_SOURCES -> if (pkg != null) exact += Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:$pkg"))
        }
        for (i in exact) if (tryStart(activity, i)) return Result.EXACT
        for (i in general) if (tryStart(activity, i)) {
            Toast.makeText(activity, "Opened Settings. Go to: $hint", Toast.LENGTH_LONG).show()
            return Result.GENERAL
        }
        AlertDialog.Builder(activity)
            .setTitle("Open Settings")
            .setMessage("This box doesn't let apps open its Settings page. Press Home, open Settings (the gear icon), and go to:\n\n$hint")
            .setPositiveButton("OK", null)
            .show()
        return Result.NONE
    }
}
