package com.galaxymedia.tvsetup

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import com.galaxymedia.tvsetup.model.DeviceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Galaxy Media home screen. Opens from the first slot of Amazon's app row.
 * No special access needed: it reads the settings file, shows status chips,
 * and launches the apps on the shelf like any normal app.
 */
class HomeActivity : AppCompatActivity() {

    private data class ChipSpec(val text: String, val color: Int, val onClick: (() -> Unit)?)

    private lateinit var prefs: Prefs
    private lateinit var updater: AppUpdater
    private val io = CoroutineScope(Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var greetingView: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var shelfRow: LinearLayout
    private lateinit var bottomScroll: HorizontalScrollView
    private lateinit var railScroll: ScrollView
    private lateinit var hintView: TextView
    private var gearView: GearView? = null
    private lateinit var bgHolder: FrameLayout
    private lateinit var centerTitle: View
    private lateinit var infoView: TextView
    private lateinit var centerBox: LinearLayout
    private var pictureView: PictureTraceView? = null
    private lateinit var contentBox: LinearLayout
    private var appliedPad = ""
    private var bgShownOnce = false
    private var pausedAt = 0L

    // ---- frame monitor: measures how smoothly each moving background draws on this stick ----
    private val frameThread by lazy { android.os.HandlerThread("galaxy-frames").also { it.start() } }
    private var measureKey: String? = null
    private var measureFrom = 0L
    private val measureDurations = ArrayList<Long>()
    private val frameListener = android.view.Window.OnFrameMetricsAvailableListener { _, fm, _ ->
        if (measureKey != null && SystemClock.uptimeMillis() >= measureFrom) {
            val total = fm.getMetric(android.view.FrameMetrics.TOTAL_DURATION)
            var n = 0
            synchronized(measureDurations) { measureDurations.add(total); n = measureDurations.size }
            if (n == 240) runOnUiThread { finishMeasure() }
        }
    }

    private fun startMeasure(key: String, motion: String) {
        stopMeasure()
        if (motion == "off" || key == "plain" || key == "logo" || key in Themes.sceneKeys) return
        val prev = prefs.bgSpeed(key)
        if (prev != null && System.currentTimeMillis() - prev.third < 3L * 24 * 3600 * 1000) return   // measured recently
        measureKey = key
        measureFrom = SystemClock.uptimeMillis() + 3000                  // ignore the warm-up
        synchronized(measureDurations) { measureDurations.clear() }
        try { window.addOnFrameMetricsAvailableListener(frameListener, android.os.Handler(frameThread.looper)) }
        catch (e: Exception) { measureKey = null }
    }

    private fun stopMeasure() {
        if (measureKey != null) {
            try { window.removeOnFrameMetricsAvailableListener(frameListener) } catch (e: Exception) { }
        }
        measureKey = null
    }

    private fun finishMeasure() {
        val key = measureKey ?: return
        val d = synchronized(measureDurations) { measureDurations.toList() }
        stopMeasure()
        if (d.size < 100) return
        val sorted = d.sorted()
        val p90ms = (sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.size - 1)] / 1_000_000L).toInt()
        val rating = if (p90ms <= 20) "smooth" else if (p90ms <= 33) "ok" else "heavy"
        prefs.setBgSpeed(key, rating, p90ms)
    }
    private val lite: Boolean get() = Themes.liteFor(this, prefs.animations)

    /** The wallpaper actually shown: lower-end sticks never get the moving-light or heavy ones. */
    private fun wallpaperNow(): String = Themes.resolve(prefs.wallpaper, lite, prefs.rotateMin, prefs.heavyBackgrounds())

    /** Pictures stay still on lower-end sticks. */
    private fun motionNow(): String = if (prefs.animations == "off" || (lite && Themes.isPicture(wallpaperNow()))) "off" else prefs.motion
    private lateinit var tonightView: TextView
    private lateinit var sportsBox: LinearLayout
    private lateinit var sportsText: TextView
    private lateinit var nightOverlay: View
    private lateinit var saverLayer: FrameLayout
    private lateinit var saverClock: TextClock
    private lateinit var noticeBox: TextView
    private lateinit var liveView: TextView
    private var saverOn = false
    private var weatherBusy = false
    private var sportsSignature = ""
    private var sportsBusy = false
    private var appliedAccent = ""
    private var appliedBackground = ""
    private var appliedShelf = ""
    private var appliedLook = ""
    private var quiet = false
    private var nightOn = false

    private var settings: DeviceSettings? = null
    private var selfUpdateVersion: String? = null
    private var chipSignature = ""
    private var shelfSignature = ""

    companion object {
        private var lastRefresh = 0L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val QUIET_AFTER_MS = 30_000L
        private const val QUIET_ALPHA = 0.25f
        private const val NIGHT_ALPHA = 0.45f
        private const val NIGHT_END_HOUR = 6
        private const val SAVER_AFTER_MS = 5 * 60_000L
        private const val SAVER_MOVE_MS = 60_000L
    }

    // Palette from the design canvas
    private var bgColor = Color.parseColor("#060A16")
    private var cardColor = Color.parseColor("#0F1C2E")
    private var textColor = Color.parseColor("#EAF6FF")
    private var mutedColor = Color.parseColor("#9FB8C8")
    private var accent = Color.parseColor("#22D3EE")
    private val green = Color.parseColor("#6BF0A0")
    private val amber = Color.parseColor("#E3B341")
    private val red = Color.parseColor("#FF6B6B")
    private val dark = Color.parseColor("#04121A")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    private val tick = object : Runnable {
        override fun run() {
            updateGreeting()
            rebuildChips()
            refreshSports()
            refreshWeather()
            applyNight(false)
            if (prefs.wallpaper == "rotate") applyTheme()      // picks up the next wallpaper when it is time
            refreshFromNetwork()                               // checks for a new version about every 10 minutes while idle
            handler.postDelayed(this, 15_000)
        }
    }

    private val quietRunnable = Runnable { enterQuiet() }
    private val saverRunnable = Runnable { showSaver() }
    private val saverMove = object : Runnable {
        override fun run() {
            moveSaverClock(false)
            handler.postDelayed(this, SAVER_MOVE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        prefs.noteBoot(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())
        updater = AppUpdater(this, prefs)
        appliedLook = prefs.textSize + prefs.highContrast
        if (prefs.highContrast) {
            bgColor = Color.BLACK; cardColor = Color.BLACK
            textColor = Color.WHITE; mutedColor = Color.WHITE
        }
        setContentView(buildUi())
        // Back stays on this screen. The remote's Home button still leaves, as it always does on Fire TV.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* stay on the Galaxy Media home screen */ }
        })
        loadStoredSettings()
        applyTheme()
        rebuildAll()
        shelfRow.post { shelfRow.getChildAt(0)?.requestFocus() }
        if (!prefs.namePrompted) askName()
    }

    override fun onResume() {
        super.onResume()
        if (appliedLook != prefs.textSize + prefs.highContrast) { recreate(); return }
        loadStoredSettings()
        applyTheme()
        if (measureKey == null) startMeasure(wallpaperNow(), motionNow())
        rebuildAll()
        applyNight(true)
        leaveQuiet(true)
        hideSaver(true)
        scheduleQuiet()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 15_000)
        refreshFromNetwork()
        refreshSports()
        refreshWeather()
        // Coming back after using another app: close the other apps' background processes.
        if (pausedAt > 0L && SystemClock.elapsedRealtime() - pausedAt > 1500L && AppCloser.enabled(this, prefs.closeApps)) {
            handler.postDelayed({ AppCloser.closeOthers(this) }, 1500)
        }
    }

    override fun onPause() {
        super.onPause()
        pausedAt = SystemClock.elapsedRealtime()
        stopMeasure()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(quietRunnable)
        handler.removeCallbacks(saverRunnable)
        hideSaver(true)
    }

    /** Any key press or touch wakes the chips and sports strip and restarts the idle timer. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (saverOn) hideSaver(false)
        if (quiet) leaveQuiet(false)
        scheduleQuiet()
    }

    /** While the screensaver is showing, the first key press only wakes the screen. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (saverOn) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                hideSaver(false)
                scheduleQuiet()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ───────────────────────── quiet mode ─────────────────────────

    private fun scheduleQuiet() {
        handler.removeCallbacks(quietRunnable)
        handler.removeCallbacks(saverRunnable)
        if (prefs.quietMode) handler.postDelayed(quietRunnable, QUIET_AFTER_MS)
        if (prefs.screensaver) handler.postDelayed(saverRunnable, SAVER_AFTER_MS)
    }

    private fun enterQuiet() {
        if (!prefs.quietMode || isFinishing) return
        quiet = true
        chipRow.animate().alpha(QUIET_ALPHA).setDuration(800).start()
        sportsBox.animate().alpha(QUIET_ALPHA).setDuration(800).start()
    }

    private fun leaveQuiet(instant: Boolean) {
        quiet = false
        chipRow.animate().cancel()
        sportsBox.animate().cancel()
        if (instant) {
            chipRow.alpha = 1f
            sportsBox.alpha = 1f
        } else {
            chipRow.animate().alpha(1f).setDuration(150).start()
            sportsBox.animate().alpha(1f).setDuration(150).start()
        }
    }

    // ───────────────────────── screensaver ─────────────────────────

    private fun showSaver() {
        if (saverOn || isFinishing || !prefs.screensaver) return
        saverOn = true
        saverLayer.animate().cancel()
        saverLayer.alpha = 0f
        saverLayer.visibility = View.VISIBLE
        saverLayer.animate().alpha(1f).setDuration(1200).start()
        saverLayer.post { moveSaverClock(true) }
        handler.removeCallbacks(saverMove)
        handler.postDelayed(saverMove, SAVER_MOVE_MS)
    }

    private fun hideSaver(instant: Boolean) {
        handler.removeCallbacks(saverMove)
        if (!saverOn) return
        saverOn = false
        saverLayer.animate().cancel()
        saverClock.animate().cancel()
        if (instant) {
            saverLayer.alpha = 0f
            saverLayer.visibility = View.GONE
        } else {
            saverLayer.animate().alpha(0f).setDuration(200).withEndAction {
                if (!saverOn) saverLayer.visibility = View.GONE
            }.start()
        }
    }

    /** The clock drifts slowly to a new spot every minute so no pixel stays lit. */
    private fun moveSaverClock(first: Boolean) {
        val maxX = (saverLayer.width - saverClock.width).coerceAtLeast(0)
        val maxY = (saverLayer.height - saverClock.height).coerceAtLeast(0)
        val tx = Random.nextInt(maxX + 1).toFloat()
        val ty = Random.nextInt(maxY + 1).toFloat()
        saverClock.animate().cancel()
        if (first) {
            saverClock.translationX = tx
            saverClock.translationY = ty
        } else {
            saverClock.animate().translationX(tx).translationY(ty)
                .setDuration(SAVER_MOVE_MS - 2000).setInterpolator(LinearInterpolator()).start()
        }
    }

    // ───────────────────────── weather ─────────────────────────

    private fun weatherSuffix(): String {
        if (prefs.weatherLat.isBlank() || WeatherRepository.text.isBlank()) return ""
        return "  ·  " + WeatherRepository.text
    }

    private fun refreshWeather() {
        val lat = prefs.weatherLat
        val lon = prefs.weatherLon
        if (lat.isBlank() || lon.isBlank()) return
        val fresh = "$lat,$lon" == WeatherRepository.key &&
            System.currentTimeMillis() - WeatherRepository.lastFetch < 30 * 60_000L
        if (fresh || weatherBusy) return
        weatherBusy = true
        io.launch {
            try { WeatherRepository.refresh(lat, lon) } catch (e: Exception) { /* no weather shown */ }
            withContext(Dispatchers.Main) {
                weatherBusy = false
                if (!isFinishing) updateGreeting()
            }
        }
    }

    // ───────────────────────── night dimming ─────────────────────────

    /** Dims the whole screen from the chosen hour until 6 AM. */
    private fun applyNight(instant: Boolean) {
        val start = prefs.nightStart
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val on = start >= 0 && (h >= start || h < NIGHT_END_HOUR)
        if (!instant && on == nightOn) return
        nightOn = on
        val target = if (on) NIGHT_ALPHA else 0f
        nightOverlay.animate().cancel()
        if (instant) nightOverlay.alpha = target
        else nightOverlay.animate().alpha(target).setDuration(1500).start()
    }

    // ───────────────────────── UI ─────────────────────────

    private fun buildUi(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(bgColor)
            clipChildren = false
            clipToPadding = false
        }
        bgHolder = FrameLayout(this)
        root.addView(bgHolder, FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(36), dp(56), dp(40)) // safe margins (adjusted in applyContentPadding)
            clipChildren = false
            clipToPadding = false
        }
        contentBox = content

        // Top row: clock + greeting on the left, chips + gear on the right
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            clipChildren = false
            clipToPadding = false
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm a"
            format24Hour = "h:mm a"
            textSize = 34f * prefs.textScale
            setTextColor(textColor)
        }
        greetingView = TextView(this).apply { textSize = 18f * prefs.textScale; setTextColor(mutedColor) }
        left.addView(clock)
        left.addView(greetingView)
        top.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))

        chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        top.addView(chipRow, LinearLayout.LayoutParams(WRAP, WRAP))
        content.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        // Family notice from the settings file (hidden when there is none)
        noticeBox = TextView(this).apply {
            textSize = 18f * prefs.textScale
            setTextColor(amber)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(10), dp(24), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(14f)
                setColor(if (prefs.highContrast) Color.BLACK else Color.parseColor("#CC0A1522"))
                setStroke(if (prefs.highContrast) dp(2) else dp(1), amber)
            }
            visibility = View.GONE
        }
        content.addView(noticeBox, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })

        // Middle: optional left rail for the shelf, then the centre title area
        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        railScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        middle.addView(railScroll, LinearLayout.LayoutParams(WRAP, MATCH))

        // Center: title, the "Tonight" label, and a hint when setup isn't finished
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        centerTitle = titleBox
        titleBox.addView(TextView(this).apply {
            text = "GALAXY MEDIA"
            textSize = 40f * prefs.textScale
            letterSpacing = 0.12f
            setTextColor(textColor)
            gravity = Gravity.CENTER
        })
        center.addView(titleBox)
        // Date and weather, shown under the title when no game line is taking that space
        infoView = TextView(this).apply {
            textSize = 22f * prefs.textScale
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        center.addView(infoView)
        // Display only: not focusable, so the remote skips it and nobody thinks it opens the game.
        tonightView = TextView(this).apply {
            textSize = 18f * prefs.textScale
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            visibility = View.GONE
        }
        center.addView(tonightView)
        // Big live-score line when one of my teams is playing right now
        liveView = TextView(this).apply {
            textSize = 26f * prefs.textScale
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            visibility = View.GONE
        }
        center.addView(liveView)
        hintView = TextView(this).apply {
            textSize = 16f * prefs.textScale
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        center.addView(hintView)
        middle.addView(center, LinearLayout.LayoutParams(0, MATCH, 1f))
        centerBox = center
        center.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePictureZone() }
        content.addView(middle, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Sports strip (scrolls sideways by itself; hidden when off or when there are no games)
        sportsText = TextView(this).apply {
            textSize = 19f * prefs.textScale
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(28))
            setTextColor(textColor)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            setHorizontallyScrolling(true)
            isSelected = true
        }
        sportsBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(14f)
                setColor(if (prefs.highContrast) Color.BLACK else Color.parseColor("#CC0A1522"))
                setStroke(if (prefs.highContrast) dp(2) else dp(1), if (prefs.highContrast) Color.WHITE else Color.parseColor("#33FFFFFF"))
            }
            visibility = View.GONE
        }
        sportsBox.addView(sportsText, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(sportsBox, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(6) })

        // Bottom: app shelf (the same shelfRow moves to the left rail when Settings says so)
        bottomScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
        }
        shelfRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(14), dp(8), dp(14))
        }
        content.addView(bottomScroll, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        // Screensaver: black screen with a big slow-moving clock. Opens after about 5 minutes idle.
        saverLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isFocusable = false
            isClickable = false
        }
        saverClock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "h:mm"
            textSize = 110f
            setTextColor(if (prefs.highContrast) Color.WHITE else Color.parseColor("#8FA3B0"))
        }
        saverLayer.addView(saverClock, FrameLayout.LayoutParams(WRAP, WRAP))
        root.addView(saverLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // Night dimming layer: black, on top of everything, never takes focus or touches.
        nightOverlay = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            isFocusable = false
            isClickable = false
        }
        root.addView(nightOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
    }

    /** Moves the shelf between the bottom row and the left rail when the setting changes. */
    private fun placeShelf() {
        if (appliedShelf == prefs.shelf) return
        appliedShelf = prefs.shelf
        val left = prefs.shelf == "left"
        (shelfRow.parent as? ViewGroup)?.removeView(shelfRow)
        railScroll.visibility = if (left) View.VISIBLE else View.GONE
        bottomScroll.visibility = if (left) View.GONE else View.VISIBLE
        shelfRow.orientation = if (left) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        shelfRow.gravity = if (left) Gravity.CENTER_VERTICAL else Gravity.CENTER
        if (left) railScroll.addView(shelfRow, FrameLayout.LayoutParams(WRAP, WRAP))
        else bottomScroll.addView(shelfRow, FrameLayout.LayoutParams(MATCH, WRAP))
        shelfSignature = ""
    }

    // ───────────────────────── sports strip ─────────────────────────

    private fun refreshSports() {
        if (prefs.sports == "off") { updateSports(); return }
        val stale = System.currentTimeMillis() - SportsRepository.lastFetch > 3 * 60 * 1000L
        if (!stale || sportsBusy) { updateSports(); return }
        sportsBusy = true
        io.launch {
            try { SportsRepository.fetchAll() } catch (e: Exception) { /* strip just hides */ }
            withContext(Dispatchers.Main) {
                sportsBusy = false
                if (!isFinishing) updateSports()
            }
        }
    }

    private fun updateSports() {
        val mode = prefs.sports
        val keys = prefs.myTeamKeys
        val shown: List<Game> = when (mode) {
            "off" -> emptyList()
            "my" -> SportsRepository.games.filter { it.homeKey in keys || it.awayKey in keys }
            else -> SportsRepository.games
        }
        updateLive(if (mode == "off") null else SportsRepository.games.firstOrNull {
            it.state == "in" && (it.homeKey in keys || it.awayKey in keys)
        })
        updateTonight(if (mode == "my" && liveView.visibility != View.VISIBLE) shown else emptyList())
        syncInfo()
        if (shown.isEmpty()) {
            sportsBox.visibility = View.GONE
            sportsSignature = ""
            return
        }
        val sig = mode + "#" + shown.joinToString("|") { it.league + it.text }
        if (sig == sportsSignature && sportsBox.visibility == View.VISIBLE) return
        sportsSignature = sig
        val sb = SpannableStringBuilder()
        for (g in shown.take(40)) {
            if (g.state == "in") {
                val s = sb.length
                sb.append("● ")
                sb.setSpan(ForegroundColorSpan(red), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val s2 = sb.length
            sb.append(g.league).append(" ")
            sb.setSpan(ForegroundColorSpan(accent), s2, s2 + g.league.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(g.text).append(" ")
        }
        sportsText.text = sb
        sportsText.isSelected = false
        sportsText.isSelected = true
        sportsBox.visibility = View.VISIBLE
    }

    /** Tells the picture wallpaper where the clear space is (between the top bar and the live-game banner). */
    private fun updatePictureZone() {
        val pv = pictureView ?: return
        if (pv.width == 0 || centerBox.height == 0) return
        val p = IntArray(2)
        val c = IntArray(2)
        pv.getLocationInWindow(p)
        centerBox.getLocationInWindow(c)
        val l = (c[0] - p[0]).toFloat()
        val t = (c[1] - p[1]).toFloat()
        pv.setZone(l, t, l + centerBox.width, t + centerBox.height)
    }

    /** The date/weather line only shows when the live-game and tonight lines are not using that space. */
    private fun syncInfo() {
        val busy = liveView.visibility == View.VISIBLE || tonightView.visibility == View.VISIBLE
        infoView.visibility = if (busy || wallpaperNow() == "logo" || Themes.isPicture(wallpaperNow())) View.GONE else View.VISIBLE
    }

    /** "LIVE Cowboys 14, Giants 7 · Q2 5:20" for one of my teams. Display only. */
    private fun updateLive(g: Game?) {
        if (g == null) {
            liveView.visibility = View.GONE
            return
        }
        val sb = SpannableStringBuilder("● LIVE  ")
        sb.setSpan(ForegroundColorSpan(red), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(g.text)
        liveView.text = sb
        liveView.visibility = View.VISIBLE
    }

    /** "TONIGHT Cowboys · Sun 3:25 PM": team, day and time only. Display only, never selectable. */
    private fun updateTonight(mine: List<Game>) {
        // Games are already sorted live first, then by start time.
        val g = mine.firstOrNull { it.state != "post" }
        if (g == null || g.start == 0L) {
            tonightView.visibility = View.GONE
            return
        }
        val team = if (g.homeKey in prefs.myTeamKeys) g.homeName else g.awayName
        val whenStr = SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(g.start))
        val sb = SpannableStringBuilder("TONIGHT ")
        sb.setSpan(ForegroundColorSpan(accent), 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(team).append(" · ").append(whenStr)
        tonightView.text = sb
        tonightView.visibility = View.VISIBLE
    }

    // ───────────────────────── theme (from Settings) ─────────────────────────

    private fun applyTheme() {
        if (appliedAccent != prefs.accent) {
            appliedAccent = prefs.accent
            accent = Themes.accentColor(prefs.accent)
            chipSignature = ""
            shelfSignature = ""
            sportsSignature = ""
        }
        applyContentPadding()
        val bgKey = wallpaperNow() + ":" + motionNow() + ":" + prefs.accent + ":" + prefs.edge + ":" + prefs.beam
        if (appliedBackground != bgKey) {
            appliedBackground = bgKey
            bgHolder.removeAllViews()
            val wp = wallpaperNow()
            val v: View? = when (wp) {
                "logo" -> ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageResource(R.drawable.galaxy_background)
                }
                "trace" -> LightTraceView(this, motionNow(), accent, Themes.beamColor(prefs.beam), if (prefs.edge == "wide") 42f else 18f)
                "nebula", "sunset", "ocean", "city", "grid", "mountains" -> SceneView(this, wp, accent)
                in Themes.pictureKeys -> PictureTraceView(this, wp, motionNow(), Themes.beamColor(prefs.beam))
                "aurora" -> AuroraView(this, motionNow())
                "plain" -> null
                else -> StarfieldView(this, motionNow())
            }
            if (v != null) bgHolder.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
            pictureView = v as? PictureTraceView
            pictureView?.post { updatePictureZone() }
            if (bgShownOnce) {                       // fade the new background in instead of cutting to it
                bgHolder.alpha = 0f
                bgHolder.animate().alpha(1f).setDuration(1400).start()
            }
            bgShownOnce = true
            startMeasure(wp, motionNow())
            centerTitle.visibility = if (wp == "logo" || Themes.isPicture(wp)) View.GONE else View.VISIBLE
            syncInfo()
        }
    }

    // ───────────────────────── data ─────────────────────────

    private fun loadStoredSettings() {
        val raw = prefs.lastSettingsJson
        settings = try { if (raw.isBlank()) null else DeviceSettings.parse(raw) } catch (e: Exception) { null }
    }

    private fun refreshFromNetwork() {
        val now = System.currentTimeMillis()
        if (now - lastRefresh < 10 * 60 * 1000L) return
        lastRefresh = now
        io.launch {
            val fresh = try { SettingsRepository(this@HomeActivity).fetch { } } catch (e: Exception) { null }
            val rel = try { updater.latestRelease() } catch (e: Exception) { null }
            val mine = currentVersion()
            withContext(Dispatchers.Main) {
                if (isFinishing) return@withContext
                if (fresh != null) settings = fresh
                selfUpdateVersion = if (!packageName.endsWith(".hometest") && rel != null && updater.isNewer(rel.version, mine)) rel.version else null
                rebuildAll()
            }
        }
    }

    private fun currentVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    @Suppress("DEPRECATION")
    private fun vpnOn(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    } catch (e: Exception) { false }

    private fun offline(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) { false }

    // ───────────────────────── rebuild ─────────────────────────

    private fun rebuildAll() {
        updateGreeting()
        rebuildChips()
        rebuildShelf()
        updateSports()
        updateNotice()
    }

    private fun updateGreeting() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val phrase = when {
            h in 5..11 -> "Good morning"
            h in 12..16 -> "Good afternoon"
            h in 17..21 -> "Good evening"
            else -> "Good night"
        }
        val name = prefs.userName.trim().replaceFirstChar { it.uppercase() }
        greetingView.text = if (name.isEmpty()) phrase else "$phrase, $name"
        infoView.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) + weatherSuffix()
    }

    private fun updateNotice() {
        val n = settings?.notice?.trim().orEmpty()
        if (n.isEmpty()) {
            noticeBox.visibility = View.GONE
        } else {
            noticeBox.text = n
            noticeBox.visibility = View.VISIBLE
        }
    }

    /** The laptop maintenance script stamps the date on the stick; count that as a finished cleanup. */
    private fun syncMaintenanceStamp() {
        try {
            val v = android.provider.Settings.Global.getString(contentResolver, "galaxy_last_maintained") ?: return
            val t = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(v.trim())?.time ?: return
            if (t > prefs.lastCleanup) prefs.lastCleanup = t
        } catch (e: Exception) { }
    }

    /**
     * The renewal reminder chip. No date yet: "Set a reminder". A date, far off: "Renews Dec 24" (press to change it).
     * Within 14 days, or past: it turns amber or red and opens the Pay / Renew screen.
     */
    private fun renewalChip(): ChipSpec? {
        val date = ReminderPicker.parse(prefs.reminderDate)
            ?: return ChipSpec("Set a reminder", mutedColor) { pickReminder(false) }
        val days = ReminderPicker.daysLeft(date)
        val nice = ReminderPicker.nice(date)
        if (days > 14) return ChipSpec("Renews $nice", mutedColor) { pickReminder(false) }
        val color = if (days <= 3) red else amber
        return ChipSpec(if (days < 0) "Expired $nice · Renew" else "Renew · $nice", color) { openPay() }
    }

    private fun pickReminder(afterPaying: Boolean) {
        ReminderPicker.show(this, prefs, afterPaying) { chipSignature = ""; rebuildChips() }
    }

    private fun rebuildChips() {
        val chips = mutableListOf<ChipSpec>()
        if (offline()) chips += ChipSpec("Offline · check Wi-Fi", red, null)
        if (prefs.settingsUrl.isBlank() && prefs.namePrompted) chips += ChipSpec("Finish setup", accent) { openSetup() }
        val s = settings
        if (s != null) {
            val st = updater.statuses(s.apps)
            val upd = st.count { it.state == "UPDATE" }
            val miss = st.count { it.state == "MISSING" }
            if (upd > 0) chips += ChipSpec(if (upd == 1) "1 app update ready" else "$upd app updates ready", accent) { openSetup() }
            if (miss > 0) chips += ChipSpec("$miss to install", accent) { openSetup() }
        }
        if (selfUpdateVersion != null) chips += ChipSpec("Galaxy Media update", accent) { openSetup() }
        val renewal = renewalChip()
        val renewalUrgent = renewal != null && (renewal.color == red || renewal.color == amber)
        if (renewal != null && renewalUrgent) chips += renewal
        syncMaintenanceStamp()
        val upHours = SystemClock.elapsedRealtime() / 3_600_000L
        if (upHours >= 48 && System.currentTimeMillis() > prefs.restartSnoozeUntil) chips += ChipSpec("Restart for best speed", accent) { askRestart(upHours) }
        if (prefs.cleanupDue()) chips += ChipSpec("Maintenance due", accent) { openCleanup() }
        if (renewal != null && !renewalUrgent) chips += renewal
        val vpn = vpnOn()
        val vpnChip = ChipSpec(if (vpn) "VPN on" else "VPN off", if (vpn) green else mutedColor) { openVpnApp() }
        val contactChip = ChipSpec("Contact", mutedColor) { openContact() }

        // The VPN and Contact chips are always shown; other chips share the two remaining slots by priority.
        val shownChips = chips.take(2) + vpnChip + contactChip
        val sig = shownChips.joinToString("|") { it.text + it.color }
        if (sig == chipSignature && chipRow.childCount > 0) return
        chipSignature = sig

        val hadGearFocus = gearView?.hasFocus() == true
        chipRow.removeAllViews()
        shownChips.forEach { chipRow.addView(makeChip(it), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(10) }) }

        val gear = GearView(this).apply {
            isFocusable = true
            isClickable = true
            setOnClickListener { showMenu() }
        }
        style(gear, 24f, emptyList()) { has -> gear.tint = if (has) dark else textColor }
        chipRow.addView(gear, LinearLayout.LayoutParams(dp(48), dp(48)))
        gearView = gear
        if (hadGearFocus) gear.requestFocus()
    }

    /** Keeps the home screen inside the light-trace frame, and further from the edge if "Screen edge" is Wider. */
    private fun applyContentPadding() {
        val trace = wallpaperNow() == "trace"
        val wide = prefs.edge == "wide"
        val key = "$trace:$wide"
        if (key == appliedPad) return
        appliedPad = key
        val extra = if (wide) 24 else 0
        contentBox.setPadding(
            dp((if (trace) 72 else 56) + extra), dp((if (trace) 52 else 36) + extra),
            dp((if (trace) 72 else 56) + extra), dp((if (trace) 56 else 40) + extra)
        )
        shelfSignature = ""   // tile widths depend on the padding
    }

    /** Tile width that makes every tile fit inside the padded screen. */
    private fun shelfTileWidth(count: Int): Int {
        val avail = resources.displayMetrics.widthPixels - contentBox.paddingLeft - contentBox.paddingRight - dp(16)
        return (avail / count.coerceAtLeast(1) - dp(16)).coerceIn(dp(96), dp(136))
    }

    private class ShelfItem(val name: String, val pkg: String)

    /** The shelf: the apps in the settings file. WorldVPN is left off, because the VPN chip opens it. */
    private fun shelfItems(): List<ShelfItem> {
        val hidden = prefs.hiddenApps
        return (settings?.apps ?: emptyList())
            .filter { it.pinned && it.packageName !in hidden && it.packageName !in Vpn.packages }
            .map { ShelfItem(it.name, it.packageName) }
    }

    private fun rebuildShelf() {
        placeShelf()
        val items = shelfItems()
        val sig = prefs.shelf + "#" + items.joinToString("|") { it.pkg + updater.isInstalled(it.pkg) }
        if (sig == shelfSignature && shelfRow.childCount > 0) return
        shelfSignature = sig

        val focusedTag = shelfRow.focusedChild?.tag?.toString()
        shelfRow.removeAllViews()
        val tileW = shelfTileWidth(items.size)
        items.forEach { a ->
            val installed = updater.isInstalled(a.pkg)
            val icon: Drawable? = if (installed) {
                try { packageManager.getApplicationIcon(a.pkg) } catch (e: Exception) { null }
            } else null
            val tile = makeTile(a.name, icon, a.name.take(1).uppercase(), a.pkg) {
                val i = packageManager.getLeanbackLaunchIntentForPackage(a.pkg)
                    ?: packageManager.getLaunchIntentForPackage(a.pkg)
                if (i == null) Toast.makeText(this, "${a.name} isn't installed yet. Open Setup & updates.", Toast.LENGTH_LONG).show()
                else startActivity(i)
            }
            shelfRow.addView(tile, tileParams(tileW))
        }
        hintView.text = if (settings == null) "Press the gear, then Setup & updates, to add your settings link." else ""
        if (focusedTag != null) shelfRow.findViewWithTag<View>(focusedTag)?.requestFocus()
    }

    // ───────────────────────── pieces ─────────────────────────

    private fun tileParams(width: Int = dp(136)): LinearLayout.LayoutParams {
        return if (prefs.shelf == "left") {
            // Shorter tiles in the rail so about four fit on screen; the rail scrolls for the rest.
            LinearLayout.LayoutParams(dp(136), dp(84)).apply { topMargin = dp(4); bottomMargin = dp(4) }
        } else {
            LinearLayout.LayoutParams(width, dp(108)).apply { marginStart = dp(8); marginEnd = dp(8) }
        }
    }

    private fun makeTile(label: String, icon: Drawable?, glyph: String, tag: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            isFocusable = true
            isClickable = true
            this.tag = tag
        }
        val labels = mutableListOf<Pair<TextView, Int>>()
        if (icon != null) {
            box.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        } else {
            val g = TextView(this).apply { text = glyph; textSize = 30f * prefs.textScale; gravity = Gravity.CENTER; setTextColor(accent) }
            box.addView(g, LinearLayout.LayoutParams(dp(48), dp(48)))
            labels += g to accent
        }
        val lbl = TextView(this).apply {
            text = label
            textSize = 15f * prefs.textScale
            setTextColor(textColor)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        }
        // Long names (like GalaxyTheater) shrink to fit the tile instead of being cut off.
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            lbl, (9 * prefs.textScale).toInt(), (15 * prefs.textScale).toInt(), 1, TypedValue.COMPLEX_UNIT_SP
        )
        box.addView(lbl, LinearLayout.LayoutParams(MATCH, WRAP))
        labels += lbl to textColor
        box.setOnClickListener { onClick() }
        style(box, 14f, labels)
        return box
    }

    private fun makeChip(spec: ChipSpec): View {
        val tv = TextView(this).apply {
            text = spec.text
            textSize = 16f * prefs.textScale
            setTextColor(spec.color)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        if (spec.onClick != null) {
            tv.isFocusable = true
            tv.isClickable = true
            tv.setOnClickListener { spec.onClick.invoke() }
            style(tv, 20f, listOf(tv to spec.color))
        } else {
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(20f)
                setColor(if (prefs.highContrast) Color.BLACK else Color.parseColor("#CC0A1522"))
                setStroke(if (prefs.highContrast) dp(2) else dp(1), if (prefs.highContrast) Color.WHITE else spec.color)
            }
        }
        return tv
    }

    // ───────────────────────── focus highlight ─────────────────────────
    // Selected item = solid accent fill, white ring, black outer ring, slightly larger.
    // The double ring stays visible on any background.

    private fun style(v: View, radiusDp: Float, labels: List<Pair<TextView, Int>>, extra: ((Boolean) -> Unit)? = null) {
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), ringBg(radiusDp))
            addState(intArrayOf(android.R.attr.state_pressed), ringBg(radiusDp))
            addState(intArrayOf(), normalBg(radiusDp))
        }
        v.setOnFocusChangeListener { view, has ->
            labels.forEach { (t, c) ->
                t.setTextColor(if (has) Color.BLACK else c)
                t.setTypeface(null, if (has) Typeface.BOLD else Typeface.NORMAL)
            }
            extra?.invoke(has)
            val s = if (has) 1.08f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    private fun normalBg(radiusDp: Float): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpf(radiusDp)
        setColor(cardColor)
        if (prefs.highContrast) setStroke(dp(2), Color.WHITE) else setStroke(dp(1), Color.parseColor("#33FFFFFF"))
    }

    private fun ringBg(radiusDp: Float): Drawable {
        fun layer(r: Float, c: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(maxOf(r, 0f))
            setColor(c)
        }
        return LayerDrawable(arrayOf<Drawable>(
            layer(radiusDp, Color.BLACK),
            layer(radiusDp - 3f, Color.WHITE),
            layer(radiusDp - 6f, accent)
        )).apply {
            setLayerInset(1, dp(3), dp(3), dp(3), dp(3))
            setLayerInset(2, dp(6), dp(6), dp(6), dp(6))
        }
    }

    // ───────────────────────── actions ─────────────────────────

    private fun openSetup() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openCleanup() {
        startActivity(Intent(this, CleanupActivity::class.java))
    }

    private fun openPay() { startActivity(Intent(this, PayActivity::class.java)) }
    private fun openContact() { startActivity(Intent(this, ContactActivity::class.java)) }

    /** The VPN chip opens the WorldVPN app, where each person signs in and connects. */
    private fun openVpnApp() {
        val i = Vpn.installed(this)?.let { packageManager.getLaunchIntentForPackage(it) }
        if (i == null) Toast.makeText(this, "The VPN app isn't installed on this stick. Open Setup & updates to install it.", Toast.LENGTH_LONG).show()
        else try { startActivity(i) } catch (e: Exception) { Toast.makeText(this, "Couldn't open the VPN app.", Toast.LENGTH_LONG).show() }
    }

    private fun askRestart(upHours: Long) {
        AlertDialog.Builder(this)
            .setTitle("Restart for best speed")
            .setMessage("This stick has been on for ${upHours / 24} days. A restart clears memory and keeps apps smooth. Go to Fire TV Settings, My Fire TV, Restart, or unplug it for 10 seconds.")
            .setPositiveButton("Open settings") { _, _ -> openFireSettings("Settings › My Fire TV › Restart") }
            .setNegativeButton("Not now") { _, _ ->
                prefs.restartSnoozeUntil = System.currentTimeMillis() + 24L * 3600 * 1000
                rebuildChips()
            }
            .show()
    }

    /** The stick's own settings (Wi-Fi, display, restart). Always reachable from the gear menu. */
    private fun openFireSettings(hint: String = "Settings") {
        SystemSettings.open(this, SystemSettings.Target.MAIN, null, hint)
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Galaxy Media")
            .setItems(arrayOf("All apps", "Stick health", "Pay / Renew", if (DeviceProfile.detect(this).platform == "fireos") "Fire TV settings" else "TV settings", "Settings", "Setup & updates", "Help: fix it")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, AllAppsActivity::class.java))
                    1 -> startActivity(Intent(this, HealthActivity::class.java))
                    2 -> openPay()
                    3 -> openFireSettings()
                    4 -> startActivity(Intent(this, SettingsActivity::class.java))
                    5 -> openSetup()
                    else -> startActivity(Intent(this, HelpActivity::class.java))
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun askName() {
        val input = EditText(this).apply {
            hint = "Your name"
            setSingleLine(true)
            setText(prefs.userName)
        }
        AlertDialog.Builder(this)
            .setTitle("What's your name?")
            .setMessage("It shows in your greeting on the home screen.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.userName = input.text.toString().trim()
                updateGreeting()
                askZip()
            }
            .setNegativeButton("Skip") { _, _ -> askZip() }
            .setOnDismissListener { prefs.namePrompted = true }
            .show()
    }

    // First-run steps after the name: ZIP code (weather), then the setup code (apps).

    private fun askZip() {
        val input = EditText(this).apply {
            hint = "ZIP code"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(5))
        }
        AlertDialog.Builder(this)
            .setTitle("What's your ZIP code?")
            .setMessage("It sets the weather on your home screen.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val zip = input.text.toString().trim()
                if (zip.length != 5) {
                    Toast.makeText(this, "A ZIP code is 5 digits. You can set weather later in Settings.", Toast.LENGTH_LONG).show()
                    askCode()
                } else {
                    io.launch {
                        val city = try { WeatherRepository.lookupZip(zip) } catch (e: Exception) { null }
                        withContext(Dispatchers.Main) {
                            if (isFinishing) return@withContext
                            if (city != null) {
                                prefs.weatherName = city.name
                                prefs.weatherLat = city.lat
                                prefs.weatherLon = city.lon
                                refreshWeather()
                                Toast.makeText(this@HomeActivity, "Weather set for ${city.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@HomeActivity, "Couldn't look up that ZIP. You can set a city later in Settings.", Toast.LENGTH_LONG).show()
                            }
                            askCode()
                        }
                    }
                }
            }
            .setNegativeButton("Skip") { _, _ -> askCode() }
            .show()
    }

    private fun askCode(prefill: String = "") {
        if (prefs.settingsUrl.isNotBlank()) return
        val input = EditText(this).apply {
            hint = "GAL-1234"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(prefill)
        }
        AlertDialog.Builder(this)
            .setTitle("Enter your setup code")
            .setMessage("It sets up your apps. Ask the owner if you don't have one.")
            .setView(input)
            .setPositiveButton("Continue") { _, _ ->
                val code = input.text.toString().trim()
                Toast.makeText(this, "Checking code…", Toast.LENGTH_SHORT).show()
                io.launch {
                    val r = SetupCodes.resolve(code)
                    if (r.url == null) {
                        withContext(Dispatchers.Main) {
                            if (isFinishing) return@withContext
                            Toast.makeText(this@HomeActivity, r.error ?: "Code not found.", Toast.LENGTH_LONG).show()
                            askCode(code)
                        }
                        return@launch
                    }
                    prefs.settingsUrl = r.url
                    val fresh = try { SettingsRepository(this@HomeActivity).fetch { } } catch (e: Exception) { null }
                    withContext(Dispatchers.Main) {
                        if (isFinishing) return@withContext
                        if (fresh != null) {
                            prefs.lastSync = System.currentTimeMillis()
                            settings = fresh
                            rebuildAll()
                            startActivity(Intent(this@HomeActivity, MainActivity::class.java).putExtra("autoInstall", true))
                        } else {
                            Toast.makeText(this@HomeActivity, "Code accepted, but the settings didn't load. Open Setup & updates and press Check updates.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Skip for now", null)
            .show()
    }

    // ───────────────────────── custom views ─────────────────────────

    /**
     * Stars. motion = "off" draws them still, "reduced" is a slow subtle twinkle, "normal" is a
     * strong twinkle plus a slow upward drift and an occasional shooting star.
     * Redraws about 10 (reduced) or 20 (normal) times a second.
     */
    private class StarfieldView(ctx: Context, private val motion: String) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
        // x, y, size, base alpha, phase, speed, drift x, drift y
        private val stars: List<FloatArray>

        init {
            val rnd = Random(11)
            stars = List(70) { i ->
                floatArrayOf(
                    rnd.nextFloat(), rnd.nextFloat(), 1f + rnd.nextFloat() * 1.6f, 0.2f + rnd.nextFloat() * 0.6f,
                    i * 1.7f, 0.8f + (i % 5) * 0.25f,
                    ((i % 7) - 3) * 0.001f, -(0.004f + (i % 4) * 0.003f)
                )
            }
        }

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val t = SystemClock.uptimeMillis() / 1000f
            val normal = motion == "normal"
            val moving = normal || motion == "reduced"
            val slow = if (normal) 1.6f else 0.35f
            val base = if (normal) 0.55f else 0.8f
            val amp = if (normal) 0.45f else 0.2f
            for (s in stars) {
                val f = if (moving) base + amp * sin(t * s[5] * slow + s[4]) else 1f
                var x = s[0]
                var y = s[1]
                if (normal) {
                    x = ((x + s[6] * t) % 1f + 1f) % 1f
                    y = ((y + s[7] * t) % 1f + 1f) % 1f
                }
                paint.alpha = (s[3] * f * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(x * width, y * height, s[2] * d, paint)
            }
            if (normal) drawShootingStar(canvas, t, d)
            if (moving) postInvalidateDelayed(if (normal) 50L else 100L)
        }

        /** One streak across the sky every 9 seconds, visible for about a second. */
        private fun drawShootingStar(canvas: Canvas, t: Float, d: Float) {
            val period = 9f
            val slot = (t / period).toInt()
            val p = t - slot * period
            if (p > 1f) return
            val r = Random(slot * 7919)
            val x0 = 0.1f + r.nextFloat() * 0.7f
            val y0 = r.nextFloat() * 0.4f
            val hx = (x0 + p * 0.28f) * width
            val hy = (y0 + p * 0.16f) * height
            val tailP = (p - 0.25f).coerceAtLeast(0f)
            val tx = (x0 + tailP * 0.28f) * width
            val ty = (y0 + tailP * 0.16f) * height
            streak.strokeWidth = 2f * d
            streak.alpha = ((1f - p) * 220).toInt().coerceIn(0, 255)
            canvas.drawLine(tx, ty, hx, hy, streak)
        }
    }

    /**
     * Light trace: two bright beams with fading tails run around the screen edge in the accent
     * color, curving around rounded corners. motion "off" shows only the faint frame, "reduced"
     * is a slow lap, "normal" is faster.
     */
    private class LightTraceView(
        ctx: Context, private val motion: String, private val color: Int, private val beamColor: Int, private val insetDp: Float
    ) : View(ctx) {
        private val hot = ColorUtils.blendARGB(beamColor, Color.WHITE, 0.55f)
        private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val a = FloatArray(2)
        private val b = FloatArray(2)

        private fun perimeter(l: Float, tp: Float, r: Float, bt: Float, rad: Float): Float =
            2f * ((r - l) - 2f * rad) + 2f * ((bt - tp) - 2f * rad) + 2f * PI.toFloat() * rad

        /** Point on the rounded rectangle, `dist` along it from the top edge going clockwise. */
        private fun pointAt(dist: Float, l: Float, tp: Float, r: Float, bt: Float, rad: Float, out: FloatArray) {
            val sw = (r - l) - 2f * rad
            val sh = (bt - tp) - 2f * rad
            val arc = (PI.toFloat() / 2f) * rad
            val per = 2f * sw + 2f * sh + 4f * arc
            var d = ((dist % per) + per) % per
            if (d < sw) { out[0] = l + rad + d; out[1] = tp; return }
            d -= sw
            if (d < arc) { val t = d / rad; out[0] = r - rad + sin(t) * rad; out[1] = tp + rad - cos(t) * rad; return }
            d -= arc
            if (d < sh) { out[0] = r; out[1] = tp + rad + d; return }
            d -= sh
            if (d < arc) { val t = d / rad; out[0] = r - rad + cos(t) * rad; out[1] = bt - rad + sin(t) * rad; return }
            d -= arc
            if (d < sw) { out[0] = r - rad - d; out[1] = bt; return }
            d -= sw
            if (d < arc) { val t = d / rad; out[0] = l + rad - sin(t) * rad; out[1] = bt - rad + cos(t) * rad; return }
            d -= arc
            if (d < sh) { out[0] = l; out[1] = bt - rad - d; return }
            d -= sh
            val t = d / rad
            out[0] = l + rad - cos(t) * rad
            out[1] = tp + rad - sin(t) * rad
        }

        override fun onDraw(canvas: Canvas) {
            val dn = resources.displayMetrics.density
            val inset = insetDp * dn
            val l = inset
            val tp = inset
            val r = width - inset
            val bt = height - inset
            val rad = 28f * dn
            val per = perimeter(l, tp, r, bt, rad)

            frame.color = color
            frame.alpha = 45
            frame.strokeWidth = 1.5f * dn
            canvas.drawRoundRect(l, tp, r, bt, rad, rad, frame)
            if (motion == "off") return

            val normal = motion == "normal"
            val lap = if (normal) 14f else 36f
            val now = SystemClock.uptimeMillis() / 1000f
            val head = (now / lap) * per
            val steps = 26
            val step = per * 0.22f / steps
            for (beam in 0 until 2) {
                val h0 = head + beam * per / 2f
                for (i in 0 until steps) {
                    pointAt(h0 - i * step, l, tp, r, bt, rad, a)
                    pointAt(h0 - (i + 1) * step, l, tp, r, bt, rad, b)
                    val fade = 1f - i / steps.toFloat()
                    glow.color = beamColor
                    glow.alpha = (95 * fade * fade).toInt()
                    glow.strokeWidth = 10f * dn
                    canvas.drawLine(a[0], a[1], b[0], b[1], glow)
                    core.color = hot
                    core.alpha = (240 * fade * fade).toInt()
                    core.strokeWidth = 3.5f * dn
                    canvas.drawLine(a[0], a[1], b[0], b[1], core)
                }
                pointAt(h0, l, tp, r, bt, rad, a)
                canvas.drawCircle(a[0], a[1], 3.5f * dn, dot)
            }
            postInvalidateDelayed(if (normal) 40L else 66L)
        }
    }

    /**
     * Still picture backgrounds drawn by the app (no image files): nebula, sunset, ocean,
     * midnight city, neon grid, mountains. They draw once and are dimmed so the text stays readable.
     */
    private class SceneView(ctx: Context, private val kind: String, private val accent: Int) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = android.graphics.Path()

        private fun sky(canvas: Canvas, w: Float, h: Float, colors: IntArray, pos: FloatArray?) {
            p.style = Paint.Style.FILL
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, colors, pos, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null
        }

        private fun glowAt(canvas: Canvas, cx: Float, cy: Float, r: Float, argb: Int) {
            val rgb = argb and 0x00FFFFFF
            p.style = Paint.Style.FILL
            p.shader = android.graphics.RadialGradient(cx, cy, r, argb, rgb, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, p)
            p.shader = null
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val d = resources.displayMetrics.density
            when (kind) {
                "nebula" -> {
                    canvas.drawColor(0xFF050816.toInt())
                    glowAt(canvas, w * 0.20f, h * 0.30f, w * 0.45f, 0x6A7C3AED)
                    glowAt(canvas, w * 0.75f, h * 0.25f, w * 0.40f, 0x5A2563EB)
                    glowAt(canvas, w * 0.55f, h * 0.78f, w * 0.50f, 0x50DB2777)
                    glowAt(canvas, w * 0.92f, h * 0.88f, w * 0.35f, 0x4010B981)
                    val rnd = Random(5)
                    p.color = Color.WHITE
                    for (i in 0 until 90) {
                        p.alpha = 60 + rnd.nextInt(150)
                        canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, (0.8f + rnd.nextFloat() * 1.4f) * d, p)
                    }
                }
                "sunset" -> {
                    sky(canvas, w, h, intArrayOf(0xFF1B1035.toInt(), 0xFF5B2A86.toInt(), 0xFFE0457B.toInt(), 0xFFFF9A3C.toInt()),
                        floatArrayOf(0f, 0.45f, 0.78f, 1f))
                    glowAt(canvas, w * 0.5f, h * 0.80f, h * 0.45f, 0x66FFD27F)
                    p.color = 0xFFFFD27F.toInt(); p.alpha = 235
                    canvas.drawCircle(w * 0.5f, h * 0.80f, h * 0.15f, p)
                    p.color = 0xFF0B0714.toInt(); p.alpha = 255
                    canvas.drawRect(0f, h * 0.86f, w, h, p)
                }
                "ocean" -> {
                    sky(canvas, w, h, intArrayOf(0xFF03121F.toInt(), 0xFF0B3A5B.toInt()), null)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2f * d
                    p.color = 0xFF22D3EE.toInt()
                    for (i in 0 until 7) {
                        p.alpha = 30 + i * 8
                        val base = h * (0.52f + i * 0.07f)
                        val amp = h * (0.008f + i * 0.003f)
                        path.reset()
                        var x = 0f
                        path.moveTo(0f, base)
                        while (x <= w) {
                            path.lineTo(x, base + amp * sin(x / w * 6.2832f * (2.5f + i * 0.3f) + i))
                            x += 20f
                        }
                        canvas.drawPath(path, p)
                    }
                    p.style = Paint.Style.FILL
                }
                "city" -> {
                    sky(canvas, w, h, intArrayOf(0xFF060B1E.toInt(), 0xFF1B2547.toInt(), 0xFF3B2A5A.toInt()), floatArrayOf(0f, 0.7f, 1f))
                    p.color = 0xFFFFF3C4.toInt(); p.alpha = 220
                    canvas.drawCircle(w * 0.82f, h * 0.2f, h * 0.05f, p)
                    val rnd = Random(9)
                    for (layer in 0 until 2) {
                        var x = -20f * d
                        while (x < w) {
                            val bw = (40f + rnd.nextFloat() * 50f) * d
                            val bh = h * (if (layer == 0) 0.18f + rnd.nextFloat() * 0.26f else 0.12f + rnd.nextFloat() * 0.34f)
                            p.color = if (layer == 0) 0xFF10152B.toInt() else 0xFF070A14.toInt()
                            p.alpha = 255
                            canvas.drawRect(x, h - bh, x + bw, h, p)
                            if (layer == 1) {
                                p.color = 0xFFFFD27F.toInt()
                                var wy = h - bh + 10f * d
                                while (wy < h - 12f * d) {
                                    var wx = x + 8f * d
                                    while (wx < x + bw - 10f * d) {
                                        if (rnd.nextFloat() < 0.45f) {
                                            p.alpha = 90 + rnd.nextInt(140)
                                            canvas.drawRect(wx, wy, wx + 5f * d, wy + 7f * d, p)
                                        }
                                        wx += 14f * d
                                    }
                                    wy += 18f * d
                                }
                            }
                            x += bw + (2f + rnd.nextFloat() * 6f) * d
                        }
                    }
                }
                "grid" -> {
                    sky(canvas, w, h, intArrayOf(0xFF0B0221.toInt(), 0xFF2A0A4A.toInt()), null)
                    val hor = h * 0.56f
                    p.shader = android.graphics.LinearGradient(0f, hor - h * 0.22f, 0f, hor, 0xFFFFD166.toInt(), 0xFFEF476F.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    canvas.drawCircle(w * 0.5f, hor - h * 0.02f, h * 0.2f, p)
                    p.shader = null
                    p.color = 0xFF0B0221.toInt(); p.alpha = 255
                    canvas.drawRect(0f, hor, w, h, p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 1.5f * d
                    p.color = accent; p.alpha = 150
                    for (k in 1..8) {
                        val y = hor + (h - hor) * (k / 8f) * (k / 8f)
                        canvas.drawLine(0f, y, w, y, p)
                    }
                    for (i in -12..12) {
                        canvas.drawLine(w * 0.5f + i * w * 0.02f, hor, w * 0.5f + i * w * 0.14f, h, p)
                    }
                    p.style = Paint.Style.FILL
                }
                "mountains" -> {
                    sky(canvas, w, h, intArrayOf(0xFF0F1230.toInt(), 0xFF3A2E6B.toInt(), 0xFFE58C6B.toInt()), floatArrayOf(0f, 0.55f, 1f))
                    val layers = intArrayOf(0xFF2A2350.toInt(), 0xFF1A1638.toInt(), 0xFF0C0A1E.toInt())
                    for (k in 0 until 3) {
                        val base = h * (0.60f + k * 0.10f)
                        val amp = h * (0.13f - k * 0.02f)
                        path.reset()
                        path.moveTo(0f, h)
                        var x = 0f
                        while (x <= w + 12f) {
                            val y = base - amp * (0.55f + 0.45f * sin(x / w * (5f + k * 2f) + k * 1.7f) + 0.25f * sin(x / w * (13f + k * 3f) + k))
                            path.lineTo(x, y)
                            x += 12f
                        }
                        path.lineTo(w + 12f, h)
                        path.close()
                        p.color = layers[k]; p.alpha = 255
                        canvas.drawPath(path, p)
                    }
                }
            }
            // Dim everything a little so clocks and chips stay easy to read.
            p.shader = null
            p.style = Paint.Style.FILL
            p.color = Color.BLACK
            p.alpha = 90
            canvas.drawRect(0f, 0f, w, h, p)
        }
    }

    /**
     * Picture wallpaper: a logo-free starry backdrop plus the logo drawn as its own layer, shrunk to
     * fit the clear space above the live-game banner (setZone, measured from the real layout). A light
     * runs along the neon outlines of the logo. The outlines were measured from each picture and
     * live in assets/traces.json as paths in original-picture pixels / 2000, with the logo's crop.
     * motion "off" shows the still picture.
     */
    private class PictureTraceView(
        ctx: Context, key: String, private val motion: String, private val color: Int
    ) : View(ctx) {
        private var bg: Bitmap? = null
        private var logo: Bitmap? = null
        private val paths = ArrayList<FloatArray>()
        private val cums = ArrayList<FloatArray>()
        private var cropX = 0f
        private var cropY = 0f
        private var zl = 0f
        private var zt = -1f
        private var zr = 0f
        private var zb = 0f
        private val pic = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
        private val hot = ColorUtils.blendARGB(color, Color.WHITE, 0.5f)
        private val a = FloatArray(2)
        private val b = FloatArray(2)
        private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val starX = FloatArray(90)
        private val starY = FloatArray(90)
        private val starPhase = FloatArray(90)
        private val starFreq = FloatArray(90)
        private val starSize = FloatArray(90)

        init {
            val rnd = java.util.Random(77)
            for (i in 0 until 90) {
                starX[i] = rnd.nextFloat(); starY[i] = rnd.nextFloat()
                starPhase[i] = rnd.nextFloat() * 6.283f
                starFreq[i] = 0.6f + rnd.nextFloat() * 1.8f
                starSize[i] = rnd.nextFloat()
            }
            try {
                val r = ctx.resources
                val id = r.getIdentifier(key, "drawable", ctx.packageName)
                if (id != 0) bg = BitmapFactory.decodeResource(r, id)
                val lid = r.getIdentifier(key + "_logo", "drawable", ctx.packageName)
                if (lid != 0) logo = BitmapFactory.decodeResource(r, lid)
            } catch (e: Throwable) { bg = null; logo = null }
            try {
                val text = ctx.assets.open("traces.json").bufferedReader().use { it.readText() }
                val obj = JSONObject(text).optJSONObject(key)
                if (obj != null) {
                    val crop = obj.getJSONArray("crop")
                    cropX = crop.getDouble(0).toFloat()
                    cropY = crop.getDouble(1).toFloat()
                    val arr = obj.getJSONArray("paths")
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONArray(i)
                        val n = p.length() / 2
                        val pts = FloatArray(n * 2) { p.getDouble(it).toFloat() }
                        val cum = FloatArray(n + 1)
                        for (j in 0 until n) {
                            val k = (j + 1) % n
                            cum[j + 1] = cum[j] + kotlin.math.hypot(pts[k * 2] - pts[j * 2], pts[k * 2 + 1] - pts[j * 2 + 1])
                        }
                        paths.add(pts); cums.add(cum)
                    }
                }
            } catch (e: Exception) { paths.clear(); cums.clear() }
        }

        /** The clear area (in this view's pixels) where the logo should sit, e.g. above the live-game banner. */
        fun setZone(l: Float, t: Float, r: Float, bt: Float) {
            if (l == zl && t == zt && r == zr && bt == zb) return
            zl = l; zt = t; zr = r; zb = bt
            invalidate()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            bg?.recycle(); logo?.recycle()
            bg = null; logo = null
        }

        /** Point `s` along the closed path (picture units), written into out. */
        private fun pointAt(pts: FloatArray, cum: FloatArray, s: Float, out: FloatArray) {
            val n = pts.size / 2
            val total = cum[n]
            val d = ((s % total) + total) % total
            var lo = 0
            var hi = n
            while (lo < hi - 1) {
                val mid = (lo + hi) ushr 1
                if (cum[mid] <= d) lo = mid else hi = mid
            }
            val seg = cum[lo + 1] - cum[lo]
            val f = if (seg > 0f) (d - cum[lo]) / seg else 0f
            val k = (lo + 1) % n
            out[0] = pts[lo * 2] + (pts[k * 2] - pts[lo * 2]) * f
            out[1] = pts[lo * 2 + 1] + (pts[k * 2 + 1] - pts[lo * 2 + 1]) * f
        }

        override fun onDraw(canvas: Canvas) {
            val bm = bg ?: return
            val iw = bm.width.toFloat()
            val ih = bm.height.toFloat()
            val moving = motion != "off"
            val normal = motion == "normal"
            val now = SystemClock.uptimeMillis() / 1000f
            val dn = resources.displayMetrics.density
            // The backdrop drifts slowly behind the logo (a little zoomed in so the edges never show).
            val sc = maxOf(width / iw, height / ih) * (if (moving) 1.1f else 1f)
            val slackX = iw * sc - width
            val slackY = ih * sc - height
            val k = if (normal) 1f else 0.4f
            val px = if (moving) 0.5f + 0.5f * sin(now * k * 0.09f) else 0.5f
            val py = if (moving) 0.5f + 0.5f * sin(now * k * 0.065f + 1.3f) else 0.5f
            canvas.save()
            canvas.translate(-slackX * px, -slackY * py)
            canvas.scale(sc, sc)
            canvas.drawBitmap(bm, 0f, 0f, pic)
            canvas.restore()
            if (moving) {
                // Twinkling stars that float slowly upward
                for (i in 0 until starX.size) {
                    val y = (((starY[i] - now * 0.004f * k * (0.5f + starSize[i])) % 1f) + 1f) % 1f
                    val tw = 0.5f + 0.5f * sin(now * starFreq[i] * k + starPhase[i])
                    starPaint.color = Color.argb((40 + 200 * tw * tw).toInt(), 210, 225, 255)
                    canvas.drawCircle(starX[i] * width, y * height, (0.7f + starSize[i] * 1.6f) * dn, starPaint)
                }
                postInvalidateDelayed(if (normal) 33L else 66L)
            }

            val lg = logo ?: return
            // Fit the logo into the clear zone (default: the band above the ticker and shelf)
            val l = if (zt >= 0f) zl else 0f
            val t = if (zt >= 0f) zt else height * 0.19f
            val r = if (zt >= 0f) zr else width.toFloat()
            val bt = if (zt >= 0f) zb else height * 0.57f
            val s = minOf((r - l) * 0.7f / lg.width, (bt - t) * 0.94f / lg.height)
            if (s <= 0f) return
            val left = (l + r) / 2f - lg.width * s / 2f
            val top = (t + bt) / 2f - lg.height * s / 2f
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(s, s)
            canvas.drawBitmap(lg, 0f, 0f, pic)
            canvas.restore()
            if (motion == "off" || paths.isEmpty()) return

            val d = dn
            val speed = if (normal) 0.14f else 0.05f       // picture units per second
            val unit = 2000f * s                            // picture unit -> screen pixels
            val ox = left - cropX * s
            val oy = top - cropY * s
            val tail = 0.2f
            val steps = 28
            val step = tail / steps
            for (pi in paths.indices) {
                val pts = paths[pi]
                val cum = cums[pi]
                val dir = if (pi % 2 == 0) 1f else -1f
                val head = now * speed * dir
                for (i in 0 until steps) {
                    pointAt(pts, cum, head - dir * i * step, a)
                    pointAt(pts, cum, head - dir * (i + 1) * step, b)
                    val fade = 1f - i / steps.toFloat()
                    glow.color = color
                    glow.alpha = (110 * fade * fade).toInt()
                    glow.strokeWidth = 12f * d
                    canvas.drawLine(ox + a[0] * unit, oy + a[1] * unit, ox + b[0] * unit, oy + b[1] * unit, glow)
                    core.color = hot
                    core.alpha = (240 * fade * fade).toInt()
                    core.strokeWidth = 4f * d
                    canvas.drawLine(ox + a[0] * unit, oy + a[1] * unit, ox + b[0] * unit, oy + b[1] * unit, core)
                }
                pointAt(pts, cum, head, a)
                canvas.drawCircle(ox + a[0] * unit, oy + a[1] * unit, 5.5f * d, dot)
            }
        }
    }

    /**
     * Aurora drift: three soft color blobs (cyan, purple, green) slowly wander behind everything.
     * Light on the GPU: the blobs are drawn on a small bitmap (1/6 size) and that is scaled up once per frame.
     */
    private class AuroraView(ctx: Context, private val motion: String) : View(ctx) {
        private val colors = intArrayOf(0xFF22D3EE.toInt(), 0xFFA78BFA.toInt(), 0xFF4ADE80.toInt())
        private val paints = colors.map { Paint(Paint.ANTI_ALIAS_FLAG) }
        private val up = Paint(Paint.FILTER_BITMAP_FLAG)
        private var small: Bitmap? = null
        private var smallCanvas: Canvas? = null
        private var radius = 0f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val sw = (w / 6).coerceAtLeast(8)
            val sh = (h / 6).coerceAtLeast(8)
            small?.recycle()
            val bm = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            small = bm
            smallCanvas = Canvas(bm)
            radius = sw * 0.42f
            for (i in colors.indices) {
                val rgb = colors[i] and 0x00FFFFFF
                paints[i].shader = android.graphics.RadialGradient(
                    0f, 0f, radius, rgb or (0x46 shl 24), rgb, android.graphics.Shader.TileMode.CLAMP
                )
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            small?.recycle()
            small = null
        }

        override fun onDraw(canvas: Canvas) {
            val bm = small ?: return
            val c = smallCanvas ?: return
            if (radius <= 0f) return
            val still = motion == "off"
            val t = if (still) 0f else SystemClock.uptimeMillis() / 1000f
            val speed = if (motion == "normal") 2.4f else 0.8f
            c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
            for (i in colors.indices) {
                val ang = t * speed * (0.16f + 0.05f * i) + i * 2.1f
                val cx = bm.width * (0.5f + 0.32f * cos(ang + i))
                val cy = bm.height * (0.5f + 0.28f * sin(ang * 1.3f + i * 1.7f))
                c.save()
                c.translate(cx, cy)
                c.drawCircle(0f, 0f, radius, paints[i])
                c.restore()
            }
            canvas.drawBitmap(bm, null, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), up)
            if (!still) postInvalidateDelayed(if (motion == "normal") 50L else 100L)
        }
    }

    /** A simple gear drawn on the canvas (no font or image needed). */
    private class GearView(ctx: Context) : View(ctx) {
        var tint: Int = Color.WHITE
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = min(width, height) / 2f * 0.46f
            paint.color = tint
            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeWidth = r * 0.34f
            canvas.drawCircle(cx, cy, r, paint)
            paint.strokeWidth = r * 0.42f
            for (i in 0 until 8) {
                val a = i * PI / 4
                val x1 = cx + cos(a).toFloat() * r * 1.1f
                val y1 = cy + sin(a).toFloat() * r * 1.1f
                val x2 = cx + cos(a).toFloat() * r * 1.55f
                val y2 = cy + sin(a).toFloat() * r * 1.55f
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
        }
    }
}
