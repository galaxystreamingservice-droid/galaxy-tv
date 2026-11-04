package com.galaxymedia.tvsetup

import android.content.Context

/** Colors and choices shared by the home screen and the Settings screen. */
object Themes {
    val accentKeys = listOf("cyan", "purple", "green", "pink", "orange", "gold", "red", "blue", "ice")

    fun accentColor(key: String): Int = when (key) {
        "purple" -> 0xFFA78BFA.toInt()
        "green" -> 0xFF4ADE80.toInt()
        "pink" -> 0xFFF472B6.toInt()
        "orange" -> 0xFFFB923C.toInt()
        "gold" -> 0xFFFACC15.toInt()
        "red" -> 0xFFF87171.toInt()
        "blue" -> 0xFF60A5FA.toInt()
        "ice" -> 0xFFE0F2FE.toInt()
        else -> 0xFF22D3EE.toInt()
    }

    fun accentName(key: String): String = when (key) {
        "purple" -> "Purple"
        "green" -> "Green"
        "pink" -> "Pink"
        "orange" -> "Orange"
        "gold" -> "Gold"
        "red" -> "Red"
        "blue" -> "Blue"
        "ice" -> "Ice white"
        else -> "Cyan"
    }

    /** Colors for the running light. Warm colors stand out against the blue and cyan pictures. */
    val beamKeys = listOf("gold", "pink", "white", "lime", "orange", "red")

    fun beamColor(key: String): Int = when (key) {
        "pink" -> 0xFFFF4DA6.toInt()
        "white" -> 0xFFFFFFFF.toInt()
        "lime" -> 0xFFB6FF3B.toInt()
        "orange" -> 0xFFFF7A1A.toInt()
        "red" -> 0xFFFF3B3B.toInt()
        else -> 0xFFFFC84A.toInt()
    }

    fun beamName(key: String): String = when (key) {
        "pink" -> "Hot pink"
        "white" -> "White"
        "lime" -> "Lime"
        "orange" -> "Orange"
        "red" -> "Red"
        else -> "Gold"
    }

    /** Picture wallpapers: a light runs along the neon outline drawn in each picture. */
    val pictureKeys = listOf("pic_tv_ring", "pic_shield", "pic_swirl_ring", "pic_shield_planet")

    fun isPicture(key: String): Boolean = key in pictureKeys

    /** Backgrounds with a moving light or heavy animation. Hidden on lower-end sticks. */
    private val heavyKeys = listOf("trace", "aurora")

    private val allWallpapers = listOf("rotate", "stars") + pictureKeys + listOf(
        "trace", "aurora", "nebula", "sunset", "ocean", "city", "grid", "mountains", "logo", "plain"
    )

    /** What actually shows: lower-end sticks get the starfield in place of the heavy ones. */
    fun effective(key: String, lite: Boolean): String = if (lite && key in heavyKeys) "stars" else key

    /** How often the "rotate" wallpaper changes, in minutes. */
    val rotateOptions = listOf(2, 5, 10, 15, 30, 60)

    private val rotateSkip = listOf("rotate", "logo", "plain")

    fun rotatePool(lite: Boolean): List<String> = wallpapersFor(lite).filter { it !in rotateSkip }

    /** The wallpaper to show now. "rotate" picks from the pool by wall-clock time, so it keeps going across restarts. */
    /** Backgrounds drawn once and left still, so there is nothing to measure. */
    val sceneKeys = listOf("nebula", "sunset", "ocean", "city", "grid", "mountains")

    fun resolve(key: String, lite: Boolean, minutes: Int, skip: Set<String> = emptySet()): String {
        if (key != "rotate") return effective(key, lite)
        val pool = rotatePool(lite).filter { it !in skip }.ifEmpty { listOf("stars") }
        val slot = (System.currentTimeMillis() / (minutes.coerceAtLeast(1) * 60_000L)).toInt()
        return pool[Math.floorMod(slot, pool.size)]
    }

    fun wallpapersFor(lite: Boolean): List<String> = if (lite) allWallpapers.filter { it !in heavyKeys } else allWallpapers

    /** Anything not listed (including removed wallpapers) shows as the starfield. */
    fun wallpaperName(key: String): String = when (key) {
        "rotate" -> "Rotate (changes by itself)"
        "pic_tv_ring" -> "TV in ring"
        "pic_shield" -> "Neon shield"
        "pic_swirl_ring" -> "Swirl ring"
        "pic_shield_planet" -> "Planet shield"
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

    val animationKeys = listOf("auto", "on", "off")

    fun animationsName(k: String): String = when (k) {
        "on" -> "On (full)"
        "off" -> "Off (still)"
        else -> "Auto"
    }

    /** Lower-end handling: "auto" follows the stick's model, "on" and "off" never hide a background. */
    fun liteFor(ctx: Context, mode: String): Boolean = if (mode == "auto") isLite(ctx) else false

    private var liteCache: Boolean? = null

    /** True on sticks the model table does not call "full" (lower models). Detected once. */
    fun isLite(ctx: Context): Boolean {
        liteCache?.let { return it }
        val lite = try { DeviceProfile.detect(ctx.applicationContext).tier != "full" } catch (e: Exception) { false }
        liteCache = lite
        return lite
    }

    fun next(keys: List<String>, current: String): String {
        val i = keys.indexOf(current)
        return keys[(i + 1) % keys.size]
    }
}
