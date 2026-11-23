package com.galaxymedia.tvsetup

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * "My Galaxy" settings: name, accent color, sports, my teams, shelf position, background, team colors,
 * quiet mode, night dimming, background motion.
 * Every row is one big button: press it to change the value or cycle to the next one.
 * Changes show on the home screen as soon as you go back.
 */
class SettingsActivity : AppCompatActivity() {

    private class Row(val label: String, val value: String, val rerender: Boolean, val onClick: () -> Unit)

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var titleView: TextView
    private var accent = Color.parseColor("#22D3EE")

    private var bgColor = Color.parseColor("#060A16")
    private var cardColor = Color.parseColor("#0F1C2E")
    private var textColor = Color.parseColor("#EAF6FF")
    private var mutedColor = Color.parseColor("#9FB8C8")

    // v0.14 premium look: black, champagne gold, ivory, the bundled serif. High contrast keeps the plain look.
    private val premium: Boolean get() = !prefs.highContrast
    private val gold = Color.parseColor("#D6B87A")
    private val goldHi = Color.parseColor("#F0DAA8")
    private val ivory = Color.parseColor("#F4EEE2")
    private val serifFont by lazy { font("Cardo-Regular") }
    private val capsFont by lazy { font("Montserrat-Medium") }
    private fun font(name: String): Typeface = try { Typeface.createFromAsset(assets, "fonts/$name.ttf") } catch (e: Exception) { Typeface.DEFAULT }

    /** Four tabs (v0.14). Owner tools appear after holding OK on the version line at the bottom. */
    private val tabs = mutableListOf("Me", "Look", "Easy to see", "Stick")
    private var tab = "Me"
    private var ownerOn = false
    private lateinit var tabBar: LinearLayout
    private fun tabOf(label: String): String = when (label) {
        "Your name", "Contact", "Weather", "Sports scores", "My teams", "Renewal reminder" -> "Me"
        "Background", "Holiday touches", "Highlight color", "Screen effects", "Background movement", "Screen edge",
        "Change every", "Light color", "Screensaver" -> "Look"
        "Text size", "High contrast" -> "Easy to see"
        "Start with the TV", "Clean up when I return home", "Maintenance reminder", "Setup & updates", "Reset my look" -> "Stick"
        else -> "Owner"     // Hidden apps, Preview season, Watch all season scenes, Season test, Dim at night, Hide extras when idle
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val sportsModes = listOf("off", "my", "all")
        private val textSizes = listOf("normal", "large", "xlarge")
        private val nightHours = listOf(-1, 20, 21, 22, 23)
        private val motionModes = listOf("off", "reduced", "normal")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (prefs.highContrast) {
            bgColor = Color.BLACK; cardColor = Color.BLACK
            textColor = Color.WHITE; mutedColor = Color.WHITE
        }
        setContentView(buildShell())
        renderTabs()
        render(0)
    }

