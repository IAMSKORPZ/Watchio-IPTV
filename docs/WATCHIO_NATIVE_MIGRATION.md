# WATCHIO NATIVE ANDROID MIGRATION SPECIFICATION

Phase 0 audit only. Existing Flutter app remains reference implementation. No Flutter behavior, schema, player, Gradle, assets, or dependencies should change during this phase.

## 1. Executive summary

Watchio is currently a Flutter app with Provider/ChangeNotifier state, service locator wiring, Drift/SQLite persistence, SharedPreferences plus FlutterSecureStorage secrets, HTTP-based IPTV integration, Media3-backed native Android live playback through a Flutter PlatformView bridge, and legacy/media_kit controllers still present.

Native Android should be Kotlin-first, Compose-first for app UI, Room for structured persistence, DataStore for preferences, encrypted storage for provider secrets, OkHttp/Retrofit for HTTP, coroutine/Flow state, and AndroidX Media3/ExoPlayer for all playback. Do not translate Flutter widgets line-for-line. Extract behavior, data contracts, and UX requirements, then build clean native modules.

Official sources checked on 2026-08-10:

- Android target API requirements: https://developer.android.com/google/play/requirements/target-sdk
- Media3 ExoPlayer overview: https://developer.android.com/media/media3/exoplayer
- Media3 playback app guide: https://developer.android.com/media/implement/playback-app
- Compose focus guide: https://developer.android.com/develop/ui/compose/touch-input/focus
- Compose for TV guide: https://developer.android.com/training/tv/playback/compose

2026 policy impact: Google Play requires new apps and app updates from 2026-08-31 to target Android 16/API 36 or higher, while Android TV apps have an API 34 exception in the current source. Native Watchio should target API 36 unless release constraints say otherwise, then validate TV exception separately.

## 2. Current architecture

Repository structure:

- `lib/`: Flutter app source.
- `lib/screens/`: user-facing screens and feature views.
- `lib/widgets/` and `lib/shared/widgets/`: reusable UI and focus wrappers.
- `lib/controllers/`: Provider/ChangeNotifier controllers.
- `lib/services/`: networking, player, import, storage helpers, app state.
- `lib/repositories/`: IPTV, M3U, search, EPG, providers, favorites.
- `lib/database/`: Drift database, generated code, platform connections.
- `lib/models/`: data models.
- `lib/core/theme/`: theme manager/storage/extensions.
- `android/`: Flutter Android shell plus Kotlin native player bridge.
- `assets/images/`: logos/background/banner assets.
- `test/`: current regression tests.

Flutter/Dart config:

- Dart SDK `^3.9.2`.
- App version `0.0.2+2`.
- Generated localizations enabled.
- Assets: `assets/images/`.
- Main packages: `provider`, `drift`, `sqlite3`, `http`, `cached_network_image`, `flutter_secure_storage`, `shared_preferences`, `file_picker`, `uuid`, `connectivity_plus`, `wakelock_plus`, `permission_handler`, `device_info_plus`, `media_kit`, `video_player`, `audio_service`, `just_audio`.

Android config:

- Namespace/applicationId: `com.watchioiptv.app`.
- `compileSdk = 36`, `targetSdk = 34`, `minSdk = 24`.
- Java/Kotlin JVM target 11.
- Release minification enabled.
- Custom `releaseUnsigned` build type.
- Native deps: `androidx.media3:media3-exoplayer:1.9.2`, `androidx.media3:media3-ui:1.9.2`.
- Manifest supports launcher and leanback launcher, touchscreen not required, leanback not required, cleartext traffic enabled, FileProvider, audio service/media button receiver, Impeller disabled.

## 3. Dependency inventory

Native equivalents:

- Provider/ChangeNotifier -> ViewModel + StateFlow.
- Drift -> Room with explicit migrations.
- SharedPreferences -> DataStore Preferences.
- FlutterSecureStorage -> EncryptedSharedPreferences or Android Keystore-backed store.
- http/HttpClient -> OkHttp + Retrofit, streaming bodies where needed.
- cached_network_image -> Coil.
- media_kit/video_player/native bridge -> Media3 ExoPlayer + PlayerView.
- file_picker -> Android Storage Access Framework.
- url_launcher -> intents.
- wakelock_plus -> Window flags/Media3 wake handling.
- permission_handler/device_info_plus -> AndroidX/core APIs where needed.

## 4. Screen inventory

Discovered `78` `*Screen` classes including private state classes. User-facing screens/pages:

