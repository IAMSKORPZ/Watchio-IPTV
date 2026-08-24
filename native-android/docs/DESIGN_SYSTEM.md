# Watchio Native Design System

Phase 14.0 establishes the shared Compose design foundation. It is not a full screen redesign.

## Tokens

Design tokens live in `ui/theme/WatchioDesignTokens.kt` and are provided by `WatchioTheme`.

- Spacing: xs, sm, md, lg, xl, xxl
- Radii: sm, md, lg
- Borders: normal and focused
- Component sizes: cards, compact buttons, list rows, TV safe padding
- Icons: sm, md, lg
- Posters: 2:3 ratio, min/max width guidance
- Motion: focus duration and overlay hide duration
- Typography: screen title, card title, body, label
- Responsive class helper: compact, medium, expanded, TV

The current themes remain `Watchio Default`, `Dark`, `Purple`, and `Blue`. Theme persistence remains DataStore-backed.

## Components

Shared components live in `ui/components/WatchioDesignComponents.kt`.

- `WatchioCard`: shared focus, selected, disabled, border, and glow foundation
- `WatchioButton`: primary, secondary, ghost, danger, and compact action variants
- `WatchioIconButton`: accessible icon/action surface with content slot
- `WatchioChip`: selected/filter chip
- `WatchioPosterCard`: poster shell with stable 2:3 media area
- `WatchioListRow`: focusable row for dense lists
- `WatchioScreenHeader`: title/subtitle header
- `WatchioLoading`, `WatchioErrorState`, `WatchioEmptyState`, `WatchioProgressBar`

`WatchioFocusableCard` keeps its existing API and now delegates to the shared `WatchioCard` foundation.

## Phase 14.0 Pilot

The pilot migration is intentionally narrow:

- Live TV action row now uses `WatchioButton` compact actions.
- Existing text labels, callbacks, test tags, and player behavior are preserved.
- Provider forms, Settings, TV Guide, Movies, Series, player screens, and EPG refresh are not mass-migrated.

## Phase 14.1 Home Pilot

Home is the first polished screen using the design system as visual benchmark:

- Primary cards: Live TV, Movies, Series
- Secondary cards: Search, My List, TV Guide
- Utility buttons: provider setup, Providers, Settings
- Header: Watchio Native branding and safe active-provider summary
- Layout: token-driven rows inside one vertical scroll container for S22, tablet, and TV reachability

## Phase 14.2A Home Visual Redesign

Home now uses the shared design tokens to approximate the approved neon IPTV direction from the visual reference:

- Dark abstract Compose-drawn background with safe contrast scrim
- Premium primary cards for Live TV, Movies, and Series
- Pill secondary actions for TV Guide, Settings, and My List
- Compact top utilities for Search, Providers, and Settings
- Safe footer metadata for provider cache state and version
- Temporary Compose logo/background placeholders until final bitmap assets are supplied

## Phase 14.2B Home Reference Match

The Home visual system now follows the approved balanced reference:

- Three equal content columns
- Live TV in the left column spanning both main rows
- Movies above TV Guide in the middle column
- Series above Settings in the right column
- No My List card on Home
- Header actions: Search, Sports, Announcements, Playlist
- Playlist reuses provider management

The loaded-provider Home avoids vertical scrolling on S22 landscape, tablet landscape, and TV-class layouts by using weighted columns and rows instead of fixed pixel coordinates.

## Phase 14.2B.1 Home Status Polish

Primary Home card status strips now use short persisted status labels instead of cached item counts. The visible refresh control is a compact accent icon button while preserving a usable touch/focus target. Footer metadata uses the same compact treatment: provider expiry on the left, app version in the center, and active provider on the right.

## TV And Touch Rules

- Focus must remain visible through the shared white border and theme glow.
- Buttons and cards keep deterministic minimum heights.
- Text uses ellipsis instead of clipping outside controls.
- Components must work with D-pad, keyboard, touch, and mouse.
- Poster cards preserve aspect ratio and avoid layout jumps.

## Security

Design components do not log URLs, provider names, credentials, or tokens. IPTV URLs remain masked by existing networking/player layers.

