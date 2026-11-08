<#
Galaxy Media maintenance. Run from a laptop on the same Wi-Fi as the stick.
Nothing is ever uninstalled. Every step except -Reboot can be undone.
A bare run (no switches) on a Fire TV does: report, debloat, trim caches, auto-start. On any other box it only reports.

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
  .\galaxy-maintain.ps1 -Ip 192.168.1.50 -Pair 192.168.1.50:41234   pair first (it asks for the 6-digit code); on its own it changes nothing
  .\galaxy-maintain.ps1 -Ip 192.168.1.50 -Port 37123 -Dump    save the full package list to upload for a new box

Debloat speed test (restart, idle, measure, one command per run):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Cycle -Label bloat1   restart, wait 10 minutes, then Audit + Benchmark (use bloat1, bloat2, bloat3)
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Cycle -Label clean1   same after the debloat (clean1, clean2, clean3)
  .\galaxy-maintain.ps1 -Summarize                                compare all labeled reports in the reports folder (no stick needed)

Install the shelf apps with no taps on the TV (for sticks you set up yourself):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -InstallApps -Gist <the 32-character settings code>

Check everything worked (read-only, saves a screenshot of the TV):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Verify     PASS / WARN / FAIL list for the whole setup
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Screenshot just save a picture of what the TV shows

Set up a whole stick from the laptop (needs ADB debugging on and the Allow prompt accepted; nothing to press on the TV):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Setup -PersonName "Carlos" -ZipCode 76101 -Gist GAL-4821
  It installs the newest Galaxy Media, fills in the name, weather city and settings link, installs the shelf apps,
  then runs the normal setup (debloat on Fire sticks, trim caches, auto-start) and a Verify.
Record the TV screen on the laptop (clean picture, no TV frame; saved in 170-second parts):
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -Record -Seconds 600
  Open a second PowerShell window for the other commands while it records. Ctrl+C stops early.

Remove every app you installed except the ones you keep (it lists them and asks first; their sign-ins and data are lost):
  .\galaxy-maintain.ps1 -Ip 192.168.1.165 -RemoveApps -DryRun              list only: do this first
  .\galaxy-maintain.ps1 -Ip 192.168.1.165 -RemoveApps                      then remove (type y)
  .\galaxy-maintain.ps1 -Ip 192.168.1.165 -RemoveApps -Keep com.a.b,com.c.d   also keep these
  Always kept: Galaxy Media, TiviMate, XC7, GalaxyTheater, AppLinked, WorldVPN (both names), SmartTube, Termux, F-Droid, Downloader.
  Google and Android system apps are never touched.

Home button: NOT SUPPORTED on Fire OS (tested on a Fire TV Stick 4K, Fire OS 8). Amazon's launcher is a protected
package and Fire OS ignores a chosen home app, so -HomeProbe, -HomeLock, -HomeTest and -InstallHomeTest only
explain this and change nothing. Still useful:
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -HomeCheck   read-only: who answers the Home button right now
  .\galaxy-maintain.ps1 -Ip 192.168.1.172 -HomeUndo    make sure Amazon's home screen is on and is the default

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
    [switch]$Cycle,
    [string]$Label,
    [int]$IdleMinutes = 10,
    [switch]$Summarize,
    [switch]$RemoveApps,
    [switch]$Setup,
    [string]$PersonName,
    [string]$ZipCode,
    [switch]$Record,
    [int]$Seconds = 0,
    [string[]]$Keep,
    [switch]$HomeProbe,
    [switch]$HomeTest,
    [switch]$InstallHomeTest,
    [string]$Gist,
    [int]$Port = 5555,
    [string]$Pair
)

$ScriptVersion = 'v0.12.14 (2026-11-08)'
Write-Host "galaxy-maintain $ScriptVersion" -ForegroundColor DarkGray
$ErrorActionPreference = 'Continue'   # adb writes status text to stderr; don't treat that as an error
$ListUrl = 'https://raw.githubusercontent.com/galaxystreamingservice-droid/galaxy-tv/main/app/src/main/assets/debloat_fireos.json'

