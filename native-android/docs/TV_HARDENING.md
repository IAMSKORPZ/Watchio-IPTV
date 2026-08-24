# Watchio Native TV Hardening

Phase 12 targets Android TV, Google TV, Sony BRAVIA, Fire TV, Firestick, and keyboard-only use.

## Focus Architecture

Top-level TV screens use explicit initial focus instead of relying on root focus:

- Home: first Home card
- Providers: first provider row when providers exist
- Settings: first theme card
- Live TV: first category
- Movies: first category
- Series: first category
- Search: search field
- TV Guide: NOW button
- Player overlays: primary Play/Pause action

Plain poster/list rows now use the same visible focus treatment as Watchio cards where they are primary TV targets.

## Remote Keys

Expected input:

- DPAD_CENTER / Enter / Select activates focused controls.
- Arrow keys move focus through Compose focus targets.
- Back / Escape exits the current overlay or screen through route handlers.
- Player left/right seek remains enabled for VOD.

## Layout Strategy

TV layouts stay density-independent and use existing adaptive breakpoints. Phase 12 does not redesign screens. It hardens safe margins, initial focus, and focus visibility.

## Player Overlay

Fullscreen player overlays keep a single Media3 session. When controls auto-hide, focus is moved back to the player surface so OK/Enter can reopen controls instead of leaving focus on removed buttons.

Phase 12.1 also makes the Live TV preview surface focusable so remote OK/Select can enter fullscreen from BRAVIA without touch or mouse.

## Cross-Device Regression Notes

See `DEVICE_REGRESSIONS.md` for S22 Settings, S22 TV Guide, BRAVIA Settings, BRAVIA fullscreen, BRAVIA guide-data, and ALL MOVIES fixes.

## Manifest

The app declares `LEANBACK_LAUNCHER`, marks touchscreen optional, marks leanback optional, and references `@drawable/tv_banner`.

## Device Status

BRAVIA manual target for this phase:

- Serial: `192.168.1.49:5555`
- Model: `BRAVIA_4K_VH22`
- Android: 12

Fire TV hardware was not detected in the current ADB session unless noted in the final report.

## Security

TV overlays and focus changes do not display provider credentials, generated Xtream URLs, or tokenized M3U URLs.
