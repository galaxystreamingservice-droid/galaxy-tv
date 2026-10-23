package com.galaxymedia.tvsetup

import android.content.Context
import android.os.Build
import org.json.JSONObject

/** What kind of box we are running on, and which tier of behavior it gets. */
data class DeviceProfile(
    val platform: String,   // "fireos" | "googletv"
    val model: String,      // Build.MODEL e.g. "AFTKM"
    val name: String,
    val ramGb: Double,
    val tier: String,       // "full" | "lite" | "unsupported"
    val killPreviousDefault: Boolean,
    val allowAnimatedBackground: Boolean
) {
    companion object {
        fun detect(ctx: Context): DeviceProfile {
            val json = JSONObject(ctx.assets.open("device_profiles.json").bufferedReader().readText())
            val isAmazon = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
                    ctx.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
            val platform = if (isAmazon) "fireos" else "googletv"
            val model = Build.MODEL

            val entry: JSONObject = if (isAmazon) {
                json.getJSONObject("fireos").optJSONObject(model)
                    ?: json.getJSONObject("defaults").getJSONObject("fireos_unknown")
            } else {
                json.getJSONObject("defaults").getJSONObject("googletv_unknown")
            }

            val tier = entry.optString("tier", "full")
            val behavior = json.getJSONObject("tierBehavior")
                .optJSONObject(if (tier == "unsupported") "lite" else tier) ?: JSONObject()

            return DeviceProfile(
                platform = platform,
                model = model,
                name = entry.optString("name", model),
                ramGb = entry.optDouble("ramGb", 2.0),
                tier = tier,
                killPreviousDefault = behavior.optBoolean("killPreviousApp", false),
                allowAnimatedBackground = behavior.optBoolean("allowAnimatedBackground", true)
            )
        }
    }

    fun summary(): String =
        "$name ($model) · ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} · ${ramGb}GB · tier=$tier"
}
