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
 * Renew screen: payment QR codes and a short "text me" note. Everything shown here (the pictures, the
 * number, the wording) comes from the secret codes file, so none of it is stored in the public app.
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
            setPadding(dp(56), dp(28), dp(56), dp(22))
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
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
        root.addView(done, LinearLayout.LayoutParams(dp(220), dp(52)).apply { topMargin = dp(10) })
        setContentView(root)
        done.requestFocus()

        say("Renew your VPN", "Loading…")
        // Show the last copy straight away so the screen is never empty offline, then refresh it.
        if (prefs.paymentCache.isNotBlank()) {
            try { render(JSONObject(prefs.paymentCache)) } catch (e: Exception) { }
        }
        Thread {
            val p = SetupCodes.payment()
            runOnUiThread {
                if (p != null) {
                    prefs.paymentCache = p.toString()
                    render(p)
                } else if (prefs.paymentCache.isBlank()) {
                    say("Renew your VPN", "Couldn't load the payment details. Check Wi-Fi and try again, or contact the owner.")
                }
            }
        }.start()
    }

    private fun btnBg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        if (focused) { setColor(accent); setStroke(dp(3), Color.WHITE) }
        else { setColor(Color.parseColor("#14203D")); setStroke(dp(1), Color.parseColor("#2A3A63")) }
    }

    private fun say(title: String, message: String) {
        body.removeAllViews()
        body.addView(TextView(this).apply {
            text = title; textSize = 32f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        body.addView(TextView(this).apply {
            text = message; textSize = 20f; setTextColor(Color.parseColor("#9FB3C8")); setPadding(0, dp(12), 0, 0)
        })
    }

    private fun render(p: JSONObject) {
        body.removeAllViews()
        body.addView(TextView(this).apply {
            text = p.optString("title", "Renew your VPN")
            textSize = 32f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        val who = prefs.userName
        if (who.isNotBlank()) body.addView(TextView(this).apply {
            text = "This stick: $who"
            textSize = 18f; setTextColor(accent); setPadding(0, dp(2), 0, dp(10))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(10)) }
        val methods = p.optJSONArray("methods")
        if (methods != null) for (i in 0 until methods.length()) {
            val m = methods.optJSONObject(i) ?: continue
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), dp(12), dp(10), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#0E1830"))
                    setStroke(dp(1), Color.parseColor("#2A3A63"))
                }
            }
            card.addView(TextView(this).apply {
                text = m.optString("label"); textSize = 22f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
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
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }, LinearLayout.LayoutParams(dp(210), dp(210)).apply { topMargin = dp(8); bottomMargin = dp(8) })
            }
            card.addView(TextView(this).apply {
                text = m.optString("note"); textSize = 17f; setTextColor(Color.parseColor("#C9D6E8")); gravity = Gravity.CENTER
            })
            row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
        }
        body.addView(row)

        val text = p.optString("text")
        if (text.isNotBlank()) body.addView(TextView(this).apply {
            this.text = text
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(8), dp(6), dp(8))
        })
    }
}