| Current screen | Source file(s) | Purpose | State/deps | Destinations | Native equivalent | Complexity |
|---|---|---|---|---|---|---|
| App initializer | `lib/screens/app_initializer_screen.dart` | Startup/provider restore | app state, providers | playlist/home | Splash/bootstrap route | Medium |
| Playlist selection | `lib/screens/playlist_screen.dart`, `playlist_type_screen.dart`, `playlist_switch_screen.dart` | Select/add provider | PlaylistController, ProviderRepository | Xtream/M3U setup/home | Provider selector | Medium |
| Device mode | `lib/screens/onboarding/device_mode_selection_screen.dart` | Input/device mode setup | input prefs | app flow | Onboarding route | Low |
| Xtream setup | `lib/screens/xtream-codes/new_xtream_code_playlist_screen.dart` | Add Xtream provider | PlaylistController, secure storage | data loader | Provider login | High |
| Xtream loader | `lib/screens/xtream-codes/xtream_code_data_loader_screen.dart` | Import Xtream data | IptvRepository, streaming import | Xtream home | Import progress screen | High |
| Xtream home/dashboard | `lib/screens/xtream-codes/xtream_code_home_screen.dart`, `xtream_code_dashboard.dart`, `v2/xtream_code_dashboard_v2.dart`, `lib/screens/home/watchio_dashboard_home.dart` | Main hub | XtreamCodeHomeController | live, movies, series, settings, search, guide | Home feature | High |
| M3U setup | `lib/screens/m3u/new_m3u_playlist_screen.dart` | Add URL/file playlist | M3uController, parser | M3U loader | M3U provider setup | High |
| M3U loader | `lib/screens/m3u/m3u_data_loader_screen.dart` | Parse/import playlist | StreamingM3uImportService | playlist/home | Import progress | High |
| M3U home | `lib/screens/m3u/m3u_home_screen.dart` | M3U hub | M3UHomeController | items/series/sports/settings | M3U home | Medium |
| M3U item list | `lib/screens/m3u/m3u_items_screen.dart` | M3U live/movie rows | M3uRepository | player/details | M3U catalog | High |
| M3U series | `lib/screens/m3u/series/m3u_series_screen.dart`, `m3u_episode_screen.dart` | Parsed series/seasons/episodes | M3uRepository | player | M3U series feature | Medium |
| Live TV | `lib/screens/live_stream/xtream_live_screen.dart`, `live_stream_screen.dart` | Categories, channels, preview, EPG | XtreamCodeHomeController, EPG storage, player bridge | fullscreen player, search | Live TV feature | Very high |
| Player | `lib/screens/player/unified_player_screen.dart`, `lib/widgets/player_widget.dart`, `m3u_player_screen.dart` | Fullscreen playback/overlay/favorites/history | AppPlayerController, FavoritesController, WatchHistoryService | back to source | Media3 player route | Very high |
| Movies | `lib/screens/movies/xtream_movies_screen.dart`, `movie_details_screen.dart` | VOD categories/grid/details | XtreamCodeHomeController, IptvRepository, TMDB, favorites | player/search | Movies feature | High |
| Series | `lib/screens/series/xtream_series_screen.dart`, `series_details_screen.dart`, `episode_screen.dart` | Series categories/details/seasons/episodes | IptvRepository, TMDB, history/favorites | player/search | Series feature | High |
| Category detail | `lib/screens/category_detail_screen.dart`, `widgets/category_detail/*` | Generic category grid/list | CategoryDetailController | details/player | Shared catalog screen | Medium |
| Search | `lib/screens/search_screen.dart` | Home/live/movies/series search | SearchRepository/IptvRepository | details/player | Search feature | High |
| Watch history/My List | `lib/screens/watch_history_screen.dart`, `widgets/watch_history/*` | History and favorites lists | WatchHistoryController, FavoritesRepository | details/player | My List/history | Medium |
| Settings shell | `lib/screens/settings/watchio_settings_screen.dart`, `widgets/watchio_settings_scaffold.dart` | Settings navigation | Provider/Theme/Input services | settings pages | Settings NavHost | Medium |
| Appearance/theme | `appearance_screen.dart`, `sections/appearance_page.dart` | Theme customization | ThemeManager/ThemeStorage | color pickers | Theme settings | Medium |
| Playback/subtitle/input | `playback_settings_screen.dart`, `subtitle_settings_section.dart`, `sections/input_mode_page.dart`, `stream_format_page.dart` | Player/input settings | SharedPreferences | none | Player settings | Medium |
| Provider management | `provider_list_screen.dart`, `provider_form_screen.dart`, `sections/provider_management_page.dart` | Add/edit/remove providers | ProviderController, secure storage | home/import | Provider management | High |
| EPG settings | `sections/epg_settings_page.dart` | EPG source/autoload | SharedPreferences, EpgSourceService | none | EPG settings | Medium |
| Backup/restore | `sections/backup_restore_page.dart` | Settings export/import | SettingsBackupService | none | Backup settings | Medium |
| Account/parental | `sections/account_info_page.dart`, `parental_controls_page.dart` | Account info/PIN | providers, secure storage | PIN dialog | Account/security | Medium |
| Announcements | `announcements_screen.dart`, `announcement_center_screen.dart` | Remote announcements | AnnouncementService | details | Announcements | Low |
| Maintenance/update | `maintenance_screen.dart`, `update/update_screen.dart` | Remote config/update install | UpdateService, ApkInstallerService | installer | Maintenance/update | Medium |
| Sports | `sports/sports_hub_screen.dart` | Football data/cache | FootballDataService | dialogs | Optional sports feature | Medium |
| Local media | `local_media/local_media_library_screen.dart` | Local library/player | file access, player | player | Optional local media | Medium |
| Stalker | `stalker/stalker_home_screen.dart` | Stalker provider placeholder/integration | Stalker services | TBD | Out of Android IPTV scope unless retained | Medium |

