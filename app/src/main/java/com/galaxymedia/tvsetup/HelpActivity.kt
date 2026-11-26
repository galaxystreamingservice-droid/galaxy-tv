package com.galaxymedia.tvsetup

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * "Fix it" help cards: plain-language steps for the common problems.
 * Nothing here changes anything by itself: each card says what to do and can open the right screen.
 * A step with a how-to video also shows a QR code, to watch on a phone while doing it on the TV (see HelpVideos).
 * Guided maintenance (the monthly upkeep) lives here too, at the bottom of the list.
 */
class HelpActivity : AppCompatActivity() {

    /** action: "" (none), "wifi", "apps", "settings", "setup" or "maintenance". video: the HelpVideos key ("" = none). */
    private class Step(val title: String, val where: String, val todo: List<String>, val note: String, val action: String = "", val video: String = "")
    private class Topic(val name: String, val steps: List<Step>)

    private val topics = listOf(
        Topic("Buffering or freezing", listOf(
            Step(
                "Check the Wi-Fi", "Settings › Network",
                listOf("Pick your Wi-Fi and look at the signal", "If it is weak, move the router closer or restart it (unplug 30 seconds)"),
                "Weak Wi-Fi is the most common cause. If every channel buffers, it is the Wi-Fi.",
                "wifi", "wifi"
            ),
            Step(
                "Try the VPN", "WorldVPN app",
                listOf("If the VPN is on, turn it off and try again", "If it is off, try turning it on or picking another server"),
                "If only one channel buffers, the channel itself is having a problem. Try a different one.",
                "", "vpn"
            ),
            Step(
                "Restart the stick", "Settings › My Fire TV",
                listOf("Restart", "Wait for the home screen, then open the app again"),
                "A restart clears a lot of small problems.",
                "settings", "restart_stick"
            )
        )),
        Topic("No sound", listOf(
            Step(
                "Check the volume", "Remote and TV",
                listOf("Press volume up on the remote", "Make sure the TV is not muted", "Make sure the TV is on the right HDMI input"),
                "The TV, a soundbar or the remote can each have their own volume."
            ),
            Step(
                "Check the audio setting", "Settings › Display & Sounds › Audio",
                listOf("Surround Sound: set to Stereo", "Then try the app again"),
                "Some TVs and soundbars cannot play surround sound and go silent.",
                "settings", "audio"
            ),
            Step(
                "Check the cable, then restart", "Back of the TV",
                listOf("Unplug the stick's HDMI and plug it back in", "Settings › My Fire TV › Restart"),
                "A loose HDMI connection can cut the sound.",
                "", "restart_stick"
            )
        )),
        Topic("App won't open", listOf(
            Step(
                "Close it and try again", "Remote",
                listOf("Press Home", "Open the app again"),
                "Sometimes an app is just stuck.",
                "", "restart_app"
            ),
            Step(
                "Clear the app's cache", "Settings › Applications › Manage Installed Applications › (app) › Clear cache",
                listOf("Press Clear cache only"),
                "Never press Clear data. That logs you out of the app.",
                "apps", "clear_cache"
            ),
            Step(
                "Check for an update", "Galaxy Media › Setup & updates",
                listOf("Install any update that is waiting", "Then open the app again"),
                "An old version of an app can stop working.",
                "setup", "update_apps"
            ),
            Step(
                "Restart the stick", "Settings › My Fire TV",
                listOf("Restart", "If it still won't open, ask the person who set up your stick"),
                "This fixes most apps that still won't start.",
                "settings", "restart_stick"
            )
        )),
        Topic("An app keeps glitching", listOf(
            Step(
                "Restart the app", "Settings › Applications › Manage Installed Applications › (app)",
                listOf("Press Force stop", "Press Home, then open the app again"),
                "Force stop only closes the app. Nothing is deleted and you stay signed in.",
                "apps", "restart_app"
            ),
            Step(
                "Clear the app's cache", "Settings › Applications › Manage Installed Applications › (app)",
                listOf("Press Clear cache", "Open the app again"),
                "Clear cache only removes temporary files. You stay signed in.",
                "apps", "clear_cache"
            ),
            Step(
                "Clear the app's data (you'll need to sign in again)", "Settings › Applications › Manage Installed Applications › (app)",
                listOf("Have the app's username and password ready", "Press Clear data, then open the app and sign in"),
                "Only do this if clearing the cache did not help. Clear data resets the app like new and signs you out.",
                "apps", "clear_data"
            ),
            Step(
                "Uninstall the app", "Settings › Applications › Manage Installed Applications › (app)",
                listOf("Press Uninstall", "Then go to the next step to put it back"),
                "This removes the app and its settings. You will sign in again after reinstalling.",
                "apps", "uninstall"
            ),
            Step(
                "Install it again", "Galaxy Media › Setup & updates",
                listOf("Press Install / update apps", "Wait for it to finish, then open the app and sign in"),
                "Setup & updates puts back the right version of every app on your list.",
                "setup", "reinstall"
            )
        )),
        Topic("Galaxy Media isn't first on the home screen", listOf(
            Step(
                "Move Galaxy Media to the front", "Fire TV home › Apps (the three squares) › Galaxy Media",
                listOf("Highlight Galaxy Media (don't open it)", "Press the Menu button (≡) on the remote", "Choose Move to front"),
                "This often resets after the Fire TV is signed out of Amazon or set up again. Do it once and it stays.",
                "", "app_front"
            )
        )),
        Topic("Stick is slow or storage is full", listOf(
            Step(
                "Run guided maintenance", "Galaxy Media › Fix it › Guided maintenance",
                listOf("Free up space", "Clear the big apps' caches", "Update and restart"),
                "About 5 minutes. A full stick is a slow stick.",
                "maintenance", "storage"
            ),
            Step(
                "Restart the stick", "Settings › My Fire TV",
                listOf("Restart", "Wait for the home screen"),
                "A restart every week or two keeps the stick quick.",
                "settings", "restart_stick"
            )
        ))
    )

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private var topic = -1
    private var step = 0
    private var accent = Color.parseColor("#22D3EE")
    private var bgColor = Color.parseColor("#060A16")
    private var cardColor = Color.parseColor("#0F1C2E")
    private var textColor = Color.parseColor("#EAF6FF")
    private var mutedColor = Color.parseColor("#9FB8C8")

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        accent = Themes.accentColor(prefs.accent)
        if (prefs.highContrast) {
            bgColor = Color.BLACK; cardColor = Color.BLACK
            textColor = Color.WHITE; mutedColor = Color.WHITE
        } else {
            bgColor = Gold.BG; cardColor = Color.parseColor("#0B0907")
            textColor = Gold.IVORY; mutedColor = Gold.MUTED; accent = Gold.GOLD
        }
        val root = FrameLayout(this).apply { setBackgroundColor(bgColor) }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(56), dp(36), dp(56), dp(36))
            clipChildren = false
            clipToPadding = false
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(body, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        render()
        HelpVideos.load(this) { render() }
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView =
        TextView(this).apply {
            text = t
            textSize = size * prefs.textScale
            setTextColor(color)
            if (Gold.on(prefs)) typeface = if (bold) Gold.caps(this@HelpActivity) else Gold.serif(this@HelpActivity)
            else if (bold) setTypeface(null, Typeface.BOLD)
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

    private fun render() {
        body.removeAllViews()
        if (topic < 0) renderMenu() else renderStep()
    }

    private fun renderMenu() {
        body.addView(text("FIX IT", 16f, accent, bold = true).apply { letterSpacing = 0.15f; gravity = Gravity.CENTER })
        body.addView(text("What's wrong?", 34f, textColor, top = 6).apply { gravity = Gravity.CENTER })
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(24), dp(12), dp(12))
        }
        var first: View? = null
        topics.forEachIndexed { i, t ->
            val b = button(t.name) { topic = i; step = 0; render() }
            if (first == null) first = b
            list.addView(b, LinearLayout.LayoutParams(dp(560), WRAP).apply { bottomMargin = dp(14) })
        }
        list.addView(button("Guided maintenance (monthly upkeep)") {
            startActivity(Intent(this, CleanupActivity::class.java))
        }, LinearLayout.LayoutParams(dp(560), WRAP).apply { bottomMargin = dp(14) })
        list.addView(button("Done") { finish() }, LinearLayout.LayoutParams(dp(560), WRAP))
        body.addView(list, LinearLayout.LayoutParams(WRAP, WRAP))
        val f = first
        f?.post { f.requestFocus() }
    }

    private fun renderStep() {
        val t = topics[topic]
        val s = t.steps[step]
        val last = step == t.steps.size - 1
        body.addView(text("${t.name.uppercase()} · STEP ${step + 1} OF ${t.steps.size}", 16f, accent, bold = true).apply {
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
        })
        body.addView(text(s.title, 34f, textColor, top = 6).apply { gravity = Gravity.CENTER })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(24), dp(30), dp(24))
            background = cardBg()
        }
        card.addView(text("Where:", 16f, mutedColor))
        card.addView(text(s.where, 21f, textColor, top = 4))
        card.addView(text("Then do this:", 16f, mutedColor, top = 18))
        s.todo.forEach { card.addView(text("→ $it", 22f, accent, bold = true, top = 6)) }
        card.addView(text(s.note, 16f, mutedColor, top = 18))
        val qr = HelpVideos.panel(this, s.video, prefs.textScale, mutedColor, dp(150))
        if (qr == null) body.addView(card, LinearLayout.LayoutParams(dp(760), WRAP).apply { topMargin = dp(20) })
        else {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(card, LinearLayout.LayoutParams(dp(600), WRAP))
            row.addView(qr, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(28) })
            body.addView(row, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(20) })
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(24), dp(8), dp(8))
        }
        var focus: View? = null
        val label = when (s.action) {
            "wifi" -> "Open Wi-Fi settings"
            "apps" -> "Open apps list"
            "setup" -> "Open Setup & updates"
            "maintenance" -> "Start guided maintenance"
            "settings" -> "Open Fire TV Settings"
            else -> ""
        }
        if (label.isNotEmpty()) {
            val b = button(label) { runAction(s.action) }
            buttons.addView(b, buttonParams())
            focus = b
        }
        val back = button(if (step > 0) "Back" else "All topics") {
            if (step > 0) step-- else topic = -1
            render()
        }
        buttons.addView(back, buttonParams())
        val next = button(if (last) "All fixed" else "Next") {
            if (last) { topic = -1; render() } else { step++; render() }
        }
        buttons.addView(next, buttonParams())
        if (focus == null) focus = next
        body.addView(buttons, LinearLayout.LayoutParams(WRAP, WRAP))
        val f = focus
        f?.post { f.requestFocus() }
    }

    private fun runAction(a: String) {
        when (a) {
            "wifi" -> SystemSettings.open(this, SystemSettings.Target.WIFI, null, "Settings › Network")
            "apps" -> SystemSettings.open(this, SystemSettings.Target.APPS, null, "Settings › Applications › Manage Installed Applications")
            "setup" -> startActivity(Intent(this, MainActivity::class.java))
            "maintenance" -> startActivity(Intent(this, CleanupActivity::class.java))
            else -> SystemSettings.open(this, SystemSettings.Target.MAIN, null, "Settings")
        }
    }

    // ───────────────────────── buttons + look ─────────────────────────

    private fun buttonParams() = LinearLayout.LayoutParams(WRAP, WRAP).apply {
        marginStart = dp(8); marginEnd = dp(8)
    }

    private fun button(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this).apply {
            text = label
            textSize = 20f * prefs.textScale
            setTextColor(textColor)
            if (Gold.on(prefs)) typeface = Gold.serif(this@HelpActivity)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(14), dp(24), dp(14))
            isFocusable = true
            isClickable = true
        }
        t.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), ringBg(14f))
            addState(intArrayOf(android.R.attr.state_pressed), ringBg(14f))
            addState(intArrayOf(), cardBg(14f))
        }
        t.setOnFocusChangeListener { v, has ->
            t.setTextColor(if (has) (if (Gold.on(prefs)) Gold.GOLD_HI else Color.BLACK) else textColor)
            if (!Gold.on(prefs)) t.setTypeface(null, if (has) Typeface.BOLD else Typeface.NORMAL)
            val s = if (has) 1.04f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        t.setOnClickListener { onClick() }
        return t
    }

    private fun cardBg(radiusDp: Float = 18f): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpf(radiusDp)
        setColor(cardColor)
        if (prefs.highContrast) setStroke(dp(2), Color.WHITE) else setStroke(dp(1), (Gold.GOLD and 0x00FFFFFF) or 0x55000000)
    }

    private fun ringBg(radiusDp: Float): Drawable {
        if (Gold.on(prefs)) return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(radiusDp)
            setColor(Color.parseColor("#1A150C"))
            setStroke(dp(3), Gold.GOLD_HI)
        }
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
