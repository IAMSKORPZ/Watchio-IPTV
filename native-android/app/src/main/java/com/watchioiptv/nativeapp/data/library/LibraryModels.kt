package com.watchioiptv.nativeapp.data.library

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ContentType

data class WatchioSearchResult(
    val providerId: ProviderId,
    val contentType: ContentType,
    val contentId: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val year: String? = null,
    val rating: String? = null,
)

data class SearchResults(
    val live: List<WatchioSearchResult> = emptyList(),
    val movies: List<WatchioSearchResult> = emptyList(),
    val series: List<WatchioSearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

data class ContinueWatchingItem(
    val providerId: ProviderId,
    val contentType: ContentType,
    val contentId: String,
    val subContentId: String?,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val positionMs: Long?,
    val durationMs: Long?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
)

data class LibraryFavoriteItem(
    val providerId: ProviderId,
    val contentType: ContentType,
    val contentId: String,
    val title: String,
    val imageUrl: String?,
)

data class LibraryHistoryItem(
    val providerId: ProviderId,
    val contentType: ContentType,
    val contentId: String,
    val subContentId: String?,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val positionMs: Long?,
    val durationMs: Long?,
    val lastWatchedAtEpochMs: Long,
)

data class MyListData(
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val liveFavorites: List<LibraryFavoriteItem> = emptyList(),
    val movieFavorites: List<LibraryFavoriteItem> = emptyList(),
    val seriesFavorites: List<LibraryFavoriteItem> = emptyList(),
    val history: List<LibraryHistoryItem> = emptyList(),
)