# No action chosen = the safe default set.
$anyAction = ($Report -or $Debloat -or $Trim -or $AutoStart -or [bool]$Speed -or $Reboot -or $HomeCheck -or $HomeLock -or $HomeUndo -or $Verify -or $Screenshot -or $Audit -or $Benchmark -or $Undebloat -or $Dump -or $InstallApps -or $Cycle -or $Summarize -or $RemoveApps -or $Setup -or $Record -or $HomeProbe -or $HomeTest -or $InstallHomeTest)
$defaultRun = $false
if (-not $anyAction -and -not $Pair) {
    $Report = $true; $Debloat = $true; $Trim = $true; $AutoStart = $true; $defaultRun = $true
} elseif (-not $anyAction -and $Pair) {
    $Report = $true      # -Pair on its own: pair, connect, and only look (read-only)
}

# ---- SUMMARIZE: compare the labeled cycle reports (no stick needed) ----
if ($Summarize) {
    $dirS = Join-Path $PSScriptRoot 'reports'
    $groups = @{}
    foreach ($f in @(Get-ChildItem -Path $dirS -Filter '*.txt' -ErrorAction SilentlyContinue)) {
        $m = [regex]::Match($f.Name, '-([A-Za-z]+)\d+\.txt$')
        if (-not $m.Success) { continue }
        $lab = $m.Groups[1].Value.ToLower()
        if (-not $groups.ContainsKey($lab)) { $groups[$lab] = @() }
        $groups[$lab] += (Get-Content -Raw $f.FullName)
    }
    $labs = @($groups.Keys | Sort-Object)
    if ($labs.Count -eq 0) { Write-Host 'No labeled reports found. Run -Cycle -Label bloat1 (and so on) first.' -ForegroundColor Yellow; exit 0 }
    function Pull([string]$text, [string]$pattern) {
        $m = [regex]::Match($text, $pattern)
        if ($m.Success) { return [double]($m.Groups[1].Value -replace ',', '') } else { return $null }
    }
    $metrics = [ordered]@{
        'Free memory before, MB'   = 'available (\d+) MB'
        'Load average (1 min)'     = 'load average: ([\d.]+)'
        'TiviMate start, ms'       = 'TiviMate: starts in ms\s+avg ([\d,.]+)'
        'XC7 start, ms'            = 'XC7: starts in ms\s+avg ([\d,.]+)'
        'SmartTube start, ms'      = 'SmartTube: starts in ms\s+avg ([\d,.]+)'
        'GalaxyTheater start, ms'  = 'GalaxyTheater: starts in ms\s+avg ([\d,.]+)'
        'WorldVPN start, ms'       = 'WorldVPN: starts in ms\s+avg ([\d,.]+)'
        'Galaxy Media jank, %'     = 'janky frames, % over 20 s:\s+avg ([\d.]+)'
        'Free memory after, MB'    = 'Free memory at the end: (\d+) MB'
    }
    $lines = @()
    $lines += ('Metric'.PadRight(26) + (($labs | ForEach-Object { (($_ + ' (' + @($groups[$_]).Count + ' runs)').PadRight(30)) }) -join ''))
    foreach ($k in $metrics.Keys) {
        $row = $k.PadRight(26)
        foreach ($l in $labs) {
            $vals = @(@($groups[$l]) | ForEach-Object { Pull $_ $metrics[$k] } | Where-Object { $null -ne $_ })
            if ($vals.Count -eq 0) { $row += 'n/a'.PadRight(30) }
            else {
                $mm = $vals | Measure-Object -Average -Minimum -Maximum
                $row += (("{0:N1}  ({1:N1} to {2:N1})" -f $mm.Average, $mm.Minimum, $mm.Maximum).PadRight(30))
            }
        }
        $lines += $row
    }
    $lines | ForEach-Object { Write-Host $_ }
    $outFile = Join-Path $dirS ('summary-' + (Get-Date -Format 'yyyy-MM-dd_HHmm') + '.txt')
    $lines | Set-Content -Path $outFile -Encoding UTF8
    Write-Host "Saved: $outFile"
    Write-Host 'Each cell is the average over the runs, with the lowest and highest run in brackets. If two labels overlap, the difference is within normal noise.'
    exit 0
}

