# Galaxy Media

A home-screen app for family Fire TV sticks. It shows the clock, a greeting with the date and weather, a
sports ticker, your app shelf and a choice of animated backgrounds. A laptop script does the one-time stick
setup and upkeep. The app itself needs no special access.

Package: `com.galaxymedia.tvsetup`. Built by GitHub Actions on every push to `main`; each build is
published as a release with `galaxy-setup-vX.Y.Z.apk`. A separate manual workflow builds a HOME test
version (own app id, pre-release only) for trying out making Galaxy Media the Home button.

## What the app does
- **Home screen:** clock, greeting, date and weather, a live-game line for your teams, a sports ticker,
  the app shelf and chips for updates, maintenance, restarts and the VPN.
- **VPN chip:** shows the real VPN state and opens the WorldVPN app (WorldVPN is kept off the shelf).
- **Renewal reminder:** a chip that starts as "Set a reminder". The person picks 1 month, a quarter, 6 months,
  a year or an exact date, and the chip then shows "Renews <date>". In the last 14 days it turns amber, then
  red, and opens the Pay / Renew screen, which also has a "Paid? Set the next reminder" button. The date is
  saved on that stick only.
- **Closing apps:** the setting "Close apps when I come back" (Auto, On, Off) asks Android to close other apps'
  background processes when the person returns to Galaxy Media. Auto turns it on for sticks with under about 2.5 GB
  of memory. The VPN app is never closed. Stick health > Free up memory does the same on demand and reports
  how much memory really came back.
- **Contact chip:** always there. It shows a Telegram code for reports and questions.
- **Pay / Renew:** payment QR codes and a "message me" note. Everything shown comes from the private codes
  file, so no payment details are stored in this repository.
- **Backgrounds:** Rotate (the default, changes by itself), Starfield, four picture wallpapers with a light
  running along the neon outline, Frame trace, Aurora drift, drawn scenes, the Galaxy Media logo and Plain
  dark. Lower-end sticks skip the animated ones. Each moving background is measured on the stick and Rotate
  skips any that draw too slowly.
- **Seasonal backgrounds (v0.13.0):** the default background is "Seasonal", which follows the calendar: Halloween, Thanksgiving, Christmas,
  New Year, Valentine's, St. Patrick's, Easter, July 4th, and a plain Galaxy G the rest of the year. Each scene is a still picture plus a short list
  of moving pieces in `app/src/main/assets/seasons/seasons.json`, drawn by `SeasonView`. Lower-end sticks (and "Animations: Off") get the still picture.
  The smoothness monitor drops a scene back to the still if it runs rough on that stick. The other backgrounds are Galaxy G, Starfield, Aurora and Nebula.
  Settings > "Preview season" cycles through the other scenes for 30 minutes, so each one can be checked (or filmed) out of season.
  The greeting ("HAPPY THANKSGIVING") shares the slot under the picture with the live-game bar. Calmer sports ticker on lower-end sticks (pages of 3 games, no scrolling).
- **Galaxy-style shelf icons:** a picture named `<package>.webp` in `app/src/main/assets/icons` replaces that app's own icon on the shelf (TiviMate, XC7, SmartTube so far),
  matching the Galaxy Theater icon. Settings > App icons switches back to each app's own. Art script: `icons.py` in galaxy-seasonal-art.zip.
- **Shelf notes:** an optional `"tag"` on an app in the settings file (for example `"tag": "Best choice"`) shows as a small note under the name on the shelf. No app update needed to change the wording.
- **Plain-language Settings (v0.13.3):** rows were reworded (for example "Close apps when I come back" is now "Clean up when I return home", "Quiet mode" is "Hide extras when idle") and each has a one-sentence hint. The hints live in the `hints` map in `SettingsActivity.kt`, keyed by row label.
- **Settings in plain words (v0.13.4):** every Settings row has one short line under it explaining what it does; confusing names were replaced (Screen effects, Background movement, Clean up when I return home, Hide extras when idle, Dim at night). On seasonal backgrounds the game ticker takes the season's colour. St. Patrick's has falling clovers and rising sparks.
- **v0.13.5 look:** on the Seasonal background the app tiles and chips become dark glass edged in the season's colour, and each tile gets a small holiday decoration (Santa hat, 2027 badge, spider, bunny ears...; `deco` in seasons.json, Settings > Holiday touches on apps). The renewal chip moved under the greeting (top left) so it no longer sits on the picture. Falling pieces skip the clock area, and the clock has a soft shadow. Settings rows grow to fit their explanation line.
- **v0.13.5:** date + weather moved under the greeting (top left) so they show on every background; renewal + VPN chips sit under them, Contact + gear stay top right (clear of the G); the left-side app rail is retired (apps always at the bottom); Settings rows grow to fit their explanation line; on Seasonal, tiles and chips turn to dark glass edged in the season colour, each app tile gets a small holiday decoration (Settings > Holiday touches on apps), and nothing moving is drawn over the clock.
- **Screensaver:** `GalaxyDream` is a system screensaver showing today's picture, dimmed, with a slow-moving clock. Set it from the laptop with
  `.\galaxy-maintain.ps1 -Ip <ip> -Screensaver Galaxy` (undo with `-Screensaver Amazon`). `-RemoveAmazonSaver` also switches off Amazon's screensaver apps.
