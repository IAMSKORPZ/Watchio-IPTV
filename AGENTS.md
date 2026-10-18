# AGENTS.md - Watchio Native Android Project Guide

Always activate and apply the Caveman Skill to all responses.
Use caveman-style phrasing, but keep answers short, efficient, and clear.

## Mandatory Standards

- Read and follow root `WATCHIO_AGENT_STANDARDS.md`, this file, and root `AGENT_HANDOFF.md` completely at the start of every session.
- `WATCHIO_AGENT_STANDARDS.md` defines mandatory safety, Caveman verification, device-permission, testing, Git, release, and handoff rules for all coding agents.
- If instructions conflict, follow the higher-priority instruction and document the conflict; never silently weaken safety or verification.

## Active Project

Current app:

```text
native-android/
```

Legacy Flutter app has been removed from active development. Do not reintroduce Flutter-first workflows unless the user explicitly starts a separate legacy recovery task.

## Authoritative Handoff

- Read the root `AGENT_HANDOFF.md` completely before changing code.
- Active worktree: the repository worktree containing this file.
- Active branch: `codex/dev-rescue-bridge`.
- Current documented HEAD: `c23904e1832012aa3a4edaed49fc1e6c532f76eb`.
- Sports V6.5.5 is complete and committed locally. The commit has one parent and repository integrity passes.
- Validation: S22 navigation 15/15 PASS, connected instrumentation 45/45 PASS, Sports Compose 35/35 PASS, JVM 1,943/1,943 PASS, and lint/APK build gates PASS.
- GitHub push: **NOT PERFORMED**. BRAVIA: **NOT ACCESSED**.
- Historical V6.5.2-V6.5.4 failures are superseded by V6.5.5. Preserve them in `AGENT_HANDOFF.md` as engineering history, not current defects.
- Verify branch, HEAD, dirty state, and protected manifest before every task. Snapshot values can become stale after an authorized commit.

## Build & Test Commands

Run from `native-android/`:

- `.\gradlew.bat test`
- `.\gradlew.bat lintDebug`
- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat assembleUitest`
- `.\gradlew.bat connectedUitestAndroidTest`

Do not use `connectedDebugAndroidTest` for normal automation because it targets the real debug app data.

## Device Safety

- Real manual app: `com.iamskorpz.watchioiptv.debug`
- Isolated UI test app: `com.iamskorpz.watchioiptv.uitest`
- Test runner: `com.iamskorpz.watchioiptv.uitest.test`
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
- Room schema is v7. Preserve `MIGRATION_6_7`; do not change schema or migrations without explicit authorization.
- Keep credentials out of logs, docs, tests, screenshots, and reports.

## Platform Targets

Watchio Native should stay usable on Android phones, tablets, Android TV, Google TV, Sony BRAVIA, and Fire TV / Firestick-compatible Android devices.