# ---- the home-lock options do not work on Fire OS: say so and stop, changing nothing ----
if ($HomeLock -or $HomeTest -or $HomeProbe -or $InstallHomeTest) {
    Write-Host "The Home-button lock-in does not work on Fire OS. Amazon's launcher is a protected package that Fire OS will not switch off," -ForegroundColor Yellow
    Write-Host 'and Fire OS ignores a home app chosen by command. This was tested on a Fire TV Stick 4K (Fire OS 8). Nothing was changed.' -ForegroundColor Yellow
    Write-Host 'Instead: Galaxy Media opens by itself after a restart, and sits first in Amazon''s app row.'
    exit 0
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
$maker = (Sh 'getprop ro.product.manufacturer' | Select-Object -First 1)
if ($defaultRun -and $maker -and ($maker -notmatch 'Amazon')) {
    Write-Host "This is not a Fire TV ($maker $model). The debloat list is for Fire OS, so only the read-only report runs. Use -Audit or -Dump." -ForegroundColor Yellow
    $Debloat = $false; $Trim = $false; $AutoStart = $false
}
$date = Get-Date -Format 'yyyy-MM-dd'
$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
$changed = $false

# ---- helpers for -Setup and -InstallApps ----
function Resolve-SettingsUrl([string]$g) {
    $g = $g.Trim()
    if ($g -match '^https?://') { return $g }
    if ($g -match '^[0-9a-fA-F]{32}$') { return "https://gist.githubusercontent.com/galaxystreamingservice-droid/$($g.ToLower())/raw/device-settings.json" }
    $clean = ($g.ToUpper() -replace '[^A-Z0-9]', '')
    $key = if ($clean.StartsWith('GAL') -and $clean.Length -gt 3) { 'GAL-' + $clean.Substring(3) } else { $clean }
    $codes = $null
    try { $codes = (Invoke-WebRequest -UseBasicParsing -ErrorAction Stop 'https://gist.githubusercontent.com/galaxystreamingservice-droid/4f0ff093b9423982b318fb21320b1c70/raw/setup-codes.json').Content | ConvertFrom-Json }
    catch { return $null }
    $val = $null
    foreach ($prop in $codes.PSObject.Properties) { if ($prop.Name.Trim().ToUpper() -eq $key) { $val = [string]$prop.Value } }
    if (-not $val) { return $null }
    if ($val -match '^https?://') { return $val }
    if ($val -match '^[0-9a-fA-F]{32}$') { return "https://gist.githubusercontent.com/galaxystreamingservice-droid/$($val.ToLower())/raw/device-settings.json" }
    return $null
}

function Install-ShelfApps([string]$setUrl) {
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
    }
}

# ---- RECORD: record the TV screen on the laptop (no TV frame, no phone camera) ----
if ($Record) {
    $total = if ($Seconds -gt 0) { $Seconds } else { 300 }
    $recDir = Join-Path $PSScriptRoot 'reports\recordings'
    New-Item -ItemType Directory -Force -Path $recDir | Out-Null
    $recStamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
    Write-Host "=== RECORD: $total seconds of the TV picture, saved in parts of up to 170 seconds ===" -ForegroundColor Cyan
    Write-Host '  Do your steps now (remote, or commands in a second window). Ctrl+C stops early.' -ForegroundColor Yellow
    Write-Host '  There is no sound, and a second or two is lost between parts. Protected video shows black.' -ForegroundColor DarkGray
    $started = Get-Date
    $part = 1
    while ($true) {
        $left = [int]($total - ((Get-Date) - $started).TotalSeconds)
        if ($left -lt 3) { break }
        $len = [Math]::Min(170, $left)
        $remote = "/sdcard/galaxy-rec-$part.mp4"
        & $adb -s $target shell "screenrecord --time-limit $len --bit-rate 8000000 $remote" 2>&1 | Out-Null
        $local = Join-Path $recDir ("$recStamp-part$part.mp4")
        & $adb -s $target pull $remote $local 2>&1 | Out-Null
        & $adb -s $target shell "rm $remote" 2>&1 | Out-Null
        if (Test-Path $local) { Write-Host "  saved: $local" -ForegroundColor Green }
        else { Write-Host '  nothing was saved. This stick may not allow screen recording.' -ForegroundColor Yellow; break }
        $part++
    }
    Write-Host "  Done. Recordings are in: $recDir"
    exit 0
}

