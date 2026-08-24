# Watchio Native Movies

Phase 7 adds Movies/VOD catalog, details, trailer metadata, and playback.

## Architecture

`MoviesScreen` and `MovieDetailsScreen` use `MoviesViewModel`. The ViewModel talks to `MoviesRepository`, favorites, history, and the shared `WatchioPlayerManager`. Compose does not query Room, read credentials, or build Xtream URLs.

## Categories

Movies use provider-neutral categories:

- `ALL MOVIES`
- `FAVOURITES`
- `HISTORY`
- provider movie categories

Xtream `vod_streams` and M3U items classified as `movie` feed the same UI model.

## Grid

Phase 14.2I redesigns the Movies browser around the shared Watchio page header, a compact left category rail, and a poster-first `LazyVerticalGrid`. The grid uses adaptive columns and stable movie ids; S22 landscape targets five or more visible poster cards while larger tablets/TVs naturally gain columns. Poster images use Coil, keep a 2:3 ratio, and fall back to a clean Watchio placeholder when missing. Provider/server order is preserved by default.

The shared header matches Live TV and Settings: compact Back, Watchio logo/name, centered `MOVIES`, clock/date, Search, and ⋮ More. Header Search opens a separate dimmed movie-search overlay and never inserts a text field into the header row.

Movie Search uses already-imported catalog rows. It searches movie titles across the active provider's All Movies catalog through the repository search path and does not call `get_vod_info` while typing. Selecting a result closes the overlay and opens Movie Details; it does not start playback.

Long press / hold OK on a poster opens a lightweight Movie Options dialog with View Details and Close. Normal tap/OK opens Movie Details. Action clutter is kept out of poster cards.

## Phase 14.2I.1 Movies Polish

Changes made in Phase 14.2I.1:

### Header
- More control is now a compact 44×44dp ⋮ icon button (Canvas-drawn three dots). No text "More".
- Matches the compact Back button visual language.

### Category Rail
- The `OutlinedTextField` ("Search categories") has been **removed** from the Movies category rail.
- The rail now starts directly with categories without a search field consuming vertical space.
- Category order is: ALL MOVIES → FAVOURITES → HISTORY → provider categories. This is enforced by `MoviesRepository.categories()` which always prepends the three system categories.
- Removing the search field was the fix for system categories not being visible on S22: the tall text field was pushing them out of the initial viewport.

### Rating Formatting
- Raw provider rating strings are now formatted via `formatRating(raw: String?): String?` (internal function in `MoviesScreens.kt`).
- Format: `★ X.X` (one decimal place, e.g. "6.458" → "★ 6.5", "7" → "★ 7.0").
- Zero, null, blank, non-numeric, negative values → `null` → rating row is hidden entirely.
- Covered by JVM unit tests in `MovieBehaviorTest`.

### Title Height
- Movie poster cards now use a fixed-height `Box(Modifier.height(52.dp))` for the title region.
- All cards in a grid row reserve the same vertical space for the title regardless of text length.
- Maximum 2 lines with ellipsis overflow.

### Grid Bottom Padding
- `LazyVerticalGrid` uses `contentPadding = PaddingValues(bottom = 24.dp)` to prevent the bottom row from clipping awkwardly at the viewport edge.

### Year on Grid Cards
- Intentionally omitted. `WatchioMovieItem` has no `releaseDate` field at list level.
- Adding it would require a Room schema change (blocked at v6).
- Year already appears on the Movie Details screen after lazy `get_vod_info` load.

### Unchanged
- Grid column sizes: 92dp compact / 132dp non-compact (preserve existing poster density).
- Category rail width: 154dp compact / 220dp non-compact (unchanged pending device test).
- All playback, history, favourites, player settings wiring.

## Phase 14.2I.2 Movies Category Height + Overflow Text

Phase 14.2I.2 applies focused usability polish to the Movies category rail without redesigning the screen or altering the approved theme:

### 1. Reduced Category Card Height
- Movies category cards use `minHeight = 54.dp` (reduced from the previous oversized default of 92dp+).
- Vertical padding inside card is compact (`8.dp` top/bottom, `14.dp` left/right).
- Allows 4-5 category rows to fit simultaneously on compact landscape (e.g. S22) without excessive empty vertical space.
- Existing background (`colors.surfaceCard` with selected accent tint), corner radii (`radii.md`), and **WHITE** focus/selection border are 100% preserved via `WatchioCard`. No new colors, glow effects, or icons introduced.

### 2. Category Text Overflow & Marquee
- **Inactive / Unfocused**: Category name renders as single line with `TextOverflow.Ellipsis`.
- **Active (Focused or Selected)**:
  - If text fits available width (e.g. `ACTION`, `ADVENTURE`): remains completely static with zero animation.
  - If text overflows available width (e.g. `JUST RELEASED HOLLYWOOD 4K ULTRA HD MOVIES`): activates `Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 600, repeatDelayMillis = 1200)` and smoothly scrolls horizontally left so user can read the complete category name.
- **Focus Shift / Inactive**: When focus leaves, marquee immediately stops and text resets to the start position with ellipsis. Only the active overflowing category animates.

