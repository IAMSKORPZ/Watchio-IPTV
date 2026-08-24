# Watchio Native AI Handoff

This file is the current quick handoff for future AI work.

## Safety

- Do not commit or push unless explicitly asked.
- Do not uninstall or clear `com.watchioiptv.nativeapp.debug` on real user devices.
- Connected automation must use the isolated `uitest` package and `connectedUitestAndroidTest`.
- Keep credentials, provider URLs, tokens, and playlist URLs out of logs, docs, tests, screenshots, and reports.

## Current App State

Native Watchio is a Kotlin/Compose Android app in `native-android`. Room is schema v6. DataStore owns settings and bootstrap state. SecretStore owns provider credentials. Media3 is the only player engine.

Startup flow is:

- Loading
- Device Mode on genuine first run
- Xtream Login when no enabled Xtream provider exists
- Home when an enabled Xtream provider exists

M3U providers remain supported after login but do not unlock Home by themselves.

## Major Features

- Xtream provider import, refresh, metadata, and secure playback URL generation
- M3U URL/local-file import with stable identity and refresh staging
- XMLTV/EPG import, cache, matching, current/next, and automatic refresh
- Home visual dashboard with section-specific refresh
- Live TV with category rail, searchable channel list, preview, fullscreen, Channel Options, favourites, history, and current programme information
- Movies and Series with playback, resume, start over, favourites, history, and continue watching
- Search, My List, Settings, Account Information, Player Settings, and theme persistence

## Live TV Current Rules

- Different channel click starts preview playback.
- Clicking the already selected channel opens fullscreen.
- Clicking/focusing preview and pressing OK/Enter opens fullscreen.
- Long press/hold OK on a channel opens Channel Options.
- Normal Live TV screen has no permanent Fullscreen, Favorite, or Retry button strip.
- Retry uses Player Settings automatic retry and Channel Options manual retry for failed selected channels.
- Programme info is split by ownership: Channel Info shows only Live TV label and channel name; lower EPG shows current programme, time range, progress, next programme, and no-EPG fallback.
- Header Search opens a separate channel-search overlay. Do not put `OutlinedTextField` in `WatchioPageHeader`; the header must stay fixed with Back, branding, title, clock/date, Search, and More.
- Left-panel `Search categories` remains category-only and separate from header channel search.
- Selecting a channel-search result closes the overlay, selects the channel, and starts preview. It must not auto-fullscreen.
- Right panel structure is top row preview + compact channel info, then bottom row EPG/programme detail. Preview must not move below programme cards.
- Compact right top row uses preview about 68-70% wide and channel info about 30-32% wide. Preview remains wider than info; both align top/bottom.
- `Connecting...`, `Buffering...`, and player errors belong inside preview surface.
- Compact channel info is identity-only. Lower EPG owns current programme, time, progress, next programme, and description.
- Lower EPG shows `CURRENT PROGRAMME`, title, current time, progress, `NEXT`, and next title. Current model has no next start/end fields.
- **Header Actions (Phase 14.2H.6)**: Live TV uses compact 44×44dp square icon buttons for Search [🔍] (`LiveSearchIconButton`) and More [⋮] (`LiveMoreButton`), matching Movies and Series. No text "Search" or "More" in the header.
- TV hardware after H.5: install/launch status belongs in latest phase report; do not assume provider exists on TV.

## Movies Current Rules (Phase 14.2I.4)

