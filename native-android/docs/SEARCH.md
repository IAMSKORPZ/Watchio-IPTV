# Watchio Native Search Architecture (Phase 14.2K)

Unified search architecture standardising search behaviour across Home, Live TV, Movies, and Series.

## 1. Four Search Scopes

| Scope | Location | Search Domain | Behavior |
|---|---|---|---|
| **Home Search** | Home Header 🔍 | Global (Live TV + Movies + Series) | Searches entire catalog of active provider, results grouped by type |
| **Live TV Search** | Live TV Header 🔍 | Live TV Channels only | Searches entire Live TV catalog of active provider |
| **Movies Search** | Movies Header 🔍 | Movies only | Searches entire Movie catalog of active provider |
| **Series Search** | Series Header 🔍 | Series only | Searches entire Series catalog of active provider |

## 2. Category Independence Rule

- `selectedCategory != searchScope`
- Selecting a category on Live TV, Movies, or Series affects the normal browsing list/grid ONLY.
- Initiating a search from the header Search button searches the **entire catalog** for that content type for the active provider.
- In-memory/DAO filtering ignores the currently selected category when a query is active.
- Dismissing or clearing the search immediately restores the browsing view to the active category.

## 3. Provider Isolation

- All search is strictly scoped to the active provider (`selectedProviderId`).
- Provider A search queries never execute against or return items from Provider B.
- Search queries use Room indexed SQL `WHERE providerId = :providerId AND normalizedName LIKE '%' || :query || '%'`.

## 4. Global Search UI & Grouping

- Home Search opens a dedicated modal search overlay with a darkened backdrop (`Color.Black.copy(alpha = 0.54f)`).
- Results are distinctly grouped into sections: `LIVE TV`, `MOVIES`, `SERIES`.
- Empty groups are cleanly omitted.
- Quick scope selector pills allow switching between `ALL`, `LIVE TV`, `MOVIES`, `SERIES`.

## 5. Result Navigation Rules

- **Live TV Result Selection**:
  - Closes search overlay.
  - Navigates to Live TV (`"live/$channelId"`).
  - Selects the target channel and starts preview playback.
  - Does NOT automatically enter fullscreen.
- **Movie Result Selection**:
  - Closes search overlay.
  - Navigates to Movie Details (`"movies/$movieId"`).
  - Does NOT auto-play.
- **Series Result Selection**:
  - Closes search overlay.
  - Navigates to Series Details (`"series/$seriesId"`).
  - Does NOT auto-play.

## 6. Back Button Precedence

- When any search overlay is open (Home Global, Live TV, Movies, Series), pressing Back dismisses the search overlay first.
- Back does NOT navigate away from the parent screen while search is open.
- Opening search in Live TV does not stop or restart preview playback.

## 7. Performance & Offline Safety

- Search runs entirely against local Room database tables (`live_streams`, `vod_streams`, `series`, `m3u_items`).
- No network requests (`get_vod_info`, `get_series_info`, XMLTV download, etc.) occur during search.
- Debounced at 250ms with non-blocking coroutines on `Dispatchers.IO`.
- Room version remains locked at **v6** (no schema migrations).

## 8. Search Result Artwork & Clean Media Titles (Phase 14.2K.1)

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

## 9. Global Search Layout Expansion & Home Search Icon (Phase 14.2K.2)

- **Expanded Modal Dimensions**:
  - Global Search panel expanded to occupy 90% landscape width and 86% landscape height (`fillMaxWidth(0.90f).fillMaxHeight(0.86f)`).
  - Removes cramped narrow dialog feeling and makes full use of wide screen space across phones, tablets, and TV.
- **Responsive Multi-Column Result Density**:
  - Dynamic responsive column layout based on available panel width:
    - `< 620dp`: 1 column
    - `620dp - 960dp`: 2 columns
    - `> 960dp`: 3 columns
  - Displays 8–10+ search results simultaneously on landscape phone (S22) and 12–15+ on tablets/TV.
- **Card Sizing & Readability**:
  - Results rendered in space-efficient `SearchResultCard` with 2:3 aspect ratio posters and square channel logos.
  - Clean two-line title truncation with ellipsis.
  - Secondary metadata row with clean Year badge and gold `★ Rating` pill.
- **Home Search Icon Correction**:
  - Home Search button in `HomeTopBar` now renders the correct magnifying-glass canvas icon (`HomeIconKind.Search`) matching Live TV, Movies, and Series headers.
  - Separated from `HomeIconKind.Movie` play triangle path.
  - Explicit semantics `testTag = "home-search"` and `contentDescription = "Search"`.

## 10. Global Search Result Space & Density Polish (Phase 14.2K.3)

- **Compact Search Controls**:
  - Compact header row (22dp) with "Search Watchio" title + "Close" button.
  - Compact `BasicTextField` (36dp) with subtle outline border and 12sp placeholder.
  - Compact single horizontal filter toolbar (24dp pills): `[Clear]`, `[ALL]`, `[LIVE TV]`, `[MOVIES]`, `[SERIES]`.
  - Top controls occupy `< 25%` of overlay height on mobile landscape and `< 15%` on tablets/TV.
- **Results Area Dominance**:
  - Results area expands to consume `75–80%` of overlay height.
  - Independent smooth scrolling inside LazyColumn while top controls remain fixed.
- **Multi-Column Dense Poster Grids**:
  - **Movies & Series**: Compact vertical poster cards (`SearchResultMediaCard`) with 2:3 aspect ratio posters, top-right gold rating badges, 2-line titles, and single-line metadata (`1970  ★ 5.9`).
    - 4 columns on landscape phones (S22), 5–7 columns on tablets and TV.
  - **Live TV**: Compact horizontal cards (`SearchResultLiveCard`, 44dp height) with 36dp channel logos and channel names.
    - 2–3 columns on landscape phones, 3–4 columns on tablets and TV.
- **Content-Type Filter Optimization**:
  - Selecting a specific scope (`MOVIES`, `SERIES`, `LIVE TV`) dedicates 100% of the result area to that content type without empty placeholder gaps.