Dialogs/bottom sheets/menus exist across setup, player controls, channel selector, delete confirmations, color picker, catalog setup, update available, parental PIN, and sports details. Native should model them as Compose dialogs/sheets with TV focus order.

## 5. Navigation map

Current navigation uses imperative `Navigator.push`, `pushReplacement`, and `pushAndRemoveUntil` with `MaterialPageRoute`. No GoRouter-style graph exists.

Observed flow:

- App starts at `AppInitializerScreen`.
- If no provider, route to playlist/provider creation.
- Provider types: Xtream and M3U are primary; Stalker exists.
- Xtream setup -> `XtreamCodeDataLoaderScreen` -> Xtream home/dashboard.
- M3U setup -> `M3uDataLoaderScreen` -> playlist/home.
- Home tiles route to Live TV, Movies, Series, My List/history, TV Guide/search/settings/sports/announcements/provider switch.
- Live TV category/channel view can open preview playback and fullscreen `UnifiedPlayerScreen`.
- Movies/Series category grids open details, then playback.
- Back is mostly Navigator pop; some settings/provider operations replace route to refresh state.

Desired native graph:

- `bootstrap` -> `provider/select` or `home/{providerId}`.
- Nested home graph: `live`, `movies`, `series`, `search`, `mylist`, `settings`.
- Details: `movie/{id}`, `series/{id}`, `season/{number}`, `episode/{id}`.
- Player route should share `WatchioPlayerManager` with preview when source is Live TV.
- Dialogs explicit route/sheet nodes for TV focus.

Live TV desired lifecycle:

1. Enter Live TV: preview player can be idle until channel selected.
2. Channel selected: preview starts.
3. Fullscreen: same Media3 session should move from preview surface to fullscreen surface.
4. Back: same session returns to preview where appropriate.
5. Leave Live TV: stop/release according to feature lifecycle.

## 6. Provider architecture

Supported provider/input mechanisms found:

- Xtream Codes via `NewXtreamCodePlaylistScreen`, `IptvRepository`, `XtreamStreamingImportService`.
- M3U URL/file via `NewM3uPlaylistScreen`, `M3uParser`, `StreamingM3uImportService`.
- Local media library screen exists.
- Stalker services/screens exist but are not in requested expected primary list.

Storage:

- Drift `playlists` stores id/name/type/url/username and sets password null in `insertPlaylist/updatePlaylist`.
- `SecureStorageService` stores provider password and username under `secure_v1_provider_{id}.*`.
- `SharedPreferencesProviderRepository` persists provider list/metadata.

Native:

- `ProviderRepository`: Room provider row, encrypted secret store, provider switcher.
- `ProviderSession`: loaded secrets in memory only; never log full URLs with credentials.
- DataStore for selected provider id and non-secret options.

## 7. M3U implementation

Source: `lib/services/m3u_parser.dart`.

Parser behavior:

- Reads file with UTF-8, reads URL with `HttpClient` and UTF-8 decoder.
- Splits by newline, trims each line.
- On `#EXTINF`, extracts metadata before comma and display name after comma.
- Attribute regex is case-insensitive and supports double quotes, single quotes, and unquoted values.
- Supports `tvg-id`, `tvg-name`, `tvg-logo`, `tvg-url`, `tvg-rec`, `tvg-shift`, `timeshift`, `group-title`, `user-agent`, `http-user-agent`, `referrer`, `http-referrer`, `catchup`, `catchup-source`, `catchup-days`, `tvg-chno`, `channel-number`.
- Stores only columns present in `M3uItem`: URL, name, tvg fields, group, `userAgent`, `referrer`, content type. Catchup attrs are parsed but not persisted in current `M3uItem`.
- Supports `#EXTGRP:` as `group-name`.
- Fallback channel name: EXTINF name -> `tvg-name` -> `tvg-id` -> filename from URL.
- Fallback group: `group-title` -> `group-name` -> `Diğer`.
- Content type detection is URL substring-based: contains `movie` -> VOD, contains `series` -> series, else live.
- Series detection supports `Name S01 E002` and `Name Season 1 Episode 2`.

Native parser requirements:

