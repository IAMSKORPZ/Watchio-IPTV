# Watchio Native

Native Android implementation of Watchio, built beside the Flutter reference app.

Native playback is foreground-only, and the Activity is landscape-only with `sensorLandscape`. Watchio does not use a background media service.

Current phase: Phase 14.2I Movies visual redesign on top of the Xtream, M3U, EPG, Live TV, Movies, Series, Search, My List, Settings, Home, and Media3 player foundations.

## Build

```powershell
.\gradlew.bat assembleDebug
```

## Test

```powershell
.\gradlew.bat test
.\gradlew.bat lint
```

Instrumentation tests require device/emulator:

```powershell
.\gradlew.bat connectedUitestAndroidTest
```

Connected tests use isolated package `com.watchioiptv.nativeapp.uitest`. The real manual debug app remains `com.watchioiptv.nativeapp.debug` and must be installed with `adb install -r` only. Do not uninstall or clear the real debug package on devices with private provider data. See `docs/TEST_ISOLATION.md`.

Architecture notes:

- `docs/ARCHITECTURE.md`
- `docs/DATABASE.md`
- `docs/SECURITY.md`
- `docs/TESTING.md`
- `docs/XTREAM.md`
- `docs/M3U.md`
- `docs/EPG.md`
- `docs/LIVE_TV.md`
- `docs/PLAYER.md`
- `docs/PLAYER_SETTINGS.md`
- `docs/PLAYBACK_LIFECYCLE.md`
- `docs/MOVIES.md`
- `docs/SERIES.md`
- `docs/SEARCH.md`
- `docs/MY_LIST.md`
- `docs/HOME.md`
- `docs/PROVIDERS.md`
- `docs/SETTINGS.md`
- `docs/TV_GUIDE.md`
- `docs/TV_HARDENING.md`
- `docs/DEVICE_REGRESSIONS.md`
- `docs/PLAYBACK_RELIABILITY.md`
- `docs/DESIGN_SYSTEM.md`
- `docs/TEST_ISOLATION.md`
- `docs/AI_HANDOFF.md`

Flutter Watchio remains production/reference implementation and must stay untouched during native migration phases.
## Phase 9

Native Watchio includes provider-scoped Search and My List foundations for Live TV, Movies, Series, Favourites, Continue Watching, and History.

## Phase 10

Native Watchio includes a responsive Home shell, shared provider management, provider switching, provider refresh/delete controls, and DataStore-backed Settings for theme, input mode, and stream format.

Phase 10.1 adds selectable native themes: Watchio Default, Dark, Purple, and Blue. Theme changes persist in DataStore and apply immediately through Compose.

## Phase 11

Native Watchio includes a provider-scoped TV Guide route. It reuses the existing XMLTV/EPG backend, Live TV playback resolver, and shared Media3 `WatchioPlayerManager`. See `docs/TV_GUIDE.md`.
## Phase 12

Native Watchio includes Android TV / Google TV / BRAVIA / Fire TV hardening for focus, D-pad navigation, leanback launch, and player overlay behavior.

## Phase 13

Native Watchio includes playback reliability hardening for bounded Media3 retry, stale-error clearing, safe VOD/Episode seeking, and request/header preservation.

Phase 13.1E keeps Live TV action buttons readable on S22 landscape and lets TV Guide discover the standard Xtream XMLTV source automatically when no EPG source row exists.

Phase 13.1G adds WorkManager-based EPG auto refresh with default 3-day interval and Settings controls. TV Guide remains Room-first and cached guide data is preserved on refresh failure.

## Phase 14

Native Watchio includes a shared Compose design system foundation for tokens, focusable cards, buttons, chips, poster shells, list rows, headers, loading, empty, error, and progress states. Phase 14.0 only pilots low-risk Live TV action buttons and does not redesign working screens.

Phase 14.2D adds first-run Mobile/TV onboarding, provider-required startup routing, and section-specific Home refresh for Live TV, Movies, and Series. See `docs/ONBOARDING.md`.

Phase 14.2D.1 fixes provider-management Back navigation. Home -> Playlist now enters a Home-returning Providers route; first-run provider setup remains guarded.

Phase 14.2D.2 makes Xtream Codes the required login gate before Home. First run is Device Mode -> Xtream Login -> Home. M3U providers remain in provider management after login, but M3U alone does not unlock Home.
# Phase 14.2E

Settings now uses a Watchio visual category menu. Existing EPG, stream format, input mode, appearance, and provider-management behavior is preserved behind category routes. Placeholder categories do not implement future functionality yet.

# Phase 14.2F

Settings -> Account Information now shows safe selected-Xtream metadata: provider name, masked username, status, expiry, provider type, added date, and refresh timestamps. Passwords and credential-bearing URLs are not displayed. Room remains v6.

