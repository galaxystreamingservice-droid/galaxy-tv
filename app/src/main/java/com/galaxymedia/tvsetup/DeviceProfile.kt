package com.galaxymedia.tvsetup

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.json.JSONObject

/** What kind of box we are running on, and which tier of behavior it gets. */
data class DeviceProfile(
    val platform: String, // "fireos" | "googletv"
    val model: String, // Build.MODEL e.g. "AFTKM"
    val name: String,
    val ramGb: Double,
    val tier: String, // "full" | "lite" | "unsupported"
    val killPreviousDefault: Boolean,
    val allowAnimatedBackground: Boolean,
    /** "table": the model is in device_profiles.json. "measured": not listed, so decided from real memory. */
    val source: String = "table",
    val measuredRamGb: Double = 0.0
) {
    companion object {
        fun detect(ctx: Context): DeviceProfile {
            val json = JSONObject(ctx.assets.open("device_profiles.json").bufferedReader().readText())
            val isAmazon = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
                ctx.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
            val platform = if (isAmazon) "fireos" else "googletv"
            val model = Build.MODEL

            // Real memory, so a model that is not in the table still gets sensible settings.
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val measuredGb = Math.round(mi.totalMem / 1073741824.0 * 10) / 10.0
            val low = am.isLowRamDevice || measuredGb < 1.4

            // The table can list Google TV models too, under "googletv" (none yet).
            val listed: JSONObject? = (if (isAmazon) json.optJSONObject("fireos") else json.optJSONObject("googletv"))
                ?.optJSONObject(model)
            val entry: JSONObject = listed
                ?: json.getJSONObject("defaults").getJSONObject(if (isAmazon) "fireos_unknown" else "googletv_unknown")

            val tier = if (listed != null) entry.optString("tier", "full") else if (low) "lite" else "full"
            val behavior = json.getJSONObject("tierBehavior")
                .optJSONObject(if (tier == "unsupported") "lite" else tier) ?: JSONObject()

            return DeviceProfile(
                platform = platform,
                model = model,
                name = if (listed != null) entry.optString("name", model) else "${Build.MANUFACTURER} $model",
                ramGb = if (listed != null) entry.optDouble("ramGb", 2.0) else measuredGb,
                tier = tier,
                killPreviousDefault = behavior.optBoolean("killPreviousApp", false),
                allowAnimatedBackground = behavior.optBoolean("allowAnimatedBackground", true),
                source = if (listed != null) "table" else "measured",
                measuredRamGb = measuredGb
            )
        }
    }

    fun summary(): String =
        "$name ($model) · ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} · ${ramGb}GB · tier=$tier"
}