## Future Migration

Future phases can migrate screens gradually to the shared components. Do not rewrite a working screen solely for visual consistency unless there is a concrete layout, focus, or accessibility issue.
# Settings Menu Visual Foundation

The Settings root reuses the Home background, Watchio card system, focus border/glow, and responsive spacing. Root cards are large translucent category cards with centered icons, bold titles, subtitles, stable dimensions, and no focus-induced layout resizing.

## Phase 14.2H Live TV Polish

Live TV applies the shared card/button/focus language without changing playback architecture. Category rows, channel rows, preview/info panels, and EPG panels use stable dimensions, visible focus, and ellipsized text. The design keeps TV remote, keyboard, touch, and mouse paths active.

## Phase 14.2H.1 Shared Page Header

`WatchioPageHeader` is the shared Settings/Live header component. It provides the compact Back icon, Watchio logo/name, centered title, local clock/date, and optional right-side actions. Settings and Live TV now use the same component.

Live TV channel search does not alter this header row. The Search action opens a dimmed Watchio-styled overlay with its own input and lazy results so the centered title and clock/date never collide with a text field on compact landscape devices.

## Phase 14.2H.2 Live Programme Hierarchy

Live TV now treats programme data as content hierarchy, not decoration. The selected channel name is the primary heading, current programme title is the secondary content, and the `LIVE TV` label is only a small badge. Accent color is reserved for badges, progress, and small metadata, so programme titles stay readable on compact phone landscape and TV layouts.

## Phase 14.2H.3 Live Right Panel

The Live right panel uses a stable two-row composition. The top row is horizontal: preview receives most of the width and compact channel info receives the smaller side card. The bottom row is the EPG detail card. This avoids giant duplicate programme blocks and keeps the player visible on S22 landscape, tablets, and TV.

## Phase 14.2H.4 Live Readability

Compact Live TV uses a 68-70% preview and 30-32% channel-info split in the right top row. Channel info uses tighter internal padding and summary-only text so names and times have room. The EPG panel uses modest extra spacing before next programme information while staying in one viewport.

## Phase 14.2H.5 Text Fit

Compact Live channel info is identity-only: small accent `LIVE TV` label plus prominent channel name. Lower EPG uses a separate `NEXT` label and title line so the next programme reads as a distinct section without adding new cards.

## Phase 14.2I Movies

Movies now uses the shared page header language from Settings and Live TV. Header actions are compact fixed-width cards so compact landscape devices keep Back, branding, centered title, clock/date, Search, and More readable. The Movies search field lives in an overlay, while category search remains in the left rail. Poster cards keep 2:3 media, visible focus border/glow, stable title height, and no permanent action clutter.

## Phase 14.2I.1 Movies Polish

- **⋮ More button**: The Movies header More control is now a compact 44×44dp square icon button with a Canvas-drawn three-dot vertical ellipsis. No text "More" label. Matches the compact Back button style.
- **Left category search removed**: The `OutlinedTextField` ("Search categories") has been removed from the Movies category rail. The rail starts directly with categories: ALL MOVIES, FAVOURITES, HISTORY, then provider categories. Header Search is the sole primary movie search control.
- **Rating formatting**: Raw provider rating strings are formatted as `★ X.X` (one decimal place). Zero, null, blank, non-numeric, or negative values are hidden entirely. The `formatRating()` function is `internal` and covered by unit tests.
- **Fixed-height title region**: Movie poster cards reserve a fixed 52dp height for the title `Box`, ensuring all cards in a grid row align regardless of title length. Maximum 2 lines with ellipsis.
- **Grid bottom padding**: `LazyVerticalGrid` uses `contentPadding = PaddingValues(bottom = 24.dp)` to prevent bottom row clipping.
- **Year on grid cards**: Intentionally omitted. `WatchioMovieItem` does not carry a release date field at list level. Adding it would require a Room schema change (blocked at v6). Year is shown on the Movie Details screen after lazy `get_vod_info` load.
- **Category rail width**: Unchanged at 154dp compact / 220dp non-compact until device testing proves text remains unreadable.
- **Grid column size**: Unchanged at 92dp compact / 132dp non-compact to preserve existing poster density.

