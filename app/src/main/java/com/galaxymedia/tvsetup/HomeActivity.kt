package com.galaxymedia.tvsetup

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.galaxymedia.tvsetup.model.DeviceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
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
    private lateinit var hintView: TextView
    private var gearView: GearView? = null

    private var settings: DeviceSettings? = null
    private var selfUpdateVersion: String? = null
    private var chipSignature = ""
    private var shelfSignature = ""

    companion object {
        private var lastRefresh = 0L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    // Palette from the design canvas
    private val bgColor = Color.parseColor("#060A16")
    private val cardColor = Color.parseColor("#0F1C2E")
    private val textColor = Color.parseColor("#EAF6FF")
    private val mutedColor = Color.parseColor("#9FB8C8")
    private val accent = Color.parseColor("#22D3EE")
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
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        updater = AppUpdater(this, prefs)
        setContentView(buildUi())
        loadStoredSettings()
        rebuildAll()
        shelfRow.post { shelfRow.getChildAt(0)?.requestFocus() }
        if (!prefs.namePrompted) askName()
    }

    override fun onResume() {
        super.onResume()
        loadStoredSettings()
        rebuildAll()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 15_000)
        refreshFromNetwork()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    // ───────────────────────── UI ─────────────────────────

    private fun buildUi(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(bgColor)
            clipChildren = false
            clipToPadding = false
        }
        root.addView(StarfieldView(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(36), dp(56), dp(40))   // safe margins
            clipChildren = false
            clipToPadding = false
        }

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
            textSize = 34f
            setTextColor(textColor)
        }
        greetingView = TextView(this).apply { textSize = 18f; setTextColor(mutedColor) }
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

        // Center: title (and a hint when setup isn't finished)
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        center.addView(TextView(this).apply {
            text = "GALAXY MEDIA"
            textSize = 40f
            letterSpacing = 0.12f
            setTextColor(textColor)
            gravity = Gravity.CENTER
        })
        center.addView(TextView(this).apply {
            text = "FAMILY EDITION"
            textSize = 14f
            letterSpacing = 0.3f
            setTextColor(Color.parseColor("#7FD8E8"))
            gravity = Gravity.CENTER
        })
        hintView = TextView(this).apply {
            textSize = 16f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        center.addView(hintView)
        content.addView(center, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Bottom: app shelf
        val scroll = HorizontalScrollView(this).apply {
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
        scroll.addView(shelfRow, FrameLayout.LayoutParams(MATCH, WRAP))
        content.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
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
                selfUpdateVersion = if (rel != null && updater.isNewer(rel.version, mine)) rel.version else null
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

    // ───────────────────────── rebuild ─────────────────────────

    private fun rebuildAll() {
        updateGreeting()
        rebuildChips()
        rebuildShelf()
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
    }

    private fun renewalChip(): ChipSpec? {
        val exp = settings?.iptvExpires ?: return null
        if (exp.isBlank()) return null
        val date = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(exp) } catch (e: Exception) { null } ?: return null
        val endOfDay = date.time + 86_399_000L
        val days = Math.floorDiv(endOfDay - System.currentTimeMillis(), 86_400_000L).toInt()
        val nice = SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        val color = when {
            days <= 3 -> red
            days <= 14 -> amber
            else -> mutedColor
        }
        return ChipSpec(if (days < 0) "IPTV expired $nice" else "IPTV renews $nice", color, null)
    }

    private fun rebuildChips() {
        val chips = mutableListOf<ChipSpec>()
        val s = settings
        if (s != null) {
            val st = updater.statuses(s.apps)
            val upd = st.count { it.state == "UPDATE" }
            val miss = st.count { it.state == "MISSING" }
            if (upd > 0) chips += ChipSpec(if (upd == 1) "1 app update ready" else "$upd app updates ready", accent) { openSetup() }
            if (miss > 0) chips += ChipSpec("$miss to install", accent) { openSetup() }
        }
        if (selfUpdateVersion != null) chips += ChipSpec("Galaxy Media update", accent) { openSetup() }
        renewalChip()?.let { chips += it }
        val vpn = vpnOn()
        chips += ChipSpec(if (vpn) "VPN on" else "VPN off", if (vpn) green else mutedColor, null)

        val sig = chips.joinToString("|") { it.text + it.color }
        if (sig == chipSignature && chipRow.childCount > 0) return
        chipSignature = sig

        val hadGearFocus = gearView?.hasFocus() == true
        chipRow.removeAllViews()
        chips.forEach { chipRow.addView(makeChip(it), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(10) }) }

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

    private fun rebuildShelf() {
        val apps = settings?.apps?.filter { it.pinned } ?: emptyList()
        val sig = apps.joinToString("|") { it.packageName + updater.isInstalled(it.packageName) }
        if (sig == shelfSignature && shelfRow.childCount > 0) return
        shelfSignature = sig

        val focusedTag = shelfRow.focusedChild?.tag?.toString()
        shelfRow.removeAllViews()
        apps.forEach { a ->
            val installed = updater.isInstalled(a.packageName)
            val icon: Drawable? = if (installed) {
                try { packageManager.getApplicationIcon(a.packageName) } catch (e: Exception) { null }
            } else null
            val tile = makeTile(a.name, icon, a.name.take(1).uppercase(), a.packageName) {
                val i = packageManager.getLeanbackLaunchIntentForPackage(a.packageName)
                    ?: packageManager.getLaunchIntentForPackage(a.packageName)
                if (i == null) Toast.makeText(this, "${a.name} isn't installed yet. Open Setup & updates.", Toast.LENGTH_LONG).show()
                else startActivity(i)
            }
            shelfRow.addView(tile, tileParams())
        }
        shelfRow.addView(makeTile("More apps", null, "+", "more") { openStore() }, tileParams())
        hintView.text = if (settings == null) "Press the gear, then Setup & updates, to add your settings link." else ""
        if (focusedTag != null) shelfRow.findViewWithTag<View>(focusedTag)?.requestFocus()
    }

    // ───────────────────────── pieces ─────────────────────────

    private fun tileParams() = LinearLayout.LayoutParams(dp(136), dp(108)).apply {
        marginStart = dp(8); marginEnd = dp(8)
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
            val g = TextView(this).apply { text = glyph; textSize = 30f; gravity = Gravity.CENTER; setTextColor(accent) }
            box.addView(g, LinearLayout.LayoutParams(dp(48), dp(48)))
            labels += g to accent
        }
        val lbl = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        }
        box.addView(lbl, LinearLayout.LayoutParams(MATCH, WRAP))
        labels += lbl to textColor
        box.setOnClickListener { onClick() }
        style(box, 14f, labels)
        return box
    }

    private fun makeChip(spec: ChipSpec): View {
        val tv = TextView(this).apply {
            text = spec.text
            textSize = 16f
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
                setColor(Color.parseColor("#CC0A1522"))
                setStroke(dp(1), spec.color)
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
        setStroke(dp(1), Color.parseColor("#33FFFFFF"))
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

    private fun openStore() {
        val i = packageManager.getLaunchIntentForPackage("com.amazon.venezia")
        if (i == null) Toast.makeText(this, "Press Home, then find the Appstore on the Fire TV home screen.", Toast.LENGTH_LONG).show()
        else startActivity(i)
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Galaxy Media")
            .setItems(arrayOf("Setup & updates", "Change my name")) { _, which ->
                if (which == 0) openSetup() else askName()
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
            }
            .setNegativeButton("Skip", null)
            .setOnDismissListener { prefs.namePrompted = true }
            .show()
    }

    // ───────────────────────── custom views ─────────────────────────

    private class StarfieldView(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val stars: List<FloatArray>

        init {
            val rnd = Random(11)
            stars = List(70) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 1f + rnd.nextFloat() * 1.6f, 0.2f + rnd.nextFloat() * 0.6f) }
        }

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            for (s in stars) {
                paint.alpha = (s[3] * 255).toInt()
                canvas.drawCircle(s[0] * width, s[1] * height, s[2] * d, paint)
            }
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
