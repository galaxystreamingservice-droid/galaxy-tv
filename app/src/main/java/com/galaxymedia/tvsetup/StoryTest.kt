package com.galaxymedia.tvsetup

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Debug
import android.system.Os
import android.system.OsConstants
import android.view.Choreographer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File

/**
 * TEMPORARY test (Settings > Season test): on the real home screen the holiday scene changes every 5 seconds,
 * and a readout shows how hard the stick is working (app CPU, memory, smoothness). Turns itself off after 10 minutes.
 */
class StoryTest(private val act: Activity) {

    companion object {
        const val STEP_MS = 5_000L
        const val LENGTH_MS = 10 * 60_000L
        fun active(p: Prefs) = System.currentTimeMillis() < p.storyTestUntil
        private fun stages(act: Activity) = StoryStages.all().filter { StoryStages.exists(act, it.id) }
        fun stageNow(act: Activity): StoryStages.Stage? {
            val s = stages(act)
            if (s.isEmpty()) return null
            return s[((System.currentTimeMillis() / STEP_MS) % s.size).toInt()]
        }
    }

    private var label: TextView? = null
    private var running = false
    private var lastFrameNs = 0L
    private var frames = 0
    private var slow = 0
    private var lastCpuTicks = -1L
    private var lastWall = 0L
    private var worstCpu = 0
    private var worstMem = 0
    private var totalSlow = 0
    private val tck = try { Os.sysconf(OsConstants._SC_CLK_TCK).coerceAtLeast(1L) } catch (e: Exception) { 100L }
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (!running) return
            if (lastFrameNs > 0) {
                frames++
                if (t - lastFrameNs > 34_000_000L) slow++
            }
            lastFrameNs = t
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun resume() {
        if (running) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCb)
        if (label == null) {
            val d = act.resources.displayMetrics.density
            label = TextView(act).apply {
                textSize = 15f; setTextColor(Color.WHITE); setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply { cornerRadius = 14 * d; setColor(Color.argb(200, 10, 10, 20)) }
            }
            act.findViewById<ViewGroup>(android.R.id.content).addView(label,
                FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (6 * d).toInt() })
        }
    }

    fun pause() {
        running = false
    }

    fun stop() {
        running = false
        label?.let { (it.parent as? ViewGroup)?.removeView(it) }
        label = null
    }

    /** Called about once a second while the test runs. */
    fun update(prefs: Prefs) {
        val st = stageNow(act)
        val cpu = cpuPercent()
        val mem = (Debug.getPss() / 1024L).toInt()
        if (cpu > worstCpu) worstCpu = cpu
        if (mem > worstMem) worstMem = mem
        totalSlow += slow
        val left = ((prefs.storyTestUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        val smooth = when { slow == 0 -> "smooth"; slow <= 3 -> "ok"; else -> "choppy" }
        label?.text = "SEASON TEST  ${st?.label ?: "no clips"}\n" +
            "CPU ${cpu}%  memory ${mem} MB  ${frames} fps  $smooth ($slow slow)   " +
            "worst: CPU ${worstCpu}%  ${worstMem} MB  $totalSlow slow   ends in ${left / 60}:${"%02d".format(left % 60)}"
        frames = 0
        slow = 0
    }

    /** This app's share of the whole stick's CPU since the last call. Video decoding runs on the video chip, not here. */
    private fun cpuPercent(): Int {
        return try {
            val stat = File("/proc/self/stat").readText()
            val f = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
            val ticks = f[11].toLong() + f[12].toLong()
            val now = System.currentTimeMillis()
            val pct = if (lastCpuTicks < 0) 0 else {
                val cpuMs = (ticks - lastCpuTicks) * 1000L / tck
                val wall = (now - lastWall).coerceAtLeast(1L)
                (cpuMs * 100L / wall / cores).toInt()
            }
            lastCpuTicks = ticks; lastWall = now
            pct
        } catch (e: Exception) { -1 }
    }
}
