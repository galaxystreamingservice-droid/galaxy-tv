package com.galaxymedia.tvsetup

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a short setup code (like GAL-4821) into the settings file link.
 * The code list is a small file in the repo: setup-codes.json, {"GAL-4821": "<32-character Gist code>"}.
 * A full link or a bare 32-character Gist code is accepted too.
 */
object SetupCodes {

    private const val CODES_URL =
        "https://raw.githubusercontent.com/galaxystreamingservice-droid/galaxy-tv/main/setup-codes.json"

    class Result(val url: String?, val error: String?)

    fun gistUrl(code: String) =
        "https://gist.githubusercontent.com/galaxystreamingservice-droid/${code.lowercase()}/raw/device-settings.json"

    private val hex32 = Regex("^[0-9a-fA-F]{32}$")

    /** Blocking. Call from a background thread. */
    fun resolve(input: String): Result {
        val raw = input.trim()
        if (raw.isEmpty()) return Result(null, "Nothing was entered.")
        if (raw.startsWith("http", ignoreCase = true)) return Result(raw, null)
        if (hex32.matches(raw)) return Result(gistUrl(raw), null)

        // "gal 4821", "GAL4821", "gal-4821" all become GAL-4821
        val clean = raw.uppercase().filter { it.isLetterOrDigit() }
        val key = if (clean.startsWith("GAL") && clean.length > 3) "GAL-" + clean.substring(3) else clean

        val body = try {
            val conn = (URL(CODES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "galaxy-media")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return Result(null, "Couldn't reach the internet. Check Wi-Fi and try again.")
        }

        val value = try {
            val obj = JSONObject(body)
            obj.keys().asSequence().firstOrNull { it.trim().uppercase() == key }?.let { obj.optString(it) }
        } catch (e: Exception) { null }

        if (value.isNullOrBlank()) return Result(null, "Code not found. Check it and try again.")
        val v = value.trim()
        return when {
            v.startsWith("http", ignoreCase = true) -> Result(v, null)
            hex32.matches(v) -> Result(gistUrl(v), null)
            else -> Result(null, "That code is set up wrong. Ask the owner.")
        }
    }
}
