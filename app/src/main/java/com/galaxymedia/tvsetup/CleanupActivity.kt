package com.galaxymedia.tvsetup

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
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
import com.galaxymedia.tvsetup.model.DeviceSettings
import java.io.File
import java.util.Locale

/**
 * Guided maintenance: step cards for the routine upkeep of a Fire TV.
 * We can't draw over Amazon's screens or clear other apps' caches without special access,
 * so each card shows what to do and opens the right Fire TV screen. The one thing we can do
 * ourselves is clear Galaxy Media's own temp files. Nothing here disables or removes any app.
 */
class CleanupActivity : AppCompatActivity() {

    /** kind: "space" (storage), "cache" (per-app cache) or "info" (plain settings card). */
    private class Step(val kind: String, val title: String, val path: String, val todo: List<String>, val note: String)

    private val steps = listOf(
        Step(
            "space", "Free up space",
            "Settings › Applications › Manage Installed Applications",
            listOf("Uninstall apps nobody uses", "Downloader › Files: delete old .apk files"),
            "A full stick is a slow stick. Aim to keep a couple of GB free."
        ),
        Step(
            "cache", "Clear app caches",
            "Settings › Applications › Manage Installed Applications › (app) › Clear cache",
            emptyList(),
            "Only press Clear cache. Never press Clear data: that logs you out of TiviMate, XC7 and the others."
        ),
        Step(
            "info", "Turn off video autoplay",
            "Settings › Preferences › Featured Content",
            listOf("Allow Video Autoplay: OFF", "Allow Audio Autoplay: OFF"),
            "Stops the loud auto-playing ads on the Fire TV home screen."
        ),
        Step(
            "info", "Privacy settings",
            "Settings › Preferences › Privacy Settings",
            listOf("Device Usage Data: OFF", "Collect App Usage Data: OFF", "Interest-based Ads: OFF"),
            "Interest-based Ads is near the bottom of the list. Your apps and channels are not touched."
        ),
        Step(
            "info", "Update and restart",
            "Settings › My Fire TV",
            listOf("About › Check for Updates", "Restart (do this last)"),
            "Install any system update first, then restart. A restart every week or two keeps the stick quick."
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

    // ───────────────────────── storage + temp files ─────────────────────────

    private fun fmt(b: Long): String =
        if (b >= 1_000_000_000L) String.format(Locale.US, "%.1f GB", b / 1e9)
        else String.format(Locale.US, "%d MB", b / 1_000_000L)

    private fun storageLine(): Pair<String, Boolean> {
        return try {
            val st = StatFs(Environment.getDataDirectory().path)
            val free = st.availableBytes
            val total = st.totalBytes
            val low = free < 1_000_000_000L || free * 100 / total < 12
            Pair("${fmt(free)} free of ${fmt(total)}" + if (low) "  ·  LOW" else "", low)
        } catch (e: Exception) {
            Pair("Couldn't read storage", false)
        }
    }

    private fun tempDirs(): List<File> = listOfNotNull(cacheDir, externalCacheDir)

    private fun dirSize(f: File): Long =
        if (f.isDirectory) (f.listFiles()?.sumOf { dirSize(it) } ?: 0L) else f.length()

    private fun tempSize(): Long = tempDirs().sumOf { d -> d.listFiles()?.sumOf { dirSize(it) } ?: 0L }

    private fun clearTemp(): Long {
        var freed = 0L
        tempDirs().forEach { d ->
            d.listFiles()?.forEach { freed += dirSize(it); it.deleteRecursively() }
        }
        return freed
    }

    private fun installedApps(): List<Pair<String, String>> {
        val raw = prefs.lastSettingsJson
        val s = try { if (raw.isBlank()) null else DeviceSettings.parse(raw) } catch (e: Exception) { null } ?: return emptyList()
        return s.apps.filter { a ->
            try { packageManager.getPackageInfo(a.packageName, 0); true } catch (e: Exception) { false }
        }.map { it.name to it.packageName }
    }

    // ───────────────────────── screen ─────────────────────────

    private fun render() {
        body.removeAllViews()
        val s = steps[step]
        val last = step == steps.size - 1
        var focusTarget: View? = null

        body.addView(text("MAINTENANCE · STEP ${step + 1} OF ${steps.size}", 16f, accent, bold = true).apply {
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
        })
        body.addView(text(s.title, 34f, textColor, top = 6).apply { gravity = Gravity.CENTER })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(24), dp(30), dp(24))
            background = cardBg()
        }

        if (s.kind == "space") {
            val (line, low) = storageLine()
            card.addView(text("Free space on this Fire TV:", 16f, mutedColor))
            card.addView(text(line, 24f, if (low) Color.parseColor("#FFB0B0") else textColor, bold = true, top = 4))
            card.addView(text("Then, if you need more room:", 16f, mutedColor, top = 18))
            card.addView(text(s.path, 19f, textColor, top = 4))
            s.todo.forEach { card.addView(text("→  $it", 21f, accent, bold = true, top = 6)) }
        } else if (s.kind == "cache") {
            card.addView(text("For each app below: open its info page, then press Clear cache.", 16f, mutedColor))
            card.addView(text(s.path, 19f, textColor, top = 4))
            val apps = installedApps()
            if (apps.isEmpty()) {
                card.addView(text("Use the button below to open the apps list, then pick your biggest apps.", 16f, mutedColor, top = 14))
            }
            apps.forEach { (name, pkg) ->
                val b = button("$name: open app info") { openAppInfo(pkg) }
                if (focusTarget == null) focusTarget = b
                card.addView(b, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
            }
        } else {
            card.addView(text("On the Fire TV, open Settings and go to:", 16f, mutedColor))
            card.addView(text(s.path, 21f, textColor, top = 4))
            card.addView(text("Then do this:", 16f, mutedColor, top = 18))
            s.todo.forEach { card.addView(text("→  $it", 22f, accent, bold = true, top = 6)) }
        }
        card.addView(text(s.note, 16f, mutedColor, top = 18))
        body.addView(card, LinearLayout.LayoutParams(dp(760), WRAP).apply { topMargin = dp(20) })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(24), dp(8), dp(8))
        }
        if (s.kind == "space") {
            val size = tempSize()
            val b = button("Clear Galaxy Media temp files (${fmt(size)})") {
                val freed = clearTemp()
                Toast.makeText(this, "Cleared ${fmt(freed)}.", Toast.LENGTH_SHORT).show()
                render()
            }
            buttons.addView(b, buttonParams())
            if (focusTarget == null) focusTarget = b
        }
        val open = if (s.kind == "cache") button("Open apps list") { openApplicationsList() }
                   else button("Open Fire TV Settings") { openSettings(Intent(Settings.ACTION_SETTINGS)) }
        buttons.addView(open, buttonParams())
        if (focusTarget == null) focusTarget = open
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
        val ft = focusTarget
        ft?.post { ft.requestFocus() }
    }

    // ───────────────────────── actions ─────────────────────────

    private fun openSettings(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Press Home, then open Settings (the gear) on the Fire TV home screen.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openAppInfo(pkg: String) {
        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$pkg") })
    }

    private fun openApplicationsList() {
        openSettings(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
    }

    private fun finishCleanup() {
        prefs.lastCleanup = System.currentTimeMillis()
        prefs.cleanupSnoozeUntil = 0L
        Toast.makeText(this, "Maintenance done. Thanks!", Toast.LENGTH_LONG).show()
        finish()
    }

    // ───────────────────────── buttons + look ─────────────────────────

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
