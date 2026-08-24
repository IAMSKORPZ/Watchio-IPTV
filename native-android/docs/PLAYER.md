# Watchio Native Player

Phase 6 uses one app-scoped `WatchioPlayerManager` backed by AndroidX Media3 `ExoPlayer`.

## Ownership

`AppContainer` owns one `Media3WatchioPlayerManager`. Live TV UI and fullscreen UI both attach surfaces to this same manager. Screen changes do not create a second player, reload media, or rebuild credential URLs.

## State

Player state is explicit:

- `Idle`
- `Connecting`
- `Buffering`
- `Playing`
- `Paused`
- `Ended`
- `Failed`

Each state carries metadata with current media, position, optional duration, first-frame flag, session id, and load generation.

## Surface Handoff

Preview and fullscreen use `PlayerView` through Compose `AndroidView`. `attachSurface(container)` moves the same `PlayerView` between containers. `detachSurface(container)` removes only the view. It does not stop or release playback.

Phase 14.2H keeps the same handoff model while replacing the Live TV screen layout. The preview panel remains a surface host for the existing app-scoped player manager. Fullscreen still attaches the same session and does not rebuild the stream URL.

Phase 14.2H.1 removes permanent Live TV retry/fullscreen action buttons. Fullscreen is triggered by selected-channel re-activation or preview activation. Retry remains the existing bounded Media3 retry/manual retry path; no second retry engine is added.

Phase 14.2H.2 changes only programme information layout. It does not change Media3, URL resolution, surface handoff, retry, or foreground lifecycle behavior.

Phase 14.2H.3 restores preview prominence in Live TV. Preview sits in the top-right row beside compact channel info, and the EPG panel sits below it. `Connecting...`, `Buffering...`, and error text are drawn inside the preview surface. Media3 session ownership and surface handoff are unchanged.

Phase 14.2H.4 adjusts only Compose sizing and spacing around that same preview surface. It does not change Media3, request headers, retry, fullscreen, or lifecycle code.

Phase 14.2H.5 keeps preview/player behavior unchanged. Only compact text fitting and EPG label spacing changed.

Phase 14.2H.5.1 keeps preview/player behavior unchanged. Compact Channel Info now shows only Live TV label and channel name.

## Media3 Config

Media3 is configured directly in native Android code. The default controller is disabled because Watchio uses a custom Compose overlay. Resize mode is driven by Player Settings and defaults to `FIT` to preserve aspect ratio.

M3U per-channel headers are passed through `DefaultHttpDataSource.Factory`:

- `User-Agent`
- `Referer`

Xtream URLs are resolved just-in-time through `PlaybackUrlResolver` and `SecretStore`; credential URLs are never persisted.

Movies and Episodes reuse the same manager. VOD player routes are fullscreen-only and add seek/progress controls while keeping the same state machine.

## Lifecycle

Leaving Live TV calls `stop()` and clears current media. Fullscreen back only pops the fullscreen surface and returns to preview playback.

Phase 13.1 adds foreground-only lifecycle handling. When the Activity backgrounds, Live TV stops/pauses through the shared manager. Movies and Episodes save progress, cancel their periodic history job, and pause. Return to Watchio never auto-resumes playback.

## Errors And Retry

Media3 `PlaybackException` values are mapped to user-safe messages. Phase 13 adds bounded automatic recovery for recoverable network/HTTP/source errors, then exposes manual Retry. Manual Retry reloads the last media item and preserves request headers. No infinite retry loop is used.

Live TV no longer shows a permanent Retry button in the normal right panel. Automatic retry is controlled by Player Settings. Manual retry is available from Channel Options only when the selected channel is in a failed state.

See `PLAYBACK_RELIABILITY.md` and `PLAYBACK_LIFECYCLE.md`.

Phase 14.2G adds `docs/PLAYER_SETTINGS.md`. Preferences are observed by the app-scoped player manager and screens without creating a second player.

## Security

Player code must not log raw playback URLs. Xtream credential URLs remain ephemeral. Phase 8 episode URLs are resolved at play time only; M3U episode headers are per item.
## Phase 9 Library Integration

Phase 9 does not change playback. Search and My List navigate to existing Live, Movie, and Series routes and keep playback URL generation inside the established resolver/player layers.

## Phase 11 TV Guide

Play Live from TV Guide loads media through the existing app-scoped `WatchioPlayerManager`. The guide does not create `GuidePlayer`, ExoPlayer, or playback URL resolver instances.
## Phase 12 TV Overlay

Fullscreen overlays keep the same `WatchioPlayerManager` session. When overlay controls hide, focus returns to the player surface so remote OK/Enter can show controls again.

## Phase 14.2J Series Playback Parity

Series tab UI redesign preserves the shared `WatchioPlayerManager` without changes. Episode playback, resume position calculation, and fullscreen VOD controls continue to reuse the unified player architecture.
