# Galaxy TV Setup — Phase 1

Fire TV / Google TV companion app that pairs with its own device, debloats it,
installs the custom launcher, and keeps your app shelf in sync with a per-device
settings file hosted as a GitHub Gist.

## What's in here

| Path | What it is |
|---|---|
| `app/` | The Android app (Kotlin) |
| `app/src/main/assets/device_profiles.json` | Build.MODEL → device name / RAM / tier table |
| `app/src/main/assets/debloat_fireos.json` | Real package list from your Fire TV Stick 4K (2nd Gen) |
| `app/src/main/assets/debloat_googletv.json` | Placeholder — fill in after the Onn package dump |
| `gist-template/device-settings.json` | Copy this into a Gist, one per TV |
| `.github/workflows/build.yml` | GitHub builds the APK for you on every push |

## Getting your first APK (no Android Studio needed)

1. On github.com, create a **new repository** named `galaxy-tv` (private is fine).
2. Upload everything in this folder to it (drag-and-drop in the browser works).
3. Go to the repo's **Actions** tab → the "Build APK" workflow runs automatically.
4. When it finishes (a few minutes), open the run → download the **GalaxyTVSetup-debug** artifact → unzip → `app-debug.apk`.
5. Host that APK somewhere Downloader on the Firestick can reach (a GitHub Release works), then install it like any other sideloaded app.

## Settings Gist

1. On github.com → **gist.github.com** → New gist.
2. Filename: `device-settings.json`. Paste `gist-template/device-settings.json`, edit the values.
3. Create **secret** gist. Click **Raw** — that URL is what you paste into the app when it asks.

## First run on the Firestick

1. Settings → My Fire TV → Developer Options → **ADB Debugging ON**.
2. Open Galaxy TV Setup → **Pair**. A dialog appears on screen: tick **Always allow**, press OK.
3. Paste the Gist raw URL when prompted.
4. Press **Run full setup**. Watch the log on the right.

## Phase 2 (next)
- Projectivy configuration push (shelf layout, backgrounds)
- Sports tile (ESPN public feed) + boxing list
- Scheduled daily sync + weekly maintenance (WorkManager)
- Kill-previous-app hook
- Phone pairing app — only if loopback pairing turns out to be blocked on your build
