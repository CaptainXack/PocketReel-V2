# Fire Stick Test Checklist

## Before you start

- Android Studio installed on PC
- Fire Stick and PC on the same Wi-Fi network
- ADB available from Android Studio Platform Tools or system PATH

## Fire Stick setup

- [ ] Note Fire Stick IP address
- [ ] Enable ADB Debugging
- [ ] Enable Apps from Unknown Sources

## Build

- [ ] Open project in Android Studio
- [ ] Wait for Gradle sync
- [ ] Build debug APK
- [ ] Confirm APK exists at `app/build/outputs/apk/debug/app-debug.apk`

## Sideload

- [ ] Run `adb connect <IP>:5555`
- [ ] Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] Open CX Fire Loader on Fire TV

## App test

- [ ] Direct URL tab opens
- [ ] Code tab opens
- [ ] Endpoint settings save
- [ ] Direct HTTPS APK download works
- [ ] Installer opens after download
- [ ] Install last download works
- [ ] Launch installed app works after install

## Code mode test

- [ ] Endpoint set to your HTTPS code API
- [ ] API returns valid JSON
- [ ] `apkUrl` is HTTPS and directly downloadable
- [ ] Optional `sha256` matches the served APK
