# Changelog

## 0.1.0-dev.2

- Adds the in-app GitHub update system in Settings.
- Supports update checks, APK download, SHA-256 verification, and secure Android installer handoff.
- Handles Android install-from-unknown-source permission flow for development updates.

## 0.1.0-dev.1

- Development pre-release for GitHub Releases update infrastructure.
- Native Android app with Home, Live TV, Movies, Series, Search, My List, TV Guide, Settings, and Media3 playback.
- Debug-signed development APK only. Not a production signed release.

## Native Android baseline

- Native Android app is the active Watchio implementation.
- Root Flutter-era app/platform folders removed from active repository.
- Root README rewritten around Kotlin, Compose, Media3, Room, DataStore, WorkManager, Xtream, M3U, and XMLTV/EPG.
- Native docs indexed under `native-android/docs/README.md`.
- Historical Flutter-to-native migration note archived under `native-android/docs/historical/`.
- CI simplified to native Android Gradle validation.
