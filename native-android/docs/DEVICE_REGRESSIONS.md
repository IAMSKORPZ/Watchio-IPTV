# Watchio Native Device Regressions

Phase 12.1 tracks Samsung Galaxy S22 and Sony BRAVIA fixes.

## Devices

- Samsung Galaxy S22: `SM_S901B`
- Sony BRAVIA: `BRAVIA_4K_VH22`, Android 12, serial `192.168.1.49:5555`

## S22 TV Guide

Manual S22 reproduction confirmed a horizontal timeline crash. Logcat showed:

- `FATAL EXCEPTION: main`
- `java.lang.IllegalArgumentException: Padding must be non-negative`
- source: `TvGuideScreen.kt`, `NowLine`

Root cause: horizontal scroll moved the NOW indicator left of the visible guide viewport. `NowLine` converted that negative x-position into `Modifier.padding(start = offset.dp)`, which Compose rejects.

Fix: `TvGuideTimeline.nowLineOffsetDp(...)` now returns `null` when the NOW line is left of the scrollable viewport, and `NowLine` skips drawing it until it is visible again. Gap/tail width math is also clamped so malformed EPG durations cannot create negative spacing.

The guide ViewModel now observes selected-provider changes and reloads reactively. This prevents a guide shell from staying empty when the route is created before DataStore/provider state is ready.

## S22 Settings

Root cause: Settings used a fixed, non-scrollable vertical layout with fixed-height option grids.

Fix: Settings now scrolls vertically and wraps Theme, Input Mode, and Stream Format options into responsive rows. Stream Format and Back remain reachable on compact screens.

## BRAVIA Settings

Root cause: the same fixed Settings layout clipped lower options on 1080p TV.

Fix: the shared Settings layout scrolls on TV and keeps all controls as focusable cards. No TV-only duplicate screen was added.

## BRAVIA Live Fullscreen

Root cause: Live TV preview was clickable for touch but was not a focusable TV target.

Fix: preview is focusable, visibly focused, and handles DPAD_CENTER, Enter, and NumPad Enter by invoking the existing fullscreen callback. It still uses the shared `WatchioPlayerManager`.

## BRAVIA TV Guide Data

Likely root cause: selected-provider timing. TV Guide could render its shell while provider state was not ready.

Fix: TV Guide observes provider changes and reloads. Channels continue to come from the active provider live catalog, and no-EPG channels remain visible.

## ALL MOVIES

Root cause: M3U movie rows can store filename-derived fallback names. ALL MOVIES and provider categories already shared the same M3U entity path, but the mapper exposed raw filenames directly.

Fix: M3U movie domain mapping now cleans common separators, file extensions, and codec/resolution tokens. Xtream mapping is unchanged.

## Security

No provider URL, username, password, playlist token, or EPG source is documented. Device installs used `install -r`; app data was not cleared.
## Phase 13.1B - S22 Landscape Responsive Content

Root cause: after enforcing landscape, several content screens still used fixed-width/tablet assumptions. On a phone-width landscape window this left Live TV with a narrow channel column and let poster grids fall back to too few columns.

Fix:

- Live TV now uses weighted landscape panes: categories 24%, channels 34%, preview 42%. Channel/category labels clamp to two lines with ellipsis, and preview uses 16:9 aspect ratio instead of fixed height.
- Movies and Series compact landscape category rail is 150dp, and the main catalog grid uses five fixed columns for phone/tablet landscape content widths below 900dp.
- Movie and Series poster cards preserve 2:3 poster aspect ratio.
- TV Guide keeps source-status messaging separate from per-channel empty rows; channels with no programmes still render "No programme information".

Validation:

- Added `LandscapeResponsiveComposeTest` covering Live TV channel/preview width, Movies five-card row, Series five-card row, and TV Guide no-programme row.
- S22 connected instrumentation: 44/44 PASS twice.
- Installed with `adb -s adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp install -r`, preserving app data.

## Phase 13.1C - Responsive Five-Column Poster Grids

Root cause: Phase 13.1B still allowed Movies and Series to grow beyond five columns on wider landscape surfaces. That made TV/tablet poster density diverge from the target design.

Fix:

- Movies and Series catalog grids now use `GridCells.Fixed(5)` for the main content area after the category sidebar.
- Card width is owned by Compose grid measurement: remaining content width minus inter-card spacing is split equally across five columns.
- Posters keep a 2:3 aspect ratio and scale with the calculated card width.
- No horizontal grid scrolling is introduced; vertical lazy-grid behavior remains.

Validation:

- `LandscapeResponsiveComposeTest` verifies first-row five-card layout, fifth-card bounds, no card overlap, and 2:3 poster ratio.

## Phase 13.1D - TV Guide Categories And Live TV Action Labels

Root cause:

- TV Guide always loaded the Live TV `All` category inside `TvGuideRepository`, so users could not filter guide rows by imported provider category.
- Live TV right-pane action cards were valid actions (`Favorite`, `Retry`) but inherited the default 160dp card minimum width inside a narrow horizontal row, leaving clipped/blank-looking controls on S22 landscape.

Fix:

- TV Guide now reuses `LiveTvRepository.categories(providerId)` and `LiveTvRepository.channels(providerId, category)`.
- UI adds a D-pad/touch focusable `Category: ...` selector that opens a category picker dialog.
- Selected category is preserved across NOW/day changes and EPG refresh; if missing, state falls back to All Channels.
- Category filtering happens before guide row presentation, while channels with no EPG still render with `No programme information`.
- Live TV action row now has explicit compact labelled buttons: `Fullscreen`, `Favorite`/`Unfavorite`, and `Retry`.

Validation:

- Repository tests cover All Channels, provider category filtering, provider isolation, and no-EPG row preservation.
- Compose tests cover TV Guide category selector and Live TV visible action labels.

## Phase 13.1E - Live Action Sizing And Automatic XMLTV Discovery

Root cause:

- The Live TV right-pane action row could still fall below the visible S22 landscape pane after the preview and EPG labels.
- TV Guide refresh depended on an existing EPG source row. Xtream providers with no persisted source could show only the guide shell until manual source creation.

Fix:

- Live TV preview actions now use one compact labelled row with smaller padding and explicit text style, keeping `Fullscreen`, `Favorite`/`Unfavorite`, and `Retry` visible without changing action behavior.
- `EpgRepository.refresh()` discovers candidate sources per provider. Existing custom/M3U sources remain first; Xtream falls back to an on-demand standard `xtream_xmltv` source.
- `TvGuideViewModel` performs one automatic EPG discovery/refresh when the selected provider has no source.
- TV Guide now separates source-missing, loading, and source-without-programmes messages.

Validation:

- Unit/lint/debug build pass after the fix.
- S22 connected instrumentation covers readable Live TV action labels and Xtream fallback XMLTV discovery.

## Phase 13.1F - Persistent EPG Cache And Reopen Loading

Root cause:

- `TvGuideViewModel.load()` did not guard repository failures, so an exception during reopen/load could leave `loading = true`.
- Once an EPG source row existed, the guide did not attempt refresh when source existed but cached EPG rows were empty.
- Real S22 DB inspection proved cache rows survived: one Xtream source, 978 EPG channels, 24,005 active programmes, 244 live categories, and 10,221 live streams. Re-entry was therefore a provider/state/query path problem, not XMLTV deletion.
- Re-entry defaulted into an expensive All Channels guide build before categories were published to UI. Matching scanned EPG channels repeatedly for each live channel, so the user saw the initial empty category dialog and loading shell for too long.

Fix:

- Guide load is Room-first and preserves existing visible channels/programmes while loading new window data.
- Load failures now clear `loading` and show a safe error instead of leaving an infinite spinner.
- A provider gets one bounded source refresh attempt when there is no source or when source exists but EPG cache counts are empty.
- Failed refresh clears loading/refreshing and keeps visible cached data.
- The ViewModel now uses the provider emitted by the selected-provider observer for guide loading instead of re-reading provider state inside the heavy guide query.
- Live categories are loaded and published before programme matching/query work, so the category picker is independent from EPG availability and heavy guide construction.
- EPG matching is indexed per guide load instead of scanning the EPG channel list repeatedly.

Validation:

- EPG repository tests cover repeated refresh without duplicate active rows.
- TV Guide repository tests cover cached data after repository recreation and category filtering from Room.
- ViewModel instrumentation covers no-cache source failure exiting loading.
