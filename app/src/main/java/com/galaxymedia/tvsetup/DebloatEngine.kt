package com.galaxymedia.tvsetup

import android.content.Context
import org.json.JSONObject

/** Disables bloat with `pm disable-user` (reversible with `pm enable`). */
class DebloatEngine(private val ctx: Context, private val profile: DeviceProfile) {

    private val lists: JSONObject by lazy {
        val file = if (profile.platform == "fireos") "debloat_fireos.json" else "debloat_googletv.json"
        JSONObject(ctx.assets.open(file).bufferedReader().readText())
    }

    private fun group(name: String): List<String> {
        val arr = lists.optJSONArray(name) ?: return emptyList()
        return List(arr.length()) { arr.getString(it) }
    }

    fun installedPackages(): Set<String> =
        AdbShell.exec(ctx, "pm list packages")
            .lineSequence().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()

    /** Returns a human-readable log of what happened. */
    fun run(disableAlexa: Boolean, blockOta: Boolean, log: (String) -> Unit) {
        val installed = installedPackages()
        val never = group("neverTouch").toSet()
        val targets = buildList {
            addAll(group("always"))
            if (disableAlexa) addAll(group("alexa"))
            if (blockOta) addAll(group("ota"))
        }.distinct()

        for (pkg in targets) {
            when {
                pkg in never -> log("skip  $pkg (protected)")
                pkg !in installed -> log("skip  $pkg (not on this build)")
                else -> {
                    val out = AdbShell.exec(ctx, "pm disable-user --user 0 $pkg").trim()
                    log("off   $pkg → ${out.ifEmpty { "ok" }}")
                }
            }
        }
    }

    /** Re-enable everything we might have disabled (our in-app "undo setup"). */
    fun restoreAll(log: (String) -> Unit) {
        (group("always") + group("alexa") + group("ota")).distinct().forEach { pkg ->
            val out = AdbShell.exec(ctx, "pm enable $pkg").trim()
            log("on    $pkg → ${out.ifEmpty { "ok" }}")
        }
    }
}
