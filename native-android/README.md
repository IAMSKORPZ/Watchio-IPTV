# Watchio Native Android

Kotlin-first Android implementation of Watchio IPTV.

This is the current active application. Playback is foreground-only, the Activity is landscape-oriented, and Media3 / ExoPlayer is the only player engine.

## Stack

- Kotlin
- Jetpack Compose
- AndroidX Media3 / ExoPlayer
- Room v6
- DataStore
- WorkManager
- OkHttp / Retrofit
- SecretStore-backed provider credentials

## Features

- First-run device mode selection
- Xtream login gate before Home
- Xtream provider import, refresh, metadata, and playback URL resolution
- M3U URL and local file providers
- XMLTV / EPG import, cache, matching, current/next, TV Guide, and WorkManager refresh
- Live TV preview/fullscreen, favourites, history, search, and EPG
- Movies playback, details, resume, search, favourites, and history
- Series, seasons, episodes, playback, resume, search, favourites, and history
- My List, Continue Watching, Settings, themes, input mode, stream format, account info, and player settings
- Android TV / BRAVIA / Fire TV remote navigation and phone/tablet touch support

## Build

```powershell
.\gradlew.bat assembleDebug
```

Debug APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Test

```powershell
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleUitest
.\gradlew.bat connectedUitestAndroidTest
```

Connected automation uses isolated packages:

- real manual app: `com.watchioiptv.nativeapp.debug`
- isolated test app: `com.watchioiptv.nativeapp.uitest`
- test runner: `com.watchioiptv.nativeapp.uitest.test`

Do not run `connectedDebugAndroidTest` for normal automation. Do not uninstall or clear the real debug package on devices containing private provider data.

## Manual Device Install

```powershell
adb devices -l
adb -s <SERIAL> install -r app\build\outputs\apk\debug\app-debug.apk
```

Use scoped ADB when multiple devices are connected.

## Documentation

Start with:

- `docs/README.md`
- `docs/AI_HANDOFF.md`
- `docs/ARCHITECTURE.md`
- `docs/TESTING.md`
- `docs/SECURITY.md`

Historical migration context is archived under `docs/historical/`.
