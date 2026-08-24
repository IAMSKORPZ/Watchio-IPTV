# Watchio Native Onboarding

Phase 14.2D.2 uses a strict Xtream-authenticated startup state machine:

```text
Loading -> NeedsDeviceMode -> NeedsXtreamLogin -> Ready
```

Startup reads only DataStore onboarding state, selected provider id, and provider metadata. It does not load catalog rows, EPG, artwork, or playback state.

## Existing Users

If an enabled Xtream provider already exists and the device-mode onboarding flag is missing, Watchio treats the install as established:

```text
Xtream provider exists + no onboarding flag -> mark onboarding complete -> select valid Xtream provider -> Home
```

The user is not forced through Mobile/TV selection or provider setup.

## Genuine First Run

Fresh state with no providers and no onboarding flag shows the Mobile/Touch or TV/Remote choice.

Mobile persists the existing `InputMode.Touch` preference. TV persists the existing `InputMode.TvRemote` preference. Both set `device_mode_onboarding_completed = true`, then route directly to the existing Xtream Codes login form.

There is no first-run Add Playlist screen and no first-run M3U choice.

## Provider Required

Home is only an Xtream-authenticated configured-session screen. If no enabled Xtream provider exists after onboarding, Watchio routes to Xtream Codes login. Successful Xtream authentication plus initial import selects the provider, marks onboarding complete, and routes to Home with setup screens removed from the back stack.

M3U URL and Local M3U providers remain supported after login through provider management, but M3U alone does not unlock Home.

Deleting the final Xtream provider removes Home eligibility and sends the app back to Xtream login. It does not reset device-mode onboarding, input mode, theme, EPG settings, or unrelated settings. If another enabled Xtream provider remains, Watchio selects it and Home remains available.

## Storage

Onboarding completion is stored in DataStore. Provider credentials stay in SecretStore. No Room schema change is used.
