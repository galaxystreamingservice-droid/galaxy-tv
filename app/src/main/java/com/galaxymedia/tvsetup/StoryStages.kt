package com.galaxymedia.tvsetup

import android.content.Context
import java.util.Calendar

/**
 * The day-by-day holiday scenes. Each stage is a short looping clip plus a still picture in assets/story
 * (<id>.mp4 and <id>.webp). Days without a stage fall back to the regular seasonal picture (SeasonView).
 */
object StoryStages {

    class Stage(val id: String, val label: String)

    private var have: Set<String>? = null

    /** True when the clip for this stage is built into the app. */
    fun exists(ctx: Context, id: String): Boolean {
        val s = have ?: try {
            (ctx.applicationContext.assets.list("story") ?: emptyArray()).toSet()
        } catch (e: Exception) { emptySet() }.also { have = it }
        return "$id.mp4" in s
    }

    /** Today's stage, or null when today uses the regular seasonal picture. */
    fun stageFor(cal: Calendar): String? {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        if (m == 11) {
            val tg = thanksgivingDay(y)
            return when (d - tg) {
                -3, -2 -> "tg_brine"                                   // Monday and Tuesday
                -1 -> "tg_fryer"                                       // Wednesday
                0 -> if (cal.get(Calendar.HOUR_OF_DAY) < 12) "tg_fryer" else "tg_dinner"
                else -> if (d > tg) "tg_leftovers" else null           // before Monday: the strutting turkey
            }
        }
        if (m == 12 && d in 1..25) return "xm_d%02d".format(d)
        return null
    }

    /** Day of November that Thanksgiving falls on (fourth Thursday). */
    private fun thanksgivingDay(y: Int): Int {
        val c = Calendar.getInstance().apply { clear(); set(y, Calendar.NOVEMBER, 1) }
        val offset = (Calendar.THURSDAY - c.get(Calendar.DAY_OF_WEEK) + 7) % 7
        return 1 + offset + 21
    }

    private val twelve = listOf(
        "a partridge in a pear tree", "2 turtle doves", "3 French hens", "4 calling birds", "5 golden rings",
        "6 geese a-laying", "7 swans a-swimming", "8 cookies and milk", "9 dancing snowmen", "10 reindeer leaping",
        "11 jingle bells", "12 drummers drumming"
    )

    /** Every stage in order, with a label, for "Watch all season scenes" in Settings. */
    fun all(): List<Stage> {
        val out = ArrayList<Stage>()
        out += Stage("tg_strut", "Thanksgiving · Nov 1 to the Sunday before")
        out += Stage("tg_brine", "Thanksgiving · Monday and Tuesday: the brine")
        out += Stage("tg_fryer", "Thanksgiving · Wednesday to Thursday noon: the fryer")
        out += Stage("tg_dinner", "Thanksgiving · Thursday dinner")
        out += Stage("tg_leftovers", "Thanksgiving · Friday to Nov 30: leftovers")
        for (d in 1..25) {
            val label = when {
                d <= 7 -> "Christmas · Dec $d · ${25 - d} days to go · a new present each day"
                d <= 13 -> "Christmas · Dec $d · ${25 - d} days to go · skating week"
                d == 24 -> "Christmas Eve · Day 11: ${twelve[10]} · Santa flies by"
                d == 25 -> "Christmas Day · Day 12: ${twelve[11]} · the big present"
                else -> "12 Days of Christmas · Dec $d · Day ${d - 13}: ${twelve[d - 14]}"
            }
            out += Stage("xm_d%02d".format(d), label)
        }
        return out
    }
}