# ---- SETUP: set up a whole stick from the laptop ----
if ($Setup) {
    Say '=== SETUP (the laptop does the setup; nothing to press on the TV) ==='
    if (-not $PersonName -or -not $ZipCode -or -not $Gist) {
        Say '  Add all three: -PersonName "Name" -ZipCode 12345 -Gist GAL-4821' 'Yellow'
        exit 1
    }
    $setUrl = Resolve-SettingsUrl $Gist
    if (-not $setUrl) { Say '  Could not turn that code into a settings link. Check the code and the laptop internet.' 'Yellow'; exit 1 }
    $cityName = ''; $cityLat = ''; $cityLon = ''
    try {
        $zr = Invoke-RestMethod -UseBasicParsing -ErrorAction Stop "https://api.zippopotam.us/us/$ZipCode"
        $place = $zr.places[0]
        $cityName = ([string]$place.'place name') + ', ' + ([string]$place.'state abbreviation')
        $cityLat = [string]$place.latitude
        $cityLon = [string]$place.longitude
        Say "  Weather city: $cityName"
    } catch { Say '  Could not look up that ZIP code, so the weather line stays off (you can set it in Settings).' 'Yellow' }
    Write-Host "This installs the newest Galaxy Media, saves the name '$PersonName' and the settings link in it, installs the shelf apps,"
    Write-Host 'then runs the normal setup. Anything already saved in Galaxy Media on this stick is replaced.'
    $go = if ($Yes) { 'y' } else { Read-Host 'Continue? (y/n)' }
    if ($go -ne 'y') { Say '  Skipped by you. Nothing was changed.'; exit 0 }

    # 1. install the newest Galaxy Media
    $apk = Join-Path $env:TEMP 'galaxy-setup-latest.apk'
    try { Invoke-WebRequest -UseBasicParsing -ErrorAction Stop 'https://github.com/galaxystreamingservice-droid/galaxy-tv/releases/latest/download/galaxy-setup.apk' -OutFile $apk }
    catch { Say '  Could not download Galaxy Media from GitHub.' 'Yellow'; exit 1 }
    $ir = (& $adb -s $target install -r $apk 2>&1 | Out-String).Trim()
    Remove-Item $apk -ErrorAction SilentlyContinue
    if ($ir -match 'Success') { Say '  Galaxy Media installed.' 'Green' } else { Say "  Galaxy Media install failed: $ir" 'Yellow'; exit 1 }

    # 2. fill in what the first-run wizard would have saved
    Sh 'am force-stop com.galaxymedia.tvsetup' | Out-Null
    $esc = { param($t) [System.Security.SecurityElement]::Escape([string]$t) }
    $xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>`n<map>`n" +
           "    <string name=`"settingsUrl`">" + (& $esc $setUrl) + "</string>`n" +
           "    <string name=`"userName`">" + (& $esc $PersonName) + "</string>`n" +
           "    <boolean name=`"namePrompted`" value=`"true`" />`n"
    if ($cityName) {
        $xml += "    <string name=`"weatherName`">" + (& $esc $cityName) + "</string>`n" +
                "    <string name=`"weatherLat`">" + (& $esc $cityLat) + "</string>`n" +
                "    <string name=`"weatherLon`">" + (& $esc $cityLon) + "</string>`n"
    }
    $xml += "</map>`n"
    $tmpXml = Join-Path $env:TEMP 'galaxy-prefs.xml'
    [System.IO.File]::WriteAllText($tmpXml, $xml, (New-Object System.Text.UTF8Encoding($false)))
    & $adb -s $target push $tmpXml /data/local/tmp/galaxy-prefs.xml 2>&1 | Out-Null
    Remove-Item $tmpXml -ErrorAction SilentlyContinue
    Sh 'run-as com.galaxymedia.tvsetup mkdir -p shared_prefs' | Out-Null
    $wr = (Sh 'run-as com.galaxymedia.tvsetup cp /data/local/tmp/galaxy-prefs.xml shared_prefs/galaxy.xml') -join ' '
    Sh 'rm /data/local/tmp/galaxy-prefs.xml' | Out-Null
    if ($wr -match 'not debuggable|Permission denied|Unknown package|No such file|error') {
        Say "  Could not save the name and settings link ($wr). Open Galaxy Media on the TV and use its first-run steps instead." 'Yellow'
    } else {
        Say "  Saved the name, weather city and settings link." 'Green'
    }

    # 3. open it, install the shelf apps, then the normal setup and a check
    Sh 'monkey -p com.galaxymedia.tvsetup -c android.intent.category.LEANBACK_LAUNCHER 1' | Out-Null
    Say '=== INSTALL APPS ==='
    Install-ShelfApps $setUrl
    $Report = $true; $AutoStart = $true; $Verify = $true
    if ($maker -match 'Amazon') { $Debloat = $true; $Trim = $true }
}