Phase 14.2F.1 persists Xtream connection metadata from `user_info` in DataStore. Account Information now shows maximum connections, active connections, and output formats when the provider supplies them. Failed refreshes preserve the last successful values.

Phase 14.2G replaces the Player Settings placeholder with DataStore-backed playback preferences for resume, remembered Live TV, controls, retry, and video scaling. Media3 remains the only player and playback remains foreground-only.

Phase 14.2H updates the Live TV screen UI with category/search rails, channel logo rows, preview, now/next EPG, and manual EPG refresh while preserving Media3, provider storage, Room v6, and foreground-only playback.

Phase 14.2H.1 aligns Live TV with the shared Settings header and moves fullscreen/favourite/retry behavior into activation/context-menu flows. Room remains v6.

Phase 14.2H.2 fixes Live TV programme hierarchy. The selected-channel panel now shows channel name, current programme, time range, progress, and next programme. The lower EPG panel shows current programme detail, next programme, no-EPG fallback, and Refresh EPG without changing playback, Room, or provider storage.

Phase 14.2H.3 corrects Live TV right-panel layout: preview plus compact channel info in the top row, programme / EPG detail below. Buffering and player errors render inside preview. Room remains v6.

Phase 14.2H.4 polishes S22 landscape readability: channel info gets more top-row width, preview stays dominant, preview/info heights align, EPG spacing improves, and no player/provider/Room architecture changes.

Phase 14.2H.5.1 final-cleans Live TV Channel Info: side card shows only Live TV label and channel name, while lower EPG owns programme title, time, progress, and next. Playback, provider, EPG, and Room architecture remain unchanged.

Phase 14.2H.5.2 fixes Live TV header search on compact landscape devices. Header Search now opens a separate overlay with channel results; the shared header remains fixed and category search remains left-panel only. Room remains v6.

Phase 14.2I gives Movies the same shared Watchio header as Live TV/Settings, moves movie search into a separate overlay, separates category search into the left rail, and keeps the main area as an adaptive poster lazy grid. Movie Details, playback, favourites, history, and Room v6 are preserved.

Phase 14.2I.1 polishes Movies UI: replaces More text with compact 3-dot (⋮) button, removes left rail category search so system categories (ALL MOVIES, FAVOURITES, HISTORY) are visible at the top, formats ratings as `★ X.X` while hiding zero/invalid ratings, establishes fixed 52dp title region for card alignment, and adds bottom padding to prevent grid clipping.

Phase 14.2I.2 reduces Movies category card height to 54dp so more rows fit vertically on S22 landscape while preserving the approved Watchio theme and WHITE focus border. Overflowing category text smoothly scrolls horizontally (marquee) when active (focused or selected); inactive long categories remain cleanly ellipsized and short category text remains static.

Phase 14.2I.3 completes Movies header icon and poster card visual polish: header Search is now a compact 44×44dp magnifying glass icon button matching the ⋮ button, movie ratings are displayed as small dark translucent badges (`★ X.X`) over the top-right of the poster, zero/missing ratings are cleanly omitted, and the redundant rating line below the title is removed while preserving the fixed 52dp title region, 2:3 poster ratio, and WHITE focus border.

Phase 14.2I.4 refines Movies category cards: sets card height to compact 48dp with 6dp spacing so 5-6 categories fit vertically on S22 landscape, and vertically centers category text via `Alignment.CenterStart` with comfortable 12dp left/right padding while preserving the WHITE focus border, conditional marquee on overflow, and movie grid presentation.

Phase 14.2J redesigns the Series browsing tab to match the Movies design system: shared Watchio header (Back, Watchio branding, centered `SERIES`, clock/date, compact 44×44dp search icon, compact 44×44dp ⋮ More icon), compact 48dp category rail (`ALL SERIES`, `FAVOURITES`, `HISTORY`, provider categories) with vertically centered text and active marquee overflow, 2:3 adaptive poster grid with top-right dark translucent rating badges (`★ X.X`), fixed 52dp title region, modal Series search overlay, and Series options dialog. Series Details, season/episode hierarchy, and Media3 playback remain preserved.

Phase 14.2H.6 unifies the Live TV header actions with Movies and Series: replaces text "Search" and "More" buttons with the exact matching compact 44×44dp magnifying glass search icon [🔍] and 3-dot vertical ellipsis more icon [⋮]. Live TV channel search overlay, EPG refresh, player preview, and Room v6 architecture remain unchanged.

Phase 14.2K standardises the unified search architecture across Home, Live TV, Movies, and Series: establishes 4 distinct search scopes (Home Global, Live TV, Movies, Series), enforces category independence (`selectedCategory != searchScope`), maintains strict active provider isolation, organizes Home Global Search into grouped `LIVE TV`, `MOVIES`, `SERIES` sections with quick scope filters, ensures proper navigation to preview / detail destinations without auto-play, preserves Back precedence (search closes first), and operates purely on Room indexed local data with zero network overhead. Room remains v6.
