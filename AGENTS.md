# AGENTS.md - Watchio Flutter Project Guide

Always activate and apply the Caveman Skill to all responses.
Use caveman-style phrasing, but keep answers short, efficient, and clear.

## Build & Development Commands

- `flutter pub get` - Install dependencies
- `flutter run` - Run app in debug mode
- `flutter build apk` - Build Android APK
- `flutter build ios` - Build iOS app
- `flutter build web` - Build web version
- `flutter build windows` - Build Windows app
- `flutter build macos` - Build macOS app
- `flutter build linux` - Build Linux app
- `flutter test` - Run all tests
- `flutter analyze` - Run static analysis
- `dart run build_runner build` - Generate Drift database code
- `flutter gen-l10n` - Generate localization files

## Git Workflow

- Use `dev` for active development.
- Use `main` for stable production work only.
- Do not push or commit unless the user asks.
- Before promotion to `main`, run analyze, tests, and a real build.

## Code Style

- Follow Flutter and Dart conventions.
- Prefer small, focused patches.
- Use Provider for state where the app already uses Provider.
- Use Drift for database work.
- Keep credentials out of logs, JSON exports, screenshots, and docs.
- Do not manually edit generated localization files.

## Platform Targets

Watchio should stay usable on Android phone/tablet, Android TV, Fire TV/Firestick, Windows, web, iOS, iPadOS, macOS, and Linux where the Flutter target is enabled.
