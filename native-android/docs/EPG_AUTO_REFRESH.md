# Watchio Native EPG Auto Refresh

Phase 13.1G adds WorkManager-backed automatic EPG refresh.

## Defaults

- Auto Refresh: on
- Interval: 3 days
- Alternatives: 1 day, 7 days

Settings are stored in DataStore, not Room.

## WorkManager

Automatic refresh uses unique periodic work:

- name: `watchio_epg_auto_refresh`
- constraint: connected network
- backoff: exponential, 30 minutes

Settings changes replace or cancel the unique work, so repeated app starts and Activity recreation do not stack workers.

## Refresh Path

Worker and manual refresh use `EpgRefreshCoordinator`, which delegates to `EpgRepository`.

Provider ids are the only identifiers used by background refresh. Xtream XMLTV URLs are reconstructed ephemerally from provider metadata and `SecretStore`; credential URLs are not stored in WorkManager input data, tags, Room, or DataStore.

## Cache Safety

Room remains the TV Guide source of truth. Refresh downloads/parses into staging and active guide rows are replaced only after successful parsing. Failed automatic or manual refresh keeps the existing cached guide.

`epg_sources.lastSuccessAtEpochMs` is updated only after a successful import transaction and is shown in Settings as the last successful refresh.

## Provider Isolation

Scheduled refresh iterates enabled providers and refreshes each provider-scoped EPG source independently. One provider failure does not delete another provider's cache.

## UI

Settings exposes:

- Auto Refresh On/Off
- Refresh Interval: 1 Day, 3 Days, 7 Days
- Last successful refresh
- Refresh Now

TV Guide remains Room-first. Opening or reopening the guide does not wait for WorkManager or force a network download when cache exists.
