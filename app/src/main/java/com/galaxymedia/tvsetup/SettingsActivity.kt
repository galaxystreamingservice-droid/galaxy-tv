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
        render(0)
    }

    private fun buildShell(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(bgColor) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(56), dp(36), dp(56), dp(36))
        }
        titleView = TextView(this).apply {
            text = "Settings"
            textSize = 34f * prefs.textScale
            setTextColor(accent)
            gravity = Gravity.CENTER
        }
        col.addView(titleView)
        col.addView(TextView(this).apply {
            text = "Press a row to change it. Press Back when you are done to see the change."
            textSize = 16f * prefs.textScale
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(18))
        })
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
        col.addView(scroll, LinearLayout.LayoutParams(dp(640), 0, 1f))
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
        out += Row("Holiday touches on apps", if (prefs.tileDeco) "On" else "Off", true) { prefs.tileDeco = !prefs.tileDeco }
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
        out += Row("Guided maintenance", "Start", false) { startActivity(Intent(this, CleanupActivity::class.java)) }
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
        out += Row("Setup & updates", "Open", false) { startActivity(Intent(this, MainActivity::class.java)) }
        out += Row("Done", "Back to home", false) { finish() }
        return out
    }

    private fun render(focusIndex: Int) {
        accent = Themes.accentColor(prefs.accent)
        titleView.setTextColor(accent)
        list.removeAllViews()
        val rs = rows()
        rs.forEachIndexed { i, r ->
            val lp = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }   // tall enough for the explanation line
            list.addView(makeRow(r, i), lp)
        }
        val idx = focusIndex.coerceIn(0, list.childCount - 1)
        list.post { list.getChildAt(idx)?.requestFocus() }
    }

    /** One short, plain sentence under a row, so nobody has to guess what it does. */
    private val hints = mapOf(
        "Your name" to "Shown in the greeting on the home screen.",
        "Highlight color" to "The color of the glow around whatever you select.",
        "Sports scores" to "Live scores along the bottom of the screen.",
        "My teams" to "Pick your teams so their games show first.",
        "Where the apps go" to "Where your app icons sit on the home screen.",
        "Hidden apps" to "Apps you do not want to see on the home screen.",
        "Renewal reminder" to "The date your service renews. A reminder shows on the home screen from a month before until a month after.",
        "App icons" to "Galaxy style is our matching set. Original is the icon each app comes with.",
        "Holiday touches on apps" to "On the Seasonal background, a holiday decoration sits on the corner of the last app, like a Santa hat at Christmas.",
        "Background" to "The picture behind everything. Seasonal changes with the holidays.",
        "Preview season" to "Try the other holiday pictures. It returns to today's picture after 30 minutes.",
        "Background movement" to "How much moves in the background. Choose Still if the picture stutters.",
        "Screen effects" to "Automatic gives older sticks fewer effects so they stay smooth. Off stops all movement.",
        "Clean up when I return home" to "When you come back from another app, apps left running are closed so the stick stays fast. Your VPN stays on.",
        "Hide extras when idle" to "After 30 seconds with no button pressed, the buttons and scores fade so the picture is clean. Press any button to bring them back.",
        "Dim at night" to "Lowers the brightness from the hour you pick until 6 AM, so the screen is not harsh in a dark room.",
        "Weather" to "Shows the temperature under the clock. Enter your ZIP code.",
        "Screensaver" to "After about 5 minutes with nothing pressed, the screen shows a dimmed picture and a slow clock.",
        "Start with the TV" to "Opens Galaxy Media by itself when the stick turns on.",
        "Guided maintenance" to "Step-by-step tips to keep the stick fast.",
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
        val label = TextView(this).apply { text = r.label; textSize = 20f * prefs.textScale; setTextColor(textColor) }
        val value = TextView(this).apply { text = r.value; textSize = 20f * prefs.textScale; setTextColor(accent); gravity = Gravity.END }
        val hint = hints[r.label]
        val labelCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labelCol.addView(label)
        val hintTv = if (hint == null) null else TextView(this).apply {
            text = hint
            textSize = 13f * prefs.textScale
            setTextColor(mutedColor)
            setPadding(0, dp(2), dp(16), 0)
        }
        if (hintTv != null) labelCol.addView(hintTv)
        box.addView(labelCol, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(value, LinearLayout.LayoutParams(WRAP, WRAP))
        val styled = mutableListOf(label to textColor, value to accent)
        if (hintTv != null) styled += hintTv to mutedColor
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
                t.setTextColor(if (has) Color.BLACK else c)
                t.setTypeface(null, if (has) Typeface.BOLD else Typeface.NORMAL)
            }
            val s = if (has) 1.03f else 1f
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
}
