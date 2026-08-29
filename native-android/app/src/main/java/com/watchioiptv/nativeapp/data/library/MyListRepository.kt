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
            .mapNotNull { item ->
                when (item.contentType) {
                    ContentType.Movie -> {
                        val vodMovie = database.vodDao().find(providerId.value, item.contentId)
                        val m3uMovie = if (vodMovie == null) database.m3uItemDao().find(providerId.value, item.contentId) else null
                        if (vodMovie == null && m3uMovie == null) {
                            null
                        } else {
                            val title = vodMovie?.name ?: m3uMovie?.name ?: item.title
                            val image = vodMovie?.posterUrl ?: m3uMovie?.tvgLogo ?: item.imageUrl
                            ContinueWatchingItem(
                                providerId = item.providerId,
                                contentType = ContentType.Movie,
                                contentId = item.contentId,
                                subContentId = null,
                                title = title,
                                subtitle = "Movie",
                                imageUrl = image,
                                positionMs = item.positionMs,
                                durationMs = item.durationMs,
                            )
                        }
                    }
                    ContentType.Episode -> {
                        val episodeId = item.subContentId
                        val seriesId = item.contentId
                        val episodeEntity = if (episodeId != null) database.episodeDao().find(providerId.value, seriesId, episodeId) else null
                        val seriesEntity = database.seriesDao().find(providerId.value, seriesId)
                        val m3uSeries = if (seriesEntity == null && episodeEntity == null) database.m3uItemDao().find(providerId.value, seriesId) else null
                        if (episodeEntity == null && seriesEntity == null && m3uSeries == null) {
                            null
                        } else {
                            val seriesTitle = seriesEntity?.name ?: m3uSeries?.name ?: item.title
                            val epTitle = episodeEntity?.title ?: "Episode"
                            val seasonNum = episodeEntity?.seasonNumber
                            val epNum = episodeEntity?.episodeNumber
                            val sub = if (seasonNum != null && epNum != null) "S$seasonNum • E$epNum" else epTitle
                            val image = episodeEntity?.imageUrl ?: seriesEntity?.backdropUrl ?: seriesEntity?.coverUrl ?: m3uSeries?.tvgLogo ?: item.imageUrl
                            ContinueWatchingItem(
                                providerId = item.providerId,
                                contentType = ContentType.Episode,
                                contentId = seriesId,
                                subContentId = episodeId,
                                title = seriesTitle,
                                subtitle = sub,
                                imageUrl = image,
                                positionMs = item.positionMs,
                                durationMs = item.durationMs,
                                seasonNumber = seasonNum,
                                episodeNumber = epNum,
                                episodeTitle = epTitle,
                            )
                        }
                    }
                    else -> null
                }
            }
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
