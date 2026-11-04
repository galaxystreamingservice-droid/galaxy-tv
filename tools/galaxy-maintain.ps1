<#
Galaxy Media maintenance. Run from a laptop on the same Wi-Fi as the stick.
Nothing is ever uninstalled. Every step except -Reboot can be undone.

  .\galaxy-maintain.ps1 -Ip 192.168.1.172              report + re-apply debloat + trim caches
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Report      report only (read-only)
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Debloat -DryRun   show what would be disabled
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Speed Fast  animations at 0.5x (Off, Fast, Default)
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -AutoStart   let Galaxy Media open itself after a restart
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Reboot      reboot the stick at the end

Measure and test (use 3 runs and compare, one reading proves little):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Audit      read-only: memory, heat, Wi-Fi, storage, frames, what is switched off
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Benchmark  app launch times and Galaxy Media smoothness, 3 runs each (about 3 minutes)
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Undebloat  turn the bloat packages back on, for before/after tests
  .\galaxy-maintain.ps1 -Ip 192.168.1.50 -Port 37123 -Audit   a box that uses Wireless debugging (Onn / Google TV)
  .\galaxy-maintain.ps1 -Ip 192.168.1.50 -Pair 192.168.1.50:41234   pair first (it asks for the 6-digit code)
  .\galaxy-maintain.ps1 -Ip 192.168.1.50 -Port 37123 -Dump    save the full package list to upload for a new box

Install the shelf apps with no taps on the TV (for sticks you set up yourself):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -InstallApps -Gist <the 32-character settings code>

Check everything worked (read-only, saves a screenshot of the TV):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Verify     PASS / WARN / FAIL list for the whole setup
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Screenshot just save a picture of what the TV shows

