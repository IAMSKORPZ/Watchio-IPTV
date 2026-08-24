package com.watchioiptv.nativeapp.data.library

import com.watchioiptv.nativeapp.core.database.LiveStreamEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.database.SeriesEntity
import com.watchioiptv.nativeapp.core.database.VodStreamEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer
import com.watchioiptv.nativeapp.core.util.TextNormalizer
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class SearchScope { Global, Live, Movies, Series }

class SearchRepository(
    private val database: WatchioDatabase,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun selectedProviderId(): ProviderId? = settingsRepository.selectedProviderId.first()

    suspend fun search(query: String, scope: SearchScope, limitPerType: Int = 40): SearchResults = withContext(Dispatchers.IO) {
        val providerId = selectedProviderId() ?: return@withContext SearchResults()
        val provider = database.providerDao().findById(providerId.value) ?: return@withContext SearchResults()
        val normalized = TextNormalizer.normalizeForSearch(query)
        if (normalized.isBlank()) return@withContext SearchResults()
        val type = ProviderType.fromPersisted(provider.type)
        SearchResults(
            live = if (scope == SearchScope.Global || scope == SearchScope.Live) live(providerId, type, normalized, limitPerType) else emptyList(),
            movies = if (scope == SearchScope.Global || scope == SearchScope.Movies) movies(providerId, type, normalized, limitPerType) else emptyList(),
            series = if (scope == SearchScope.Global || scope == SearchScope.Series) series(providerId, type, normalized, limitPerType) else emptyList(),
        )
    }

    private suspend fun live(providerId: ProviderId, type: ProviderType, query: String, limit: Int): List<WatchioSearchResult> = when (type) {
        ProviderType.Xtream -> database.liveStreamDao().search(providerId.value, query, limit).map { it.toResult() }
        ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().searchByType(providerId.value, ContentType.Live.persisted, query, limit).map { it.toResult(ContentType.Live) }
    }

    private suspend fun movies(providerId: ProviderId, type: ProviderType, query: String, limit: Int): List<WatchioSearchResult> = when (type) {
        ProviderType.Xtream -> database.vodDao().search(providerId.value, query, limit).map { it.toResult() }
        ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().searchByType(providerId.value, ContentType.Movie.persisted, query, limit).map { it.toResult(ContentType.Movie) }
    }

    private suspend fun series(providerId: ProviderId, type: ProviderType, query: String, limit: Int): List<WatchioSearchResult> = when (type) {
        ProviderType.Xtream -> database.seriesDao().search(providerId.value, query, limit).map { it.toResult() }
        ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().searchByType(providerId.value, ContentType.Series.persisted, query, limit)
            .groupBy { it.seriesName ?: it.name.substringBefore(" S").substringBefore(" Season").trim() }
            .values.mapNotNull { it.minByOrNull { row -> row.playlistOrder }?.toResult(ContentType.Series) }
    }

    private fun LiveStreamEntity.toResult() = WatchioSearchResult(
        providerId = ProviderId(providerId),
        contentType = ContentType.Live,
        contentId = streamId,
        title = name,
        subtitle = categoryId,
        imageUrl = iconUrl,
    )

    private fun VodStreamEntity.toResult(): WatchioSearchResult {
        val clean = MediaTitleNormalizer.cleanTitle(name)
        return WatchioSearchResult(
            providerId = ProviderId(providerId),
            contentType = ContentType.Movie,
            contentId = streamId,
            title = clean.displayTitle,
            subtitle = genre ?: categoryId,
            imageUrl = posterUrl,
            year = clean.detectedYear,
            rating = rating,
        )
    }

    private fun SeriesEntity.toResult(): WatchioSearchResult {
        val clean = MediaTitleNormalizer.cleanTitle(name)
        return WatchioSearchResult(
            providerId = ProviderId(providerId),
            contentType = ContentType.Series,
            contentId = seriesId,
            title = clean.displayTitle,
            subtitle = genre ?: categoryId,
            imageUrl = coverUrl,
            year = clean.detectedYear ?: releaseDate?.take(4),
            rating = rating,
        )
    }

    private fun M3uItemEntity.toResult(type: ContentType): WatchioSearchResult {
        val rawTitle = if (type == ContentType.Series) seriesName ?: name.substringBefore(" S").substringBefore(" Season").trim() else name
        val clean = MediaTitleNormalizer.cleanTitle(rawTitle)
        return WatchioSearchResult(
            providerId = ProviderId(providerId),
            contentType = type,
            contentId = if (type == ContentType.Series) TextNormalizer.normalizeForSearch(clean.displayTitle).replace(' ', '-') else itemId,
            title = clean.displayTitle,
            subtitle = groupTitle,
            imageUrl = tvgLogo,
            year = clean.detectedYear,
            rating = null,
        )
    }
}
