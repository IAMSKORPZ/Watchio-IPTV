# Watchio Agent Handoff

> **READ THIS FILE COMPLETELY BEFORE MAKING ANY CHANGE.**

This `AGENT_HANDOFF.md` is the authoritative current handoff for the active rescue/dev worktree.

The older file:

```text
C:\Users\mrsko\Documents\GitHub\Watchio-IPTV\native-android\docs\AI_HANDOFF.md
```

is historical/stale for current development and **MUST NOT** override this file.

---

## Current Status At A Glance

| Area | Status | Current fact |
|---|---|---|
| Branch / Base | PASS | `codex/dev-rescue-bridge` at `e44923f5caf77528b3d0b287f3ba6f689addd288`; `origin/dev` matches |
| Live TV NOW, NEXT & LATER | READY FOR REVIEW (UNCOMMITTED) | Programme info moved from channel rows to right-side EpgPanel. ChannelRow is compact (logo + number + name + star). EpgPanel shows pink NOW label, title, time range, progress bar, NEXT, LATER on channel highlight. Validated on S22. Uncommitted per user instruction. |
| DNS Login Removal | COMPLETE / PUSHED | Committed (`e44923f`) and pushed to `origin/dev` |
| Settings Gear Refinement | COMPLETE / PUSHED | Committed (`fa7c77b`) and pushed to `origin/dev` |
| Icon Redesign & Refinements | COMPLETE / PUSHED | Committed (`3789c5f`) and pushed to `origin/dev` |
| TV Guide / Player / EPG Categories | COMPLETE / PUSHED | Committed (`8dd6ff3`) and pushed to `origin/dev`; accepted by user |
| Tracked working tree | MODIFIED (UNCOMMITTED) | Feature implementation uncommitted for user review per explicit instruction |
| JVM tests | PASS | All unit tests pass across debug, local, release, uitest variants (`.\gradlew.bat test`) |
| Lint / build gates | PASS | `lintDebug`, `compileDebugKotlin`, `assembleDebug` pass cleanly |
| Android TV / BRAVIA | DEFERRED / UNTOUCHED | TV unavailable; `192.168.1.49:5555` untouched |
| Rescue bridge manifest | PROTECTED | SHA-256 `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` verified unchanged |
| Public stable | UNCHANGED | `v0.1.4` (`a0e5bafce943eebecbaeb03e0122ba7c2e0c62e5`); `origin/main` untouched (`0364d24`) |
| Next update | PENDING REVIEW | Live TV NOW, NEXT & LATER ready for user physical review |

---

## Repository / Git State

| Item | Verified value |
|---|---|
| Worktree | `C:\Users\mrsko\.codex\watchio-dev-rescue-worktree` |
| Branch | `codex/dev-rescue-bridge` |
| `HEAD` | `e44923f5caf77528b3d0b287f3ba6f689addd288` |
| `origin/dev` | `e44923f5caf77528b3d0b287f3ba6f689addd288` |
| `origin/main` | `0364d249ee513f0e814ca8163707e8c2ba47210c` |
| Latest commit subject | `Remove DNS Login button from Xtream login screen` |
| Tracked working tree | Modified (uncommitted for review: `LiveTvModels.kt`, `EpgChannelMatcher.kt`, `LiveTvRepository.kt`, `LiveTvScreens.kt`, `LiveTvViewModel.kt`, `TvGuideRepository.kt`, `LiveTvBrowsingStateTest.kt`, `AGENT_HANDOFF.md`) |
| Untracked files | `EpgNowNextCalculator.kt`, `EpgNowNextCalculatorTest.kt`, intentional `s22-*`, `current-temp.xml`, `tvguide-*`, `watchio-*` evidence files |

Untracked `s22-*`, `tvguide-*`, `watchio-*` evidence files are intentional and **MUST NOT** be blindly staged, committed, cleaned, or deleted.

---

## Live TV NOW, NEXT & LATER — Completed Implementation (Uncommitted for Review)

### Architecture & Scope
1. **Core Domain & Models (`LiveTvModels.kt`):**
   - Extended `LiveTvNowNext` with timestamps and additional programme fields:
     - `nowStartEpochMs: Long?`, `nowEndEpochMs: Long?`
     - `nextStartEpochMs: Long?`, `nextEndEpochMs: Long?`
     - `laterTitle: String?`, `laterStartEpochMs: Long?`, `laterEndEpochMs: Long?`
     - Helper properties: `hasNow`, `hasNext`, `hasLater`, `hasAny`.

2. **EPG Matching & Deduplication (`EpgChannelMatcher.kt`, `TvGuideRepository.kt`):**
   - Extracted `EpgMatchIndex` into `com.iamskorpz.watchioiptv.data.epg.EpgMatchIndex` as a shared public class.
   - Refactored `TvGuideRepository` to use the shared index, avoiding duplicate matching logic.
   - Matches by exact EPG channel ID, sanitized name, alphanumeric name, and fuzzy matching while enforcing strict provider isolation.

3. **Pure Calculator (`EpgNowNextCalculator.kt`):**
   - Pure function `calculate(programmes, nowEpochMs)` with zero Android/database dependencies.
   - Filters valid programmes (`endEpochMs > startEpochMs`).
   - Identifies active `NOW` programme (`start <= now < end`), choosing the latest start time if overlaps exist.
   - Identifies `NEXT` programme (earliest programme strictly starting after current programme start, or after `now` if no current programme).
   - Identifies `LATER` programme (earliest programme strictly starting after `NEXT` programme start).
   - Computes progress fraction: `((now - start) / (end - start)).coerceIn(0f, 1f)`.
   - Handles gaps, out-of-order lists, single-programme schedules, and missing programmes gracefully.

4. **Batch Data Fetching (`LiveTvRepository.kt`):**
   - Added `nowNextForChannels(providerId, channels, nowEpochMs): Map<String, LiveTvNowNext>`.
   - Queries EPG channels once, matches IDs, and batches queries in chunks of 500 EPG channel IDs.
   - Runs on injected `CoroutineDispatcher` (defaults to `Dispatchers.IO`), allowing mock/test dispatchers.
   - `nowNext(channel, nowEpochMs)` reuses `nowNextForChannels` for consistency.

5. **State Management & Periodic Ticker (`LiveTvViewModel.kt`):**
   - Added `channelProgrammes: Map<String, LiveTvNowNext> = emptyMap()` to `LiveTvUiState`.
   - Implemented `loadChannelProgrammes(providerId, channels)` triggered on category selection, search filtering, EPG refresh, and initial load.
   - `startEpgTicker`: Refreshes `nowNext` and visible channel batch every 60 seconds.
   - Lifecycle safe: `channelProgrammesJob` cancelled on `leaveLiveTv()` and `pauseForBackground()`.

6. **UI Rendering & TV Accessibility (`LiveTvScreens.kt`):**
   - Passed `channelProgrammes` map and 1-based channel numbering to `ChannelRow`.
   - Redesigned `ChannelRow`:
     - Channel badge with logo, fallback number, and channel name.
     - **NOW** section: Bold title, start-end time (`HH:mm - HH:mm`), thin primary progress bar.
     - **NEXT** section: `NEXT` badge, formatted start time and title (`HH:mm • Title`).
     - **LATER** section: `LATER` badge, formatted start time and title (`HH:mm • Title`) (omitted if not available).
     - **Fallback**: "No programme information" displayed when no EPG match or programme data exists.
     - Accessibility & D-pad: The entire `ChannelRow` card remains the sole clickable/focusable item; inner text elements do not steal focus.
   - Updated `EpgPanel` and `ChannelOptionsDialog` to show formatted times and LATER programme.

### Validation Results
- **Unit Tests:**
  - `EpgNowNextCalculatorTest.kt`: 7 unit tests verifying normal sequence, end boundary, gaps, overlaps, missing programmes, later detection, and progress calculation. All passed.
  - `LiveTvBrowsingStateTest.kt`: Updated `FakeLiveTvRepo` to override `nowNextForChannels`. All passed.
  - `.\gradlew.bat test`: All 1187 unit tests passed across debug, local, release, uitest variants.
- **Lint:**
  - `.\gradlew.bat lintDebug`: 0 errors. Passed.
- **Compilation / Assembly:**
  - `.\gradlew.bat assembleDebug`: Passed.
- **Device Verification (Samsung Galaxy S22):**
  - Installed debug APK via ADB (`adb install -r app-debug.apk`).
  - Navigated to Live TV: Verified NOW with progress bar, NEXT with start time, LATER with start time, fallback on channels without EPG.
  - Switched categories: Verified programmes updated correctly.
  - Selected channel: Verified playback tuned smoothly.
  - Saved screenshots: `s22-live-now-next-channels.png`, `s22-live-now-next-category-changed.png`, `s22-live-now-next-playing.png`.
- **Rescue Manifest Protection:**
  - `native-android/update/update.json` SHA-256 `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` verified untouched.
- **Git State:**
  - Branch: `codex/dev-rescue-bridge`
  - All changes uncommitted in working tree for user physical review (no commits or push made).

---

## Theme Builder Phase 2 — Completed Implementation

Theme Builder Phase 2 is **COMPLETE / ACCEPTED / COMMITTED / PUSHED TO origin/dev**:

- **Commit SHA:** `2384698ea94ddea751a9ac896e28c7c9345822e2`
- **Commit Subject:** `Complete application-wide theme integration`
- **Scope:** 20 files changed, 633 insertions(+), 216 deletions(-)

### Accepted Architecture & Scope:

- **App-wide semantic theme integration:** All screens use semantic tokens (Home, Live, Movies, Series, Search, Sports, Settings, Notifications, Updates, TV Guide).
- **Shared/global components:** Page headers, cards, buttons, dialogs, modals, and lists consume active `WatchioTheme`.
- **Global background:** Normal decorative gradient/background lives at root `WatchioAppBackground`. Screen-level layers omit duplicate opaque backgrounds unless semantically required.
- **Transparent page headers:** `WatchioPageHeader` renders over global/artwork context without duplicate background bars.
- **Artwork-readable typography:** Detail headers for Movies and Series use dedicated `artworkTextPrimary` and `artworkTextSecondary` for high readability over posters/backdrops.
- **Independent Card / Panel / Control outline semantic roles:**
  - `CARD`: genuine media and library cards only (`cards.outlineWidthDp`).
  - `PANEL`: container surfaces only (`surfaces.outlineWidthDp`).
  - `CONTROL`: interactive buttons, search fields, chips, action items (`controls.outlineWidthDp`).
  - `FOCUS`: distinct focused state outline (`focus.outlineWidthDp`), independent of normal outlines.
