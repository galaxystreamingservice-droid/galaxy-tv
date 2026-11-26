package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

/** The premium look shared by the smaller screens: black, champagne gold, ivory, Cardo. High contrast keeps the plain look. */
object Gold {
    val BG = Color.parseColor("#050403")
    val GOLD = Color.parseColor("#D6B87A")
    val GOLD_HI = Color.parseColor("#F0DAA8")
    val IVORY = Color.parseColor("#F4EEE2")
    val MUTED = Color.argb(170, 244, 238, 226)
    private val PANEL = Color.parseColor("#0B0907")
    private val PANEL_FOCUS = Color.parseColor("#1A150C")

    private var serifCache: Typeface? = null
    private var capsCache: Typeface? = null

    fun on(p: Prefs): Boolean = !p.highContrast

    fun serif(ctx: Context): Typeface = serifCache ?: load(ctx, "Cardo-Regular").also { serifCache = it }
    fun caps(ctx: Context): Typeface = capsCache ?: load(ctx, "Montserrat-Medium").also { capsCache = it }

    private fun load(ctx: Context, name: String): Typeface =
        try { Typeface.createFromAsset(ctx.assets, "fonts/$name.ttf") } catch (e: Exception) { Typeface.DEFAULT }

    fun page(p: Prefs, plain: Int): Int = if (on(p)) BG else plain

    /** Button background: plain look keeps the accent fill; premium is a dark panel with a gold line, brighter when selected. */
    fun button(p: Prefs, dp: (Int) -> Int, accent: Int, plainFill: Int, plainLine: Int, focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        if (on(p)) {
            if (focused) { setColor(PANEL_FOCUS); setStroke(dp(3), GOLD_HI) }
            else { setColor(PANEL); setStroke(dp(1), (GOLD and 0x00FFFFFF) or 0x66000000) }
        } else {
            if (focused) { setColor(accent); setStroke(dp(3), Color.WHITE) } else { setColor(plainFill); setStroke(dp(1), plainLine) }
        }
    }

    fun buttonText(p: Prefs, focused: Boolean): Int =
        if (on(p)) (if (focused) GOLD_HI else IVORY) else (if (focused) Color.BLACK else Color.WHITE)

    fun title(p: Prefs, ctx: Context, tv: android.widget.TextView) {
        if (on(p)) { tv.setTextColor(IVORY); tv.typeface = serif(ctx) }
        else { tv.setTextColor(Color.WHITE); tv.setTypeface(null, Typeface.BOLD) }
    }

    fun body(p: Prefs, plain: Int): Int = if (on(p)) MUTED else plain

    /** A gold-edged pill instead of the plain system toast. High contrast keeps the system toast. */
    fun toast(ctx: Context, msg: CharSequence, length: Int) {
        val p = Prefs(ctx)
        val t = android.widget.Toast.makeText(ctx, msg, length)
        if (on(p)) {
            val d = ctx.resources.displayMetrics.density
            val tv = android.widget.TextView(ctx).apply {
                text = msg
                textSize = 18f
                typeface = serif(ctx)
                setTextColor(IVORY)
                gravity = android.view.Gravity.CENTER
                setPadding((26 * d).toInt(), (12 * d).toInt(), (26 * d).toInt(), (12 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 40 * d
                    setColor(Color.parseColor("#F00B0907"))
                    setStroke((1 * d).toInt(), GOLD)
                }
            }
            @Suppress("DEPRECATION")
            t.view = tv
        }
        t.show()
    }
}
