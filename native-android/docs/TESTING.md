# Watchio Native Android Testing

## Unit Tests

Run on Windows:

```powershell
.\gradlew.bat test
```

Unit tests cover pure Kotlin behavior such as URL masking, theme state, and provider credential store logic.

## Room Instrumentation Tests

Room tests use `Room.inMemoryDatabaseBuilder` with the target app context and close the database in `@After`.

Focused isolated run:

```powershell
adb shell am instrument -w -r -e class com.watchioiptv.nativeapp.DatabaseRepositoryTest com.watchioiptv.nativeapp.uitest.test/androidx.test.runner.AndroidJUnitRunner
```

## DataStore Instrumentation Tests

DataStore tests must not share production settings files. `DataStoreSettingsTest` creates a unique file under the target app test directory for each test and owns a dedicated IO coroutine scope. Cleanup cancels that scope and deletes the file.

This avoids duplicate active DataStore instances for one backing file in the same instrumentation process.

Focused isolated run:

```powershell
adb shell am instrument -w -r -e class com.watchioiptv.nativeapp.DataStoreSettingsTest com.watchioiptv.nativeapp.uitest.test/androidx.test.runner.AndroidJUnitRunner
```

## UI Instrumentation Tests

UI tests launch `MainActivity` and verify the Compose host is attached on TV hardware. Provider form validation is covered by focused unit tests; full text navigation is checked manually when a stable accessibility root is available.

Phase 14.2A adds Home visual hierarchy coverage using stable test tags for the redesigned Home cards and compact top actions. Tests avoid ambiguous duplicate labels such as Settings, because the Home top bar and secondary action can both expose the same user-facing destination.

Phase 14.2B updates Home tests for the balanced reference layout. Connected instrumentation may run with no provider configured, so tests validate the no-provider Add Provider CTA in that state and validate loaded-provider cards when provider data exists. Playlist header routing is tested against the existing Providers route.

Phase 14.2B.1 keeps Room at v6. Home status testing focuses on stable semantics and repository persistence: provider `lastRefreshAtEpochMs` drives the `Updated last` label, Xtream expiry metadata is DataStore-backed by provider id, and no cached counts are used in primary Home status strings.

Focused isolated run:

```powershell
adb shell am instrument -w -r -e class com.watchioiptv.nativeapp.WatchioNavigationTest com.watchioiptv.nativeapp.uitest.test/androidx.test.runner.AndroidJUnitRunner
```

## Connected Device Requirements

Use exactly one stable device in `device` state:

```powershell
adb devices -l
```

Offline emulators or unreachable wireless devices can make Gradle/UTP choose a bad target. If stale native debug installs interfere, uninstall only:

```powershell
adb uninstall com.watchioiptv.nativeapp.uitest
adb uninstall com.watchioiptv.nativeapp.uitest.test
```

Do not uninstall or clear `com.watchioiptv.nativeapp.debug` on manually configured devices such as the S22. That package contains the real provider, Room data, DataStore settings, SecretStore credentials, favourites, history, and EPG cache.

Phase 14.2C moves connected instrumentation to an isolated `uitest` build:

- real/manual app: `com.watchioiptv.nativeapp.debug`
- isolated test app: `com.watchioiptv.nativeapp.uitest`
- test runner package: `com.watchioiptv.nativeapp.uitest.test`

See `docs/TEST_ISOLATION.md`.

## Full Validation

```powershell
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleUitest
.\gradlew.bat connectedUitestAndroidTest
.\gradlew.bat connectedUitestAndroidTest
```

Connected test reports are written under `app/build/reports-phase2/<run-id>/connected/uitest`. Set `WATCHIO_ANDROID_TEST_RUN_ID` to make the run folder deterministic.

## Xtream Tests

Xtream tests use fake credentials and MockWebServer only. They cover successful import, invalid auth, refresh, duplicate detection, provider deletion secret cleanup, playback URL masking, flexible DTO mapping, URL normalization, and a synthetic large catalog.

## M3U Tests

M3U tests use fake playlists only. Unit tests cover parser parity for attributes, EXTGRP, fallback names/categories, content classification, series detection, BOM/CRLF, HLS media-manifest rejection, and URL masking.

Instrumentation tests use MockWebServer and fake local input streams. They cover URL import, local-file import, category/count persistence, failed refresh preservation, favorites/history stable identity, and a 50,000-entry playlist.

## EPG Tests

EPG unit tests cover XMLTV timezone parsing, URL masking, now/next progress math, and centralized channel matching priority.

