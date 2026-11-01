package com.galaxymedia.tvsetup

/** Colors and choices shared by the home screen and the Settings screen. */
object Themes {
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

    val wallpaperKeys = listOf(
        "stars", "trace", "aurora", "nebula", "sunset", "ocean", "city", "grid", "mountains", "logo", "plain"
    )

    /** Anything not listed (including the removed "team" wallpaper) shows as the starfield. */
    fun wallpaperName(key: String): String = when (key) {
        "trace" -> "Light trace"
        "aurora" -> "Aurora drift"
        "nebula" -> "Nebula"
        "sunset" -> "Sunset"
        "ocean" -> "Ocean"
        "city" -> "Midnight city"
        "grid" -> "Neon grid"
        "mountains" -> "Mountains"
        "logo" -> "Galaxy Media logo"
        "plain" -> "Plain dark"
        else -> "Starfield"
    }

    fun next(keys: List<String>, current: String): String {
        val i = keys.indexOf(current)
        return keys[(i + 1) % keys.size]
    }
}
