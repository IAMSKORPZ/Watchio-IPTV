# Announcements

Watchio reads announcements asynchronously from `announcements/announcements.json` on the `dev` branch. The last valid JSON feed is cached in the existing Preferences DataStore, so the inbox remains available offline. Seen and dismissed IDs are stored separately; no Room tables or migrations are used.

## Feed format

```json
{
  "version": 1,
  "announcements": [
    {
      "id": "watchio-announcements-welcome",
      "title": "Welcome to Announcements",
      "body": "Watchio news and alerts are now available in the app.",
      "publishedAt": "2026-08-31T16:00:00Z",
      "type": "GENERAL",
      "priority": "NORMAL",
      "dismissible": true,
      "expiresAt": null,
      "action": null
    }
  ]
}
```

Required fields are `id`, `title`, `body`, `publishedAt`, `type`, and `priority`. Supported types are `GENERAL`, `FEATURE`, `MAINTENANCE`, `IMPORTANT`, and `UPDATE`. Priorities are `NORMAL`, `IMPORTANT`, and `CRITICAL`. `dismissible`, `expiresAt`, and `action` are optional. Dates use ISO-8601 UTC timestamps.

Supported action objects:

- `{"type":"OPEN_URL","url":"https://example.com","label":"OPEN"}` allows HTTP or HTTPS only.
- `{"type":"OPEN_SCREEN","target":"MOVIES","label":"VIEW"}` allows only known app destinations: `HOME`, `LIVE_TV`, `MOVIES`, `SERIES`, or `SETTINGS`.
- `{"type":"OPEN_UPDATER","label":"UPDATE NOW"}` opens the existing updater screen without changing updater behavior.

Unknown fields are ignored. A malformed announcement is skipped without discarding valid entries. A fetched feed replaces the cache only after successful feed parsing.