- Streaming parser; do not load huge playlists entirely when avoidable.
- Preserve attr compatibility and fallback naming.
- Persist catchup fields in new Room schema if catch-up is product behavior.
- Fixture tests: same sample M3U through Dart parser and Kotlin parser, compare normalized channel model excluding random UUID.

## 8. Xtream implementation

Source: `lib/repositories/iptv_repository.dart`, `lib/services/xtream_streaming_import_service.dart`, models.

API:

- Base URL + `player_api.php`.
- Params include username/password from `ApiConfig.baseParams`.
- `getPlayerInfo`: no action.
- Live categories: `action=get_live_categories`.
- VOD categories: `action=get_vod_categories`.
- Series categories: `action=get_series_categories`.
- Live streams: `action=get_live_streams`, optional `category_id`.
- VOD streams: `action=get_vod_streams`, optional `category_id`; tracks server order.
- Series: `action=get_series`, optional `category_id`; tracks server order.
- VOD details: `action=get_vod_info`, `vod_id`.
- Series info: `action=get_series_info`, `series_id`.
- EPG source construction uses `xmltv.php?username=...&password=...`.
- Stream URL generation exists in `build_media_url.dart` and `playback_url_resolver.dart`: `/live/{user}/{pass}/{id}.{ext}`, `/movie/{user}/{pass}/{id}.{ext}`, `/series/{user}/{pass}/{id}.{ext}`, `/timeshift/{user}/{pass}/{duration}/{start}/{id}.ts`.

Native boundaries:

- `XtreamApi`: Retrofit endpoints.
- `XtreamRepository`: auth/session/data import.
- `XtreamImportWorker`: streamed JSON imports into Room transactions.
- `PlaybackUrlResolver`: centralized, masks credentials in logs.

## 9. XMLTV/EPG implementation

Sources: `lib/services/epg_import_service.dart`, `epg_storage_service.dart`, `epg_source_service.dart`.

Current behavior:

- HTTP import uses `http.Client().send` stream, transforms with UTF-8 decoder.
- File import uses `File.openRead`.
- `_xmlElements` incrementally yields complete `<channel>` and `<programme>` elements from chunks. This avoids full-document loading.
- Parses channel `id`, display-name, icon source via regex.
- Parses programme `channel`, `start`, `stop`, `title`, `desc`.
- XMLTV time parser supports `yyyyMMddHHmmss` plus optional `+HHMM`/`-HHMM` offset, converts to UTC then local on read.
- Batch insert size default 500.
- Persists custom SQL tables outside Drift table list: `epg_channels`, `epg_programs`.
- Indexes: `idx_epg_program_window(playlist_id, epg_channel_id, start_time, end_time)`, `idx_epg_channel_name(playlist_id, display_name)`.
- Prunes programs outside 48h past and 72h future.
- EPG channel lookup tries exact display name, case-insensitive id/name, compact normalized LIKE, and multiple channel keys.
- URL masking covers `username` and `password`.

Gzip/Brotli: current code does not explicitly decompress gzip or Brotli. If server sends decoded HTTP bodies, package behavior may help, but no manual compressed XMLTV handling is present in inspected code. Native must explicitly support `gzip` and Brotli where provider sends compressed guide files.

Native:

- OkHttp streaming response body.
- Detect content encoding and extension (`.gz`, `.br`) before XML pull parsing.
- Use `XmlPullParser`/streaming parser, never DOM for large XMLTV.
- Room EPG tables or dedicated SQLite DAO with indexed queries.

## 10. Database/schema

Current DB: Drift/SQLite, database name `watchio`, schema version `12`.

Drift tables:

- `playlists`: id PK, name, type, url, username, password, createdAt.
- `categories`: PK `(categoryId, playlistId, type)`, categoryId, categoryName, parentId, playlistId, type, createdAt, updatedAt.
- `userInfos`: id PK, playlistId, username, password, message, auth, status, expDate, isTrial, activeCons, createdAt, maxConnections, allowedOutputFormats.
- `serverInfos`: id PK, playlistId, url, port, httpsPort, serverProtocol, rtmpPort, timezone, timestampNow, timeNow.
- `liveStreams`: PK `(streamId, playlistId)`, streamId, name, streamIcon, categoryId, epgChannelId, playlistId, createdAt.
- `vodStreams`: PK `(streamId, playlistId)`, streamId, name, streamIcon, categoryId, rating, rating5based, containerExtension, playlistId, createdAt, genre, youtubeTrailer, serverOrder.
- `seriesStreams`: PK `(seriesId, playlistId)`, seriesId, name, cover, plot, cast, director, genre, releaseDate, rating, rating5based, youtubeTrailer, episodeRunTime, categoryId, playlistId, createdAt, lastModified, backdropPath, serverOrder.
- `seriesInfos`: id PK, seriesId, name, cover, plot, cast, director, genre, releaseDate, lastModified, rating, rating5based, backdropPath, youtubeTrailer, episodeRunTime, categoryId, playlistId, tmdbId.
- `seasons`: id PK, seriesId, airDate, episodeCount, seasonId, name, overview, seasonNumber, voteAverage, cover, coverBig, playlistId.
- `episodes`: id PK, seriesId, episodeId, episodeNum, title, containerExtension, season, customSid, added, directSource, playlistId, tmdbId, releasedate, plot, durationSecs, duration, movieImage, bitrate, rating.
- `watchHistories`: PK `(playlistId, streamId)`, playlistId, contentType, streamId, seriesId, watchDuration, totalDuration, lastWatched, imagePath, title.
- `m3uItems`: id PK, playlistId, url, name, tvgId, tvgName, tvgLogo, tvgUrl, tvgRec, tvgShift, groupTitle, groupName, userAgent, referrer, categoryId, contentType, createdAt, updatedAt.
- `m3uSeries`: PK `(playlistId, seriesId)`, playlistId, seriesId, name, categoryId, cover.
- `m3uEpisodes`: PK `(playlistId, seriesId, seasonNumber, episodeNumber)`, playlistId, seriesId, seasonNumber, episodeNumber, name, url, categoryId, cover.
- `favorites`: id PK, playlistId, contentType, streamId, episodeId, m3uItemId, name, imagePath, createdAt, updatedAt.
- `footballCaches`: cacheKey PK, responseJson, cachedAt, expiresAt.
- `tmdbTrailerCaches`: PK `(tmdbId, type)`, tmdbId, type, trailerKey, cachedAt.

Custom EPG tables:

- `epg_channels`: PK `(playlist_id, epg_channel_id)`, display_name, icon_url, updated_at.
- `epg_programs`: PK `(playlist_id, epg_channel_id, program_id)`, title, description, start_time, end_time, updated_at.

Migration history:

- v4 fixes xstream -> xtream string.
- v5 M3U items.
- v6 M3U series/episodes.
- v8 favorites.
- v9 football cache and VOD metadata.
- v10 series TMDB id.
- v11 TMDB trailer cache.
- v12 VOD/series server order.

Room recommendation:

- Keep `schemaVersion = 1` for new native DB, with entities matching current behavior but improving indexes and secrets separation.
- Do not store provider password in primary Room provider row.
- Add missing persisted M3U catchup fields if native catch-up uses them.
- Add normalized columns for search and EPG matching.

## 11. Player architecture

Current Android playback:

- `android/app/src/main/kotlin/.../NativeLivePlayerManager.kt` owns native Media3 ExoPlayer sessions keyed by `playerId`.
- Flutter registers MethodChannel/EventChannel/PlatformView in `MainActivity.kt`.
- `NativeLivePlayerSession` builds ExoPlayer with `DefaultHttpDataSource.Factory`, cross-protocol redirects, request headers, `DefaultMediaSourceFactory`.
- It attaches/detaches `PlayerView` surfaces without necessarily destroying session.
- Emits `isPlaying`, `isBuffering`, position, duration, hasAudio, hasVideo, firstFrame, error.
- MIME detection for `.m3u8`, `.mpd`, `.ts`, `.mp4`.
- Uses no built-in controller.
- Error state stores `PlaybackException` message/code.

Flutter-side player files include `native_live_player_controller.dart`, `exoplayer_controller.dart`, `media_kit_player_controller.dart`, `player_factory.dart`, `player_state.dart`, `unified_player_screen.dart`, and `player_widget.dart`. MediaKit is still present but should not be target for native Android.

Native target:

- Use `WatchioPlayerManager` as singleton/service scoped to app process or activity retained component.
- One Media3 `ExoPlayer` per active playback session unless PiP/multiple preview later requires more.
- Expose `StateFlow<WatchioPlayerState>`.
- Surface handoff API: attach preview `PlayerView`, attach fullscreen `PlayerView`, detach without release.
- Use MediaSession only if Android media controls/background behavior is needed.
- Track selection via Media3 TrackSelection APIs.
- Central retry/error policy, not per-screen hacks.

## 12. Movies

Current behavior:

- Movies screen loads categories and VOD streams from cached DB through `XtreamCodeHomeController/IptvRepository`.
- Virtual categories: All, Favorites, History.
- Grid supports category filtering and search navigation.
- `MovieDetailsScreen` loads VOD info and TMDB trailer fallback, has Smarters-style details, favorite toggle, play action.
- VOD stream URLs use Xtream movie path with container extension/suffix.
- TMDB trailer cache is 30 days.

Native:

- Compose lazy grids with paging from Room.
- `MoviesViewModel`, `MovieDetailsViewModel`.
- Preload only visible poster images with Coil.
- Keep server order column.

## 13. Series

Current behavior:

- Series screen mirrors movies: categories, virtual favorites/history, grid.
- `SeriesDetailsScreen` fetches/caches series info, seasons, episodes.
- Missing seasons may be synthesized from episode payload.
- Episode playback uses series URL.
- TMDB trailer fallback for TV.

Native:

- `SeriesRepository` handles list and detail caching.
- `SeriesDetails` Compose screen: poster, rating, metadata, plot, play, season selector, episode list.
- Room transaction replaces detail rows for one series.

## 14. Search

Current behavior:

- `SearchScreen` supports scoped search.
- Home search is expected to cover live, movies, series.
- Movies search scopes movies; series search scopes series.
- DB search methods use `contains`/LIKE with limit default 20.

Native:

- `SearchRepository` with debounce, query normalization, Flow.
- Use Room FTS or normalized indexed columns for large playlists.
- Avoid repeat network work; search local imported catalogs.

## 15. Favourites

Current behavior:

- `favorites` table stores playlistId, contentType, streamId, optional episodeId, optional m3uItemId, name, imagePath.
- `FavoritesRepository` reconstructs content item differently for Xtream and M3U.
- Virtual favorite category is injected into live/movie/series category flows.
- Favorite toggle exists in details/player/list contexts.

Native:

- Single Favorite entity with provider/content identity.
- Uniqueness should include playlistId, type, streamId, and episodeId/m3uItemId.
- DAO should resolve favorites to display models with joins or repository lookups.

## 16. History

Current behavior:

- `watchHistories` keyed by `(playlistId, streamId)`.
- Stores contentType, optional seriesId, watchDuration, totalDuration, lastWatched, imagePath, title.
- Player saves history periodically for VOD and once for live recent.
- Used as virtual History category.

Native:

- Separate `WatchHistory` entity; consider including episode id in key to avoid collision.
- Background/periodic save in player manager.
- Resume VOD/episodes where watchDuration exists.

## 17. TMDB

Source: `lib/services/tmdb_service.dart`.

Current behavior:

- TMDB API key is hard-coded in source. Do not print value in reports/logs.
- Details endpoint: `/movie/{id}` or `/tv/{id}`.
- Videos endpoint: `/movie/{id}/videos` or `/tv/{id}/videos`.
- Trailer preference: official YouTube Trailer -> YouTube Trailer -> any YouTube.
- Cache table `tmdb_trailer_caches`, 30-day freshness.

Native:

- Do not embed API key directly in source.
- Use remote config/build config or backend proxy depending release model.
- Keep 30-day trailer cache.

## 18. Settings

Settings found:

| Setting area | Storage | UI | Native storage |
|---|---|---|---|
| Theme colors/custom theme | SharedPreferences via ThemeManager/ThemeStorage | Appearance/color pickers | DataStore JSON or typed keys |
| Input mode | SharedPreferences/InputModeController | Input mode page | DataStore |
| Stream format | SharedPreferences | Stream format page | DataStore |
| EPG source/autoload | SharedPreferences/EpgSourceService | EPG settings | DataStore |
| Playback settings | SharedPreferences/model | Playback settings | DataStore |
| Subtitle settings | SharedPreferences | Subtitle settings | DataStore |
| Provider management | SharedPreferences + secure storage + Drift | Provider pages | Room + encrypted secrets |
| Parental controls/PIN | SharedPreferences + secure storage | Parental page/dialog | Encrypted store + DataStore flags |
| Announcements/remote config/update | SharedPreferences/cache | Settings/update screens | DataStore/cache |
| Backup/restore | SharedPreferences export with denylist | Backup page | Export safe prefs only |

## 19. Theme system

Sources: `lib/core/theme/*`, settings appearance pages.

Current behavior:

- Theme customization stored in SharedPreferences.
- App has custom color model and manager, with live updates through Provider/ChangeNotifier.
- Native must retain user customization.

Default native palette:

- Base: `#050712`, `#0B1020`, `#101426`, `#111327`.
- Text: `#F8F8FC`, `#B7BAC8`, `#8E92A8`.
- Pink: `#FF3D9A`, `#FF58B0`, `#B51F70`.
- Purple: `#A855F7`, `#C45CFF`, `#7437D8`.
- Turquoise: `#20D9D2`, `#39EEE5`, `#129C9A`.
- Live TV = Pink, Movies = Purple, Series = Turquoise.
- Focus default `#FFFFFF`, optional glow `#D95CFF`.

Native Compose:

- `WatchioTheme` maps DataStore theme state to Material 3 color scheme plus custom `WatchioColors`.
- No hard-coded immutable palette; defaults only.
- TV focus colors part of theme extension.

## 20. TV/remote input

Current Flutter:

- `FocusableActionDetector`, FocusNodes, custom `tv_focusable.dart`, focus wrappers, keyboard handling, player overlay focus nodes.
- Manifest supports leanback launcher and touchscreen optional.
- Live TV/player/search/login/settings use multiple FocusNodes and manual navigation.