EPG instrumentation tests use MockWebServer and fake XML only. They cover plain XML, gzip, Brotli, `304 Not Modified`, failed refresh preservation, Xtream XMLTV source resolution without Room credentials, provider isolation, a 2,000-channel/10,000-programme guide, and Room migration `3 -> 4`.

## Live TV / Player Tests

Phase 6 unit tests cover player state metadata for same-session preview/fullscreen behavior. Instrumentation tests cover provider-neutral Live TV category/channel mapping, M3U User-Agent/Referer propagation, virtual Favorites/History, and direct playback request creation. Real decoder/provider behavior still needs BRAVIA/manual smoke with private streams.

Phase 13 unit tests cover retry/recovery state safety and VOD/live seek clamping. S22 connected tests are the current automated connected target when online. Real long-duration playback soak remains manual and must not be claimed from automation alone.

## Movies Tests

Phase 7 unit tests cover flexible Xtream VOD detail parsing and resume thresholds. Instrumentation tests cover M3U movie mapping, headers, virtual category behavior, playback request creation, and a 10,000-movie catalog. Real VOD decode still needs private BRAVIA smoke.

Phase 14.2I adds compact-landscape Compose coverage for the shared Movies header, fixed header layout while movie search is open, separate movie-search overlay, separate category search, adaptive poster density, missing-poster fallback, and result selection into the existing Movie Details route.

## Series Tests

Phase 8 unit tests cover flexible Xtream `get_series_info` parsing, episode URL resolution/masking, and shared VOD resume thresholds. Instrumentation coverage continues to validate Room identity/cascade behavior, while manual Tab S9 smoke checks Series navigation, touch layout, and real episode playback when a private provider has suitable content.
## Phase 9

Repository tests cover provider-scoped search, scoped search, Continue Watching filtering, favourite removal, and a 30,000-row live search limit/order case. Connected tests remain the authority for Compose and Room integration on device.

## Phase 11

TV Guide unit tests cover deterministic time-to-width mapping, clipping, progress clamping, and DST day windows. Instrumentation tests cover provider isolation, no-EPG channels, bounded guide windows, and a 2,000-channel / 10,000-programme guide.
## Phase 12

Phase 12 validates BRAVIA/Android TV readiness with connected tests, scoped ADB install/launch, manifest audit, focus audit, and manual remote-only smoke where available.

## Phase 13.1E

S22 connected tests cover compact Live TV action labels and Xtream EPG fallback discovery. EPG tests verify explicit/custom source priority before standard `xmltv.php`, while keeping credentials out of Room/logs.

## Phase 14 Design System

Design-system instrumentation tests cover shared component rendering, accessible labels, compact action labels, progress/error/loading/empty states, and poster ratio behavior. Existing connected regression tests remain the guard for provider, playback, EPG, Settings, Movies, Series, Search, My List, resume, and TV Guide behavior.

Phase 14.1 adds Home Compose coverage for the polished hierarchy and Live TV route activation. Existing TV Guide and Settings tests continue to verify Home scroll reachability for downstream routes.

## Phase 14.2D

Bootstrap unit tests cover existing-user migration, genuine first-run device-mode selection, and Input Mode persistence. Connected tests must continue to use the isolated `connectedUitestAndroidTest` task only.

## Phase 14.2D.1

Providers Back regression is covered by navigation behavior and device smoke: Home opens provider management through the `providers/home` route, while first-run/no-provider setup keeps the guarded `providers` route. Connected tests remain isolated from the real debug package.

## Phase 14.2D.2

Bootstrap unit tests cover the strict Xtream Home gate: fresh state needs Device Mode, completed mode without Xtream needs Xtream login, M3U alone does not unlock Home, existing Xtream users skip onboarding, and stale selected ids recover to another enabled Xtream provider. Connected tests must still use `connectedUitestAndroidTest`.
# Phase 14.2E Settings Tests

Settings menu coverage uses the isolated `uitest` app and `connectedUitestAndroidTest`. Tests assert the requested root categories, absence of My List, scroll to Check for Updates, category back-stack behavior, and Settings Back to Home. Real debug app data must not be cleared for this phase.

# Phase 14.2F Account Information Tests

Account Information unit tests cover selected Xtream provider metadata, masked username display, expiry-derived status, independent section refresh timestamps, missing optional data, and provider switching isolation. Settings Compose tests verify the Account Information route opens real content, has exactly one header Back control, removes the placeholder text, and does not render password text.

