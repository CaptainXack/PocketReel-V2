# PocketReel

PocketReel is a native Kotlin Android and Fire app for playing local media from SD cards, tablets, shared storage, and chosen folders.

It is being reshaped from the original downloader experiment into a premium local-media app with:

- local folder scanning
- poster walls
- TMDb metadata matching
- internal playback
- resume and library features
- Android and Fire first-class support

## Current scaffold

This repo now contains the first working foundation for the Android and Fire build:

- document-tree folder picker for local media folders
- recursive video scanner for SD card and picked folders
- library screen for discovered media
- TMDb metadata repository scaffold
- Media3 player activity for local playback
- GitHub Actions APK build workflow
- support target pushed back toward older Fire and Android devices

## How the app is meant to work

1. Install the APK.
2. Open the app.
3. Pick your main media folder or SD card once.
4. The app scans videos and builds its own internal library.
5. It can fetch artwork and metadata from TMDb when configured for the build.
6. Tap any library item to play it in the built-in player.

The app is designed to sort media internally without moving the real files.

## TMDb configuration

TMDb values are read from Gradle properties so they do not have to be hardcoded into source files.

Add these to a local Gradle properties file when building privately:

```properties
TMDB_API_KEY=your_tmdb_api_key
TMDB_READ_ACCESS_TOKEN=your_tmdb_read_access_token
```

If those values are blank, the app still scans and plays local media, but artwork matching stays disabled.

## Android Studio build steps

1. Open the project in Android Studio.
2. Let Gradle sync.
3. Build the debug APK.
4. The APK will normally land in:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## GitHub Actions phone-friendly build path

This project includes a GitHub Actions workflow that builds a debug APK in the cloud.

### Fast path

1. Push the repo to GitHub.
2. Open **Actions**.
3. Wait for **Build debug APK** to finish.
4. Download the **PocketReel-debug-apk** artifact.
5. Extract it and install the APK.

## Fire device test steps

1. On the Fire device open:
   - **Settings > My Fire TV > About > Network**
2. Note the IP address.
3. Then open:
   - **Settings > My Fire TV > Developer Options**
4. Turn on:
   - **ADB Debugging**
   - **Apps from Unknown Sources**
5. Connect from a PC or Termux:

```bash
adb connect 192.168.1.50:5555
adb install -r /path/to/app-debug.apk
```

Replace the IP address and APK path with your real values.

## Planned next passes

### Media library pass
- cleaner poster-wall layout
- grouped movies and shows
- continue watching
- watched state
- favorites
- profile support

### Playback pass
- resume position tracking
- subtitle auto-detection
- preferred audio language selection
- preferred subtitle language selection
- richer player controls

### Metadata pass
- background artwork refresh
- smarter movie and show matching
- local cache for posters and descriptions
- manual rematch tools

## Notes

- Android and Fire are the primary targets.
- iPhone and iPad are a later companion target, not the main platform.
- Older Fire devices are a design target, so the minimum SDK is kept lower than a modern-only app.
- The app builds its own library view and does not need to reorganize the real file tree.
