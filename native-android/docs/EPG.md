# Watchio Native EPG

Phase 5 adds XMLTV/EPG data foundations only. It does not start Media3, show live playback, or build the final TV Guide UI.

## Sources

EPG sources are provider-scoped rows in `epg_sources`.

- `xtream_xmltv`: stores no URL. Refresh resolves `{base}/xmltv.php?username=...&password=...` from provider metadata and `SecretStore`.
- `m3u_header`: stored from `#EXTM3U` attributes. Supported precedence is `url-tvg`, then `x-tvg-url`, then `tvg-url`.
- `custom_url`: stores a user/custom URL when configured by repository call.

Xtream credentials are never duplicated in Room. M3U/custom URLs may contain tokens, so logs and errors pass through `SensitiveUrlMasker`.

## Download And Compression

EPG uses the existing OkHttp stack. Responses are streamed and support redirects, timeouts, HTTP errors, `304 Not Modified`, `ETag`, and `Last-Modified`.

Supported bodies:

- plain XML
- gzip by `Content-Encoding`, `.gz`, or `.gzip`
- Brotli by `Content-Encoding: br` or `.br`

## XML Parser

`XmlTvParser` uses a pull parser. It does not build DOM and does not read the whole guide into a string.

Parsed channel fields:

- `channel id`
- first valid `display-name`
- `icon src`

Parsed programme fields:

- `channel`
- `start`
- `stop`
- first valid `title`
- first valid `desc`

Malformed individual programmes are skipped when required fields or timestamps are invalid.

## Timezone

XMLTV timestamps are normalized to UTC epoch milliseconds.

Supported examples:

- `yyyyMMddHHmmss +0000`
- `yyyyMMddHHmmss -0500`
- `yyyyMMddHHmmss +0130`
- missing offset

Missing offset follows current Flutter behavior: interpret the timestamp as UTC.

## Database

Room schema version is currently `6`. EPG tables were introduced earlier and remain part of the active v6 schema.

Production tables:

- `epg_sources`
- `epg_channels`
- `epg_programmes`

Staging tables:

- `epg_import_channels`
- `epg_import_programmes`

Programme identity is deterministic: hash of XMLTV channel id, start, stop, and title.

Indexes cover provider/channel lookups, channel name matching, and programme time windows.

## Refresh

Refresh stages into import tables first. Active EPG is replaced only after parsing succeeds, so failed refresh keeps the old guide available offline.

Batch size is `1,000`.

Retention follows Flutter defaults:

- keep 48 hours past
- keep 72 hours future

## Matching

`EpgChannelMatcher` centralizes matching.

Priority:

1. exact tvg-id / Xtream `epgChannelId` == XMLTV channel id
2. case-insensitive id
3. exact display name
4. normalized display name
5. compact normalized name, only when unambiguous

Ambiguous compact matches return no match.

No persistent match cache exists yet. Matching can be recomputed after M3U/Xtream refresh or EPG refresh.

## Queries

Repository/DAO support:

- current programme
- next programme
- programme window for one channel
- guide window for many channels
- now/next progress clamped from 0 to 1

All queries are provider-scoped and use Room data, so offline startup can use the cached guide.

## Background Work

WorkManager periodic refresh is not scheduled in Phase 5. Manual refresh/repository architecture is implemented first to avoid aggressive background network work.

## Phase 11 TV Guide Reuse

TV Guide uses `EpgRepository.guide()` for bounded provider-scoped windows and `EpgChannelMatcher` for existing matching precedence. Manual guide refresh calls `EpgRepository.refresh()`, so staging and failed-refresh preservation stay unchanged.

## Phase 13.1E Source Discovery

`EpgRepository.refresh()` now resolves a bounded provider-scoped source list instead of failing immediately when the selected source is missing.

Priority:

- explicit requested source id, when supplied
- enabled custom/M3U provider sources by priority
- Xtream standard `xtream_xmltv` source

For Xtream, the standard source is created on demand when missing. It stores no credential URL. The refresh path builds `xmltv.php?username=...&password=...` ephemerally from provider metadata and `SecretStore`, then masks diagnostics. Non-Xtream providers still require an existing M3U header or custom source.

## Phase 13.1F Cache Semantics

Room remains the source of truth for EPG. Successful imports write `epg_channels` and `epg_programmes`; TV Guide reopen reads those rows directly and does not depend on in-memory ViewModel data. Refresh still stages first and replaces active rows only after a valid parse. Failed download/parse paths delete only staging rows and keep the previous active cache.

`epg_sources.lastSuccessAtEpochMs`, `lastRefreshAtEpochMs`, `lastErrorAtEpochMs`, and row counts are already persisted and are enough for the future three-day refresh policy. No schema migration is needed for Phase 13.1F.

## Phase 13.1G Auto Refresh

Automatic refresh uses WorkManager unique periodic work with a connected-network constraint. Default setting is enabled with a 3-day interval. Manual Settings refresh and scheduled refresh share `EpgRefreshCoordinator`, so per-provider imports are locked and failed refreshes retain the Room cache. See `EPG_AUTO_REFRESH.md`.

## Phase 14.2H Live TV Surface

Live TV reads now/next from the existing provider-scoped Room EPG cache. It shows current title, optional description, programme time range, progress, and next title for the selected channel. The Live TV refresh action calls the shared `EpgRefreshCoordinator`; it does not create a second EPG path or clear cached guide data on failure.

## Phase 14.2H.2 Live Programme Detail

Live TV programme display is cache-first. Selecting a channel queries the existing provider-scoped EPG cache and never downloads XMLTV directly from the UI path.

Selected channel state drives both the upper info card and lower EPG detail panel:

- no selected channel: `No channel selected` / `No programme selected`
- selected channel without EPG: `No programme information` / `No EPG Information Available`
- selected channel with EPG: current title, time range, progress, next title, and description where available

The EPG detail panel must ignore stale now/next data when no channel is selected. Manual Refresh EPG still uses the shared coordinator and preserves the old cache if refresh fails.

## Phase 14.2H.3 Live Panel Placement

Live TV still uses the same cached current/next data. The layout now puts preview and compact channel summary in the top row, with the full EPG detail panel below. No extra EPG lookup, network call, source discovery path, or Room migration is added.

## Phase 14.2H.4 Readability

Live TV EPG display keeps the same cached data path. Compact channel info shows only current summary. The lower EPG panel owns full current/next detail and separates `NEXT` from the progress bar with extra spacing. Next programme time is shown only if a future model exposes it; no missing time is invented.

## Phase 14.2H.5

No EPG repository or schema change. The lower EPG panel owns current programme title, current programme time, progress, next label, and next title. `LiveTvNowNext` currently has next title only, not next start/end.
