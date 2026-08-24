# Watchio IPTV

Watchio is now a native Android IPTV player application.

The active application lives in:

```text
native-android/
```

The older Flutter application has been removed from the active repository. Historical migration notes are kept under `native-android/docs/historical/` only.

Watchio does not provide channels, playlists, streams, subscriptions, or IPTV content. Users must add their own legal provider details.

## Current Status

Native Android is the current source of truth.

Supported targets:

- Android phones
- Android tablets
- Android TV / Google TV
- Sony BRAVIA
- Fire TV / Firestick-compatible Android devices

## Tech Stack

- Kotlin
- Jetpack Compose
- AndroidX Media3 / ExoPlayer
- Room schema v6
- DataStore
- WorkManager
- OkHttp / Retrofit
- Android SecretStore / encrypted credential storage
- Xtream Codes
- M3U / M3U8
- XMLTV / EPG

## Current Features

- First-run device mode flow
- Strict Xtream login gate before Home
- Xtream provider import and refresh
- M3U URL and local M3U file import
- XMLTV / EPG import, cache, matching, and auto refresh
- Home dashboard
- Live TV preview and fullscreen playback
- Movies playback and resume
- Series, seasons, episodes, playback, and resume
- Global Search plus scoped Live / Movies / Series search
- My List, favourites, history, and continue watching
- TV Guide with cached EPG
- Settings, Account Information, Player Settings, EPG Settings, Appearance, Input Mode, Stream Format
- Android TV / remote / keyboard / touch navigation

## Project Structure

```text
Watchio/
├── README.md
├── native-android/
│   ├── app/
│   ├── docs/
│   ├── gradle/
│   ├── test-fixtures/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── README.md
└── .github/
```

## Build

Run from `native-android/`:

```powershell
.\gradlew.bat assembleDebug
```

APK:

```text
native-android\app\build\outputs\apk\debug\app-debug.apk
```

## Testing

Run from `native-android/`:

```powershell
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleUitest
.\gradlew.bat connectedUitestAndroidTest
```

Connected tests must use the isolated UI-test package:

- real manual app: `com.watchioiptv.nativeapp.debug`
- isolated test app: `com.watchioiptv.nativeapp.uitest`
- test runner: `com.watchioiptv.nativeapp.uitest.test`

Never uninstall or clear the real debug package for instrumentation testing. Install manual builds with `adb install -r` only so private provider data stays intact.

## Branches

- `dev`: active development baseline
- `main`: stable verified baseline

After repository modernization, both branches should point at the same verified native Android state.

## Security

- Provider passwords stay out of Room and DataStore.
- Xtream credentials live in SecretStore.
- Credential-bearing playback and EPG URLs are generated ephemerally.
- Logs must pass through `SensitiveUrlMasker`.
- Tests and docs use fake providers such as `example.invalid`.

## Documentation

Start here:

- `native-android/docs/README.md`
- `native-android/docs/AI_HANDOFF.md`
- `native-android/docs/ARCHITECTURE.md`
- `native-android/docs/TESTING.md`
- `native-android/docs/SECURITY.md`

## Legal

Watchio is a media player app only. It does not host, sell, provide, promote, or distribute IPTV content. Users are responsible for using their own legal content sources and following local laws.
