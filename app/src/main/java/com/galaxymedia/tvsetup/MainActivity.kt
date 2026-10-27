package com.galaxymedia.tvsetup

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.galaxymedia.tvsetup.model.AppEntry
import com.galaxymedia.tvsetup.model.DeviceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private lateinit var expiryView: TextView
    private lateinit var profile: DeviceProfile
    private lateinit var prefs: Prefs
    private lateinit var updater: AppUpdater
    private val io = CoroutineScope(Dispatchers.IO)

    private val queue = ArrayDeque<AppEntry>()
    private var awaitingInstall = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        profile = DeviceProfile.detect(this)
        updater = AppUpdater(this, prefs)
        logView = findViewById(R.id.log)
        statusView = findViewById(R.id.status)
        expiryView = findViewById(R.id.expiry)

        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        findViewById<TextView>(R.id.title).text = getString(R.string.title) + "  ·  v$ver"
        findViewById<TextView>(R.id.deviceInfo).text = profile.summary()
        if (profile.tier == "unsupported") log("⚠ This model is too old for the full experience. Lite features only.")

        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkUpdates() }
        findViewById<Button>(R.id.btnInstall).setOnClickListener { startInstalls() }
        findViewById<Button>(R.id.btnUrl).setOnClickListener { askForSettingsUrl() }
        findViewById<Button>(R.id.btnWifi).setOnClickListener { openSystem(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<Button>(R.id.btnSystem).setOnClickListener {
            log("Factory reset: My Fire TV → Reset to Factory Defaults.")
            openSystem(Settings.ACTION_SETTINGS)
        }

        refreshStatus()
        if (prefs.settingsUrl.isBlank()) log("Press 'Set settings file URL' and paste this TV's settings link.")
    }

    override fun onResume() {
        super.onResume()
        if (awaitingInstall) {
            awaitingInstall = false
            updater.reconcilePending(::log)
            refreshStatus()
            nextInstall()
        } else {
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val url = if (prefs.settingsUrl.isBlank()) "settings URL: not set" else "settings URL: set"
        val sync = if (prefs.lastSync == 0L) "never checked"
                   else "checked " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(prefs.lastSync))
        val perm = if (updater.canInstall()) "installs allowed" else "installs NOT allowed yet"
        statusView.text = "$url  ·  $sync  ·  $perm"
        refreshExpiry()
    }

    /** Subtle IPTV renewal note from the settings file. Muted normally, amber at 14 days, red at 3. */
    private fun refreshExpiry() {
        val raw = prefs.lastSettingsJson
        val exp = try { if (raw.isBlank()) "" else DeviceSettings.parse(raw).iptvExpires } catch (e: Exception) { "" }
        val date = if (exp.isBlank()) null else try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(exp) } catch (e: Exception) { null }
        if (date == null) { expiryView.visibility = View.GONE; return }
        val endOfDay = date.time + 86_399_000L
        val days = Math.floorDiv(endOfDay - System.currentTimeMillis(), 86_400_000L).toInt()
        val nice = SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        expiryView.text = if (days < 0) "IPTV · expired $nice" else "IPTV · renews $nice"
        expiryView.setTextColor(when {
            days <= 3 -> Color.parseColor("#FF6B6B")
            days <= 14 -> Color.parseColor("#E3B341")
            else -> Color.parseColor("#6F8A99")
        })
        expiryView.visibility = View.VISIBLE
    }

    private fun checkUpdates() {
        io.launch {
            val s = SettingsRepository(this@MainActivity).fetch(::logAsync)
            if (s == null) { logAsync("No settings available yet."); return@launch }
            prefs.lastSync = System.currentTimeMillis()
            logAsync("── ${s.deviceName} ──")
            updater.statuses(s.apps).forEach { logAsync(it.line()) }
            runOnUiThread { refreshStatus() }
        }
    }

    private fun startInstalls() {
        if (!updater.canInstall()) { askInstallPermission(); return }
        io.launch {
            val s = SettingsRepository(this@MainActivity).fetch(::logAsync)
            if (s == null) { logAsync("No settings available yet."); return@launch }
            val todo = updater.statuses(s.apps).filter { it.state == "UPDATE" || it.state == "MISSING" }.map { it.app }
            if (todo.isEmpty()) { logAsync("Everything is up to date."); return@launch }
            queue.clear()
            queue.addAll(todo)
            logAsync("${todo.size} to install. Press Install on each prompt.")
            runOnUiThread { nextInstall() }
        }
    }

    private fun nextInstall() {
        val app = queue.removeFirstOrNull() ?: return
        log("get   ${app.name} …")
        io.launch {
            val file = updater.download(app)
            withContext(Dispatchers.Main) {
                if (file == null) {
                    log("FAIL  ${app.name}: download error")
                    nextInstall()
                } else {
                    updater.markPending(app)
                    awaitingInstall = true
                    try {
                        startActivity(updater.installIntent(file))
                    } catch (e: Exception) {
                        awaitingInstall = false
                        log("FAIL  ${app.name}: ${e.message}")
                        nextInstall()
                    }
                }
            }
        }
    }

    private fun askInstallPermission() {
        AlertDialog.Builder(this)
            .setTitle("Allow installs")
            .setMessage("Turn ON 'Install unknown apps' for Galaxy TV Setup on the next screen, then come back and press Install updates.")
            .setPositiveButton("Open") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                } catch (e: ActivityNotFoundException) {
                    log("Could not open that screen. Go to Settings → My Fire TV → Developer Options → Install unknown apps.")
                    openSystem(Settings.ACTION_SETTINGS)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openSystem(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (e2: ActivityNotFoundException) { log("Could not open system settings on this build.") }
        }
    }

    private fun askForSettingsUrl() {
        val input = EditText(this).apply {
            hint = "https://gist.githubusercontent.com/…/raw/device-settings.json"
            setText(prefs.settingsUrl)
        }
        AlertDialog.Builder(this)
            .setTitle("Settings file URL")
            .setMessage("Paste the RAW link to this device's settings Gist.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                prefs.settingsUrl = url
                if (url.isBlank()) log("settings URL is EMPTY - nothing was pasted. Try again.")
                else log("settings URL saved (${url.length} chars, ends …${url.takeLast(20)})")
                refreshStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun log(line: String) { logView.append("\n$line") }
    private fun logAsync(line: String) { runOnUiThread { log(line) } }
}
