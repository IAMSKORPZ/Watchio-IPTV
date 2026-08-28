# Updates

Watchio uses GitHub Releases as the development update distribution backend.

## Channels

- `dev`: development/debug builds and pre-releases.
- `stable`: future production signed builds.

Current development manifest endpoint:

```text
https://raw.githubusercontent.com/IAMSKORPZ/Watchio-IPTV/dev/native-android/update/update.json
```

The app does not use authenticated GitHub API calls for update checks. Future update checks should fetch the raw manifest, compare the installed `versionCode`, show the `versionName` and release notes, then let the user choose whether to download.

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

Future flow:

1. Settings -> Check for Updates.
2. Fetch `update.json`.
3. Compare installed `versionCode` with manifest `versionCode`.
4. Show release notes.
5. User chooses Download.
6. Download APK.
7. Verify SHA-256.
8. Open Android package installer.
9. User approves installation.

## Development Signing Limits

The first GitHub update release is debug-signed. It can only update an existing app when:

- application ID matches `com.watchioiptv.nativeapp.debug`
- signing certificate matches the installed debug build
- `versionCode` is greater than or equal to the installed update path requirements

This is not a production signed release. Do not commit keystores, signing passwords, or release signing credentials.