HOME screen test (only with the separate "Galaxy Media HOME TEST" app installed, never on family sticks):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -HomeCheck   read-only: who answers the Home button right now
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -HomeLock    Home button opens Galaxy Media (turns Amazon's home off)
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -HomeUndo    put Amazon's home screen back

-Yes skips the confirmation question before disabling packages.
Add -Alexa and/or -Ota to -Debloat if that stick's settings file turns those groups on.
Stick IP: Settings > My Fire TV > About > Network.
#>
[CmdletBinding()]
param(
    [string]$Ip,
    [switch]$Report,
    [switch]$Debloat,
    [switch]$Trim,
    [switch]$AutoStart,
    [ValidateSet('', 'Off', 'Fast', 'Default')][string]$Speed = '',
    [switch]$Alexa,
    [switch]$Ota,
    [switch]$DryRun,
    [switch]$Yes,
    [switch]$Reboot,
    [switch]$HomeCheck,
    [switch]$HomeLock,
    [switch]$HomeUndo,
    [switch]$Verify,
    [switch]$Screenshot,
    [switch]$Audit,
    [switch]$Benchmark,
    [switch]$Undebloat,
    [switch]$Dump,
    [switch]$InstallApps,
    [string]$Gist,
    [int]$Port = 5555,
    [string]$Pair
)

$ScriptVersion = '2026-11-01 verify'
Write-Host "galaxy-maintain $ScriptVersion" -ForegroundColor DarkGray
$ErrorActionPreference = 'Continue'   # adb writes status text to stderr; don't treat that as an error
$ListUrl = 'https://raw.githubusercontent.com/galaxystreamingservice-droid/galaxy-tv/main/app/src/main/assets/debloat_fireos.json'

# No action chosen = the safe default set.
if (-not ($Report -or $Debloat -or $Trim -or $AutoStart -or [bool]$Speed -or $Reboot -or $HomeCheck -or $HomeLock -or $HomeUndo -or $Verify -or $Screenshot -or $Audit -or $Benchmark -or $Undebloat -or $Dump -or $InstallApps)) {
    $Report = $true; $Debloat = $true; $Trim = $true; $AutoStart = $true
}

# ---- find adb ----
function Find-Adb {
    $c = Get-Command adb -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    foreach ($p in @("$PSScriptRoot\adb.exe", "$PSScriptRoot\platform-tools\adb.exe",
                     "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", 'C:\platform-tools\adb.exe')) {
        if (Test-Path $p) { return $p }
    }
    return $null
}
$adb = Find-Adb
if (-not $adb) { Write-Host 'adb not found. Put adb.exe next to this script or add platform-tools to PATH.' -ForegroundColor Red; exit 1 }

if (-not $Ip) { $Ip = Read-Host 'Stick IP address' }
$target = "${Ip}:$Port"

function Sh([string]$cmd) {
    @(& $adb -s $target shell $cmd 2>&1 | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}

# ---- connect ----
if ($Pair) {
    Write-Host "Pairing with $Pair (type the 6-digit code shown on the box) ..."
    & $adb pair $Pair
}
Write-Host "Connecting to $target ..."
& $adb connect $target | Out-Null
$state = (& $adb -s $target get-state 2>&1 | Out-String).Trim()
if ($state -ne 'device') {
    Write-Host "Not connected ($state)." -ForegroundColor Red
    Write-Host 'Check the IP, that ADB Debugging is ON, and accept the Allow USB debugging prompt on the TV.'
    exit 1
}

$out = New-Object System.Collections.Generic.List[string]
function Say([string]$line, [string]$color = '') {
    $out.Add($line)
    if ($color) { Write-Host $line -ForegroundColor $color } else { Write-Host $line }
}

function Get-FreeMB {
    try {
        $row = (Sh 'df /data' | Select-Object -Last 1) -split '\s+'
        return [math]::Round([int64]$row[3] / 1024)
    } catch { return $null }
}

function Get-List([string]$cmd) {
    Sh $cmd | Where-Object { $_ -like 'package:*' } | ForEach-Object { $_ -replace '^package:', '' }
}

# The never-touch list lives in the repo, next to the debloat list.
$cfg = $null
try { $cfg = (Invoke-WebRequest -UseBasicParsing -ErrorAction Stop $ListUrl).Content | ConvertFrom-Json }
catch { Say 'Could not download the debloat list from GitHub (laptop offline?).' 'Yellow' }

$model = (Sh 'getprop ro.product.model' | Select-Object -First 1)
$date = Get-Date -Format 'yyyy-MM-dd'
$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
$changed = $false

# ---- 1. report ----
if ($Report) {
    Say "=== REPORT  $model  $Ip  $date ==="
    Say ('Fire OS / Android: ' + (Sh 'getprop ro.build.version.release' | Select-Object -First 1) +
         '   Build: ' + (Sh 'getprop ro.build.display.id' | Select-Object -First 1))
    $free = Get-FreeMB
    if ($null -ne $free) { Say "Free storage: $free MB" }
    $m = [regex]::Match(((Sh 'cat /proc/meminfo') -join "`n"), 'MemAvailable:\s+(\d+)')
    if ($m.Success) { Say ('Free memory: ' + [math]::Round([int64]$m.Groups[1].Value / 1024) + ' MB') }
    Say 'ADB: connected'
    $last = Sh 'settings get global galaxy_last_maintained' | Select-Object -First 1
    Say ('Last maintained: ' + $(if ($last -and $last -ne 'null') { $last } else { 'never' }))
    if ($cfg) {
        $disabled = Get-List 'pm list packages -d'
        $installed = Get-List 'pm list packages'
        Say 'Apps:'
        foreach ($p in $cfg.neverTouch) {
            if ($installed -notcontains $p) { Say "  $p : not installed"; continue }
            $vm = [regex]::Match(((Sh "dumpsys package $p") -join "`n"), 'versionName=(\S+)')
            $v = if ($vm.Success) { $vm.Groups[1].Value } else { '?' }
            $flag = if ($disabled -contains $p) { '  ** DISABLED **' } else { '' }
            Say "  $p : v$v$flag"
        }
    }
}

# ---- 2. re-apply debloat ----
if ($Debloat -and $cfg) {
    Say '=== DEBLOAT ==='
    $targets = @($cfg.always)
    if ($Alexa) { $targets += @($cfg.alexa) }
    if ($Ota) { $targets += @($cfg.ota) }
    $never = @($cfg.neverTouch)
    $installed = Get-List 'pm list packages'
    $disabled = Get-List 'pm list packages -d'
    $toDisable = @()
    foreach ($p in $targets) {
        if ($never -contains $p) { Say "  skip (never touch): $p"; continue }
        if ($p -in @('com.amazon.tv.oobe', 'com.amazon.ftvads.deeplinking')) { continue }   # Fire OS protects these
        if ($installed -notcontains $p) { continue }
        if ($disabled -contains $p) { continue }
        $toDisable += $p
    }
    $nDone = 0
    if ($toDisable.Count -eq 0) {
        Say 'Nothing to disable: everything on the list is already off or not present.'
    } elseif ($DryRun) {
        foreach ($p in $toDisable) { Say "  would disable: $p" }
        Say "Dry run: $($toDisable.Count) package(s) would be disabled. Nothing was changed."
    } else {
        Write-Host "These $($toDisable.Count) package(s) will be disabled (reversible, nothing is uninstalled):"
        $toDisable | ForEach-Object { Write-Host "  $_" }
        $go = if ($Yes) { 'y' } else { Read-Host 'Disable them now? (y/n)' }
        if ($go -eq 'y') {
            foreach ($p in $toDisable) {
                $r = (Sh "pm disable-user --user 0 $p") -join ' '
                if ($r -match 'Exception|Error|protected') { Say "  $p -> skipped, Fire OS protects it" 'Yellow' }
                else { Say "  disabled: $p"; $nDone++; $changed = $true }
            }
        } else { Say 'Skipped by you. Nothing was changed.' }
        Say "Newly disabled: $nDone package(s)."
    }
    # Safety net: an app on the never-touch list must never stay disabled.
    foreach ($p in $never) {
        if (($installed -contains $p) -and ($disabled -contains $p)) {
            if ($DryRun) { Say "  would re-enable: $p" } else {
                Sh "pm enable $p" | Out-Null
                Say "  RE-ENABLED (was disabled): $p" 'Yellow'; $changed = $true
            }
        }
    }
} elseif ($Debloat) { Say 'Debloat skipped: no list.' 'Yellow' }

# ---- 3. trim caches ----
if ($Trim) {
    Say '=== TRIM CACHES ==='
    $before = Get-FreeMB
    $res = (Sh 'pm trim-caches 999999999999') -join ' '
    Start-Sleep -Seconds 3
    $after = Get-FreeMB
    if ($null -ne $before -and $null -ne $after) {
        Say "Free storage: $before MB -> $after MB (freed $($after - $before) MB)"
    }
    if ($res -match 'Exception|Error|denied|not found') { Say "pm said: $res" 'Yellow' }
    $changed = $true
}

# ---- 3b. let Galaxy Media open itself at startup ----
if ($AutoStart) {
    Say '=== AUTO-START ==='
    $r = (Sh 'appops set com.galaxymedia.tvsetup SYSTEM_ALERT_WINDOW allow') -join ' '
    if ($r -match 'Exception|Error|Unknown|denied') { Say "  could not grant it: $r" 'Yellow' }
    else { Say '  granted: Galaxy Media can open itself after the stick restarts.' }
    $changed = $true
}

# ---- 4. speed ----
if ($Speed) {
    $scale = @{ Off = '0'; Fast = '0.5'; Default = '1.0' }[$Speed]
    foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
        Sh "settings put global $k $scale" | Out-Null
    }
    Say "Animation scale set to $scale ($Speed)."
    $changed = $true
}

# ---- HOME screen test (explicit options only; never part of the default run) ----
$TestPkg = 'com.galaxymedia.tvsetup.hometest'
$AmazonHome = 'com.amazon.tv.launcher'
function Show-Home {
    Say '  Apps that answer the Home button:'
    Sh 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' |
        Where-Object { $_ -match '/' } | ForEach-Object { Say "    $_" }
    $cur = Sh 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' |
        Where-Object { $_ -match '/' } | Select-Object -Last 1
    Say "  The Home button opens now: $cur"
}
if ($HomeCheck) {
    Say '=== HOME CHECK (read only) ==='
    $inst = Get-List 'pm list packages'
    Say ("  HOME TEST app installed: " + $(if ($inst -contains $TestPkg) { 'yes' } else { 'no' }))
    $off = Get-List 'pm list packages -d'
    Say ("  Amazon home screen: " + $(if ($off -contains $AmazonHome) { 'turned off' } else { 'on' }))
    Show-Home
}
if ($HomeLock) {
    Say '=== HOME LOCK (test) ==='
    $inst = Get-List 'pm list packages'
    if ($inst -notcontains $TestPkg) {
        Say "  The HOME TEST app ($TestPkg) is not installed on this stick. Install it first." 'Yellow'
    } else {
        Write-Host "This makes the Home button open Galaxy Media by turning off Amazon's home screen."
        Write-Host 'Undo any time with: .\galaxy-maintain.ps1 -Ip <IP> -HomeUndo   (ADB has to stay on).'
        $go = if ($Yes) { 'y' } else { Read-Host 'Continue? (y/n)' }
        if ($go -eq 'y') {
            $r = (Sh "pm disable-user --user 0 $AmazonHome") -join ' '
            if ($r -match 'Exception|Error|protected|Unknown') {
                Say "  Fire OS refused: $r" 'Yellow'
                Say '  Nothing changed. Press Home on the remote: if a "choose a home app" box appears, pick Galaxy Media (home).'
            } else {
                Say "  Amazon's home screen is turned off."
                Sh 'input keyevent KEYCODE_HOME' | Out-Null
                Start-Sleep -Seconds 3
            }
            Show-Home
        } else { Say '  Skipped by you. Nothing was changed.' }
    }
}
if ($HomeUndo) {
    Say '=== HOME UNDO ==='
    $r = (Sh "pm enable $AmazonHome") -join ' '
    Say "  Amazon home screen: $r"
    Sh "pm clear-package-preferred-activities $TestPkg" | Out-Null
    Show-Home
    Say '  When you are done testing, uninstall the HOME TEST app (Settings > Applications).'
}

# ---- VERIFY: one read-only health check of everything the app and script set up ----
function Chk([bool]$ok, [string]$text, [string]$kind = 'FAIL') {
    if ($ok) { Say "  [PASS] $text" 'Green' }
    elseif ($kind -eq 'WARN') { Say "  [WARN] $text" 'Yellow' }
    else { Say "  [FAIL] $text" 'Red' }
}
function Take-Shot {
    $remote = '/sdcard/galaxy-verify.png'
    Sh "screencap -p $remote" | Out-Null
    $dirS = Join-Path $PSScriptRoot 'reports'
    New-Item -ItemType Directory -Force -Path $dirS | Out-Null
    $local = Join-Path $dirS ("{0}-screen-{1}.png" -f ($model -replace '\W', ''), $stamp)
    & $adb -s $target pull $remote $local 2>&1 | Out-Null
    Sh "rm $remote" | Out-Null
    if (Test-Path $local) { Say "  Screenshot saved: $local" }
    else { Say '  Screenshot failed (Fire OS blocks it on some screens).' 'Yellow' }
}
if ($Verify) {
    Say "=== VERIFY  $model  $Ip  $date ==="
    $pkg = 'com.galaxymedia.tvsetup'
    $installed = Get-List 'pm list packages'
    $disabled = Get-List 'pm list packages -d'
    $vm = [regex]::Match(((Sh "dumpsys package $pkg") -join "`n"), 'versionName=(\S+)')
    Chk ($installed -contains $pkg) ("Galaxy Media installed" + $(if ($vm.Success) { ': v' + $vm.Groups[1].Value } else { '' }))
    # Edit this list if a stick's shelf is different.
    $shelf = [ordered]@{ 'TiviMate' = 'ar.tvplayer.tv'; 'XC7' = 'com.haxapps.xc7'; 'SmartTube' = 'org.smarttube.stable'; 'GalaxyTheater' = 'fyahrebrands.moviebox.galaxytheater'; 'WorldVPN' = 'io.world.vpn' }
    $missing = @()
    foreach ($k in $shelf.Keys) { if ($installed -notcontains $shelf[$k]) { $missing += $k } }
    Chk ($missing.Count -eq 0) ($(if ($missing.Count -eq 0) { 'All 5 shelf apps are installed' } else { 'Not installed yet: ' + ($missing -join ', ') }))
    if ($cfg) {
        $bad = @($cfg.neverTouch | Where-Object { $disabled -contains $_ })
        Chk ($bad.Count -eq 0) ($(if ($bad.Count -eq 0) { 'No protected app is switched off' } else { 'Switched off by mistake: ' + ($bad -join ', ') }))
        $skipFor = @('com.amazon.tv.oobe', 'com.amazon.ftvads.deeplinking')
        $left = @(@($cfg.always) | Where-Object { ($installed -contains $_) -and ($disabled -notcontains $_) -and (@($cfg.neverTouch) -notcontains $_) -and ($skipFor -notcontains $_) })
        Chk ($left.Count -eq 0) ($(if ($left.Count -eq 0) { 'Debloat is complete (nothing left to disable)' } else { "$($left.Count) bloat packages are still on. Run the script with no options." })) 'WARN'
    } else { Say '  [INFO] Debloat check skipped (could not download the list).' }
    $aw = (Sh "appops get $pkg SYSTEM_ALERT_WINDOW") -join ' '
    Chk ($aw -match 'allow') 'Auto-start permission is granted (Galaxy Media opens by itself after a restart)'
    $st = Sh 'settings get global galaxy_last_maintained' | Select-Object -First 1
    $stOk = [bool]($st -and $st -ne 'null')
    Chk $stOk ('Maintenance stamp: ' + $(if ($stOk) { $st } else { 'none yet' })) 'WARN'
    Chk ($disabled -notcontains $AmazonHome) "Amazon's home screen is on (normal setup: the Home button goes to Amazon)" 'WARN'
    $crash = @(Sh 'logcat -d -b crash' | Where-Object { $_ -match 'com\.galaxymedia' })
    Chk ($crash.Count -eq 0) ($(if ($crash.Count -eq 0) { 'No Galaxy Media crashes in the log' } else { "$($crash.Count) crash lines for Galaxy Media. Send me the log." }))
    # The app's own saved settings (private link and settings text are never printed)
    $prefsText = (Sh "run-as $pkg cat shared_prefs/galaxy.xml") -join "`n"
    if ($prefsText -match 'name="') {
        function PStr([string]$k) { $m = [regex]::Match($prefsText, 'name="' + $k + '">([^<]*)<'); if ($m.Success) { $m.Groups[1].Value } else { '' } }
        $nm = PStr 'userName'
        Chk ([bool]$nm) ('Name is set' + $(if ($nm) { ": $nm" } else { '' }))
        Chk ([bool](PStr 'settingsUrl')) 'Settings file link is set'
        Chk ((PStr 'lastSettings').Length -gt 20) 'Settings file loaded (the shelf has data)'
        $wn = PStr 'weatherName'
        Chk ([bool]$wn) ('Weather city is set' + $(if ($wn) { ": $wn" } else { '' })) 'WARN'
        $wp = PStr 'wallpaper'
        Say ("  [INFO] Background: " + $(if ($wp) { $wp } else { 'rotate (default)' }))
    } else { Say '  [INFO] Could not read the app settings (works on debug builds only).' }
    $top = Sh 'dumpsys activity activities' | Where-Object { $_ -match 'mResumedActivity' } | Select-Object -First 1
    Say "  [INFO] On the TV now: $top"
    Say ("  [INFO] Stick has been on for: " + ((Sh 'uptime') -join ' '))
    Take-Shot
}
if ($Screenshot -and -not $Verify) { Say '=== SCREENSHOT ==='; Take-Shot }

# ---- helpers for the measuring options ----
function Stat($vals) {
    $v = @($vals | Where-Object { $null -ne $_ })
    if ($v.Count -eq 0) { return 'n/a' }
    $m = $v | Measure-Object -Average -Minimum -Maximum
    return ("avg {0:N1}  (min {1:N1}, max {2:N1}, runs {3})" -f $m.Average, $m.Minimum, $m.Maximum, $v.Count)
}
function Get-Launch([string]$p) {
    foreach ($cat in 'android.intent.category.LEANBACK_LAUNCHER', 'android.intent.category.LAUNCHER') {
        $r = Sh "cmd package resolve-activity --brief -a android.intent.action.MAIN -c $cat -p $p" | Where-Object { $_ -match '/' } | Select-Object -Last 1
        if ($r) { return $r }
    }
    return $null
}
$ShelfApps = [ordered]@{ 'TiviMate' = 'ar.tvplayer.tv'; 'XC7' = 'com.haxapps.xc7'; 'SmartTube' = 'org.smarttube.stable'; 'GalaxyTheater' = 'fyahrebrands.moviebox.galaxytheater'; 'WorldVPN' = 'io.world.vpn' }
$GmPkg = 'com.galaxymedia.tvsetup'

# ---- AUDIT: read-only snapshot of how the stick is doing ----
if ($Audit) {
    Say ("=== AUDIT (read-only)  $model  $Ip  $date " + (Get-Date -Format 'HH:mm') + ' ===')
    Say ('Android ' + (Sh 'getprop ro.build.version.release' | Select-Object -First 1) + '   Build ' + (Sh 'getprop ro.build.display.id' | Select-Object -First 1))
    Say ('Uptime: ' + ((Sh 'uptime') -join ' '))
    $mi = (Sh 'cat /proc/meminfo') -join "`n"
    function MemMb([string]$name) { $m = [regex]::Match($mi, "${name}:\s+(\d+)"); if ($m.Success) { [math]::Round([int64]$m.Groups[1].Value / 1024) } else { 'n/a' } }
    Say ("Memory: total {0} MB, available {1} MB, swap free {2} MB" -f (MemMb 'MemTotal'), (MemMb 'MemAvailable'), (MemMb 'SwapFree'))
    $mem = @(Sh 'dumpsys meminfo')
    $start = -1
    for ($k = 0; $k -lt $mem.Count; $k++) { if ($mem[$k] -like 'Total PSS by process:*') { $start = $k; break } }
    if ($start -ge 0) {
        Say 'Biggest memory users:'
        $end = [math]::Min($start + 9, $mem.Count - 1)
        $mem[($start + 1)..$end] | ForEach-Object { Say "  $_" }
    }
    $free = Get-FreeMB
    if ($null -ne $free) { Say "Free storage: $free MB" }
    # heat
    $th = @(Sh 'dumpsys thermalservice')
    $temps = @($th | Where-Object { $_ -match 'Temperature\{mValue=' })
    if ($temps.Count -gt 0) { Say 'Temperatures (thermal service):'; $temps | Select-Object -First 8 | ForEach-Object { Say "  $_" } }
    else { Say 'Temperatures: the thermal service reports none' 'Yellow' }
    $ts = $th | Where-Object { $_ -match 'Thermal Status' } | Select-Object -First 1
    if ($ts) { Say "  $ts" }
    $zones = @(Sh 'for z in /sys/class/thermal/thermal_zone*; do echo $(cat $z/type) $(cat $z/temp); done' | Where-Object { $_ -match '\d' })
    if ($zones.Count -gt 0) { Say 'Chip sensors (type, raw temp):'; $zones | Select-Object -First 8 | ForEach-Object { Say "  $_" } }
    # Wi-Fi
    $wf = (Sh 'dumpsys wifi') -join "`n"
    $wm = [regex]::Match($wf, 'mWifiInfo[^\n]*')
    if ($wm.Success) {
        $rssi = [regex]::Match($wm.Value, 'RSSI: (-?\d+)').Groups[1].Value
        $ls = [regex]::Match($wm.Value, 'Link speed: (\d+)').Groups[1].Value
        $fq = [regex]::Match($wm.Value, 'Frequency: (\d+)').Groups[1].Value
        $band = if ([int]$fq -ge 4900) { '5 GHz' } elseif ([int]$fq -gt 0) { '2.4 GHz' } else { '?' }
        Say "Wi-Fi: signal $rssi dBm (closer to 0 is stronger), link speed $ls Mbps, $fq MHz ($band)"
    } else { Say 'Wi-Fi: not found (wired connection, or the format differs)' 'Yellow' }
    # animations
    Say ('Animation scales: window ' + (Sh 'settings get global window_animation_scale' | Select-Object -First 1) + ', transition ' + (Sh 'settings get global transition_animation_scale' | Select-Object -First 1) + ', animator ' + (Sh 'settings get global animator_duration_scale' | Select-Object -First 1))
    # Galaxy Media itself
    $gm = (Sh "dumpsys meminfo $GmPkg") -join "`n"
    $gmm = [regex]::Match($gm, 'TOTAL\s+(\d+)')
    if ($gmm.Success) { Say ("Galaxy Media uses about {0} MB of memory" -f [math]::Round([int64]$gmm.Groups[1].Value / 1024)) }
    $gf = (Sh "dumpsys gfxinfo $GmPkg") -join "`n"
    $tot = [regex]::Match($gf, 'Total frames rendered: (\d+)').Groups[1].Value
    $jk = [regex]::Match($gf, 'Janky frames: (\d+) \(([\d.]+)%\)')
    if ($tot) { Say ("Galaxy Media frames drawn since it started: $tot, janky: " + $(if ($jk.Success) { $jk.Groups[1].Value + ' (' + $jk.Groups[2].Value + '%)' } else { 'n/a' })) }
    # what is switched on and off (this is also your undo snapshot)
    $installed = Get-List 'pm list packages'
    $disabled = Get-List 'pm list packages -d'
    Say ("Packages: {0} installed, {1} switched off" -f $installed.Count, $disabled.Count)
    Say 'Switched off right now (keep this list as an undo snapshot):'
    $disabled | ForEach-Object { Say "  $_" }
}

# ---- BENCHMARK: launch times and smoothness, 3 runs each ----
if ($Benchmark) {
    Say "=== BENCHMARK  $model  $Ip  $date ==="
    Say 'About 3 minutes. Do not touch the remote while it runs.' 'Yellow'
    $installed = Get-List 'pm list packages'
    foreach ($name in $ShelfApps.Keys) {
        $p = $ShelfApps[$name]
        if ($installed -notcontains $p) { Say "  ${name}: not installed"; continue }
        $comp = Get-Launch $p
        if (-not $comp) { Say "  ${name}: no launch screen found"; continue }
        $times = @()
        for ($i = 1; $i -le 3; $i++) {
            Sh "am force-stop $p" | Out-Null
            Start-Sleep -Seconds 2
            $o = (Sh "am start -W -n $comp") -join ' '
            $m = [regex]::Match($o, 'TotalTime:\s*(\d+)')
            if ($m.Success) { $times += [double]$m.Groups[1].Value }
            Start-Sleep -Seconds 4
            Sh 'input keyevent KEYCODE_HOME' | Out-Null
            Start-Sleep -Seconds 2
        }
        Say ("  {0}: starts in ms  {1}" -f $name, (Stat $times))
        Sh "am force-stop $p" | Out-Null
    }
    $gcomp = Get-Launch $GmPkg
    if ($gcomp) {
        $jank = @(); $pss = @()
        for ($i = 1; $i -le 3; $i++) {
            Sh "am start -n $gcomp" | Out-Null
            Start-Sleep -Seconds 4
            Sh "dumpsys gfxinfo $GmPkg reset" | Out-Null
            Start-Sleep -Seconds 20
            $gf = (Sh "dumpsys gfxinfo $GmPkg") -join "`n"
            $jk = [regex]::Match($gf, 'Janky frames: \d+ \(([\d.]+)%\)')
            if ($jk.Success) { $jank += [double]$jk.Groups[1].Value }
            $gm = (Sh "dumpsys meminfo $GmPkg") -join "`n"
            $gmm = [regex]::Match($gm, 'TOTAL\s+(\d+)')
            if ($gmm.Success) { $pss += [math]::Round([int64]$gmm.Groups[1].Value / 1024) }
        }
        Say ("  Galaxy Media janky frames, % over 20 s:  " + (Stat $jank) + '   (under about 5 is smooth)')
        Say ("  Galaxy Media memory, MB:  " + (Stat $pss))
    }
    $mi = (Sh 'cat /proc/meminfo') -join "`n"
    $am = [regex]::Match($mi, 'MemAvailable:\s+(\d+)')
    if ($am.Success) { Say ("  Free memory at the end: {0} MB" -f [math]::Round([int64]$am.Groups[1].Value / 1024)) }
}

# ---- UNDEBLOAT: turn the bloat back on, so before/after can be measured on the same stick ----
if ($Undebloat) {
    Say '=== UNDEBLOAT (turns the switched-off Amazon packages back on) ==='
    if (-not $cfg) { Say '  Needs the debloat list from GitHub. Is the laptop online?' 'Yellow' }
    else {
        $all = @($cfg.always) + @($cfg.alexa) + @($cfg.ota)
        $disabled = Get-List 'pm list packages -d'
        $on = @($all | Where-Object { $disabled -contains $_ })
        if ($on.Count -eq 0) { Say '  Nothing to turn back on.' }
        else {
            Write-Host "These $($on.Count) package(s) will be turned back on:"
            $on | ForEach-Object { Write-Host "  $_" }
            $go = if ($Yes) { 'y' } else { Read-Host 'Turn them back on? (y/n)' }
            if ($go -eq 'y') {
                foreach ($p in $on) { Sh "pm enable $p" | Out-Null; Say "  enabled: $p" }
                Say '  Restart the stick before measuring. Run -Debloat again afterward to switch them off.'
            } else { Say '  Skipped by you.' }
        }
    }
}

# ---- DUMP: everything needed to build a debloat list for a new kind of box ----
if ($Dump) {
    Say "=== DUMP  $model  $Ip  $date ==="
    foreach ($k in 'ro.product.manufacturer', 'ro.product.model', 'ro.product.device', 'ro.product.name', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.fingerprint', 'ro.product.cpu.abi') {
        Say ("  {0} = {1}" -f $k, (Sh "getprop $k" | Select-Object -First 1))
    }
    $mi = (Sh 'cat /proc/meminfo') -join "`n"
    $tm = [regex]::Match($mi, 'MemTotal:\s+(\d+)')
    if ($tm.Success) { Say ("  total memory = {0} MB" -f [math]::Round([int64]$tm.Groups[1].Value / 1024)) }
    Say 'Apps that answer the Home button:'
    Sh 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Where-Object { $_ -match '/' } | ForEach-Object { Say "  $_" }
    Say 'Apps shown in the launcher (TV):'
    Sh 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER' | Where-Object { $_ -match '/' } | ForEach-Object { Say "  $_" }
    Say 'ALL packages (path and user id):'
    Sh 'pm list packages -f -U' | ForEach-Object { Say "  $_" }
    Say 'System packages:'
    Get-List 'pm list packages -s' | ForEach-Object { Say "  $_" }
    Say 'Installed by you (not system):'
    Get-List 'pm list packages -3' | ForEach-Object { Say "  $_" }
    Say 'Switched off now:'
    Get-List 'pm list packages -d' | ForEach-Object { Say "  $_" }
    Say 'Upload the saved report file from the reports folder.'
}

# ---- INSTALL APPS: straight from the laptop, no prompts on the TV ----
if ($InstallApps) {
    Say '=== INSTALL APPS (installed over ADB, nothing to press on the TV) ==='
    if (-not $Gist) {
        Say '  Add the settings code: -Gist <the 32-character code, or the full settings link>' 'Yellow'
    } else {
        $setUrl = if ($Gist -match '^https?://') { $Gist } else { "https://gist.githubusercontent.com/galaxystreamingservice-droid/$($Gist.ToLower())/raw/device-settings.json" }
        $set = $null
        try { $set = (Invoke-WebRequest -UseBasicParsing -ErrorAction Stop $setUrl).Content | ConvertFrom-Json }
        catch { Say '  Could not read the settings file. Check the code and the laptop internet.' 'Yellow' }
        if ($set) {
            $installedNow = Get-List 'pm list packages'
            $tmpDir = Join-Path $env:TEMP 'galaxy-apks'
            New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
            foreach ($a in @($set.apps)) {
                $nm = [string]$a.name
                if ($a.managedByStore -or -not $a.apkUrl -or ([string]$a.apkUrl) -like 'REPLACE*') { Say "  ${nm}: skipped (updates through its own store, or no link in the settings file)"; continue }
                if ($installedNow -contains [string]$a.package) { Say "  ${nm}: already installed"; continue }
                $file = Join-Path $tmpDir (([string]$a.package) + '.apk')
                Say "  ${nm}: downloading ..."
                try { Invoke-WebRequest -UseBasicParsing -ErrorAction Stop ([string]$a.apkUrl) -OutFile $file }
                catch { Say "  ${nm}: download failed" 'Yellow'; continue }
                Say "  ${nm}: installing ..."
                $r = (& $adb -s $target install -r $file 2>&1 | Out-String).Trim()
                if ($r -match 'Success') { Say "  ${nm}: installed" 'Green' } else { Say "  ${nm}: failed ($r)" 'Yellow' }
            }
            Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
            Say '  Done. Run -Verify to check the whole stick.'
        }
    }
}

# ---- stamp + save ----
if ($changed -and -not $DryRun) { Sh "settings put global galaxy_last_maintained $date" | Out-Null }

$dir = Join-Path $PSScriptRoot 'reports'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$file = Join-Path $dir ("{0}-{1}-{2}.txt" -f ($model -replace '\W', ''), ($Ip -replace '\.', '-'), $stamp)
$out | Set-Content -Path $file -Encoding UTF8
Write-Host "Saved: $file"

# ---- 5. reboot ----
if ($Reboot) {
    $ans = Read-Host 'Reboot the stick now? (y/n)'
    if ($ans -eq 'y') { & $adb -s $target reboot; Write-Host 'Rebooting.' }
}