- Movies uses the shared `WatchioPageHeader`: Back, Watchio logo/name, centered `MOVIES`, clock/date, [🔍 Search], [⋮ More].
- Both header Search and More are compact 44×44dp icon buttons (Canvas-drawn magnifying glass and three vertical dots). There is NO text "Search" or "More" in the header.
- Header Search opens a dimmed `MovieSearchOverlay`. Do not put movie search fields in the header row.
- Movie Search searches imported catalog rows only and opens Movie Details on selection. It must not auto-play.
- **The left category rail has NO `OutlinedTextField` search field.** The `Search categories` / `movie-category-search` tag must be absent.
- Category rail order: ALL MOVIES → FAVOURITES → HISTORY → provider categories.
- **Category Card Height & Alignment**: Movies category cards use compact `48.dp` height with `6.dp` bottom gap in `LazyColumn`. Category text is vertically centered via `contentAlignment = Alignment.CenterStart` with comfortable `12.dp` horizontal padding. Fits 5-6 category rows vertically on S22 landscape.
- **Focus / Selection Border**: Category cards and poster cards use the existing **WHITE** focus/selection border from `WatchioCard`. No custom colors.
- **Marquee Overflow Text**:
  - Inactive categories show single-line text with `TextOverflow.Ellipsis`.
  - Focused/selected category: if text fits width, stays static; if text overflows, smoothly scrolls horizontally left with `Modifier.basicMarquee`.
  - When focus moves away, marquee stops immediately and text resets with ellipsis.
- **Movie Poster Card Layout**:
  - 2:3 aspect ratio poster.
  - Rating overlay: formatted rating (`★ X.X`) rendered in a small dark translucent badge at the top-right inside the poster area.
  - Zero/null/invalid ratings are completely hidden (no badge).
  - Title: fixed 52dp height region directly beneath poster (max 2 lines with ellipsis).
  - No separate rating line below the title.
- Normal tap/OK opens Movie Details. Long press/hold OK opens Movie Options.
- Year is NOT shown on grid cards (shows on Movie Details only).
- `LazyVerticalGrid` uses `contentPadding = PaddingValues(bottom = 24.dp)` to prevent bottom row clipping.
- Category rail width: 154dp compact / 220dp non-compact.
- Grid column min sizes: 92dp compact / 132dp non-compact.

## Series Current Rules (Phase 14.2J)

- Series uses the shared `WatchioPageHeader`: Back, Watchio logo/name, centered `SERIES`, clock/date, [🔍 Search], [⋮ More].
- Both header Search and More are compact 44×44dp icon buttons (Canvas-drawn magnifying glass and three vertical dots). There is NO text "Search" or "More" in the header.
- Header Search opens a dimmed `SeriesSearchOverlay`.
- Category rail order: `ALL SERIES` → `FAVOURITES` → `HISTORY` → provider categories.
- Category cards use compact `48.dp` height with `6.dp` bottom gap in `LazyColumn`. Text is vertically centered via `Alignment.CenterStart` with `12.dp` horizontal padding. Fits 5-6 rows on S22 landscape.
- Preserves standard `WatchioCard` with official **WHITE** focus border (`colors.focusBorder`).
- Marquee overflow: active (focused/selected) overflowing category smoothly marquees left; inactive/short categories remain static with ellipsis.
- Series Poster Card: 2:3 aspect ratio, top-right dark translucent rating badge (`★ X.X`), zero/missing rating suppression, fixed 52dp title region, no separate rating line below title.
- Tap/OK opens Series Details (`SeriesDetailsScreen`). Long-press/hold-OK opens `SeriesOptionsDialog`.
- Series hierarchy: Series → Series Details → Season → Episode → Playback.
- 100% lazy loading; no eager `get_series_info` calls during browsing. Room remains v6. Media3 playback unchanged.

## Search Architecture Rules (Phase 14.2K)

- 4 distinct search scopes: Home (Global), Live TV (Channels only), Movies (Movies only), Series (Series only).
- **Category Independence**: Selected category on Live TV/Movies/Series affects normal browsing only. Header search queries the **entire** active provider catalog for that content type regardless of active category.
- **Strict Provider Isolation**: All searches scope strictly to `selectedProviderId()`. No results from other providers are ever returned.
- **Home Global Search**: Opens modal overlay with grouped sections (`LIVE TV`, `MOVIES`, `SERIES`) and quick scope filter pills (`ALL`, `LIVE TV`, `MOVIES`, `SERIES`).
- **Result Navigation**:
  - Live TV result -> `"live/$channelId"` (opens Live TV, selects channel, starts preview playback; no auto-fullscreen).
  - Movie result -> `"movies/$movieId"` (opens Movie Details; no auto-play).
  - Series result -> `"series/$seriesId"` (opens Series Details; no auto-play).
