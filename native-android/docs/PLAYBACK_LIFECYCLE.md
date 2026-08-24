# Watchio Native Playback Lifecycle

Phase 13.1 makes playback foreground-only and locks the app to landscape.

## Foreground Policy

Watchio does not run a background media service. When the Activity stops, the active route supplies a playback background handler:

- Live TV stops/pauses the shared player so no hidden stream keeps playing.
- Movies save the current position, cancel the periodic history job, and pause.
- Episodes save the current episode position, cancel the periodic history job, and pause.
- If playback was already paused, returning to Watchio does not auto-play.

The user must press Play or select a channel again after returning.

Phase 14.2G does not add background playback. Player Settings keeps foreground-only playback as the fixed product rule.

## Retry

`Media3WatchioPlayerManager.pause()` cancels pending retry work. A stream in `Recovering` cannot restart itself while Watchio is backgrounded.

## History

Movies and Episodes reuse the existing history repository. Background pause performs one immediate save, then stops the 15-second save loop until the user presses Play again. Live TV does not write VOD resume progress.

## Audio, Wake, And Surface

The shared Media3 player receives pause/stop through the existing manager. No notification, foreground service, second player, or background audio path is created. Surfaces remain owned by the existing `PlayerView` handoff model and are reattached normally when the UI returns.

## Landscape

`MainActivity` is locked centrally in `AndroidManifest.xml` with `sensorLandscape`. This prevents portrait on phones/tablets while allowing both landscape rotations. TV devices remain naturally landscape.

## Validation Notes

Manual Phase 13.1 S22 install is restricted to `192.168.1.28:38687` after confirming model `SM_S901B`. Other attached devices are not manual install targets for this phase.
