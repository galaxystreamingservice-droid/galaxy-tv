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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import com.galaxymedia.tvsetup.model.DeviceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var tonightView: TextView
    private lateinit var sportsBox: LinearLayout
    private lateinit var sportsText: TextView
    private var sportsSignature = ""
    private var sportsBusy = false
    private var appliedAccent = ""
    private var appliedBackground = ""
    private var appliedShelf = ""
    private var appliedLook = ""

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
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
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
        rebuildAll()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 15_000)
        refreshFromNetwork()
        refreshSports()
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
        bgHolder = FrameLayout(this)
        root.addView(bgHolder, FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(36), dp(56), dp(40)) // safe margins
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
        titleBox.addView(TextView(this).apply {
            text = "FAMILY EDITION"
            textSize = 14f * prefs.textScale
            letterSpacing = 0.3f
            setTextColor(Color.parseColor("#7FD8E8"))
            gravity = Gravity.CENTER
        })
        center.addView(titleBox)
        // Display only: not focusable, so the remote skips it and nobody thinks it opens the game.
        tonightView = TextView(this).apply {
            textSize = 18f * prefs.textScale
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            visibility = View.GONE
        }
        center.addView(tonightView)
        hintView = TextView(this).apply {
            textSize = 16f * prefs.textScale
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        center.addView(hintView)
        middle.addView(center, LinearLayout.LayoutParams(0, MATCH, 1f))
        content.addView(middle, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Sports strip (scrolls sideways by itself; hidden when off or when there are no games)
        sportsText = TextView(this).apply {
            textSize = 16f * prefs.textScale
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
        updateTonight(if (mode == "my") shown else emptyList())
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

    /** "TONIGHT  Cowboys · Sun 3:25 PM": team, day and time only. Display only, never selectable. */
    private fun updateTonight(mine: List<Game>) {
        // Games are already sorted live first, then by start time.
        val g = mine.firstOrNull { it.state != "post" }
        if (g == null || g.start == 0L) {
            tonightView.visibility = View.GONE
            return
        }
        val team = if (g.homeKey in prefs.myTeamKeys) g.homeName else g.awayName
        val whenStr = SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(g.start))
        val sb = SpannableStringBuilder("TONIGHT  ")
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
        val bgKey = prefs.wallpaper + ":" + prefs.team
        if (appliedBackground != bgKey) {
            appliedBackground = bgKey
            bgHolder.removeAllViews()
            val v: View? = when (prefs.wallpaper) {
                "logo" -> ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageResource(R.drawable.galaxy_background)
                }
                "team" -> TeamBgView(this, Themes.team(prefs.team))
                "plain" -> null
                else -> StarfieldView(this)
            }
            if (v != null) bgHolder.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
            centerTitle.visibility = if (prefs.wallpaper == "logo") View.GONE else View.VISIBLE
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
        updateSports()
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
        placeShelf()
        val apps = settings?.apps?.filter { it.pinned } ?: emptyList()
        val sig = prefs.shelf + "#" + apps.joinToString("|") { it.packageName + updater.isInstalled(it.packageName) }
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

    private fun tileParams(): LinearLayout.LayoutParams {
        return if (prefs.shelf == "left") {
            // Shorter tiles in the rail so about four fit on screen; the rail scrolls for the rest.
            LinearLayout.LayoutParams(dp(136), dp(84)).apply { topMargin = dp(4); bottomMargin = dp(4) }
        } else {
            LinearLayout.LayoutParams(dp(136), dp(108)).apply { marginStart = dp(8); marginEnd = dp(8) }
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

    private fun openStore() {
        val i = packageManager.getLaunchIntentForPackage("com.amazon.venezia")
        if (i == null) Toast.makeText(this, "Press Home, then find the Appstore on the Fire TV home screen.", Toast.LENGTH_LONG).show()
        else startActivity(i)
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Galaxy Media")
            .setItems(arrayOf("Settings", "Setup & updates")) { _, which ->
                if (which == 0) startActivity(Intent(this, SettingsActivity::class.java)) else openSetup()
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

    /** Team-color wallpaper: team color fill, big faint team name, bottom stripe. No logos. */
    private class TeamBgView(ctx: Context, private val team: Themes.TeamTheme) : View(ctx) {
        private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = team.stripe
            alpha = 40
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
        }
        private val stripePaint = Paint().apply { color = team.stripe }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(team.bg)
            namePaint.textSize = height * 0.26f
            canvas.drawText(team.name.uppercase(), width / 2f, height * 0.58f, namePaint)
            val d = resources.displayMetrics.density
            canvas.drawRect(0f, height - 8f * d, width.toFloat(), height.toFloat(), stripePaint)
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
