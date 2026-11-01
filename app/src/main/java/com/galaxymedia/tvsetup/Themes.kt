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

    /** Picture wallpapers: a light runs along the neon outline drawn in each picture. */
    val pictureKeys = listOf(
        "pic_tv_ring_text", "pic_galaxy_ring_text", "pic_tv_ring", "pic_shield", "pic_shield_planet",
        "pic_swirl_ring", "pic_swirl_galaxy", "pic_monitor_ring"
    )

    fun isPicture(key: String): Boolean = key in pictureKeys

    val wallpaperKeys = listOf("stars") + pictureKeys + listOf(
        "trace", "aurora", "nebula", "sunset", "ocean", "city", "grid", "mountains", "logo", "plain"
    )

    /** Anything not listed (including the removed "team" wallpaper) shows as the starfield. */
    fun wallpaperName(key: String): String = when (key) {
        "pic_tv_ring_text" -> "Galaxy Media, solid TV"
        "pic_galaxy_ring_text" -> "Galaxy Media, outline TV"
        "pic_tv_ring" -> "TV in ring"
        "pic_shield" -> "Neon shield"
        "pic_shield_planet" -> "Planet shield"
        "pic_swirl_ring" -> "Swirl ring"
        "pic_swirl_galaxy" -> "Galaxy swirl"
        "pic_monitor_ring" -> "Monitor ring"
        "trace" -> "Frame trace"
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
