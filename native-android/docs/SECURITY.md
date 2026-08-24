# Watchio Native Security

Phase 2 security policy.

## Provider Secrets

Xtream usernames, passwords, API tokens, and future parental PIN material are secrets. They are stored through `SecretStore`, backed on Android by AndroidX Security encrypted storage. They are not stored in Room or DataStore.

M3U URL providers do not store separate credentials, but playlist URLs may contain usernames, passwords, tokens, or signed query strings. Treat playlist URLs as sensitive diagnostics data.

`ProviderCredentialStore` scopes credentials by provider id. Deleting a provider also deletes provider-scoped secrets through repository behavior.

Credential models such as `XtreamCredentials` must not expose values through `toString()`.

## Sensitive URLs

IPTV credentials often appear in query strings and path segments:

- `player_api.php?username=...&password=...`
- `/live/{username}/{password}/{stream}.ts`
- `/movie/{username}/{password}/{stream}.mp4`
- `/series/{username}/{password}/{episode}.mp4`
- `xmltv.php?username=...&password=...`
- `get.php?username=...&password=...&type=m3u_plus`
- `list.m3u?token=...`

All diagnostic logging must pass through `SensitiveUrlMasker`.

Phase 3 implements Xtream URL generation in `PlaybackUrlResolver`. Generated live/movie/series URLs are ephemeral and must be masked before any diagnostic output. The provider add flow authenticates before saving permanent provider metadata and deletes provider-scoped secrets on failure.

Phase 4 masks M3U playlist URLs in diagnostics. M3U direct item URLs are persisted for user-provided playlists, but no playback phase logs them raw.

Phase 5 resolves Xtream XMLTV URLs ephemerally from provider metadata plus `SecretStore`; raw credential-bearing XMLTV URLs are not persisted for Xtream. Custom and M3U-header EPG URLs may be persisted because they are user-provided sources, so backup/export and diagnostics must treat them as sensitive.

Phase 6 resolves Xtream live URLs only at channel-selection time and passes M3U direct URLs/headers directly to Media3. Player diagnostics must not log raw URLs or request headers.

Phase 13 keeps playback error messages user-safe and URL-free. Retry state retains the active in-memory playback request only; it does not persist generated Xtream playback URLs.

Phase 7 resolves Xtream movie URLs only at playback time. TMDB uses `WATCHIO_TMDB_API_KEY` from local Gradle/environment configuration; no TMDB key is committed. YouTube trailer keys are not provider secrets, but provider URLs and request headers remain sensitive.

Phase 8 resolves Xtream episode URLs only at playback time. Episode history stores provider/series/episode identity and progress, never credential-bearing playback URLs. TMDB TV trailer fallback reuses the protected `WATCHIO_TMDB_API_KEY` path and `tmdb_trailer_caches`.

## Logging

Debug builds may log masked HTTP method/URL diagnostics. Release builds must not log full provider URLs, credentials, tokens, or authorization headers.

Crash reporting must strip provider URLs, request headers, usernames, passwords, playlist bodies, and EPG URLs before upload.

## Cleartext IPTV

Many user-supplied IPTV servers still use HTTP. Native Watchio currently permits cleartext traffic so user providers are not blocked. This is a compatibility tradeoff.

Future hardening should scope cleartext as narrowly as Android allows for user-entered hosts, while keeping arbitrary user IPTV endpoints functional.

## API Keys

API keys must not be hard-coded into source. Use remote configuration, server-side proxying, or secure build-time injection depending on feature.

## Backup Policy

Android backup is disabled in the native manifest. Backups must never include provider secrets, tokens, full stream URLs, or private playlist data.
## Search And My List

Search and My List never read `SecretStore` directly and never display credential-bearing Xtream URLs. Navigation uses stable content ids. M3U direct URLs remain in catalog storage from Phase 4, but Phase 9 UI does not render them.

## TV Guide

TV Guide never displays raw XMLTV URLs, playback URLs, usernames, passwords, or request headers. Xtream XMLTV and playback URLs stay ephemeral inside existing EPG and Live TV repositories.

## Account Information

Settings -> Account Information is read-only and safe-display only. It may show a masked Xtream username plus non-secret provider metadata such as display name, expiry, provider type, and refresh timestamps. It never displays passwords, raw SecretStore values, server URLs, XMLTV URLs, playback URLs, request headers, or raw API payloads.

Phase 14.2F.1 also displays safe Xtream connection metadata when available: maximum connections, active connections, and allowed output formats. These values are stored per provider in DataStore and contain no password or credential-bearing URL material.