# ---- CYCLE: restart, stay idle, then measure (for before/after tests) ----
if ($Cycle) {
    if (-not $Label) { Say '  Give the run a label, for example -Label bloat1' 'Yellow'; exit 1 }
    Say "=== CYCLE $Label : restart, $IdleMinutes minutes idle, then Audit + Benchmark ==="
    Say '  Do not touch the remote and do not open any apps until it finishes.' 'Yellow'
    & $adb -s $target reboot | Out-Null
    Start-Sleep -Seconds 60
    $back = $false
    for ($i = 0; $i -lt 30; $i++) {
        & $adb connect $target 2>&1 | Out-Null
        $st = (& $adb -s $target get-state 2>&1 | Out-String).Trim()
        if ($st -eq 'device') { $back = $true; break }
        Start-Sleep -Seconds 10
    }
    if (-not $back) { Say '  The stick did not come back on ADB within 6 minutes. Check it and run again.' 'Red'; exit 1 }
    for ($m = $IdleMinutes; $m -gt 0; $m--) { Write-Host "  idle: $m minute(s) left ..."; Start-Sleep -Seconds 60 }
    $Audit = $true
    $Benchmark = $true
}

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
    Sh 'cmd package set-home-activity com.amazon.tv.launcher/.ui.HomeActivity_vNext' | Out-Null
    Show-Home
    Say '  If the HOME TEST app is still installed, remove it: adb uninstall com.galaxymedia.tvsetup.hometest'
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
    $resGen = (Sh 'cmd package resolve-activity --brief -a android.settings.SETTINGS') -join ' '
    Chk ($resGen -notmatch 'Dummy|CTS|compliance') 'Generic Settings shortcut opens a real screen (Galaxy Media uses its own safe fallback either way)' 'WARN'
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
        Say '  Add the settings code: -Gist GAL-4821 (or the 32-character code, or the full settings link)' 'Yellow'
    } else {
        $setUrlIA = Resolve-SettingsUrl $Gist
        if (-not $setUrlIA) { Say '  Could not turn that code into a settings link. Check the code and the laptop internet.' 'Yellow' }
        else { Install-ShelfApps $setUrlIA; Say '  Done. Run -Verify to check the whole stick.' }
    }
}

