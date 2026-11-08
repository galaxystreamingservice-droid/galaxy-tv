package com.galaxymedia.tvsetup

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
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

        val ver = currentVersion()
        findViewById<TextView>(R.id.title).text = "Galaxy Media · v$ver"
        findViewById<TextView>(R.id.deviceInfo).text = profile.summary()
        if (profile.tier == "unsupported") log("⚠ This model is too old for the full experience. Lite features only.")

        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkUpdates() }
        findViewById<Button>(R.id.btnInstall).setOnClickListener { startInstalls() }
        findViewById<Button>(R.id.btnSelf).setOnClickListener { selfUpdate() }
        findViewById<Button>(R.id.btnDiag).setOnClickListener { showDiagnostics() }
        findViewById<Button>(R.id.btnUrl).setOnClickListener { askForSettingsUrl() }
        findViewById<Button>(R.id.btnWifi).setOnClickListener { SystemSettings.open(this, SystemSettings.Target.WIFI, null, "Settings › Network") }
        findViewById<Button>(R.id.btnSystem).setOnClickListener {
            log("Factory reset: My Fire TV → Reset to Factory Defaults.")
            SystemSettings.open(this, SystemSettings.Target.MAIN, null, "Settings › My Fire TV › Reset to Factory Defaults")
        }

        styleButtons()
        refreshStatus()
        if (prefs.settingsUrl.isBlank()) log("Press 'Set settings file URL' and enter your setup code.")

        // Coming from the first-run wizard: go straight into the installs.
        if (intent.getBooleanExtra("autoInstall", false)) startInstalls()
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
        statusView.text = "$url · $sync · $perm"
        refreshExpiry()
    }

    /** Subtle renewal-reminder note from the settings file. Muted normally, amber at 14 days, red at 3. */
    private fun refreshExpiry() {
        val exp = prefs.reminderDate
        val date = if (exp.isBlank()) null else try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(exp) } catch (e: Exception) { null }
        if (date == null) { expiryView.visibility = View.GONE; return }
        val endOfDay = date.time + 86_399_000L
        val days = Math.floorDiv(endOfDay - System.currentTimeMillis(), 86_400_000L).toInt()
        val nice = SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        expiryView.text = if (days < 0) "Reminder · expired $nice" else "Reminder · renews $nice"
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val ready = ArrayDeque<Pair<AppEntry, java.io.File>>()
    private var installRun: List<AppEntry> = emptyList()
    private var progressDialog: AlertDialog? = null
    private var progressText: TextView? = null
    private var progressNote: TextView? = null
    private var progressBar: ProgressBar? = null

    /** Step 1 of 2: download every app first, behind a "please don't press anything" screen. */
    private fun startInstalls() {
        if (!updater.canInstall()) { askInstallPermission(); return }
        io.launch {
            val s = SettingsRepository(this@MainActivity).fetch(::logAsync)
            if (s == null) { logAsync("No settings available yet."); return@launch }
            val todo = updater.statuses(s.apps).filter { it.state == "UPDATE" || it.state == "MISSING" }.map { it.app }
            if (todo.isEmpty()) { logAsync("Everything is up to date."); return@launch }
            queue.clear()
            ready.clear()
            installRun = todo
            logAsync("${todo.size} to install.")
            runOnUiThread { downloadAll(todo) }
        }
    }

    private fun downloadAll(todo: List<AppEntry>) {
        showProgress("Getting your apps ready", todo.size)
        io.launch {
            var done = 0
            for (app in todo) {
                withContext(Dispatchers.Main) { updateProgress("Downloading ${app.name}  (${done + 1} of ${todo.size})", done) }
                val file = updater.download(app)
                if (file != null) ready.addLast(app to file) else logAsync("FAIL ${app.name}: download error")
                done++
            }
            withContext(Dispatchers.Main) {
                updateProgress("All downloaded", todo.size)
                if (ready.isEmpty()) { hideProgress(); showSummary(); return@withContext }
                progressNote?.text = "Next, Fire TV will ask you to press Install for each app. Press Install every time it asks."
                window.decorView.postDelayed({ hideProgress(); nextInstall() }, 3000)
            }
        }
    }

    /** Step 2 of 2: open the installer prompts one after another. No waiting between them. */
    /** Big, obvious focus: the selected button turns solid accent color with a thick white border and grows a little. */
    private fun styleButtons() {
        val accent = Themes.accentColor(prefs.accent)
        val d = resources.displayMetrics.density
        fun bg(focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 12 * d
            if (focused) { setColor(accent); setStroke((4 * d).toInt(), Color.WHITE) }
            else { setColor(Color.parseColor("#14203D")); setStroke((1 * d).toInt(), Color.parseColor("#2A3A63")) }
        }
        for (id in listOf(R.id.btnCheck, R.id.btnInstall, R.id.btnSelf, R.id.btnDiag, R.id.btnUrl, R.id.btnWifi, R.id.btnSystem)) {
            val b = findViewById<Button>(id)
            b.isAllCaps = false
            b.textSize = 18f
            b.setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            b.background = bg(false)
            b.setTextColor(Color.WHITE)
            b.setOnFocusChangeListener { v, has ->
                v.background = bg(has)
                (v as Button).setTextColor(if (has) Color.BLACK else Color.WHITE)
                v.animate().scaleX(if (has) 1.03f else 1f).scaleY(if (has) 1.03f else 1f).setDuration(120).start()
            }
        }
        findViewById<Button>(R.id.btnInstall).text = "Install / update apps"
        findViewById<Button>(R.id.btnCheck).requestFocus()
    }

    private fun nextInstall() {
        val (app, file) = ready.removeFirstOrNull() ?: run { showSummary(); return }
        log("install ${app.name} …")
        updater.markPending(app)
        awaitingInstall = true
        try {
            startActivity(updater.installIntent(file))
        } catch (e: Exception) {
            awaitingInstall = false
            log("FAIL ${app.name}: ${e.message}")
            nextInstall()
        }
    }

    private fun showSummary() {
        val run = installRun
        if (run.isEmpty()) return
        installRun = emptyList()
        refreshStatus()
        val missing = run.count { !updater.isInstalled(it.packageName) }
        val lines = run.joinToString("\n") { a -> (if (updater.isInstalled(a.packageName)) "OK         " else "Not installed  ") + a.name }
        val b = AlertDialog.Builder(this)
            .setTitle(if (missing == 0) "All done" else "$missing not installed yet")
            .setMessage(lines)
            .setPositiveButton("Done", null)
        if (missing > 0) b.setNegativeButton("Try again") { _, _ -> startInstalls() }
        b.show()
    }

    private fun showProgress(title: String, total: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(12))
        }
        progressText = TextView(this).apply { textSize = 20f; text = "Starting…" }
        progressNote = TextView(this).apply {
            text = "Please don't press anything on the remote while this runs."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(14))
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = total; progress = 0 }
        box.addView(progressText)
        box.addView(progressNote)
        box.addView(progressBar, LinearLayout.LayoutParams(-1, -2))
        progressDialog = AlertDialog.Builder(this).setTitle(title).setView(box).setCancelable(false).show()
    }

    private fun updateProgress(text: String, done: Int) {
        progressText?.text = text
        progressBar?.progress = done
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun askInstallPermission() {
        AlertDialog.Builder(this)
            .setTitle("Allow installs")
            .setMessage("Turn ON 'Install unknown apps' for Galaxy Media on the next screen, then come back and press Install / update apps.")
            .setPositiveButton("Open") { _, _ ->
                SystemSettings.open(this, SystemSettings.Target.UNKNOWN_SOURCES, packageName, "Settings › My Fire TV › Developer options › Install unknown apps")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askForSettingsUrl() {
        val input = EditText(this).apply {
            hint = "GAL-1234"
            setText(if (prefs.settingsUrl.isBlank()) "" else prefs.settingsUrl)
        }
        AlertDialog.Builder(this)
            .setTitle("Setup code")
            .setMessage("Enter the short setup code (like GAL-1234). The full link or the 32-character code also works.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) {
                    log("Nothing was entered. Try again.")
                } else {
                    io.launch {
                        val r = SetupCodes.resolve(text)
                        if (r.url == null) {
                            logAsync(r.error ?: "Code not found.")
                        } else {
                            prefs.settingsUrl = r.url
                            logAsync("Setup code accepted. Press Check updates.")
                            runOnUiThread { refreshStatus() }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun currentVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    /** Checks GitHub for a newer Galaxy Media and installs it over this one (same signing key). */
    private fun selfUpdate() {
        if (!updater.canInstall()) { askInstallPermission(); return }
        io.launch {
            val rel = updater.latestRelease()
            if (rel == null) { logAsync("Couldn't reach GitHub to check for a new version."); return@launch }
            val mine = currentVersion()
            if (!updater.isNewer(rel.version, mine)) { logAsync("Galaxy Media is up to date (v$mine)."); return@launch }
            logAsync("New version v${rel.version} found. Downloading…")
            val file = updater.downloadTo(rel.apkUrl, "galaxy-self.apk")
            withContext(Dispatchers.Main) {
                if (file == null) { log("FAIL Galaxy Media: download error"); return@withContext }
                try { startActivity(updater.installIntent(file)) }
                catch (e: Exception) { log("FAIL Galaxy Media: ${e.message}") }
            }
        }
    }

    /** Big, photo-friendly report. Contains no links or passwords. */
    private fun showDiagnostics() {
        val raw = prefs.lastSettingsJson
        val appLines = try {
            if (raw.isBlank()) "settings file: not loaded yet"
            else DeviceSettings.parse(raw).apps.joinToString("\n") {
                (if (updater.isInstalled(it.packageName)) "[x] " else "[ ] ") + it.name
            }
        } catch (e: Exception) { "settings file: unreadable" }
        val lastLog = logView.text.toString().lines().takeLast(12).joinToString("\n")
        val report = "Galaxy Media v${currentVersion()}\n" +
            "${profile.summary()}\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Build ${Build.DISPLAY}\n" +
            "${statusView.text}\n\n" +
            (if (raw.isBlank()) "APPS\n" else "APPS ([x] = installed)\n") + "$appLines\n\n" +
            "LOG\n$lastLog"
        val dark = Color.parseColor("#0A1522")
        val tv = TextView(this).apply {
            text = report; textSize = 18f; setPadding(48, 32, 48, 32)
            setTextColor(Color.parseColor("#EAF6FF")); setBackgroundColor(dark)
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Diagnostics v${currentVersion()} - take a photo")
            .setView(ScrollView(this).apply { setBackgroundColor(dark); addView(tv) })
            .setPositiveButton("Close", null)
            .create()
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(dark))
        dlg.show()
    }

    private fun log(line: String) { logView.append("\n$line") }
    private fun logAsync(line: String) { runOnUiThread { log(line) } }
}