    private fun buildShell(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(bgColor) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(56), dp(36), dp(56), dp(36))
        }
        if (premium) root.setBackgroundColor(Color.parseColor("#050403"))
        titleView = TextView(this).apply {
            text = "Settings"
            textSize = (if (premium) 40f else 34f) * prefs.textScale
            setTextColor(accent)
            gravity = Gravity.CENTER
            if (premium) typeface = serifFont
        }
        col.addView(titleView)
        col.addView(TextView(this).apply {
            text = "Press a row to change it. Press Back when you are done."
            textSize = 15f * prefs.textScale
            setTextColor(if (premium) Color.argb(170, 244, 238, 226) else mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        })
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
        col.addView(tabBar, LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(10) })
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        col.addView(scroll, LinearLayout.LayoutParams(dp(if (premium) 760 else 640), 0, 1f))
        // The version line. Holding OK on it opens the owner tools (hidden apps, season tests, preview).
        col.addView(TextView(this).apply {
            text = "Galaxy Media " + try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
            textSize = 12f * prefs.textScale
            letterSpacing = 0.14f
            setTextColor(if (premium) Color.argb(150, 214, 184, 122) else mutedColor)
            if (premium) typeface = capsFont
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(6), dp(16), dp(6))
            isFocusable = true
            isLongClickable = true
            setOnFocusChangeListener { v, has -> v.alpha = if (has) 1f else 0.7f }
            setOnLongClickListener {
                ownerOn = !ownerOn
                if (ownerOn && "Owner" !in tabs) tabs += "Owner"
                if (!ownerOn) { tabs.remove("Owner"); if (tab == "Owner") tab = "Me" }
                Toast.makeText(this@SettingsActivity, if (ownerOn) "Owner tools on" else "Owner tools off", Toast.LENGTH_SHORT).show()
                renderTabs(); render(0, false)
                true
            }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
    }

    private fun sportsName(mode: String): String = when (mode) {
        "off" -> "Off"
        "my" -> "My teams"
        else -> "All games today"
    }

    private fun cleanupName(k: String): String = when (k) {
        "weekly" -> "Weekly"
        "monthly" -> "Monthly"
        else -> "Off"
    }

    private fun textName(k: String): String = when (k) {
        "large" -> "Large"
        "xlarge" -> "Extra large"
        else -> "Normal"
    }

    private fun nightName(h: Int): String = when {
        h < 0 -> "Off"
        h == 12 -> "From 12 PM"
        h > 12 -> "From ${h - 12} PM"
        else -> "From $h AM"
    }

    private fun motionName(k: String): String = when (k) {
        "normal" -> "Normal"
        "reduced" -> "Slow"
        else -> "Still"
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset my look?")
            .setMessage("Puts accent, background, sports, shelf, text size, contrast, quiet mode, night dimming and motion back to the defaults. Your name and teams stay.")
            .setPositiveButton("Reset") { _, _ ->
                prefs.resetLook()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun teamsSummary(): String {
        val names = prefs.myTeams.map { it.substringAfter('|') }.sorted()
        return when {
            names.isEmpty() -> "None picked"
            names.size <= 2 -> names.joinToString(", ")
            else -> "${names.size} teams"
        }
    }

    private fun rows(): List<Row> {
        val out = mutableListOf<Row>()
        out += Row("Your name", prefs.userName.ifBlank { "Not set" }, false) { askName() }
        out += Row("Contact", "Message Galaxy Media", false) { startActivity(android.content.Intent(this, ContactActivity::class.java)) }
        val accIdx = out.size
        out += Row("Highlight color", Themes.accentName(prefs.accent), false) { pickAccent(accIdx) }
        out += Row("Sports scores", sportsName(prefs.sports), true) {
            prefs.sports = Themes.next(sportsModes, prefs.sports)
        }
        if (prefs.sports == "my") {
            val idx = out.size
            out += Row("My teams", teamsSummary(), false) { pickLeague(idx) }
        }
        val hideIdx = out.size
        out += Row("Hidden apps", if (prefs.hiddenApps.isEmpty()) "None" else "${prefs.hiddenApps.size} hidden", false) { pickHidden(hideIdx) }
        val remIdx = out.size
        out += Row("Renewal reminder", ReminderPicker.parse(prefs.reminderDate)?.let { ReminderPicker.nice(it) } ?: "Not set", false) {
            ReminderPicker.show(this, prefs, false) { render(remIdx) }
        }
        out += Row("Holiday touches", if (prefs.tileDeco) "On" else "Off", true) { prefs.tileDeco = !prefs.tileDeco }
        val bgIdx = out.size
        out += Row("Background", Themes.wallpaperName(Themes.resolve(prefs.wallpaper, Themes.liteFor(this, prefs.animations), prefs.rotateMin)), false) { pickBackground(bgIdx) }
        val lite = Themes.liteFor(this, prefs.animations)
        val wp = Themes.resolve(prefs.wallpaper, lite, prefs.rotateMin)
        if (wp == "season") {
            val pv = prefs.previewScene
            val pvName = if (pv.isEmpty()) "Today's picture" else (Seasons.info(this, pv)?.optString("name", pv) ?: pv) + " (for 30 min)"
            out += Row("Preview season", pvName, true) {
                prefs.previewScene = Themes.next(Themes.previewScenes, prefs.previewScene)
            }
        }
        if (wp == "trace" || Themes.isPicture(wp)) {
            out += Row("Screen edge", if (prefs.edge == "wide") "Wider" else "Normal", true) {
                prefs.edge = if (prefs.edge == "wide") "normal" else "wide"
            }
        }
        if (wp == "rotate") {
            out += Row("Change every", "${prefs.rotateMin} min", true) {
                val i = Themes.rotateOptions.indexOf(prefs.rotateMin)
                prefs.rotateMin = Themes.rotateOptions[(i + 1) % Themes.rotateOptions.size]
            }
        }
        if (wp == "rotate" || (!lite && (wp == "trace" || Themes.isPicture(wp)))) {
            out += Row("Light color", Themes.beamName(prefs.beam), true) {
                prefs.beam = Themes.next(Themes.beamKeys, prefs.beam)
            }
        }
        if (wp == "stars" || wp == "rotate" || (!lite && (wp == "trace" || wp == "aurora" || Themes.isPicture(wp) || Themes.isSeason(wp)))) {
            out += Row("Background movement", motionName(prefs.motion), true) {
                prefs.motion = Themes.next(motionModes, prefs.motion)
            }
        }
        out += Row("Screen effects", if (prefs.animations == "auto") "Automatic (" + (if (Themes.isLite(this)) "fewer effects" else "full effects") + ")" else Themes.animationsName(prefs.animations), true) {
            prefs.animations = Themes.next(Themes.animationKeys, prefs.animations)
        }
        out += Row("Clean up when I return home", if (prefs.closeApps == "auto") "Automatic (" + (if (AppCloser.enabled(this, "auto")) "on" else "off") + ")" else if (prefs.closeApps == "on") "On" else "Off", true) {
            prefs.closeApps = Themes.next(listOf("auto", "on", "off"), prefs.closeApps)
        }
        out += Row("Hide extras when idle", if (prefs.quietMode) "On" else "Off", true) {
            prefs.quietMode = !prefs.quietMode
        }
        out += Row("Dim at night", nightName(prefs.nightStart), true) {
            val i = nightHours.indexOf(prefs.nightStart)
            prefs.nightStart = nightHours[(i + 1) % nightHours.size]
        }
        val weatherIdx = out.size
        out += Row("Weather", prefs.weatherName.ifBlank { "Off" }, false) { askWeather(weatherIdx) }
        out += Row("Screensaver", if (prefs.screensaver) "On" else "Off", true) {
            prefs.screensaver = !prefs.screensaver
        }
        out += Row("Start with the TV", if (prefs.autoStart) "On" else "Off", true) {
            prefs.autoStart = !prefs.autoStart
        }
        out += Row("Maintenance reminder", cleanupName(prefs.cleanupEvery), true) {
            prefs.cleanupEvery = Themes.next(listOf("off", "weekly", "monthly"), prefs.cleanupEvery)
        }
        out += Row("Text size", textName(prefs.textSize), false) {
            prefs.textSize = Themes.next(textSizes, prefs.textSize)
            recreate()
        }
        out += Row("High contrast", if (prefs.highContrast) "On" else "Off", false) {
            prefs.highContrast = !prefs.highContrast
            recreate()
        }
        out += Row("Reset my look", "Press to reset", false) { confirmReset() }
        out += Row("Watch all season scenes", "Play", false) { startActivity(Intent(this, SeasonPreviewActivity::class.java)) }   // TEMPORARY
        out += Row("Season test", if (StoryTest.active(prefs)) "On, ${((prefs.storyTestUntil - System.currentTimeMillis()) / 60_000L) + 1} min left" else "Off", true) {   // TEMPORARY
            prefs.storyTestUntil = if (StoryTest.active(prefs)) 0L else System.currentTimeMillis() + StoryTest.LENGTH_MS
        }
        out += Row("Setup & updates", "Open", false) { startActivity(Intent(this, MainActivity::class.java)) }
        return out
    }

    /** focusIndex is the row's place in the full list (the pickers remember it); only the current tab's rows are shown. */
    private fun render(focusIndex: Int, takeFocus: Boolean = true) {
        accent = Themes.accentColor(prefs.accent)
        titleView.setTextColor(if (premium) ivory else accent)
        list.removeAllViews()
        val all = rows()
        val target = all.getOrNull(focusIndex)
        val shown = all.withIndex().filter { tabOf(it.value.label) == tab }
        shown.forEach { (i, r) ->
            val lp = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(if (premium) 6 else 12) }
            list.addView(makeRow(r, i), lp)
        }
        if (!takeFocus) return
        val idx = shown.indexOfFirst { it.value === target }.coerceAtLeast(0).coerceAtMost(list.childCount - 1)
        list.post { list.getChildAt(idx)?.requestFocus() }
    }

    /** The tab names across the top. Moving onto a tab shows its rows; Down goes into them. */
    private fun renderTabs() {
        tabBar.removeAllViews()
        for (t in tabs) {
            val tv = TextView(this).apply {
                text = t
                textSize = (if (premium) 22f else 18f) * prefs.textScale
                if (premium) typeface = serifFont
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(8), dp(22), dp(10))
                isFocusable = true
                isClickable = true
            }
            fun paint(has: Boolean) {
                val on = t == tab
                tv.setTextColor(if (has) (if (premium) goldHi else Color.BLACK) else if (on) (if (premium) gold else accent) else (if (premium) Color.argb(150, 244, 238, 226) else mutedColor))
                tv.background = if (has) ringBg(18f) else if (on && premium) GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(18f)
                    setColor(Color.TRANSPARENT); setStroke(dp(1), (gold and 0x00FFFFFF) or 0x66000000.toInt())
                } else null
            }
            paint(false)
            tv.setOnFocusChangeListener { _, has ->
                if (has && tab != t) { tab = t; render(0, false); for (k in 0 until tabBar.childCount) (tabBar.getChildAt(k) as? TextView)?.let { it.onFocusChangeListener?.onFocusChange(it, it.hasFocus()) } }
                paint(has)
            }
            tv.setOnClickListener { list.getChildAt(0)?.requestFocus() }
            tabBar.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) })
        }
    }

    /** One short, plain sentence under a row, so nobody has to guess what it does. */
    private val hints = mapOf(
        "Your name" to "Shown in the greeting on the home screen.",
        "Watch all season scenes" to "Plays every holiday scene back to back. Left and right skip, Back exits.",
        "Season test" to "For 10 minutes the home screen plays every holiday day by day (scene, greeting, countdown and app decoration), 5 seconds each, and shows how hard the stick is working.",
        "Highlight color" to "The color of the glow around whatever you select.",
        "Sports scores" to "Live scores along the bottom of the screen.",
        "My teams" to "Pick your teams so their games show first.",
        "Where the apps go" to "Where your app icons sit on the home screen.",
        "Hidden apps" to "Apps you do not want to see on the home screen.",
        "Renewal reminder" to "The date your service renews. A reminder shows on the home screen from a month before until a month after.",
        "App icons" to "Galaxy style is our matching set. Original is the icon each app comes with.",
        "Holiday touches" to "A holiday decoration on the last app, and on the Sports stadium the holiday itself: snow on Christmas, fireworks on New Year's Eve and the 4th of July.",
        "Contact" to "Questions, problems or a renewal: send Galaxy Media a message.",
        "Background" to "The picture behind everything. Seasonal changes with the holidays.",
        "Preview season" to "Try the other holiday pictures. It returns to today's picture after 30 minutes.",
        "Background movement" to "How much moves in the background. Choose Still if the picture stutters.",
        "Screen effects" to "Automatic gives older sticks fewer effects so they stay smooth. Off stops all movement.",
        "Clean up when I return home" to "When you come back from another app, apps left running are closed so the stick stays fast. Your VPN stays on.",
        "Hide extras when idle" to "After 30 seconds with no button pressed, the buttons and scores fade so the picture is clean. Press any button to bring them back.",
        "Dim at night" to "Owner tool. Galaxy Media's own home screen dims from 10 PM to 6 AM by itself; other apps are never dimmed.",
        "Weather" to "Shows the temperature under the clock. Enter your ZIP code.",
        "Screensaver" to "After about 5 minutes with nothing pressed, the screen shows a dimmed picture and a slow clock.",
        "Start with the TV" to "Opens Galaxy Media by itself when the stick turns on.",
        "Maintenance reminder" to "How often to remind you about those tips.",
        "Text size" to "Makes the writing bigger or smaller.",
        "High contrast" to "Stronger colors and borders, easier to see.",
        "Reset my look" to "Puts colors, background and sizes back to normal.",
        "Setup & updates" to "Install or update the apps and Galaxy Media."
    )

    private fun makeRow(r: Row, index: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            minimumHeight = dp(64)
            isFocusable = true
            isClickable = true
        }
        val valueColor = if (premium) gold else accent
        val label = TextView(this).apply { text = r.label; textSize = (if (premium) 22f else 20f) * prefs.textScale; setTextColor(if (premium) ivory else textColor); if (premium) typeface = serifFont }
        val value = TextView(this).apply {
            text = r.value; textSize = (if (premium) 14f else 20f) * prefs.textScale; setTextColor(valueColor); gravity = Gravity.END
            if (premium) { typeface = capsFont; isAllCaps = true; letterSpacing = 0.12f; maxWidth = dp(300) }
        }
        val hint = hints[r.label]
        val labelCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labelCol.addView(label)
        val hintTv = if (hint == null) null else TextView(this).apply {
            text = hint
            textSize = 13f * prefs.textScale
            setTextColor(if (premium) Color.argb(160, 244, 238, 226) else mutedColor)
            setPadding(0, dp(2), dp(16), 0)
        }
        if (hintTv != null) labelCol.addView(hintTv)
        box.addView(labelCol, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(value, LinearLayout.LayoutParams(WRAP, WRAP))
        val styled = mutableListOf(label to (if (premium) ivory else textColor), value to valueColor)
        if (hintTv != null) styled += hintTv to (if (premium) Color.argb(160, 244, 238, 226) else mutedColor)
        style(box, 14f, styled)
        box.setOnClickListener {
            r.onClick()
            if (r.rerender) render(index)
        }
        return box
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
                prefs.namePrompted = true
                render(0)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── weather city ──

    private fun askWeather(rowIndex: Int) {
        val input = EditText(this).apply {
            hint = "City, e.g. Fort Worth"
            setSingleLine(true)
            setText(prefs.weatherName)
        }
        AlertDialog.Builder(this)
            .setTitle("Weather city")
            .setMessage("Shows the temperature next to your greeting.")
            .setView(input)
            .setPositiveButton("Save") { _, _ -> findCity(input.text.toString().trim(), rowIndex) }
            .setNeutralButton("Turn off") { _, _ ->
                prefs.weatherName = ""
                prefs.weatherLat = ""
                prefs.weatherLon = ""
                render(rowIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun findCity(q: String, rowIndex: Int) {
        if (q.isBlank()) return
        Toast.makeText(this, "Looking up $q...", Toast.LENGTH_SHORT).show()
        Thread {
            val city = try { WeatherRepository.geocode(q) } catch (e: Exception) { null }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (city == null) {
                    Toast.makeText(this, "Couldn't find that city. Check the spelling and the Wi-Fi.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                prefs.weatherName = city.name
                prefs.weatherLat = city.lat
                prefs.weatherLon = city.lon
                render(rowIndex)
            }
        }.start()
    }

    // ── my teams: pick a league, then tick the teams you follow ──

    private fun pickAccent(rowIndex: Int) {
        val keys = Themes.accentKeys
        val names = keys.map { Themes.accentName(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Accent color")
            .setSingleChoiceItems(names, keys.indexOf(prefs.accent).coerceAtLeast(0)) { dialog, which ->
                prefs.accent = keys[which]
                dialog.dismiss()
                render(rowIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickBackground(rowIndex: Int) {
        val keys = Themes.wallpapersFor(Themes.liteFor(this, prefs.animations))
        val names = keys.map { Themes.wallpaperName(it) }.toTypedArray()
        val current = keys.indexOf(prefs.wallpaper).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Background")
            .setSingleChoiceItems(names, current) { dialog, which ->
                prefs.wallpaper = keys[which]
                dialog.dismiss()
                render(rowIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickHidden(rowIndex: Int) {
        val apps = try {
            val raw = prefs.lastSettingsJson
            if (raw.isBlank()) emptyList() else com.galaxymedia.tvsetup.model.DeviceSettings.parse(raw).apps.filter { it.pinned }
        } catch (e: Exception) { emptyList() }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps yet. Open Setup & updates and load your settings first.", Toast.LENGTH_LONG).show()
            return
        }
        val hidden = prefs.hiddenApps
        val checked = BooleanArray(apps.size) { i -> apps[i].packageName in hidden }
        AlertDialog.Builder(this)
            .setTitle("Hide apps from the shelf")
            .setMultiChoiceItems(apps.map { it.name }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Save") { _, _ ->
                val keep = hidden.filter { h -> apps.none { it.packageName == h } }.toMutableSet()
                apps.forEachIndexed { i, a -> if (checked[i]) keep += a.packageName }
                prefs.hiddenApps = keep
                render(rowIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickLeague(rowIndex: Int) {
        val labels = SportsRepository.teamLeagueLabels()
        AlertDialog.Builder(this)
            .setTitle("Pick a league")
            .setItems(labels.toTypedArray()) { _, i -> loadTeams(labels[i], rowIndex) }
            .setNeutralButton("Clear all") { _, _ ->
                prefs.myTeams = emptySet()
                render(rowIndex)
            }
            .setNegativeButton("Close") { _, _ -> render(rowIndex) }
            .show()
    }

    private fun loadTeams(label: String, rowIndex: Int) {
        Toast.makeText(this, "Loading $label teams...", Toast.LENGTH_SHORT).show()
        Thread {
            val teams = try { SportsRepository.fetchTeams(label) } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (teams.isEmpty()) {
                    Toast.makeText(this, "Couldn't load teams. Check the Wi-Fi and try again.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val keys = teams.map { "$label:${it.first}" }
                val current = prefs.myTeamKeys
                val checked = BooleanArray(teams.size) { i -> keys[i] in current }
                AlertDialog.Builder(this)
                    .setTitle("$label teams")
                    .setMultiChoiceItems(teams.map { it.second }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
                    .setPositiveButton("Save") { _, _ ->
                        val keep = prefs.myTeams.filter { !it.startsWith("$label:") }.toMutableSet()
                        teams.forEachIndexed { i, t -> if (checked[i]) keep += "$label:${t.first}|${t.second}" }
                        prefs.myTeams = keep
                        render(rowIndex)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    // ── focus highlight, same as the home screen: solid accent fill, white ring, black outer ring ──

    private fun style(v: View, radiusDp: Float, labels: List<Pair<TextView, Int>>) {
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), ringBg(radiusDp))
            addState(intArrayOf(android.R.attr.state_pressed), ringBg(radiusDp))
            addState(intArrayOf(), normalBg(radiusDp))
        }
        v.setOnFocusChangeListener { view, has ->
            labels.forEach { (t, c) ->
                t.setTextColor(if (has) (if (premium) goldHi else Color.BLACK) else c)
                if (!premium) t.setTypeface(null, if (has) Typeface.BOLD else Typeface.NORMAL)
            }
            val s = if (has) 1.03f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    private fun normalBg(radiusDp: Float): Drawable = if (premium) LayerDrawable(arrayOf<Drawable>(
        // no card: a fine gold hairline under each row
        GradientDrawable().apply { setColor((gold and 0x00FFFFFF) or 0x38000000) },
        GradientDrawable().apply { setColor(Color.parseColor("#050403")) }
    )).apply { setLayerInset(1, 0, 0, 0, dp(1)) } else GradientDrawable().apply {
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
        if (premium) return LayerDrawable(arrayOf<Drawable>(     // selected: a gold frame around dark glass
            layer(radiusDp, goldHi),
            layer(radiusDp - 2f, Color.parseColor("#F0120E09"))
        )).apply { setLayerInset(1, dp(2), dp(2), dp(2), dp(2)) }
        return LayerDrawable(arrayOf<Drawable>(
            layer(radiusDp, Color.BLACK),
            layer(radiusDp - 3f, Color.WHITE),
            layer(radiusDp - 6f, accent)
        )).apply {
            setLayerInset(1, dp(3), dp(3), dp(3), dp(3))
            setLayerInset(2, dp(6), dp(6), dp(6), dp(6))
        }
    }
}