- **Zero-width outline omission:** When an outline width is `0.dp`, `Modifier.border` is omitted entirely to prevent hairline borders.
- **Watchio Default invariants:** Normal Card, Panel, and Control outlines are `0.dp`. Focus styling remains distinct. Custom themes can independently customize outlines.
- **Player OSD:** Player controls and overlays use semantic player tokens; actual video pixels remain unthemed.
- **Blur rendering:** Intentionally deferred; not enabled.

---

## Validation Summary

### 1. Automated Gates (All Passed)

| Gate | Command | Result |
|---|---|---|
| JVM Tests | `.\gradlew.bat test` | PASS (1187 / 1187) |
| Lint | `.\gradlew.bat lintDebug` | PASS |
| Assemble Debug | `.\gradlew.bat assembleDebug` | PASS |
| Assemble Local | `.\gradlew.bat assembleLocal` | PASS |
| Assemble Uitest | `.\gradlew.bat assembleUitest` | PASS |
| Assemble Uitest Test | `.\gradlew.bat assembleUitestAndroidTest` | PASS |
| S22 Full Instrumentation | `.\gradlew.bat connectedUitestAndroidTest` | PASS (247 / 247 passed, 0 failed, 0 errors, 11m 53s) |
| Git whitespace/conflict check | `git diff --check` | PASS (clean exit 0) |

*Note on S22 Instrumentation History:* Earlier runs failed due to environmental instability (device screen timeout/lock, portrait rotation lock, and third-party background memory pressure). Once stabilized (screen stay-on, landscape locked, keyguard dismissed, heavy social apps force-stopped), the unchanged 247-test suite completed 100% clean on device `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp`. No test or production code modifications were needed.

### 2. S22 Physical Theme Builder Acceptance (All Passed)

- **Card-only:** Movie cards outlined (~2dp), Series cards outlined, library cards outlined, header controls unaffected, panels unaffected. PASS.
- **Panel-only:** Intended panels outlined (~2dp), cards unaffected (0dp), controls unaffected (0dp). PASS.
- **Control-only:** Intended controls outlined (~2dp), header controls outlined, cards unaffected (0dp), panels unaffected (0dp). PASS.
- **Focus:** Focus treatment remains clearly visible and distinct from normal outlines. PASS.
- **Light Theme Regression:** Series artwork header readable, Movie artwork header readable, Back icon visible with correct button contrast. PASS.
- **Watchio Default Restoration:** Watchio Default active. Normal card, panel, and control outlines absent. Zero hairline borders. Focus styling visible. PASS.

### 3. Android TV / BRAVIA Physical Acceptance

- **Status:** **DEFERRED / NOT COMPLETED**
- **Device:** `192.168.1.49:5555`
- **Rule:** Do NOT claim Android TV Theme Builder acceptance passed. TV is unavailable. Do NOT connect or query until user explicitly authorizes.

---

## Rescue Bridge Protection — Critical

> **RESCUE BRIDGE PROTECTION: `native-android/update/update.json` MUST NOT CHANGE.**

`native-android/update/update.json` intentionally rescues old public v0.1.1/v0.1.2 clients that mistakenly query the DEV updater endpoint. It advertises public stable `v0.1.4` while retaining `channel = dev`.

| Item | Value |
|---|---|
| Required SHA-256 | `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` |
| Genuine DEV backup | `C:\Users\mrsko\.codex\watchio-security-backups\dev-manifest-rescue-20260917\update.json` |

Do not restore, regenerate, reformat, replace, change channel, or merge-overwrite the rescue manifest.

---

## Public Stable Release

| Item | Value |
|---|---|
| Release | `v0.1.4` |
| Package | `com.iamskorpz.watchioiptv` |
| Version name | `0.1.4` |
| Version code | `17` |
| APK SHA-256 | `6354657B34B9B069C53AA44DD6C93EFEFEA2014C583FBCE423E7778118B58C05` |
| Signer SHA-256 | `8A:76:E2:0B:7C:B2:E1:68:12:F5:05:12:75:A3:D1:12:FC:FB:AB:7C:24:24:C5:E8:97:F5:58:87:6B:CC:6F:F0` |
| Release commit | `a0e5bafce943eebecbaeb03e0122ba7c2e0c62e5` |

Do not publish, rebuild, or modify the public stable release during active development.

---

## Package Identities & Signing

| Variant | Package | Purpose |
|---|---|---|
| PUBLIC | `com.iamskorpz.watchioiptv` | Production release |
| DEV | `com.iamskorpz.watchioiptv.debug` | Manual dev APK on real devices |
| LOCAL | `com.iamskorpz.watchioiptv.local` | Local build variant |
| UITEST | `com.iamskorpz.watchioiptv.uitest` | Isolated automated UI testing |
| UITEST TEST | `com.iamskorpz.watchioiptv.uitest.test` | Test runner instrumentation |

*Notice:* Do NOT use `com.watchioiptv.nativeapp.debug` from the stale `AI_HANDOFF.md`. Never regenerate keystores or allow signer crossover.

---

## Core Invariants

1. **Room Schema:** Remains v6. No entity, index, schema, migration, or DB version changes without explicit need and authorization.
2. **Media3 Architecture:** Media3 / ExoPlayer is the only playback engine. Preserve single app-scoped `WatchioPlayerManager` architecture.
3. **Provider Isolation:** Selected-provider state, active-server state, server switching, Xtream login, and managed endpoint behavior must remain intact.
4. **Quick Login Security:** ECDH, HKDF, AES-GCM, secure storage, same-WiFi validation, expiry, and two-phase sync must be preserved. Never put passwords in QR codes.
5. **Credentials & Secrets:** Never commit API secrets or tokens. Keep credentials out of logs, docs, tests, screenshots, and reports.
6. **Device Safety:** Never clear app data or destructively uninstall without explicit user approval.

---

## NEXT UPDATE — SPORTS BROADCAST / WHERE TO WATCH

- **Status:** **PLANNED / NOT STARTED**
- **Rule:** **DO NOT START IMPLEMENTATION FROM THIS DOCUMENTATION TASK.** Implementation must be separately authorized.

### 1. Product Goal

For football fixtures displayed in Watchio:
1. Identify legitimate official broadcaster / TV-station information for the fixture (e.g. UK: Sky Sports Main Event, TNT Sports 1; US: NBC, Peacock).
2. Match those broadcaster names against channels already present in the active user's own IPTV/provider catalogue.
3. Allow the user to "Watch Now" or tune directly to their provider channel when a match is found.

**Strict Boundaries:**
- Watchio **MUST NOT** supply or bundle streams.
- Watchio **MUST NOT** scrape pirate stream sites or illegal link aggregators.
- Watchio **MUST NOT** automatically inject broadcaster streams.
- Watchio remains strictly a player/client that plays only user-supplied provider/local streams.

### 2. High-Level Data Flow

```text
Fixture (from football-data.org)
    │
    ▼
Broadcaster Candidates / "Where to Watch" (from official broadcaster API)
    │
    ▼
Broadcaster Name Normalization (aliases, region tags, HD/UHD stripping)
    │
    ▼
Provider Channel Matcher (match against active user's provider channels)
    │
    ▼
EPG Match Verifier (optional confirmation against provider EPG schedule)
    │
    ▼
Confidence Assessment (Confirmed / Likely / Possible / No Match)
    │
    ▼
UI Action: "Watch on [User's Channel Name]" (when confidence sufficient)
```

### 3. Proposed Component Architecture

```text
SportsRepository
    └── Provides fixtures, scores, league standings (existing football-data.org)

BroadcastRepository
    └── Fetches legitimate broadcaster candidates per fixture / region (new provider-agnostic layer)

ProviderChannelMatcher
    └── Matches normalized broadcaster names against the user's active provider channel catalogue

EpgMatchVerifier
    └── Verifies fixture / teams / timing against provider channel's EPG guide data for confidence scoring
```

*Architectural Invariant:* Do not tightly couple broadcaster data to `football-data.org`. Keep `BroadcastRepository` provider-agnostic so broadcaster data providers can be swapped or multi-sourced.

### 4. Existing Sports Source

Watchio currently uses `football-data.org` for fixtures and scores. Do **not** replace it merely to add broadcaster data. Broadcaster lookup should be an orthogonal layer attached to fixture identity.

### 5. Broadcast Provider Research Targets (For Next Phase)

- **Primary Prototype Target:** **Sportmonks Football API — TV Stations**
  - Website: <https://www.sportmonks.com/football-api/>
  - Documentation: <https://docs.sportmonks.com/v3/endpoints-and-entities/endpoints/tv-stations>
- **Secondary Comparison Target:** **SoccersAPI — Where to Watch**
  - Documentation: <https://docs.soccersapi.com/guides/recipes/>
- **Other Considerations:** SportLens, API-Football / RapidAPI TV station endpoints.

*Requirements to verify during research:* Current pricing, free-tier limits, rate limits, UK/global coverage depth, licensing, and whether fixture-to-broadcaster mapping is included or requires higher tiers.

### 6. WhatsApp Channel Reference

User provided public WhatsApp channel: <https://whatsapp.com/channel/0029VaFM0nnKAwEqi972yU0n>
- **Purpose:** Informational product reference only (shows what daily broadcaster mappings look like).
- **CRITICAL RULE:** **DO NOT** scrape WhatsApp. Do **NOT** build browser automation, WhatsApp API integrations, or fragile web scrapers. Production Watchio must use structured, licensed APIs.

### 7. Channel Matching Requirements

