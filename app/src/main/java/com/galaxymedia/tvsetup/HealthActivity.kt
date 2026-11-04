package com.galaxymedia.tvsetup

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Plain-language health check for the stick: Wi-Fi strength and band, a 3-run speed test, free memory,
 * heat, restarts and storage, with advice on what is probably wrong (distance, router, heat, power).
 */
class HealthActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private var accent = Color.parseColor("#22D3EE")
    private val good = Color.parseColor("#4ADE80")
    private val warn = Color.parseColor("#FACC15")
    private val bad = Color.parseColor("#F87171")
    private var testing = false
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        accent = Themes.accentColor(prefs.accent)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050816"))
            setPadding(dp(56), dp(28), dp(56), dp(22))
        }
        root.addView(TextView(this).apply {
            text = "Stick health"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "What is slowing things down? This looks at Wi-Fi, speed, memory and heat."
            textSize = 16f
            setTextColor(Color.parseColor("#9FB3C8"))
            setPadding(0, dp(2), 0, dp(10))
        })
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        val b1 = button("Test my connection") { runSpeedTest() }
        bar.addView(b1, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(10), 0) })
        bar.addView(button("Free up memory") { freeMemory() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(10), 0) })
        bar.addView(button("Refresh") { render() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(10), 0) })
        bar.addView(button("Done") { finish() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(bar)
        setContentView(root)
        render()
        b1.requestFocus()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- small view helpers ----------

    private fun bg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        if (focused) { setColor(accent); setStroke(dp(3), Color.WHITE) }
        else { setColor(Color.parseColor("#14203D")); setStroke(dp(1), Color.parseColor("#2A3A63")) }
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isFocusable = true
        isClickable = true
        background = bg(false)
        setOnFocusChangeListener { v, has ->
            v.background = bg(has)
            (v as TextView).setTextColor(if (has) Color.BLACK else Color.WHITE)
        }
        setOnClickListener { onClick() }
    }

    private fun section(text: String) = body.addView(TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(accent)
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(14), 0, dp(4))
    })

    private fun row(label: String, value: String, color: Int) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(2)) }
        r.addView(TextView(this).apply { text = label; textSize = 18f; setTextColor(Color.parseColor("#C9D6E8")) }, LinearLayout.LayoutParams(dp(260), -2))
        r.addView(TextView(this).apply { text = value; textSize = 18f; setTextColor(color); setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(r)
    }

    private fun note(text: String, color: Int) = body.addView(TextView(this).apply {
        this.text = "•  $text"
        textSize = 17f
        setTextColor(color)
        setPadding(0, dp(3), 0, dp(3))
    })

    // ---------- readings ----------

    /** signal (dBm), frequency (MHz), link speed (Mbps), or null when not on Wi-Fi. */
    @Suppress("DEPRECATION")
    private fun wifi(): Triple<Int, Int, Int>? = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val i = wm.connectionInfo
        if (i == null || i.networkId == -1 || i.rssi <= -127) null else Triple(i.rssi, i.frequency, i.linkSpeed)
    } catch (e: Exception) { null }

    private fun thermal(): Int = try {
        if (Build.VERSION.SDK_INT >= 29) (getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else 0
    } catch (e: Exception) { 0 }

    private fun render() {
        body.removeAllViews()
        val advice = ArrayList<Pair<Int, String>>()

        // Wi-Fi
        section("Wi-Fi")
        val w = wifi()
        var weakSignal = false
        var fairSignal = false
        if (w == null) {
            row("Connection", "Not on Wi-Fi (wired, or offline)", warn)
            advice += warn to "This stick is not on Wi-Fi right now (wired, or offline)."
        } else {
            val (rssi, freq, link) = w
            val label = when {
                rssi >= -55 -> "Excellent"
                rssi >= -67 -> "Good"
                rssi >= -75 -> "Fair"
                else -> "Weak"
            }
            weakSignal = rssi < -75
            fairSignal = rssi < -67
            row("Signal", "$label  ($rssi dBm)", if (weakSignal) bad else if (fairSignal) warn else good)
            val band = if (freq in 2400..2500) "2.4 GHz" else if (freq >= 4900) "5 GHz" else "?"
            row("Band", band, if (band == "2.4 GHz") warn else good)
            row("Link speed", "$link Mbps  (the radio's top speed, not your real speed)", Color.WHITE)
            if (weakSignal) advice += bad to "Weak Wi-Fi signal. Move the router closer to this TV, or use a wired connection."
            else if (fairSignal) advice += warn to "Fair Wi-Fi signal. Streaming may stutter at busy times. Closer to the router is better."
            if (band == "2.4 GHz") advice += warn to "On the 2.4 GHz band. 5 GHz is usually faster and clearer for streaming, if the router has it."
        }

        // Speed
        section("Internet speed")
        val mbps = prefs.lastSpeedMbps
        if (testing) row("Test", "Testing... about 20 seconds", warn)
        else if (prefs.lastSpeedAt > 0L) {
            val ago = ((System.currentTimeMillis() - prefs.lastSpeedAt) / 60000L).toInt()
            val when_ = if (ago < 1) "just now" else if (ago < 120) "$ago min ago" else "${ago / 60} h ago"
            row("Last test ($when_)", prefs.lastSpeed, if (mbps in 0f..9.99f) bad else if (mbps in 10f..24.99f) warn else good)
        } else row("Last test", "Not run yet. Press Test my connection.", Color.WHITE)
        if (!testing && mbps >= 0f) {
            if (mbps < 10f) advice += bad to (if (weakSignal || fairSignal) "Slow internet and a weak signal. Distance from the router is the likely cause." else "Slow internet with a strong signal. That points at the router or the internet plan, not the stick.")
            else if (mbps < 25f) advice += warn to "Enough for HD. 4K would be tight."
            else advice += good to "Internet speed looks good."
        }

        // Memory, storage
        section("Stick")
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = (mi.totalMem / 1048576L).toInt()
        val freeMb = (mi.availMem / 1048576L).toInt()
        val pct = if (totalMb > 0) freeMb * 100 / totalMb else 100
        row("Free memory", "$freeMb MB of $totalMb MB  ($pct%)", if (pct < 15) bad else if (pct < 25) warn else good)
        val sf = StatFs(Environment.getDataDirectory().path)
        val storageMb = (sf.availableBytes / 1048576L).toInt()
        row("Free storage", "$storageMb MB", if (storageMb < 500) bad else if (storageMb < 1000) warn else good)
        val th = thermal()
        val thName = listOf("Normal (no heat warning)", "Warm", "Hot", "Very hot", "Critical", "Emergency", "Shutting down").getOrElse(th) { "Hot" }
        row("Heat", thName, if (th >= 2) bad else if (th == 1) warn else good)
        val upMin = (SystemClock.elapsedRealtime() / 60000L).toInt()
        row("On for", if (upMin < 120) "$upMin min" else "${upMin / 60} h ${upMin % 60} min", Color.WHITE)
        val restarts = prefs.restartsSince(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
        row("Restarts this week", "$restarts", if (restarts >= 3) bad else Color.WHITE)
        if (pct < 15) advice += bad to "Low memory. Press Free up memory, or restart the stick."
        if (th >= 2) advice += bad to "The stick is hot. Give it air: an HDMI extender helps, and keep it away from the back of the TV."
        if (restarts >= 3) advice += bad to "It restarted $restarts times this week. Use the wall adapter that came with it, not the TV's USB port."

        section("Backgrounds on this stick")
        val measured = Themes.wallpapersFor(false).mapNotNull { k -> prefs.bgSpeed(k)?.let { k to it } }
        if (measured.isEmpty()) note("Not measured yet. Each moving background is measured the first time it shows.", Color.WHITE)
        else measured.forEach { (k, v) ->
            row(Themes.wallpaperName(k), "${v.first}  (typical frame ${v.second} ms)", when (v.first) { "smooth" -> good; "ok" -> warn; else -> bad })
        }
        if (measured.any { it.second.first == "heavy" }) note("Rotate skips backgrounds marked heavy for a week. You can still pick one yourself.", warn)

        section("About this stick")
        val prof = DeviceProfile.detect(this)
        row("Maker and model", "${Build.MANUFACTURER} ${Build.MODEL}", Color.WHITE)
        row("In my model list", if (prof.source == "table") "Yes: ${prof.name}" else "No. Settings chosen from its memory instead", if (prof.source == "table") good else warn)
        row("Platform", if (prof.platform == "fireos") "Fire OS (Amazon)" else "Google TV / Android TV", Color.WHITE)
        row("Android", "${Build.VERSION.RELEASE}  (API ${Build.VERSION.SDK_INT})", Color.WHITE)
        row("Memory", "${prof.measuredRamGb} GB measured", Color.WHITE)
        val liteNow = Themes.liteFor(this, prefs.animations)
        row("Animations", Themes.animationsName(prefs.animations) + if (prefs.animations == "auto") (if (liteNow) " → lighter for this stick" else " → full for this stick") else "", Color.WHITE)
        row("App store", if (prof.platform == "fireos") "Amazon Appstore" else "Google Play", Color.WHITE)
        row("Galaxy Media", "v" + packageManager.getPackageInfo(packageName, 0).versionName, Color.WHITE)

        section("What this means")
        if (advice.isEmpty()) advice += good to "Nothing looks wrong. If videos still freeze, run the connection test. If only one channel freezes, the problem is that channel."
        advice.forEach { note(it.second, it.first) }
    }

    // ---------- actions ----------

    private fun downloadMbps(bytes: Int): Double? {
        val c = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        val t0 = System.nanoTime()
        var total = 0L
        c.inputStream.use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                total += n
            }
        }
        val sec = (System.nanoTime() - t0) / 1e9
        return if (sec > 0 && total > 0) total * 8 / 1e6 / sec else null
    }

    /** Three 10 MB downloads, so one odd reading cannot mislead. */
    private fun runSpeedTest() {
        if (testing) return
        testing = true
        render()
        Thread {
            val results = ArrayList<Double>()
            repeat(3) { try { downloadMbps(10_000_000)?.let { results.add(it) } } catch (e: Exception) { } }
            runOnUiThread {
                testing = false
                if (results.isEmpty()) {
                    prefs.lastSpeed = "Couldn't reach the speed test. Is the stick online?"
                    prefs.lastSpeedMbps = -1f
                } else {
                    val avg = results.average()
                    prefs.lastSpeed = String.format(Locale.US, "%.0f Mbps  (lowest %.0f, highest %.0f)", avg, results.minOrNull()!!, results.maxOrNull()!!)
                    prefs.lastSpeedMbps = avg.toFloat()
                }
                prefs.lastSpeedAt = System.currentTimeMillis()
                render()
            }
        }.start()
    }

    private fun freeMemory() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var n = 0
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.packageName == packageName || (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            try { am.killBackgroundProcesses(ai.packageName); n++ } catch (e: Exception) { }
        }
        Toast.makeText(this, "Asked $n apps to close in the background", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ render() }, 1500)
    }
}
