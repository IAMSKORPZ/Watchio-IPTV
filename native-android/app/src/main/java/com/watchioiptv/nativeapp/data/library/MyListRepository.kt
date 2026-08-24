package com.watchioiptv.nativeapp.data.library

import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MyListRepository(
    private val database: WatchioDatabase,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
) {
    suspend fun selectedProviderId(): ProviderId? = settingsRepository.selectedProviderId.first()

    suspend fun load(): MyListData = withContext(Dispatchers.IO) {
        val providerId = selectedProviderId() ?: return@withContext MyListData()
        val favorites = favoritesRepository.getFavorites(providerId)
        val history = historyRepository.recent(providerId)
        val continueWatching = history
            .filter { (it.contentType == ContentType.Movie || it.contentType == ContentType.Episode) && MoviesRepository.shouldResumePosition(it.positionMs, it.durationMs) }
            .map { ContinueWatchingItem(it.providerId, it.contentType, it.contentId, it.subContentId, it.title, subtitleFor(it.contentType, it.contentId, it.subContentId), it.imageUrl, it.positionMs, it.durationMs) }
        val libraryHistory = history.map {
            LibraryHistoryItem(it.providerId, it.contentType, it.contentId, it.subContentId, it.title, subtitleFor(it.contentType, it.contentId, it.subContentId), it.imageUrl, it.positionMs, it.durationMs, it.lastWatchedAtEpochMs)
        }
        MyListData(
            continueWatching = continueWatching,
            liveFavorites = favorites.filter { it.contentType == ContentType.Live }.map { LibraryFavoriteItem(it.providerId, it.contentType, it.contentId, it.title, it.imageUrl) },
            movieFavorites = favorites.filter { it.contentType == ContentType.Movie }.map { LibraryFavoriteItem(it.providerId, it.contentType, it.contentId, it.title, it.imageUrl) },
            seriesFavorites = favorites.filter { it.contentType == ContentType.Series }.map { LibraryFavoriteItem(it.providerId, it.contentType, it.contentId, it.title, it.imageUrl) },
            history = libraryHistory,
        )
    }

    suspend fun removeFavorite(item: LibraryFavoriteItem): MyListData {
        favoritesRepository.toggle(
            com.watchioiptv.nativeapp.domain.repository.FavoriteItem(
                providerId = item.providerId,
                contentType = item.contentType,
                contentId = item.contentId,
                title = item.title,
                imageUrl = item.imageUrl,
                createdAtEpochMs = 0,
            ),
        )
        return load()
    }

    private suspend fun subtitleFor(contentType: ContentType, contentId: String, subContentId: String?): String? {
        return when (contentType) {
            ContentType.Episode -> {
                val episode = subContentId?.let { database.episodeDao().getBySeries(selectedProviderId()?.value.orEmpty(), contentId).firstOrNull { ep -> ep.episodeId == it } }
                episode?.let { "S${it.seasonNumber} E${it.episodeNumber} - ${it.title}" } ?: "Episode"
            }
            ContentType.Movie -> "Movie"
            ContentType.Live -> "Live TV"
            ContentType.Series -> "Series"
        }
    }
}
