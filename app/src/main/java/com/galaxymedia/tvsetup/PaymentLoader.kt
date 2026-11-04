package com.galaxymedia.tvsetup

import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/** Loads the "_payment" block from the secret codes file, showing the last saved copy straight away. */
object PaymentLoader {
    fun load(activity: AppCompatActivity, prefs: Prefs, onData: (JSONObject) -> Unit, onFail: (reachable: Boolean) -> Unit) {
        var haveSaved = false
        if (prefs.paymentCache.isNotBlank()) {
            try { onData(JSONObject(prefs.paymentCache)); haveSaved = true } catch (e: Exception) { }
        }
        Thread {
            val r = SetupCodes.payment()
            activity.runOnUiThread {
                val p = r.data
                if (p != null) {
                    prefs.paymentCache = p.toString()
                    onData(p)
                } else if (!haveSaved) onFail(r.reachable)
            }
        }.start()
    }
}
