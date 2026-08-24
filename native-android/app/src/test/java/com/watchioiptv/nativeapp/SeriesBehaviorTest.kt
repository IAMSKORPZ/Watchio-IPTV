package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.series.SeriesDetails
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
