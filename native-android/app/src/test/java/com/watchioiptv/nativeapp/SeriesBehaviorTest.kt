package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.series.SeriesDetails
import com.watchioiptv.nativeapp.data.series.SeriesRepository
import com.watchioiptv.nativeapp.data.series.WatchioEpisodeItem
import com.watchioiptv.nativeapp.data.series.WatchioSeriesItem
import com.watchioiptv.nativeapp.feature.series.SeriesDetailsUiState
import com.watchioiptv.nativeapp.data.xtream.XtreamPlaybackUrlResolver
import com.watchioiptv.nativeapp.data.xtream.XtreamSeriesInfoResponseDto
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesBehaviorTest {
    @Test
    fun seriesInfoAcceptsFlexibleTypesAndGroupedEpisodes() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<XtreamSeriesInfoResponseDto>(
            """
            {
              "info":{"name":"Show","tmdb_id":"55","backdrop_path":"http://example.invalid/back.jpg"},
              "seasons":[{"season_number":"1","episode_count":"2","name":"Season 1"}],
              "episodes":{"1":[{"id":123,"episode_num":"1","title":"Pilot","container_extension":"mp4","info":{"duration_secs":"42","rating":8.5}}]}
            }
            """.trimIndent(),
        )

        assertEquals(55, dto.info?.tmdbId)
        assertEquals(listOf("http://example.invalid/back.jpg"), dto.info?.backdropPath)
        assertEquals(1, dto.seasons.single().seasonNumber)
        assertEquals("123", dto.episodes.getValue("1").single().id)
        assertEquals(42, dto.episodes.getValue("1").single().info?.durationSecs)
    }

    @Test
    fun episodeResolverUsesEpisodeIdAndMasksSecrets() = runTest {
        val secrets = ProviderCredentialStore(FakeSecretStore())
        secrets.saveXtreamCredentials("provider-a", XtreamCredentials("user", "pass"))
        val resolver = XtreamPlaybackUrlResolver(FakeProviderRepository(), secrets, FakeSettingsRepository())

        val url = resolver.resolve(PlaybackUrlRequest(ProviderId("provider-a"), ContentType.Episode, "12345", ".mkv"))

        assertEquals("http://example.invalid/series/user/pass/12345.mkv", url)
        val masked = SensitiveUrlMasker.mask(url)
        assertFalse(masked, masked.contains("user"))
        assertFalse(masked, masked.contains("pass"))
    }

    @Test
    fun episodeResumeUsesVodThresholds() {
        assertFalse(MoviesRepository.shouldResumePosition(20_000, 600_000))
        assertTrue(MoviesRepository.shouldResumePosition(120_000, 600_000))
        assertFalse(MoviesRepository.shouldResumePosition(590_000, 600_000))
    }

    @Test
    fun detailsStateExposesOnlyMeaningfulEpisodeResume() {
        val base = episode("episode-1", positionMs = 20_000, durationMs = 600_000)
        assertEquals(null, stateWith(base).resumeEpisode)

        val resumable = episode("episode-2", positionMs = 120_000, durationMs = 600_000)
        assertEquals("episode-2", stateWith(resumable).resumeEpisode?.episodeId)

        val complete = episode("episode-3", positionMs = 590_000, durationMs = 600_000)
        assertEquals(null, stateWith(complete).resumeEpisode)
    }

    @Test
    fun episodeLabelFormattingHandlesPaddedAndFallbackValues() {
        assertEquals("S02 • E04", SeriesRepository.formatEpisodeLabel(2, 4, "Episode 4"))
        assertEquals("E04", SeriesRepository.formatEpisodeLabel(0, 4, "Episode 4"))
        assertEquals("E04", SeriesRepository.formatEpisodeLabel(null, 4, "Episode 4"))
        assertEquals("S01", SeriesRepository.formatEpisodeLabel(1, 0, "Special"))
        assertEquals("Pilot", SeriesRepository.formatEpisodeLabel(0, 0, "Pilot"))
        assertEquals("Pilot", SeriesRepository.formatEpisodeLabel(null, null, "Pilot"))
        assertEquals(null, SeriesRepository.formatEpisodeLabel(0, 0, null))
        assertEquals(null, SeriesRepository.formatEpisodeLabel(null, null, ""))
    }

    @Test
    fun detailsStatePrioritizesTargetEpisodeId() {
        val ep1 = episode("ep-1", positionMs = 120_000, durationMs = 600_000)
        val ep2 = episode("ep-2", positionMs = 240_000, durationMs = 600_000)
        val baseState = SeriesDetailsUiState(
            loading = false,
            targetEpisodeId = "ep-2",
            details = SeriesDetails(
                series = WatchioSeriesItem(
                    providerId = ProviderId("provider-a"),
                    providerType = ProviderType.Xtream,
                    id = "series-1",
                    name = "Show",
                    coverUrl = null,
                    categoryId = null,
                    plot = null,
                    cast = null,
                    director = null,
                    genre = null,
                    releaseDate = null,
                    rating = null,
                    runtime = null,
                    trailerKey = null,
                    tmdbId = null,
                    serverOrder = 0,
                    isFavorite = false,
                    lastEpisodeId = null,
                ),
                title = "Show",
                posterUrl = null,
                backdropUrl = null,
                plot = null,
                cast = null,
                director = null,
                genre = null,
                releaseDate = null,
                rating = null,
                runtime = null,
                trailerKey = null,
                tmdbId = null,
                seasons = emptyList(),
                episodes = listOf(ep1, ep2),
            ),
        )

        assertEquals("ep-2", baseState.resumeEpisode?.episodeId)
    }

    @Test
    fun continueWatchingDeduplicationPreservesLatestActivityAcrossSeasons() {
        val series1 = WatchioSeriesItem(
            providerId = ProviderId("provider-a"),
            providerType = ProviderType.Xtream,
            id = "series-1",
            name = "Breaking Bad",
            coverUrl = null,
            categoryId = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            runtime = null,
            trailerKey = null,
            tmdbId = null,
            serverOrder = 0,
            isFavorite = false,
            lastEpisodeId = null,
        )
        val epS03E02 = WatchioEpisodeItem(
            providerId = ProviderId("provider-a"),
            providerType = ProviderType.Xtream,
            seriesId = "series-1",
            episodeId = "ep-s03e02",
            seasonNumber = 3,
            episodeNumber = 2,
            title = "Caballo Sin Nombre",
            plot = null,
            imageUrl = null,
            duration = null,
            durationSeconds = null,
            rating = null,
            releaseDate = null,
            containerExtension = "mp4",
            tmdbId = null,
            directUrl = null,
            headers = emptyMap(),
            resumePositionMs = 120_000,
            resumeDurationMs = 600_000,
        )
        val epS01E08 = WatchioEpisodeItem(
            providerId = ProviderId("provider-a"),
            providerType = ProviderType.Xtream,
            seriesId = "series-1",
            episodeId = "ep-s01e08",
            seasonNumber = 1,
            episodeNumber = 8,
            title = "Pilot",
            plot = null,
            imageUrl = null,
            duration = null,
            durationSeconds = null,
            rating = null,
            releaseDate = null,
            containerExtension = "mp4",
            tmdbId = null,
            directUrl = null,
            headers = emptyMap(),
            resumePositionMs = 150_000,
            resumeDurationMs = 600_000,
        )
        val catalog = com.watchioiptv.nativeapp.data.series.SeriesCatalogCache(
            providerId = ProviderId("provider-a"),
            series = listOf(series1),
            seriesLookup = mapOf("series-1" to series1),
            providerCategories = emptyMap(),
            searchIndex = emptyMap(),
        )

        // S03E02 watched yesterday (epoch 1000), S01E08 watched today (epoch 2000)
        val histories = listOf(
            com.watchioiptv.nativeapp.domain.repository.HistoryItem(
                providerId = ProviderId("provider-a"),
                contentType = ContentType.Episode,
                contentId = "series-1",
                subContentId = "ep-s01e08",
                title = "Episode 8",
                positionMs = 150_000,
                durationMs = 600_000,
                lastWatchedAtEpochMs = 2000,
            ),
            com.watchioiptv.nativeapp.domain.repository.HistoryItem(
                providerId = ProviderId("provider-a"),
                contentType = ContentType.Episode,
                contentId = "series-1",
                subContentId = "ep-s03e02",
                title = "Episode 2",
                positionMs = 120_000,
                durationMs = 600_000,
                lastWatchedAtEpochMs = 1000,
            ),
        )

        val candidateHistories = histories
            .filter { it.providerId == ProviderId("provider-a") }
            .filter { it.contentType == ContentType.Episode }
            .filter { !it.contentId.isNullOrBlank() && !it.subContentId.isNullOrBlank() }
            .filter { SeriesRepository.shouldResumePosition(it.positionMs, it.durationMs) }
            .filter { !SeriesRepository.isCompletedPosition(it.positionMs, it.durationMs) }
            .sortedByDescending { it.lastWatchedAtEpochMs }
            .distinctBy { it.contentId }

        val episodeIds = candidateHistories.mapNotNull { it.subContentId }.distinct()
        val allEpisodes = listOf(epS03E02, epS01E08)
        val epLookup = allEpisodes.filter { episodeIds.contains(it.episodeId) }.associateBy { it.episodeId }

        val continueWatching = candidateHistories.mapNotNull { h ->
            val series = catalog.seriesLookup[h.contentId] ?: return@mapNotNull null
            val ep = epLookup[h.subContentId] ?: return@mapNotNull null

            val seasonNum = ep.seasonNumber.takeIf { it > 0 }
            val epNum = ep.episodeNumber.takeIf { it > 0 }
            val epTitle = ep.title.ifBlank { h.title }
            val label = SeriesRepository.formatEpisodeLabel(seasonNum, epNum, epTitle)
            val progress = if (h.durationMs != null && h.durationMs > 0L && h.positionMs != null && h.positionMs > 0L) {
                (h.positionMs.toFloat() / h.durationMs.toFloat()).coerceIn(0f, 1f)
            } else null

            com.watchioiptv.nativeapp.data.series.SeriesCardUiModel(
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

        assertEquals(1, continueWatching.size)
        val selected = continueWatching.single()
        assertEquals("ep-s01e08", selected.targetEpisodeId)
        assertEquals("S01 • E08", selected.episodeLabel)
        assertEquals(0.25f, selected.progress ?: 0f, 0.001f)
    }

    @Test
    fun continueWatchingEpisodeBatchingChunksIds() {
        val episodeIds = (1..950).map { "ep-$it" }
        val chunks = episodeIds.chunked(400)
        assertEquals(3, chunks.size)
        assertEquals(400, chunks[0].size)
        assertEquals(400, chunks[1].size)
        assertEquals(150, chunks[2].size)
    }

    @Test
    fun seriesCatalogSnapshotContainsNoEpisodes() {
        val cache = com.watchioiptv.nativeapp.data.series.SeriesCatalogCache(
            providerId = ProviderId("p1"),
            series = emptyList(),
            seriesLookup = emptyMap(),
            providerCategories = emptyMap(),
            searchIndex = emptyMap(),
        )
        assertEquals(0, cache.series.size)
        assertEquals(0, cache.seriesLookup.size)
    }

    @Test
    fun nextEpisodeResolutionHandlesSameSeasonProgression() {
        val ep1 = episodeWithSeason("ep-1", season = 1, number = 1)
        val ep2 = episodeWithSeason("ep-2", season = 1, number = 2)
        val ep3 = episodeWithSeason("ep-3", season = 1, number = 3)
        val episodes = listOf(ep1, ep2, ep3)

        val nextOf1 = resolveNextFromList(episodes, currentId = "ep-1")
        assertEquals("ep-2", nextOf1?.episodeId)

        val nextOf2 = resolveNextFromList(episodes, currentId = "ep-2")
        assertEquals("ep-3", nextOf2?.episodeId)
    }

    @Test
    fun nextEpisodeResolutionHandlesCrossSeasonProgression() {
        val s1e1 = episodeWithSeason("s1-ep1", season = 1, number = 1)
        val s1e2 = episodeWithSeason("s1-ep2", season = 1, number = 2)
        val s2e1 = episodeWithSeason("s2-ep1", season = 2, number = 1)
        val s2e2 = episodeWithSeason("s2-ep2", season = 2, number = 2)
        val episodes = listOf(s1e1, s1e2, s2e1, s2e2)

        val nextOfS1Final = resolveNextFromList(episodes, currentId = "s1-ep2")
        assertEquals("s2-ep1", nextOfS1Final?.episodeId)
        assertEquals(2, nextOfS1Final?.seasonNumber)
        assertEquals(1, nextOfS1Final?.episodeNumber)
    }

    @Test
    fun nextEpisodeResolutionReturnsNullOnFinalEpisode() {
        val s1e1 = episodeWithSeason("s1-ep1", season = 1, number = 1)
        val s2e1 = episodeWithSeason("s2-ep1", season = 2, number = 1)
        val episodes = listOf(s1e1, s2e1)

        val nextOfFinal = resolveNextFromList(episodes, currentId = "s2-ep1")
        assertEquals(null, nextOfFinal)
    }

    @Test
    fun nextEpisodeResolutionDeduplicatesDuplicateIdsAndPreventsSelfTransitions() {
        val ep1 = episodeWithSeason("ep-1", season = 1, number = 1)
        val ep1Dup = episodeWithSeason("ep-1", season = 1, number = 1)
        val ep2 = episodeWithSeason("ep-2", season = 1, number = 2)
        val episodes = listOf(ep1, ep1Dup, ep2)

        val next = resolveNextFromList(episodes, currentId = "ep-1")
        assertEquals("ep-2", next?.episodeId)
    }

    @Test
    fun nextEpisodeResolutionHandlesSpecialsAndSeasonZeroSafely() {
        val special = episodeWithSeason("special-1", season = 0, number = 1)
        val s1e1 = episodeWithSeason("s1-ep1", season = 1, number = 1)
        val s1e2 = episodeWithSeason("s1-ep2", season = 1, number = 2)
        val episodes = listOf(special, s1e1, s1e2)

        // Normal S1E1 proceeds to S1E2, not to S00 special
        val next = resolveNextFromList(episodes, currentId = "s1-ep1")
        assertEquals("s1-ep2", next?.episodeId)
    }

    @Test
    fun previousEpisodeResolutionHandlesSameAndCrossSeason() {
        val s1e1 = episodeWithSeason("s1-ep1", season = 1, number = 1)
        val s1e2 = episodeWithSeason("s1-ep2", season = 1, number = 2)
        val s2e1 = episodeWithSeason("s2-ep1", season = 2, number = 1)
        val episodes = listOf(s1e1, s1e2, s2e1)

        val prevOfS2 = resolvePreviousFromList(episodes, currentId = "s2-ep1")
        assertEquals("s1-ep2", prevOfS2?.episodeId)

        val prevOfFirst = resolvePreviousFromList(episodes, currentId = "s1-ep1")
        assertEquals(null, prevOfFirst)
    }

    private fun episodeWithSeason(id: String, season: Int, number: Int): WatchioEpisodeItem = WatchioEpisodeItem(
        providerId = ProviderId("provider-a"),
        providerType = ProviderType.Xtream,
        seriesId = "series-1",
        episodeId = id,
        seasonNumber = season,
        episodeNumber = number,
        title = "Episode $number",
        plot = null,
        imageUrl = null,
        duration = null,
        durationSeconds = null,
        rating = null,
        releaseDate = null,
        containerExtension = "mp4",
        tmdbId = null,
        directUrl = null,
        headers = emptyMap(),
        resumePositionMs = null,
        resumeDurationMs = null,
    )

    private fun canonicalEpisodes(raw: List<WatchioEpisodeItem>): List<WatchioEpisodeItem> {
        return raw
            .distinctBy { it.episodeId }
            .sortedWith(
                compareBy<WatchioEpisodeItem> { ep ->
                    if (ep.seasonNumber > 0) ep.seasonNumber else Int.MAX_VALUE - 1000 + ep.seasonNumber
                }.thenBy { ep ->
                    if (ep.episodeNumber > 0) ep.episodeNumber else Int.MAX_VALUE - 1000 + ep.episodeNumber
                }
            )
    }

    private fun resolveNextFromList(episodes: List<WatchioEpisodeItem>, currentId: String): WatchioEpisodeItem? {
        val canonical = canonicalEpisodes(episodes)
        val idx = canonical.indexOfFirst { it.episodeId == currentId }
        if (idx < 0 || idx >= canonical.size - 1) return null
        val next = canonical[idx + 1]
        return if (next.episodeId != currentId) next else null
    }

    private fun resolvePreviousFromList(episodes: List<WatchioEpisodeItem>, currentId: String): WatchioEpisodeItem? {
        val canonical = canonicalEpisodes(episodes)
        val idx = canonical.indexOfFirst { it.episodeId == currentId }
        if (idx <= 0) return null
        val prev = canonical[idx - 1]
        return if (prev.episodeId != currentId) prev else null
    }

    @Test
    fun formatSeriesMetaLineCombinesValidValuesCleanly() {
        val baseSeries = WatchioSeriesItem(
            providerId = ProviderId("provider-a"),
            providerType = ProviderType.Xtream,
            id = "series-1",
            name = "Breaking Bad",
            coverUrl = null,
            categoryId = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            runtime = null,
            trailerKey = null,
            tmdbId = null,
            serverOrder = 0,
            isFavorite = false,
            lastEpisodeId = null,
        )
        val fullDetails = SeriesDetails(
            series = baseSeries,
            title = "Breaking Bad",
            posterUrl = null,
            backdropUrl = null,
            plot = "A chemistry teacher diagnosed with inoperable lung cancer...",
            cast = "Bryan Cranston, Aaron Paul",
            director = "Vince Gilligan",
            genre = "Crime, Drama, Thriller",
            releaseDate = "2008-01-20",
            rating = "9.5",
            runtime = "49 min",
            trailerKey = null,
            tmdbId = null,
            seasons = emptyList(),
            episodes = emptyList(),
        )

        val meta = com.watchioiptv.nativeapp.feature.series.formatSeriesMetaLine(fullDetails)
        assertEquals("2008 • Crime, Drama, Thriller • ★ 9.5", meta)

        val partialDetails = fullDetails.copy(
            releaseDate = null,
            genre = "Drama",
            rating = null,
        )
        assertEquals("Drama", com.watchioiptv.nativeapp.feature.series.formatSeriesMetaLine(partialDetails))

        val emptyDetails = fullDetails.copy(
            releaseDate = "",
            genre = null,
            rating = "0",
        )
        assertEquals("", com.watchioiptv.nativeapp.feature.series.formatSeriesMetaLine(emptyDetails))
    }

    private fun stateWith(episode: WatchioEpisodeItem): SeriesDetailsUiState = SeriesDetailsUiState(
        loading = false,
        details = SeriesDetails(
            series = WatchioSeriesItem(
                providerId = ProviderId("provider-a"),
                providerType = ProviderType.Xtream,
                id = "series-1",
                name = "Show",
                coverUrl = null,
                categoryId = null,
                plot = null,
                cast = null,
                director = null,
                genre = null,
                releaseDate = null,
                rating = null,
                runtime = null,
                trailerKey = null,
                tmdbId = null,
                serverOrder = 0,
                isFavorite = false,
                lastEpisodeId = null,
            ),
            title = "Show",
            posterUrl = null,
            backdropUrl = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            runtime = null,
            trailerKey = null,
            tmdbId = null,
            seasons = emptyList(),
            episodes = listOf(episode),
        ),
    )

    private fun episode(id: String, positionMs: Long?, durationMs: Long?): WatchioEpisodeItem = WatchioEpisodeItem(
        providerId = ProviderId("provider-a"),
        providerType = ProviderType.Xtream,
        seriesId = "series-1",
        episodeId = id,
        seasonNumber = 1,
        episodeNumber = 1,
        title = "Episode",
        plot = null,
        imageUrl = null,
        duration = null,
        durationSeconds = null,
        rating = null,
        releaseDate = null,
        containerExtension = "mp4",
        tmdbId = null,
        directUrl = null,
        headers = emptyMap(),
        resumePositionMs = positionMs,
        resumeDurationMs = durationMs,
    )

    private class FakeSecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun putSecret(key: String, value: String) { values[key] = value }
        override suspend fun getSecret(key: String): String? = values[key]
        override suspend fun removeSecret(key: String) { values.remove(key) }
    }

    private class FakeProviderRepository : ProviderRepository {
        override fun observeProviders(): Flow<List<WatchioProvider>> = flowOf(emptyList())
        override suspend fun getProviders(): List<WatchioProvider> = emptyList()
        override suspend fun getProvider(providerId: ProviderId): WatchioProvider = WatchioProvider(
            providerId, "Provider", ProviderType.Xtream, "http://example.invalid", 1, 1, null, true,
        )
        override suspend fun saveProvider(provider: WatchioProvider) = Unit
        override suspend fun deleteProvider(providerId: ProviderId) = Unit
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val selectedProviderId: Flow<ProviderId?> = flowOf(null)
        override val inputMode: Flow<InputMode> = flowOf(InputMode.Auto)
        override val streamFormat: Flow<StreamFormat> = flowOf(StreamFormat.Auto)
        override suspend fun setSelectedProviderId(providerId: ProviderId?) = Unit
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit
    }
}