# ---- HOME LOCK TEST, driven from the laptop ----
function Top-Activity {
    $t = Sh 'dumpsys activity activities' | Where-Object { $_ -match 'mResumedActivity' } | Select-Object -First 1
    return [string]$t
}
if ($HomeProbe) {
    Say "=== HOME PROBE (can Fire OS turn off Amazon's home screen? It is switched back on right away) ==="
    Say '  Keep an app in front on the TV (Galaxy Media is fine), not Amazon''s home screen, while this runs.' 'Yellow'
    $r = (Sh "pm disable-user --user 0 $AmazonHome") -join ' '
    if ($r -match 'Exception|Error|protected|Unknown') {
        Say "  Fire OS refused: $r" 'Yellow'
        Say '  So a Home-button lock-in is not possible on this stick. Nothing was changed.'
    } else {
        Start-Sleep -Seconds 3
        Sh "pm enable $AmazonHome" | Out-Null
        $offNow = Get-List 'pm list packages -d'
        if ($offNow -contains $AmazonHome) { Sh "pm enable $AmazonHome" | Out-Null; Start-Sleep -Seconds 1; $offNow = Get-List 'pm list packages -d' }
        if ($offNow -contains $AmazonHome) { Say "  Fire OS allowed it, but Amazon's home screen is STILL OFF. Run -HomeUndo now." 'Red' }
        else { Say "  Fire OS allows it, and Amazon's home screen is back on. A lock-in is possible on this stick." 'Green' }
    }
}
if ($InstallHomeTest) {
    Say '=== INSTALL HOME TEST APP (over ADB) ==='
    try {
        $rels = Invoke-RestMethod -UseBasicParsing -ErrorAction Stop -Headers @{ 'User-Agent' = 'galaxy-maintain' } 'https://api.github.com/repos/galaxystreamingservice-droid/galaxy-tv/releases'
        $rel = @($rels | Where-Object { $_.prerelease -and $_.tag_name -like 'home-test-*' }) | Select-Object -First 1
        $asset = if ($rel) { @($rel.assets | Where-Object { $_.name -like '*.apk' }) | Select-Object -First 1 } else { $null }
        if (-not $asset) { Say '  No HOME TEST pre-release found. On GitHub, run Actions > Build HOME test APK first.' 'Yellow' }
        else {
            $apk = Join-Path $env:TEMP 'galaxy-home-test.apk'
            Say ("  Downloading " + $rel.tag_name + " ...")
            Invoke-WebRequest -UseBasicParsing -ErrorAction Stop $asset.browser_download_url -OutFile $apk
            $r = (& $adb -s $target install -r $apk 2>&1 | Out-String).Trim()
            Remove-Item $apk -ErrorAction SilentlyContinue
            if ($r -match 'Success') { Say '  Installed: Galaxy Media HOME TEST. Open it once on the TV and do the wizard.' 'Green' } else { Say "  Install failed: $r" 'Yellow' }
        }
    } catch { Say '  Could not reach GitHub from the laptop.' 'Yellow' }
}
if ($HomeTest) {
    Say '=== HOME TEST (lock, press Home from ADB, check, then undo or keep) ==='
    $inst = Get-List 'pm list packages'
    if ($inst -notcontains $TestPkg) {
        Say '  The HOME TEST app is not installed. Run -InstallHomeTest first.' 'Yellow'
    } else {
        Write-Host "This turns off Amazon's home screen for a few minutes. Keep this window open."
        $go = if ($Yes) { 'y' } else { Read-Host 'Continue? (y/n)' }
        if ($go -eq 'y') {
            $r = (Sh "pm disable-user --user 0 $AmazonHome") -join ' '
            if ($r -match 'Exception|Error|protected|Unknown') {
                Say "  Fire OS refused: $r" 'Yellow'
                Say '  Nothing changed. Try pressing Home on the remote: a "choose a home app" box may appear.'
            } else {
                Start-Sleep -Seconds 3
                Sh 'input keyevent KEYCODE_HOME' | Out-Null
                Start-Sleep -Seconds 4
                $t1 = Top-Activity
                Say "  After pressing Home: $t1"
                Chk ($t1 -match 'galaxymedia\.tvsetup\.hometest') 'Home opens Galaxy Media (HOME TEST)'
                # from inside another app
                Sh 'monkey -p com.esaba.downloader -c android.intent.category.LEANBACK_LAUNCHER 1' | Out-Null
                Start-Sleep -Seconds 4
                $t2a = Top-Activity
                Sh 'input keyevent KEYCODE_HOME' | Out-Null
                Start-Sleep -Seconds 4
                $t2 = Top-Activity
                Say "  Another app was open: $t2a"
                Say "  After pressing Home again: $t2"
                Chk ($t2 -match 'galaxymedia\.tvsetup\.hometest') 'Home from inside another app returns to Galaxy Media'
                Show-Home
                Write-Host ''
                Write-Host 'Now look at the TV and try the remote: Home, the gear menu, All apps, Fire TV settings, Back.'
                $ans = if ($Yes) { 'y' } else { Read-Host 'Put Amazon''s home screen back now? (y = undo, n = keep the lock for more testing) [y]' }
                if ($ans -ne 'n') {
                    Sh "pm enable $AmazonHome" | Out-Null
                    $offNow = Get-List 'pm list packages -d'
                    if ($offNow -contains $AmazonHome) { Say "  Amazon's home screen is STILL OFF. Run -HomeUndo." 'Red' } else { Say "  Amazon's home screen is back on." 'Green' }
                } else {
                    Say '  The lock is still on. To undo: .\galaxy-maintain.ps1 -Ip <IP> -HomeUndo' 'Yellow'
                }
            }
        } else { Say '  Skipped by you. Nothing was changed.' }
    }
}