## Phase 14.2I.2 Movies Category Height + Overflow Text

- **Reduced Category Card Height**: Category cards in the Movies rail use `minHeight = 54.dp` with compact `8.dp` vertical padding, allowing 4-5 categories to fit on S22 landscape without wasted vertical space.
- **Preserved Theme & WHITE Focus Border**: Uses standard `WatchioCard` keeping `colors.surfaceCard`, `colors.moviesAccent` selection tint, and the official **WHITE** (`colors.focusBorder`) focus border. No custom colors or glow effects.
- **Marquee Text on Overflow**:
  - Inactive categories display single-line text with `TextOverflow.Ellipsis`.
  - Active (focused/selected) categories: if text fits, remains static; if text overflows, activates `Modifier.basicMarquee` to scroll horizontally left smoothly.
  - When focus moves away, marquee immediately stops and text resets with ellipsis.

## Phase 14.2I.3 Movies Search Icon + Poster Card Visual Polish

- **Header Search Icon Button**: Replaces text "Search" in header with a compact 44×44dp square icon button (`MoviesSearchIconButton`) using a Canvas-drawn magnifying glass. Symmetrically matches the 44×44dp ⋮ More button.
- **Poster Rating Badge Overlay**: Formatted movie ratings (`★ X.X`) are displayed as a small translucent badge (`Color.Black.copy(alpha = 0.72f)`) on the top-right corner of the poster. Zero/null/invalid ratings are completely hidden.
- **No Duplicate Rating Row**: The separate rating line below the title is removed. Movie cards consist of: Poster (with top-right rating badge) + fixed 52dp Title region directly below.
- **Preserved Theme & WHITE Focus Border**: 2:3 aspect ratio poster, adaptive grid density, and white focus borders are 100% preserved.

## Phase 14.2I.4 Movies Category Card Compact + Text Centering

- **Compact 48dp Category Cards**: Explicitly sizes each `MovieCategoryRow` to `48.dp` height with `6.dp` bottom gap in `LazyColumn`. Fits 5-6 rows on S22 landscape.
- **Vertically Centered & Left-Aligned Text**: Encloses category label in a full-height `48.dp` container using `Alignment.CenterStart` and `12.dp` horizontal padding, eliminating vertical gap asymmetry.
- **Preserved Theme & WHITE Focus Border**: Standard `WatchioCard` preserves `colors.surfaceCard`, `colors.moviesAccent` selected overlay, and official WHITE focus border.

## Phase 14.2J Series Tab Redesign (Parity with Movies)

- **Unified Shared Header**: Uses `WatchioPageHeader(title = "SERIES")` with compact 44×44dp magnifying glass search icon (`SeriesSearchIconButton`) and 44×44dp ⋮ More icon (`SeriesMoreButton`).
- **Compact 48dp Category Rail**: Uses `SeriesCategoryRow` at `48.dp` height with `6.dp` bottom gap, `Alignment.CenterStart` vertical text centering, and conditional active horizontal marquee for overflowing categories.
- **2:3 Poster Grid & Rating Badges**: `SeriesCard` renders 2:3 aspect ratio poster with top-right dark translucent rating badge (`★ X.X`), zero/missing rating suppression, fixed 52dp title region, and official WHITE focus border.
- **Modal Search Overlay**: Replaces inline search with `SeriesSearchOverlay` matching Movies search UX.

## Phase 14.2H.6 Live TV Header Action Consistency

- **Unified Header Actions**: Live TV, Movies, and Series now strictly share the identical action button layout in `WatchioPageHeader`: `[🔍 Search Icon] [⋮ More Icon]`.
- **Live TV Search Button**: Compact 44×44dp square icon button (`LiveSearchIconButton`) with Canvas magnifying glass icon. No text "Search" in header.
- **Live TV More Button**: Compact 44×44dp square icon button (`LiveMoreButton`) with Canvas 3-dot vertical ellipsis. No text "More" in header.
- **Matching Styling**: Identical 44×44dp dimensions, canvas icon geometry, white focus borders, and D-pad navigation.
