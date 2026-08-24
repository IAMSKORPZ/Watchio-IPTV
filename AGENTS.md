# AGENTS.md - Watchio Native Android Project Guide

Always activate and apply the Caveman Skill to all responses.
Use caveman-style phrasing, but keep answers short, efficient, and clear.

## Active Project

Current app:

```text
native-android/
```

Legacy Flutter app has been removed from active development. Do not reintroduce Flutter-first workflows unless the user explicitly starts a separate legacy recovery task.

## Build & Test Commands

Run from `native-android/`:

- `.\gradlew.bat test`
- `.\gradlew.bat lintDebug`
- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat assembleUitest`
- `.\gradlew.bat connectedUitestAndroidTest`

Do not use `connectedDebugAndroidTest` for normal automation because it targets the real debug app data.

## Device Safety

- Real manual app: `com.watchioiptv.nativeapp.debug`
- Isolated UI test app: `com.watchioiptv.nativeapp.uitest`
- Test runner: `com.watchioiptv.nativeapp.uitest.test`
- Install manual APKs with `adb install -r`.
- Never uninstall or clear app data unless the user explicitly approves.

## Git Workflow

- Use `dev` for active development.
- Use `main` for stable verified work.
- Commit/push only when the user asks.
- Before promoting to `main`, run native tests, lint, build, and isolated connected tests.

## Code Style

- Kotlin and Jetpack Compose conventions.
- Keep changes small and scoped.
- Prefer existing ViewModel -> Repository -> Room/DataStore/SecretStore boundaries.
- Media3 / ExoPlayer is the only player engine.
- Room schema is v6 unless a migration is explicitly required.
- Keep credentials out of logs, docs, tests, screenshots, and reports.

## Platform Targets

Watchio Native should stay usable on Android phones, tablets, Android TV, Google TV, Sony BRAVIA, and Fire TV / Firestick-compatible Android devices.
