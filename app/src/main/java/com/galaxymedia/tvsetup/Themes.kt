package com.galaxymedia.tvsetup

/** Colors and choices shared by the home screen and the Settings screen. */
object Themes {
    data class TeamTheme(val key: String, val name: String, val bg: Int, val stripe: Int)

    val accentKeys = listOf("cyan", "purple", "green")

    fun accentColor(key: String): Int = when (key) {
        "purple" -> 0xFFA78BFA.toInt()
        "green" -> 0xFF4ADE80.toInt()
        else -> 0xFF22D3EE.toInt()
    }

    fun accentName(key: String): String = when (key) {
        "purple" -> "Purple"
        "green" -> "Green"
        else -> "Cyan"
    }

    val wallpaperKeys = listOf("stars", "logo", "team", "plain")

    fun wallpaperName(key: String): String = when (key) {
        "logo" -> "Galaxy Media logo"
        "team" -> "Team colors"
        "plain" -> "Plain dark"
        else -> "Starfield"
    }

    // Team colors are drawn by the app: no logos.
    val teams = listOf(
        TeamTheme("cowboys", "Cowboys", 0xFF041E42.toInt(), 0xFF869397.toInt()),
        TeamTheme("heat", "Heat", 0xFF1A0A10.toInt(), 0xFFF9A01B.toInt()),
        TeamTheme("marlins", "Marlins", 0xFF06202E.toInt(), 0xFF00A3E0.toInt()),
        TeamTheme("dolphins", "Dolphins", 0xFF002E33.toInt(), 0xFFFC4C02.toInt())
    )

    fun team(key: String): TeamTheme = teams.firstOrNull { it.key == key } ?: teams[0]

    fun next(keys: List<String>, current: String): String {
        val i = keys.indexOf(current)
        return keys[(i + 1) % keys.size]
    }
}
