package com.galaxymedia.tvsetup

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

/**
 * Short how-to videos for the help and maintenance steps, shown as a QR code to watch on a phone
 * while the TV is busy doing the step. The links live in the "help" part of the "_tips" block of the
 * codes file, so a new video needs no app update:
 *   "_tips": { ..., "help": { "clear_cache": "https://youtu.be/...", "restart_stick": "https://youtu.be/..." } }
 * Keys: wifi, vpn, audio, restart_app, clear_cache, clear_data, uninstall, reinstall, update_apps,
 *       restart_stick, storage, autoplay, privacy, unknown_apps, maintenance.
 * A step only shows a code once its video has a link, so there are never dead codes.
 */
object HelpVideos {

    @Volatile private var links: Map<String, String> = emptyMap()
    @Volatile private var fetched = false

    private fun parse(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = HashMap<String, String>()
        o.keys().forEach { k -> val v = o.optString(k); if (v.startsWith("http")) m[k] = v }
        return m
    }

    /** Loads the links once per app start (the last copy is kept, so codes show offline too).
     *  [changed] runs on the main thread only if the links differ from what was shown already. */
    fun load(activity: Activity, changed: () -> Unit = {}) {
        val sp = activity.getSharedPreferences("help_videos", Context.MODE_PRIVATE)
        if (links.isEmpty()) links = try { parse(JSONObject(sp.getString("links", "{}") ?: "{}")) } catch (e: Exception) { emptyMap() }
        if (fetched) return
        fetched = true
        Thread {
            val r = SetupCodes.tips()
            val help = r.data?.optJSONObject("help") ?: return@Thread
            val fresh = parse(help)
            sp.edit().putString("links", help.toString()).apply()
            if (fresh != links) {
                links = fresh
                activity.runOnUiThread { if (!activity.isFinishing) changed() }
            }
        }.start()
    }

    fun url(key: String): String? = links[key]

    fun qr(content: String, size: Int): Bitmap? = try {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    } catch (e: Exception) { null }

    /** The QR code with "Watch how on your phone" under it, or null when this step has no video yet. */
    fun panel(ctx: Context, key: String, textScale: Float, textColor: Int, sizePx: Int): View? {
        if (key.isEmpty()) return null
        val u = url(key) ?: return null
        val bmp = qr(u, sizePx) ?: return null
        val pad = (sizePx * 0.04f).toInt()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ImageView(ctx).apply {
                setImageBitmap(bmp); setBackgroundColor(Color.WHITE); setPadding(pad, pad, pad, pad)
            }, LinearLayout.LayoutParams(sizePx + 2 * pad, sizePx + 2 * pad))
            addView(TextView(ctx).apply {
                text = "Watch how\non your phone"
                textSize = 15f * textScale
                setTextColor(textColor)
                gravity = Gravity.CENTER
                setPadding(0, pad * 2, 0, 0)
            })
        }
    }
}
