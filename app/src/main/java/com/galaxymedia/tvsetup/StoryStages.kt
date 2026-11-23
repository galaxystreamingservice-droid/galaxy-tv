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

    /** Stand-in stages that show the regular seasonal picture: the scene name, or null for a real clip. */
    fun oldScene(id: String): String? = if (id.startsWith("old_")) id.removePrefix("old_") else null

    /** True when the clip for this stage is built into the app (stand-ins always count). */
    fun exists(ctx: Context, id: String): Boolean {
        if (oldScene(id) != null) return true
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
        if (m == 12 && d >= 26) return "ny_d$d"                      // countdown tower (Dec 31: 11:59)
        if (m == 1 && d <= 7) return "ny_jan%02d".format(d)           // Jan 1 smoke trail, Jan 2–6 journey, Jan 7 arrival
        if (m == 1) return "ny_galaxy"                               // Galaxy January
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

    /**
     * The pretend date for a stage, so the Season test shows that day's greeting, countdown, date line and
     * tile decoration too (not just the picture). Evening time, except Thanksgiving dinner and the midnight launch.
     */
    fun dateFor(id: String): Calendar {
        val y = Calendar.getInstance().get(Calendar.YEAR)
        fun d(m: Int, day: Int, h: Int = 20) = Calendar.getInstance().apply { clear(); set(y, m - 1, day, h, 0) }
        fun plus(c: Calendar, n: Int, h: Int = 20) = (c.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, n); set(Calendar.HOUR_OF_DAY, h) }
        val tg = Seasons.thanksgiving(y)
        return when {
            id == "old_thanksgiving" -> d(11, 10)
            id == "tg_brine" -> plus(tg, -3)
            id == "tg_fryer" -> plus(tg, -1)
            id == "tg_dinner" -> plus(tg, 0, 18)
            id == "tg_leftovers" -> plus(tg, 1)
            id.startsWith("xm_d") -> d(12, id.removePrefix("xm_d").toIntOrNull() ?: 1)
            id == "ny_launch" -> d(1, 1, 0)
            id.startsWith("ny_jan") -> d(1, id.removePrefix("ny_jan").toIntOrNull() ?: 1)
            id.startsWith("ny_d") -> d(12, id.removePrefix("ny_d").toIntOrNull() ?: 31)
            id == "ny_galaxy" -> d(1, 15)
            id == "old_valentines" -> d(2, 12)
            id == "old_stpatricks" -> d(3, 15)
            id == "old_easter" -> plus(Seasons.easterDate(y), -2)
            id == "old_july4" -> d(7, 2)
            id == "old_halloween" -> d(10, 28)
            else -> d(9, 10)
        }
    }

    /** Every stage in order, with a label, for "Watch all season scenes" in Settings. */
    fun all(): List<Stage> {
        val out = ArrayList<Stage>()
        out += Stage("old_thanksgiving", "Thanksgiving · Nov 1 to the Sunday before")
        out += Stage("tg_brine", "Thanksgiving · Monday and Tuesday: the brine")
        out += Stage("tg_fryer", "Thanksgiving · Wednesday to Thursday noon: the fryer")
        out += Stage("tg_dinner", "Thanksgiving · Thursday dinner")
        out += Stage("tg_leftovers", "Thanksgiving · Friday to Nov 30: leftovers")
        for (d in 1..25) {
            val label = when {
                d == 1 -> "Christmas · Dec 1 · the kids arrive, only the G star shines"
                d <= 8 -> "Christmas · Dec $d · lighting the tree, ornament $d"
                d == 13 -> "Christmas · Dec 13 · the very long letter (and the puppy)"
                d <= 17 -> "Christmas · Dec $d · letters to Santa, ${d - 8} in the sky"
                d == 21 -> "Christmas · Dec 21 · longest night, the reindeer touch noses"
                d <= 23 -> "Christmas · Dec $d · Santa is on his way"
                d == 24 -> "Christmas Eve · the night before Christmas"
                else -> "Christmas morning"
            }
            out += Stage("xm_d%02d".format(d), label)
        }
        // Holidays still waiting for their new art: today's simple seasonal picture stands in ("old_" + scene).
        for (d in 26..30) out += Stage("ny_d$d", "New Year · Dec $d · countdown tower: ${32 - d} days to go")
        out += Stage("ny_d31", "New Year's Eve · Dec 31 · the tower shows 11:59")
        out += Stage("ny_launch", "Midnight · the G ball drops and blasts off (plays once on Jan 1)")
        out += Stage("ny_jan01", "Jan 1 · smoke trail, the ball heads for space")
        out += Stage("ny_jan02", "Jan 2 · leaving Earth")
        out += Stage("ny_jan03", "Jan 3 · passing the Moon")
        out += Stage("ny_jan04", "Jan 4 · the asteroid field (BONK)")
        out += Stage("ny_jan05", "Jan 5 · past the planets")
        out += Stage("ny_jan06", "Jan 6 · a comet races it")
        out += Stage("ny_jan07", "Jan 7 · it arrives and becomes the G planet")
        out += Stage("ny_galaxy", "Galaxy January · Jan 8–31 · laps around the G")
        out += Stage("old_valentines", "Valentine's · current picture (new art coming)")
        out += Stage("old_stpatricks", "St. Patrick's · current picture (new art coming)")
        out += Stage("old_easter", "Easter · current picture (new art coming)")
        out += Stage("old_july4", "July 4 · current picture (new art coming)")
        out += Stage("old_halloween", "Halloween · current picture (new art coming)")
        out += Stage("old_galaxy", "Between holidays · Galaxy picture")
        return out
    }
}
