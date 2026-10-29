package com.galaxymedia.tvsetup

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Current temperature and conditions from Open-Meteo (free, no key, no login).
 * If anything fails the greeting simply shows no weather.
 */
object WeatherRepository {

    class City(val name: String, val lat: String, val lon: String)

    @Volatile var text = ""
    @Volatile var lastFetch = 0L
    @Volatile var key = ""

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "galaxy-media")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /** Looks a city name up. Returns null if nothing matches. Throws if the network is down. */
    fun geocode(q: String): City? {
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=1&language=en&format=json")
        val r = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
        val name = r.optString("name", q).ifBlank { q }
        if (!r.has("latitude") || !r.has("longitude")) return null
        return City(name, r.getDouble("latitude").toString(), r.getDouble("longitude").toString())
    }

    /** Looks a US ZIP code up (zippopotam.us, free, no key). Null if the ZIP doesn't exist. Throws if the network is down. */
    fun lookupZip(zip: String): City? {
        val body = try {
            get("https://api.zippopotam.us/us/${URLEncoder.encode(zip, "UTF-8")}")
        } catch (e: java.io.FileNotFoundException) {
            return null
        }
        val p = JSONObject(body).optJSONArray("places")?.optJSONObject(0) ?: return null
        val lat = p.optString("latitude")
        val lon = p.optString("longitude")
        if (lat.isBlank() || lon.isBlank()) return null
        return City(p.optString("place name", zip).ifBlank { zip }, lat, lon)
    }

    /** Fetches the current weather into [text]. Throws on failure (and retries sooner than usual). */
    fun refresh(lat: String, lon: String) {
        val k = "$lat,$lon"
        val now = System.currentTimeMillis()
        if (k != key) text = ""
        key = k
        lastFetch = now
        try {
            val f = Locale.getDefault().country == "US"
            val unit = if (f) "fahrenheit" else "celsius"
            val body = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&temperature_unit=$unit")
            val cur = JSONObject(body).optJSONObject("current") ?: throw Exception("no data")
            val t = cur.getDouble("temperature_2m").roundToInt()
            text = "$t°${if (f) "F" else "C"} ${describe(cur.optInt("weather_code", -1))}"
        } catch (e: Exception) {
            lastFetch = now - 25 * 60_000L // try again in about 5 minutes
            throw e
        }
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Storms"
        else -> ""
    }.trim()
}
