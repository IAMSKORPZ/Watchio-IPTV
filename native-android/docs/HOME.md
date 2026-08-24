# Watchio Native Home

Phase 10 turns Home into the native app shell around the working catalog and player features. Phase 14.1 polishes the visual hierarchy using the shared design-system foundation. Phase 14.2A applies the first screenshot-inspired visual redesign to Home only. Phase 14.2B aligns Home to the approved balanced three-column reference.

## Architecture

`HomeScreen` renders immutable state from `HomeViewModel`. The ViewModel combines selected provider settings with provider-scoped repository counts. Compose does not query Room, read secrets, or build provider URLs.

## Dashboard

Home exposes Live TV, Movies, Series, Search, TV Guide, Settings, and Playlist/provider management after Xtream authentication. Counts are scoped to the selected Xtream provider and use repository count queries instead of loading catalogs.

Phase 11 adds a TV Guide Home card. It has no artificial catalog count and does not affect Live TV, Movies, or Series counts.

## Visual Hierarchy

Primary actions are Live TV, Movies, and Series. They use glass-style cards, semantic Watchio accents, count labels, direct refresh affordances, and media-specific icon treatment.

The loaded-provider Home structure is:

```text
Header: Watchio | Time/Date | Search | Sports | Announcements | Playlist
Main:   Live TV | Movies    | Series
        Live TV | TV Guide  | Settings
Footer: safe provider/cache/version context
```

Live TV spans the full height of the left column. Movies and Series share equal width and height in the upper row. TV Guide sits directly under Movies. Settings sits directly under Series.

My List remains an application feature and route, but it is no longer exposed on the Home dashboard.

Utility actions are Search, Sports, Announcements, and Playlist in the top bar. Search opens the unified Global Search overlay (Phase 14.2K) searching Live TV, Movies, and Series across the active provider with grouped result sections. Playlist opens the existing provider management route. Sports and Announcements are safe placeholder routes for future phases.

## Header

The header shows Watchio branding, a compact temporary logo mark, live clock/date, and compact utility actions. It never displays usernames, passwords, raw provider URLs, tokens, or credential-bearing links.

## Phase 14.2A Visual Treatment

The Home background is currently a Compose-drawn dark neon treatment with layered gradients, soft motion-like arcs, and a safety scrim. This is a temporary native implementation because final Watchio logo and wallpaper assets are not yet present in the repository.

The footer shows only safe provider metadata, version, and active provider context. The app no longer shows `Watchio Native` as user-facing Home branding; the product name is `Watchio`.

## Status Metadata

Primary card status areas do not show cached item counts. Live TV, Movies, and Series show the selected provider's last successful catalogue refresh time as local device time:

```text
Updated last: 10:59 PM
```

If no successful catalogue refresh timestamp exists, Home shows `Updated last: Never`. The timestamp comes from persistent provider metadata and survives navigation, restart, and `adb install -r`.

The compact refresh control uses the existing provider refresh flow. Failed refreshes preserve the previous successful timestamp and cached catalogue data.

## Provider Expiry

The footer left slot displays safe provider expiry metadata:

```text
Expiration: 29 Oct 2026
```

Xtream expiry is parsed from `user_info.exp_date` during successful authentication/import/refresh and persisted in DataStore by provider id. Providers without expiry data show `Expiration: Not available`. Home never displays usernames, passwords, provider URLs, raw authentication responses, tokens, or SecretStore values.

## Responsive Behavior

The dashboard uses shared spacing, size, typography, icon, and focus tokens. S22/tablet/TV layouts keep the same three-column structure and the loaded-provider Home is designed to fit without vertical scrolling.

## Input

Dashboard actions use `WatchioCard` and `WatchioButton`, so touch, mouse click, Enter/OK, and D-pad focus remain supported. Initial focus is requested on Live TV. Focus styling comes from the Watchio theme.

## Accessibility

Primary and secondary cards expose concise content descriptions such as `LIVE TV, 10221 channels`. Decorative badges are kept visually compact and do not expose secrets.

## Empty State

Home is not an unauthenticated landing page. Bootstrap routes users without an enabled Xtream provider to Xtream Codes login; M3U-only state does not render Home.

Phase 14.2D routes normal no-provider startup to provider setup before Home. The empty state remains as defensive UI for stale navigation only.

## Section Refresh

Home refresh buttons are section-specific:

- Live TV refresh updates Live categories and Live streams only.
- Movies refresh updates VOD categories and VOD streams only.
- Series refresh updates Series categories and Series catalog only.

Each card has its own refreshing state and DataStore-backed last-success timestamp. Failed refreshes preserve cached content and do not advance timestamps. EPG data is independent from Home catalog refresh.
