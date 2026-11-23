package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Today's weather, drawn lightly over the holiday scene (from the ZIP code in Settings):
 * rain, snow, storm (rain plus a faint lightning flicker every 30–60 s) or fog (slow drifting haze).
 * A few dozen shapes per frame, so it costs about as much as the old pictures' rain and snow.
 * It only animates while the home screen is showing.
 */
class WeatherLayer(ctx: Context, private val kind: String) : View(ctx) {

    private class Drop(var x: Float, var y: Float, val speed: Float, val len: Float, val sway: Float, val phase: Float)

    private val drops = ArrayList<Drop>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flash = Paint().apply { color = Color.WHITE }
    private val fog = Paint(Paint.ANTI_ALIAS_FLAG)
    private var last = 0L
    private var nextFlash = 0L
    private var flashAt = 0L
    private val rnd = Random(7)

    init {
        isFocusable = false
        isClickable = false
    }

    private val rainy get() = kind == "rain" || kind == "storm"

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        drops.clear()
        if (w == 0 || h == 0) return
        val n = when (kind) { "rain" -> 46; "storm" -> 60; "snow" -> 34; else -> 0 }
        val d = resources.displayMetrics.density
        repeat(n) {
            drops += if (rainy) Drop(rnd.nextFloat() * w, rnd.nextFloat() * h, (520 + rnd.nextFloat() * 260) * d, (10 + rnd.nextFloat() * 8) * d, 0f, 0f)
            else Drop(rnd.nextFloat() * w, rnd.nextFloat() * h, (30 + rnd.nextFloat() * 50) * d, (1.5f + rnd.nextFloat() * 2f) * d, (10 + rnd.nextFloat() * 25) * d, rnd.nextFloat() * 6.28f)
        }
        nextFlash = SystemClock.uptimeMillis() + 8_000L
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0f else ((now - last).coerceAtMost(100L)) / 1000f
        last = now
        val w = width.toFloat()
        val h = height.toFloat()
        val t = now / 1000f
        when (kind) {
            "rain", "storm" -> {
                paint.color = Color.argb(if (kind == "storm") 120 else 100, 190, 215, 255)
                paint.strokeWidth = 1.6f * resources.displayMetrics.density
                val slant = 0.25f                                   // the drops fall at a slight angle
                for (p in drops) {
                    p.y += p.speed * dt; p.x += p.speed * slant * dt
                    if (p.y > h) { p.y = -p.len; p.x = rnd.nextFloat() * (w + h * slant) - h * slant }
                    canvas.drawLine(p.x, p.y, p.x - p.len * slant, p.y - p.len, paint)
                }
                if (kind == "storm") {
                    if (now >= nextFlash) { flashAt = now; nextFlash = now + 30_000L + rnd.nextLong(30_000L) }
                    val a = now - flashAt
                    // two quick soft flickers, never brighter than a faint wash
                    val f = when { a < 90 -> 0.16f; a < 160 -> 0.04f; a < 260 -> 0.12f; a < 700 -> 0.12f * (1f - (a - 260) / 440f); else -> 0f }
                    if (f > 0f) { flash.alpha = (255 * f).toInt(); canvas.drawRect(0f, 0f, w, h, flash) }
                }
            }
            "snow" -> {
                paint.color = Color.argb(200, 235, 245, 255)
                for (p in drops) {
                    p.y += p.speed * dt
                    if (p.y > h + p.len) { p.y = -p.len; p.x = rnd.nextFloat() * w }
                    canvas.drawCircle(p.x + sin(t * 0.8f + p.phase) * p.sway, p.y, p.len, paint)
                }
            }
            "fog" -> {
                for (k in 0 until 3) {                              // three wide hazy banks drifting sideways
                    val cx = ((t * (12f + k * 6f) + k * w / 3f) % (w * 1.6f)) - w * 0.3f
                    val cy = h * (0.22f + 0.14f * k) + sin(t * 0.2f + k) * h * 0.02f
                    val r = w * 0.45f
                    fog.shader = RadialGradient(cx, cy, r, Color.argb(46, 220, 225, 235), Color.argb(0, 220, 225, 235), Shader.TileMode.CLAMP)
                    canvas.save(); canvas.scale(1f, 0.35f, cx, cy); canvas.drawCircle(cx, cy, r, fog); canvas.restore()
                }
            }
        }
        if (windowVisibility == VISIBLE && kind.isNotEmpty()) postInvalidateOnAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        last = 0L
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

}