Phase 14.2G adds DataStore tests for Player Settings defaults, persistence, and provider-scoped remembered Live TV channel ids. Settings Compose tests verify Player Settings is no longer a placeholder and exposes real sections.

Phase 14.2F.1 adds tests for flexible Xtream account metadata parsing, provider-scoped DataStore metadata, persisted metadata after successful import, and preservation of previous metadata after failed section refresh.

Phase 14.2H adds Live TV route assertions for the redesigned category rail, channel list, preview, info panel, EPG panel, search action, and refresh action. Existing player, repository, and isolated connected test suites remain the regression guard.

Phase 14.2H.1 updates Live TV tests for the shared Settings-style header and verifies the old permanent `Fullscreen`, `Favorite`, and `Retry` button strip is absent from the normal Live screen. Phase 14.2H.5.2 adds a compact-landscape regression test proving Header Search opens an overlay, keeps `LIVE TV` and clock/date visible, keeps the text field out of the header row, filters channels, and selects a result back into preview.

Phase 14.2H.2 adds compact Live TV regression coverage for selected-channel programme display, no-EPG fallback, next programme display, time range display, and stale now/next suppression when no channel is selected. Connected testing continues to use only `connectedUitestAndroidTest`; never uninstall or clear the real `com.watchioiptv.nativeapp.debug` package on user devices.

Phase 14.2H.3 adds Live TV layout regression coverage that checks preview is above the EPG panel, preview and channel info share the top row, preview remains wider than compact info, and `Buffering...` stays inside the preview surface.

Phase 14.2H.4 strengthens those checks with compact preview width, channel-info minimum width, top/bottom alignment, readable programme time, and no-EPG geometry preservation.


Phase 14.2H.5.1 updates compact-channel-info regression coverage: Channel Info contains only Live TV label and channel name, while programme title/time/NEXT remain below in EPG.

# Phase 14.2I Movies Tests

Phase 14.2I adds Movies Compose coverage including shared header Back/branding/title/clock, header Search opening an overlay without header collision, category rail visibility, and poster grid five-card-first-row assertion.

# Phase 14.2I.1 Movies Polish Tests

Phase 14.2I.1 adds the following new tests in `LandscapeResponsiveComposeTest`:

- `moviesMoreButtonIsCompactNotText` — asserts the `movies-more` tag is displayed and the text "More" is absent (icon-only three-dot button).
- `moviesRailShowsSystemCategoriesFirst` — asserts `movie-category-all`, `movie-category-favorites`, `movie-category-history` are visible and vertically ordered before provider categories.
- `moviesRailHasNoLeftCategorySearchField` — asserts `movie-category-search` tag and "Search categories" text are both absent from the Movies rail.
- `moviesRatingZeroIsHidden` — asserts that a movie with rating "0" shows neither "0" nor "★ 0.0" and has no `movie-rating` tag.
- `moviesRatingIsFormattedWithStarAndOneDecimal` — asserts rating "6.458" is displayed as "★ 6.5" and the raw "6.458" is absent.
- `moviesTitleRegionHasConsistentHeight` — asserts all `movie-title-region` boxes have the same height (within 4px tolerance) across 6 cards with varying title lengths.
- `moviesSearchOverlayOpensAndClosesWithBack` — asserts overlay absent initially, opens on Search click, closes on Close click.

The existing `moviesHeaderSearchOpensOverlayAndKeepsCategorySearchSeparate` test has been updated: the final assertion flipped from `assertIsDisplayed()` to `assertTrue(...isEmpty())` for `movie-category-search` to reflect the removed left search field.

Phase 14.2I.1 also adds JVM unit tests in `MovieBehaviorTest`:

- `formatRatingNullReturnsNull`
- `formatRatingBlankReturnsNull`
- `formatRatingZeroStringReturnsNull`
- `formatRatingNegativeReturnsNull`
- `formatRatingNonNumericReturnsNull`
- `formatRatingDecimalRoundsToOnePlace`
- `formatRatingWholeNumberShowsDecimalPoint`
- `formatRatingTrimmedInput`

# Phase 14.2I.2 Movies Category Height & Overflow Tests

Phase 14.2I.2 adds Compose tests in `LandscapeResponsiveComposeTest`:

- `moviesCategoryCardHeightIsReduced` — verifies Movies category cards have reduced height (`<= 60.dp`, around 54dp) compared to previous 92dp+ default.
- `moviesMultipleCategoryRowsFitOnS22Landscape` — verifies at least 4-5 categories are simultaneously displayed in the rail on S22 landscape.
- `moviesCategorySelectionAndDpadNavigationWorks` — verifies selecting a category invokes `onCategory` callback.
- `moviesShortAndLongCategoryTextRenderCleanly` — verifies short category names (`ACTION`) and long names (`JUST RELEASED HOLLYWOOD 4K ULTRA HD MOVIES`) render cleanly without layout exceptions.
- `moviesHeaderAndGridRemainIntactInPhase14_2I_2` — verifies Header, Search, More, and Movie Grid remain fully intact and visible.

# Phase 14.2I.3 Movies Search Icon & Poster Card Tests

Phase 14.2I.3 adds Compose tests in `LandscapeResponsiveComposeTest`:

- `moviesNoDuplicateRatingBelowTitle` — verifies only 1 rating element exists per rated card and no redundant rating text line is rendered below the title.

# Phase 14.2I.4 Movies Category Card Compact & Centering Tests

Phase 14.2I.4 adds Compose tests in `LandscapeResponsiveComposeTest`:

- `moviesCategoryCardHeightIsCompact48to52Dp` — measures rendered bounds of `movie-category-all` to ensure card height is in range `47.dp..52.dp`.
- `moviesCategoryTextIsVerticallyCentered` — asserts top and bottom gaps around category text are within 4dp of each other (vertically centered) and left padding is in range `8.dp..18.dp` (left-aligned).
- `moviesMultipleCategoryRowsFitOnS22LandscapePhase14_2I_4` — verifies at least 5 category rows are simultaneously displayed on S22 landscape.

# Phase 14.2J Series Tab Redesign Tests

Phase 14.2J adds Compose tests in `LandscapeResponsiveComposeTest`:

- `seriesScreenUsesSharedHeaderAndCorrectTitle` — verifies Series uses shared Watchio header with title "SERIES", compact 44×44dp search icon button, and compact 44×44dp ⋮ More icon button.
- `seriesCategoryRailHasCompactHeightAndSystemCategoriesFirst` — verifies `ALL SERIES`, `FAVOURITES`, `HISTORY`, and provider categories render with compact 48dp height and vertically centered text.
- `seriesGridPosterCardAspectAndRatingBadge` — verifies 2:3 aspect ratio posters, top-right rating badges (`★ 8.2`), zero rating suppression, and fixed 52dp title region.
- `seriesTapOpensDetailsAndLongPressOpensOptions` — verifies tap triggers `onSeries` (opens Details) and long click shows `SeriesOptionsDialog`.
- `seriesSearchOverlayOpensAndResultsWork` — verifies clicking search icon opens `SeriesSearchOverlay`, search filtering works, and selecting a result opens Details.

# Phase 14.2H.6 Live TV Header Consistency Tests

Phase 14.2H.6 adds Compose tests in `LandscapeResponsiveComposeTest`:

- `liveTvHeaderSearchAndMoreAreCompactIconsMatchingMoviesAndSeries` — verifies Live TV uses compact 44×44dp search icon button [🔍] and compact 44×44dp More icon button [⋮] matching Movies/Series, that text buttons "Search" and "More" are absent from the header, that search click opens `LiveChannelSearchOverlay`, and that more click triggers `onRefreshEpg`.

# Phase 14.2K Unified Search Architecture Tests

Phase 14.2K adds tests in `SearchArchitectureUnitTest` and `LandscapeResponsiveComposeTest`:

- `contentRouteResolvesCorrectScreenDestinations` — verifies `contentRoute` correctly maps `ContentType.Live` to `"live/$id"`, `ContentType.Movie` to `"movies/$id"`, and `ContentType.Series`/`Episode` to `"series/$id"`.
- `textNormalizerRemovesDiacriticsAndPunctuation` — verifies search query normalization.
- `searchResultsProperlyTracksEmptyAndGroupedCounts` — verifies `SearchResults` grouping.
- `searchScopeEnumValuesAreExhaustive` — verifies `SearchScope` enum values (`Global`, `Live`, `Movies`, `Series`).
- `liveTvSearchSearchesEntireCatalogRegardlessOfSelectedCategory` — verifies Live TV search queries across all channels regardless of selected category.
- `moviesSearchSearchesEntireCatalogRegardlessOfSelectedCategory` — verifies Movies search queries across all movies regardless of selected category.
- `seriesSearchSearchesEntireCatalogRegardlessOfSelectedCategory` — verifies Series search queries across all series regardless of selected category.
- `globalSearchGroupsResultsByLiveMoviesAndSeries` — verifies Home Global Search renders grouped `LIVE TV`, `MOVIES`, `SERIES` sections and routes correctly.
