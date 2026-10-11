package com.iamskorpz.watchioiptv.data.library

import com.iamskorpz.watchioiptv.core.database.CatalogSearchRow
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.util.MediaTitleNormalizer
import com.iamskorpz.watchioiptv.core.util.TextNormalizer
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.system.measureNanoTime

enum class SearchScope { Global, Live, Movies, Series }

data class SearchPhaseResult(
    val results: SearchResults,
    val cacheWarm: Boolean,
    val candidateCount: Int,
    val preparationMs: Double,
    val rankingMs: Double,
)

private data class IndexedSearchResult(
    val result: WatchioSearchResult,
    val title: PreparedSearchText,
    val subtitle: PreparedSearchText?,
    val sourceOrder: Int,
)

private data class ProviderSearchSnapshot(
    val providerId: ProviderId,
    val live: List<IndexedSearchResult>,
    val movies: List<IndexedSearchResult>,
    val series: List<IndexedSearchResult>,
) {
    fun items(scope: SearchScope): List<IndexedSearchResult> = when (scope) {
        SearchScope.Live -> live
        SearchScope.Movies -> movies
        SearchScope.Series -> series
        SearchScope.Global -> live + movies + series
    }
}

class SearchRepository(
    private val database: WatchioDatabase,
    private val settingsRepository: SettingsRepository,
    private val historyStore: SearchHistoryStore = EmptySearchHistoryStore,
) {
    @Volatile private var snapshot: ProviderSearchSnapshot? = null
    private val snapshotMutex = Mutex()

    val activeProviderId: Flow<ProviderId?> = settingsRepository.selectedProviderId
    suspend fun selectedProviderId(): ProviderId? = settingsRepository.selectedProviderId.first()

    fun invalidate(providerId: ProviderId? = null) {
        val current = snapshot
        if (providerId == null || current?.providerId == providerId) snapshot = null
    }

    suspend fun warm(providerId: ProviderId): SearchPhaseResult = searchPhase(
        providerId = providerId,
        query = "",
        scope = SearchScope.Global,
        includeFuzzy = false,
        limitPerType = 0,
    ).also { logPerformance("warm", SearchScope.Global, it) }

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

    suspend fun search(query: String, scope: SearchScope, limitPerType: Int = 40): SearchResults {
        val providerId = selectedProviderId() ?: return SearchResults()
        return search(providerId, query, scope, limitPerType)
    }

    suspend fun search(providerId: ProviderId, query: String, scope: SearchScope, limitPerType: Int = 40): SearchResults =
        searchPhase(providerId, query, scope, includeFuzzy = true, limitPerType = limitPerType).results

    suspend fun searchPhase(
        providerId: ProviderId,
        query: String,
        scope: SearchScope,
        includeFuzzy: Boolean,
        limitPerType: Int = 500,
        fuzzyOnly: Boolean = false,
    ): SearchPhaseResult = withContext(Dispatchers.Default) {
        val wasWarm = snapshot?.providerId == providerId
        lateinit var catalog: ProviderSearchSnapshot
        val preparationNs = measureNanoTime { catalog = getOrLoadSnapshot(providerId) }
        val candidates = catalog.items(scope)
        val normalized = prepareSearchText(query)
        if (normalized.normalized.isBlank() || limitPerType == 0) {
            return@withContext SearchPhaseResult(
                results = SearchResults(),
                cacheWarm = wasWarm,
                candidateCount = candidates.size,
                preparationMs = preparationNs / 1_000_000.0,
                rankingMs = 0.0,
            )
        }
        lateinit var results: SearchResults
        val rankingNs = measureNanoTime {
            val ranked = rank(candidates, normalized, includeFuzzy, fuzzyOnly)
            results = SearchResults(
                live = ranked.filter { it.result.contentType == ContentType.Live }.take(limitPerType).map(::displayResult),
                movies = ranked.filter { it.result.contentType == ContentType.Movie }.take(limitPerType).map(::displayResult),
                series = ranked.filter { it.result.contentType == ContentType.Series }.take(limitPerType).map(::displayResult),
            )
        }
        SearchPhaseResult(
            results = results,
            cacheWarm = wasWarm,
            candidateCount = candidates.size,
            preparationMs = preparationNs / 1_000_000.0,
            rankingMs = rankingNs / 1_000_000.0,
        )
    }

    suspend fun searchProgressively(
        providerId: ProviderId,
        query: String,
        scope: SearchScope,
        limitPerType: Int = 500,
        onStrong: suspend (SearchPhaseResult) -> Unit,
    ): SearchPhaseResult {
        val strong = searchPhase(providerId, query, scope, includeFuzzy = false, limitPerType = limitPerType)
        logPerformance("strong", scope, strong)
        onStrong(strong)
        // StateFlow conflates back-to-back values. Leave one TV/phone frame so
        // Compose can draw strong matches before fuzzy completion replaces it.
        delay(PROGRESSIVE_FRAME_MS)
        val fuzzy = searchPhase(
            providerId = providerId,
            query = query,
            scope = scope,
            includeFuzzy = true,
            limitPerType = limitPerType,
            fuzzyOnly = true,
        )
        logPerformance("fuzzy", scope, fuzzy)
        return fuzzy.copy(
            results = SearchResults(
                live = (strong.results.live + fuzzy.results.live).distinctBy(WatchioSearchResult::contentId).take(limitPerType),
                movies = (strong.results.movies + fuzzy.results.movies).distinctBy(WatchioSearchResult::contentId).take(limitPerType),
                series = (strong.results.series + fuzzy.results.series).distinctBy(WatchioSearchResult::contentId).take(limitPerType),
            ),
            cacheWarm = strong.cacheWarm,
            candidateCount = strong.candidateCount,
            preparationMs = strong.preparationMs + fuzzy.preparationMs,
            rankingMs = strong.rankingMs + fuzzy.rankingMs,
        )
    }

    private suspend fun getOrLoadSnapshot(providerId: ProviderId): ProviderSearchSnapshot {
        snapshot?.takeIf { it.providerId == providerId }?.let { return it }
        return snapshotMutex.withLock {
            snapshot?.takeIf { it.providerId == providerId }?.let { return@withLock it }
            val provider = database.providerDao().findById(providerId.value)
                ?: return@withLock ProviderSearchSnapshot(providerId, emptyList(), emptyList(), emptyList()).also { snapshot = it }
            val providerType = ProviderType.fromPersisted(provider.type)
            val built = coroutineScope {
                val liveRows = async(Dispatchers.IO) { rows(providerId, providerType, ContentType.Live) }
                val movieRows = async(Dispatchers.IO) { rows(providerId, providerType, ContentType.Movie) }
                val seriesRows = async(Dispatchers.IO) { rows(providerId, providerType, ContentType.Series) }
                ProviderSearchSnapshot(
                    providerId = providerId,
                    live = prepare(providerId, ContentType.Live, liveRows.await(), providerType),
                    movies = prepare(providerId, ContentType.Movie, movieRows.await(), providerType),
                    series = prepare(providerId, ContentType.Series, seriesRows.await(), providerType),
                )
            }
            snapshot = built
            built
        }
    }

    private suspend fun rows(providerId: ProviderId, providerType: ProviderType, type: ContentType): List<CatalogSearchRow> =
        when (providerType) {
            ProviderType.Xtream -> when (type) {
                ContentType.Live -> database.liveStreamDao().getSearchRows(providerId.value)
                ContentType.Movie -> database.vodDao().getSearchRows(providerId.value)
                ContentType.Series -> database.seriesDao().getSearchRows(providerId.value)
                else -> emptyList()
            }
            ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().getSearchRowsByType(providerId.value, type.persisted)
        }

    private suspend fun prepare(
        providerId: ProviderId,
        contentType: ContentType,
        rows: List<CatalogSearchRow>,
        providerType: ProviderType,
    ): List<IndexedSearchResult> {
        val sourceRows = if (contentType == ContentType.Series && providerType != ProviderType.Xtream) {
            rows.groupBy { row -> m3uSeriesTitle(row) }.values.mapNotNull { group -> group.minByOrNull(CatalogSearchRow::sourceOrder) }
        } else rows
        return sourceRows.mapIndexed { index, row ->
            if (index and 127 == 0) currentCoroutineContext().ensureActive()
            val rawTitle = if (contentType == ContentType.Series && providerType != ProviderType.Xtream) m3uSeriesTitle(row) else row.title
            val preparedTitle = if (
                contentType != ContentType.Live &&
                !(contentType == ContentType.Series && providerType != ProviderType.Xtream) &&
                row.normalizedTitle.isNotBlank()
            ) {
                PreparedSearchText(row.normalizedTitle, row.normalizedTitle.split(' ').filter(String::isNotBlank))
            } else {
                prepareSearchText(rawTitle)
            }
            val id = if (contentType == ContentType.Series && providerType != ProviderType.Xtream) {
                TextNormalizer.normalizeForSearch(rawTitle).replace(' ', '-').ifBlank { row.contentId }
            } else row.contentId
            val result = WatchioSearchResult(
                providerId = providerId,
                contentType = contentType,
                contentId = id,
                title = rawTitle,
                subtitle = row.subtitle,
                imageUrl = row.imageUrl,
                year = null,
                rating = row.rating,
            )
            IndexedSearchResult(
                result = result,
                title = preparedTitle,
                subtitle = row.subtitle?.takeIf(String::isNotBlank)?.let(::prepareSearchText),
                sourceOrder = row.sourceOrder,
            )
        }
    }

    private suspend fun rank(
        candidates: List<IndexedSearchResult>,
        query: PreparedSearchText,
        includeFuzzy: Boolean,
        fuzzyOnly: Boolean,
    ): List<IndexedSearchResult> {
        val matched = ArrayList<Pair<IndexedSearchResult, SearchRank>>()
        candidates.forEachIndexed { index, item ->
            if (index and 127 == 0) currentCoroutineContext().ensureActive()
            rankPreparedSearchText(item.title, item.subtitle, query, includeFuzzy)?.let { rank ->
                if (!fuzzyOnly || rank.type == SearchMatchType.Fuzzy) matched += item to rank
            }
        }
        return matched
            .sortedWith(compareBy<Pair<IndexedSearchResult, SearchRank>> { it.second.type.rank }
                .thenBy { it.second.distance }
                .thenBy { it.first.sourceOrder })
            .map { it.first }
            .distinctBy { Triple(it.result.providerId, it.result.contentType, it.result.contentId) }
    }

    private fun displayResult(item: IndexedSearchResult): WatchioSearchResult {
        if (item.result.contentType == ContentType.Live) return item.result
        val clean = MediaTitleNormalizer.cleanTitle(item.result.title)
        return item.result.copy(title = clean.displayTitle, year = clean.detectedYear)
    }

    private fun m3uSeriesTitle(row: CatalogSearchRow): String =
        row.seriesName?.takeIf(String::isNotBlank)
            ?: row.title.substringBefore(" S").substringBefore(" Season").trim()

    private fun logPerformance(stage: String, scope: SearchScope, result: SearchPhaseResult) {
        if (!com.iamskorpz.watchioiptv.BuildConfig.DEBUG) return
        android.util.Log.d(
            "WatchioSearchPerf",
            "stage=$stage scope=${scope.name} warm=${result.cacheWarm} candidates=${result.candidateCount} " +
                "prepareMs=${"%.1f".format(java.util.Locale.ROOT, result.preparationMs)} " +
                "rankMs=${"%.1f".format(java.util.Locale.ROOT, result.rankingMs)} " +
                "results=${result.results.live.size + result.results.movies.size + result.results.series.size}",
        )
    }

    private companion object {
        const val MAX_RECENTS = 10
        const val PROGRESSIVE_FRAME_MS = 120L
    }
}
