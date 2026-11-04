package com.galaxymedia.tvsetup

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/** Reports and contact: the Telegram code from the secret payment block, for any problem or question. */
class ContactActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private var accent = Color.parseColor("#22D3EE")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        accent = Themes.accentColor(prefs.accent)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050816"))
            setPadding(dp(56), dp(28), dp(56), dp(22))
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val done = TextView(this).apply {
            text = "Done"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            isFocusable = true
            isClickable = true
            background = btnBg(false)
            setOnFocusChangeListener { v, has ->
                v.background = btnBg(has)
                (v as TextView).setTextColor(if (has) Color.BLACK else Color.WHITE)
            }
            setOnClickListener { finish() }
        }
        root.addView(done, LinearLayout.LayoutParams(dp(220), dp(48)))
        setContentView(root)
        done.requestFocus()

        say("Reports and contact", "Loading…")
        PaymentLoader.load(this, prefs,
            onData = { render(it) },
            onFail = { reachable ->
                say("Reports and contact", if (reachable) "Contact details aren't set up yet. Please ask the owner." else "Couldn't reach the internet. Check Wi-Fi and try again.")
            })
    }

    private fun btnBg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        if (focused) { setColor(accent); setStroke(dp(3), Color.WHITE) }
        else { setColor(Color.parseColor("#14203D")); setStroke(dp(1), Color.parseColor("#2A3A63")) }
    }

    private fun say(title: String, message: String) {
        body.removeAllViews()
        body.addView(TextView(this).apply { text = title; textSize = 30f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) })
        body.addView(TextView(this).apply { text = message; textSize = 20f; setTextColor(Color.parseColor("#9FB3C8")); setPadding(0, dp(12), 0, 0) })
    }

    private fun render(p: JSONObject) {
        // The Telegram card from the payment block
        var tg: JSONObject? = null
        val methods = p.optJSONArray("methods")
        if (methods != null) for (i in 0 until methods.length()) {
            val m = methods.optJSONObject(i) ?: continue
            if (m.optString("label").contains("telegram", ignoreCase = true)) { tg = m; break }
        }
        val card: JSONObject = tg ?: run {
            say("Reports and contact", "Contact details aren't set up yet. Please ask the owner.")
            return
        }

        body.removeAllViews()
        body.addView(TextView(this).apply { text = "Reports and contact"; textSize = 30f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) })
        body.addView(TextView(this).apply {
            text = "Something not working, a question, or a payment to confirm? Message me on Telegram."
            textSize = 20f; setTextColor(Color.parseColor("#C9D6E8")); setPadding(0, dp(6), 0, dp(14))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val qr = card.optString("qr")
        if (qr.isNotBlank()) {
            val bmp = try {
                val bytes = Base64.decode(qr, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) { null }
            if (bmp != null) row.addView(ImageView(this).apply {
                setImageBitmap(bmp); setBackgroundColor(Color.WHITE); setPadding(dp(8), dp(8), dp(8), dp(8))
                scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true
            }, LinearLayout.LayoutParams(dp(230), dp(230)))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), 0, 0, 0) }
        col.addView(TextView(this).apply { text = card.optString("note"); textSize = 30f; setTextColor(accent); setTypeface(null, Typeface.BOLD) })
        col.addView(TextView(this).apply {
            text = "Scan the code with your phone's camera, or search for that name in Telegram."
            textSize = 20f; setTextColor(Color.WHITE); setPadding(0, dp(10), 0, 0)
        })
        val who = prefs.userName
        if (who.isNotBlank()) col.addView(TextView(this).apply {
            text = "Say this stick's name: $who"; textSize = 18f; setTextColor(Color.parseColor("#9FB3C8")); setPadding(0, dp(10), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(row)
    }
}
