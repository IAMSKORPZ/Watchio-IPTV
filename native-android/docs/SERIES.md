# Watchio Native Series

Phase 8 adds Series, seasons, episodes, and episode playback.

## Architecture

`SeriesScreen`, `SeriesDetailsScreen`, and the episode player use `SeriesViewModel`. The ViewModel talks to `SeriesRepository`, shared favorites/history repositories, TMDB trailer lookup, and the app-scoped `WatchioPlayerManager`.

Compose does not query Room, read Xtream credentials, or build provider URLs.

## Categories

Series categories are provider-neutral:

- `ALL SERIES`
- `FAVOURITES`
- `HISTORY`
- provider series categories

Xtream `series` rows and M3U items classified as `series` feed the same UI model.

## Search And Grid

Search is local against imported Room data. The grid uses `LazyVerticalGrid` with adaptive columns, stable keys, Coil covers, touch scrolling, keyboard focus, and TV-compatible focus targets.

## Details

Xtream details use `get_series_info` lazily when a Series detail screen opens. Native caches Series metadata, seasons, and episodes for 24 hours. If refresh fails and cached data exists, the cached data remains usable.

M3U Series details are grouped from parsed Phase 4 metadata into Series, seasons, and episodes using deterministic playlist identity.

## Seasons

Provider season metadata is preserved when available. If providers omit season rows but return episodes grouped by season, Native synthesizes deterministic seasons such as `Season 1`, ordered numerically.

## Episodes

Episode identity is provider-scoped and uses:

`providerId + seriesId + episodeId`

History uses `ContentType.Episode`, `contentId = seriesId`, and `subContentId = episodeId`, so episodes cannot collide with Series-level favorites or other providers.

## Playback

Episode playback reuses the shared Media3 `WatchioPlayerManager`. There is no Series-specific player and no second ExoPlayer.

Xtream episode URLs are generated ephemerally by `PlaybackUrlResolver`:

`/series/{username}/{password}/{episodeId}.{extension}`

M3U episodes use persisted direct URLs plus per-item `User-Agent` and `Referer` headers.

## Resume

Episode resume uses the same VOD thresholds as Movies: meaningful progress is required and near-complete items do not show resume. Progress is saved periodically and on player exit.

## Favorites And History

Series favorites use the shared `favorites` table with `ContentType.Series`. Favoriting a Series does not favorite seasons or episodes.

The `HISTORY` category collapses recent episode history to Series-level items so one watched show appears once.

## TMDB

Series trailer fallback uses TMDB TV videos and the existing `tmdb_trailer_caches` table with type `tv`. The key still comes from `WATCHIO_TMDB_API_KEY`; no key is stored in Room or docs.

## Security

Xtream credentials stay in `SecretStore`. Episode URLs are masked before diagnostics and are not persisted.
## Phase 14.2J Series Tab Redesign

Phase 14.2J redesigns the Series browsing tab to match the Movies design system:

### 1. Shared Watchio Header
- Structure: `[Back] [Watchio Logo + Watchio]       SERIES       [Time/Date] [🔍 Search Icon] [⋮ More]`
- Header title is explicitly `SERIES`.
- Search is a compact 44×44dp square icon button with a Canvas-drawn magnifying glass icon matching the ⋮ More button. There is no visible text "Search" in the normal header.
- The ⋮ More button is a compact 44×44dp square icon button with a Canvas-drawn three-dot vertical ellipsis.
- Semantics: `"Search Series"` and `"More options"`.

### 2. Compact Category Rail
- Category cards use explicit `48.dp` height with `6.dp` bottom gap in `LazyColumn`.
- Category text is vertically centered via `contentAlignment = Alignment.CenterStart` with `12.dp` left/right padding.
- System category order: `ALL SERIES` → `FAVOURITES` → `HISTORY` → provider categories.
- Overflow Marquee:
  - Inactive categories display single-line text with `TextOverflow.Ellipsis`.
  - Focused/selected category: if text fits, remains static; if text overflows, smoothly marquees left with `Modifier.basicMarquee`.
  - When focus moves away, marquee immediately stops and text resets with ellipsis.
- Preserves standard `WatchioCard` with official **WHITE** focus border (`colors.focusBorder`).

### 3. Main Series Poster Grid
- 2:3 aspect ratio posters in an adaptive `LazyVerticalGrid` (92dp compact / 132dp standard min column width).
- Formatted ratings (`★ X.X`) are displayed as a small dark translucent badge (`Color.Black.copy(alpha = 0.72f)`, `RoundedCornerShape(4.dp)`) in the top-right corner of the poster.
- Zero, null, blank, negative, or invalid ratings render NO badge.
- Fixed 52dp title region directly below poster (`series-title-region`). No separate rating line below title.
- 24dp bottom content padding prevents clipping.

### 4. Interactions & Flow
- **Tap / OK**: Opens Series Details screen (`SeriesDetailsScreen`). Does NOT auto-play or start playback.
- **Long Press / Hold OK**: Opens `SeriesOptionsDialog` ("Series Options", "View Details", "Close").
- **Search Icon**: Opens modal `SeriesSearchOverlay`. Queries local imported catalog only without eager network calls. Selecting a result opens Details and dismisses the overlay. Back dismisses overlay first.
- **Series Hierarchy**: Main tab displays Series items only (not individual seasons or episodes). Hierarchy remains `Series → Series Details → Season → Episode → Playback`.
- **Performance**: 100% lazy; no `get_series_info` calls during grid browsing or search typing.
- **Room & Media3**: Room schema remains v6. Playback architecture and player manager remain unchanged.

### 5. Phase 14.2K Unified Search Integration
- Series header Search queries the entire Series catalog of the active provider, ignoring whichever category is selected in the category rail.
- Selecting a series from the search overlay closes search and opens Series Details (does not auto-play).
- Search overlay preserves Back precedence (Back dismisses search overlay first before leaving Series).
