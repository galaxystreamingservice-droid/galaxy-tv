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
 * Guided cleanup: step cards that tell you exactly where to go in Fire TV's own Settings.
 * We can't draw over Amazon's screens, so each card names the path and the switches to turn off.
 * This only reduces ads and tracking. It does not disable any apps.
 */
class CleanupActivity : AppCompatActivity() {

    private class Step(val title: String, val path: String, val todo: List<String>, val note: String)

    private val steps = listOf(
        Step(
            "Turn off video autoplay",
            "Settings › Preferences › Featured Content",
            listOf("Allow Video Autoplay: OFF", "Allow Audio Autoplay: OFF"),
            "Stops the loud auto-playing ads on the Fire TV home screen."
        ),
        Step(
            "Stop usage tracking",
            "Settings › Preferences › Privacy Settings",
            listOf("Device Usage Data: OFF", "Collect App Usage Data: OFF"),
            "Amazon stops collecting how this device and your apps are used."
        ),
        Step(
            "Turn off interest-based ads",
            "Settings › Preferences › Privacy Settings",
            listOf("Interest-based Ads: OFF"),
            "It is near the bottom of the Privacy Settings list. Your apps and channels are not touched."
        )
    )

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
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
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView =
        TextView(this).apply {
            text = t
            textSize = size * prefs.textScale
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

    private fun render() {
        body.removeAllViews()
        val s = steps[step]
        val last = step == steps.size - 1

        body.addView(text("CLEANUP · STEP ${step + 1} OF ${steps.size}", 16f, accent, bold = true).apply {
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
        })
        body.addView(text(s.title, 34f, textColor, top = 6).apply { gravity = Gravity.CENTER })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(24), dp(30), dp(24))
            background = cardBg()
        }
        card.addView(text("On the Fire TV, open Settings and go to:", 16f, mutedColor))
        card.addView(text(s.path, 21f, textColor, top = 4))
        card.addView(text("Then switch these off:", 16f, mutedColor, top = 18))
        s.todo.forEach { card.addView(text("→  $it", 22f, accent, bold = true, top = 6)) }
        card.addView(text(s.note, 16f, mutedColor, top = 18))
        body.addView(card, LinearLayout.LayoutParams(dp(760), WRAP).apply { topMargin = dp(20) })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(24), dp(8), dp(8))
        }
        val first = button("Open Fire TV Settings") { openFireSettings() }
        buttons.addView(first, buttonParams())
        if (step > 0) buttons.addView(button("Back") { step--; render() }, buttonParams())
        buttons.addView(button(if (last) "Done" else "Next") {
            if (last) finishCleanup() else { step++; render() }
        }, buttonParams())
        if (step == 0) buttons.addView(button("Remind me in a week") {
            prefs.cleanupSnoozeUntil = System.currentTimeMillis() + 7 * 86_400_000L
            Toast.makeText(this, "OK, I'll remind you in a week.", Toast.LENGTH_LONG).show()
            finish()
        }, buttonParams())
        body.addView(buttons, LinearLayout.LayoutParams(WRAP, WRAP))
        first.post { first.requestFocus() }
    }

    private fun openFireSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Press Home, then open Settings (the gear) on the Fire TV home screen.", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishCleanup() {
        prefs.lastCleanup = System.currentTimeMillis()
        prefs.cleanupSnoozeUntil = 0L
        Toast.makeText(this, "Cleanup done. Thanks!", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun buttonParams() = LinearLayout.LayoutParams(WRAP, WRAP).apply {
        marginStart = dp(8); marginEnd = dp(8)
    }

    private fun button(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this).apply {
            text = label
            textSize = 18f * prefs.textScale
            setTextColor(textColor)
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
            t.setTextColor(if (has) Color.BLACK else textColor)
            t.setTypeface(null, if (has) Typeface.BOLD else Typeface.NORMAL)
            val s = if (has) 1.06f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        t.setOnClickListener { onClick() }
        return t
    }

    private fun cardBg(radiusDp: Float = 18f): Drawable = GradientDrawable().apply {
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
