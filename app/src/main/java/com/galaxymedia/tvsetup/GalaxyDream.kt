package com.galaxymedia.tvsetup

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.FrameLayout
import android.widget.TextClock
import java.util.Calendar
import java.util.Random

/**
 * Galaxy Media's own screensaver: today's seasonal picture, dimmed, with a slow-moving clock.
 * It replaces Amazon's screensaver when the laptop script sets it as the system screensaver.
 */
class GalaxyDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private var root: FrameLayout? = null
    private var clock: TextClock? = null

    private val mover = object : Runnable {
        override fun run() {
            place()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val prefs = Prefs(this)
        val lite = Themes.liteFor(this, prefs.animations)
        val motion = if (prefs.animations == "off" || lite) "off" else prefs.motion
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        frame.addView(SeasonView(this, prefs.previewScene.ifEmpty { Seasons.sceneFor(Calendar.getInstance()) }, motion), FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply { setBackgroundColor(Color.argb(150, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        val tc = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "h:mm"
            textSize = 96f
            setTextColor(Color.argb(210, 255, 255, 255))
        }
        frame.addView(tc, FrameLayout.LayoutParams(-2, -2))
        root = frame
        clock = tc
        setContentView(frame)
        frame.post { place() }
        handler.postDelayed(mover, 60_000L)
    }

    /** The clock moves a little every minute so no pixel stays lit in one place. */
    private fun place() {
        val r = root ?: return
        val c = clock ?: return
        val maxX = (r.width - c.width).coerceAtLeast(1)
        val maxY = (r.height - c.height).coerceAtLeast(1)
        c.x = rnd.nextInt(maxX).toFloat()
        c.y = rnd.nextInt(maxY).toFloat()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(mover)
        super.onDetachedFromWindow()
    }
}