## Phase 14.2I.3 Movies Search Icon + Poster Card Visual Polish

Phase 14.2I.3 completes visual alignment for the Movies header and poster cards:

### 1. Header Search Icon Button
- The header Search button is now a compact 44×44dp square icon button with a Canvas-drawn magnifying glass icon matching the ⋮ More button.
- Replaces visible text "Search" in the normal header while keeping accessibility semantics (`"Search Movies"`).
- Tap / OK continues to open the existing `MovieSearchOverlay`.

### 2. Poster Rating Badge Overlay
- The formatted rating (`★ 6.5`, `★ 7.7`) has moved from a separate line below the title to a small dark translucent badge (`Color.Black.copy(alpha = 0.72f)`, `RoundedCornerShape(4.dp)`) positioned at the top-right inside the poster area.
- Zero, null, blank, negative, or invalid ratings render no badge, keeping the poster clean.
- The redundant rating text line beneath the title is completely removed.
- Card presentation:
  - 2:3 aspect ratio poster with top-right rating badge
  - Fixed 52dp height title region directly beneath poster (max 2 lines with ellipsis)
- Poster grid dimensions, adaptive columns, category rail, and WHITE focus border remain 100% intact.

## Phase 14.2I.4 Movies Category Card Compact + Text Centering

Phase 14.2I.4 delivers targeted category rail visual refinement:

### 1. Compact 48dp Category Card Height
- `MovieCategoryRow` explicitly sets `height(48.dp)` and `minHeight = 48.dp` with `padding(bottom = 6.dp)` in `LazyColumn`.
- Allows 5-6 category rows to fit simultaneously on compact landscape (e.g. S22), fitting `ALL MOVIES`, `FAVOURITES`, `HISTORY`, and provider categories without wasted vertical space.

### 2. Vertically Centered Category Text
- Uses `contentAlignment = Alignment.CenterStart` inside a full-height 48dp Box.
- Category text is vertically centered with zero top/bottom gap asymmetry, with comfortable `12.dp` left/right horizontal padding.

### 3. Preserved Theme, Focus & Marquee
- Preserves standard `WatchioCard` with **WHITE** focus border (`colors.focusBorder`).
- Preserves conditional marquee: inactive text remains ellipsized; active (focused/selected) overflowing text smoothly marquees left; short text remains static.
- Header, Search icon, ⋮ More button, and 2:3 Movie Grid remain 100% unchanged.


## Details

Details show poster, title, metadata, plot, Play/Resume, Start Over, Trailer, and Favorite. Missing provider fields are hidden or replaced with a small fallback.

Xtream details are loaded lazily through `get_vod_info` only when the details screen opens. Detail rows are cached in `movie_details` for 24 hours. If detail loading fails, list metadata still displays.

## M3U Movies

M3U movies use the persisted direct URL and per-item `User-Agent`/`Referer` headers. They do not use Xtream credentials.

Phase 12.1 normalizes M3U movie display names at repository mapping time so filename fallbacks such as codec/resolution-heavy names do not leak directly into ALL MOVIES. Xtream titles and provider metadata are not changed.

## Playback

Movie playback uses the existing app-scoped `WatchioPlayerManager` and Media3 ExoPlayer. Xtream movie URLs are generated ephemerally by `PlaybackUrlResolver`; M3U movie URLs are read from `m3u_items`.

Movie playback is fullscreen only. The overlay supports Play/Pause, 10-second seek backward/forward, progress display, and Back.

## Resume And History

History uses the shared `watch_history` table. Resume is offered after 60 seconds and not near completion. Progress is saved periodically and on player exit.

## Favorites

Favorites use the shared `favorites` table and update the details state immediately.

## TMDB

TMDB trailer fallback is optional and key-safe. Native reads `WATCHIO_TMDB_API_KEY` from a Gradle property or environment variable into `BuildConfig`; no key is hard-coded in source. Trailer cache rows live in `tmdb_trailer_caches` for 30 days.

Fallback order:

1. provider trailer
2. official YouTube trailer from TMDB
3. YouTube trailer from TMDB
4. any YouTube video from TMDB

Trailer playback opens YouTube externally. Native does not scrape YouTube or play YouTube through Media3.

## Database

Phase 7 upgrades Room to schema v5:

- `movie_details`
- `tmdb_trailer_caches`

Migration `4 -> 5` is explicit and non-destructive.

## Hardware Status

Real Live TV and Movie/VOD playback have been manually validated on Samsung Galaxy Tab S9 with a private provider. BRAVIA/Fire TV production validation and broader codec coverage remain open.
## Phase 9 Library Integration

Movies participate in Global Search, Favourites, History, and Continue Watching. Resume eligibility uses the existing movie resume threshold.

## Phase 14.2K Unified Search Integration

- Movies header Search queries the entire Movie catalog of the active provider, ignoring whichever category is selected in the category rail.
- Selecting a movie from the search overlay closes search and opens Movie Details (does not auto-play).
- Search overlay preserves Back precedence (Back dismisses search overlay first before leaving Movies).
