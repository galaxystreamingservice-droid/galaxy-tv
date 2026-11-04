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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Renew screen: payment QR codes and a short "message me" note. Everything shown here (the pictures, the
 * handle, the wording) comes from the secret codes file, so none of it is stored in the public app.
 */
class PayActivity : AppCompatActivity() {

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
            setPadding(dp(56), dp(24), dp(56), dp(18))
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val done = button("Done") { finish() }
        buttons.addView(done, LinearLayout.LayoutParams(dp(200), dp(48)))
        buttons.addView(button("Paid? Set the next reminder") { ReminderPicker.show(this, prefs, true) { } },
            LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(14) })
        root.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        setContentView(root)
        done.requestFocus()

        say("Renew your VPN", "Loading…")
        PaymentLoader.load(this, prefs,
            onData = { render(it) },
            onFail = { reachable ->
                if (reachable) say("Renew your VPN", "The payment details aren't set up yet. Please contact the owner.")
                else say("Renew your VPN", "Couldn't reach the internet. Check Wi-Fi and try again.")
            })
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(18), 0, dp(18), 0)
        isFocusable = true
        isClickable = true
        background = btnBg(false)
        setOnFocusChangeListener { v, has ->
            v.background = btnBg(has)
            (v as TextView).setTextColor(if (has) Color.BLACK else Color.WHITE)
        }
        setOnClickListener { onClick() }
    }

    private fun btnBg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        if (focused) { setColor(accent); setStroke(dp(3), Color.WHITE) }
        else { setColor(Color.parseColor("#14203D")); setStroke(dp(1), Color.parseColor("#2A3A63")) }
    }

    private fun say(title: String, message: String) {
        body.removeAllViews()
        body.addView(TextView(this).apply {
            text = title; textSize = 30f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        body.addView(TextView(this).apply {
            text = message; textSize = 20f; setTextColor(Color.parseColor("#9FB3C8")); setPadding(0, dp(12), 0, 0)
        })
    }

    private fun render(p: JSONObject) {
        body.removeAllViews()

        // Title, with this stick's name on the right so they can quote it
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = p.optString("title", "Renew your VPN")
            textSize = 30f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val who = prefs.userName
        if (who.isNotBlank()) head.addView(TextView(this).apply {
            text = "This stick: $who"; textSize = 18f; setTextColor(accent)
        })
        body.addView(head)

        // The instruction comes first, so it is never cut off
        val note = p.optString("text")
        if (note.isNotBlank()) body.addView(TextView(this).apply {
            text = note
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(8))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(4)) }
        val shown = ArrayList<JSONObject>()
        val all = p.optJSONArray("methods")
        if (all != null) for (i in 0 until all.length()) {
            val m = all.optJSONObject(i) ?: continue
            if (!m.optString("label").contains("telegram", ignoreCase = true)) shown += m   // Telegram lives on the Contact screen
        }
        val n = shown.size
        val qrDp = if (n >= 4) 150 else if (n == 3) 180 else 210
        for (m in shown) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#0E1830"))
                    setStroke(dp(1), Color.parseColor("#2A3A63"))
                }
            }
            card.addView(TextView(this).apply {
                text = m.optString("label"); textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            val qr = m.optString("qr")
            if (qr.isNotBlank()) {
                val bmp = try {
                    val bytes = Base64.decode(qr, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) { null }
                if (bmp != null) card.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    setBackgroundColor(Color.WHITE)
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }, LinearLayout.LayoutParams(dp(qrDp), dp(qrDp)).apply { topMargin = dp(6); bottomMargin = dp(6) })
            }
            card.addView(TextView(this).apply {
                text = m.optString("note"); textSize = 15f; setTextColor(Color.parseColor("#C9D6E8")); gravity = Gravity.CENTER
            })
            row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        }
        body.addView(row)
    }
}
