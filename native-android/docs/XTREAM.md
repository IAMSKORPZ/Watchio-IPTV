# Watchio Native Xtream

Phase 3 implements Xtream provider creation, authentication, catalog import, refresh, and playback URL resolution. It does not implement playback, EPG/XMLTV import, movie details, or series details.

## Provider Model

Room stores non-secret provider metadata: id, display name, type, canonical server base URL, timestamps, last refresh, and enabled state. Xtream username and password are stored only through `ProviderCredentialStore`.

Duplicate detection compares provider type, normalized server URL, and stored username. Passwords are not compared.

## Section Refresh

Phase 14.2D adds section-specific Xtream refresh operations:

- Live: `get_live_categories` and `get_live_streams`
- Movies: `get_vod_categories` and `get_vod_streams`
- Series: `get_series_categories` and `get_series`

Each operation may authenticate first, but it does not import unrelated catalog sections. Section timestamps update only after the relevant Room replacement succeeds.

## Server URL Normalization

User input accepts `http://`, `https://`, hostnames, ports, and legitimate path prefixes. Missing schemes default to `http://`. Query strings and fragments are removed. `player_api.php` is rejected because only the base server URL is persisted.

## Endpoints

All API calls use `player_api.php` with `username` and `password` query parameters:

- player info/authentication
- `action=get_live_categories`
- `action=get_live_streams`
- `action=get_vod_categories`
- `action=get_vod_streams`
- `action=get_series_categories`
- `action=get_series`

`get_vod_info`, `get_series_info`, XMLTV, and playback are implemented by later native phases. Phase 8 uses `get_series_info` lazily for Series details and episode lists.

## Import Strategy

Import sequence is authenticate, live categories, live streams, movie categories, movies, series categories, series, save. DTOs tolerate common Xtream type drift such as string, number, boolean, null, and empty string for known fields.

Catalog replacement happens in one Room transaction for categories, live, VOD, and series. Favorites, history, and settings are not deleted during refresh.

## Secret Lifecycle

New provider flow validates input, authenticates, fetches catalog, saves credentials, saves Room metadata/catalog in a transaction, then selects the provider. If add fails, provider-scoped secrets are deleted.

Provider deletion still removes Room provider/catalog rows through cascade and then deletes provider secrets.

## Playback URL Resolver

The Xtream resolver generates ephemeral URLs only:

- live: `{base}/live/{username}/{password}/{streamId}.{extension}`
- movie: `{base}/movie/{username}/{password}/{streamId}.{extension}`
- series: `{base}/series/{username}/{password}/{episodeId}.{extension}`

URLs are not persisted. Diagnostics must pass through `SensitiveUrlMasker`. Live stream format follows `StreamFormat`: `AUTO` and `TS` use `.ts`; `HLS` uses `.m3u8`.

## Testing

MockWebServer tests cover valid import, invalid auth, refresh replacement, duplicate provider behavior, provider deletion secrets, and a synthetic large catalog of 10,000 live streams, 10,000 movies, and 5,000 series.

## Phase 14.2D.2 Login Gate
Xtream Codes is the required initial login path. Successful authentication and initial import unlock Home. Failed authentication/import stays on the Xtream form and never renders Home.

## Account Information

Phase 14.2F exposes safe Xtream account information under Settings. The page reuses persisted provider metadata, DataStore-backed expiry, and section refresh timestamps. Username is read from `ProviderCredentialStore` only to render a masked display value. Passwords, raw SecretStore values, server URLs, XMLTV URLs, and playback URLs are never displayed.

Phase 14.2F.1 stores safe provider-scoped `user_info` account metadata in DataStore after successful Xtream authentication:

- `status`
- `max_connections`
- `active_cons`
- `allowed_output_formats`

These fields update on initial login, full provider refresh, and section refresh authentication. A failed refresh preserves previous successful metadata. Output formats are normalized for display, for example `m3u8` becomes `HLS` and `ts` becomes `TS`. Phase 14.2F.1 does not add a Room migration.

## Phase 14.2J Series Redesign Parity

Series grid browsing and modal search overlay query imported Room catalog data only. `get_series_info` is strictly called on-demand when opening `SeriesDetailsScreen`. Room schema remains at v6 without migration.

