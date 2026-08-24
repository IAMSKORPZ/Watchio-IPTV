# Watchio Native Providers

Phase 10 adds a shared provider management surface for Xtream, M3U URL, and Local M3U providers.

## Architecture

`ProviderManagementScreen` renders state from `ProviderManagementViewModel`. The ViewModel uses `ProviderRepository`, `SettingsRepository`, `XtreamRepository`, and `M3uRepository`.

## Provider Selection

Selecting a provider persists `selected_provider_id` through DataStore. Existing Live, Movies, Series, Search, EPG, My List, Favorites, and History repositories already read the selected provider, so provider switching updates feature data without app restart.

## Refresh

Refresh delegates to the existing Xtream or M3U refresh path. Duplicate refresh taps are ignored while one refresh job is active. Failure shows a safe message and preserves the existing catalog because existing refresh implementations stage or replace only after success.

## Deletion

Deletion requires confirmation and displays only the safe provider display name. Room provider deletion cascades provider-scoped catalog, EPG, favorites, history, movie detail, series, season, and episode rows. `RoomProviderRepository` also deletes provider secrets.

If the active provider is deleted, the next enabled Xtream provider is selected deterministically for Home eligibility. If no Xtream provider remains, selected provider is cleared and Home eligibility is lost.

## Security

Provider screens do not display usernames, passwords, tokens, or generated playback URLs.

The Settings -> Account Information page displays safe provider metadata for the selected Xtream provider. It may show a masked username, provider display name, expiry, local added date, and refresh timestamps. It does not display passwords, raw server URLs, or generated credential URLs.

## First-Run Routing

After the Phase 14.2D.2 device-mode choice, Watchio routes directly to the existing Xtream Codes form. M3U URL and Local M3U remain available after authentication from Home -> Playlist -> Providers, but they do not satisfy the initial Home gate.

Deleting the last Xtream provider routes to Xtream login while preserving device-mode onboarding and unrelated settings.