Native requirements:

- Treat DPAD as first-class.
- Use Compose focus APIs: `focusRequester`, `focusGroup`, `focusProperties`, `onFocusChanged`, save/restore focused item.
- Use TV Material components where appropriate.
- Player overlay must support DPAD_CENTER/Enter, Back, arrows, escape, touch, mouse.
- Initial focus explicit on every TV screen and dialog.

## 21. Assets

Assets found:

- `assets/images/logo.png`
- `assets/images/logo_icon.png`
- `assets/images/App_Logo.png`
- `assets/images/background.png`
- `assets/images/tv_banner.png`
- Android launcher icons in mipmap densities.
- Android TV banner `android/app/src/main/res/drawable-nodpi/tv_banner.png`.
- Web icons/favicon.
- iOS/macOS icons and launch images.

Native can reuse logo/banner/background assets directly. Do not duplicate large assets during audit.

## 22. Performance findings

Risks:

- Huge Xtream JSON lists.
- Huge M3U files currently loaded fully for parse in basic parser; streaming import service exists and should be preferred.
- Huge XMLTV files need streaming; current EPG does streaming element extraction.
- Search uses LIKE/contains and may degrade on massive catalogs.
- Poster grids can overfetch images.
- EPG channel matching has SQL LIKE and some in-memory fallback.
- Player startup/retry state split across controllers/screens.

Native recommendations:

- Room indexes on provider/category/content/search keys.
- FTS or normalized search tables.
- Paging 3 for catalog grids.
- OkHttp streaming + kotlinx serialization streaming where feasible.
- Coil memory/disk cache tuned for TV grids.
- XML pull parsing with batch DB writes.
- Structured concurrency with import cancellation tokens.

## 23. Security findings

Do not expose secrets:

- TMDB API key hard-coded in source.
- Football API key hard-coded in source.
- Xtream credentials used in API/query/path URLs.
- Some logs mask resolved playback/EPG URLs, but audit all `debugPrint` before release.
- `usesCleartextTraffic=true` required for many IPTV providers but is a risk.
- Provider passwords migrated to FlutterSecureStorage, but legacy SharedPreferences migration exists.
- Playlist info widget can reveal/copy password by user action.

Native:

- Central URL masker.
- No credentials in Room, logs, crash reports, analytics, screenshots, backup exports.
- Encrypted local secret storage.
- Network security config scoped if possible; allow cleartext only where user provider requires.

## 24. Known bugs/workarounds

Evidence found:

- MediaKit remains but should not guide native Android; Media3 bridge is current intentional path.
- Player code has retry/error overlay defenses and first-frame/hasVideo state to avoid false failure overlays.
- Live TV preview/fullscreen lifecycle requires careful session ownership.
- Server order migration/refresh exists for movies/series where old rows have `serverOrder = 0`.
- EPG code contains memory-efficient streaming patches.
- Favourites/history virtual categories require resolving IDs back to content rows.
- Flutter focus/navigation complexity is high and should be redesigned with native focus state.

Do not reproduce:

- Per-screen unmanaged player creation.
- False error overlay hacks.
- Line-for-line focus node wiring.
- Hard-coded API keys.
- Loading massive XMLTV/M3U into memory in native import paths.

## 25. Native target architecture

Recommended architecture:

```text
UI: Jetpack Compose + TV Material + PlayerView interop
ViewModel: StateFlow immutable UI state
Domain: small use cases only where they remove duplication
Data: repositories for Xtream, M3U, EPG, TMDB, provider, favorites, history
Persistence: Room + DataStore + encrypted secrets
Network: OkHttp + Retrofit
Playback: WatchioPlayerManager + Media3 ExoPlayer
Images: Coil
Background: WorkManager for refresh/import where useful
```

Keep architecture pragmatic. Use use cases for cross-feature behavior like provider refresh, search, playback URL resolution, favorite toggle, history save.

## 26. Native package structure

Recommended:

```text
com.watchio.app
  core/
    database/
    datastore/
    network/
    security/
    player/
    model/
    util/
    image/
  data/
    provider/
    xtream/
    m3u/
    epg/
    tmdb/
    favorites/
    history/
    settings/
  domain/
    playback/
    search/
    import/
  feature/
    bootstrap/
    provider/
    home/
    livetv/
    epg/
    movies/
    series/
    search/
    mylist/
    settings/
    player/
  ui/
    components/
    theme/
    focus/
```

## 27. Flutter -> Native technology mapping

