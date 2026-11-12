package com.galaxymedia.tvsetup

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

/**
 * Tips & tricks: one card per video, each with a QR code to scan with a phone camera.
 * The list comes from the "_tips" block of the codes file, so videos can be added without a new build:
 * "_tips": {"intro": "...", "items": [{"title": "Setup", "note": "From a fresh Fire TV to your apps", "url": "https://youtu.be/..."}]}
 */
class TipsActivity : Activity() {

    private lateinit var prefs: Prefs
    private var accent = Color.parseColor("#22D3EE")
    private val bg = Color.parseColor("#060A16")
    private val card = Color.parseColor("#141B3A")
    private val textColor = Color.parseColor("#EAF6FF")
    private val muted = Color.parseColor("#9FB8C8")
    private lateinit var list: LinearLayout
    private lateinit var intro: TextView

    private val defaults = listOf(
        "Galaxy Media basics" to "Home screen, the menu, settings and updates",
        "Keep it running" to "Maintenance, resetting an app, quick fixes",
        "Live TV with Galaxy 2.0" to "WorldVPN, live TV, sports search, multi-screen",
        "TiviMate and movies" to "TiviMate, then Galaxy Theater"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        accent = Themes.accentColor(prefs.accent)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(32), dp(56), dp(32))
        }
        body.addView(TextView(this).apply {
            text = "How-to videos"; textSize = 32f * prefs.textScale; setTextColor(textColor); setTypeface(null, Typeface.BOLD)
        })
        intro = TextView(this).apply {
            text = "Loading…"; textSize = 18f * prefs.textScale; setTextColor(muted); setPadding(0, dp(6), 0, dp(16))
        }
        body.addView(intro)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(body) }
        val root = FrameLayout(this).apply { setBackgroundColor(bg); addView(scroll) }
        setContentView(root)
        Thread {
            val r = SetupCodes.tips()
            runOnUiThread { show(r.data, r.reachable) }
        }.start()
    }

    private fun show(data: JSONObject?, reachable: Boolean) {
        val items = mutableListOf<Triple<String, String, String>>()
        val arr = data?.optJSONArray("items")
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            items += Triple(o.optString("title"), o.optString("note"), o.optString("url"))
        }
        if (items.isEmpty()) defaults.forEach { items += Triple(it.first, it.second, "") }
        intro.text = when {
            !reachable -> "No internet right now. Check the Wi-Fi, then open Tips again."
            data?.optString("intro").isNullOrBlank() -> "Point your phone's camera at a code to watch that video on your phone."
            else -> data!!.optString("intro")
        }
        list.removeAllViews()
        items.chunked(3).forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (title, note, url) ->
                line.addView(tile(title, note, url), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(16); bottomMargin = dp(16)
                })
            }
            repeat(3 - row.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            list.addView(line)
        }
        (list.getChildAt(0) as? LinearLayout)?.getChildAt(0)?.requestFocus()
    }

    private fun tile(title: String, note: String, url: String): View {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isFocusable = true
            background = box(false)
            setOnFocusChangeListener { v, has -> v.background = box(has) }
        }
        if (url.isNotBlank()) {
            val qr = qr(url, dp(150))
            if (qr != null) t.addView(ImageView(this).apply {
                setImageBitmap(qr); setBackgroundColor(Color.WHITE); setPadding(dp(6), dp(6), dp(6), dp(6))
            }, LinearLayout.LayoutParams(dp(162), dp(162)))
        } else {
            t.addView(TextView(this).apply {
                text = "Video coming soon"; textSize = 15f * prefs.textScale; setTextColor(muted); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(162), dp(162)))
        }
        t.addView(TextView(this).apply {
            text = title; textSize = 20f * prefs.textScale; setTextColor(textColor); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        })
        if (note.isNotBlank()) t.addView(TextView(this).apply {
            text = note; textSize = 15f * prefs.textScale; setTextColor(muted); gravity = Gravity.CENTER
        })
        return t
    }

    private fun box(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(card)
        setStroke(dp(if (focused) 4 else 1), if (focused) accent else Color.parseColor("#2A3560"))
    }

    private fun qr(content: String, size: Int): Bitmap? = try {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    } catch (e: Exception) { null }
}
