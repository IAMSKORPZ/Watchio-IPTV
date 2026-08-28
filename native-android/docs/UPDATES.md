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
https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.0-dev.1
```

Current development APK asset target:

```text
https://github.com/IAMSKORPZ/Watchio-IPTV/releases/download/v0.1.0-dev.1/watchio-dev-0.1.0-dev.1-debug.apk
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
8. Open Android package installer through a scoped `FileProvider` content URI.
9. User approves installation.

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

## Development Signing Limits

The first GitHub update release is debug-signed. It can only update an existing app when:

- application ID matches `com.watchioiptv.nativeapp.debug`
- signing certificate matches the installed debug build
- `versionCode` is greater than or equal to the installed update path requirements

This is not a production signed release. Do not commit keystores, signing passwords, or release signing credentials.

## Policy Notes

The installer handoff follows Android secure file sharing guidance: Watchio exposes only `cacheDir/updates` through `FileProvider`, sends a `content://` URI, and grants temporary read permission to the package installer. It does not use `file://` URIs or broad storage permissions.

`REQUEST_INSTALL_PACKAGES` is present for sideload/development distribution. Google Play treats this as a high-risk permission and requires permitted use plus Play Console declaration. Before any Play Store production release, either justify this updater model in policy docs or remove the permission for Play-distributed builds.
