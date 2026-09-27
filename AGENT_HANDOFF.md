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
| Branch / Base | PASS | `codex/dev-rescue-bridge` at `2384698ea94ddea751a9ac896e28c7c9345822e2`; `origin/dev` matches |
| Theme Builder Phase 2 | COMPLETE | Committed and pushed to `origin/dev` (`2384698`) |
| TV Guide / Player / EPG Categories | COMPLETE / ACCEPTED | Accepted by user; full regression pass complete; commit + push authorized |
| Tracked working tree | STAGING | 22 source/test/resource files + `AGENT_HANDOFF.md` |
| JVM tests | PASS | All unit tests pass (1187 / 1187) |
| Lint / build gates | PASS | `lintDebug`, `assembleDebug`, `assembleLocal`, `assembleUitest`, `assembleUitestAndroidTest`, `git diff --check` |
| S22 automated UITEST (Full Suite) | PASS | 262 / 262 passed (0 failed, 0 errors, 11m 47s) |
| S22 physical acceptance | PASS | Physically accepted by user: TV Guide redesign, mini-preview, fullscreen, controls, Live/Movie/Series playback, EPG Categories |
| Android TV / BRAVIA | DEFERRED / UNTOUCHED | TV unavailable; `192.168.1.49:5555` untouched |
| Rescue bridge manifest | PROTECTED | SHA-256 `AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB` verified unchanged |
| Public stable | UNCHANGED | `v0.1.4` (`a0e5bafce943eebecbaeb03e0122ba7c2e0c62e5`); `origin/main` untouched |
| Next update | PLANNED | Sports Broadcast / Where to Watch |
| Next action | COMMIT + PUSH | Commit accepted work to dev, push to `origin/dev` |

---

## Repository / Git State

| Item | Verified value |
|---|---|
| Worktree | `C:\Users\mrsko\.codex\watchio-dev-rescue-worktree` |
| Branch | `codex/dev-rescue-bridge` |
| `HEAD` | `2384698ea94ddea751a9ac896e28c7c9345822e2` |
| `origin/dev` | `2384698ea94ddea751a9ac896e28c7c9345822e2` |
| `origin/main` | `0364d249ee513f0e814ca8163707e8c2ba47210c` |
| Latest commit subject | `Complete application-wide theme integration` |
| Tracked working tree | DIRTY — TVG-1 + TVG-2 changes pending commit |
| Untracked files | `AGENT_HANDOFF.md` + intentional `s22-*` test/physical evidence |

Untracked `AGENT_HANDOFF.md` and `s22-*` evidence files are intentional and **MUST NOT** be blindly staged, committed, cleaned, or deleted.

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
TV GUIDE / PLAYER / EPG CATEGORIES: COMPLETE / ACCEPTED / VALIDATED
DEV HEAD:                          2384698ea94ddea751a9ac896e28c7c9345822e2
origin/dev:                        2384698ea94ddea751a9ac896e28c7c9345822e2
TRACKED WORKING TREE:              STAGING FOR COMMIT
S22 AUTOMATED SUITE (FULL):        PASS (262 / 262, 0 failed, 11m 47s)
S22 PHYSICAL ACCEPTANCE:           PASS (User accepted all TV Guide, Player, EPG flows)
ANDROID TV PHYSICAL THEME BUILDER: DEFERRED / UNTOUCHED
RESCUE BRIDGE:                     PROTECTED / UNCHANGED (AB1963BA44FBFDAFDC37EC60C6DADBDCF1D53E59F153CABE9E882026D83A94BB)
PUBLIC STABLE:                     UNCHANGED v0.1.4
NEXT ACTION:                       COMMIT to dev, PUSH to origin/dev
NEXT UPDATE:                       SPORTS BROADCAST / WHERE TO WATCH
SPORTS IMPLEMENTATION:             NOT STARTED
SAFE TO START SPORTS AUDIT:        YES (after commit+push)
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