# ---- REMOVE APPS: uninstall every app you installed except the ones you keep ----
if ($RemoveApps) {
    Say '=== REMOVE EXTRA APPS ==='
    $always = @('com.galaxymedia.tvsetup', 'ar.tvplayer.tv', 'com.haxapps.xc7', 'fyahrebrands.moviebox.galaxytheater',
                'fyahrebrands.applinked.galaxystore', 'io.world.vpn', 'ai.world.tv', 'org.smarttube.stable',
                'com.termux', 'org.fdroid.fdroid', 'com.esaba.downloader')
    $keepAll = @($always + @($Keep | Where-Object { $_ }))
    $names = @{
        'com.apple.atve.androidtv.appletv' = 'Apple TV'; 'com.crunchyroll.crunchyroid' = 'Crunchyroll'
        'com.amazon.amazonvideo.livingroom' = 'Prime Video'; 'com.amazon.music.tv' = 'Amazon Music'
        'com.instantbits.cast.receiver' = 'Web Video Caster'; 'net.torguard.openvpn.client' = 'TorGuard VPN'
    }
    $third = Get-List 'pm list packages -3'
    $extras = @($third | Where-Object { $keepAll -notcontains $_ -and $_ -notlike 'com.google.android.*' -and $_ -notlike 'com.android.*' } | Sort-Object)
    if ($extras.Count -eq 0) {
        Say '  Nothing to remove: every app you installed is on the keep list.' 'Green'
    } else {
        Say "  These $($extras.Count) apps would be uninstalled (their sign-ins and data are lost):" 'Yellow'
        foreach ($p in $extras) {
            $friendly = if ($names.ContainsKey($p)) { '   (' + $names[$p] + ')' } else { '' }
            Say ('    ' + $p + $friendly)
        }
        if ($DryRun) {
            Say '  Dry run: nothing was removed.'
        } else {
            $go = if ($Yes) { 'y' } else { Read-Host "Uninstall these $($extras.Count) apps? (y/n)" }
            if ($go -eq 'y') {
                $before = Get-FreeMB
                foreach ($p in $extras) { Say ('  ' + $p + ': ' + ((Sh "pm uninstall --user 0 $p") -join ' ')) }
                Start-Sleep -Seconds 2
                $after = Get-FreeMB
                if ($null -ne $before -and $null -ne $after) { Say "  Free storage: $before MB -> $after MB (freed $($after - $before) MB)" 'Green' }
                Say '  To get an app back, install it again from the store.'
            } else { Say '  Skipped by you. Nothing was removed.' }
        }
    }
}

# ---- stamp + save ----
if ($changed -and -not $DryRun) { Sh "settings put global galaxy_last_maintained $date" | Out-Null }

$dir = Join-Path $PSScriptRoot 'reports'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$labelPart = if ($Label) { "-$Label" } else { '' }
$file = Join-Path $dir (("{0}-{1}-{2}" -f ($model -replace '\W', ''), ($Ip -replace '\.', '-'), $stamp) + $labelPart + '.txt')
$out | Set-Content -Path $file -Encoding UTF8
Write-Host "Saved: $file"

# ---- 5. reboot ----
if ($Reboot) {
    $ans = Read-Host 'Reboot the stick now? (y/n)'
    if ($ans -eq 'y') { & $adb -s $target reboot; Write-Host 'Rebooting.' }
}
