package com.galaxymedia.tvsetup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Opens a system settings screen safely on any box.
 *
 * Some boxes handle the generic "open Settings" requests with a placeholder. Fire OS 8 does: its Settings package contains
 * "CTSDummySettingsHandler", which just sends the person to the home screen. Asking Android what a request would open is
 * not reliable (it answered with something harmless-looking and then still launched the placeholder), so instead we look
 * inside the settings packages for a placeholder. If one is present, we never send the generic requests at all. We use only
 * explicit launcher entries that are not placeholders, and otherwise show the steps in words. A person is never dropped
 * on the home screen by mistake.
 */
object SystemSettings {
    enum class Target { MAIN, WIFI, APPS, APP_INFO, UNKNOWN_SOURCES }
    enum class Result { EXACT, GENERAL, NONE }

    private val settingsPackages = listOf("com.amazon.tv.settings.v2", "com.amazon.tv.settings", "com.android.tv.settings", "com.android.settings")
    private val placeholderWords = listOf("dummy", "cts", "compliance")

    private fun isPlaceholderName(n: String?): Boolean {
        val x = n?.lowercase() ?: return false
        return placeholderWords.any { x.contains(it) }
    }

    /** True when any settings package on this box contains a placeholder handler. */
    private fun placeholderPresent(pm: PackageManager): Boolean {
        for (p in settingsPackages) {
            val acts = try { pm.getPackageInfo(p, PackageManager.GET_ACTIVITIES).activities } catch (e: Exception) { null } ?: continue
            if (acts.any { isPlaceholderName(it.name) }) return true
        }
        return false
    }

    /** True when apps can open Settings screens directly on this box. False on Fire OS 8, where only a placeholder answers. */
    fun canOpenDirectly(ctx: Context): Boolean = !placeholderPresent(ctx.packageManager)

    private fun tryStart(ctx: Context, i: Intent): Boolean {
        val pm = ctx.packageManager
        val flags = if (i.component != null) 0 else PackageManager.MATCH_DEFAULT_ONLY
        val best = try { pm.resolveActivity(i, flags) } catch (e: Exception) { null } ?: return false
        val all = try { pm.queryIntentActivities(i, flags) } catch (e: Exception) { emptyList() }
        if (isPlaceholderName(best.activityInfo?.name) || all.any { isPlaceholderName(it.activityInfo?.name) }) return false
        return try { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    /** hint = the path to show in words when no shortcut works, for example "Settings › Network". */
    fun open(activity: AppCompatActivity, target: Target, pkg: String? = null, hint: String = "Settings"): Result {
        val pm = activity.packageManager
        val blocked = placeholderPresent(pm)

        // Explicit launcher entries of the settings packages (never the placeholder).
        val launchers = ArrayList<Intent>()
        for (p in settingsPackages) {
            val li = try { pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p) } catch (e: Exception) { null }
            if (li != null) launchers += li
        }

        val implicitExact = ArrayList<Intent>()
        val implicitMain = Intent(Settings.ACTION_SETTINGS)
        when (target) {
            Target.MAIN -> {}
            Target.WIFI -> implicitExact += Intent(Settings.ACTION_WIFI_SETTINGS)
            Target.APPS -> implicitExact += Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            Target.APP_INFO -> if (pkg != null) implicitExact += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            Target.UNKNOWN_SOURCES -> if (pkg != null) implicitExact += Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:$pkg"))
        }

        // The "Install unknown apps" screen is its own request and is not handled by the placeholder.
        if (target == Target.UNKNOWN_SOURCES) {
            for (i in implicitExact) if (tryStart(activity, i)) return Result.EXACT
        } else if (!blocked) {
            for (i in implicitExact) if (tryStart(activity, i)) return Result.EXACT
            if (tryStart(activity, implicitMain)) {
                if (target != Target.MAIN) Toast.makeText(activity, "Opened Settings. Go to: $hint", Toast.LENGTH_LONG).show()
                return Result.GENERAL
            }
        }
        for (i in launchers) if (tryStart(activity, i)) {
            if (target != Target.MAIN) Toast.makeText(activity, "Opened Settings. Go to: $hint", Toast.LENGTH_LONG).show()
            return if (target == Target.MAIN) Result.EXACT else Result.GENERAL
        }
        AlertDialog.Builder(activity)
            .setTitle("Here's where to go")
            .setMessage("Fire TV doesn't let apps open its Settings page, so this one is a quick manual step.\n\n1. Press Home on your remote.\n2. Open the gear icon (Settings).\n3. Then go to:\n\n$hint")
            .setPositiveButton("OK", null)
            .show()
        return Result.NONE
    }
}
