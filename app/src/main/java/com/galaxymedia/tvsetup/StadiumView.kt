package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Sports theme: the black-and-gold night stadium. The game is written live on the stadium's big screen.
 * On a holiday (only the day itself, plus Christmas Eve and New Year's Eve) the stadium dresses up: snow and a
 * light garland, falling leaves, fireworks, hearts, clovers or petals. Today's real weather (from the ZIP code)
 * falls over it. Three layers, so the big picture is drawn once and only the small moving things redraw.
 */
class StadiumView(ctx: Context, private val animate: Boolean, weatherKind: String, holidayTouches: Boolean) : FrameLayout(ctx) {

    /** What the big screen shows. Lines: top team, bottom team, then a small status line. */
    data class Board(
        val away: String, val awayScore: String,
        val home: String, val homeScore: String,
        val status: String, val live: Boolean,
        val single: String = ""            // one centred line instead of two teams (UFC, a greeting)
    )

    private val base = BaseLayer(ctx)
    private val holiday: String? = if (holidayTouches) holidayFor(Calendar.getInstance()) else null
    private val weather = if (weatherKind.isNotEmpty() && animate) WeatherLayer(ctx, weatherKind) else null

    init {
        isFocusable = false
        addView(base, LayoutParams(-1, -1))
        // Real snow replaces the Christmas snow, so it never snows twice.
        val fx = holiday?.let { EffectsLayer(ctx, it, animate, skipSnow = weatherKind == "snow") }
        if (fx != null) addView(fx, LayoutParams(-1, -1))
        if (weather != null) addView(weather, LayoutParams(-1, -1))
        base.greeting = greetingFor(holiday)
    }

    fun setBoard(b: Board?) { base.board = b; base.invalidate() }

    companion object {
        /** Where the picture's big screen is, as fractions of the picture (assets/stadium.webp). */
        private val SCREEN = RectF(0.5407f, 0.1073f, 0.8086f, 0.2922f)
        private const val ZOOM = 1.25f      // the stadium fills the screen, a little larger than the picture
        private const val CENTER_X = 0.60f  // the part of the picture in the middle of the TV
        private const val TOP = -0.055f     // nudged up so the field sits above the apps

        private var cached: Bitmap? = null
        private fun bitmap(ctx: Context): Bitmap? = cached ?: try {
            ctx.applicationContext.assets.open("stadium.webp").use { BitmapFactory.decodeStream(it) }.also { cached = it }
        } catch (e: Exception) { null }

        private var serif: Typeface? = null
        private var serifBold: Typeface? = null
        private var caps: Typeface? = null
        fun fonts(ctx: Context) {
            if (serif != null) return
            val a = ctx.applicationContext.assets
            serif = try { Typeface.createFromAsset(a, "fonts/Cardo-Regular.ttf") } catch (e: Exception) { Typeface.SERIF }
            serifBold = try { Typeface.createFromAsset(a, "fonts/Cardo-Bold.ttf") } catch (e: Exception) { Typeface.create(Typeface.SERIF, Typeface.BOLD) }
            caps = try { Typeface.createFromAsset(a, "fonts/Montserrat-Medium.ttf") } catch (e: Exception) { Typeface.SANS_SERIF }
        }

        /** The holiday the stadium dresses up for today, or null. */
        fun holidayFor(cal: Calendar): String? {
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val y = cal.get(Calendar.YEAR)
            val tg = Seasons.thanksgiving(y)
            val ea = Seasons.easterDate(y)
            return when {
                m == 12 && (d == 24 || d == 25) -> "christmas"
                (m == 12 && d == 31) || (m == 1 && d == 1) -> "newyear"
                m == 11 && d == tg.get(Calendar.DAY_OF_MONTH) -> "thanksgiving"
                m == 7 && d == 4 -> "july4"
                m == 10 && d == 31 -> "halloween"
                m == 2 && d == 14 -> "valentine"
                m == 3 && d == 17 -> "stpatricks"
                m == ea.get(Calendar.MONTH) + 1 && d == ea.get(Calendar.DAY_OF_MONTH) -> "easter"
                else -> null
            }
        }

        fun greetingFor(h: String?): String = when (h) {
            "christmas" -> "Merry Christmas"
            "newyear" -> "Happy New Year"
            "thanksgiving" -> "Happy Thanksgiving"
            "july4" -> "Happy Fourth of July"
            "halloween" -> "Happy Halloween"
            "valentine" -> "Happy Valentine’s Day"
            "stpatricks" -> "Happy St. Patrick’s Day"
            "easter" -> "Happy Easter"
            else -> ""
        }

        private val GOLD = Color.parseColor("#D6B87A")
        private val HI = Color.parseColor("#F0DAA8")
        private val IVORY = Color.parseColor("#F4EEE2")
        private val LIVE = Color.parseColor("#E0565B")
    }

    // ───────────── the picture and the big screen (redrawn only when the game changes) ─────────────

    private class BaseLayer(ctx: Context) : View(ctx) {
        var board: Board? = null
        var greeting = ""
        private val bmp = bitmap(ctx)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG)
        private val screen = RectF()
        private val dst = RectF()

        init { fonts(ctx) }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawColor(Color.rgb(5, 4, 4))
            val b = bmp ?: return
            val s = w / b.width * ZOOM
            val ox = w / 2f - CENTER_X * b.width * s
            val oy = TOP * h
            dst.set(ox, oy, ox + b.width * s, oy + b.height * s)
            canvas.drawBitmap(b, null, dst, paint)
            screen.set(ox + SCREEN.left * b.width * s, oy + SCREEN.top * b.height * s,
                ox + SCREEN.right * b.width * s, oy + SCREEN.bottom * b.height * s)
            drawBoard(canvas)
        }

        private fun drawBoard(c: Canvas) {
            val r = screen
            val bh = r.height()
            val pad = r.width() * 0.07f
            val bd = board
            text.textAlign = Paint.Align.CENTER
            if (bd == null || bd.single.isNotEmpty()) {
                val line = bd?.single?.ifEmpty { null } ?: greeting.ifEmpty { "GALAXY MEDIA" }
                val isLogo = line == "GALAXY MEDIA"
                text.typeface = if (isLogo) caps else serif
                text.color = if (isLogo) GOLD else IVORY
                text.letterSpacing = if (isLogo) 0.3f else 0f
                text.textSize = bh * (if (isLogo) 0.16f else 0.24f)
                fit(line, r.width() - 2 * pad)
                c.drawText(line, r.centerX(), r.centerY() + text.textSize * 0.30f - (if (bd != null) bh * 0.08f else 0f), text)
                if (bd != null && bd.status.isNotEmpty()) statusLine(c, bd)
                text.letterSpacing = 0f
                return
            }
            // Two rows: team name on the left, score on the right
            val rowH = bh * 0.27f
            val y1 = r.top + bh * 0.36f
            val y2 = y1 + rowH
            text.typeface = serifBold
            text.textSize = bh * 0.21f
            text.color = IVORY
            text.textAlign = Paint.Align.LEFT
            val nameW = r.width() * 0.62f
            fit(bd.away, nameW); c.drawText(bd.away, r.left + pad, y1, text)
            text.textSize = bh * 0.21f
            fit(bd.home, nameW); c.drawText(bd.home, r.left + pad, y2, text)
            text.textSize = bh * 0.23f
            text.textAlign = Paint.Align.RIGHT
            text.color = HI
            c.drawText(bd.awayScore, r.right - pad, y1, text)
            c.drawText(bd.homeScore, r.right - pad, y2, text)
            statusLine(c, bd)
        }

        private fun statusLine(c: Canvas, bd: Board) {
            val r = screen
            val bh = r.height()
            text.typeface = caps
            text.textSize = bh * 0.085f
            text.letterSpacing = 0.18f
            text.color = GOLD
            text.textAlign = Paint.Align.CENTER
            val y = r.bottom - bh * 0.14f
            val st = bd.status.uppercase()
            fit(st, r.width() * 0.8f)
            c.drawText(st, r.centerX(), y, text)
            if (bd.live) {                                  // a small red dot before a live game's status
                val tw = text.measureText(st)
                text.color = LIVE
                c.drawCircle(r.centerX() - tw / 2f - bh * 0.06f, y - text.textSize * 0.35f, bh * 0.022f, text)
            }
            text.letterSpacing = 0f
        }

        /** Shrinks the text until it fits the width. */
        private fun fit(s: String, maxW: Float) {
            while (text.textSize > 8f && text.measureText(s) > maxW) text.textSize *= 0.92f
        }
    }

    // ───────────── holiday touches (only the small moving pieces redraw) ─────────────

    private class EffectsLayer(ctx: Context, private val kind: String, private val animate: Boolean, private val skipSnow: Boolean) : View(ctx) {
        private class P(var x: Float, var y: Float, val vx: Float, val vy: Float, val size: Float, val phase: Float, val color: Int, var spin: Float)
        private class Burst(val x: Float, val y: Float, val born: Long, val color: Int, val n: Int)

        private val parts = ArrayList<P>()
        private val bursts = ArrayList<Burst>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rnd = Random(11)
        private var last = 0L
        private var nextBurst = 0L
        private val d = resources.displayMetrics.density

        init { isFocusable = false; isClickable = false }

        private val night: Boolean get() { val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return hr >= 18 || hr < 5 }
        private val fireworks get() = kind == "newyear" || (kind == "july4" && night)

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            parts.clear()
            if (w == 0) return
            val (n, colors) = when (kind) {
                "christmas" -> (if (skipSnow) 0 else 40) to intArrayOf(Color.argb(210, 245, 240, 230))
                "thanksgiving" -> 18 to intArrayOf(Color.parseColor("#C8742E"), Color.parseColor("#D6B87A"), Color.parseColor("#8E2F2A"))
                "valentine" -> 12 to intArrayOf(Color.parseColor("#D98A9A"), Color.parseColor("#E8C9A0"))
                "stpatricks" -> 14 to intArrayOf(Color.parseColor("#3F8F5A"), Color.parseColor("#6FB07E"))
                "easter" -> 18 to intArrayOf(Color.parseColor("#F2D7E0"), Color.parseColor("#F4EEC8"), Color.parseColor("#DCCFF0"))
                else -> 0 to intArrayOf(Color.WHITE)
            }
            repeat(n) {
                val up = kind == "valentine"                   // hearts float up; everything else falls
                parts += P(rnd.nextFloat() * w, rnd.nextFloat() * h,
                    (rnd.nextFloat() - 0.5f) * 14f * d,
                    (if (up) -1f else 1f) * (if (kind == "christmas") 28f + rnd.nextFloat() * 34f else 22f + rnd.nextFloat() * 26f) * d,
                    (if (kind == "christmas") 1.6f + rnd.nextFloat() * 2.2f else 5f + rnd.nextFloat() * 5f) * d,
                    rnd.nextFloat() * 6.28f, colors[rnd.nextInt(colors.size)], rnd.nextFloat() * 6.28f)
            }
            nextBurst = SystemClock.uptimeMillis() + 1500
        }

        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            val dt = if (last == 0L || !animate) 0f else ((now - last).coerceAtMost(100L)) / 1000f
            last = now
            val w = width.toFloat(); val h = height.toFloat()
            val t = now / 1000f
            when (kind) {
                "halloween" -> wash(canvas, w, h, Color.argb(70, 230, 120, 30))
                "stpatricks" -> wash(canvas, w, h, Color.argb(45, 60, 170, 90))
                "valentine" -> wash(canvas, w, h, Color.argb(40, 220, 110, 140))
                "thanksgiving" -> wash(canvas, w, h, Color.argb(40, 230, 150, 60))
            }
            if (kind == "christmas") garland(canvas, w, h, t)
            for (p in parts) {
                p.x += p.vx * dt; p.y += p.vy * dt; p.spin += dt * 1.2f
                if (p.y > h + 20) { p.y = -20f; p.x = rnd.nextFloat() * w }
                if (p.y < -20) { p.y = h + 20f; p.x = rnd.nextFloat() * w }
                val x = p.x + sin(t * 0.7f + p.phase) * 14f * d
                paint.color = p.color
                when (kind) {
                    "christmas" -> canvas.drawCircle(x, p.y, p.size, paint)
                    "thanksgiving", "easter" -> { canvas.save(); canvas.rotate(Math.toDegrees(p.spin.toDouble()).toFloat(), x, p.y)
                        canvas.drawOval(x - p.size, p.y - p.size * 0.5f, x + p.size, p.y + p.size * 0.5f, paint); canvas.restore() }
                    "valentine" -> heart(canvas, x, p.y, p.size)
                    "stpatricks" -> clover(canvas, x, p.y, p.size * 0.6f, p.spin)
                }
            }
            if (fireworks && animate) fireworks(canvas, w, h, now)
            if (animate) postInvalidateDelayed(33)             // about 30 frames a second is plenty for drifting things
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            last = 0L
            if (visibility == VISIBLE) invalidate()
        }

        private fun wash(c: Canvas, w: Float, h: Float, col: Int) {
            glow.shader = RadialGradient(w * 0.55f, h * 0.45f, w * 0.6f, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h * 0.75f, glow)
            glow.shader = null
        }

        /** Warm bulbs hung in swags along the top of the stadium, twinkling slowly. */
        private fun garland(c: Canvas, w: Float, h: Float, t: Float) {
            val swags = 5
            val top = h * 0.035f
            val sag = h * 0.05f
            paint.color = Color.argb(150, 90, 70, 40)
            paint.strokeWidth = 1.5f * d
            for (s in 0 until swags) {
                val x0 = w * s / swags; val x1 = w * (s + 1) / swags
                var px = x0; var py = top
                for (k in 1..12) {
                    val u = k / 12f
                    val x = x0 + (x1 - x0) * u; val y = top + sag * 4f * u * (1 - u)
                    c.drawLine(px, py, x, y, paint); px = x; py = y
                }
                for (k in 1..5) {
                    val u = k / 6f
                    val x = x0 + (x1 - x0) * u; val y = top + sag * 4f * u * (1 - u) + 3f * d
                    val tw = 0.65f + 0.35f * sin(t * 1.6f + s * 1.7f + k * 2.3f)
                    glow.shader = RadialGradient(x, y, 9f * d, Color.argb((120 * tw).toInt(), 255, 205, 120), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    c.drawCircle(x, y, 9f * d, glow)
                    paint.color = Color.argb((255 * tw).toInt(), 255, 226, 160)
                    c.drawCircle(x, y, 2.6f * d, paint)
                }
            }
            glow.shader = null
        }

        private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
            path.reset()
            path.moveTo(x, y + s * 0.9f)
            path.cubicTo(x - s * 1.4f, y, x - s * 0.6f, y - s, x, y - s * 0.3f)
            path.cubicTo(x + s * 0.6f, y - s, x + s * 1.4f, y, x, y + s * 0.9f)
            c.drawPath(path, paint)
        }

        private fun clover(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
            for (k in 0 until 3) {
                val ang = a + k * 2.094f
                c.drawCircle(x + cos(ang) * s, y + sin(ang) * s, s, paint)
            }
        }

        /** Gold and ivory bursts in the open sky, never over the big screen. */
        private fun fireworks(c: Canvas, w: Float, h: Float, now: Long) {
            if (now >= nextBurst) {
                val leftSide = rnd.nextBoolean()
                val x = if (leftSide) w * (0.08f + rnd.nextFloat() * 0.25f) else w * (0.82f + rnd.nextFloat() * 0.14f)
                val y = h * (0.06f + rnd.nextFloat() * 0.22f)
                val col = listOf(Color.parseColor("#F0DAA8"), Color.parseColor("#D6B87A"), Color.parseColor("#F4EEE2"),
                    if (kind == "july4") Color.parseColor("#C8494E") else Color.parseColor("#E8C27A"),
                    if (kind == "july4") Color.parseColor("#6F8FD8") else Color.parseColor("#F0DAA8"))[rnd.nextInt(5)]
                bursts += Burst(x, y, now, col, 22)
                nextBurst = now + 1400 + rnd.nextLong(2200)
            }
            val it = bursts.iterator()
            while (it.hasNext()) {
                val b = it.next()
                val age = (now - b.born) / 1000f
                if (age > 2.2f) { it.remove(); continue }
                val r = (1f - (1f - (age / 2.2f)).let { k -> k * k }) * 70f * d
                val alpha = (255 * (1f - age / 2.2f)).toInt().coerceIn(0, 255)
                paint.color = (b.color and 0x00FFFFFF) or (alpha shl 24)
                for (k in 0 until b.n) {
                    val ang = k * (6.283f / b.n)
                    val px = b.x + cos(ang) * r
                    val py = b.y + sin(ang) * r + age * age * 10f * d   // a little droop as it fades
                    c.drawCircle(px, py, 2.2f * d, paint)
                }
            }
        }
    }
}
