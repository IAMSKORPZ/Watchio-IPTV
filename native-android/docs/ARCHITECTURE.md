# Watchio Native Architecture

Phase 5 adds XMLTV/EPG source, import, matching, and query foundations on top of the provider catalog foundation. Flutter Watchio remains reference app.

## Decisions

- Kotlin-first: new application code is Kotlin. Java only for Android/library interop.
- Compose-first: app UI uses Jetpack Compose, Material 3, and TV-friendly focus patterns.
- DI: manual `AppContainer` for Phase 1. It keeps generated DI out until real feature graph and scopes justify Hilt.
- Room: `WatchioDatabase` version 2 contains provider, catalog, favorites, history, and EPG contract entities. It does not store secrets.
- DataStore: `WatchioSettingsRepository` owns typed settings for selected provider, input mode, stream format, theme JSON, EPG auto-refresh, and resume playback.
- Secrets: `SecretStore` plus `ProviderCredentialStore` store provider credentials. Provider credentials stay out of Room/DataStore.
- Networking: OkHttp + Retrofit. Debug diagnostics pass through `SensitiveUrlMasker`; release avoids credential logs.
- Media3: `WatchioPlayerManager` owns playback. Screens must not create unmanaged ExoPlayer instances.
- Player ownership: preview/fullscreen should attach surfaces to same Media3 session where practical.
- TV focus: reusable `WatchioFocusableCard` uses Compose focus state, visible focus border, and glow.
- Design system: `WatchioTheme` provides shared tokens and reusable controls in `ui/components`; migrations are incremental and must preserve working feature behavior.
- Bootstrap: `BootstrapViewModel` owns startup routing. It resolves Loading, NeedsDeviceMode, NeedsProvider, and Ready without loading large catalogs or EPG.
- Results/errors: `WatchioResult` and `WatchioError` provide small shared failure vocabulary.
- Time/text: `WatchioClock` and `TextNormalizer` keep timestamps and searchable names testable.
- Xtream: `XtreamRepository` owns auth/import/refresh. UI never builds Xtream URLs or reads passwords.
- M3U: `M3uRepository` owns URL/file opening, streaming parse, staged import, refresh, and counts. UI never parses playlists or reads local files directly.
- EPG: `EpgRepository`, `XmlTvParser`, and `EpgChannelMatcher` own XMLTV source resolution, streaming import, retention, and guide queries.
- Live TV: `LiveTvViewModel` and `LiveTvRepository` provide provider-neutral categories/channels, EPG now/next, favorites, history, and playback requests.
- Player: app-scoped `WatchioPlayerManager` owns one Media3 session. Preview and fullscreen attach surfaces to the same player instead of creating separate streams.
- Movies: `MoviesViewModel` and `MoviesRepository` provide provider-neutral VOD catalog, details, trailer metadata, history/resume, favorites, and shared-player playback.
- Series: `SeriesViewModel` and `SeriesRepository` provide provider-neutral Series catalog, lazy details, seasons, episodes, TMDB TV trailers, history/resume, favorites, and shared-player episode playback.
- Search: `GlobalSearchViewModel` and `SearchRepository` provide provider-scoped catalog search with category independence across Global, Live TV, Movies, and Series.

## Package Shape

- `core`: database, datastore, DI, logging, model, network, player, security, util.
- `domain`: provider/catalog models, repository contracts, playback URL resolver contract.
- `data`: Room-backed repository implementations.
- `data.xtream`: Xtream API DTOs, flexible JSON parsing, import repository, URL normalization, playback URL resolver.
- `data.m3u`: M3U streaming parser, content classifier, import repository, and stable identity mapping.
- `data.epg`: EPG source descriptors, XMLTV pull parser, import repository, and channel matcher.
- `data.live`: provider-neutral Live TV mapping for Xtream and M3U catalogs.
- `data.movies`: provider-neutral VOD mapping, Xtream detail loading, TMDB trailer cache, playback requests.
- `data.series`: provider-neutral Series mapping, Xtream `get_series_info`, M3U episode grouping, season synthesis, TMDB TV trailer cache, playback requests.
- `data.library`: global search, favorites, history, and continue watching aggregations.
- `feature.live`: Live TV browsing, preview, fullscreen, overlay controls, header channel search, and focus-aware UI.
- `feature.movies`: shared-header movie browser, category rail, movie-search overlay, poster grid, details, options dialog, and fullscreen VOD player route.
- `feature.series`: shared-header series browser, compact category rail, series-search overlay, poster grid, details, season/episode navigation, and series player route.
- `feature.library`: global search overlay screen and My List screens.
- `ui`: app shell, theme, reusable components.
- `feature`: current Home ViewModel proof of Room/Repository/ViewModel/Compose flow.
- `feature.home`: responsive Home shell and selected-provider dashboard counts.
- `feature.provider`: shared provider management, switching, refresh, and delete confirmation.
- `feature.settings`: DataStore-backed native settings surface.

