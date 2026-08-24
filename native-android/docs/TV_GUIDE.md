# Watchio Native TV Guide

Phase 11 adds the native TV Guide UI on the existing Phase 5 EPG backend.

## Architecture

`TvGuideScreen` renders immutable `TvGuideUiState`. `TvGuideViewModel` owns guide actions and delegates to `TvGuideRepository`. The repository composes `LiveTvRepository`, `EpgRepository`, Room EPG DAOs, and the existing `EpgChannelMatcher`.

Compose does not query Room, parse XMLTV, construct Xtream URLs, read credentials, or own ExoPlayer.

## Domain Models

- `WatchioGuideChannel`: provider id, channel id, display name, logo, channel number, category, favourite state, matched EPG channel id, and original `LiveTvChannel`.
- `WatchioGuideProgramme`: provider-neutral programme identity, channel id, title, optional description/metadata, UTC start/end, progress, and live-now state.
- `WatchioGuideWindow`: selected UTC window, local day, and compact day choices.
- `ProgrammeDetails`: selected programme plus channel.

Room entities do not leak into Compose.

## Provider Scope

The guide reads the selected provider from DataStore through `LiveTvRepository`. Live channels, EPG channels, and programmes are queried only for that provider. Provider A and Provider B rows are never merged.

Phase 12.1 makes provider selection reactive in `TvGuideViewModel`; if the guide route is created before selected-provider state is ready, it reloads when the provider arrives instead of staying on an empty shell.

## Timeline

The default window opens near now: about one hour of past context and several future hours. Date navigation supports Yesterday, Today, and Tomorrow within the Phase 5 retained guide range.

Time mapping is deterministic:

- `30 minutes = 120 dp`
- programme width derives from clipped duration
- malformed zero/negative durations clamp to a small safe width
- gaps render as empty spacers, not stretched programmes

Programme times display in the device local timezone from UTC epoch milliseconds. DST uses `java.time` zone rules.

## Current State

The guide shows a vertical NOW line when current time is inside the visible window. Current programme uses `start <= now < end`; progress is clamped from `0..1`.

## Layout And Input

The guide uses a fixed channel column, shared horizontal scroll for the time header and programme rows, and lazy vertical rows for large channel lists. Controls and cells use `WatchioFocusableCard`, so touch, mouse, keyboard Enter, and TV OK/DPAD focus styling reuse existing app behavior.

## Details And Playback

Selecting a programme opens a detail dialog with channel, local date/time, duration, description, and optional metadata when available. `Play Live` resolves the selected channel through `LiveTvRepository` and loads the shared `WatchioPlayerManager`; no guide-specific player exists.

Future catch-up, reminders, recording, and timeshift are not introduced.

## Refresh

Manual refresh calls existing `EpgRepository.refresh()`. Phase 5 staging preserves old guide data on failed refresh. Automatic WorkManager refresh remains future hardening.

When a selected provider has no EPG source row, `TvGuideViewModel` attempts one automatic refresh/discovery for that provider on guide open. Xtream providers can therefore discover the standard XMLTV source without re-adding the provider. Existing M3U header/custom sources keep priority.

Phase 13.1F makes guide startup Room-first. The ViewModel loads provider channels and cached EPG rows before any network work, so reopening TV Guide, changing category, or relaunching the app can show saved guide data immediately. If the source exists but cached EPG counts are empty, one bounded refresh is attempted. Failures clear loading and preserve any visible cached guide.

## Missing Data

Channels without an EPG match remain visible and show `No programme information`. Missing programme spans are empty gaps. Failed matching does not hide channels.

Top-level status messages stay distinct:

- source missing: `No EPG source available.`
- refresh in progress: `Loading TV Guide...`
- source exists but no programmes loaded: `Guide not downloaded yet.`

## Security

Xtream XMLTV and playback URLs stay ephemeral below the repository layer. The guide never displays usernames, passwords, raw stream URLs, XMLTV URLs, or request headers.

## Large Guide Strategy

The repository queries a bounded visible window. The UI uses `LazyColumn` for channel rows and does not compose all rows eagerly. Phase 11 tests cover 2,000 channels and 10,000 programmes.