- **Back Precedence**: When any search overlay is open, Back closes search overlay first before navigating away from parent screen.
- Room remains locked at **v6**. Zero network requests during search.

## Search Result Artwork & Clean Titles (Phase 14.2K.1)

- **Search Result Artwork**:
  - Live TV: Channel logo in `ContentScale.Fit` with fallback icon (`TV`).
  - Movies: 2:3 compact poster (44–60dp width) with fallback (`No Image`).
  - Series: 2:3 compact poster (44–60dp width) with fallback (`No Image`).
  - Metadata: Clean year detection and formatted star rating (`★ X.X`) rendered alongside titles.
- **MediaTitleNormalizer**:
  - Conservative, non-destructive normalization strips scene release markers (`1080p`, `BluRay`, `x264`, `WEB-DL`, `DDP5.1`, `[FEATURETTE]`, etc.) while detecting release years.
  - Strictly preserves legitimate numbered titles (e.g. `1917`, `2001: A Space Odyssey`, `Blade Runner 2049`, `Se7en`, `Catch-22`, `Spider-Man: No Way Home`, `Mission: Impossible`, `F9`, `28 Years Later`).
  - Title Priority: 1. Clean provider title, 2. tvg-name / explicit metadata, 3. normalized catalog title, 4. filename fallback.
  - Search queries match both clean display titles and dot/underscore-separated names via `TextNormalizer`.

## Global Search Layout Expansion & Home Search Icon (Phase 14.2K.2)

- **Expanded Modal Dimensions**: Global Search panel expanded to occupy 90% landscape width and 86% landscape height (`fillMaxWidth(0.90f).fillMaxHeight(0.86f)`).
- **Responsive Multi-Column Density**: Results dynamically display across 1, 2, or 3 columns based on available panel width (<620dp: 1 col, 620-960dp: 2 col, >960dp: 3 col).
- **Space-Efficient Result Cards**: 2:3 aspect ratio posters and square channel logos, 2-line title truncation, clean year badge, and gold rating pill (`★ X.X`).
- **Home Search Icon**: Canvas-drawn magnifying glass icon matching Live TV, Movies, and Series (`HomeIconKind.Search` separated from `HomeIconKind.Movie`). Explicit semantics `testTag = "home-search"` and `contentDescription = "Search"`.

## Global Search Result Space & Density Polish (Phase 14.2K.3)

- **Compact Search Controls**:
  - Header row (22dp) with "Search Watchio" title + "Close" button.
  - Compact `BasicTextField` (36dp) with subtle focus outline and 12sp placeholder.
  - Compact single horizontal filter toolbar (24dp pills): `[Clear]`, `[ALL]`, `[LIVE TV]`, `[MOVIES]`, `[SERIES]`.
  - Top controls vertical footprint `< 100dp` (< 25% on landscape phone, < 15% on tablet/TV).
- **Results Area Dominance**:
  - Results container consumes `75–80%` of overlay height.
  - Independent smooth scrolling inside LazyColumn while top controls remain fixed.
- **Dense Poster Grids & Live Cards**:
  - **Movies & Series**: Compact vertical poster cards (`SearchResultMediaCard`) with 2:3 aspect ratio posters, top-right gold rating badges, 2-line titles, and single-line metadata (`1970  ★ 5.9`).
    - 4 columns on landscape phones (S22), 5–7 columns on tablets and TV.
  - **Live TV**: Compact horizontal cards (`SearchResultLiveCard`, 44dp height) with 36dp channel logos and channel names.
    - 2–3 columns on landscape phones, 3–4 columns on tablets and TV.
- **Content-Type Filter Optimization**:
  - Specific scope (`MOVIES`, `SERIES`, `LIVE TV`) allocates 100% of results space to that content type without empty header placeholders.


## Testing

Use:

```powershell
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleUitest
.\gradlew.bat connectedUitestAndroidTest
.\gradlew.bat connectedUitestAndroidTest
```

Install real debug builds with `adb install -r` only. Do not uninstall unless the user explicitly approves data loss.