## Tests

- Unit: masking, credential safety, and theme defaults.
- Instrumentation: Room/repository, DataStore, Android secure storage, navigation smoke, focusable card render.
- Connected instrumentation: Phase 14.2C uses a dedicated `uitest` build type with application id `com.watchioiptv.nativeapp.uitest`. The real manual debug package remains `com.watchioiptv.nativeapp.debug`, so connected tests cannot mutate the user's provider, SecretStore, Room, DataStore, favourites, history, or EPG cache.
- Fixtures: fake-only M3U, Xtream, XMLTV files in `test-fixtures/`.
- Full native test strategy and connected device requirements: `docs/TESTING.md`.
- Xtream behavior and import strategy: `docs/XTREAM.md`.
- M3U parser, import, refresh, and identity behavior: `docs/M3U.md`.
- EPG sources, import, retention, matching, and query behavior: `docs/EPG.md`.
- Live TV behavior: `docs/LIVE_TV.md`.
- Player behavior: `docs/PLAYER.md`.
- Movies behavior: `docs/MOVIES.md`.
- Series behavior: `docs/SERIES.md`.

## Flutter Drift Parity

See `docs/DATABASE.md` for mapping. Native keeps behavior concepts but intentionally avoids line-for-line schema copying. Passwords move to `SecretStore`; catalog names gain normalized fields; favorites/history use stable composite identities.
## Phase 9 Search And My List

Search and My List follow the existing Compose -> ViewModel -> Repository -> Room boundary. `SearchRepository` searches only the selected provider. `MyListRepository` composes shared favourites/history repositories instead of creating duplicate library state.

## Phase 10 App Shell

Home, Providers, and Settings follow the same Compose -> ViewModel -> Repository boundary. Provider switching persists only selected provider id in DataStore. Refresh delegates to existing provider repositories. Delete uses provider-scoped Room cascades plus secret cleanup.

Settings also owns native theme selection. `WatchioSettingsRepository` persists a theme id in DataStore, maps it to `WatchioThemeState`, and `WatchioNativeApp` collects that state around `WatchioTheme` so Home, Providers, Settings, Search, My List, Live TV, Movies, Series, and detail screens update immediately.

## Phase 11 TV Guide

TV Guide follows the same Compose -> ViewModel -> Repository boundary. `TvGuideRepository` composes `LiveTvRepository`, `EpgRepository`, Room EPG DAOs, and `EpgChannelMatcher`. Play Live loads the existing shared `WatchioPlayerManager`; no Guide player or second ExoPlayer is introduced.
## Phase 12 TV Hardening

TV hardening keeps existing feature architecture intact. Focus fixes are applied at composable boundaries and shared TV controls; player ownership and repository boundaries are unchanged.

## Phase 14 Design System

Phase 14.0 adds central Compose design tokens for spacing, radii, borders, component sizes, icon sizes, posters, motion, typography, and responsive classes. `WatchioFocusableCard` now uses the shared card foundation while keeping its existing API. Live TV action buttons are the first narrow pilot for the new button variants.

Phase 14.1 applies the design system to Home only. Home keeps the same ViewModel state and route callbacks while presenting primary, secondary, and utility navigation with shared cards/buttons. Phase 14.2B keeps the same architecture and changes only the Home composition: Watchio/time/header actions, a balanced three-column loaded-provider dashboard, no-provider setup CTA, and safe placeholder routes for future Sports and Announcements actions.

