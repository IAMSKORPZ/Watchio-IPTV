# Watchio Native Playback Reliability

Phase 13 hardens the existing shared Media3 player. It does not replace Media3, add VLC/FFmpeg, or create another player manager.

## Player Ownership

`AppContainer` owns one `Media3WatchioPlayerManager`. Live TV, Movies, and Episodes all use the same manager and state flow. Live preview/fullscreen continues to move the existing `PlayerView` surface instead of creating a second `ExoPlayer`.

## State Machine

Player states are:

- `Idle`
- `Connecting`
- `Buffering`
- `Playing`
- `Paused`
- `Ended`
- `Recovering`
- `Failed`
- `Released`

New playback requests clear stale errors and reset retry state. Recovery state is temporary and only used for bounded automatic retry.

## Retry Policy

Automatic retry is intentionally conservative:

- default 2 automatic attempts
- configurable 1-3 attempts through Player Settings
- short backoff: 1.5s then 3s
- only network/timeout/HTTP/source I/O style errors are auto-retried
- decoder/unsupported failures go to `Failed`

Manual Retry remains available from UI and reuses the last `PlaybackMedia` request, including M3U `User-Agent` and `Referer` headers. Xtream URLs remain generated ephemerally before playback and are not persisted for retry.

Phase 13.1 cancels pending retry work when playback is paused or the app backgrounds, so `Recovering` cannot restart hidden playback.

Phase 14.2G lets users disable automatic retry. Manual Retry remains available.

## Buffering And Rebuffering

`STATE_BUFFERING` maps to `Buffering`. Video surface remains attached; previous frame is not intentionally hidden. If playback resumes, stale recoverable error state is cleared by `Playing`.

## Seeking

Movie and Episode seek controls use shared clamping:

- seek below zero clamps to 0
- VOD seek beyond known duration clamps to duration
- unknown duration is tolerated
- Live streams do not apply VOD duration assumptions

## History And Resume

Movies and Episodes keep the existing 15-second progress-save cadence. Progress also saves on player exit. Live TV does not start a periodic resume job.

On app background, Movies and Episodes perform one immediate progress save, cancel the periodic job, and remain paused until explicit user playback.

## Surface And Lifecycle

`attachSurface` removes the `PlayerView` from any previous parent before attaching to the new container. `detachSurface` only removes the view from that container. `stop()` clears current media and returns to `Idle`; `release()` releases ExoPlayer and reports `Released`.

## Audio Focus

Media3 audio attributes use media/movie content type and request audio focus through ExoPlayer.

## Security

Player user-facing errors are generic and do not include raw URLs. M3U headers remain in memory as request headers. Credential-bearing Xtream URLs remain ephemeral and must be masked before diagnostics.

## Device Status

S22 and Tab S9 are available connected Android targets. BRAVIA was not online during the start of Phase 13. Fire TV remains not run unless hardware appears.
