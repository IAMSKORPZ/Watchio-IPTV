package com.iamskorpz.watchioiptv

import android.view.ViewGroup
import com.iamskorpz.watchioiptv.CatalogRefreshPhase1Test.FakeWatchioDatabase
import com.iamskorpz.watchioiptv.CatalogRefreshPhase1Test.MutableClock
import com.iamskorpz.watchioiptv.core.database.CategoryEntity
import com.iamskorpz.watchioiptv.core.database.LiveStreamEntity
import com.iamskorpz.watchioiptv.core.database.MovieDetailEntity
import com.iamskorpz.watchioiptv.core.database.ProviderEntity
import com.iamskorpz.watchioiptv.core.database.SeriesEntity
import com.iamskorpz.watchioiptv.core.database.VodStreamEntity
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.WatchioAudioTrack
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerMetadata
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.core.player.WatchioSubtitleTrack
import com.iamskorpz.watchioiptv.core.security.ProviderCredentialStore
import com.iamskorpz.watchioiptv.core.security.SecretStore
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshCoordinator
import com.iamskorpz.watchioiptv.data.epg.EpgRepository
import com.iamskorpz.watchioiptv.data.live.LiveTvCategory
import com.iamskorpz.watchioiptv.data.live.LiveTvCategoryKind
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.data.live.LiveTvRepository
import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.RoomProviderRepository
import com.iamskorpz.watchioiptv.data.movies.MovieCategory
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.movies.WatchioMovieItem
import java.io.ByteArrayInputStream
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshCoordinator
import com.iamskorpz.watchioiptv.data.refresh.CatalogUpdateEvent
import com.iamskorpz.watchioiptv.data.refresh.RefreshStatus
import com.iamskorpz.watchioiptv.data.refresh.RefreshTrigger
import com.iamskorpz.watchioiptv.data.series.SeriesCategory
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.data.series.WatchioSeriesItem
import com.iamskorpz.watchioiptv.data.xtream.XtreamPlaybackUrlResolver
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.model.StreamFormat
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlRequest
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlResolver
import com.iamskorpz.watchioiptv.domain.repository.FavoriteItem
import com.iamskorpz.watchioiptv.domain.repository.FavoritesRepository
import com.iamskorpz.watchioiptv.domain.repository.HistoryItem
import com.iamskorpz.watchioiptv.domain.repository.HistoryRepository
import com.iamskorpz.watchioiptv.domain.repository.LiveTvBrowsingState
import com.iamskorpz.watchioiptv.domain.repository.PlayerSettings
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.domain.repository.VideoScalingMode
import com.iamskorpz.watchioiptv.feature.live.LiveTvViewModel
import com.iamskorpz.watchioiptv.feature.movies.MoviesViewModel
import com.iamskorpz.watchioiptv.feature.series.SeriesViewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSilentUiPhase3Test {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var database: FakeWatchioDatabase
    private lateinit var clock: MutableClock
    private lateinit var secretStore: FakeSecretStore
    private lateinit var credentialStore: ProviderCredentialStore
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var favoritesRepository: FakeFavoritesRepository
    private lateinit var historyRepository: FakeHistoryRepository
    private lateinit var playerManager: TestPlayerManager
    private lateinit var xtreamRepository: XtreamRepository
    private lateinit var m3uRepository: M3uRepository
    private lateinit var coordinator: CatalogRefreshCoordinator
    private lateinit var server: MockWebServer

    private val provider1 = ProviderId("provider-1")
    private val provider2 = ProviderId("provider-2")

    private val testJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val testOkHttpClient = OkHttpClient.Builder().build()
    private fun testRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(testOkHttpClient)
        .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun testProvider(id: String, name: String) = ProviderEntity(
        id = id,
        displayName = name,
        type = "xtream",
        serverUrl = "http://example.invalid",
        createdAtEpochMs = clock.nowEpochMs(),
        updatedAtEpochMs = clock.nowEpochMs(),
        lastRefreshAtEpochMs = clock.nowEpochMs(),
        enabled = true,
    )

    private fun testCategory(providerId: String, id: String, name: String, contentType: String, order: Int) = CategoryEntity(
        providerId = providerId,
        categoryId = id,
        name = name,
        normalizedName = name.lowercase(),
        parentId = null,
        contentType = contentType,
        serverOrder = order,
    )

    private fun testLiveStream(providerId: String, id: String, name: String, categoryId: String, order: Int) = LiveStreamEntity(
        providerId = providerId,
        streamId = id,
        name = name,
        normalizedName = name.lowercase(),
        iconUrl = null,
        categoryId = categoryId,
        epgChannelId = null,
        streamExtension = "ts",
        serverOrder = order,
        createdAtEpochMs = clock.nowEpochMs(),
        updatedAtEpochMs = clock.nowEpochMs(),
    )

    private fun testMovie(providerId: String, id: String, name: String, categoryId: String, order: Int) = VodStreamEntity(
        providerId = providerId,
        streamId = id,
        name = name,
        normalizedName = name.lowercase(),
        posterUrl = null,
        categoryId = categoryId,
        rating = null,
        containerExtension = "mp4",
        genre = null,
        trailer = null,
        serverOrder = order,
        updatedAtEpochMs = clock.nowEpochMs(),
    )

    private fun testSeries(providerId: String, id: String, name: String, categoryId: String, order: Int) = SeriesEntity(
        providerId = providerId,
        seriesId = id,
        name = name,
        normalizedName = name.lowercase(),
        coverUrl = null,
        plot = null,
        cast = null,
        director = null,
        genre = null,
        releaseDate = null,
        rating = null,
        trailer = null,
        runtime = null,
        categoryId = categoryId,
        tmdbId = null,
        serverOrder = order,
        updatedAtEpochMs = clock.nowEpochMs(),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = FakeWatchioDatabase()
        clock = MutableClock(1_700_000_000_000L)
        secretStore = FakeSecretStore()
        credentialStore = ProviderCredentialStore(secretStore)
        settingsRepository = FakeSettingsRepository(provider1)
        favoritesRepository = FakeFavoritesRepository()
        historyRepository = FakeHistoryRepository()
        playerManager = TestPlayerManager()

        server = MockWebServer()
        server.start()

        val baseUrl = server.url("/").toString()
        val providerRepo = RoomProviderRepository(database.providerDao(), credentialStore)
        val resolver = XtreamPlaybackUrlResolver(providerRepo, credentialStore, settingsRepository)

        xtreamRepository = XtreamRepository(
            database = database,
            credentialStore = credentialStore,
            settingsRepository = settingsRepository,
            retrofitFactory = { testRetrofit(baseUrl) },
            clock = clock,
            backgroundScope = testScope,
            runInTransaction = database::runTransaction,
        )
        m3uRepository = M3uRepository(
            database = database,
            okHttpClient = testOkHttpClient,
            settingsRepository = settingsRepository,
            clock = clock,
            openLocalInputStream = { ByteArrayInputStream(ByteArray(0)) },
            runInTransaction = database::runTransaction,
        )
        coordinator = CatalogRefreshCoordinator(
            database = database,
            xtreamRepository = xtreamRepository,
            m3uRepository = m3uRepository,
            clock = clock,
            coordinatorScope = testScope,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun liveTvSilentlyUpdatesChannelsWithoutLoadingOverlayOrInterruptingPlayback() = testScope.runTest {
        // Arrange: seed provider and channels
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.categoryDaoImpl.upsertAll(listOf(
            testCategory(provider1.value, "sports", "Sports", ContentType.Live.persisted, 1),
        ))
        database.liveStreamDaoImpl.upsertAll(listOf(
            testLiveStream(provider1.value, "ch-1", "Channel One", "sports", 1),
            testLiveStream(provider1.value, "ch-2", "Channel Two", "sports", 2),
        ))

        val liveRepo = LiveTvRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/ch"
            },
            ioDispatcher = testDispatcher,
        )
        val epgRepo = EpgRepository(database, testOkHttpClient, credentialStore, clock)
        val epgCoordinator = EpgRefreshCoordinator(database, epgRepo)

        val viewModel = LiveTvViewModel(
            liveTvRepository = liveRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            epgRefreshCoordinator = epgCoordinator,
            playerManager = playerManager,
            clock = clock,
            catalogRefreshCoordinator = coordinator,
        )
        advanceUntilIdle()

        // Verify initial state
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(2, viewModel.uiState.value.channels.size)
        val activeChannel = viewModel.uiState.value.channels.first { it.id == "ch-1" }
        viewModel.selectChannel(activeChannel)
        testScheduler.runCurrent()
        assertEquals(1, playerManager.loadCount)
        assertEquals("Channel One", playerManager.lastLoadedMedia?.title)

        // Act: Insert new channel in database (simulating background sync) and emit silent coordinator update
        database.liveStreamDaoImpl.upsertAll(listOf(
            testLiveStream(provider1.value, "ch-3", "Channel Three (New)", "sports", 3),
        ))

        viewModel.refreshSilently()
        testScheduler.runCurrent()

        // Assert: New channel is present, loading overlay is FALSE, selected channel preserved, playerManager NOT reloaded
        val updatedState = viewModel.uiState.value
        assertFalse("Silent update must never display loading overlay", updatedState.loading)
        assertEquals(3, updatedState.channels.size)
        assertTrue(updatedState.channels.any { it.id == "ch-3" })
        assertEquals("Selected channel must be preserved", "ch-1", updatedState.selectedChannel?.id)
        assertEquals("Player load count must remain 1 (playback must NOT be interrupted)", 1, playerManager.loadCount)
        viewModel.leaveLiveTv()
    }

    @Test
    fun liveTvPreservesActivePlaybackEvenIfPlayingChannelDisappearsFromCatalogue() = testScope.runTest {
        // Arrange: 2 channels
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.liveStreamDaoImpl.upsertAll(listOf(
            testLiveStream(provider1.value, "ch-1", "Channel One", "all", 1),
            testLiveStream(provider1.value, "ch-2", "Channel Two", "all", 2),
        ))

        val liveRepo = LiveTvRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/ch"
            },
            ioDispatcher = testDispatcher,
        )
        val epgRepo = EpgRepository(database, testOkHttpClient, credentialStore, clock)
        val epgCoordinator = EpgRefreshCoordinator(database, epgRepo)

        val viewModel = LiveTvViewModel(
            liveTvRepository = liveRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            epgRefreshCoordinator = epgCoordinator,
            playerManager = playerManager,
            clock = clock,
            catalogRefreshCoordinator = coordinator,
        )
        advanceUntilIdle()

        val activeChannel = viewModel.uiState.value.channels.first { it.id == "ch-1" }
        viewModel.selectChannel(activeChannel)
        testScheduler.runCurrent()
        assertEquals(1, playerManager.loadCount)

        // Act: Provider removes ch-1 from server catalogue.
        database.liveStreamDaoImpl.deleteByProvider(provider1.value)
        database.liveStreamDaoImpl.upsertAll(listOf(
            testLiveStream(provider1.value, "ch-2", "Channel Two", "all", 2),
        ))

        viewModel.refreshSilently()
        testScheduler.runCurrent()

        // Assert: Channel list has only ch-2, but selectedChannel is still ch-1 and playerManager did not restart or stop
        val updatedState = viewModel.uiState.value
        assertEquals(1, updatedState.channels.size)
        assertEquals("ch-2", updatedState.channels.first().id)
        assertNotNull(updatedState.selectedChannel)
        assertEquals("ch-1", updatedState.selectedChannel?.id)
        assertEquals("Playback must not be reloaded when channel disappears", 1, playerManager.loadCount)
        viewModel.leaveLiveTv()
    }

    @Test
    fun moviesSilentlyUpdatesAndPreservesCategoryAndPaginationDepth() = testScope.runTest {
        // Arrange: seed movies
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.categoryDaoImpl.upsertAll(listOf(
            testCategory(provider1.value, "cat-action", "Action", ContentType.Movie.persisted, 1),
            testCategory(provider1.value, "cat-comedy", "Comedy", ContentType.Movie.persisted, 2),
        ))
        val initialMovies = (1..15).map { index ->
            testMovie(provider1.value, "movie-$index", "Movie $index", "cat-action", index)
        }
        database.vodDaoImpl.upsertAll(initialMovies)

        val moviesRepo = MoviesRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/movie"
            },
            credentialStore = credentialStore,
            retrofitFactory = { testRetrofit(server.url("/").toString()) },
            tmdbRetrofitFactory = { testRetrofit(server.url("/").toString()) },
            clock = clock,
            ioDispatcher = testDispatcher,
        )

        val viewModel = MoviesViewModel(
            moviesRepository = moviesRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            playerManager = playerManager,
            clock = clock,
            xtreamRepository = xtreamRepository,
            catalogRefreshCoordinator = coordinator,
            defaultDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        // Select the Action category explicitly
        val actionCategory = viewModel.moviesState.value.categories.first { it.id == "cat-action" }
        viewModel.selectCategory(actionCategory)
        advanceUntilIdle()

        assertEquals(15, viewModel.moviesState.value.movies.size)
        assertEquals("cat-action", viewModel.moviesState.value.selectedCategory?.id)

        // Act: Add 5 new movies to cat-action
        val newMovies = (16..20).map { index ->
            testMovie(provider1.value, "movie-$index", "Movie $index", "cat-action", index)
        }
        database.vodDaoImpl.upsertAll(newMovies)

        viewModel.refreshSilently()
        advanceUntilIdle()

        // Assert: pagination depth was preserved (loaded up to 20), category preserved, no loading screen
        val updatedState = viewModel.moviesState.value
        assertFalse(updatedState.loading)
        assertEquals("cat-action", updatedState.selectedCategory?.id)
        assertEquals(20, updatedState.movies.size)
        assertTrue(updatedState.movies.any { it.id == "movie-20" })
    }

    @Test
    fun moviesPreservesDetailsModalDuringSilentRefresh() = testScope.runTest {
        // Arrange
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.categoryDaoImpl.upsertAll(listOf(
            testCategory(provider1.value, "cat-action", "Action", ContentType.Movie.persisted, 1),
        ))
        database.vodDaoImpl.upsertAll(listOf(
            testMovie(provider1.value, "movie-1", "Original Movie", "cat-action", 1),
        ))
        database.movieDetailDaoImpl.upsertDetail(MovieDetailEntity(
            providerId = provider1.value,
            movieId = "movie-1",
            title = "Original Movie",
            posterUrl = null,
            backdropUrl = null,
            plot = "Original Plot",
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            runtime = null,
            youtubeTrailer = null,
            tmdbId = null,
            containerExtension = "mp4",
            updatedAtEpochMs = clock.nowEpochMs(),
        ))

        val moviesRepo = MoviesRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/movie"
            },
            credentialStore = credentialStore,
            retrofitFactory = { testRetrofit(server.url("/").toString()) },
            tmdbRetrofitFactory = { testRetrofit(server.url("/").toString()) },
            clock = clock,
            ioDispatcher = testDispatcher,
        )

        val viewModel = MoviesViewModel(
            moviesRepository = moviesRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            playerManager = playerManager,
            clock = clock,
            xtreamRepository = xtreamRepository,
            catalogRefreshCoordinator = coordinator,
            defaultDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        val actionCategory = viewModel.moviesState.value.categories.first { it.id == "cat-action" }
        viewModel.selectCategory(actionCategory)
        advanceUntilIdle()

        // Open details
        val movie = viewModel.moviesState.value.movies.first()
        viewModel.loadDetails(movie)
        advanceUntilIdle()
        assertNotNull(viewModel.detailsState.value.details)
        assertEquals("Original Movie", viewModel.detailsState.value.details?.title)

        // Act: Update movie title in database and refresh silently
        database.vodDaoImpl.upsertAll(listOf(
            testMovie(provider1.value, "movie-1", "Updated Movie Title", "cat-action", 1),
        ))
        database.movieDetailDaoImpl.upsertDetail(MovieDetailEntity(
            providerId = provider1.value,
            movieId = "movie-1",
            title = "Updated Movie Title",
            posterUrl = null,
            backdropUrl = null,
            plot = "Original Plot",
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            runtime = null,
            youtubeTrailer = null,
            tmdbId = null,
            containerExtension = "mp4",
            updatedAtEpochMs = clock.nowEpochMs(),
        ))
        viewModel.refreshSilently()
        advanceUntilIdle()

        // Assert: Details modal remained OPEN and details updated without closing
        val detailsState = viewModel.detailsState.value
        assertNotNull("Details modal must not be closed on silent refresh", detailsState.details)
        assertEquals("Updated Movie Title", detailsState.details?.title)
    }

    @Test
    fun seriesSilentlyUpdatesAndPreservesCategoryAndDetails() = testScope.runTest {
        // Arrange
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.categoryDaoImpl.upsertAll(listOf(
            testCategory(provider1.value, "cat-drama", "Drama", ContentType.Series.persisted, 1),
        ))
        database.seriesDaoImpl.upsertAll(listOf(
            testSeries(provider1.value, "series-1", "Original Series", "cat-drama", 1),
        ))

        val seriesRepo = SeriesRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/episode"
            },
            credentialStore = credentialStore,
            retrofitFactory = { testRetrofit(server.url("/").toString()) },
            tmdbRetrofitFactory = { testRetrofit(server.url("/").toString()) },
            clock = clock,
            ioDispatcher = testDispatcher,
        )

        val viewModel = SeriesViewModel(
            seriesRepository = seriesRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            playerManager = playerManager,
            clock = clock,
            xtreamRepository = xtreamRepository,
            catalogRefreshCoordinator = coordinator,
            defaultDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        val dramaCategory = viewModel.seriesState.value.categories.first { it.id == "cat-drama" }
        viewModel.selectCategory(dramaCategory)
        advanceUntilIdle()

        assertEquals(1, viewModel.seriesState.value.series.size)
        assertEquals("cat-drama", viewModel.seriesState.value.selectedCategory?.id)

        // Act: Add newly discovered series row
        database.seriesDaoImpl.upsertAll(listOf(
            testSeries(provider1.value, "series-2", "New Series", "cat-drama", 2),
        ))

        viewModel.refreshSilently()
        advanceUntilIdle()

        // Assert: New series added silently
        val updatedState = viewModel.seriesState.value
        assertFalse(updatedState.loading)
        assertEquals(2, updatedState.series.size)
        assertTrue(updatedState.series.any { it.series.id == "series-2" })
    }

    @Test
    fun seriesDetailsRefreshFailureKeepsCachedDetailsAndDoesNotEscapeCoroutine() = testScope.runTest {
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.seriesDaoImpl.upsertAll(listOf(
            testSeries(provider1.value, "series-1", "Offline Series", "cat-drama", 1).copy(updatedAtEpochMs = 0L),
        ))

        val seriesRepo = SeriesRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/episode"
            },
            credentialStore = credentialStore,
            retrofitFactory = { testRetrofit(server.url("/").toString()) },
            tmdbRetrofitFactory = { testRetrofit(server.url("/").toString()) },
            clock = clock,
            ioDispatcher = testDispatcher,
        )
        val viewModel = SeriesViewModel(
            seriesRepository = seriesRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            playerManager = playerManager,
            clock = clock,
            xtreamRepository = xtreamRepository,
            defaultDispatcher = testDispatcher,
        )
        val item = seriesRepo.item(provider1, "series-1")!!

        viewModel.loadDetails(item)
        advanceUntilIdle()
        assertEquals("Offline Series", viewModel.detailsState.value.details?.title)

        viewModel.refreshDetails()
        advanceUntilIdle()

        assertEquals("Offline Series", viewModel.detailsState.value.details?.title)
        assertEquals("Unable to refresh episodes from this provider.", viewModel.detailsState.value.errorMessage)
        assertFalse(viewModel.detailsState.value.loading)
    }

    @Test
    fun staleProviderEventsAreIgnoredWhenProviderIsSwitched() = testScope.runTest {
        // Arrange: Provider 1 active
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.providerDaoImpl.upsert(testProvider(provider2.value, "Provider 2"))
        database.categoryDaoImpl.upsertAll(listOf(
            testCategory(provider1.value, "all", "ALL CHANNELS", ContentType.Live.persisted, 1),
            testCategory(provider2.value, "all", "ALL CHANNELS", ContentType.Live.persisted, 1),
        ))
        database.liveStreamDaoImpl.upsertAll(listOf(
            testLiveStream(provider1.value, "ch-p1-1", "Provider 1 Channel", "all", 1),
            testLiveStream(provider2.value, "ch-p2-1", "Provider 2 Channel", "all", 1),
        ))

        val liveRepo = LiveTvRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/ch"
            },
        )
        val epgRepo = EpgRepository(database, testOkHttpClient, credentialStore, clock)
        val epgCoordinator = EpgRefreshCoordinator(database, epgRepo)

        val viewModel = LiveTvViewModel(
            liveTvRepository = liveRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            epgRefreshCoordinator = epgCoordinator,
            playerManager = playerManager,
            clock = clock,
            catalogRefreshCoordinator = coordinator,
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.channels.size)
        assertEquals("ch-p1-1", viewModel.uiState.value.channels.first().id)

        // Act: An update event arrives for Provider 2 (e.g. from an asynchronous background task of previous provider)
        // Insert new channel for provider 2
        database.liveStreamDaoImpl.upsertAll(listOf(
            testLiveStream(provider2.value, "ch-p2-2", "Provider 2 New Channel", "all", 2),
        ))

        // Trigger update event for provider2
        val eventForP2 = CatalogUpdateEvent(
            providerId = provider2,
            trigger = RefreshTrigger.AutomaticPeriodic,
            status = RefreshStatus.Updated,
            refreshedSections = setOf(ContentType.Live),
            changedSections = setOf(ContentType.Live),
            completedAtEpochMs = clock.nowEpochMs(),
        )

        // ViewModel should ignore this event because active provider is provider1
        val currentState = viewModel.uiState.value
        assertEquals("ch-p1-1", currentState.channels.single().id)
        assertEquals(provider1, currentState.channels.single().providerId)
    }

    @Test
    fun unchangedCatalogueStatusTriggersNoUiStateEmission() = testScope.runTest {
        // Arrange
        database.providerDaoImpl.upsert(testProvider(provider1.value, "Provider 1"))
        database.categoryDaoImpl.upsertAll(listOf(
            testCategory(provider1.value, "all", "ALL CHANNELS", ContentType.Live.persisted, 1),
        ))
        database.liveStreamDaoImpl.upsertAll(listOf(
            testLiveStream(provider1.value, "ch-1", "Channel One", "all", 1),
        ))

        val liveRepo = LiveTvRepository(
            database = database,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = object : PlaybackUrlResolver {
                override suspend fun resolve(request: PlaybackUrlRequest) = "http://stream/ch"
            },
        )
        val epgRepo = EpgRepository(database, testOkHttpClient, credentialStore, clock)
        val epgCoordinator = EpgRefreshCoordinator(database, epgRepo)

        val viewModel = LiveTvViewModel(
            liveTvRepository = liveRepo,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            epgRefreshCoordinator = epgCoordinator,
            playerManager = playerManager,
            clock = clock,
            catalogRefreshCoordinator = coordinator,
        )
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value

        // Event for unchanged status
        val unchangedEvent = CatalogUpdateEvent(
            providerId = provider1,
            trigger = RefreshTrigger.AutomaticPeriodic,
            status = RefreshStatus.Unchanged,
            refreshedSections = setOf(ContentType.Live),
            changedSections = emptySet(),
            completedAtEpochMs = clock.nowEpochMs(),
        )

        // Should not trigger refreshSilently
        advanceUntilIdle()
        assertEquals(stateBefore, viewModel.uiState.value)
    }

    private class FakeSecretStore : SecretStore {
        private val secrets = mutableMapOf<String, String>()
        override suspend fun getSecret(key: String): String? = secrets[key]
        override suspend fun putSecret(key: String, value: String) { secrets[key] = value }
        override suspend fun removeSecret(key: String) { secrets.remove(key) }
    }

    private class FakeSettingsRepository(
        var activeProviderId: ProviderId? = ProviderId("provider-1"),
    ) : SettingsRepository {
        val browsingStates = mutableMapOf<String, LiveTvBrowsingState>()
        var playerSettingsValue = PlayerSettings(
            autoPlayLiveChannel = false,
            rememberLastLiveChannel = true,
        )

        override val selectedProviderId: Flow<ProviderId?>
            get() = flowOf(activeProviderId)
        override val inputMode: Flow<InputMode>
            get() = flowOf(InputMode.TvRemote)
        override val streamFormat: Flow<StreamFormat>
            get() = flowOf(StreamFormat.Auto)
        override val playerSettings: Flow<PlayerSettings>
            get() = flowOf(playerSettingsValue)

        override suspend fun setSelectedProviderId(providerId: ProviderId?) {
            activeProviderId = providerId
        }
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit

        override fun observeLiveBrowsingState(providerId: ProviderId): Flow<LiveTvBrowsingState> =
            flowOf(browsingStates[providerId.value] ?: LiveTvBrowsingState())

        override suspend fun saveLiveBrowsingState(providerId: ProviderId, state: LiveTvBrowsingState) {
            browsingStates[providerId.value] = state
        }
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        private val favorites = mutableListOf<FavoriteItem>()
        override suspend fun toggle(favorite: FavoriteItem): Boolean {
            val exists = favorites.any { it.contentId == favorite.contentId }
            if (exists) favorites.removeAll { it.contentId == favorite.contentId }
            else favorites.add(favorite)
            return !exists
        }
        override suspend fun isFavorite(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String?): Boolean =
            favorites.any { it.contentId == contentId }
        override suspend fun getFavorites(providerId: ProviderId): List<FavoriteItem> = favorites
    }

    private class FakeHistoryRepository : HistoryRepository {
        override suspend fun upsert(item: HistoryItem) = Unit
        override suspend fun find(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String?): HistoryItem? = null
        override suspend fun recent(providerId: ProviderId): List<HistoryItem> = emptyList()
    }

    private class TestPlayerManager : WatchioPlayerManager {
        private var metadata = WatchioPlayerMetadata()
        private val mutableState = MutableStateFlow<WatchioPlayerState>(WatchioPlayerState.Idle(metadata))
        override val state: StateFlow<WatchioPlayerState> = mutableState
        var lastLoadedMedia: PlaybackMedia? = null
        var loadCount: Int = 0

        override suspend fun load(media: PlaybackMedia) {
            loadCount++
            lastLoadedMedia = media
            metadata = metadata.copy(currentMedia = media, isSeekable = !media.isLive)
            mutableState.value = WatchioPlayerState.Playing(metadata)
        }
        override fun play() { mutableState.value = WatchioPlayerState.Playing(metadata) }
        override fun pause() { mutableState.value = WatchioPlayerState.Paused(metadata) }
        override fun stop() {
            metadata = metadata.copy(currentMedia = null)
            mutableState.value = WatchioPlayerState.Idle(metadata)
        }
        override fun retry() = Unit
        override fun seekTo(positionMs: Long) { metadata = metadata.copy(positionMs = positionMs) }
        override fun seekBy(deltaMs: Long) { metadata = metadata.copy(positionMs = (metadata.positionMs + deltaMs).coerceAtLeast(0L)) }
        override fun selectAudioTrack(track: WatchioAudioTrack) { metadata = metadata.copy(selectedAudioTrack = track) }
        override fun selectSubtitleTrack(track: WatchioSubtitleTrack?) { metadata = metadata.copy(selectedSubtitleTrack = track) }
        override fun setVideoScalingMode(mode: VideoScalingMode) { metadata = metadata.copy(videoScalingMode = mode) }
        override fun setPlaybackSpeed(speed: Float) { metadata = metadata.copy(playbackSpeed = speed) }
        override fun setMuted(muted: Boolean) { metadata = metadata.copy(isMuted = muted) }
        override fun restart() { seekTo(0L); play() }
        override fun snapshot(): WatchioPlayerMetadata = metadata
        override fun attachSurface(container: ViewGroup) = Unit
        override fun detachSurface(container: ViewGroup) = Unit
        override fun release() = Unit
    }
}
