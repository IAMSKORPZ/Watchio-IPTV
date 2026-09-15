# Updates

Watchio uses GitHub Releases as the development update distribution backend.

## Channels

- `dev`: development/debug builds and pre-releases.
- `stable`: future production signed builds.

Current development manifest endpoint:

```text
https://raw.githubusercontent.com/IAMSKORPZ/Watchio-IPTV/dev/native-android/update/update.json
```

The app does not use authenticated GitHub API calls for update checks. The Updates screen fetches the raw manifest, compares the installed Android `versionCode`, shows `versionName` and release notes, then lets the user choose whether to download.

## Release Assets

Development releases should attach:

- `watchio-dev-<version>.apk`
- `update.json`
- `SHA256SUMS.txt`

The APK asset is public. `update.json` stores public release URLs and the APK SHA-256 hash.

Current development release target:

```text
https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.0-dev.2
```

Current development APK asset target:

```text
https://github.com/IAMSKORPZ/Watchio-IPTV/releases/download/v0.1.0-dev.2/watchio-dev-0.1.0-dev.2-debug.apk
```

## Install Flow

Watchio must not silently install APKs.

Current flow:

1. Settings -> Check for Updates.
2. Fetch `update.json` from the channel endpoint.
3. Compare installed `versionCode` with manifest `versionCode`.
4. Show release notes.
5. User chooses Download Update.
6. Download APK into app cache under `cacheDir/updates`.
7. Verify SHA-256.
8. Show `Update Ready`.
9. User chooses `Install Update`.
10. Open Android package installer through a scoped `FileProvider` content URI.
11. User approves installation.

If Android blocks app installs from Watchio, the screen opens system "Install unknown apps" settings for this package. Watchio does not retry installation automatically after settings returns; the user presses Install Update again.

Update metadata is validated before use:

- supported schema version
- expected channel
- positive `versionCode`
- non-empty `versionName`
- HTTPS APK URL
- safe APK filename
- 64-character SHA-256

Remote release notes are rendered as plain text, not HTML.

## Updates Screen UX

Updates is a standalone Watchio page with a shared `WatchioPageHeader` titled `UPDATES`. TV uses a two-column status/action layout; smaller screens stack the same content. The screen is user-driven:

- opening the screen may check the manifest
- it never downloads automatically
- it never opens Android's installer automatically after verification
- downloading shows bytes and percent when the server provides size
- verifying shows a short integrity-check message
- release notes are plain text and capped for readability
- only buttons are focusable; status and release-note cards are display-only
- default TV focus lands on the active primary action when one is available

## Signing Identities

Watchio has two permanent signing identities. DEV signs `com.iamskorpz.watchioiptv.debug` for local development builds and GitHub prereleases. PUBLIC signs `com.iamskorpz.watchioiptv` for public releases. The keys, aliases, passwords, and certificates are independent. Neither identity may fall back to Android's default debug keystore.

A DEV update is compatible only when:

- application ID matches `com.iamskorpz.watchioiptv.debug`
- signing certificate matches the installed debug build
- `versionCode` is greater than or equal to the installed update path requirements

Local keystores are stored outside Git:

```text
%USERPROFILE%\.watchio\signing\watchio-dev.jks
%USERPROFILE%\.watchio\signing\watchio-public.jks
```

Current certificate SHA-256 fingerprints:

```text
DEV: 0A:7E:10:03:97:7E:5D:14:FB:35:E3:36:1D:61:42:E1:35:40:73:DA:43:D2:B9:A2:DC:00:10:9C:B9:10:09:4F
PUBLIC: 8A:76:E2:0B:7C:B2:E1:68:12:F5:05:12:75:A3:D1:12:FC:FB:AB:7C:24:24:C5:E8:97:F5:58:87:6B:CC:6F:F0
```

Gradle verifies each requested identity against its expected fingerprint and fails when credentials are missing or mismatched. The isolated LOCAL (`com.iamskorpz.watchioiptv.local`) and UITEST packages may use Android's default debug key because they never share a DEV or PUBLIC package ID.

Required GitHub configuration for automated dev releases:

- repository variable: `WATCHIO_DEV_CERT_SHA256`
- secret: `WATCHIO_DEV_KEYSTORE_BASE64`
- secret: `WATCHIO_DEV_KEYSTORE_PASSWORD`
- secret: `WATCHIO_DEV_KEY_ALIAS`
- secret: `WATCHIO_DEV_KEY_PASSWORD`

The keystore secret must contain the base64-encoded keystore file. The private keystore file and passwords must never be committed.

PUBLIC releases use the equivalent `WATCHIO_PUBLIC_*` secrets and `WATCHIO_PUBLIC_CERT_SHA256` repository variable through `.github/workflows/public-release.yml`. Public tags use `vX.Y.Z`; DEV tags use `vX.Y.Z-dev.N`. Tags are immutable.

## Automated Development Release Workflow

Workflow:

```text
.github/workflows/dev-release.yml
```

Trigger:

```text
workflow_dispatch
```

Inputs:

- `versionName`, for example `0.1.0-dev.3`
- `versionCode`, for example `4`
- `releaseNotes`, one plain-text note per line

The workflow runs only from `dev`, uses `contents: write`, and uses one concurrency group so two development releases cannot publish at the same time.

Release order:

1. Validate input format and reject version downgrades.
2. Reject existing tags/releases.
3. Require signing secrets and expected certificate fingerprint.
4. Apply requested version to the Android build.
5. Run unit tests, lint, debug build, and UI-test build.
6. Verify package name, version, and signing certificate fingerprint.
7. Rename the debug APK to `watchio-dev-<versionName>-debug.apk`.
8. Generate `SHA256SUMS.txt`.
9. Create a GitHub prerelease and upload APK/checksum.
10. Generate final `update.json` with public release URLs.
11. Upload `update.json` to the release.
12. Commit the version/manifest/checksum back to `dev`.

The workflow intentionally fails before publishing if signing secrets or `WATCHIO_DEV_CERT_SHA256` are missing. This prevents incompatible CI-signed APKs from being offered to installed development apps.

## Manual Device Rules

Use `scripts/install-dev.ps1` for local DEV APK validation and scoped installs. It verifies package and certificate before `adb install -r`, retries with `-d` only for `INSTALL_FAILED_VERSION_DOWNGRADE`, and never uninstalls or clears data automatically.

## Policy Notes

The installer handoff follows Android secure file sharing guidance: Watchio exposes only `cacheDir/updates` through `FileProvider`, sends a `content://` URI, and grants temporary read permission to the package installer. It does not use `file://` URIs or broad storage permissions.

`REQUEST_INSTALL_PACKAGES` is present for sideload/development distribution. Google Play treats this as a high-risk permission and requires permitted use plus Play Console declaration. Before any Play Store production release, either justify this updater model in policy docs or remove the permission for Play-distributed builds.