Matching broadcaster names to IPTV provider channels must handle diverse naming conventions:
- **Normalization:** Case folding, whitespace trimming, punctuation stripping (`&` vs `and`), separator normalization (`|`, `-`, `:`).
- **Resolution/Quality Suffixes:** Strip or normalize `HD`, `FHD`, `UHD`, `4K`, `HEVC`, `50FPS`, `RAW`.
- **Country/Region Prefixes:** Handle `UK:`, `UK |`, `US:`, `US |`, `EN -`, etc., while preserving regional intent.
- **Broadcaster Aliases:** E.g., `Sky Sports Main Event` == `Sky Main Event` == `SS Main Event`. `TNT Sports 1` == `TNT 1` == former `BT Sport 1`.
- **False-Positive Prevention:** Avoid over-normalizing (e.g. `Sky Sports Premier League` must not match `Sky Sports Football` or `Sky Sports Main Event`).

### 8. Confidence Model

Evidence signals:
1. **API Signal:** Official API lists Broadcaster X for fixture in country Y.
2. **Channel Name Signal:** User's provider contains channel matching Broadcaster X.
3. **EPG Signal (Optional):** Provider channel's EPG at fixture kickoff lists matching teams/event.

*Confidence Levels:*
- **Confirmed:** Broadcaster match + EPG schedule match.
- **Likely:** Broadcaster name match with high string-similarity / known alias.
- **Possible:** Partial/generic match (e.g. main channel without region specificity).
- **No Match:** Broadcaster carries fixture, but user has no corresponding channel.

*Safety Rule:* Never auto-tune or auto-play ambiguous or low-confidence matches.

### 9. Sports UI Direction