Phase 14.2D keeps the approved Home composition but changes routing and refresh behavior. Startup now requires device-mode onboarding and a selected provider before Home. Home refresh actions call provider-specific Live, Movies, or Series refresh APIs and persist independent DataStore timestamps.

## Phase 14.2D.2 Xtream Gate
Bootstrap owns Home eligibility. It checks only DataStore onboarding state and provider metadata, and Home is eligible only when an enabled Xtream provider exists. M3U providers remain managed after login but do not satisfy authentication.

# Phase 14.2E Settings Routing

Settings is split into a root category route and focused category routes. The root menu performs no catalog, EPG programme, or playback loading. DataStore-backed settings remain in `SettingsViewModel`; category screens call the same existing setters rather than creating duplicate storage.

## Phase 14.2G Player Settings

Player Settings stays inside the Settings routing model. `WatchioSettingsRepository` owns playback preferences in DataStore. `Media3WatchioPlayerManager`, Live TV, Movies, and Series observe those preferences through injected repositories; no second player, SharedPreferences store, or Room migration is introduced.

## Phase 14.2H Live TV

Live TV remains Compose -> `LiveTvViewModel` -> repositories/player manager. The ViewModel owns selected category, selected channel, search, now/next EPG, favourite state, refresh state, playback requests, and fullscreen requests. Compose renders those states only; it does not query Room, build playback URLs, or read credentials.

Phase 14.2H.2 keeps the same architecture and changes only selected-channel programme presentation. Current/next programme data comes from the existing EPG repository/cache, scoped to the selected provider and selected channel. Room remains schema v6 and no migration is introduced.

Phase 14.2H.3 also stays UI-only. It changes `RightLivePanel` composition to preview + compact info above EPG detail. The same `WatchioPlayerManager`, selected-channel state, EPG state, retry path, and fullscreen callbacks are reused.

Phase 14.2H.4 is also UI-only. It adjusts weights, padding, and spacing in the existing `RightLivePanel`, `ChannelInfoCard`, and `EpgPanel`. Provider, EPG, Media3, Room, DataStore, fullscreen, retry, and Channel Options architecture are unchanged.

Phase 14.2H.5 stays UI-only. It changes compact `ChannelInfoCard` text limits and `EpgPanel` NEXT presentation only. Room remains v6.

Phase 14.2H.5.1 is UI-only. Compact `ChannelInfoCard` now renders only Live TV label and channel name. Programme data remains in `EpgPanel`.

## Phase 14.2I Movies Architecture

Movies follows the shared Compose -> `MoviesViewModel` -> `MoviesRepository` architecture.
- Shared `WatchioPageHeader` with compact icon actions ([🔍 Search], [⋮ More]).
- Category rail: `ALL MOVIES` -> `FAVOURITES` -> `HISTORY` -> provider categories. Category cards are compact (48dp height) with vertically centered text and active horizontal marquee on text overflow.
- Poster grid: 2:3 aspect ratio cards with top-right dark translucent rating badges (`★ X.X`), fixed 52dp title region, and 24dp bottom content padding.
- Movie Details, fullscreen Media3 VOD player, favourites, history, and Room schema v6 are completely preserved.

## Phase 14.2J Series Architecture

Series follows the shared Compose -> `SeriesViewModel` -> `SeriesRepository` architecture in parity with Movies.
- Shared `WatchioPageHeader` with compact icon actions ([🔍 Search], [⋮ More]) and title `SERIES`.
- Category rail: `ALL SERIES` -> `FAVOURITES` -> `HISTORY` -> provider categories. Category cards are compact (48dp height) with vertically centered text and active horizontal marquee on text overflow.
- Poster grid: 2:3 aspect ratio cards with top-right dark translucent rating badges (`★ X.X`), fixed 52dp title region, and 24dp bottom content padding.
- Series hierarchy: `Series → Series Details → Season → Episode → Playback`.
- Modal `SeriesSearchOverlay` and `SeriesOptionsDialog`.
- Series Details, season synthesis, episode playback, history/favourites, and Room schema v6 remain completely preserved.
