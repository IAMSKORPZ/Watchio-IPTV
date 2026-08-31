# Watchio IPTV

Watchio is a native Android IPTV player. It does not provide channels, playlists, subscriptions, or media. Add only legal provider details and content sources.

## Platforms

Watchio supports Android phones and tablets, Android TV, Google TV, Sony BRAVIA, Fire TV, and Firestick-compatible Android devices. Touch, keyboard, and remote/D-pad navigation are supported.

## Features

- Xtream Codes and M3U/M3U8 URL or local-file providers
- Live TV, XMLTV/EPG import, TV Guide, preview, and fullscreen playback
- Movies and TV Shows with categories, SQL-backed search, details, favourites, history, and resume playback
- Series seasons and episodes, Next Episode countdown, and autoplay
- Continue Watching for resumable movies and episodes
- Media3 / ExoPlayer playback with subtitle, audio-track, aspect-ratio, speed, overlay, and error controls
- Android TV icon-only header actions and stable remote focus
- Announcements inbox with a remote GitHub feed, persistent read/unread state, and cached offline viewing
- TV/Fire TV double-Back exit: first Back shows a toast; second Back within two seconds exits
- Full-screen Watchio updater with responsive TV/mobile layouts, SHA-256 verification, and secure installer handoff

Movies and Series catalogs use Room-backed `LIMIT`/`OFFSET` windows (page size 150). Watchio does not hydrate full provider catalogs or provider-wide episode lists when opening those tabs.

## Architecture

Active app: `native-android/`

```text
Compose UI -> ViewModel -> Repository -> Room / DataStore / provider APIs
```

Kotlin, Jetpack Compose, AppContainer manual DI, Room schema v6, DataStore, secure credential storage, WorkManager, OkHttp/Retrofit, and Media3/ExoPlayer are current architecture. Historical Flutter migration notes are archived under `native-android/docs/historical/`; they are not current implementation guidance.

## Build

From repository root:

```powershell
cd native-android
.\gradlew.bat assembleDebug
```

Debug APK: `app\build\outputs\apk\debug\app-debug.apk`

## Test

From `native-android/`:

```powershell
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleUitest
.\gradlew.bat assembleUitestAndroidTest
.\gradlew.bat connectedUitestAndroidTest
```

Connected automation uses isolated packages:

- Manual app: `com.watchioiptv.nativeapp.debug`
- UI-test app: `com.watchioiptv.nativeapp.uitest`
- Test runner: `com.watchioiptv.nativeapp.uitest.test`

Do not use `connectedDebugAndroidTest` for normal automation. Do not uninstall or clear manual-app data. Install manual debug builds with `adb install -r` only. BRAVIA updates stay in-app-updater only.

## Branches and updates

- `dev`: active development and development-update source
- `main`: stable, verified checkpoint

Test `dev` first, then fast-forward `main` to that exact commit. Development APK releases use GitHub Actions and `native-android/update/update.json`; see `native-android/docs/UPDATES.md`.

## Documentation

- `native-android/README.md`: module build and testing reference
- `native-android/docs/README.md`: documentation index
- `native-android/docs/ARCHITECTURE.md`: package map and boundaries
- `native-android/docs/TESTING.md`: local and connected test guidance
- `native-android/docs/ANNOUNCEMENTS.md`: feed format, caching, and announcement actions
- `native-android/docs/SECURITY.md`: credentials and URL-masking rules
- `CHANGELOG.md`: checkpoint history

## Legal

Watchio is a media player only. Users are responsible for their own legal content sources and local compliance.