- In Fixture Details / Match View:
  - Display "Where to Watch" section grouped by country/region.
  - Highlight user's matching channels: *"Available on your provider: [Channel Name]"*.
  - Provide "Watch Channel" action button (only when channel exists in user's active provider).
  - If no channel match: display broadcaster info with *"No matching channel in your playlist"*.

### 10. Security & Performance Constraints

- **Security:** Do **not** hardcode private paid API keys in the APK. If a paid API is selected, evaluate proxy/backend architecture or secure secret injection. Do not log provider credentials.
- **Performance:**
  - Avoid N+1 network requests (batch fixture broadcaster lookups where supported).
  - Cache broadcaster mappings with daily/fixture TTL.
  - Failures must be nonfatal (if broadcaster API is down, fixtures still display normally).
  - Heavy channel matching and EPG scans must execute off the main thread with indexing/caching.

### 11. Future Sports Test Plan (To Be Implemented Later)

- Unit tests for `BroadcastRepository` parser and error handling.
- Unit tests for `ProviderChannelMatcher` (exact match, aliases, HD/FHD stripping, prefix stripping, collision prevention, region handling).
- Unit tests for `EpgMatchVerifier` (matching program titles against teams, kickoff time window tolerance).
- Fallback tests (broadcaster API down, empty provider playlist, provider switching).
- Compose tests for "Where to Watch" fixture UI component.

### 12. Required Next Action

The **FIRST** task of the Sports update must be:
**RESEARCH + READ-ONLY ARCHITECTURE AUDIT**

1. Audit existing `SportsRepository`, `SportsUiState`, and `SportsScreen.kt`.
2. Audit `football-data.org` integration and fixture model structures.
3. Audit provider channel models (`LiveTvChannel`) and database queries.
4. Audit EPG data structures and retrieval mechanisms (`LiveTvNowNext`, `TvGuideRepository`).
5. Research Sportmonks and SoccersAPI TV stations endpoints (pricing, rate limits, sample payloads).
6. Create detailed architectural design document before writing code.

---

## Build & Test Environment

Set environment before running Gradle:

```powershell
$env:JAVA_HOME='C:\Users\mrsko\.jdks\jbr-21.0.11'
$env:ANDROID_HOME='C:\Users\mrsko\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
```

Target S22 device:

```powershell
$env:ANDROID_SERIAL='adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp'
```

Gates:

```powershell
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleLocal
.\gradlew.bat assembleUitest
.\gradlew.bat assembleUitestAndroidTest
.\gradlew.bat connectedUitestAndroidTest
```

*Never* use `connectedDebugAndroidTest` for automation.

---

## TV Guide — Phase TVG-1: Foundation + Core Visual Redesign

**Status: COMPLETE — not yet committed to `origin/dev`**

Changes implemented:

- **Header:** TV Guide now uses `WatchioPageHeader` — identical header implementation to Movies, Series, Live TV.
- **Date/refresh strip removed:** The NOW / TODAY / date row and REFRESH button are no longer rendered. Internal 7-day date logic and `TvGuideTimeline.nowLineOffsetDp()` preserved.
- **NOW line preserved:** Pink vertical `NowLine()` composable remains visible in the programme timeline. `timelineScrollRequest` defaults to `TimelineScrollRequest.Now` so the NOW line is visible and the timeline auto-scrolls to current time on mount.
- **Category picker:** Horizontal scrollable chip row below header. Tapping opens `TvGuideCategoryPickerDialog`.
- **Hero section:** Compact hero (reduced height). Removed right-side channel logo/artwork panel. Mini-player occupies left 60% of hero.
- **WATCH LIVE button removed:** No longer rendered in hero description row. Tap mini-player to enter fullscreen directly.
- **Grid density:** Row height reduced to show 4–5 channel rows on S22 landscape. Programme cell text scaled accordingly.
- **Semantic theme tokens:** All TV Guide composables consume `WatchioTheme` colour/typography tokens.

---

## TV Guide — Phase TVG-2: Player Fix Pass

**Status: COMPLETE — not yet committed to `origin/dev`**

### Files Changed

| File | Change |
|---|---|
| `ui/components/WatchioPlayerSurface.kt` | New file: lifecycle-aware mini-player surface |
| `feature/player/WatchioFullscreenPlayerScreen.kt` | Controls fix: tap, D-pad, auto-hide, focus blocking |
| `core/player/Media3WatchioPlayerManager.kt` | `attachSurface`: blocks focus on `PlayerView` |
| `feature/tvguide/TvGuideScreen.kt` | WATCH LIVE removed; `timelineScrollRequest` defaults to Now; `onWatchLive` fallback |

### Root Causes Fixed

1. **Fullscreen small-rectangle bug:** `TvGuideScreen` stays on NavHost backstack; `WatchioPlayerSurface.update` lambda was re-attaching `PlayerView` to mini-player container on every recompose, stealing it from fullscreen. Fix: lifecycle-aware `isResumed` gate — when not resumed, AndroidView not composed, fallback shown instead.
2. **Controls not reappearing:** Native `FrameLayout`/`PlayerView` were focusable, stealing focus when Compose controls were removed; `onPreviewKeyEvent` never received events; touch consumed by AndroidView blocked Compose `clickable`. Fix: `isFocusable=false`/`descendantFocusability=FOCUS_BLOCK_DESCENDANTS` on all containers + transparent Compose `pointerInput` overlay + consume both `KeyDown`+`KeyUp` in hidden state.

### WatchioPlayerSurface.kt (new)

- Lifecycle observer via `LocalLifecycleOwner`: on `ON_PAUSE`/`ON_STOP`/dispose calls `playerManager.detachSurface(currentContainer)`.
- When `!isResumed`: AndroidView not composed; fallback `Box(color=Black)` shown.
- AndroidView `FrameLayout`: `isFocusable=false`, `isFocusableInTouchMode=false`, `descendantFocusability=FOCUS_BLOCK_DESCENDANTS`.
- Zero-width border: uses `.then(if (focused) Modifier.border(...) else Modifier)` pattern — no hairline on unfocused.

### WatchioFullscreenPlayerScreen.kt

- Added `restartAutoHideTimer()` / `showControls()` / `hideControls()` helpers (via `lastInteractionEpochMs` pattern driving `LaunchedEffect`).
- `onPreviewKeyEvent` hidden-state handling:
  - UP/DOWN (Live): `triggerChannelSwitch()`, consumes KeyDown+KeyUp.
  - LEFT/RIGHT (seekable): `triggerSeek()`, consumes KeyDown+KeyUp.
  - CENTER/Enter/NumPadEnter/Space: `showControls()` on KeyDown, consumes both.
  - Other: `false` (pass through).
- Transparent tap `Box` with `pointerInput { detectTapGestures { onTap { ... } } }` placed after AndroidView — tap toggles controls.
- Removed `.clickable {}` from root `Box`.
- AndroidView `FrameLayout`: `isFocusable=false`, `isFocusableInTouchMode=false`, `descendantFocusability=FOCUS_BLOCK_DESCENDANTS`.

### Automated Gate Results (TVG-2)

| Gate | Result |
|---|---|
| `.\gradlew.bat test` | PASS |
| `.\gradlew.bat lintDebug` | PASS |
| `.\gradlew.bat assembleDebug assembleLocal assembleUitest assembleUitestAndroidTest` | PASS |
| `.\gradlew.bat connectedUitestAndroidTest` | PASS — 260 / 260, 0 failed |
| `adb install -r app-debug.apk` | SUCCESS |
| SHA-256 of `update.json` | `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` ✓ |

### S22 Physical Evidence (TVG-2)

| File | Content |
|---|---|
| `s22-live-tvguide.png` | TV Guide loaded with 4+ channel rows, mini-player present, no WATCH LIVE button |
| `s22-fullscreen-active.png` | Fullscreen player fills `[0,0][2340,1080]`, controls visible (Channels/Play/Audio/Subtitles/Aspect/Settings), channel info overlay |
| `s22-fullscreen-active.xml` | UI dump confirms `exo_content_frame` + `SurfaceView` at full device bounds, no small rectangle |
| `s22-controls-hidden.png` | After tap: controls hidden — no control text elements in UI dump |
| `s22-controls-shown.png` | After second tap: controls reappear (Play, Favourite, content metadata visible) |
| `s22-back-from-fullscreen.png` | Back navigation returns to app normally |

### S22 Physical Verification Summary (TVG-2)

1. **Fullscreen fills screen:** `SurfaceView` bounds `[0,0][2340,1080]` — no small rectangle. ✓
2. **Controls on entry:** Channels, Play, Previous, Next, Audio, Subtitles, Aspect, Settings all present. ✓
3. **Tap hides controls:** Single tap on video area hides all control overlays. ✓
4. **Tap shows controls:** Second tap restores controls and content metadata. ✓
5. **Back navigation:** Returns to previous screen in NavHost. ✓
6. **No WATCH LIVE button:** Hero description row in TV Guide shows no WATCH LIVE action. ✓
7. **Mini-player to fullscreen:** Tapping mini-player transitions to fullscreen player. ✓

---

## Final Handoff Summary

```text
THEME BUILDER PHASE 2:              COMPLETE / COMMITTED / PUSHED (2384698)
TV GUIDE / PLAYER / EPG CATEGORIES: COMPLETE / ACCEPTED / COMMITTED / PUSHED (8dd6ff3)
DEV HEAD:                          8dd6ff313ef3002928e92a94833ab80f09268d2b
origin/dev:                        8dd6ff313ef3002928e92a94833ab80f09268d2b
TRACKED WORKING TREE:              CLEAN (handoff records post-push verification)
S22 AUTOMATED SUITE (FULL):        PASS (262 / 262, 0 failed, 11m 47s)
S22 PHYSICAL ACCEPTANCE:           PASS (User accepted all TV Guide, Player, EPG flows)
ANDROID TV PHYSICAL THEME BUILDER: DEFERRED / UNTOUCHED
RESCUE BRIDGE:                     PROTECTED / UNCHANGED (AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB)
PUBLIC STABLE:                     UNCHANGED v0.1.4
NEXT ACTION:                       START SPORTS AUDIT (read-only architecture audit)
NEXT UPDATE:                       SPORTS BROADCAST / WHERE TO WATCH
SPORTS IMPLEMENTATION:             NOT STARTED
SAFE TO START SPORTS AUDIT:        YES (clean dev base)
SAFE TO START SPORTS CODE:         NO (audit and design review required first)
```

---

## TV Guide — Video Surface + Initial NOW Regression Pass (2026-09-27)

**Status: IMPLEMENTED AND TARGET-VALIDATED — not committed or pushed**

### Final fixes

- `Media3WatchioPlayerManager.attachSurface()` now replaces retained preview sizing with `FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)` on every attachment, including same-parent updates, and requests/invalidate layout on both container and `PlayerView`.
- The first attempted generic `ViewGroup.LayoutParams` was rejected during S22 physical verification with `ClassCastException` in `FrameLayout.measureChildWithMargins`; it was corrected before final validation.
- TV Guide initial horizontal positioning is deferred until real guide state finishes loading, then scrolls to NOW once. The placeholder window can no longer consume the initial NOW request.
- Loaded selection now retains a programme only when it exists on the selected channel; otherwise it selects the live programme, then the first real programme.

### Validation

| Check | Result |
|---|---|
| JVM tests | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assembleLocal` | PASS |
| `assembleUitest` | PASS |
| `assembleUitestAndroidTest` | PASS |
| `git diff --check` | PASS |
| Targeted TV Guide S22 instrumentation | PASS — 14 / 14 |
| Full isolated S22 instrumentation | 258 / 260 PASS; two unrelated `AppearanceComposeTest` preview-visibility assertions failed |
| Rescue manifest SHA-256 | `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` — unchanged |

### S22 physical result

- Exact device: `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp` (`SM-S901B`).
- Debug APK installed with replacement only; no uninstall or data clear.
- Guide initial viewport: PASS — NOW line visible near the current time immediately after load.
- Preview activation: PASS — first channel activation enters the mini-player without the former sizing crash.
- Moving-video surface fill: NOT VERIFIED — sampled provider channels remained buffering/reconnecting or returned logo fallback.
- Fullscreen moving-video fill and controls cycle: NOT VERIFIED in this pass because sampled provider content was unavailable.
- Evidence: `s22-tv-guide-now-video-surface.png`, `s22-tv-guide-preview-full-surface.png`, `s22-tv-guide-preview-second-channel.png`.
- BRAVIA: NOT TOUCHED.

## Shared Video Surface Root-Cause Fix Pass (2026-09-27)

**Status: IMPLEMENTED AND VALIDATED - not committed or pushed**

### Root cause and fix

- The retained Media3 `PlayerView` used the default `SurfaceView`; reparenting it between Compose preview and fullscreen containers could retain stale geometry.
- Preview surfaces inherited fullscreen Zoom/Fill, cropping mini previews.
- A background preview could reclaim the singleton player after fullscreen attached it.
- The shared view now uses a `TextureView`, match-parent dimensions, and FIT base resize mode.
- Preview always uses FIT. Fullscreen uses the selected scaling mode and owns the shared surface until detached.
- Reattachment resets match-parent `FrameLayout.LayoutParams`, requests layout, and invalidates.

### Files changed by this pass

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/core/player/WatchioPlayerManager.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/core/player/Media3WatchioPlayerManager.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/components/WatchioPlayerSurface.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/live/LiveTvScreens.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/player/WatchioFullscreenPlayerScreen.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/tvguide/TvGuideScreen.kt`
- `native-android/app/src/main/res/layout/watchio_player_view.xml`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/PlayerSurfaceInstrumentedTest.kt`
- `AGENT_HANDOFF.md`

### Validation

| Check | Result |
|---|---|
| JVM tests | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assembleLocal` | PASS |
| `assembleUitest` | PASS |
| `assembleUitestAndroidTest` | PASS |
| `git diff --check` | PASS |
| Targeted S22 instrumentation | PASS - 15 / 15 |
| Rescue manifest SHA-256 | `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` - unchanged |

The combined Gradle run emitted a recoverable Kotlin incremental-cache daemon warning, fell back automatically, and completed successfully with exit code 0.

### S22 physical verification

- Device: `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp` (`SM-S901B`).
- Debug app installed with replacement only; no uninstall or data clear.
- Live TV mini preview: PASS - real moving Sky Cinema video, correctly fitted and not cropped.
- Live TV fullscreen: PASS - real moving video fills the intended area; no top-left strip.
- TV Guide fullscreen: PASS - real moving Sky Cinema video through the Guide route, correctly framed.
- TV Guide mini-preview moving video: PARTIAL - sampled Guide channel buffered; no screenshot proves moving pixels inside the small Guide box.
- Movie playback: NOT VERIFIED - sampled title remained on centered buffering/fallback.
- Series playback: NOT VERIFIED - no reliable moving sample completed.
- BRAVIA: NOT TOUCHED.

### Evidence added (untracked)

- `s22-player-fix-live-screen.png`
- `s22-player-fix-live-fullscreen-corrected.png`
- `s22-player-fix-guide-preview-corrected.png`
- `s22-player-fix-guide-fullscreen-corrected.png`
- `s22-player-fix-movie-playback-corrected.png`

### Safety

- Commit: NO.
- Push: NO.
- BRAVIA touched: NO.
- App data cleared: NO.
- App uninstalled: NO.
- Existing dirty work and evidence preserved.

## EPG Categories Entry Screen Pass (2026-09-27)

**Status: IMPLEMENTED AND VALIDATED - not committed or pushed**

### Flow and behavior

- Home `TV GUIDE` now opens `EPG CATEGORIES`.
- Selection opens existing TV Guide with optional stable `categoryId` route argument.
- `TvGuideViewModel` applies initial category on entry; in-guide controls remain free afterward.
- Natural back stack: Home, EPG Categories, TV Guide.
- Existing direct Guide route remains supported through optional argument default.

### Data and UI

- Active-provider `LiveTvRepository.categories()` and `channels()` supply categories and real counts; no programme query.
- Shows `ALL CHANNELS`, `FAVOURITES`, then provider categories in repository order. Entry screen excludes `HISTORY`.
- Selected-provider Flow reload prevents cross-provider/stale data.
- Shared `WatchioPageHeader`, semantic theme colors, existing `WatchioCard` focus styling.
- Two columns at width >= 700dp; one column below. Initial focus requests ALL CHANNELS. Touch and OK share action.

### Files created

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/tvguide/EpgCategoriesScreen.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/EpgCategoriesComposeTest.kt`

### Files modified

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/tvguide/TvGuideViewModel.kt`
- `AGENT_HANDOFF.md` in place

### Validation

| Check | Result |
|---|---|
| Full JVM tests | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assembleLocal` | PASS |
| `assembleUitest` | PASS |
| `assembleUitestAndroidTest` | PASS |
| Targeted EPG Categories + TV Guide instrumentation | PASS - 15 / 15 |
| `git diff --check` | PASS |
| Rescue manifest SHA-256 | `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` - unchanged |

### S22 physical verification

- Device: `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp` (`SM-S901B`) only.
- Replacement install only; no uninstall/data clear.
- Home to EPG Categories, header, two-column layout, real counts, special/provider entries: PASS.
- Initial visible focus ALL CHANNELS: PASS.
- D-pad Right/Down + OK opened `UK | QUALIFIERS`: PASS.
- Existing Guide opened filtered and at NOW: PASS.
- Back returned to EPG Categories: PASS.
- `UK | MOVIES` also opened correctly; internal selector remained usable: PASS.
- Favourites displayed actual count `0`; separate empty-favourites Guide selection not physically completed.
- Evidence: `s22-epg-categories.png`, `s22-epg-categories-back.png`, `s22-epg-category-guide-filtered.png`, `s22-epg-category-all-guide.png`, `s22-epg-category-favourites-guide.png`.
- BRAVIA: NOT TOUCHED.

### User-accepted playback state & final verification

User physically accepted current TV Guide preview/fullscreen, Live TV, Movies, Series, and player control recovery, EPG Categories navigation, and TV Guide density/now-line alignment.

Final pre-commit validation results:
- **Branch:** `codex/dev-rescue-bridge`
- **Base HEAD:** `2384698ea94ddea751a9ac896e28c7c9345822e2` (matches `origin/dev`)
- **JVM tests:** PASS (1187 / 1187)
- **Lint / build gates:** PASS (`lintDebug`, `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`, `git diff --check`)
- **S22 Full Connected UITEST:** PASS — 262 / 262 passed, 0 failed, 0 errors (11m 47s) on `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp`
- **Rescue manifest SHA-256:** `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` (verified unchanged)
- **BRAVIA:** Untouched (`192.168.1.49:5555`)
- **Public main:** Untouched (`0364d249ee513f0e814ca8163707e8c2ba47210c`)
- **Evidence files:** Retained untracked
- **Commit/Push authorization:** Explicitly authorized by user for completed TV Guide / Player / EPG Categories work

### Final Commit & Push Verification

- **Commit SHA:** `8dd6ff313ef3002928e92a94833ab80f09268d2b`
- **Commit Subject:** `Complete TV Guide, player and EPG categories improvements`
- **Files Committed:** 23 files (16 tracked modifications, 6 new source/test/resource files, `AGENT_HANDOFF.md`)
- **Push Target:** `origin/dev`
- **Push Result:** SUCCESS (`2384698..8dd6ff3 codex/dev-rescue-bridge -> dev`)
- **HEAD SHA:** `8dd6ff313ef3002928e92a94833ab80f09268d2b`
- **origin/dev SHA:** `8dd6ff313ef3002928e92a94833ab80f09268d2b` (matches HEAD)
- **origin/main SHA:** `0364d249ee513f0e814ca8163707e8c2ba47210c` (completely unchanged)
- **Working Tree:** Source tree clean. Untracked physical evidence preserved.
- **Public Releases/Tags:** None created.
- **Rescue Manifest SHA-256:** `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` (verified unchanged after push)

## 2026-09-27 — Watchio DNS Login + managed endpoint relocation (uncommitted)

### Baseline and scope

- Branch remains `codex/dev-rescue-bridge`; HEAD remains `8dd6ff313ef3002928e92a94833ab80f09268d2b`.
- Existing TV Guide/player/EPG Categories work was preserved.
- Managed endpoint source changed from `https://iamskorpz.github.io/Watchio_Website/img/watchio_endpoints.json` to `https://raw.githubusercontent.com/IAMSKORPZ/Watchio_Website/main/2711/WCIT/RTA/SREDIVORP/watchio_endpoints.json`.
- Live check on 2026-09-27: the requested new raw URL returned HTTP 404. It must be published at that exact path before fresh installs can load the catalogue.

### DNS Login architecture

- Added a Watchio-native `DNS LOGIN` option from the existing Xtream screen and a dedicated Username + Password screen with no URL field.
- Stage 1 resolver contract is strict Watchio JSON: POST `{ "username": "...", "app": "watchio", "platform": "android" }`; entered password is never represented in, or sent by, the resolver API.
- Accepted responses: version 1 `ok` with managed `endpointId` or `{id,url}`, `not_found`, and safe `error` handling.
- Managed IDs resolve against the downloaded Watchio endpoint catalogue. Disabled/unknown IDs are rejected. Direct URLs are normalized with the existing endpoint rules; blank, malformed, unsupported, credential-bearing, query-bearing, and fragment-bearing URLs are rejected, while valid ports are preserved.
- Stage 2 feeds the resolved endpoint into the existing `XtreamRepository` / `player_api.php` authentication and two-phase import. Provider creation and credential storage occur only after successful Xtream authentication. Managed provider IDs, provider isolation, active-provider selection, active-server state, and switch-server behavior remain on the existing architecture.
- Existing managed Xtream login, Quick Login, M3U URL, local M3U, and saved accounts remain unchanged.
- No XOLO/no1apps protocol, endpoint, obfuscation, keys, fields, or fallback was added.

### Backend status

- `DYNAMIC WATCHIO DNS RESOLVER BACKEND STILL REQUIRED.`
- No production resolver URL is configured (`WATCHIO_DNS_RESOLVER_URL` is intentionally empty), so DNS Login fails closed with `DNS Login is not available yet. Please use Xtream login.`
- Required backend: a real HTTPS POST service implementing the contract above. GitHub/raw GitHub cannot provide it. Do not configure a URL until that service exists and is tested.

### Validation

- Focused resolver/endpoint JVM tests: PASS.
- Full `test`: PASS.
- `lintDebug`: PASS, zero errors.
- `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`: PASS.
- `git diff --check`: PASS.
- One Kotlin incremental-cache registration warning occurred during the combined gate run; Gradle fell back to non-incremental compilation and the complete build finished successfully.
- S22 target: `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp` / `SM-S901B` only.
- Targeted `ProviderFormComposeTest`: first run found SIGN IN skipped the new DNS button; focus edge fixed. Clean rerun PASS: 5/5.
- Replacement-installed debug APK on S22 and launched it. Existing saved provider/session remained present, proving app data was preserved. No uninstall or data clear.
- Automated S22 UI verified username/password-only DNS form, password masking, no URL field, and D-pad traversal. Real successful DNS resolution was not testable because no backend exists. Manual credential entry was intentionally not performed, so keyboard-dismiss and live Xtream login remain physical-user acceptance items.
- BRAVIA untouched. No credentials captured in evidence or logs.
- Rescue manifest SHA-256 before/after: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.

### Modified source/test files in this pass

- `native-android/app/src/main/assets/watchio_endpoints.json`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/core/di/AppContainer.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/data/xtream/WatchioDnsResolver.kt` (new)
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/data/xtream/WatchioEndpointManager.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/data/xtream/XtreamRepository.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/provider/XtreamProviderViewModel.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/test/java/com/iamskorpz/watchioiptv/WatchioDnsResolverTest.kt` (new)
- `native-android/app/src/test/java/com/iamskorpz/watchioiptv/WatchioEndpointManagerTest.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/ProviderFormComposeTest.kt`
- `AGENT_HANDOFF.md` updated in place.

**COMMIT/PUSH REQUIRES EXPLICIT USER APPROVAL. No commit and no push were performed.**

### Commit authorization — 2026-09-27

- User explicitly authorized committing the validated DNS Login client and `/2711` remote configuration migration.
- Target branch: `origin/dev` from local `codex/dev-rescue-bridge`.
- `origin/main` must remain `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- Rescue manifest SHA-256 verified before staging: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- Untracked screenshots, XML, logs, and other evidence remain excluded and preserved.

## 2026-09-27 — `/2711` announcements + managed endpoint locations (uncommitted)

### Canonical production locations

- Announcements: `https://raw.githubusercontent.com/IAMSKORPZ/Watchio_Website/main/2711/JFO/YLT/announcements.json`
- Managed endpoints: `https://raw.githubusercontent.com/IAMSKORPZ/Watchio_Website/main/2711/WCIT/RTA/SREDIVORP/watchio_endpoints.json`
- Removed Android use of old announcements location: `https://raw.githubusercontent.com/IAMSKORPZ/Watchio-IPTV/main/announcements/announcements.json`.
- Old GitHub Pages endpoint location remains removed from Android sources.

### Compatibility and HTTP validation

- Both raw URLs returned HTTP 200 on 2026-09-27.
- Both responses parsed as JSON and were not GitHub HTML pages.
- Announcement feed matches existing `version` + `announcements[]` parser contract. Existing optional `enabled` and `expiresAt` support remains compatible; UI and cached-feed fallback are unchanged.
- Endpoint feed matches existing `version` + `endpoints[]` parser contract with `id`, `url`, `priority`, and `enabled`.
- DNS Login retained unchanged. Endpoint ID lookup continues using the managed endpoint catalogue. Resolver remains fail-closed until a real HTTPS dynamic backend exists. Password never goes to resolver.
- No GitHub authentication, token, API key, private credentials, or user/provider credentials added.

### Validation

- Focused `AnnouncementRepositoryTest`, `WatchioEndpointManagerTest`, and `WatchioDnsResolverTest`: PASS.
- Full `test`: PASS.
- `lintDebug`: PASS, zero errors.
- `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`: PASS.
- `git diff --check`: PASS.
- S22 `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp`: replacement install PASS; app startup PASS; saved `Admin` provider/session preserved; Notifications inbox loaded production announcement entries. No uninstall or data clear.
- Managed endpoint HTTP/schema validation passed directly. Existing active server remained `MediaTitans`; no credentials were entered or exposed.
- BRAVIA untouched.
- Rescue manifest SHA-256 before/after: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.

### Files added to existing dirty change set by this pass

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/data/announcements/AnnouncementData.kt`
- `native-android/app/src/test/java/com/iamskorpz/watchioiptv/AnnouncementRepositoryTest.kt`
- `AGENT_HANDOFF.md` updated in place.

**COMMIT/PUSH REQUIRES EXPLICIT USER APPROVAL. No commit and no push were performed.**

## 2026-09-27 — DNS Login and `/2711` migration commit/push verification

- User authorization received.
- Source commit: `72df69e827952ef6529cd475eb35c0813f8a4c09` — `Complete Watchio remote config migration and DNS login client`.
- Source commit included 13 intended source, resource, test, and handoff files only.
- Source commit pushed normally to `origin/dev`; no force push.
- Follow-up commit updates this handoff with final immutable source commit and push state.
- `origin/main` remained `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- Rescue manifest SHA-256 after source commit/push: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- Security review found no real usernames, passwords, provider credentials, private URLs, GitHub tokens, API keys, accidental logs, or local-machine secrets in committed files. Fake test values only.
- Untracked screenshots, XML files, logs, and evidence remained unmodified and uncommitted.
- BRAVIA untouched. No release or tag created.

## 2026-09-27 — TV/remote login keyboard dismissal fix (uncommitted)

### Root cause and implementation

- Login forms relied on the platform IME and normal navigation Back handling while leaving the active Compose text field focused. TV/Samsung IMEs can consume Back themselves or keep their IME view reported as present after hiding, leaving remote focus trapped or causing the next Back to be swallowed.
- Added one shared `DismissImeBeforeNavigationBack` controller for Xtream, DNS Login, M3U URL, and Local M3U forms. It uses actual IME inset height, clears text focus, hides the keyboard, restores focus to the form action, and tracks whether the current IME session has already consumed Back. The next Back then performs normal screen navigation.
- Quick Login remains unchanged because it has no text entry/IME.
- Password fields remain masked. No credentials were logged or added to tests.

### Files changed in this pass

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/ProviderFormComposeTest.kt`
- `AGENT_HANDOFF.md` updated in place.

### Validation

- Focused `ProviderFormComposeTest` on authorised S22: PASS, 7/7. Covers Xtream and DNS first-Back IME dismissal, restored D-pad focus, and normal subsequent Back navigation.
- Full `test`: PASS.
- `lintDebug`: PASS, zero errors.
- `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`: PASS.
- `git diff --check`: PASS before this handoff update; rerun after update required and recorded in final report.
- S22 `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp`: replacement install PASS; no uninstall/data clear. Existing saved provider/session preserved.
- Exact final debug build physical Xtream flow: keyboard opened; first Back closed it and stayed on login; focus returned to the form action; keyboard did not immediately reopen; D-pad could re-enter an input and reopen the IME; second Back with the IME closed returned to Provider Management. DNS equivalent covered by the 7/7 connected test and its first-Back physical check.
- Mobile behavior preserved by scoped form-level handling; normal touch/IME field behavior and existing IME actions were not changed.
- BRAVIA untouched.
- Rescue manifest SHA-256 before implementation: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`; final verification recorded in final report.

**No commit and no push were performed.**

### Keyboard fix commit/push record

- Validated source/test/handoff commit: `8f35f3248abdd84dec40e13bc64fbbfb7fae95d3` — `Fix TV login keyboard dismissal and focus handling`.
- Push to `origin/dev`: PASS. No force push, rebase, tag, or release.
- Validation retained: focused S22 tests 7/7 PASS; JVM tests, `lintDebug`, `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`, and `git diff --check` PASS.
- Rescue manifest SHA-256 after commit/push: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- BRAVIA untouched. S22 app data and saved provider/session preserved.

## 2026-09-27 — Automatic device/input detection (uncommitted)

### Architecture and behavior

- Existing `InputMode.Auto`, `InputMode.Touch`, and `InputMode.TvRemote` persistence remains authoritative. No preference key, Room schema, or migration changed.
- Added one shared detector using `UiModeManager`, television UI mode, Leanback, television feature, touchscreen availability, smallest-width tablet classification, and supplemental Amazon/Fire TV model signals.
- Supported classifications: phone, tablet, Android/Google TV, Fire TV, and other TV-style/non-touch devices.
- `Auto` resolves phone/tablet to Touch and all TV classes to TV Remote. Explicit Touch or TV Remote remains a persisted manual override and is never reset when screens reopen.
- First-run device screen shows `Detected: ...`, visually marks detected choice, and puts initial remote focus on it when Compose is in keyboard input mode. Explicit left/right focus links make remote traversal deterministic. Selecting detected choice preserves `Auto`; selecting opposite choice saves manual override.
- Settings Input Mode screen remains available, shows current detection, and keeps Auto/TV Remote/Touch controls.
- Fullscreen player and root TV Back behavior now reuse shared detector instead of duplicate partial TV checks.

### Files changed

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/core/device/DeviceInputDetector.kt` (new)
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/bootstrap/BootstrapViewModel.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/player/WatchioFullscreenPlayerScreen.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/test/java/com/iamskorpz/watchioiptv/DeviceInputDetectorTest.kt` (new)
- `native-android/app/src/test/java/com/iamskorpz/watchioiptv/BootstrapViewModelTest.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/DeviceModeComposeTest.kt` (new)
- `AGENT_HANDOFF.md` updated in place.

### Validation

- Focused detector/bootstrap JVM tests: PASS. Covers television UI mode, Leanback, television feature, Fire TV primary/fallback, phone, tablet, automatic first-run mode, and persisted manual override.
- Device-mode Compose test on authorised S22: PASS, 2/2. Covers detected Touch and TV initial focus plus D-pad Right and Enter manual override activation.
- Full `test`: PASS.
- `lintDebug`: PASS, zero errors.
- `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`: PASS.
- S22 final replacement install: PASS. Settings showed `Detected: Mobile / Touch` with Auto selected. Manual TV Remote override selected and survived process restart. Auto restored afterward. Existing saved `Admin` provider/session remained present after final APK install. No uninstall or data clear.
- TV/Fire classification: covered by pure JVM tests; no physical TV used.
- Rescue manifest SHA-256 remained `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` before implementation and after S22 validation.
- BRAVIA untouched. No commit or push.

### Automatic device/input detection commit and push record

- Validated source/test/handoff commit: `9a83d1814d5f402bd92e11589f65f1557b189839` - `Add automatic device and input detection`.
- Push to `origin/dev`: PASS. No force push, rebase, tag, or release.
- Validation retained: focused detector/bootstrap JVM tests PASS; S22 device-mode Compose tests 2/2 PASS; full `test`, `lintDebug`, `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`, and `git diff --check` PASS.
- S22 replacement install PASS; existing provider/session preserved; no uninstall or data clear.
- Rescue manifest SHA-256 after source commit/push: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- `origin/main` remained `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- BRAVIA untouched. Untracked evidence preserved.

## 2026-09-27 - Remove redundant Live TV category Search

### Root cause and implementation

- The redundant Search item was not a repository or synthetic category. It was a hardcoded `OutlinedTextField` labelled `Search categories` above the Live TV category rail.
- Removed only that category-filter field and its reserved vertical space. Category rail now renders repository categories directly in existing order.
- Preserved ALL CHANNELS, FAVOURITES, HISTORY, genuine provider categories, counts/filtering behavior, selection IDs, focus requesters, category-to-content focus transfer, and saved browsing state.
- Genuine provider categories named `Search` remain visible because no category name or ID filtering/removal was added.
- Existing top-right `Search Live TV` action and search overlay remain unchanged.

### Files changed

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/live/LiveTvScreens.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/LandscapeResponsiveComposeTest.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/HomeComposeTest.kt`
- `AGENT_HANDOFF.md` updated in place.

### Validation

- Focused `LiveTvBrowsingStateTest`: PASS.
- Focused S22 UITEST: PASS, 5/5. Covered absent category Search, preserved special/provider categories including provider category named Search, top-right search overlay, whole-catalog results, result selection, category D-pad activation/focus transfer, and Home-to-Live navigation.
- Full `test`: PASS.
- `lintDebug`: PASS, zero errors.
- `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`: PASS.
- Physical S22 replacement install: PASS; no uninstall/data clear. Existing `Admin` provider/session preserved. Category Search absent; ALL CHANNELS, FAVOURITES, HISTORY, and provider categories visible; top-right Search opened, accepted text, returned real results, selected a result, closed cleanly, and retained ALL CHANNELS context.
- Physical playback route was exercised with real provider channels, but sampled channels remained Buffering/Reconnecting; moving-video playback is NOT VERIFIED, not treated as app failure.
- Physical Back dismissed keyboard first and then closed Search, returning to Live TV with category context intact.
- Evidence added untracked: `s22-live-no-category-search.png`, `s22-live-search-category-removed-1.png`, `s22-live-search-category-removed-2.png`, `s22-live-search-result-playback.png`.
- BRAVIA untouched. Rescue manifest unchanged.

### Commit and push

- Feature commit: `a2fb414157909a1938c5b0330e57e401ba81ca34` (`Remove redundant Live TV Search category`).
- Pushed exclusively to `origin/dev`: PASS; `origin/dev` matched feature commit after fetch.
- `origin/main` remained `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- No force push, rebase, tag, or release. BRAVIA remained untouched.
- Untracked evidence preserved. Correct Git untracked count: 239; earlier 243 figure included four tracked modifications in total status entries.

## 2026-09-27 - Home Settings and Coming Soon update (uncommitted)

### Implementation

- Replaced Home header Playlist/Profiles action with Settings, retaining its 52dp size, position, semantic control styling, touch action, focusability, and existing Settings route.
- Replaced lower Home Settings tile with a non-interactive `Coming Soon` tile using the existing TV/guide placeholder icon, unchanged tile size, and theme-aware styling.
- Coming Soon exposes descriptive semantics but no click action or focus target, so it cannot open an empty screen or trap TV focus.
- Provider/profile management remains available through Settings > Provider Management. Existing provider/session behavior is unchanged.

### Files changed

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/HomeHeaderActionsComposeTest.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/HomeComposeTest.kt`
- `AGENT_HANDOFF.md` updated in place.

### Validation

- Focused S22 UITEST classes `HomeHeaderActionsComposeTest` and `HomeComposeTest`: PASS, 9/9. Covers header Settings size/accessibility/focusability/touch activation, Settings navigation, Back to Home, and Coming Soon having no click action.
- Full `test`: PASS.
- `lintDebug`: PASS, zero errors.
- `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`: PASS.
- Physical S22 replacement install: PASS; no uninstall/data clear. Header Settings and non-clickable Coming Soon rendered correctly. Header Settings opened existing Settings; Provider Management remained present; Back returned Home. Saved `Admin` provider/session remained present.
- BRAVIA untouched.
- Rescue manifest SHA-256 remained `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- Commit: NO. Push: NO.

### Home update commit and push record

- Validated source/test/handoff commit: `79f08aa0d3edf8a745af223ab238a7702dc1ee29` - `Update Home header Settings and Coming Soon tile`.
- Push to `origin/dev`: PASS. No force push, rebase, tag, or release.
- `origin/main` remained `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- Rescue manifest SHA-256 after source commit/push: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- BRAVIA untouched. Untracked evidence preserved.

## 2026-09-27 - Application icon redesign and category rail refinements

### Implementation

- Centralized vector icon system in `ui/icons/WatchioIcons.kt`:
  - Distinct colorful section identities on Home: Live TV blue television (`#2563EB`), Movies purple clapperboard (`#7C3AED`), TV Shows orange panel layout (`#EA580C`).
  - Football green trophy (`#16A34A`), Settings slate gear (`#64748B`), Favourites red heart (`#DC2626`), Search cyan magnifier (`#0891B2`), Coming Soon pink sparkle (`#DB2777`), History teal clock (`#0D9488`).
  - Replaced Settings icon with a canonical 6-tooth gear/cogwheel in `WatchioIcons.kt` (`WatchioIconKind.Settings`), and reused it in `WatchioFullscreenPlayerScreen.kt` (`PlayerIconKind.Settings`).
  - Replaced Home primary cards, Home header actions, TV Guide, Coming Soon, and Settings category cards with reusable icon identities without changing button/card dimensions, actions, focus, navigation, or accessibility descriptions.
- Removed decorative icons beside category labels throughout:
  - Live TV (`CategoryRow` in `LiveTvScreens.kt`)
  - Movies (`MovieCategoryRow` in `MoviesScreens.kt`)
  - TV Shows (`SeriesCategoryRow` in `SeriesScreens.kt`)
  - Cleared decorative icons from Favourites, History, Search, and provider categories.
  - Preserved category text labels, item padding/spacing, selection highlighting, item counts, filtering, and focus transfer navigation intact.
  - Preserved actual top-right Search buttons and search icons across all screens (`LiveSearchIconButton`, `MoviesSearchIconButton`, `SeriesSearchIconButton`).

### Files changed

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/icons/WatchioIcons.kt` (new centralized vector icon system)
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/live/LiveTvScreens.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/movies/MoviesScreens.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/series/SeriesScreens.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/player/WatchioFullscreenPlayerScreen.kt`
- `native-android/app/src/test/java/com/iamskorpz/watchioiptv/WatchioIconsTest.kt` (new)
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/HomeComposeTest.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/LandscapeResponsiveComposeTest.kt`
- `AGENT_HANDOFF.md` updated in place.

### Validation

- Unit tests (`WatchioIconsTest`): PASS (2/2; identities, approved colors, and Settings slate identity color).
- Full JVM test suite (`.\gradlew.bat test`): PASS (1187 / 1187).
- Lint (`.\gradlew.bat lintDebug`): PASS (zero errors).
- Build assemblies (`assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`): PASS.
- S22 connected UITEST:
  - `HomeComposeTest`: PASS (5/5).
  - `LandscapeResponsiveComposeTest#categoryRailsInLiveMoviesAndSeriesHaveCleanLabelsWithoutDecorativeIcons`: PASS (1/1; verifies category rails render clean labels without decorative icons and top-right search buttons remain present).
- `git diff --check`: PASS (zero whitespace/conflict errors).
- Rescue manifest `update.json` SHA-256: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` (verified unchanged).
- BRAVIA device `192.168.1.49:5555`: untouched.

### Icon redesign commit and push record

- Feature commit: `3789c5f94131bf8c0207ea2a20786f7eb310590f` - `Redesign Watchio icons and refine category navigation`.
- Push to `origin/dev`: PASS. No force push, rebase, tag, or release.
- `origin/main` remained `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- Rescue manifest SHA-256 after source commit/push: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- BRAVIA untouched. Untracked evidence preserved.

## 2026-09-27 - Watchio Settings gear icon refinement

### Implementation

- Refined Settings icon geometry in `ui/icons/WatchioIcons.kt` (`WatchioIconKind.Settings`):
  - Replaced stroked wireframe gear outline with a solid 6-tooth mechanical cogwheel.
  - Used `PathFillType.EvenOdd` with defined trapezoidal teeth (`rOuter = unit * 0.44f`, `rRoot = unit * 0.31f`, tooth tip half-angle 11.0°, flank angle 6.5°).
  - Clean circular cutout bore hole in center (`rHole = unit * 0.14f`).
  - Filled style renders sharp, unmistakable gear across Home top header action (`WatchioIconKind.Settings`) and fullscreen player controls (`PlayerIconKind.Settings`).

### Files changed

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/icons/WatchioIcons.kt`
- `AGENT_HANDOFF.md` updated in place.

### Validation

- Full JVM test suite (`.\gradlew.bat test`): PASS (1187 / 1187).
- Lint (`.\gradlew.bat lintDebug`): PASS (zero errors).
- Build assembly (`assembleDebug`): PASS.
- S22 physical replacement install: PASS (`adb install -r native-android\app\build\outputs\apk\debug\app-debug.apk`); no uninstall or data clear.
- S22 physical screen captures:
  - Home top-right action bar: Clean, unmistakable solid gear icon.
  - Fullscreen player controls: Clean, sharp white gear icon.
- `git diff --check`: PASS (zero whitespace/conflict errors).
- Rescue manifest `update.json` SHA-256: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` (verified unchanged).
- BRAVIA device `192.168.1.49:5555`: untouched. Untracked evidence preserved.

### Settings gear refinement commit and push record

- Feature commit: `fa7c77bc1b69733c30ea515d18ba84dfd02cf1f7` - `Refine Watchio Settings gear icon`.
- Push to `origin/dev`: PASS. No force push, rebase, tag, or release.
- `origin/main` remained `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- Rescue manifest SHA-256 after source commit/push: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- BRAVIA untouched. Untracked evidence preserved.

## 2026-09-27 - Watchio DNS Login button removal from Xtream login screen

### Implementation

- Completely removed the `DNS LOGIN` button from the Xtream login screen (`XtreamProviderScreen` in `ui/WatchioNativeApp.kt`):
  - Removed `onDnsLogin` callback navigation from `composable("providers/xtream/add")`.
  - Removed `onDnsLogin: () -> Unit` parameter from `XtreamProviderScreen`.
  - Removed `dnsLoginFocus` FocusRequester.
  - Removed `XtreamLoginAction` for `DNS LOGIN` (`testTag("xtream-dns-login")`).
  - Rewired D-Pad navigation for predictable vertical focus:
    - `connectFocus` down navigation points directly to `quickLoginFocus`.
    - `quickLoginFocus` up navigation points directly to `connectFocus`.
  - Cleaned and balanced button spacing: replaced awkward gap with clean `Spacer(Modifier.height(8.dp))` between `QUICK LOGIN` and `Cancel`.
- Preserved underlying DNS Login implementation:
  - `composable("providers/dns/add")` route preserved.
  - `DnsLoginScreen` composable preserved.
  - `XtreamProviderViewModel.connectDns` backend logic preserved.
  - `WatchioDnsResolver` and unit tests in `WatchioDnsResolverTest.kt` preserved.
  - All saved accounts, credentials, provider configurations, and user data remain intact.

### Files changed

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/ProviderFormComposeTest.kt`
- `AGENT_HANDOFF.md` updated in place.

### Validation

- Full JVM test suite (`.\gradlew.bat test`): PASS (1187 / 1187 passed).
- Lint (`.\gradlew.bat lintDebug`): PASS (zero errors).
- Build assembly (`assembleDebug`, `assembleUitest`, `assembleUitestAndroidTest`): PASS.
- S22 connected UITEST (`connectedUitestAndroidTest`):
  - `ProviderFormComposeTest`: PASS (7 / 7 passed, 0 failed; asserted absence of `xtream-dns-login` and clean D-pad traversal).
- S22 physical device verification:
  - Installed debug APK with `adb install -r native-android\app\build\outputs\apk\debug\app-debug.apk` (no uninstall or data clear).
  - Captured on-device screenshots (`s22-login-verified.png`, `s22-login-full.png`): confirmed `DNS LOGIN` button is completely removed, "or" divider sits cleanly between `SIGN IN` and `QUICK LOGIN`, and `Cancel` is evenly spaced.
  - D-pad bidirectional traversal physically verified on S22: `SIGN IN` <-> `QUICK LOGIN` <-> `Cancel` with no skipped steps or stuck focus.
  - Quick Login navigation verified working on device (`s22-quicklogin-screen.xml`).
  - Cancel navigation verified returning to Home screen.
- `git diff --check`: PASS (zero whitespace/conflict errors).
- Rescue manifest `update.json` SHA-256: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` (verified unchanged).
- BRAVIA device `192.168.1.49:5555`: untouched. Untracked evidence preserved.

### DNS login removal commit and push record

- Feature commit: `e44923f5caf77528b3d0b287f3ba6f689addd288` - `Remove DNS Login button from Xtream login screen`.
- Push to `origin/dev`: PASS. No force push, rebase, tag, or release.
- `origin/main` remained `0364d249ee513f0e814ca8163707e8c2ba47210c` and untouched.
- Rescue manifest SHA-256 after source commit/push: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- BRAVIA untouched. Untracked evidence preserved.

## 2026-09-28 - Live TV NOW / NEXT / LATER release validation

### Implementation

- Added shared indexed EPG matching and NOW/NEXT/LATER calculation for Live TV.
- Live TV loads programme data in batches for visible channels and refreshes progress on a 60-second lifecycle-aware ticker.
- The compact channel rail remains unchanged; the selected channel drives a separate CURRENT PROGRAMME panel with NOW, NEXT, LATER, progress, time and missing-EPG fallback states.
- Programme matching remains provider-scoped. Channel focus does not start playback; existing playback selection remains intact.
- Reused the shared EPG match index in TV Guide to avoid duplicate matching logic.
- Corrected one stale Compose assertion to match the accepted missing-EPG wording, `No programme information`.

### Validation

- Full JVM tests: PASS.
- `lintDebug`: PASS (zero errors).
- `lintRelease`: PASS (zero errors).
- `assembleDebug`: PASS.
- `assembleRelease`: PASS.
- `assembleLocal`: PASS.
- `assembleUitest`: PASS.
- `assembleUitestAndroidTest`: PASS.
- Focused S22 isolated Compose tests: PASS (5/5) after correcting the stale missing-EPG text assertion.
- S22 physical acceptance already completed for compact rows, focused-channel updates, category changes and playback route; evidence remains untracked (`s22-live-now-next-channels.png`, `s22-live-now-next-category-changed.png`, `s22-live-now-next-playing.png`).
- `git diff --check`: PASS.
- Rescue manifest SHA-256 before release preparation: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- Release convention audit: current public release is `v0.1.4` / code 17, so the next public release is `v0.1.5` / code 18; canonical asset name remains `Watchio-IPTV.apk`.
- Expected public signing certificate remains `8A:76:E2:0B:7C:B2:E1:68:12:F5:05:12:75:A3:D1:12:FC:FB:AB:7C:24:24:C5:E8:97:F5:58:87:6B:CC:6F:F0` and must be verified on the final APK before publication.
- BRAVIA untouched. App data preserved. Untracked evidence preserved.

### Live TV feature commit record

- Feature commit: `b65ad56e7bead82e34074f6c9791484095b96446` - `Add Live TV now next and later programme details`.
- Intended push target: `origin/dev` only.

## 2026-09-28 - Home focus highlight and announcement Dismiss readability fixes (uncommitted)

### Bug 1 - Home primary-card focus

- Root cause: `WatchioCard` already supplied the shared focus border, scale, glow, and focused surface, but `HomePrimaryCard` painted an opaque full-card gradient and child surfaces over that mechanism. The previous overlay restored four-sided visibility but remained a separate thin edge border, so it could not match TV Guide's Control-role focus frame and visible focused-surface spacing.
- Fix: removed the large-card-only border overlay. `HomePrimaryCard` now uses the same `WatchioCard` Control-role focus mechanism as `HomeSecondaryPill`/TV Guide. Its original gradient is inset by the configured theme focus-outline width only while focused, exposing the shared focused surface as the same rounded inner spacing while the shared wrapper supplies ring width, colour, corner radius, scale, and glow. Touch actions, dimensions, identity colours, navigation order, refresh controls, and routes are unchanged.

### Bug 2 - Announcement Dismiss text

- Root cause: the Dismiss action used the Primary button variant. Primary keeps `selectedButtonText`, while a focused card uses the darker `focusedSurface`; this produced dark text on a dark focused surface.
- Fix: Dismiss now uses the existing Secondary button variant, which uses theme `buttonText`/`buttonSurface` tokens and remains readable in normal and focused states. Dismiss callback, Back behavior, focus trap, and touch behavior are unchanged.

### Files changed

- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/ui/WatchioNativeApp.kt`
- `native-android/app/src/main/java/com/iamskorpz/watchioiptv/feature/startup/StartupNotifications.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/HomeComposeTest.kt`
- `native-android/app/src/androidTest/java/com/iamskorpz/watchioiptv/StartupNotificationsComposeTest.kt`
- `AGENT_HANDOFF.md`

### Validation

- Full JVM test suite: PASS.
- `lintDebug`: PASS, zero errors.
- `assembleDebug`, `assembleUitest`, and `assembleUitestAndroidTest`: PASS.
- Focused isolated S22 UITEST: PASS, 2/2. Covers Live TV -> Movies -> Series D-pad focus traversal, Series activation, Back-to-Home focus restoration, Dismiss focusability, Dismiss touch activation, and callback behavior.
- S22 replacement install: PASS; no uninstall or data clear. Existing provider/session preserved.
- S22 comparison/validation: replacement install PASS with provider/session preserved. Captured TV Guide's working focus frame before the correction (`s22-tv-guide-focus-reference2.png`) and confirmed the implementation now routes large cards through that exact shared Control-role wrapper. Focus traversal/restoration and exclusive focus passed isolated S22 instrumentation 2/2. Final large-card screenshot capture remained limited because the physical S22 correctly stayed in touch input mode; no false D-pad visual claim is recorded.
- Announcement modal behavior was exercised on the physical S22 through isolated UITEST; no production announcement state or content was changed.
- `git diff --check`: PASS.
- Rescue manifest SHA-256: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- MAIN untouched. BRAVIA untouched. Untracked evidence preserved. No commit or push.

## 2026-09-28 - Home focus follow-up investigation (blocked; not fixed)

- The prior Home-focus completion claim is withdrawn. Repeated physical S22 captures still show no white frame on Live TV despite Android UIAutomator reporting the Live TV node focused.
- Proven physical state: focused node content description `LIVE TV, Updated last: 9:04 pm`, bounds `[108,286][784,921]`, `focusable=true`, `focused=true`; screenshot `s22-home-focus-live-final-mechanism.png` still has no visible frame.
- Input mode was not the blocker: real ADB D-pad key events moved Android focus onto the Live TV card. The temporary Activity/input-mode recovery experiment was removed.
- Rendering/modifier ordering remains implicated: TV Guide/Refresh transparent content exposes the shared `WatchioCard` focus treatment, while the large primary cards use a full-size gradient/content structure. However, speculative custom overlays, hard-coded strokes, single-target click handling, and large-card-only focus overrides did not satisfy physical acceptance and were removed as required.
- The existing diagnostic semantics test (`WatchioFocusVisualActive`) and prior primary-card Control-role/inset work remain uncommitted, but they are not sufficient proof of visible output.
- Physical results: Live TV FAIL; Movies NOT RE-VERIFIED; Series NOT RE-VERIFIED; TV Guide reference PASS from existing evidence. Required four-capture acceptance is incomplete.
- Task status: BLOCKED / NOT FULLY VERIFIED. No fix claim. Full validation gates were not rerun because no accepted root-cause fix was retained.
- Latest replacement install used only authorized S22 `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp`; app data/session preserved. BRAVIA untouched.
- Rescue manifest SHA-256 remains `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- No commit, push, release, tag, MAIN change, uninstall, or data clear.

## 2026-09-29 - Large Home focus-ring rendering continuation (blocked; candidate rejected)

- Reproduced on authorized S22 with real D-pad events. Android focus moves through Live TV, Movies, and Series, but no thick white frame appears.
- Explicit outer/inner content separation was tested: `WatchioCard` remained outer owner; gradient moved into an inner clipped box. Physical result exposed only thin normal top/bottom outline on Movies/Series, not the focused white ring.
- Moving the existing border into Material `Surface.border` and padding shared card content was also tested. Physical result remained unchanged.
- UIAutomator evidence shows the physically focused node is an outer clickable/focusable wrapper with empty description while the child carrying Watchio card semantics remains unfocused. Thus `collectIsFocusedAsState()` does not activate the shared visual state on the physical path, despite Android focus acquisition.
- Removing/reordering the redundant explicit `focusable` was tested and rejected: startup action activation or D-pad traversal regressed. Those changes and all rendering candidates were removed.
- No candidate met acceptance. Retained source remains prior uncommitted state; Announcement Dismiss fix unchanged.
- Candidate gates: JVM `1347/1347` PASS, `lintDebug` PASS, `assembleDebug` PASS, `assembleUitest` PASS, `assembleUitestAndroidTest` PASS, `git diff --check` PASS. These do not override failed physical acceptance.
- Physical results: Live TV FAIL; Movies FAIL; Series FAIL; TV Guide existing reference PASS. Required four-state acceptance not achieved.
- Evidence preserved untracked, including `s22-home-focus-live-nested-final.png`, `s22-home-focus-movies-nested-final.png`, `s22-home-focus-series-nested-final.png`, and related candidate captures/XML.
- Rescue manifest SHA-256 remains `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- BRAVIA and MAIN untouched. No commit, push, release, tag, uninstall, or data clear.

## 2026-09-29 - Large Home focus owner mismatch fixed and physically accepted

### Root cause and fix

- Confirmed root cause: `WatchioCard` composed `Modifier.clickable(...)` and then a second explicit `focusable(...)`. Physical D-pad focus landed the redundant focus target, while the state used for the shared `WatchioCard` visual treatment did not represent that authoritative target.
- Removed the redundant explicit `focusable`; `WatchioCard` now has one effective interactive focus target owned by its existing clickable path.
- `WatchioCard` observes that target with `onFocusChanged`, so the same physical focus state drives scale, glow, focused surface, diagnostic semantics, and focus frame.
- Focus frame remains shared and theme-driven. The existing `WatchioCard` border is rendered as a topmost inset overlay so full-card gradient content cannot obscure it. No Home-specific or hard-coded white border was added.
- `HomePrimaryCard` now delegates focus requester, semantics, touch activation, and D-pad activation directly to `WatchioCard`, matching the coherent TV Guide path. No outer competing focus/click node remains.
- Announcement Dismiss text-colour fix remains unchanged.

### Physical S22 acceptance

- Device: `adb-R5CT83DSMZW-Pw1ptV._adb-tls-connect._tcp` (`SM-S901B`). Replacement install only; provider/session and app data preserved.
- Real D-pad focus and `DPAD_CENTER`:
  - Live TV visual focus PASS; activation opened Live TV once PASS.
  - Movies visual focus PASS; activation opened Movies once PASS.
  - Series visual focus PASS; activation opened Series once PASS.
  - TV Guide reference focus PASS; activation opened EPG Categories once PASS.
- Repeated traversal showed one moving ring, no stuck ring, no missing ring, and no neighbouring-card activation.
- Evidence preserved untracked:
  - `s22-focus-live-pass3.png`
  - `s22-focus-movies-pass.png`
  - `s22-focus-series-pass.png`
  - `s22-focus-tvguide-pass.png`
  - route XML: `s22-focus-live-open.xml`, `s22-focus-movies-open.xml`, `s22-focus-series-open.xml`, `s22-focus-tvguide-open.xml`

### Validation

- Focused S22 Compose test `homePrimaryCardsKeepVisibleDpadFocusAndRestoreLiveFocus`: PASS (1/1).
- Full JVM tests: PASS (1347/1347, zero failures/errors/skips).
- `lintDebug`: PASS, zero errors. Required isolated one-worker run after two combined Gradle batches stalled without results.
- `assembleDebug`: PASS.
- `assembleUitest`: PASS.
- `assembleUitestAndroidTest`: PASS.
- `git diff --check`: PASS.
- Rescue manifest SHA-256: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- BRAVIA untouched. MAIN untouched. Untracked evidence preserved. No commit, push, release, tag, uninstall, or data clear.

### DEV commit and push

- Feature commit: `00a984b2203ffb54cef613f4509508e3919fdb1c` - `Fix TV focus indicators and announcement dismiss styling`.
- Push target: `origin/dev` only.
- Push verification: PASS; feature commit matched `origin/dev` after fetch.
- Validation at commit time: focused S22 tests 2/2 PASS; JVM tests 1347/1347 PASS; `lintDebug`, `assembleDebug`, `assembleUitest`, `assembleUitestAndroidTest`, and `git diff --check` PASS.
- Rescue manifest SHA-256: `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB`.
- MAIN and BRAVIA remained untouched. No release or tag was created. Untracked evidence remained uncommitted.
