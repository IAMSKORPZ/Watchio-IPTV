package com.iamskorpz.watchioiptv.data.library

import com.iamskorpz.watchioiptv.core.database.LiveStreamEntity
import com.iamskorpz.watchioiptv.core.database.M3uItemEntity
import com.iamskorpz.watchioiptv.core.database.SeriesEntity
import com.iamskorpz.watchioiptv.core.database.VodStreamEntity
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.util.MediaTitleNormalizer
import com.iamskorpz.watchioiptv.core.util.TextNormalizer
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

enum class SearchScope { Global, Live, Movies, Series }

class SearchRepository(
    private val database: WatchioDatabase,
    private val settingsRepository: SettingsRepository,
    private val historyStore: SearchHistoryStore = EmptySearchHistoryStore,
) {
    val activeProviderId: Flow<ProviderId?> = settingsRepository.selectedProviderId
    suspend fun selectedProviderId(): ProviderId? = settingsRepository.selectedProviderId.first()

    suspend fun recentQueries(providerId: ProviderId): List<String> = withContext(Dispatchers.IO) { historyStore.get(providerId) }

    suspend fun recordQuery(providerId: ProviderId, query: String) = withContext(Dispatchers.IO) {
        val display = query.trim().replace(Regex("\\s+"), " ")
        val normalized = normalizeSearchQuery(display)
        if (normalized.isBlank()) return@withContext
        val updated = listOf(display) + historyStore.get(providerId).filter { normalizeSearchQuery(it) != normalized }
        historyStore.put(providerId, updated.take(MAX_RECENTS))
    }

    suspend fun removeRecent(providerId: ProviderId, query: String) = withContext(Dispatchers.IO) {
        val normalized = normalizeSearchQuery(query)
        historyStore.put(providerId, historyStore.get(providerId).filter { normalizeSearchQuery(it) != normalized })
    }

    suspend fun clearRecents(providerId: ProviderId) = withContext(Dispatchers.IO) { historyStore.put(providerId, emptyList()) }

    suspend fun search(query: String, scope: SearchScope, limitPerType: Int = 40): SearchResults = withContext(Dispatchers.IO) {
        val providerId = selectedProviderId() ?: return@withContext SearchResults()
        search(providerId, query, scope, limitPerType)
    }

    suspend fun search(providerId: ProviderId, query: String, scope: SearchScope, limitPerType: Int = 40): SearchResults = withContext(Dispatchers.IO) {
        val provider = database.providerDao().findById(providerId.value) ?: return@withContext SearchResults()
        val normalized = normalizeSearchQuery(query)
        if (normalized.isBlank()) return@withContext SearchResults()
        val type = ProviderType.fromPersisted(provider.type)
        SearchResults(
            live = if (scope == SearchScope.Global || scope == SearchScope.Live) live(providerId, type, normalized, limitPerType) else emptyList(),
            movies = if (scope == SearchScope.Global || scope == SearchScope.Movies) movies(providerId, type, normalized, limitPerType) else emptyList(),
            series = if (scope == SearchScope.Global || scope == SearchScope.Series) series(providerId, type, normalized, limitPerType) else emptyList(),
        )
    }

    private suspend fun live(providerId: ProviderId, type: ProviderType, query: String, limit: Int): List<WatchioSearchResult> = when (type) {
        ProviderType.Xtream -> database.liveStreamDao().getByProvider(providerId.value).map { it.toResult() }
        ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().getByProviderAndType(providerId.value, ContentType.Live.persisted).map { it.toResult(ContentType.Live) }
    }.rankedForSearch(query, limit)

    private suspend fun movies(providerId: ProviderId, type: ProviderType, query: String, limit: Int): List<WatchioSearchResult> = when (type) {
        ProviderType.Xtream -> database.vodDao().getByProvider(providerId.value).map { it.toResult() }
        ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().getByProviderAndType(providerId.value, ContentType.Movie.persisted).map { it.toResult(ContentType.Movie) }
    }.rankedForSearch(query, limit)

    private suspend fun series(providerId: ProviderId, type: ProviderType, query: String, limit: Int): List<WatchioSearchResult> = when (type) {
        ProviderType.Xtream -> database.seriesDao().getByProvider(providerId.value).map { it.toResult() }
        ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().getByProviderAndType(providerId.value, ContentType.Series.persisted)
            .groupBy { it.seriesName ?: it.name.substringBefore(" S").substringBefore(" Season").trim() }
            .values.mapNotNull { it.minByOrNull { row -> row.playlistOrder }?.toResult(ContentType.Series) }
    }.rankedForSearch(query, limit)

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

    private companion object { const val MAX_RECENTS = 10 }
}
