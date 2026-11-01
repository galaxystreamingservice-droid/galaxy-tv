<#
Galaxy Media maintenance. Run from a laptop on the same Wi-Fi as the stick.
Nothing is ever uninstalled. Every step except -Reboot can be undone.

  .\galaxy-maintain.ps1 -Ip 192.168.1.172              report + re-apply debloat + trim caches
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Report      report only (read-only)
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Debloat -DryRun   show what would be disabled
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Speed Fast  animations at 0.5x (Off, Fast, Default)
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -AutoStart   let Galaxy Media open itself after a restart
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Reboot      reboot the stick at the end

-Yes skips the confirmation question before disabling packages.
Add -Alexa and/or -Ota to -Debloat if that stick's settings file turns those groups on.
Stick IP: Settings > My Fire TV > About > Network.
#>
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
    [switch]$Reboot
)

$ErrorActionPreference = 'Continue'   # adb writes status text to stderr; don't treat that as an error
$ListUrl = 'https://raw.githubusercontent.com/galaxystreamingservice-droid/galaxy-tv/main/app/src/main/assets/debloat_fireos.json'

# No action chosen = the safe default set.
if (-not ($Report -or $Debloat -or $Trim -or $AutoStart -or [bool]$Speed -or $Reboot)) {
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
$target = "${Ip}:5555"

function Sh([string]$cmd) {
    @(& $adb -s $target shell $cmd 2>&1 | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}

# ---- connect ----
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
