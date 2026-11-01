# Galaxy Media

A home-screen app for family Fire TV sticks. It shows the clock, a greeting with the weather, a sports
ticker, your app shelf and a choice of animated backgrounds. A laptop script does the one-time stick
setup and upkeep. The app itself needs no special access.

Package: `com.galaxymedia.tvsetup`. Built by GitHub Actions on every push to `main`; each build is
published as a release with `galaxy-setup-vX.Y.Z.apk`.

## What the app does
- **Home screen:** clock, greeting, date and weather, live-game line for your teams, sports ticker,
  app shelf (including "More apps"), update and renewal chips.
- **Backgrounds:** Rotate (changes by itself every few minutes, and is the default), Starfield,
  four picture wallpapers with a light that runs along the neon outline, Frame trace, Aurora drift,
  drawn scenes (Nebula, Sunset, Ocean, Midnight city, Neon grid, Mountains), the Galaxy Media logo,
  and Plain dark. Lower-end sticks skip the animated ones.
- **Settings:** name, accent color, sports and teams, hidden apps, background, light color,
  screen edge, quiet mode, night dimming, motion, screensaver, weather city, open on startup.
- **Setup and updates:** installs and updates the apps listed in the stick's settings file, and
  updates itself from this repo's releases.
- **Help:** short fix-it cards for buffering, no sound and apps that will not open.

## Set up a new stick
1. Fire TV Settings, My Fire TV, About: click the device name 7 times. Then open Developer Options and
   turn ADB Debugging and Apps from Unknown Sources on.
2. Install Downloader, then install the app from the latest release (type the link by hand):
   `https://github.com/galaxystreamingservice-droid/galaxy-tv/releases/download/vX.Y.Z/galaxy-setup-vX.Y.Z.apk`
3. Open Galaxy Media. Enter a name, a ZIP code and the setup code. The app downloads the stick's
   settings file, then shows the apps to install.
4. On a laptop on the same Wi-Fi, run `tools/galaxy-maintain.ps1` (see below).

## Laptop script
`tools/galaxy-maintain.ps1` needs `adb.exe` on the laptop.

    .\galaxy-maintain.ps1 -Ip <stick IP>              report, debloat, trim caches, auto-start permission
    .\galaxy-maintain.ps1 -Ip <stick IP> -Report      read-only report
    .\galaxy-maintain.ps1 -Ip <stick IP> -Debloat -DryRun
    .\galaxy-maintain.ps1 -Ip <stick IP> -Speed Fast  animations at half length (Off, Fast, Default)

Nothing is ever uninstalled. Debloat only disables packages, and Fire OS refuses a few of them.
The script stamps the date on the stick, and the app counts that as a finished maintenance.

## Settings file
Each stick reads one JSON file (a secret GitHub Gist) through a short setup code. It lists the
apps (`name`, `package`, `version`, `apkUrl`, `autoUpdate`, `pinned`, `managedByStore`) and may
set `iptvExpires` (`YYYY-MM-DD`), a family `notice`, and sports leagues and teams. A template is in
`gist-template/`. Do not commit real download links, setup codes or keys to this repo.

## Repo layout
- `app/` the Android app (Kotlin, no external services except the free Open-Meteo weather API)
- `app/src/main/assets/` debloat lists and the outline paths used by the picture wallpapers
- `tools/` laptop maintenance script
- `gist-template/` example settings file
- `.github/workflows/build.yml` build and release
