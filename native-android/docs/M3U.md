# Watchio Native M3U

Phase 4 adds M3U URL and local-file import only. It does not parse XMLTV, build EPG, or start playback.

## Provider Model

- `m3u_url`: provider row stores the canonical playlist URL.
- `m3u_file`: provider row stores the persisted SAF content URI.
- M3U providers do not store secrets. Playlist URLs may contain tokens, so logs use `SensitiveUrlMasker`.

## Parser Behavior

`M3uParser` streams UTF-8 input line by line from OkHttp or `ContentResolver`. It does not read the full playlist into a string in the import path. UTF-8 BOM, LF, and CRLF are handled by the reader.

Supported entry attributes match current Flutter behavior:

- `tvg-id`, `tvg-name`, `tvg-logo`, `tvg-url`, `tvg-rec`
- `tvg-shift`, `timeshift`
- `group-title`, `#EXTGRP:`
- `user-agent`, `http-user-agent`
- `referrer`, `http-referrer`
- `catchup`, `catchup-source`, `catchup-days`
- `tvg-chno`, `channel-number`

Attribute names are case-insensitive. Values may be double quoted, single quoted, or unquoted.

Name fallback:

1. EXTINF display name
2. `tvg-name`
3. `tvg-id`
4. filename/path from URL

Category fallback preserves Flutter parity:

1. `group-title`
2. `#EXTGRP`
3. `Diğer`

The fallback is intentionally preserved for parity. Later localization can render this semantic fallback differently.

## Classification

The classifier preserves Flutter URL/name heuristics:

- contains `movie` -> Movie
- contains `series` -> Series
- otherwise -> Live

HLS media manifests are guarded by `#EXT-X-TARGETDURATION`, `#EXT-X-MEDIA-SEQUENCE`, and `#EXT-X-PLAYLIST-TYPE` so segment manifests are not imported as channel lists.

Series detection supports:

- `Name S01E01`
- `Name S01 E001`
- `Name Season 1 Episode 2`

## Persistence

M3U uses shared `categories` plus dedicated `m3u_items` because M3U needs direct URLs, per-item headers, catch-up metadata, and timeshift fields. Xtream tables continue to avoid credential-bearing URLs.

Stable item id:

- If `tvg-id` exists: provider + content type + `tvg-id` + normalized name + category.
- Otherwise: provider + content type + normalized name + category + direct URL.

This preserves favorites/history when providers rotate URLs but keep stable channel metadata. Duplicate IDs within one import use Room upsert, so the later item wins consistently.

## Import And Refresh

Import flow:

1. Open URL/file
2. Stream parse
3. Batch stage rows
4. Save categories
5. Replace active `m3u_items` from staging
6. Select provider

Rows are staged in `m3u_import_items`; existing catalog remains available until parsing completes. A failed refresh clears staging and keeps the previous catalog, favorites, history, and settings.

Local files use Android Storage Access Framework. The app stores the content URI and persists read permission when available. If the file is moved, deleted, or permission is revoked, UI shows a safe file unavailable message.

## Playback Metadata

The table preserves direct URL, User-Agent, referrer, content type, catch-up fields, timeshift, and channel number for a later playback phase. No Media3 playback is invoked in Phase 4.

## Security

Debug network logs mask playlist query credentials such as `username`, `password`, and `token`. Tests cover `get.php` and signed M3U query forms. No real provider URLs or playlists are committed.

## Section Refresh

M3U section refresh reopens the provider playlist/file through the existing streaming parser, then replaces only the requested content type. Live, Movies, and Series cache identity remains provider-scoped and stable. Other M3U sections, EPG, favourites, history, and resume state are preserved.

## Intentional Differences From Flutter

- Native streams and stages imports; Flutter currently materializes the full playlist string/list.
- Native uses deterministic item IDs; Flutter generated UUIDs.
- Native persists catch-up, timeshift, channel number, User-Agent, and referrer metadata for future playback.
