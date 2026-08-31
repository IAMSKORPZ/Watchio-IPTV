# Watchio Native Android

Current Watchio application: Kotlin-first IPTV player for phones, tablets, Android TV, Google TV, BRAVIA, Fire TV, and Firestick-compatible Android devices.

## Architecture

Jetpack Compose UI calls ViewModels, which coordinate repositories, Room v6, DataStore, secure credential storage, and provider APIs through AppContainer manual DI. Media3 / ExoPlayer is the only playback engine.

Supported providers and features include Xtream Codes, M3U/M3U8, XMLTV/EPG, Live TV, Movies, TV Shows, search, favourites, history, Continue Watching, resume playback, Next Episode, and autoplay.

Movies and Series use Room-backed `LIMIT`/`OFFSET` catalog windows of 150 items. Search remains SQL-backed, and opening Series does not load provider-wide episodes.

## TV behavior

Remote/D-pad navigation and visible focus are first-class. Header actions use compact icons. TV and Fire TV require a second Back press within two seconds to exit; first Back shows confirmation. Nested screens and fullscreen playback keep their own Back behavior.

## Build

Run from this directory:

```powershell
.\gradlew.bat assembleDebug
```

Debug APK: `app\build\outputs\apk\debug\app-debug.apk`

## Test

```powershell
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleUitest
.\gradlew.bat assembleUitestAndroidTest
.\gradlew.bat connectedUitestAndroidTest
```

Use only isolated connected testing:

- Manual app: `com.watchioiptv.nativeapp.debug`
- UI-test app: `com.watchioiptv.nativeapp.uitest`
- Test runner: `com.watchioiptv.nativeapp.uitest.test`

Never run `connectedDebugAndroidTest` for normal automation. Do not uninstall or clear manual-app data. Install manual builds with `adb install -r` only. Do not modify BRAVIA through ADB; use its in-app updater.

## Updates and docs

Development releases use GitHub Actions and `update/update.json`; see `docs/UPDATES.md`. Start broader documentation at `docs/README.md`.