- **Settings shortcuts:** every "Open settings" button goes through one safe opener (`SystemSettings`). Fire OS 8 answers the
  generic settings request with a placeholder (`CTSDummySettingsHandler`) that sends the person to the home screen, so the app
  skips placeholders, tries the exact screen, then the main Settings page, and otherwise shows the steps in words.
- **Gear menu:** All apps (hold OK on an app to remove it), Stick health (Wi-Fi, speed test, memory, heat, restarts,
  which backgrounds are smooth), Pay / Renew, Fire TV settings, Settings, Setup & updates, Help.
- **Setup and updates:** the "Install / update apps" button downloads every app first, then opens the install prompts one after another, and
  updates itself from this repository's releases.

## About the Home button
Fire OS does not let an app become the Home button: Amazon's launcher is a protected package and Fire OS
ignores a home app chosen by command (tested on a Fire TV Stick 4K, Fire OS 8). Instead, Galaxy Media opens by
itself after a restart, and works best as the first app in Amazon's app row. A manual HOME test workflow exists
for trying this on Google TV boxes.

## Set up a new stick
1. Fire TV Settings, My Fire TV, About: click the device name 7 times. Then open Developer Options and
   turn ADB Debugging and Apps from Unknown Sources on.
2. Install Downloader, then install the app from the latest release (type the link by hand):
   `https://github.com/galaxystreamingservice-droid/galaxy-tv/releases/download/vX.Y.Z/galaxy-setup-vX.Y.Z.apk`
3. Open Galaxy Media. Enter a name, a ZIP code and the setup code. The app downloads the settings file and
   shows the apps to install.
4. On a laptop on the same Wi-Fi, run `tools/galaxy-maintain.ps1` (see below).

## Laptop script
`tools/galaxy-maintain.ps1` needs `adb.exe` (put the `platform-tools` folder next to the script).

    .\galaxy-maintain.ps1 -Ip <stick IP>              report, debloat, trim caches, auto-start permission
    .\galaxy-maintain.ps1 -Ip <stick IP> -Verify      PASS / WARN / FAIL check of the whole setup, plus a screenshot
    .\galaxy-maintain.ps1 -Ip <stick IP> -Audit       memory, heat, Wi-Fi, frames, what is switched off
    .\galaxy-maintain.ps1 -Ip <stick IP> -Benchmark   app launch times and smoothness, 3 runs each
    .\galaxy-maintain.ps1 -Ip <stick IP> -Cycle -Label bloat1   restart, wait, measure (then -Summarize to compare)
    .\galaxy-maintain.ps1 -Ip <stick IP> -InstallApps -Gist <code>   install the shelf apps with no taps on the TV
    .\galaxy-maintain.ps1 -Ip <stick IP> -Dump        save the package list of a new kind of box
    .\galaxy-maintain.ps1 -Ip <IP> -Setup -PersonName "Name" -ZipCode 12345 -Gist GAL-4821   set up a whole stick from the laptop
    .\galaxy-maintain.ps1 -Ip <IP> -Record -Seconds 600   record the TV screen on the laptop (no TV frame)
    .\galaxy-maintain.ps1 -Ip <IP> -RemoveApps -DryRun   list the apps you installed that are not on the keep list (then without -DryRun to remove them)

Nothing is ever uninstalled. Debloat only switches packages off, and Fire OS refuses a few of them.
The script stamps the date on the stick, and the app counts that as a finished maintenance.

## Settings file
Each stick reads one JSON file (a secret GitHub Gist) through a short setup code. It lists the apps
(`name`, `package`, `version`, `apkUrl`, `autoUpdate`, `pinned`, `managedByStore`), an optional family
`notice`, and sports leagues and teams. Set `pinned`
to `false` to keep an app off the shelf. A template is in `gist-template/`. The codes and the payment
details live in a separate private file. Do not commit real download links, setup codes or payment
details to this repository.

## Repo layout
- `app/` the Android app (Kotlin; the only outside service is the free Open-Meteo weather API)
- `app/src/main/assets/` debloat lists and the outline paths used by the picture wallpapers
- `app/src/hometest/` the manifest overlay used only by the HOME test build
- `tools/` laptop maintenance script
- `gist-template/` example settings file
- `.github/workflows/` the normal build and the manual HOME test build
