package com.galaxymedia.tvsetup

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One game on the scrolling strip. state: "in" (live), "pre" (upcoming), "post" (final). */
data class Game(val league: String, val text: String, val state: String, val start: Long)

/**
 * Today's games from ESPN's public scoreboard feed (free, no login, but unofficial:
 * ESPN can change it without notice). If anything fails, the strip simply hides.
 */
object SportsRepository {

    private class League(val sport: String, val key: String, val label: String)

    private val leagues = listOf(
        League("football", "nfl", "NFL"),
        League("basketball", "nba", "NBA"),
        League("hockey", "nhl", "NHL"),
        League("baseball", "mlb", "MLB"),
        League("soccer", "eng.1", "EPL"),
        League("soccer", "usa.1", "MLS"),
        League("mma", "ufc", "UFC")
    )

    @Volatile var games: List<Game> = emptyList()
    @Volatile var lastFetch = 0L
    @Volatile private var lastGood = 0L

    fun fetchAll(): List<Game> {
        val out = mutableListOf<Game>()
        var anyOk = false
        for (l in leagues) {
            try { out += fetchLeague(l); anyOk = true } catch (e: Exception) { /* skip this league */ }
        }
        val now = System.currentTimeMillis()
        lastFetch = now
        if (anyOk) {
            lastGood = now
            games = out.sortedWith(compareBy<Game>({ order(it.state) }, { it.start }))
        } else if (now - lastGood > 30 * 60 * 1000L) {
            games = emptyList()   // keep the last good list for a while, then give up quietly
        }
        return games
    }

    private fun order(state: String) = when (state) { "in" -> 0; "pre" -> 1; else -> 2 }

    private fun fetchLeague(l: League): List<Game> {
        val url = "https://site.api.espn.com/apis/site/v2/sports/${l.sport}/${l.key}/scoreboard"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "galaxy-media")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = mutableListOf<Game>()

        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val type = ev.optJSONObject("status")?.optJSONObject("type")
            val state = type?.optString("state", "") ?: ""
            val detail = type?.optString("shortDetail", "") ?: ""
            val start = parseDate(ev.optString("date", ""))

            // Only "today": live games, or games starting within a day-ish window
            val inWindow = start != 0L && start > now - 18 * 3600_000L && start < now + 30 * 3600_000L
            if (state != "in" && !inWindow) continue

            val text: String
            if (l.label == "UFC") {
                val name = ev.optString("shortName", "").ifBlank { ev.optString("name", "") }
                if (name.isBlank()) continue
                text = when (state) {
                    "in" -> "$name · Live"
                    "post" -> "$name · Final"
                    else -> "$name · ${whenText(start)}"
                }
            } else {
                val comp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                val cs = comp.optJSONArray("competitors") ?: continue
                var home = ""; var away = ""; var homeScore = ""; var awayScore = ""
                for (j in 0 until cs.length()) {
                    val c = cs.optJSONObject(j) ?: continue
                    val team = c.optJSONObject("team")
                    val name = team?.optString("shortDisplayName", "").orEmpty()
                        .ifBlank { team?.optString("abbreviation", "").orEmpty() }
                    val score = c.optString("score", "")
                    if (c.optString("homeAway") == "home") { home = name; homeScore = score }
                    else { away = name; awayScore = score }
                }
                if (home.isBlank() || away.isBlank()) continue
                text = when (state) {
                    "pre" -> "$away @ $home · ${whenText(start)}"
                    else -> "$away $awayScore, $home $homeScore · ${if (state == "post") detail.ifBlank { "Final" } else detail}"
                }
            }
            out += Game(l.label, text, state, start)
        }
        return out
    }

    private fun parseDate(s: String): Long {
        if (s.isBlank()) return 0L
        for (p in listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                val f = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return f.parse(s)?.time ?: continue
            } catch (e: Exception) { /* try next pattern */ }
        }
        return 0L
    }

    /** "7:20 PM" if it's today on this TV's clock, otherwise "Sat 7:20 PM". */
    private fun whenText(ms: Long): String {
        if (ms == 0L) return "TBD"
        val a = Calendar.getInstance().apply { timeInMillis = ms }
        val b = Calendar.getInstance()
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "h:mm a" else "EEE h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }
}
