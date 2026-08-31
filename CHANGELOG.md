# Changelog

## 0.1.0-dev.3

- Uses Room-backed 150-item windows for Movies and Series catalog browsing, while keeping full-catalog search in SQL.
- Avoids provider-wide episode loading when opening Series; seasons and episodes load only for selected content.
- Refreshes Movie Details and Series Details, including clearer season selection and episode presentation.
- Stabilizes season-selector focus after changing seasons on remote-driven devices.
- Uses icon-only shared header actions for compact Android TV layouts.
- Adds TV and Fire TV double-Back exit: first Back shows a two-second confirmation, second Back exits.
- Adds resume playback, Continue Watching, Next Episode countdown, and autoplay improvements.

## 0.1.0-dev.2

- Adds the in-app GitHub update system in Settings.
- Supports update checks, APK download, SHA-256 verification, and secure Android installer handoff.
- Handles Android install-from-unknown-source permission flow for development updates.

## 0.1.0-dev.1

- Development pre-release for GitHub Releases update infrastructure.
- Native Android app with Home, Live TV, Movies, Series, Search, My List, TV Guide, Settings, and Media3 playback.
- Debug-signed development APK only. Not a production signed release.

## Native Android baseline

- Native Android app is active Watchio implementation.
- Root Flutter-era app/platform folders removed from active repository.
- Native docs indexed under `native-android/docs/README.md`.
- Historical Flutter-to-native migration note archived under `native-android/docs/historical/`.
