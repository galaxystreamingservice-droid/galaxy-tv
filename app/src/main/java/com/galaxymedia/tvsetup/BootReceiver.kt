package com.galaxymedia.tvsetup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opens the home screen after the stick boots. Android 10+ only lets a background app start a
 * screen if it has "display over other apps"; the laptop maintenance script grants that.
 * If it isn't granted, this quietly does nothing.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).autoStart) return
        try {
            context.startActivity(
                Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { }
    }
}
