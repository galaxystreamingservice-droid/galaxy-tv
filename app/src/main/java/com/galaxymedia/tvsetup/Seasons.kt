package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.util.Calendar

/** Which seasonal picture to show today, and the text and color that go with it. The scenes live in assets/seasons. */
object Seasons {
    private var cache: JSONObject? = null

    private fun meta(ctx: Context): JSONObject? {
        cache?.let { return it }
        return try {
            val txt = ctx.applicationContext.assets.open("seasons/seasons.json").bufferedReader().use { it.readText() }
            JSONObject(txt).also { cache = it }
        } catch (e: Exception) { null }
    }

    fun info(ctx: Context, scene: String): JSONObject? = meta(ctx)?.optJSONObject(scene)

    fun greeting(ctx: Context, scene: String): String = info(ctx, scene)?.optString("greeting", "") ?: ""

    fun accent(ctx: Context, scene: String): Int =
        try { Color.parseColor(info(ctx, scene)?.optString("accent", "#22D3EE") ?: "#22D3EE") } catch (e: Exception) { Color.parseColor("#22D3EE") }

    /** Dates are the same every year, except Easter, which moves. Anything not listed shows the plain Galaxy picture. */
    fun sceneFor(cal: Calendar): String {
        val md = (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
        fun between(a: Int, b: Int) = md in a..b
        return when {
            between(1227, 1231) || between(101, 107) -> "newyear"
            between(1201, 1226) -> "christmas"
            between(201, 214) -> "valentines"
            between(310, 317) -> "stpatricks"
            inEasterWindow(cal) -> "easter"
            between(628, 705) -> "july4"
            between(1010, 1031) -> "halloween"
            between(1101, 1130) -> "thanksgiving"
            else -> "galaxy"
        }
    }

    /** The two weeks up to and including Easter Sunday. */
    private fun inEasterWindow(cal: Calendar): Boolean {
        val y = cal.get(Calendar.YEAR)
        val a = y % 19; val b = y / 100; val c = y % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        val easter = Calendar.getInstance().apply { clear(); set(y, month - 1, day) }
        val start = (easter.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -14) }
        val today = Calendar.getInstance().apply { clear(); set(y, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)) }
        return !today.before(start) && !today.after(easter)
    }
}
