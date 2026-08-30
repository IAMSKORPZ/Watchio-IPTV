package com.watchioiptv.nativeapp.data.series

import com.watchioiptv.nativeapp.BuildConfig
import com.watchioiptv.nativeapp.core.database.CategoryEntity
import com.watchioiptv.nativeapp.core.database.EpisodeEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.database.SeasonEntity
import com.watchioiptv.nativeapp.core.database.SeriesEntity
import com.watchioiptv.nativeapp.core.database.TmdbTrailerCacheEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer
import com.watchioiptv.nativeapp.core.util.TextNormalizer
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.xtream.XtreamApi
import com.watchioiptv.nativeapp.data.xtream.XtreamEpisodeDto
import com.watchioiptv.nativeapp.data.xtream.XtreamSeasonDto
import com.watchioiptv.nativeapp.data.xtream.XtreamSeriesInfoDto
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

class SeriesRepository(
    private val database: WatchioDatabase,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val playbackUrlResolver: PlaybackUrlResolver,
    private val credentialStore: ProviderCredentialStore,
    private val retrofitFactory: (String) -> Retrofit,
    private val tmdbRetrofitFactory: (String) -> Retrofit,
    private val clock: WatchioClock,
) {
    private var activeEpisode: WatchioEpisodeItem? = null
    @Volatile
    private var catalogCache: SeriesCatalogCache? = null
    private val cacheMutex = Mutex()
    private var searchWarmupJob: Job? = null
    private var catalogGeneration: Long = 0L
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun invalidateCache(providerId: ProviderId? = null) {
        val current = catalogCache
        if (providerId == null || current?.providerId == providerId) {
            searchWarmupJob?.cancel()
            searchWarmupJob = null
            catalogCache = null
        }
    }

    suspend fun selectedProviderId(): ProviderId? = settingsRepository.selectedProviderId.first()

    fun markActiveEpisode(episode: WatchioEpisodeItem) {
        activeEpisode = episode
    }

    fun currentActiveEpisode(): WatchioEpisodeItem? = activeEpisode

    fun clearActiveEpisode() {
        activeEpisode = null
    }

    suspend fun categories(providerId: ProviderId): List<SeriesCategory> = withContext(Dispatchers.IO) {
        listOf(
            SeriesCategory("all", "ALL SERIES", SeriesCategoryKind.All),
            SeriesCategory("continue_watching", "CONTINUE WATCHING", SeriesCategoryKind.ContinueWatching),
            SeriesCategory("favorites", "FAVOURITES", SeriesCategoryKind.Favorites),
            SeriesCategory("history", "HISTORY", SeriesCategoryKind.History),
        ) + database.categoryDao().getByType(providerId.value, ContentType.Series.persisted).map { it.toSeriesCategory() }
    }

    suspend fun getOrLoadCatalog(providerId: ProviderId): SeriesCatalogCache = withContext(Dispatchers.IO) {
        val current = catalogCache
        if (current != null && current.providerId == providerId) {
            return@withContext current
        }
        cacheMutex.withLock {
            val locked = catalogCache
            if (locked != null && locked.providerId == providerId) {
                locked
            } else {
                val provider = database.providerDao().findById(providerId.value)
                if (provider == null) {
                    SeriesCatalogCache(providerId, emptyList(), emptyMap(), emptyMap(), emptyMap())
                } else {
                    val providerType = ProviderType.fromPersisted(provider.type)
                    val rows = when (providerType) {
                        ProviderType.Xtream -> database.seriesDao().getByProvider(providerId.value).map { row ->
                            row.toSeries(providerType)
                        }
                        ProviderType.M3uUrl, ProviderType.M3uFile -> database.m3uItemDao().getByProviderAndType(providerId.value, ContentType.Series.persisted)
                            .groupBy { m3uSeriesId(it) }
                            .values
                            .mapNotNull { group -> group.minByOrNull { it.playlistOrder } }
                            .map { row -> row.toSeries(providerType) }
                    }
                    val lookup = rows.associateBy { it.id }
                    val categoryMap = rows.groupBy { it.categoryId.orEmpty() }

                    val newCache = SeriesCatalogCache(
                        providerId = providerId,
                        series = rows,
                        seriesLookup = lookup,
                        providerCategories = categoryMap,
                        searchIndex = emptyMap(),
                    )
                    catalogCache = newCache

                    // Launch cancellable background search index warmup
                    val currentGeneration = ++catalogGeneration
                    searchWarmupJob?.cancel()
                    searchWarmupJob = repositoryScope.launch {
                        val searchMap = buildMap(rows.size) {
                            for (item in rows) {
                                put(item.id, TextNormalizer.normalizeForSearch("${item.name} ${item.genre.orEmpty()}"))
                            }
                        }
                        if (catalogGeneration == currentGeneration) {
                            newCache.searchIndex = searchMap
                        }
                    }

                    newCache
                }
            }
        }
    }

    suspend fun seriesCards(providerId: ProviderId, category: SeriesCategory, query: String = ""): List<SeriesCardUiModel> = withContext(Dispatchers.IO) {
        val catalog = getOrLoadCatalog(providerId)
        val normalizedQuery = if (query.isNotBlank()) TextNormalizer.normalizeForSearch(query) else ""

        val searchIdx = catalog.searchIndex
        fun matchesQuery(item: WatchioSeriesItem): Boolean {
            if (normalizedQuery.isBlank()) return true
            val text = searchIdx[item.id] ?: TextNormalizer.normalizeForSearch("${item.name} ${item.genre.orEmpty()}")
            return text.contains(normalizedQuery)
        }

        when (category.kind) {
            SeriesCategoryKind.All -> {
                val list = if (normalizedQuery.isBlank()) catalog.series else catalog.series.filter { matchesQuery(it) }
                list.map { SeriesCardUiModel(series = it) }
            }
            SeriesCategoryKind.Provider -> {
                val catSeries = catalog.providerCategories[category.sourceCategoryId.orEmpty()].orEmpty()
                val list = if (normalizedQuery.isBlank()) catSeries else catSeries.filter { matchesQuery(it) }
                list.map { SeriesCardUiModel(series = it) }
            }
            SeriesCategoryKind.Favorites -> {
                val favorites = favoritesRepository.getFavorites(providerId).filter { it.contentType == ContentType.Series }.associateBy { it.contentId }
                val favList = catalog.series.filter { favorites.containsKey(it.id) }
                val list = if (normalizedQuery.isBlank()) favList else favList.filter { matchesQuery(it) }
                list.map { SeriesCardUiModel(series = it.copy(isFavorite = true)) }
            }
            SeriesCategoryKind.History -> {
                val histories = historyRepository.recent(providerId).filter { it.contentType == ContentType.Episode }
                val histList = histories.mapNotNull { h -> catalog.seriesLookup[h.contentId] }.distinctBy { it.id }
                val list = if (normalizedQuery.isBlank()) histList else histList.filter { matchesQuery(it) }
                list.map { SeriesCardUiModel(series = it) }
            }
            SeriesCategoryKind.ContinueWatching -> {
                if (normalizedQuery.isNotBlank()) {
                    val list = catalog.series.filter { matchesQuery(it) }
                    list.map { SeriesCardUiModel(series = it) }
                } else {
                    val histories = historyRepository.recent(providerId)
                        .filter { it.providerId == providerId }
                        .filter { it.contentType == ContentType.Episode }
                        .filter { !it.contentId.isNullOrBlank() && !it.subContentId.isNullOrBlank() }
                        .filter { shouldResumePosition(it.positionMs, it.durationMs) }
                        .filter { !isCompletedPosition(it.positionMs, it.durationMs) }
                        .sortedByDescending { it.lastWatchedAtEpochMs }

                    val candidateHistories = histories.distinctBy { it.contentId }
                    val episodeIds = candidateHistories.mapNotNull { it.subContentId }.distinct()
                    if (episodeIds.isEmpty()) {
                        emptyList()
                    } else {
                        val provider = database.providerDao().findById(providerId.value)
                        val providerType = provider?.let { ProviderType.fromPersisted(it.type) } ?: ProviderType.Xtream
                        val episodes: List<WatchioEpisodeItem> = when (providerType) {
                            ProviderType.Xtream -> {
                                episodeIds.chunked(400).flatMap { chunk ->
                                    database.episodeDao().getByIds(providerId.value, chunk).map { it.toEpisode(providerType, emptyList()) }
                                }
                            }
                            ProviderType.M3uUrl, ProviderType.M3uFile -> {
                                episodeIds.chunked(400).flatMap { chunk ->
                                    database.m3uItemDao().getByIds(providerId.value, chunk).map { item ->
                                        val sId = m3uSeriesId(item)
                                        WatchioEpisodeItem(
                                            providerId = providerId,
                                            providerType = providerType,
                                            seriesId = sId,
                                            episodeId = item.itemId,
                                            seasonNumber = item.seasonNumber ?: 1,
                                            episodeNumber = item.episodeNumber ?: item.playlistOrder,
                                            title = item.name,
                                            plot = null,
                                            imageUrl = item.tvgLogo,
                                            duration = null,
                                            durationSeconds = null,
                                            rating = null,
                                            releaseDate = null,
                                            containerExtension = item.directUrl.substringAfterLast('.', "").substringBefore('?').takeIf { it.isNotBlank() },
                                            tmdbId = null,
                                            directUrl = item.directUrl,
                                            headers = buildMap {
                                                item.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
                                                item.referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
                                            },
                                            resumePositionMs = null,
                                            resumeDurationMs = null,
                                        )
                                    }
                                }
                            }
                        }
                        val epLookup = episodes.associateBy { it.episodeId }

                        candidateHistories.mapNotNull { h ->
                            val series = catalog.seriesLookup[h.contentId] ?: return@mapNotNull null
                            val ep = epLookup[h.subContentId] ?: return@mapNotNull null

                            val seasonNum = ep.seasonNumber.takeIf { it > 0 }
                            val epNum = ep.episodeNumber.takeIf { it > 0 }
                            val epTitle = ep.title.ifBlank { h.title }
                            val label = formatEpisodeLabel(seasonNum, epNum, epTitle)
                            val progress = if (h.durationMs != null && h.durationMs > 0L && h.positionMs != null && h.positionMs > 0L) {
                                (h.positionMs.toFloat() / h.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else null

                            SeriesCardUiModel(
                                series = series,
                                isContinueWatching = true,
                                targetEpisodeId = h.subContentId,
                                seasonNumber = seasonNum,
                                episodeNumber = epNum,
                                episodeTitle = epTitle,
                                episodeLabel = label,
                                progress = progress,
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun series(providerId: ProviderId, category: SeriesCategory, query: String = ""): List<WatchioSeriesItem> =
        seriesCards(providerId, category, query).map { it.series }

    suspend fun item(providerId: ProviderId, seriesId: String): WatchioSeriesItem? {
        val catalog = getOrLoadCatalog(providerId)
        return catalog.seriesLookup[seriesId]
    }

    suspend fun details(series: WatchioSeriesItem): SeriesDetails = withContext(Dispatchers.IO) {
        val cachedSeries = database.seriesDao().find(series.providerId.value, series.id)
        val cachedSeasons = database.seriesDao().getSeasons(series.providerId.value, series.id)
        val cachedEpisodes = database.episodeDao().getBySeries(series.providerId.value, series.id)
        val hasFreshCache = cachedEpisodes.isNotEmpty() && cachedSeries != null && clock.nowEpochMs() - cachedSeries.updatedAtEpochMs < DETAIL_CACHE_MS
        if (!hasFreshCache && series.providerType == ProviderType.Xtream) {
            loadXtreamDetails(series)
        } else if (cachedEpisodes.isEmpty() && series.providerType != ProviderType.Xtream) {
            cacheM3uDetails(series)
        }
        val finalSeries = database.seriesDao().find(series.providerId.value, series.id) ?: cachedSeries
        val finalEpisodes = database.episodeDao().getBySeries(series.providerId.value, series.id)
        val finalSeasons = database.seriesDao().getSeasons(series.providerId.value, series.id)
        val histories = historyRepository.recent(series.providerId)
        val episodes = finalEpisodes.map { it.toEpisode(series.providerType, histories) }
        val seasons = synthesizeSeasons(series.providerId, series.id, finalSeasons, finalEpisodes)
        val providerTrailer = finalSeries?.trailer ?: series.trailerKey
        val trailer = providerTrailer?.takeIf { it.isNotBlank() } ?: (finalSeries?.tmdbId ?: series.tmdbId)?.let { tmdbTrailer(it) }
        SeriesDetails(
            series = series,
            title = finalSeries?.name ?: series.name,
            posterUrl = finalSeries?.coverUrl ?: series.coverUrl,
            backdropUrl = finalSeries?.backdropUrl,
            plot = finalSeries?.plot ?: series.plot,
            cast = finalSeries?.cast ?: series.cast,
            director = finalSeries?.director ?: series.director,
            genre = finalSeries?.genre ?: series.genre,
            releaseDate = finalSeries?.releaseDate ?: series.releaseDate,
            rating = finalSeries?.rating ?: series.rating,
            runtime = finalSeries?.runtime ?: series.runtime,
            trailerKey = trailer,
            tmdbId = finalSeries?.tmdbId ?: series.tmdbId,
            seasons = seasons,
            episodes = episodes,
        )
    }

    suspend fun playback(episode: WatchioEpisodeItem, resume: Boolean): EpisodePlaybackRequest {
        val url = when (episode.providerType) {
            ProviderType.Xtream -> playbackUrlResolver.resolve(
                PlaybackUrlRequest(episode.providerId, ContentType.Episode, episode.episodeId, episode.containerExtension?.let { ".$it" }),
            )
            ProviderType.M3uUrl, ProviderType.M3uFile -> episode.directUrl ?: throw IllegalStateException("Episode URL unavailable.")
        }
        val start = if (resume && shouldResume(episode.resumePositionMs, episode.resumeDurationMs)) MoviesRepository.clampedResumePosition(episode.resumePositionMs, episode.resumeDurationMs) else 0L
        return EpisodePlaybackRequest(episode, url, episode.headers, start)
    }

    fun shouldResume(positionMs: Long?, durationMs: Long?): Boolean = shouldResumePosition(positionMs, durationMs)
    fun isCompleted(positionMs: Long?, durationMs: Long?): Boolean = isCompletedPosition(positionMs, durationMs)

    private suspend fun loadXtreamDetails(series: WatchioSeriesItem) {
        val provider = database.providerDao().findById(series.providerId.value) ?: return
        val credentials = credentialStore.getXtreamCredentials(series.providerId.value) ?: return
        val base = provider.serverUrl ?: return
        val response = runCatching {
            retrofitFactory(base).create(XtreamApi::class.java)
                .seriesInfo(credentials.username, credentials.password, seriesId = series.id)
        }.getOrNull() ?: return
        val now = clock.nowEpochMs()
        val updated = response.info.toSeriesEntity(series, now)
        val episodes = response.episodes.flatMap { (seasonKey, list) ->
            val season = seasonKey.toIntOrNull() ?: 0
            list.mapIndexedNotNull { index, dto -> dto.toEpisodeEntity(series, season, index + 1, now) }
        }
        val seasons = synthesizeSeasonEntities(series.providerId, series.id, response.seasons, episodes, now)
        database.seriesDao().replaceDetails(series.providerId.value, updated, seasons, episodes)
    }

    private suspend fun cacheM3uDetails(series: WatchioSeriesItem) {
        val items = database.m3uItemDao().getByProviderAndType(series.providerId.value, ContentType.Series.persisted)
            .filter { m3uSeriesId(it) == series.id }
        val now = clock.nowEpochMs()
        val episodes = items.mapNotNull { item ->
            EpisodeEntity(
                providerId = item.providerId,
                seriesId = series.id,
                episodeId = item.itemId,
                seasonNumber = item.seasonNumber ?: 1,
                episodeNumber = item.episodeNumber ?: item.playlistOrder,
                title = item.name,
                normalizedTitle = item.normalizedName,
                imageUrl = item.tvgLogo,
                plot = null,
                containerExtension = item.directUrl.substringAfterLast('.', "").substringBefore('?').takeIf { it.isNotBlank() },
                duration = null,
                durationSecs = null,
                rating = null,
                releaseDate = null,
                tmdbId = null,
                directUrl = item.directUrl,
                userAgent = item.userAgent,
                referrer = item.referrer,
                updatedAtEpochMs = now,
            )
        }
        val current = database.seriesDao().find(series.providerId.value, series.id) ?: SeriesEntity(
            providerId = series.providerId.value,
            seriesId = series.id,
            name = series.name,
            normalizedName = TextNormalizer.normalizeForSearch(series.name),
            coverUrl = series.coverUrl,
            plot = series.plot,
            cast = series.cast,
            director = series.director,
            genre = series.genre,
            releaseDate = series.releaseDate,
            rating = series.rating,
            trailer = series.trailerKey,
            runtime = series.runtime,
            categoryId = series.categoryId,
            tmdbId = series.tmdbId,
            serverOrder = series.serverOrder,
            updatedAtEpochMs = now,
        ).also { database.seriesDao().upsertAll(listOf(it)) }
        database.seriesDao().replaceDetails(
            series.providerId.value,
            current.copy(updatedAtEpochMs = now),
            synthesizeSeasonEntities(series.providerId, series.id, emptyList(), episodes, now),
            episodes,
        )
    }

    private suspend fun tmdbTrailer(tmdbId: Int): String? {
        val now = clock.nowEpochMs()
        val cached = database.movieDetailDao().findTrailer(tmdbId.toString(), "tv")
        if (cached != null && now - cached.cachedAtEpochMs < TRAILER_CACHE_MS) return cached.trailerKey
        val apiKey = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val videos = tmdbRetrofitFactory(TMDB_BASE).create(TmdbApi::class.java).tvVideos(tmdbId, apiKey).results
            videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" && it.official == true }
                ?: videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                ?: videos.firstOrNull { it.site == "YouTube" }
        }.getOrNull()?.key?.also { database.movieDetailDao().upsertTrailer(TmdbTrailerCacheEntity(tmdbId.toString(), "tv", it, now)) }
    }

    private fun CategoryEntity.toSeriesCategory() = SeriesCategory(categoryId, name, SeriesCategoryKind.Provider, categoryId)

    private fun SeriesEntity.toSeries(providerType: ProviderType) = WatchioSeriesItem(
        providerId = ProviderId(providerId),
        providerType = providerType,
        id = seriesId,
        name = MediaTitleNormalizer.cleanTitle(name).displayTitle,
        coverUrl = coverUrl,
        categoryId = categoryId,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        rating = rating,
        runtime = runtime,
        trailerKey = trailer,
        tmdbId = tmdbId,
        serverOrder = serverOrder,
        isFavorite = false,
        lastEpisodeId = null,
        normalizedSearchText = "",
        formattedRating = com.watchioiptv.nativeapp.feature.movies.formatRating(rating),
    )

    private fun M3uItemEntity.toSeries(providerType: ProviderType): WatchioSeriesItem {
        val id = m3uSeriesId(this)
        val rawName = seriesName ?: name.substringBefore(" S").substringBefore(" Season").trim()
        val cleanTitle = MediaTitleNormalizer.cleanTitle(rawName).displayTitle
        return WatchioSeriesItem(
            providerId = ProviderId(providerId),
            providerType = providerType,
            id = id,
            name = cleanTitle,
            coverUrl = tvgLogo,
            categoryId = categoryId,
            plot = null,
            cast = null,
            director = null,
            genre = groupTitle,
            releaseDate = null,
            rating = null,
            runtime = null,
            trailerKey = null,
            tmdbId = null,
            serverOrder = playlistOrder,
            isFavorite = false,
            lastEpisodeId = null,
            normalizedSearchText = "",
            formattedRating = null,
        )
    }

    private fun XtreamSeriesInfoDto?.toSeriesEntity(series: WatchioSeriesItem, now: Long): SeriesEntity = SeriesEntity(
        providerId = series.providerId.value,
        seriesId = series.id,
        name = clean(this?.name) ?: series.name,
        normalizedName = TextNormalizer.normalizeForSearch(clean(this?.name) ?: series.name),
        coverUrl = clean(this?.coverBig) ?: clean(this?.cover) ?: series.coverUrl,
        plot = clean(this?.plot) ?: series.plot,
        cast = clean(this?.cast) ?: series.cast,
        director = clean(this?.director) ?: series.director,
        genre = clean(this?.genre) ?: series.genre,
        releaseDate = clean(this?.releaseDate) ?: clean(this?.releaseDateCamel) ?: series.releaseDate,
        rating = clean(this?.rating) ?: series.rating,
        rating5Based = clean(this?.rating5Based),
        trailer = clean(this?.youtubeTrailer) ?: series.trailerKey,
        runtime = clean(this?.episodeRunTime) ?: series.runtime,
        backdropUrl = this?.backdropPath?.firstOrNull(),
        categoryId = series.categoryId,
        tmdbId = this?.tmdbId ?: series.tmdbId,
        serverOrder = series.serverOrder,
        updatedAtEpochMs = now,
    )

    private fun XtreamEpisodeDto.toEpisodeEntity(series: WatchioSeriesItem, season: Int, fallbackEpisode: Int, now: Long): EpisodeEntity? {
        val epId = clean(id) ?: return null
        val titleValue = clean(title) ?: "Episode ${episodeNum ?: fallbackEpisode}"
        return EpisodeEntity(
            providerId = series.providerId.value,
            seriesId = series.id,
            episodeId = epId,
            seasonNumber = season,
            episodeNumber = episodeNum ?: fallbackEpisode,
            title = titleValue,
            normalizedTitle = TextNormalizer.normalizeForSearch(titleValue),
            imageUrl = clean(info?.movieImage),
            plot = clean(info?.plot),
            containerExtension = clean(containerExtension),
            duration = clean(info?.duration),
            durationSecs = info?.durationSecs,
            rating = clean(info?.rating),
            releaseDate = clean(info?.releaseDate),
            tmdbId = info?.tmdbId,
            directUrl = null,
            updatedAtEpochMs = now,
        )
    }

    private fun EpisodeEntity.toEpisode(providerType: ProviderType, histories: List<com.watchioiptv.nativeapp.domain.repository.HistoryItem>): WatchioEpisodeItem {
        val h = histories.firstOrNull { it.contentId == seriesId && it.subContentId == episodeId }
        return WatchioEpisodeItem(
            providerId = ProviderId(providerId),
            providerType = providerType,
            seriesId = seriesId,
            episodeId = episodeId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = title,
            plot = plot,
            imageUrl = imageUrl,
            duration = duration,
            durationSeconds = durationSecs,
            rating = rating,
            releaseDate = releaseDate,
            containerExtension = containerExtension,
            tmdbId = tmdbId,
            directUrl = directUrl,
            headers = buildMap {
                userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
                referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            },
            resumePositionMs = h?.positionMs,
            resumeDurationMs = h?.durationMs,
        )
    }

    private fun synthesizeSeasons(providerId: ProviderId, seriesId: String, seasons: List<SeasonEntity>, episodes: List<EpisodeEntity>): List<WatchioSeason> {
        return synthesizeSeasonEntities(providerId, seriesId, emptyList(), episodes, 0L)
            .associateBy { it.seasonNumber }
            .plus(seasons.associateBy { it.seasonNumber })
            .values
            .sortedBy { it.seasonNumber }
            .map {
                WatchioSeason(providerId, seriesId, it.seasonId ?: "season-${it.seasonNumber}", it.seasonNumber, it.name, it.airDate, it.episodeCount ?: 0, it.overview, it.coverBigUrl ?: it.coverUrl, it.rating)
            }
    }

    private fun synthesizeSeasonEntities(providerId: ProviderId, seriesId: String, seasons: List<XtreamSeasonDto>, episodes: List<EpisodeEntity>, now: Long): List<SeasonEntity> {
        val counts = episodes.groupingBy { it.seasonNumber }.eachCount()
        val real = seasons.mapNotNull { dto ->
            val number = dto.seasonNumber ?: return@mapNotNull null
            SeasonEntity(
                providerId = providerId.value,
                seriesId = seriesId,
                seasonNumber = number,
                name = clean(dto.name) ?: "Season $number",
                seasonId = clean(dto.id) ?: "season-$number",
                airDate = clean(dto.airDate),
                overview = clean(dto.overview),
                coverUrl = clean(dto.cover),
                coverBigUrl = clean(dto.coverBig),
                rating = null,
                episodeCount = dto.episodeCount ?: counts[number] ?: 0,
                updatedAtEpochMs = now,
            )
        }.associateBy { it.seasonNumber }
        val synthesized = counts.mapValues { (number, count) ->
            real[number] ?: SeasonEntity(providerId.value, seriesId, number, "Season $number", "season-$number", null, null, null, null, null, count, now)
        }
        return (real + synthesized).values.sortedBy { it.seasonNumber }
    }

    private fun m3uSeriesId(item: M3uItemEntity): String {
        val base = item.seriesName ?: item.name.substringBefore(" S").substringBefore(" Season").trim()
        return TextNormalizer.normalizeForSearch(base).replace(' ', '-').ifBlank { item.itemId }
    }

    private sealed interface SeriesRow {
        data class Xtream(val entity: SeriesEntity) : SeriesRow
        data class M3u(val entity: M3uItemEntity) : SeriesRow
    }

    @Serializable private data class TmdbVideosDto(val results: List<TmdbVideoDto> = emptyList())
    @Serializable private data class TmdbVideoDto(val key: String? = null, val site: String? = null, val type: String? = null, val official: Boolean? = null)
    private interface TmdbApi {
        @GET("tv/{tmdbId}/videos")
        suspend fun tvVideos(@Path("tmdbId") tmdbId: Int, @Query("api_key") apiKey: String): TmdbVideosDto
    }

    companion object {
        private const val SEARCH_LIMIT = 500
        private const val DETAIL_CACHE_MS = 24L * 60L * 60L * 1000L
        private const val TRAILER_CACHE_MS = 30L * 24L * 60L * 60L * 1000L
        private const val TMDB_BASE = "https://api.themoviedb.org/3/"
        fun shouldResumePosition(positionMs: Long?, durationMs: Long?): Boolean = MoviesRepository.shouldResumePosition(positionMs, durationMs)
        fun isCompletedPosition(positionMs: Long?, durationMs: Long?): Boolean = MoviesRepository.isCompletedPosition(positionMs, durationMs)
        fun clampedResumePosition(positionMs: Long?, durationMs: Long?): Long = MoviesRepository.clampedResumePosition(positionMs, durationMs)
        fun formatEpisodeLabel(seasonNumber: Int?, episodeNumber: Int?, episodeTitle: String?): String? {
            val s = seasonNumber?.takeIf { it > 0 }
            val e = episodeNumber?.takeIf { it > 0 }
            return when {
                s != null && e != null -> "S%02d • E%02d".format(s, e)
                e != null -> "E%02d".format(e)
                s != null -> "S%02d".format(s)
                !episodeTitle.isNullOrBlank() -> episodeTitle.trim()
                else -> null
            }
        }
        private fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "[]" && it != "{}" }
    }
}
