package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import org.json.JSONObject
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * A seasonal wallpaper. Stronger sticks get a base picture plus a few small moving pieces (leaves, snow, fireworks...)
 * and, for some scenes, moving layers on the letter G. Lower-end sticks, and "Animations: Off", get the still picture.
 * Each scene is described in assets/seasons/seasons.json, so a new scene is data and not code.
 */
class SeasonView(ctx: Context, private val scene: String, private val motion: String, private val weather: String = "") : View(ctx) {

    private val artW = 1920f
    private val artH = 1080f
    private val animate = motion != "off"
    private val speed = if (motion == "reduced") 0.55f else 1f

    private class Layer(
        val bmp: Bitmap, val x: Float, val y: Float, val px: Float, val py: Float,
        val sway: Float, val bob: Float, val tilt: Float, val period: Float, val phase: Float
    )

    private var base: Bitmap? = null
    private var layers: List<Layer> = emptyList()
    private var back: List<(Canvas, Float) -> Unit> = emptyList()
    private var front: List<(Canvas, Float) -> Unit> = emptyList()
    private val t0 = SystemClock.uptimeMillis()
    private var started = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** The clock and greeting sit top left: moving pieces are not drawn there, so the time stays easy to read. */
    private fun inClock(x: Float, y: Float): Boolean =
        (x < 660f && y < 430f) ||                       // clock, greeting, date, status chips
        (x > 1480f && y < 200f) ||                      // Contact and the gear
        (y > 470f && x > 200f && x < 1720f)             // the greeting lane, the ticker and the apps: kept calm

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (started) return
        started = true
        Thread { load() }.start()
    }

    private fun open(name: String, opts: BitmapFactory.Options): Bitmap? = try {
        context.applicationContext.assets.open("seasons/$name").use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) { null }

    private fun load() {
        val info = Seasons.info(context, scene) ?: return
        val still = info.optString("still")
        val baseName = if (animate) info.optString("base", still) else still
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = if (animate) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        val b = open(baseName, opts) ?: open(still, opts) ?: return
        val ls = ArrayList<Layer>()
        val bk = ArrayList<(Canvas, Float) -> Unit>()
        val fr = ArrayList<(Canvas, Float) -> Unit>()
        if (animate) {
            val lo = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val la = info.optJSONArray("layers")
            if (la != null) for (i in 0 until la.length()) {
                val o = la.getJSONObject(i)
                val bmp = open(o.getString("file"), lo) ?: continue
                val pv = o.optJSONArray("pivot")
                ls += Layer(
                    bmp, o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    pv?.getDouble(0)?.toFloat() ?: 0f, pv?.getDouble(1)?.toFloat() ?: 0f,
                    o.optDouble("sway", 0.0).toFloat(), o.optDouble("bob", 0.0).toFloat(), o.optDouble("tilt", 0.0).toFloat(),
                    o.optDouble("period", 3.0).toFloat(), o.optDouble("phase", 0.0).toFloat()
                )
            }
            val ea = info.optJSONArray("emitters")
            val rnd = Random(scene.hashCode().toLong())
            if (ea != null) for (i in 0 until ea.length()) {
                val o = ea.getJSONObject(i)
                val fx = try { build(o, rnd) } catch (e: Exception) { null } ?: continue
                if (o.optString("z") == "back") bk += fx else fr += fx
            }
            // Weather-aware: light rain or snow when the forecast says so (Christmas already has its own snow).
            val extra = when {
                weather == "rain" -> JSONObject("{\"type\":\"fall\",\"sprite\":\"drop\",\"count\":46,\"speed\":[520,780],\"sway\":[0,4],\"size\":[0.7,1.1],\"angle\":14,\"tint\":[\"#BFD8FF\"],\"alpha\":110}")
                weather == "snow" && scene != "christmas" -> JSONObject("{\"type\":\"fall\",\"sprite\":\"snow\",\"count\":34,\"speed\":[40,90],\"sway\":[10,40],\"size\":[0.5,1.1],\"spin\":[-20,20],\"tint\":[\"#EBF5FF\"],\"alpha\":200}")
                else -> null
            }
            if (extra != null) { val fx = try { build(extra, rnd) } catch (e: Exception) { null }; if (fx != null) fr += fx }
        }
        post {
            base = b; layers = ls; back = bk; front = fr
            invalidate()
        }
    }

    override fun onDraw(c: Canvas) {
        val b = base ?: return
        val s = max(width / artW, height / artH)
        c.save()
        c.translate((width - artW * s) / 2f, (height - artH * s) / 2f)
        c.scale(s, s)
        c.drawBitmap(b, 0f, 0f, paint)
        if (animate) {
            val t = (SystemClock.uptimeMillis() - t0) / 1000f * speed
            for (fx in back) fx(c, t)
            for (l in layers) drawLayer(c, l, t)
            for (fx in front) fx(c, t)
        }
        dayTint()?.let { c.drawRect(0f, 0f, artW, artH, it) }
        c.restore()
        if (animate) postInvalidateDelayed(40) else postInvalidateDelayed(60_000L)   // the still picture still follows the time of day
    }

    private val tintPaint = Paint()
    private var tintHour = -1

    /** A little darker late at night, a touch warmer in the evening, a little brighter in the morning. */
    private fun dayTint(): Paint? {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (h != tintHour) {
            tintHour = h
            tintPaint.color = when (h) {
                in 6..9 -> Color.argb(18, 255, 241, 214)
                in 19..21 -> Color.argb(40, 10, 6, 18)
                in 22..23, in 0..5 -> Color.argb(70, 5, 10, 30)
                else -> Color.TRANSPARENT
            }
        }
        return if (Color.alpha(tintPaint.color) == 0) null else tintPaint
    }

    private fun drawLayer(c: Canvas, l: Layer, t: Float) {
        val w = (2.0 * PI * t / l.period).toFloat()
        val rot = l.sway * sin(w) + l.tilt * sin(w + l.phase)
        val dy = l.bob * sin(w + l.phase)
        c.save()
        c.translate(0f, dy)
        if (rot != 0f) c.rotate(rot, l.px, l.py)
        c.drawBitmap(l.bmp, l.x, l.y, paint)
        c.restore()
    }

    // ------------------------------------------------------------------ small helpers

    private fun rng(o: JSONObject, key: String, a: Float, b: Float): FloatArray {
        val r = o.optJSONArray(key) ?: return floatArrayOf(a, b)
        return floatArrayOf(r.getDouble(0).toFloat(), r.getDouble(1).toFloat())
    }

    private fun pick(r: FloatArray, rnd: Random): Float = r[0] + rnd.nextFloat() * (r[1] - r[0])

    private fun tints(o: JSONObject): IntArray {
        val a = o.optJSONArray("tint") ?: return IntArray(0)
        return IntArray(a.length()) { Color.parseColor(a.getString(it)) }
    }

    private fun tintPaint(color: Int, a: Int): Paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        alpha = a
    }

    private fun drawSprite(c: Canvas, bmp: Bitmap, x: Float, y: Float, sx: Float, sy: Float, rot: Float, p: Paint) {
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        c.scale(sx, sy)
        c.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, p)
        c.restore()
    }

    // ------------------------------------------------------------------ the moving pieces

    private fun build(o: JSONObject, rnd: Random): ((Canvas, Float) -> Unit)? = when (o.getString("type")) {
        "fall" -> buildFall(o, rnd, false)
        "rise" -> buildFall(o, rnd, true)
        "twinkle" -> buildTwinkle(o, rnd)
        "blink" -> buildBlink(o)
        "fly" -> buildFly(o, rnd)
        "burst" -> buildBurst(o)
        "bob" -> buildBob(o)
        "orbit" -> buildOrbit(o)
        "streak" -> buildStreak(o)
        else -> null
    }

    /** Leaves, snow, confetti and petals fall; hearts rise. They stay out of the middle column, where the G is. */
    private fun buildFall(o: JSONObject, rnd: Random, rise: Boolean): (Canvas, Float) -> Unit {
        val sprite = Sprites.get(o.getString("sprite"))
        val n = (o.optInt("count", 20) * 0.6f).toInt().coerceAtLeast(4)   // calmer: fewer pieces
        val sp = rng(o, "speed", 40f, 90f).let { floatArrayOf(it[0] * 0.85f, it[1] * 0.85f) }   // and a little slower
        val sz = rng(o, "size", 0.7f, 1.3f)
        val sw = rng(o, "sway", 10f, 40f)
        val spin = rng(o, "spin", -40f, 40f)
        val cols = tints(o)
        val alphaVal = o.optInt("alpha", 215)
        val av = o.optJSONArray("avoid")
        val a0 = av?.getDouble(0)?.toFloat() ?: 0f
        val span = (av?.getDouble(1)?.toFloat() ?: 0f) - a0
        val fixedAngle: Float? = if (o.has("angle")) o.getDouble("angle").toFloat() else null   // rain falls at one slant
        class P(val x0: Float, val y0: Float, val v: Float, val s: Float, val sw: Float, val ph: Float, val r0: Float, val vr: Float, val paint: Paint)
        val ps = List(n) {
            var x = rnd.nextFloat() * (artW - max(span, 0f))
            if (span > 0f && x > a0) x += span
            P(
                x, rnd.nextFloat() * (artH + 120f), pick(sp, rnd), pick(sz, rnd), pick(sw, rnd), rnd.nextFloat() * 6.28f,
                fixedAngle ?: (rnd.nextFloat() * 360f), if (fixedAngle != null) 0f else pick(spin, rnd),
                if (cols.isEmpty()) Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = alphaVal } else tintPaint(cols[rnd.nextInt(cols.size)], alphaVal)
            )
        }
        return { c, t ->
            for (p in ps) {
                val travel = (p.y0 + p.v * t) % (artH + 120f)
                val y = if (rise) artH + 60f - travel else travel - 60f
                val x = p.x0 + p.sw * sin(t * 1.3f + p.ph)
                if (inClock(x, y)) continue
                drawSprite(c, sprite, x, y, p.s, p.s, p.r0 + p.vr * t, p.paint)
            }
        }
    }

    private fun buildTwinkle(o: JSONObject, rnd: Random): (Canvas, Float) -> Unit {
        val n = o.optInt("count", 50)
        val xs = FloatArray(n) { rnd.nextFloat() * artW }
        val ys = FloatArray(n) { rnd.nextFloat() * artH }
        val ph = FloatArray(n) { rnd.nextFloat() * 6.28f }
        val sp = FloatArray(n) { 0.6f + rnd.nextFloat() }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 242, 220) }
        return { c, t ->
            for (i in 0 until n) {
                val a = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * sp[i] * 2.4f + ph[i]))
                p.alpha = (200 * a).toInt()
                c.drawCircle(xs[i], ys[i], 2.4f, p)
            }
        }
    }

    /** Lights and sparkles that fade in and out in place. */
    private fun buildBlink(o: JSONObject): (Canvas, Float) -> Unit {
        val sprite = Sprites.get(o.optString("sprite", "dot"))
        val size = o.optDouble("size", 1.0).toFloat()
        val sp = o.optDouble("speed", 2.0).toFloat()
        val pts = o.getJSONArray("points")
        val n = pts.length()
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val cs = IntArray(n)
        for (i in 0 until n) {
            val a = pts.getJSONArray(i)
            xs[i] = a.getDouble(0).toFloat(); ys[i] = a.getDouble(1).toFloat(); cs[i] = Color.parseColor(a.getString(2))
        }
        val filters = Array(n) { PorterDuffColorFilter(cs[it], PorterDuff.Mode.SRC_IN) }
        val dot = Paint(Paint.FILTER_BITMAP_FLAG)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG)
        return { c, t ->
            for (i in 0 until n) {
                val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(t * sp + i * 1.7f))
                halo.color = cs[i]
                halo.alpha = (70 * a).toInt()
                c.drawCircle(xs[i], ys[i], 20f * size, halo)
                dot.colorFilter = filters[i]
                dot.alpha = (255 * a).toInt()
                drawSprite(c, sprite, xs[i], ys[i], 0.9f * size, 0.9f * size, 0f, dot)
            }
        }
    }

    private fun buildFly(o: JSONObject, rnd: Random): (Canvas, Float) -> Unit {
        val sprite = Sprites.get("bat")
        val n = o.optInt("count", 6)
        val yr = rng(o, "y", 100f, 400f)
        val sp = rng(o, "speed", 80f, 160f)
        val sz = rng(o, "size", 0.8f, 1.3f)
        val paint = tintPaint(tints(o).firstOrNull() ?: Color.BLACK, 255)
        val x0 = FloatArray(n) { rnd.nextFloat() * (artW + 400f) }
        val y0 = FloatArray(n) { pick(yr, rnd) }
        val v = FloatArray(n) { pick(sp, rnd) * (if (it % 2 == 0) 1f else -1f) }
        val s = FloatArray(n) { pick(sz, rnd) }
        val ph = FloatArray(n) { rnd.nextFloat() * 6.28f }
        return { c, t ->
            val w = artW + 400f
            for (i in 0 until n) {
                val m = (x0[i] + abs(v[i]) * t) % w
                val x = if (v[i] > 0f) m - 200f else artW + 200f - m
                val y = y0[i] + 18f * sin(t * 0.9f + ph[i])
                val flap = 0.55f + 0.45f * abs(sin(t * 9f + ph[i]))
                if (inClock(x, y)) continue
                drawSprite(c, sprite, x, y, s[i] * 1.6f, s[i] * 1.6f * flap, 0f, paint)
            }
        }
    }

    /** Fireworks: a ring of dots grows and fades, each at its own moment. */
    private fun buildBurst(o: JSONObject): (Canvas, Float) -> Unit {
        val sprite = Sprites.get("burst")
        val period = o.optDouble("period", 3.5).toFloat()
        val items = o.getJSONArray("items")
        val n = items.length()
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val rs = FloatArray(n)
        val ps = Array(n) { Paint() }
        for (i in 0 until n) {
            val a = items.getJSONArray(i)
            xs[i] = a.getDouble(0).toFloat(); ys[i] = a.getDouble(1).toFloat(); rs[i] = a.getDouble(2).toFloat()
            ps[i] = tintPaint(Color.parseColor(a.getString(3)), 255)
        }
        return { c, t ->
            for (i in 0 until n) {
                val ph = ((t / period) + i * 0.37f) % 1f
                val e = 1f - (1f - ph) * (1f - ph)
                val s = rs[i] * (0.15f + 0.85f * e) / 110f
                ps[i].alpha = (255f * (1f - ph).pow(1.3f)).toInt().coerceIn(0, 255)
                drawSprite(c, sprite, xs[i], ys[i], s, s, 0f, ps[i])
            }
        }
    }

    /** Easter eggs that drift up and down a little. */
    private fun buildBob(o: JSONObject): (Canvas, Float) -> Unit {
        val sprite = Sprites.get("egg")
        val items = o.getJSONArray("items")
        val n = items.length()
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val sx = FloatArray(n)
        val sy = FloatArray(n)
        val ps = Array(n) { Paint() }
        for (i in 0 until n) {
            val a = items.getJSONArray(i)
            xs[i] = a.getDouble(0).toFloat(); ys[i] = a.getDouble(1).toFloat()
            sx[i] = (2f * a.getDouble(2).toFloat()) / 28f; sy[i] = (2f * a.getDouble(3).toFloat()) / 52f
            ps[i] = tintPaint(Color.parseColor(a.getString(4)), 235)
        }
        return { c, t ->
            for (i in 0 until n) if (!inClock(xs[i], ys[i])) drawSprite(c, sprite, xs[i], ys[i] + 10f * sin(t * 1.3f + i), sx[i], sy[i], 4f * sin(t * 0.9f + i), ps[i])
        }
    }

    private fun buildOrbit(o: JSONObject): (Canvas, Float) -> Unit {
        val cx = o.optDouble("cx", 960.0).toFloat()
        val cy = o.optDouble("cy", 400.0).toFloat()
        val rx = o.optDouble("rx", 360.0).toFloat()
        val ry = o.optDouble("ry", 90.0).toFloat()
        val period = o.optDouble("period", 18.0).toFloat()
        val size = o.optDouble("size", 26.0).toFloat()
        val col = Color.parseColor(o.optString("tint", "#A78BFA"))
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = 70 }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        return { c, t ->
            val a = (2.0 * PI * t / period).toFloat()
            val x = cx + rx * cos(a)
            val y = cy + ry * sin(a)
            c.drawCircle(x, y, size * 1.9f, halo)
            c.drawCircle(x, y, size, body)
        }
    }

    /** A shooting star now and then. */
    private fun buildStreak(o: JSONObject): (Canvas, Float) -> Unit {
        val period = o.optDouble("period", 11.0).toFloat()
        val dur = 1.2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        return { c, t ->
            val k = floor(t / period).toInt()
            val local = (t - k * period) / dur
            if (local in 0f..1f) {
                val sx = 300f + ((k * 977) % 1300)
                val sy = 40f + ((k * 613) % 240)
                val hx = sx - 560f * local
                val hy = sy + 300f * local
                for (j in 0 until 7) {
                    val f = j / 7f
                    p.alpha = (230 * (1f - f) * (1f - local * 0.4f)).toInt().coerceIn(0, 255)
                    c.drawLine(hx + 20f * j, hy - 11f * j, hx + 20f * (j + 1), hy - 11f * (j + 1), p)
                }
            }
        }
    }
}

