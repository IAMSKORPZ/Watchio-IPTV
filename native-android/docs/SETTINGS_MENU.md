# Settings Menu

Phase 14.2E changes Settings from a single controls page into a Watchio category launcher.

## Root Categories

The root menu shows exactly these categories:

- Provider Management - Manage IPTV providers
- Account Information - View your account details
- Player Settings - Playback and video settings
- EPG Settings - Guide and programme settings
- Parental Controls - Restrict content and settings
- Stream Format - Choose your preferred format
- Input Mode - Mobile touch or TV remote controls
- Appearance - Theme and visual customization
- Backup & Restore - Export and restore application data
- Check for Updates - Check for a newer Watchio version

## Layout

The root uses the same generated Watchio background as Home, a header with Back, Watchio branding, a centered SETTINGS title, and live local time/date. Category cards are shown in a 3-column lazy grid with vertical scrolling for smaller landscape screens.

## Routing

Provider Management opens the existing provider management screen. Account Information opens the safe Xtream account metadata page. Player Settings opens real DataStore-backed playback preferences. EPG Settings, Stream Format, Input Mode, and Appearance reuse the existing DataStore-backed settings logic. Parental Controls, Backup & Restore, and Check for Updates are styled placeholders only.

## Back Behavior

Settings root Back returns to Home. Category Back returns to Settings root through the navigation back stack.

## Deferred Work

Future implementation order: Provider Management polish, Parental Controls, Backup & Restore, Check for Updates.
