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
 * "My Galaxy" settings: name, accent color, sports, my teams, shelf position, background, team colors.
 * Every row is one big button: press it to change the value or cycle to the next one.
 * Changes show on the home screen as soon as you go back.
 */
class SettingsActivity : AppCompatActivity() {

    private class Row(val label: String, val value: String, val rerender: Boolean, val onClick: () -> Unit)

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var titleView: TextView
    private var accent = Color.parseColor("#22D3EE")

    private val bgColor = Color.parseColor("#060A16")
    private val cardColor = Color.parseColor("#0F1C2E")
    private val textColor = Color.parseColor("#EAF6FF")
    private val mutedColor = Color.parseColor("#9FB8C8")

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val sportsModes = listOf("off", "my", "all")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
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
            textSize = 34f
            setTextColor(accent)
            gravity = Gravity.CENTER
        }
        col.addView(titleView)
        col.addView(TextView(this).apply {
            text = "Press a row to change it. Changes show when you go back."
            textSize = 16f
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
        out += Row("Name", prefs.userName.ifBlank { "Not set" }, false) { askName() }
        out += Row("Accent color", Themes.accentName(prefs.accent), true) {
            prefs.accent = Themes.next(Themes.accentKeys, prefs.accent)
        }
        out += Row("Sports", sportsName(prefs.sports), true) {
            prefs.sports = Themes.next(sportsModes, prefs.sports)
        }
        if (prefs.sports == "my") {
            val idx = out.size
            out += Row("My teams", teamsSummary(), false) { pickLeague(idx) }
        }
        out += Row("Shelf position", if (prefs.shelf == "left") "Left rail" else "Bottom row", true) {
            prefs.shelf = if (prefs.shelf == "left") "bottom" else "left"
        }
        out += Row("Background", Themes.wallpaperName(prefs.wallpaper), true) {
            prefs.wallpaper = Themes.next(Themes.wallpaperKeys, prefs.wallpaper)
        }
        if (prefs.wallpaper == "team") {
            out += Row("Team colors", Themes.team(prefs.team).name, true) {
                prefs.team = Themes.next(Themes.teams.map { it.key }, prefs.team)
            }
        }
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
            val lp = LinearLayout.LayoutParams(MATCH, dp(64)).apply { bottomMargin = dp(12) }
            list.addView(makeRow(r, i), lp)
        }
        val idx = focusIndex.coerceIn(0, list.childCount - 1)
        list.post { list.getChildAt(idx)?.requestFocus() }
    }

    private fun makeRow(r: Row, index: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(10))
            isFocusable = true
            isClickable = true
        }
        val label = TextView(this).apply { text = r.label; textSize = 20f; setTextColor(textColor) }
        val value = TextView(this).apply { text = r.value; textSize = 20f; setTextColor(accent); gravity = Gravity.END }
        box.addView(label, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(value, LinearLayout.LayoutParams(WRAP, WRAP))
        style(box, 14f, listOf(label to textColor, value to accent))
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

    // ── my teams: pick a league, then tick the teams you follow ──

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
}