/** Small white shapes drawn once; the scenes tint and move them. */
private object Sprites {
    private val cache = HashMap<String, Bitmap>()

    fun get(name: String): Bitmap = cache.getOrPut(name) { make(name) }

    private fun make(name: String): Bitmap {
        val size = if (name == "burst") 256 else 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        when (name) {
            "leaf" -> {
                val path = Path()
                for (k in 0 until 10) {
                    val a = Math.toRadians(-90.0 + k * 36.0)
                    val r = if (k % 2 == 0) 28.0 else 12.6
                    val x = (32.0 + r * cos(a)).toFloat()
                    val y = (32.0 + r * sin(a)).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, p)
            }
            "snow" -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.strokeCap = Paint.Cap.ROUND
                for (k in 0 until 3) {
                    val a = Math.toRadians(k * 60.0)
                    val dx = (26.0 * cos(a)).toFloat()
                    val dy = (26.0 * sin(a)).toFloat()
                    c.drawLine(32f - dx, 32f - dy, 32f + dx, 32f + dy, p)
                }
            }
            "confetti" -> c.drawRect(22f, 14f, 42f, 50f, p)
            "heart" -> {
                val path = Path()
                path.moveTo(32f, 56f)
                path.cubicTo(4f, 36f, 6f, 10f, 24f, 10f)
                path.cubicTo(30f, 10f, 32f, 16f, 32f, 20f)
                path.cubicTo(32f, 16f, 34f, 10f, 40f, 10f)
                path.cubicTo(58f, 10f, 60f, 36f, 32f, 56f)
                path.close()
                c.drawPath(path, p)
            }
            "bat" -> {
                val path = Path()
                path.moveTo(32f, 30f); path.lineTo(20f, 18f); path.lineTo(6f, 34f); path.lineTo(14f, 38f); path.lineTo(22f, 34f)
                path.lineTo(28f, 44f); path.lineTo(32f, 38f); path.lineTo(36f, 44f); path.lineTo(42f, 34f); path.lineTo(50f, 38f)
                path.lineTo(58f, 34f); path.lineTo(44f, 18f); path.close()
                c.drawPath(path, p)
                c.drawOval(RectF(28f, 22f, 36f, 42f), p)
            }
            "clover" -> {
                val leaf = Path()
                leaf.moveTo(32f, 56f)
                leaf.cubicTo(4f, 36f, 6f, 10f, 24f, 10f)
                leaf.cubicTo(30f, 10f, 32f, 16f, 32f, 20f)
                leaf.cubicTo(32f, 16f, 34f, 10f, 40f, 10f)
                leaf.cubicTo(58f, 10f, 60f, 36f, 32f, 56f)
                leaf.close()
                for (k in 0 until 4) {
                    c.save()
                    c.rotate(k * 90f, 32f, 32f)
                    c.translate(0f, -12f)
                    c.scale(0.5f, 0.5f, 32f, 32f)
                    c.drawPath(leaf, p)
                    c.restore()
                }
            }
            "petal" -> c.drawOval(RectF(24f, 8f, 40f, 56f), p)
            "drop" -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND
                c.drawLine(32f, 10f, 32f, 54f, p)
            }
            "spark" -> {
                val path = Path()
                path.moveTo(32f, 4f); path.lineTo(37f, 27f); path.lineTo(60f, 32f); path.lineTo(37f, 37f)
                path.lineTo(32f, 60f); path.lineTo(27f, 37f); path.lineTo(4f, 32f); path.lineTo(27f, 27f); path.close()
                c.drawPath(path, p)
            }
            "egg" -> c.drawOval(RectF(18f, 6f, 46f, 58f), p)
            "burst" -> {
                for (k in 0 until 24) {
                    val a = 2.0 * PI * k / 24.0
                    for (j in 0 until 6) {
                        val r = 110.0 * (0.3 + 0.14 * j)
                        p.alpha = 255 - j * 22
                        c.drawCircle((128.0 + r * cos(a)).toFloat(), (128.0 + r * sin(a)).toFloat(), max(2f, 5f - 0.7f * j), p)
                    }
                }
                p.alpha = 255
                c.drawCircle(128f, 128f, 6f, p)
            }
            else -> c.drawCircle(32f, 32f, 14f, p)   // "dot"
        }
        return bmp
    }
}
