# Watchio Production Release Checklist

## Supported baseline

- Production application ID: `com.iamskorpz.watchioiptv`.
- Current public release: v0.1.7, versionCode 20, Room schema 7.
- Current development database: Room schema 10, with additive migrations from 7 through 10.
- Main application minSdk: 24. Fire OS 5/API 22 is not supported by the normal Watchio build.
- User-supplied IPTV services can require HTTP. Cleartext traffic therefore remains enabled for provider compatibility. Watchio-controlled updater and endpoint configuration traffic must use trusted HTTPS origins.

## Repository preparation

1. Merge only validated development commits into `main` through the approved repository process.
2. Record the exact `main` SHA. The public workflow accepts only that full SHA.
3. Select a semantic version newer than the latest published release and a versionCode greater than the current stable manifest.
4. Confirm `native-android/update/update.json` is unchanged unless a separate development-channel task approved it.
5. Review release notes for secrets, provider data, unsupported claims, and incomplete features.

## Dry run

Run the Public Release workflow with `dryRun=true`. Verify the resulting private workflow artifact contains:

- `Watchio-IPTV.apk`
- `SHA256SUMS.txt`
- `SOURCE_SHA.txt`
- `stable.json`

Verify package ID, versionName, versionCode, public signing certificate, APK signature, APK checksum, immutable release URL, stable channel, and source SHA. A dry run must not create a tag, GitHub release, or stable metadata commit.

## Manual acceptance required before publication

Use the exact dry-run APK and production signing identity. Perform an in-place upgrade over public v0.1.7 without uninstalling or clearing data. Verify:

- Login session and managed provider credentials
- Providers, active-provider selection, playlists, favourites, watch history, settings, parental controls, EPG, downloads, and offline playback
- Room 7-to-10 migration
- Update check, APK download, checksum, installer handoff, and post-update launch
- Phone touch and Back behavior
- Tablet layout
- Android TV/Google TV/Sony BRAVIA D-pad focus, activation, Back, and playback
- Supported Fire TV device behavior on API 24 or newer

Record Fire OS 5/API 22 as unsupported. Do not report physical acceptance from emulator, JVM, debug, UITEST, or synthetic-only evidence.

## Publication

1. Require approval through the GitHub `production` environment.
2. Rerun with `dryRun=false` and the same source SHA, version, and release notes.
3. The workflow revalidates live `main`, downloads only its validated bundle, publishes the immutable APK asset, checks public availability, then commits generated `stable.json` to `main`.
4. Confirm the GitHub release asset is named exactly `Watchio-IPTV.apk`.
5. Confirm the raw stable manifest and immutable APK URL are publicly accessible.
6. Confirm the stable manifest checksum equals the downloaded APK checksum.

## Failure handling

Follow `docs/PRODUCTION_RELEASE_ROLLBACK.md`. Prefer restoring the previous stable metadata or shipping a higher-version forward fix. Do not assume Android downgrade preserves data.
