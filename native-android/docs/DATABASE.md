# Watchio Native Database

Room schema version: `6`.

Schema export location: `native-android/app/schemas/`.

## Entities

- `providers`: non-secret provider metadata only.
- `categories`: provider-scoped categories by content type.
- `live_streams`: normalized live channel metadata, no credential URLs.
- `vod_streams`: normalized movie/VOD metadata.
- `series`: normalized series metadata.
- `seasons`: provider + series scoped seasons.
- `episodes`: provider + series + episode scoped episodes.
- `favorites`: unique favorite identity by provider/content/subcontent.
- `watch_history`: unique history identity by provider/content/subcontent.
- `epg_channels`: EPG channel contract for later XMLTV phase.
- `epg_programmes`: EPG programme contract for later XMLTV phase.
- `epg_sources`: provider-scoped XMLTV source descriptors and refresh metadata.
- `epg_import_channels`: temporary staging rows for safe EPG refresh.
- `epg_import_programmes`: temporary staging rows for safe EPG refresh.
- `m3u_items`: normalized M3U item catalog with direct URL and per-item playback metadata.
- `m3u_import_items`: temporary staging rows used during M3U refresh/import.
- `app_metadata`: small database smoke/test metadata.
- `movie_details`: lazy Xtream VOD detail cache.
- `tmdb_trailer_caches`: 30-day trailer fallback cache.

## Keys

Provider id scopes every catalog table. Series episodes use `(providerId, seriesId, episodeId)` so episode history cannot collide with movie or series ids. Favorites/history use `(providerId, contentType, contentId, subContentId)` to prevent duplicates without relying on random row ids.

## Foreign Keys

Provider deletion cascades to categories, streams, movies, series, favorites, history, and EPG rows. Series deletion cascades to seasons and episodes. EPG programme deletion cascades from EPG channels.

Provider deletion also cascades to `m3u_items`. Staging rows are cleared by import session.

Provider repository deletion also deletes provider secrets from `SecretStore`; Room cannot cascade into encrypted storage.

## Indexes

Indexes reflect current Watchio query patterns:

- `providerId`: isolate active provider data.
- `providerId + contentType`: category/favorite/history filters.
- `providerId + categoryId`: catalog category lookup.
- `providerId + normalizedName`: future search.
- `providerId + serverOrder`: preserve provider ordering.
- `providerId + lastWatchedAtEpochMs`: recent history.
- `providerId + epgChannelId + start/end`: future EPG window lookup.
- `providerId + contentType/categoryId/playlistOrder`: M3U counts, category lists, and playlist order.

## Secret Policy

Passwords, tokens, full generated stream URLs, and authorization headers are not stored in Room. Xtream stream URLs must be generated later by `PlaybackUrlResolver` with secrets pulled only at resolve time.

## Drift Parity Notes

Native schema maps conceptually to Flutter Drift:

- `playlists` -> `providers` + `SecretStore`
- `categories` -> `categories`
- `liveStreams` -> `live_streams`
- `vodStreams` -> `vod_streams`
- `seriesStreams`/`seriesInfos` -> `series`
- `seasons` -> `seasons`
- `episodes` -> `episodes`
- `favorites` -> `favorites`
- `watchHistories` -> `watch_history`
- `epg_channels`/`epg_programs` -> native EPG contract tables

This is not field-for-field parity. Native intentionally separates secrets and uses normalized names/indexes for future search/EPG matching.

## Migration Policy

Phase 2 uses a destructive fallback only from Phase 1 schema version `1`, because no production user data exists in Native Watchio yet. Phase 5 adds explicit migration `3 -> 4` for EPG source/staging tables. Phase 7 adds explicit migration `4 -> 5` for movie detail and TMDB trailer caches. Phase 8 adds explicit migration `5 -> 6` for richer Series season and episode detail cache fields.

## Xtream Import Notes

Phase 3 imports Xtream categories, live streams, VOD streams, and series list rows into the existing version 2 schema. No credential-bearing stream URLs are stored. Refresh replaces provider-scoped catalog rows in a Room transaction while preserving favorites, watch history, provider settings, and secrets.

## M3U Import Notes

Phase 4 uses shared categories plus `m3u_items` because M3U direct URLs, User-Agent, referrer, catch-up, timeshift, and channel number metadata do not fit Xtream stream rows safely. Import writes to `m3u_import_items` in batches, then replaces active M3U rows only after parsing succeeds. Favorites and history use deterministic `itemId` values so refresh does not break identity when stable metadata remains.

## EPG Import Notes

Phase 5 finalizes EPG source descriptors and XMLTV import. Active `epg_channels` and `epg_programmes` are replaced from staging only after parsing succeeds. Programmes use deterministic IDs based on channel, start, stop, and title. Retention prunes outside 48 hours past and 72 hours future.

## Live TV Notes

Phase 6 adds no schema. Live TV reads provider-scoped categories, Xtream `live_streams`, M3U `m3u_items`, `favorites`, `watch_history`, and EPG programme tables. Xtream playback URLs remain ephemeral and M3U request headers come from persisted playlist metadata.

## Movie Notes

Phase 7 reads Xtream `vod_streams` and M3U `m3u_items` classified as `movie`. Lazy details are cached in `movie_details`; trailer fallback keys are cached in `tmdb_trailer_caches`. Watch progress and resume use shared `watch_history`.

## Series Notes

Phase 8 reads Xtream `series` and M3U `m3u_items` classified as `series`. Lazy Xtream `get_series_info` results populate `seasons` and `episodes`; M3U episodes are grouped from deterministic playlist metadata. Episode history uses `contentType = episode`, `contentId = seriesId`, and `subContentId = episodeId`.
## Phase 9 Library Queries

Phase 9 did not require a schema bump. Search uses existing normalized name columns in `live_streams`, `vod_streams`, `series`, and `m3u_items`. My List uses `favorites`, `watch_history`, and episode rows for labels.

## Phase 10 App Shell

Phase 10 does not change schema version `6`. Home and Providers use existing provider-scoped count queries. Provider deletion continues to rely on Room foreign-key cascades plus repository-level secret deletion.

## Phase 11 TV Guide

Phase 11 keeps Room schema version `6`. TV Guide uses existing `live_streams`, `m3u_items`, `epg_sources`, `epg_channels`, and `epg_programmes` indexes and does not add migrations.
