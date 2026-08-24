# Watchio Native Settings

Phase 10 exposes typed DataStore settings through a native Settings screen.

## Architecture

`SettingsScreen` renders `SettingsUiState` from `SettingsViewModel`. The ViewModel writes only through `WatchioSettingsRepository`.

## Exposed Settings

- Theme: Watchio Default, Dark, Purple, Blue
- Input mode: Auto, TV Remote, Touch
- Stream format: AUTO, TS, HLS
- Player Settings: resume, live auto-play, controls, retry, and video scaling

## Persistence

Settings are stored in DataStore. Theme selection stores a theme id and maps it to the native Watchio palette set. Invalid or legacy stored values fall back to Watchio Default. Stream format continues to drive future Xtream playback URL resolution through `PlaybackUrlResolver`.

Selecting a theme updates the Compose app theme immediately through collected DataStore state. No Activity restart is required. Watchio Default preserves and restores the original Phase 10 native palette.

Phase 14.0 keeps the same theme ids and persistence model while moving shared spacing, sizing, focus, poster, and typography values into Compose design tokens.

## Input And Focus

Settings controls use the shared focusable card component for touch, keyboard, mouse, and TV D-pad operation.

Phase 12.1 makes Settings vertically scrollable and wraps option rows on compact screens, so Stream Format and Back remain reachable on Galaxy S22 and 1080p TV layouts.

## Account Information

Phase 14.2F replaces the Account Information placeholder with a real Xtream account page. The page uses the shared Settings header, keeps the single compact header Back control, and shows provider-scoped safe metadata only:

- provider display name
- masked Xtream username
- account status derived from persisted expiry
- expiration date when available
- provider type
- local provider-added date
- last provider refresh
- independent Live, Movies, and Series refresh timestamps

Phase 14.2F.1 persists safe Xtream `user_info` connection metadata in provider-scoped DataStore after successful authentication. When supplied by the provider, Account Information shows maximum connections, active connections, and allowed output formats as human-readable values such as `HLS, TS`. Missing optional values render as `Not available`. The page never shows passwords, raw SecretStore values, server URLs, XMLTV URLs, or credential-bearing playback URLs.

## Player Settings

Phase 14.2G replaces the Player Settings placeholder with a real Watchio-styled page. Settings are DataStore-backed, apply without app restart where safe, and preserve existing playback defaults for current users. Room stays v6.
# Watchio Native Settings

Settings are DataStore-backed and remain usable in landscape on phone, tablet, and TV.

Phase 13.1G adds TV Guide / EPG controls:

- Auto Refresh On/Off, default on
- Refresh Interval: 1 Day, 3 Days, 7 Days, default 3 Days
- Last successful refresh, sourced from `epg_sources.lastSuccessAtEpochMs`
- Refresh Now, using the same coordinator as scheduled refresh

Turning auto refresh off cancels unique periodic WorkManager work. Manual Refresh Now remains available.
# Phase 14.2E Settings Menu

Settings now opens to a 10-card category launcher instead of dumping all controls on the root screen. Existing controls remain available under their category routes:

- Provider Management: existing Providers screen.
- EPG Settings: auto refresh, refresh interval, last success, Refresh Now.
- Stream Format: AUTO, TS, HLS.
- Input Mode: Auto, TV Remote, Touch.
- Appearance: Watchio Default, Dark, Purple, Blue.

Placeholder routes exist for Account Information, Player Settings, Parental Controls, Backup & Restore, and Check for Updates. The update placeholder performs no network request and does not fake update status.
