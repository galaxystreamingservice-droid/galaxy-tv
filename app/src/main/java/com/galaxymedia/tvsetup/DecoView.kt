package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The holiday decoration on the last app tile. It lives in its own small see-through view, so the steam, sparkles and
 * candle bursts that reach beyond the tile always redraw cleanly. It only moves on sticks that get motion.
 */
class DecoView(ctx: Context, private val bmp: Bitmap, private val kind: String, private val animate: Boolean, private val label: String) : View(ctx) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 20, 10)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val path = Path()
    private val t0 = SystemClock.uptimeMillis()
    private var dx = 0f
    private var dy = 0f
    private var dw = 0f
    private var dh = 0f
    private val tau = (2.0 * PI).toFloat()

    init {
        isFocusable = false
        isClickable = false
    }

    /** Where the decoration itself sits inside this view; the rest is room for steam and sparks. */
    fun setDecoRect(x: Float, y: Float, w: Float, h: Float) {
        dx = x; dy = y; dw = w; dh = h
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (dw <= 0f) return
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        if (kind == "easter") { ears(c, t); if (animate) postInvalidateDelayed(40); return }
        if (kind == "halloween") { spiderDrop(c, t); if (animate) postInvalidateDelayed(40); return }
        var rot = 0f
        var off = 0f
        var sc = 1f
        var px = 0.5f
        var py = 1f
        if (animate) when (kind) {
            "christmas" -> { rot = 5f * sin(tau * t / 2.4f); px = 0.15f; py = 0.9f }          // the hat sways
            "valentines" -> {                                                                  // a heartbeat
                val ph = (t % 1.4f) / 1.4f
                val beat = if (ph < 0.5f) max(0f, sin(ph * 2f * tau)) else 0f
                sc = 1f + 0.12f * beat; py = 0.5f
            }
            "newyear" -> { rot = 6f * sin(tau * t / 2.2f); off = -dh * 0.06f * abs(sin(tau * t / 1.1f)); py = 0.6f }  // the glasses bounce
        }
        c.save()
        c.translate(dx, dy + off)
        c.rotate(rot, dw * px, dh * py)
        c.scale(sc, sc, dw * px, dh * py)
        rect.set(0f, 0f, dw, dh)
        c.drawBitmap(bmp, null, rect, paint)
        if (label.isNotEmpty()) {                               // the year on the party glasses' brow
            text.textSize = dh * 0.27f
            c.drawText(label, dw * 0.5f, dh * 0.23f + text.textSize * 0.36f, text)
        }
        c.restore()
        if (animate) {
            when (kind) {
                "thanksgiving" -> steam(c, t)
                "july4" -> candle(c, t)
                "newyear" -> sparkles(c, t)
                "valentines" -> arrow(c, t)
                "stpatricks" -> coins(c, t)
            }
            postInvalidateDelayed(40)
        }
    }

    /** Three wisps of steam rising from the roast turkey, one after another. */
    private fun steam(c: Canvas, t: Float) {
        fx.style = Paint.Style.STROKE
        fx.strokeWidth = dh * 0.055f
        fx.strokeCap = Paint.Cap.ROUND
        for (j in 0 until 3) {
            val ph = ((t / 2.4f) + j / 3f) % 1f
            val x0 = dx + dw * (0.24f + 0.13f * j)     // rising from the turkey's back
            val y0 = dy + dh * 0.32f
            path.reset()
            for (q in 0..13) {
                val f = q / 13f
                val yy = y0 - (dh * 0.16f + dh * 0.63f * ph) * f - dh * 0.18f * ph
                val xx = x0 + dh * 0.063f * sin(f * 5f + ph * 6f + j)
                if (q == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            fx.color = Color.WHITE
            fx.alpha = (150 * sin(PI.toFloat() * ph)).toInt().coerceIn(0, 255)
            c.drawPath(path, fx)
        }
    }

    /** The roman candle fires a ball every 1.2 seconds; it bursts in red, white or blue. */
    private fun candle(c: Canvas, t: Float) {
        fx.style = Paint.Style.FILL
        val mx = dx + dw * 0.72f
        val my = dy + dh * 0.08f
        val rise = dh * 0.34f
        val side = dh * 0.16f
        val ph = (t % 1.2f) / 1.2f
        if (ph < 0.45f) {
            val p = ph / 0.45f
            val bx = mx + side * p
            val by = my - rise * p
            fx.color = Color.rgb(255, 230, 140)
            c.drawCircle(bx, by, dh * 0.045f, fx)
            for (q in 1..4) {
                fx.color = Color.argb(200 - q * 40, 255, 180, 80)
                c.drawCircle(bx - side * 0.07f * q, by + rise * 0.07f * q, dh * 0.02f, fx)
            }
        } else {
            val p = (ph - 0.45f) / 0.55f
            val bx = mx + side
            val by = my - rise
            val cols = intArrayOf(Color.rgb(255, 80, 90), Color.WHITE, Color.rgb(110, 160, 255))
            fx.color = cols[(t / 1.2f).toInt() % 3]
            fx.alpha = (255 * (1f - p)).toInt().coerceIn(0, 255)
            val r = dh * 0.04f + dh * 0.19f * (1f - (1f - p) * (1f - p))
            for (j in 0 until 12) {
                val a = 2.0 * PI * j / 12
                c.drawCircle(bx + r * cos(a).toFloat(), by + r * sin(a).toFloat(), dh * 0.026f, fx)
            }
        }
    }

    /** Twinkling stars around the New Year glasses. */
    private fun sparkles(c: Canvas, t: Float) {
        fx.style = Paint.Style.FILL
        val pts = arrayOf(floatArrayOf(-0.10f, -0.10f), floatArrayOf(1.08f, -0.04f), floatArrayOf(0.5f, -0.28f), floatArrayOf(1.02f, 0.95f))
        for ((j, pt) in pts.withIndex()) {
            val a = max(0f, sin(tau * (t / 1.6f) + j * 2.1f))
            fx.color = Color.rgb(255, 240, 170)
            fx.alpha = (255 * a).toInt().coerceIn(0, 255)
            val sx = dx + dw * pt[0]
            val sy = dy + dh * pt[1]
            val r = dh * 0.13f * (0.6f + 0.4f * a)
            path.reset()
            path.moveTo(sx, sy - r); path.lineTo(sx + r * 0.3f, sy - r * 0.3f); path.lineTo(sx + r, sy); path.lineTo(sx + r * 0.3f, sy + r * 0.3f)
            path.lineTo(sx, sy + r); path.lineTo(sx - r * 0.3f, sy + r * 0.3f); path.lineTo(sx - r, sy); path.lineTo(sx - r * 0.3f, sy - r * 0.3f)
            path.close()
            c.drawPath(path, fx)
        }
    }

    private fun ease(x: Float): Float = x * x * (3f - 2f * x)

    /** Two bunny ears standing on top of the tile, each moving on its own, with a quick flick now and then. */
    private fun ears(c: Canvas, t: Float) {
        val ew = dw * 0.30f
        val baseY = dy + dh
        for (side in 0..1) {
            val cx = dx + dw * (if (side == 0) 0.32f else 0.68f)
            val dir = if (side == 0) -1f else 1f
            var rot = 10f * dir
            if (animate) {
                rot += 5f * sin(tau * t / 2.2f + side * 1.3f)
                val ph = (t + side * 1.1f) % 3.2f
                if (ph < 0.35f) rot += dir * 14f * sin(PI.toFloat() * ph / 0.35f)
            }
            c.save()
            c.rotate(rot, cx, baseY)
            rect.set(cx - ew / 2f, baseY - dh, cx + ew / 2f, baseY)
            c.drawBitmap(bmp, null, rect, paint)
            c.restore()
        }
    }

    /** The spider lets itself down on its thread onto the tile, waits, climbs back up, then rests. */
    private fun spiderDrop(c: Canvas, t: Float) {
        val ph = if (animate) (t % 4f) / 4f else 0f
        val drop = when {
            ph < 0.40f -> ease(ph / 0.40f)
            ph < 0.55f -> 1f
            ph < 0.90f -> 1f - ease((ph - 0.55f) / 0.35f)
            else -> 0f
        }
        val off = dh * 1.3f * drop
        val ax = dx + dw * 0.5f
        fx.style = Paint.Style.STROKE
        fx.strokeWidth = max(2f, dh * 0.035f)
        fx.color = Color.argb(200, 230, 230, 240)
        c.drawLine(ax, dy - dh * 0.8f, ax, dy + off + dh * 0.15f, fx)
        rect.set(dx, dy + off, dx + dw, dy + off + dh)
        c.drawBitmap(bmp, null, rect, paint)
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y - r); path.lineTo(x + r * 0.3f, y - r * 0.3f); path.lineTo(x + r, y); path.lineTo(x + r * 0.3f, y + r * 0.3f)
        path.lineTo(x, y + r); path.lineTo(x - r * 0.3f, y + r * 0.3f); path.lineTo(x - r, y); path.lineTo(x - r * 0.3f, y - r * 0.3f)
        path.close()
        c.drawPath(path, fx)
    }

    private fun smallHeart(c: Canvas, x: Float, y: Float, r: Float) {
        c.drawCircle(x - r * 0.5f, y - r * 0.2f, r * 0.55f, fx)
        c.drawCircle(x + r * 0.5f, y - r * 0.2f, r * 0.55f, fx)
        path.reset()
        path.moveTo(x - r * 1.02f, y); path.lineTo(x + r * 1.02f, y); path.lineTo(x, y + r * 1.15f); path.close()
        c.drawPath(path, fx)
    }

    /** The beating heart fires a little love arrow up and away every 1.8 seconds, trailing tiny hearts. */
    private fun arrow(c: Canvas, t: Float) {
        val ph = (t % 1.8f) / 1.8f
        if (ph > 0.55f) return
        val p = ph / 0.55f
        val sx = dx + dw * 0.55f
        val sy = dy + dh * 0.45f
        val ex = sx + dw * 1.5f
        val ey = sy - dh * 1.1f
        val fade = if (p > 0.7f) 1f - (p - 0.7f) / 0.3f else 1f
        fx.style = Paint.Style.FILL
        for (q in 1..3) {
            val f = p - q * 0.12f
            if (f <= 0f) continue
            fx.color = Color.rgb(255, 120, 170)
            fx.alpha = (200 * fade * (1f - q * 0.25f)).toInt().coerceIn(0, 255)
            smallHeart(c, sx + (ex - sx) * f, sy + (ey - sy) * f, dh * 0.07f)
        }
        val x = sx + (ex - sx) * p
        val y = sy + (ey - sy) * p
        val len = dh * 0.7f
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(atan2((ey - sy).toDouble(), (ex - sx).toDouble())).toFloat())
        fx.style = Paint.Style.STROKE
        fx.strokeWidth = dh * 0.05f
        fx.strokeCap = Paint.Cap.ROUND
        fx.color = Color.rgb(255, 235, 200)
        fx.alpha = (255 * fade).toInt().coerceIn(0, 255)
        c.drawLine(-len, 0f, 0f, 0f, fx)
        fx.color = Color.rgb(255, 120, 170)
        fx.alpha = (255 * fade).toInt().coerceIn(0, 255)
        c.drawLine(-len, 0f, -len - dh * 0.1f, -dh * 0.1f, fx)
        c.drawLine(-len, 0f, -len - dh * 0.1f, dh * 0.1f, fx)
        fx.style = Paint.Style.FILL
        fx.color = Color.rgb(255, 70, 120)
        fx.alpha = (255 * fade).toInt().coerceIn(0, 255)
        path.reset()
        path.moveTo(dh * 0.18f, 0f); path.lineTo(-dh * 0.04f, -dh * 0.1f); path.lineTo(-dh * 0.04f, dh * 0.1f); path.close()
        c.drawPath(path, fx)
        c.restore()
    }

    /** Coins hop over the rim of the pot and tumble down, flipping as they fall; the heap glints. */
    private fun coins(c: Canvas, t: Float) {
        fx.style = Paint.Style.FILL
        for (k in 0..1) {
            val ph = ((t + k * 0.75f) % 1.5f) / 1.5f
            val sx = dx + dw * 0.86f
            val sy = dy + dh * 0.32f
            val x = sx + dw * 0.35f * ph
            val y = sy - dh * 0.12f * sin(PI.toFloat() * min(ph * 2f, 1f)) + dh * 1.2f * ph * ph
            val flip = max(0.15f, abs(cos(ph * 9f)))
            val r = dh * 0.11f
            fx.color = Color.rgb(255, 205, 60)
            fx.alpha = (255 * (1f - ph)).toInt().coerceIn(0, 255)
            rect.set(x - r * flip, y - r * 0.55f, x + r * flip, y + r * 0.55f)
            c.drawOval(rect, fx)
        }
        for ((j, pt) in arrayOf(floatArrayOf(0.36f, 0.08f), floatArrayOf(0.62f, 0.04f)).withIndex()) {
            val a = max(0f, sin(tau * (t / 1.4f) + j * 2.4f))
            fx.color = Color.rgb(255, 250, 210)
            fx.alpha = (255 * a).toInt().coerceIn(0, 255)
            star(c, dx + dw * pt[0], dy + dh * pt[1], dh * 0.12f * (0.6f + 0.4f * a))
        }
    }
}
