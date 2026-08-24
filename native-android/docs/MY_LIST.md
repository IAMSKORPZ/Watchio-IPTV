# Watchio Native My List

Phase 9 adds one surface for Continue Watching, Favourites, and History.

`MyListScreen` renders state. `MyListViewModel` loads and updates immutable state. `MyListRepository` composes existing `FavoritesRepository`, `HistoryRepository`, and Room catalog DAOs.

Continue Watching is derived from watch history for Movies and Episodes only. Live TV is history, not resumable content. Completed or near-complete videos are filtered with the Movies resume threshold.

Favourites are grouped as Live TV, Movies, and Series. Removing a favourite calls the shared favourites repository and does not delete catalog rows.

History is provider-scoped and sorted by latest watched time. Episode rows keep series id plus episode id so navigation returns through Series details.

My List works offline from Room/DataStore and does not expose credentials or generated playback URLs.
