# Watchio Native Live TV

Phase 6 adds the first native Live TV experience.

## Architecture

`LiveTvScreen` talks to `LiveTvViewModel`. The ViewModel uses `LiveTvRepository`, favorites, history, EPG, and `WatchioPlayerManager`. Compose does not query Room, build Xtream URLs, or read provider passwords.

## Categories

Live TV categories are provider-neutral:

- `ALL CHANNELS`
- `FAVOURITES`
- `HISTORY`
- provider categories from Room

Provider category order is preserved from import. M3U groups and Xtream live categories feed the same UI model.

## Channels

`LiveTvChannel` represents both Xtream and M3U live channels. It carries id, provider id/type, name, logo, category, EPG id, M3U direct URL where applicable, request headers, server order, and favorite state.

## Playback

Selecting a channel resolves playback and loads it into the shared player manager. Xtream uses the secure resolver. M3U uses the persisted direct URL plus item headers.

## Preview And Fullscreen

Channel selection starts playback in preview. Fullscreen reuses the same player session and only moves the surface. Back exits fullscreen to preview without restarting the stream.

Phase 12.1 makes the preview surface focusable and handles DPAD_CENTER, Enter, and NumPad Enter, so Android TV remotes can open fullscreen without touch.

Phase 13.1E kept the former preview action controls compact in phone landscape and TV side panes. Phase 14.2H.1 removed those permanent action buttons from the normal Live TV screen.

## Phase 14.2H Layout

Live TV now uses a three-panel landscape-first layout:

- left category rail with All, Favorites, History, and provider categories
- middle searchable channel list with logo-aware rows
- right preview, selected-channel info, and EPG now/next

The screen keeps the app-scoped `WatchioPlayerManager`; no player object, URL resolver, SecretStore path, Room schema, or playback lifecycle is replaced. Category focus never auto-plays the first channel. Playback begins only when the user selects a channel.

Category search filters the category rail only. Header Search opens a separate dimmed channel-search overlay; it never inserts a text field into the page header. Channel search filters provider-neutral live channels by name and does not mutate persisted catalog data. Selecting a result closes the overlay, selects that channel, and starts preview playback without entering fullscreen. The More action reuses the existing EPG refresh coordinator and keeps cached EPG when refresh fails.

## Phase 14.2H.1 Interaction Cleanup

Live TV uses the shared Settings-style header: compact Back icon, Watchio logo/name, centered `LIVE TV`, clock/date, Search, and More. The old Live-specific header and extra content Back control are gone.

The shared header is structurally stable. Opening channel search leaves Back, branding, centered title, clock/date, Search, and More in place; the search text field lives only in the overlay.

Permanent `Fullscreen`, `Favorite`, and `Retry` buttons were removed. A different channel click starts preview. Clicking the currently selected channel again opens fullscreen. Clicking/focusing preview and pressing OK/Enter opens fullscreen only when a channel is selected. Focus movement alone does not fullscreen.

Channel rows support long press through `combinedClickable`. The Channel Options dialog acts on the long-pressed channel without switching playback and contains favourite toggle, cached programme information, optional Retry Stream when the selected channel has failed, and Close. Back dismisses the dialog before normal Live TV Back.

## Phase 14.2H.2 Programme Information

The selected-channel information card now uses this hierarchy:

- small `LIVE TV` badge
- selected channel name as the primary heading
- current programme title, or `No programme information`
- current start/end time when available
- progress bar when the current programme has a valid time window
- `Next: ...` when a next programme exists

The lower EPG panel is the detailed programme area. It shows the current title, time range, progress, next programme, and description. If there is no selected channel it shows `No programme selected`. If the selected channel has no cached EPG match it shows `No EPG Information Available` plus a Refresh EPG action. It does not show stale programme data from a previously selected channel.

The compact phone-landscape layout keeps the programme information visible by ordering the right panel as selected-channel info, EPG detail, then a small preview surface. TV and larger landscape layouts keep the larger preview/detail arrangement.

## Phase 14.2H.3 Right Panel Layout

The approved right-side composition is:

- top row: preview player on the left, compact selected-channel information on the right
- bottom row: one wide programme / EPG details panel

The preview is never placed below programme cards. Player states such as `Connecting...`, `Buffering...`, and playback errors render inside the preview surface so the layout does not jump when player state changes.

Compact channel info contains only identity content: `LIVE TV` badge and channel name. Full programme detail belongs in the lower EPG panel. There is no permanent Fullscreen, Favourite, or Retry button row.

## Phase 14.2H.4 Spacing And Readability

The right panel keeps the H.3 structure and refines compact landscape sizing:

- top row remains preview plus compact channel info
- preview uses about 68-70% of the top-row width
- channel info uses about 30-32% of the top-row width
- preview and channel info align top and bottom
- right panel keeps slightly tighter separation between top row and EPG detail
- lower EPG gets more breathing room for current title, time, progress, and next programme

Compact channel info stays identity-only. Programme title, programme time, next programme, and longer description stay in the lower EPG panel on compact layouts.

## Phase 14.2H.5 Micro Polish

Compact channel info is now final-cleaned to show only `LIVE TV` and channel name. It no longer duplicates programme title, current time, progress, next programme, or description.

Lower EPG detail now separates `NEXT` from the progress bar with its own label and title line. The current `LiveTvNowNext` model does not carry next-programme start/end times, so next time is not displayed until that data exists.

## EPG

The selected channel uses Phase 5 EPG data for now/next and progress. Missing EPG shows a clean fallback.

Phase 14.2H surfaces current programme title, optional description, time range, progress, next programme, and a manual Refresh EPG action. Missing guide data remains a normal no-EPG state, not a playback failure.

## Favorites And History

Favorites use the Phase 2 repository. Live history is updated once per channel selection/play start, not continuously.

## Focus And Input

Live TV uses TV-focusable controls and lazy lists. Fullscreen supports OK/Enter/Space to show controls, Back/Escape to exit, and touch/click to toggle controls. Overlay controls auto-hide while video remains visible.

## Deferred

No movie playback, series playback, Media3 background service, PiP, VLC, FFmpeg, or final TV Guide UI is included.
## Phase 9 Library Integration

Live TV participates in Global Search, Favourites, and History. Live rows do not appear in Continue Watching because there is no resumable timeline.

## Phase 11 TV Guide Integration

TV Guide reuses `LiveTvRepository` for selected-provider live channels and Play Live requests. Channel playback semantics remain shared with Live TV.

## Phase 14.2H.6 Header Action Consistency

Live TV header actions now match Movies and Series with compact 44×44dp square icon buttons:
- **Search**: [🔍] Canvas-drawn magnifying glass icon (`LiveSearchIconButton`). Semantics: `"Search Live TV"`. Clicking opens the existing `LiveChannelSearchOverlay`.
- **More**: [⋮] Canvas-drawn 3-dot vertical ellipsis (`LiveMoreButton`). Semantics: `"More Live TV Options"`. Clicking invokes the existing `onRefreshEpg` action.
- Header structure: `[Back] [Watchio Logo + Watchio]     LIVE TV     [Time/Date] [🔍] [⋮]`
- Channel list, preview, Channel Info, EpgPanel, Room v6, and Media3 playback remain unchanged.

## Phase 14.2K Unified Search Integration

- Live TV search queries the entire Live TV channel catalog for the active provider, ignoring whichever category is currently highlighted.
- Dismissing search immediately restores the browsing channel list for the selected category.
- Selecting a channel from search dismisses search, selects the channel, and starts preview playback.
- Search overlay preserves Back precedence (Back dismisses search overlay first before leaving Live TV).
