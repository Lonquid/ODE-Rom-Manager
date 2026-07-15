# ODE Rom Manager

An Android app for managing ROM libraries on the **EZ Flash Omega Definitive Edition** (SimpleDE firmware).

## Features

- **Library** — Browse all ROMs on your SD card. Rename files to clean titles (strips region tags, preserves translation credits). Bulk rename and bulk art scraping.
- **Artwork Scraping** — Pulls box art from ScreenScraper.fr, converts to the exact EZ Flash BMP format (128×120, 24-bit BGR), and places it in the correct `/SYSTEM/IMGS/XX/XX/XXXX.bmp` path automatically.
- **ROM Hack Workflow** — For GBA ROM hacks: confirms the display name, checks the header code, assigns a unique `0XXX` code (never collides with retail games), lets you pick artwork from your phone, backs up the original ROM to `/storage/emulated/0/ODE Rom Manager/backups/`, then patches the header in place.
- **Backup Log** — Persistent log of every header modification. Works without the SD card inserted. Confirm that changes worked (then delete the backup to save space), or revert and restore the original ROM.
- **Settings** — ScreenScraper credentials, firmware type (SimpleDE or stock), artwork region preference, auto-scan toggle.

## Building

### Via GitHub Actions (recommended — no Android Studio needed)

1. Fork or push this repo to your GitHub account
2. Go to the **Actions** tab
3. The `Build Debug APK` workflow runs automatically on every push
4. When it finishes (~3–5 min), click the run → scroll to **Artifacts** → download `ODE-Rom-Manager-debug`
5. Unzip and install `app-debug.apk` on your phone (enable "Install from unknown sources" first)

### Manually triggering a build

In the Actions tab, select **Build Debug APK** → click **Run workflow** → **Run workflow**.

### Local build (requires Android Studio or command line)

```bash
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK with API 34.

## First Launch

1. The app will ask for **All Files Access** permission (needed to write backup files to `/storage/emulated/0/ODE Rom Manager/backups/`)
2. Tap **Connect SD Card** and select the root of your SD card in the file picker
3. Go to **Settings** and enter your ScreenScraper.fr username and password
4. Confirm firmware type is set to **SimpleDE** (default)
5. Go to **Home** and tap **Scan Now** to discover your ROMs

## ROM Hack Workflow

1. Go to the **Hacks** tab — all GBA ROMs are listed here
2. Tap a ROM to open the workflow dialog
3. **Step 1**: Confirm or edit the display name
4. **Step 2**: If the game code has existing art, confirm it. Otherwise:
   - A unique `0XXX` code is generated
   - Pick an image from your phone's gallery or files
   - Tap **Backup & Apply Changes**
5. Insert the SD card into your GBA, test the game
6. Go to the **Backups** tab and confirm it worked (or revert if not)

## Notes

- Artwork thumbnails only work for **GBA** ROMs on the EZ Flash. GB/GBC/NES etc. don't support thumbnails at the firmware level.
- The app requires a [ScreenScraper.fr](https://www.screenscraper.fr) account for artwork scraping. Free accounts work fine.
- ROM files are never modified except during the explicit Hack Workflow (and only after a backup is made).

## Version

`1.0.0` — Initial release
