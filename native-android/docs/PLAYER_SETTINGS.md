# Player Settings

Phase 14.2G replaces the Player Settings placeholder with a real DataStore-backed page.

## Implemented Preferences

- Auto Resume, default on.
- Auto Play Live Channel, default off.
- Remember Last Live Channel, default on.
- Show Player Controls, default on.
- Control Auto-Hide Delay: 3 Seconds, 5 Seconds, 8 Seconds, Never. Default 5 Seconds.
- Auto Retry Streams, default on.
- Retry Attempts: 1, 2, 3. Default 2.
- Video Scaling: Fit, Fill, Zoom. Default Fit.

## Behaviour

Auto Resume controls normal Movie and Series top-level play actions. Turning it off does not delete saved progress. Where resume data exists, users can still choose explicit Resume. Start Over remains unchanged.

Auto Play Live Channel only starts a remembered channel for the selected provider when that channel still exists. It never starts a random first channel.

Remember Last Live Channel stores only provider id plus stable channel id. It never stores Xtream playback URLs, M3U direct URLs, or credentials. Turning it off clears remembered live-channel ids and does not affect favourites or history.

Show Player Controls hides optional fullscreen overlay controls when off, but keeps required Play/Pause and Back/system navigation available so playback cannot trap the user.

Auto Retry uses the existing Media3 recovery path. Retry attempts are bounded to 1-3, defaulting to current behaviour of 2 attempts. Manual Retry remains available.

Video Scaling maps to Media3 `PlayerView` resize modes. Fit preserves the whole picture. Fill and Zoom preserve aspect ratio while cropping through Media3 zoom mode.

## Storage

Preferences live in `WatchioSettingsRepository` DataStore keys. Room remains schema v6 and no migration is required.

## Unsupported

Decoder engine switches, VLC, FFmpeg, external players, and background playback are intentionally not exposed. Media3 remains the only player engine and Watchio remains foreground-only.
## Phase 14.2H.1 Live Retry

Live TV no longer shows a permanent Retry button. Automatic retry continues to use Player Settings: `Auto Retry Streams` and `Retry Attempts`. Manual retry is available only from Channel Options when the selected channel is in a failed state.

## Phase 14.2H.2 Live Programme Panel

Player Settings behavior is unchanged. The Live TV programme information fix does not add playback settings, change retry defaults, or create a second player path.

## Phase 14.2H.4

No Player Settings behavior changes. Live TV spacing polish keeps automatic retry controlled by `Auto Retry Streams` and `Retry Attempts`.

## Phase 14.2H.5

No Player Settings behavior changes. No permanent Retry button is added back.

## Phase 14.2H.5.1

No Player Settings behavior changes. Channel Info cleanup does not affect automatic retry or manual retry from Channel Options.
