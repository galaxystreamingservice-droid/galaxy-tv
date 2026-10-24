package com.galaxymedia.tvsetup

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var profile: DeviceProfile
    private lateinit var prefs: Prefs
    private val io = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        profile = DeviceProfile.detect(this)
        logView = findViewById(R.id.log)
        findViewById<TextView>(R.id.deviceInfo).text = profile.summary()

        val btnPair = findViewById<Button>(R.id.btnPair)
        val btnSetup = findViewById<Button>(R.id.btnSetup)
        val btnSync = findViewById<Button>(R.id.btnSync)
        val btnMaint = findViewById<Button>(R.id.btnMaint)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val btnWifi = findViewById<Button>(R.id.btnWifi)

        fun enableAll(on: Boolean) { listOf(btnSetup, btnSync, btnMaint, btnReset).forEach { it.isEnabled = on } }
        if (prefs.paired) enableAll(true)

        if (profile.tier == "unsupported") {
            log("⚠ This model is too old for the full experience. Lite-only features will be used.")
        }

        btnPair.setOnClickListener {
            log("Connecting to this device's ADB (127.0.0.1:5555)…")
            log(">>> If a dialog appears on screen, choose ALWAYS ALLOW then OK.")
            io.launch {
                val result = AdbShell.connectDetailed(this@MainActivity)
                val ok = result.ok
                withContext(Dispatchers.Main) {
                    log("diag: ${result.detail}")
                    if (ok) {
                        prefs.paired = true
                        log("✓ Paired. Shell access confirmed.")
                        log(AdbShell.exec(this@MainActivity, "getprop ro.product.model").trim().let { "model via shell: $it" })
                        enableAll(true)
                        if (prefs.settingsUrl.isBlank()) askForSettingsUrl()
                    } else {
                        log("✗ Could not connect. Check: Developer Options → ADB Debugging = ON, then try again.")
                        log("  If it never prompts, this build may block loopback ADB — we'll use the phone pairing method instead.")
                    }
                }
            }
        }

        btnSetup.setOnClickListener {
            io.launch {
                val s = SettingsRepository(this@MainActivity).fetch(::logAsync)
                if (s == null) { logAsync("Setup needs a settings file first."); return@launch }

                logAsync("── Debloat ──")
                DebloatEngine(this@MainActivity, profile).run(s.disableAlexa, s.blockOtaUpdates, ::logAsync)

                logAsync("── Launcher ──")
                val li = LauncherInstaller(this@MainActivity)
                li.install(s.launcherPackage, s.launcherApkUrl, ::logAsync)
                li.makeDefault(s.launcherPackage, ::logAsync)

                logAsync("── Apps ──")
                AppReconciler(this@MainActivity).reconcile(s.apps, ::logAsync)

                logAsync("✓ Setup complete. Press HOME.")
            }
        }

        btnSync.setOnClickListener {
            io.launch {
                val s = SettingsRepository(this@MainActivity).fetch(::logAsync) ?: return@launch
                AppReconciler(this@MainActivity).reconcile(s.apps, ::logAsync)
                logAsync("✓ Sync done.")
            }
        }

        btnMaint.setOnClickListener {
            io.launch { MaintenanceTasks.clearCache(this@MainActivity, ::logAsync) }
        }

        // Wi-Fi works before pairing too — it's just the system settings screen.
        btnWifi.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                catch (e2: ActivityNotFoundException) { log("Could not open system settings on this build.") }
            }
        }

        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Are you sure?")
                .setMessage("This erases this device back to Amazon factory defaults. Custom home screen, all apps and settings will be gone. This cannot be undone, and ADB Debugging will be turned off.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Yes, reset it") { _, _ ->
                    log("Factory reset requested…")
                    io.launch { MaintenanceTasks.factoryReset(this@MainActivity) }
                }
                .show()
        }
    }

    private fun askForSettingsUrl() {
        val input = EditText(this).apply { hint = "https://gist.githubusercontent.com/…/raw/device-settings.json" }
        AlertDialog.Builder(this)
            .setTitle("Settings file URL")
            .setMessage("Paste the RAW link to this device's settings Gist.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.settingsUrl = input.text.toString().trim()
                log("settings URL saved")
            }
            .show()
    }

    private fun log(line: String) {
        logView.append("\n$line")
    }

    private fun logAsync(line: String) {
        runOnUiThread { log(line) }
    }
}
