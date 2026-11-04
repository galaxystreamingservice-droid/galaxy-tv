package com.galaxymedia.tvsetup

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Every app on the stick that can be opened, in one grid. Works as a safety net: even with
 * apps that are not on the shelf, or when Amazon's home screen is turned off, everything is reachable here.
 */
class AllAppsActivity : AppCompatActivity() {

    private class Entry(val label: String, val pkg: String, val system: Boolean, val intent: Intent, val icon: Drawable?)

    private var accent = Color.parseColor("#22D3EE")
    private lateinit var prefs: Prefs
    private val columns = 6

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        accent = Themes.accentColor(prefs.accent)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050816"))
            setPadding(dp(56), dp(32), dp(56), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "All apps"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Press OK to open an app. Hold OK on an app to remove it. Back returns to Galaxy Media."
            textSize = 16f
            setTextColor(Color.parseColor("#9FB3C8"))
            setPadding(0, dp(4), 0, dp(14))
        })

        val entries = loadApps()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var first: View? = null
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No apps found."
                textSize = 20f
                setTextColor(Color.WHITE)
            })
        }
        entries.chunked(columns).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { e ->
                val t = tile(e)
                if (first == null) first = t
                row.addView(t, LinearLayout.LayoutParams(0, dp(116), 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            }
            // keep the last row's tiles the same width as the rows above
            repeat(columns - rowItems.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(116), 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(0, 0, 0, dp(12))
            addView(list)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        first?.requestFocus()
    }

    private fun loadApps(): List<Entry> {
        val pm = packageManager
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg.startsWith("com.galaxymedia.tvsetup") || !seen.add(pkg)) continue
                val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val f = ri.activityInfo.applicationInfo.flags
                val builtIn = (f and ApplicationInfo.FLAG_SYSTEM) != 0 || (f and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                out += Entry(ri.loadLabel(pm).toString(), pkg, builtIn, i, try { ri.loadIcon(pm) } catch (e: Exception) { null })
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    /** Apps Amazon allows to be removed get a normal Fire TV uninstall prompt. Built-in apps cannot be removed here. */
    private fun confirmRemove(e: Entry) {
        if (e.system) {
            AlertDialog.Builder(this)
                .setTitle(e.label)
                .setMessage("This is a built-in app, so Fire TV does not let it be removed here.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Remove ${e.label}?")
            .setMessage("Fire TV will ask you to confirm. You can install it again later.")
            .setPositiveButton("Remove") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}"))) }
                catch (ex: Exception) { Toast.makeText(this, "Couldn't start the removal.", Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun bg(focused: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        if (focused) {
            setColor(accent)
            setStroke(dp(3), Color.WHITE)
        } else {
            setColor(Color.parseColor("#14203D"))
            setStroke(dp(1), Color.parseColor("#2A3A63"))
        }
    }

    private fun tile(e: Entry): View {
        val label = TextView(this).apply {
            text = e.label
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(dp(8), dp(10), dp(8), dp(8))
            background = bg(false)
            addView(ImageView(this@AllAppsActivity).apply { setImageDrawable(e.icon) }, LinearLayout.LayoutParams(dp(54), dp(54)))
            addView(label)
        }
        box.setOnFocusChangeListener { v, has ->
            v.background = bg(has)
            label.setTextColor(if (has) Color.BLACK else Color.WHITE)
            v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
        }
        box.setOnLongClickListener { confirmRemove(e); true }
        box.setOnClickListener {
            try { startActivity(e.intent) }
            catch (ex: Exception) { Toast.makeText(this, "Couldn't open ${e.label}", Toast.LENGTH_SHORT).show() }
        }
        return box
    }
}