| Flutter current | Native target |
|---|---|
| Flutter widgets | Jetpack Compose |
| FocusNode/FocusableActionDetector | Compose focus APIs + TV Material |
| Provider/ChangeNotifier | ViewModel + StateFlow |
| Drift | Room |
| SharedPreferences | DataStore |
| FlutterSecureStorage | EncryptedSharedPreferences/Keystore |
| http/HttpClient | OkHttp/Retrofit |
| cached_network_image | Coil |
| media_kit/video_player/native bridge | Media3 ExoPlayer |
| PlatformView PlayerView bridge | Direct PlayerView interop in Compose |
| Dart parser services | Kotlin streaming parsers |

## 28. Behavioural parity matrix

| Feature | Flutter | Native target | Risk |
|---|---:|---:|---|
| Xtream login | Yes | Required | High |
| Provider switching | Yes | Required | High |
| M3U URL/file import | Yes | Required | High |
| Local M3U | Yes | Required | High |
| Stalker | Partial/present | Decide before Phase 1 | Medium |
| Live TV categories/channels | Yes | Required | High |
| Live preview | Yes | Required/improve lifecycle | Very high |
| Fullscreen player | Yes | Required | Very high |
| Media3/ExoPlayer Android playback | Yes bridge | Required direct native | Very high |
| EPG/XMLTV | Yes | Required | High |
| XMLTV streaming parse | Yes | Required | High |
| XMLTV gzip/Brotli | Not explicit | Required | High |
| M3U attr compatibility | Yes | Required | High |
| Catch-up/timeshift URL | Present | Required if enabled | High |
| Movies grid/details | Yes | Required | Medium |
| Series/seasons/episodes | Yes | Required | Medium |
| Search scopes | Yes | Required | Medium |
| Favorites | Yes | Required | Medium |
| History/resume | Yes partial | Required | Medium |
| TMDB trailers/cache | Yes | Required with safer key | Medium |
| Theme customization | Yes | Required | Medium |
| Settings | Yes | Required | Medium |
| TV remote input | Yes | Required/improve | High |
| Keyboard/touch/mouse | Yes partial | Required | Medium |
| Sports/local media/announcements/update | Present | Decide scope | Medium |

## 29. Testing strategy

Unit tests:

- M3U parser fixtures, including quoted/unquoted attrs, malformed lines, fallback names, user-agent/referrer, timeshift/catchup.
- Xtream DTO mapping for auth, categories, live, VOD, series, series info, episodes.
- XMLTV parser timezones, channel mapping, compressed inputs, malformed programmes.
- Playback URL resolver masks credentials and builds correct paths.
- Room migrations and indexes.
- Favorites uniqueness and resolution.
- History save/resume.
- Theme persistence.
- Search scopes.

Instrumentation/UI tests:

- DPAD navigation per main screen.
- Initial focus and focus restore.
- Back behavior.
- Preview -> fullscreen -> preview handoff without stream restart where possible.
- Player state transitions: Idle, Connecting, Buffering, Playing, Paused, Ended, Failed.
- Phone, tablet, 720p TV, 1080p TV, 4K TV layouts.

## 30. Migration phases

Recommended 16 phases:

1. Phase 0: audit/specification.
2. Phase 1: native project foundation beside Flutter.
3. Phase 2: core Gradle, package structure, DI, logging, theme defaults.
4. Phase 3: Room/DataStore/encrypted secrets.
5. Phase 4: provider login/switching.
6. Phase 5: Xtream API/import.
7. Phase 6: M3U parser/import.
8. Phase 7: XMLTV/EPG parser/storage.
9. Phase 8: Home/dashboard.
10. Phase 9: Live TV catalog and preview.
11. Phase 10: WatchioPlayerManager + fullscreen Media3.
12. Phase 11: Movies/details/TMDB.
13. Phase 12: Series/seasons/episodes.
14. Phase 13: Search/My List/Favorites/History.
15. Phase 14: Settings/theme/input/backup.
16. Phase 15: Android TV/Fire TV performance, security, release hardening, parity testing.

## 31. Risks

- IPTV provider variability and malformed playlists.
- XMLTV size and compressed guide formats.
- Credential leakage through URLs/logging.
- Player lifecycle regressions, especially Live preview/fullscreen.
- TV focus regressions.
- Current Flutter app has mixed old/new player paths.
- Database parity across Drift custom tables and future Room schema.
- API key handling must change before production.
- Google Play target SDK deadline: current Flutter Android target is 34; native should plan API 36.

## 32. Recommended Phase 1 implementation plan

Do only after approval:

1. Create native Android project beside Flutter, not replacing it.
2. Set Kotlin-first Gradle with target/compile API 36, min API 24 unless device support changes.
3. Add Compose, TV Material, Navigation, Lifecycle ViewModel, Coroutines, Room, DataStore, OkHttp/Retrofit, Coil, Media3.
4. Add skeleton packages only.
5. Add logging masker and secret store first.
6. Add empty `WatchioTheme` with default palette plus customizable model.
7. Add baseline test harness and fixture folder.
8. No feature implementation until core foundation reviewed.

