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
- **VPN chip:** shows the real VPN state and opens the WorldVPN app. A second chip, "VPN renews <date>",
  appears in the last 14 days and opens the Pay / Renew screen.
- **Pay / Renew:** payment QR codes and a "message me" note. Everything shown comes from the private codes
  file, so no payment details are stored in this repository.
- **Backgrounds:** Rotate (the default, changes by itself), Starfield, four picture wallpapers with a light
  running along the neon outline, Frame trace, Aurora drift, drawn scenes, the Galaxy Media logo and Plain
  dark. Lower-end sticks skip the animated ones. Each moving background is measured on the stick and Rotate
  skips any that draw too slowly.
- **Your shelf:** apps listed in the settings file, plus up to 8 total that a person adds themselves: hold OK
  on an app in All apps and choose Add to my shelf. Hold OK on one of your added tiles to take it off.
- **Gear menu:** All apps (hold OK for Open, Add to my shelf, Uninstall), Stick health (Wi-Fi, speed test, memory, heat, restarts,
  which backgrounds are smooth), Pay / Renew, Fire TV settings, Settings, Setup & updates, Help.
- **Setup and updates:** downloads every app first, then opens the install prompts one after another, and
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

Nothing is ever uninstalled. Debloat only switches packages off, and Fire OS refuses a few of them.
The script stamps the date on the stick, and the app counts that as a finished maintenance.

## Settings file
Each stick reads one JSON file (a secret GitHub Gist) through a short setup code. It lists the apps
(`name`, `package`, `version`, `apkUrl`, `autoUpdate`, `pinned`, `managedByStore`), the person's
`vpnExpires` date (`YYYY-MM-DD`), an optional family `notice`, and sports leagues and teams. Set `pinned`
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
