# Watchio IPTV

Watchio IPTV is a Flutter IPTV client created by **iamSkorpz**.

It is built for Android phones, tablets, Android TV, Fire TV, Firestick, Windows, web, Linux, macOS, and iOS-ready Flutter targets. Watchio does not provide channels, playlists, streams, subscriptions, or IPTV content. Users must add their own legal provider details.

## Features

- Xtream Codes provider support
- M3U URL, M3U8, and local M3U file support
- Stalker portal support
- Live TV, Movies, Series, and EPG guide
- Favourites and watch history
- Global search
- Secure provider credential storage
- Custom themes, highlight colours, panel colours, and background styles
- TV remote, keyboard, mouse, and touch support
- Android TV and Fire TV friendly focus navigation
- Local media library
- Backup, restore, announcements, maintenance, and update checks

## Supported Devices

- Android phones and tablets
- Samsung Galaxy devices
- Android TV and Google TV
- Amazon Fire TV and Firestick
- Windows desktop
- Web
- Linux
- macOS
- iOS and iPadOS Flutter targets

## Requirements

- Flutter SDK
- A legal IPTV provider or playlist
- Provider details for one supported login type:
  - Xtream Codes
  - M3U URL
  - M3U file
  - Stalker portal

## Development

```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

Run on connected Android device:

```bash
flutter run
```

## Security

- Provider passwords are stored with secure storage where supported.
- Exported provider JSON does not include passwords.
- Playlist JSON does not include passwords.
- Media URL debug logs redact credentials.

## Disclaimer

Watchio IPTV is a media player app only. It does not host, sell, provide, promote, or distribute IPTV content. Users are responsible for using their own legal content sources and following local laws.

## Author

Created by **iamSkorpz**.
