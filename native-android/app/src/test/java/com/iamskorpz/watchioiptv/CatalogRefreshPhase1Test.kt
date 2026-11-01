package com.iamskorpz.watchioiptv

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.iamskorpz.watchioiptv.core.database.AppMetadataDao
import com.iamskorpz.watchioiptv.core.database.AppMetadataEntity
import com.iamskorpz.watchioiptv.core.database.CatalogSearchRow
import com.iamskorpz.watchioiptv.core.database.CategoryDao
import com.iamskorpz.watchioiptv.core.database.CategoryEntity
import com.iamskorpz.watchioiptv.core.database.EpgChannelEntity
import com.iamskorpz.watchioiptv.core.database.EpgDao
import com.iamskorpz.watchioiptv.core.database.EpgImportChannelEntity
import com.iamskorpz.watchioiptv.core.database.EpgImportProgrammeEntity
import com.iamskorpz.watchioiptv.core.database.EpgProgrammeEntity
import com.iamskorpz.watchioiptv.core.database.EpgSourceEntity
import com.iamskorpz.watchioiptv.core.database.EpisodeDao
import com.iamskorpz.watchioiptv.core.database.EpisodeEntity
import com.iamskorpz.watchioiptv.core.database.FavoriteDao
import com.iamskorpz.watchioiptv.core.database.FavoriteEntity
import com.iamskorpz.watchioiptv.core.database.LiveStreamDao
import com.iamskorpz.watchioiptv.core.database.LiveStreamEntity
import com.iamskorpz.watchioiptv.core.database.M3uImportItemEntity
import com.iamskorpz.watchioiptv.core.database.M3uItemDao
import com.iamskorpz.watchioiptv.core.database.M3uItemEntity
import com.iamskorpz.watchioiptv.core.database.MovieDetailDao
import com.iamskorpz.watchioiptv.core.database.MovieDetailEntity
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadDao
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity
import com.iamskorpz.watchioiptv.core.database.ProviderDao
import com.iamskorpz.watchioiptv.core.database.ProviderEntity
import com.iamskorpz.watchioiptv.core.database.SQLITE_MAX_BIND_PARAMS
import com.iamskorpz.watchioiptv.core.database.SeasonEntity
import com.iamskorpz.watchioiptv.core.database.SeriesDao
import com.iamskorpz.watchioiptv.core.database.SeriesEntity
import com.iamskorpz.watchioiptv.core.database.SportsCacheDao
import com.iamskorpz.watchioiptv.core.database.SportsFixtureCacheEntity
import com.iamskorpz.watchioiptv.core.database.TmdbTrailerCacheEntity
import com.iamskorpz.watchioiptv.core.database.VodDao
import com.iamskorpz.watchioiptv.core.database.VodStreamEntity
import com.iamskorpz.watchioiptv.core.database.WatchHistoryDao
import com.iamskorpz.watchioiptv.core.database.WatchHistoryEntity
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.security.ProviderCredentialStore
import com.iamskorpz.watchioiptv.core.security.SecretStore
import com.iamskorpz.watchioiptv.core.util.WatchioClock
import com.iamskorpz.watchioiptv.data.RoomFavoritesRepository
import com.iamskorpz.watchioiptv.data.RoomHistoryRepository
import com.iamskorpz.watchioiptv.data.RoomProviderRepository
import com.iamskorpz.watchioiptv.data.library.SearchHistoryStore
import com.iamskorpz.watchioiptv.data.library.SearchRepository
import com.iamskorpz.watchioiptv.data.library.SearchScope
import com.iamskorpz.watchioiptv.data.m3u.M3uImportState
import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.m3u.M3uUrlInput
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshCoordinator
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshResult
import com.iamskorpz.watchioiptv.data.refresh.CatalogUpdateEvent
import com.iamskorpz.watchioiptv.data.refresh.EmptyCatalogResponseException
import com.iamskorpz.watchioiptv.data.refresh.RefreshStatus
import com.iamskorpz.watchioiptv.data.refresh.RefreshTrigger
import com.iamskorpz.watchioiptv.data.refresh.getOrThrow
import com.iamskorpz.watchioiptv.data.series.SeriesCategory
import com.iamskorpz.watchioiptv.data.series.SeriesCategoryKind
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.data.xtream.XtreamCredentialsInput
import com.iamskorpz.watchioiptv.data.xtream.XtreamImportState
import com.iamskorpz.watchioiptv.data.xtream.XtreamPlaybackUrlResolver
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.domain.model.StreamFormat
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.domain.repository.XtreamAccountMetadata
import com.iamskorpz.watchioiptv.feature.sports.CachedSportsMatch
import com.iamskorpz.watchioiptv.feature.sports.FootballScheduleSource
import com.iamskorpz.watchioiptv.feature.sports.InMemorySportsMatchCache
import com.iamskorpz.watchioiptv.feature.sports.SportsBroadcastState
import com.iamskorpz.watchioiptv.feature.sports.SportsFixture
import com.iamskorpz.watchioiptv.feature.sports.SportsRepository
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideRepository
import com.iamskorpz.watchioiptv.data.live.LiveTvRepository
import com.iamskorpz.watchioiptv.data.epg.EpgRepository
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshCoordinator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.LocalDate

class CatalogRefreshPhase1Test {
    private lateinit var database: FakeWatchioDatabase
    private lateinit var server: MockWebServer
    private lateinit var secretStore: FakeSecretStore
    private lateinit var credentialStore: ProviderCredentialStore
    private lateinit var settings: FakeSettingsRepository
    private lateinit var clock: MutableClock
    private lateinit var xtreamRepository: XtreamRepository
    private lateinit var m3uRepository: M3uRepository
    private lateinit var coordinator: CatalogRefreshCoordinator
    private lateinit var moviesRepository: MoviesRepository
    private lateinit var seriesRepository: SeriesRepository
    private lateinit var searchRepository: SearchRepository
    private lateinit var sportsMatchCache: InMemorySportsMatchCache
    private lateinit var sportsRepository: SportsRepository
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

    @Before
    fun setup() {
        database = FakeWatchioDatabase()
        server = MockWebServer()
        server.start()
        secretStore = FakeSecretStore()
        credentialStore = ProviderCredentialStore(secretStore)
        settings = FakeSettingsRepository()
        clock = MutableClock(1_000L)
        val providerRepository = RoomProviderRepository(database.providerDao(), credentialStore)
        val favoritesRepository = RoomFavoritesRepository(database.favoriteDao())
        val historyRepository = RoomHistoryRepository(database.watchHistoryDao())
        val playbackResolver = XtreamPlaybackUrlResolver(providerRepository, credentialStore, settings)

        xtreamRepository = XtreamRepository(
            database = database,
            credentialStore = credentialStore,
            settingsRepository = settings,
            retrofitFactory = ::testRetrofit,
            clock = clock,
            backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            runInTransaction = database::runTransaction,
        )
        m3uRepository = M3uRepository(
            database = database,
            okHttpClient = testOkHttpClient,
            settingsRepository = settings,
            clock = clock,
            openLocalInputStream = { ByteArrayInputStream(ByteArray(0)) },
            runInTransaction = database::runTransaction,
        )
        coordinator = CatalogRefreshCoordinator(
            database = database,
            xtreamRepository = xtreamRepository,
            m3uRepository = m3uRepository,
            clock = clock,
            coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        moviesRepository = MoviesRepository(
            database = database,
            settingsRepository = settings,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = playbackResolver,
            credentialStore = credentialStore,
            retrofitFactory = ::testRetrofit,
            tmdbRetrofitFactory = ::testRetrofit,
            clock = clock,
        )
        seriesRepository = SeriesRepository(
            database = database,
            settingsRepository = settings,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = playbackResolver,
            credentialStore = credentialStore,
            retrofitFactory = ::testRetrofit,
            tmdbRetrofitFactory = ::testRetrofit,
            clock = clock,
        )
        searchRepository = SearchRepository(
            database = database,
            settingsRepository = settings,
            historyStore = InMemorySearchHistoryStore(),
        )
        val liveTvRepository = LiveTvRepository(
            database = database,
            settingsRepository = settings,
            favoritesRepository = favoritesRepository,
            historyRepository = historyRepository,
            playbackUrlResolver = playbackResolver,
        )
        val epgRepository = EpgRepository(
            database = database,
            okHttpClient = testOkHttpClient,
            credentialStore = credentialStore,
            clock = clock,
        )
        val epgCoordinator = EpgRefreshCoordinator(database, epgRepository)
        val tvGuideRepository = TvGuideRepository(database, liveTvRepository, epgRepository, epgCoordinator)
        sportsMatchCache = InMemorySportsMatchCache()
        sportsRepository = SportsRepository(
            scheduleSource = object : FootballScheduleSource {
                override suspend fun getFixtures(date: LocalDate): Result<List<SportsFixture>> = Result.success(emptyList())
            },
            tvGuideRepository = tvGuideRepository,
            matchCache = sportsMatchCache,
        )

        xtreamRepository.onLiveUpdated = {
            searchRepository.invalidate(it)
            sportsRepository.invalidateProviderChannelCache(it)
        }
        m3uRepository.onLiveUpdated = {
            searchRepository.invalidate(it)
            sportsRepository.invalidateProviderChannelCache(it)
        }
        xtreamRepository.onMoviesUpdated = {
            moviesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
        m3uRepository.onMoviesUpdated = {
            moviesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
        xtreamRepository.onSeriesUpdated = {
            seriesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
        m3uRepository.onSeriesUpdated = {
            seriesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun concurrentRefreshDeduplicationPreventsDuplicateXtreamRequests() = runBlocking {
        enqueueXtreamCatalog(live = 2, movies = 2, series = 2)
        val added = xtreamRepository.addProvider(xtreamInput())
        val initialRequests = server.requestCount

        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val liveStreamRequests = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.contains("action=get_live_streams")) {
                    liveStreamRequests.incrementAndGet()
                    startedLatch.countDown()
                    releaseLatch.await(5, TimeUnit.SECONDS)
                }
                return xtreamDispatcherResponse(path, live = 3, movies = 2, series = 2)
            }
        }

        clock.set(2_000L)
        val jobs = (1..5).map {
            async(Dispatchers.IO) {
                coordinator.refreshProvider(added.providerId, RefreshTrigger.Manual)
            }
        }
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
        releaseLatch.countDown()
        val results = jobs.awaitAll()

        assertEquals(1, liveStreamRequests.get())
        assertEquals(7, server.requestCount - initialRequests)
        assertTrue(results.all { it is CatalogRefreshResult.Success && it.liveCount == 3 })
        assertEquals(1, results.count { it is CatalogRefreshResult.Success && !it.coalesced })
        assertEquals(4, results.count { it is CatalogRefreshResult.Success && it.coalesced })
    }

    @Test
    fun manualRefreshJoinsInProgressAutomaticRefreshAndPreservesCompletionAndFailureFeedback() = runBlocking {
        enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
        val added = xtreamRepository.addProvider(xtreamInput())

        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val liveCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.contains("action=get_live_streams")) {
                    liveCalls.incrementAndGet()
                    startedLatch.countDown()
                    releaseLatch.await(5, TimeUnit.SECONDS)
                }
                return xtreamDispatcherResponse(path, live = 2, movies = 1, series = 1)
            }
        }

        clock.set(3_000L)
        val autoJob = async(Dispatchers.IO) {
            coordinator.refreshLive(added.providerId, RefreshTrigger.AutomaticStartup)
        }
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
        val manualJob = async(Dispatchers.IO) {
            coordinator.refreshLive(added.providerId, RefreshTrigger.Manual)
        }
        releaseLatch.countDown()

        val autoResult = autoJob.await() as CatalogRefreshResult.Success
        val manualResult = manualJob.await() as CatalogRefreshResult.Success
        assertEquals(1, liveCalls.get())
        assertFalse(autoResult.coalesced)
        assertTrue(manualResult.coalesced)
        assertEquals(RefreshStatus.Updated, manualResult.status)
        assertEquals(2, manualResult.liveCount)

        // Now test failure propagation when manual joins an automatic refresh that fails
        val failStarted = CountDownLatch(1)
        val failRelease = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.contains("action=get_live_categories")) {
                    failStarted.countDown()
                    failRelease.await(5, TimeUnit.SECONDS)
                    return MockResponse().setResponseCode(500)
                }
                return xtreamDispatcherResponse(path, live = 2, movies = 1, series = 1)
            }
        }

        val autoFailJob = async(Dispatchers.IO) {
            coordinator.refreshLive(added.providerId, RefreshTrigger.AutomaticPeriodic)
        }
        assertTrue(failStarted.await(5, TimeUnit.SECONDS))
        val manualFailJob = async(Dispatchers.IO) {
            coordinator.refreshLive(added.providerId, RefreshTrigger.Manual)
        }
        failRelease.countDown()

        val autoFailure = autoFailJob.await() as CatalogRefreshResult.Failure
        val manualFailure = manualFailJob.await() as CatalogRefreshResult.Failure
        assertFalse(autoFailure.coalesced)
        assertTrue(manualFailure.coalesced)
        assertTrue(manualFailure.message.isNotBlank())
        assertEquals(2, database.liveStreamDao().countByProvider(added.providerId.value))
    }

    @Test
    fun fullProviderRefreshAndSectionRefreshNeverRaceOrDuplicateInFlightSection() = runBlocking {
        enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
        val added = xtreamRepository.addProvider(xtreamInput())

        val liveStarted = CountDownLatch(1)
        val releaseLive = CountDownLatch(1)
        val liveCalls = AtomicInteger(0)
        val vodCalls = AtomicInteger(0)
        val seriesCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                when {
                    path.contains("action=get_live_streams") -> {
                        liveCalls.incrementAndGet()
                        liveStarted.countDown()
                        releaseLive.await(5, TimeUnit.SECONDS)
                    }
                    path.contains("action=get_vod_streams") -> vodCalls.incrementAndGet()
                    path.contains("action=get_series") && !path.contains("categories") -> seriesCalls.incrementAndGet()
                }
                return xtreamDispatcherResponse(path, live = 2, movies = 3, series = 4)
            }
        }

        clock.set(4_000L)
        val sectionJob = async(Dispatchers.IO) {
            coordinator.refreshLive(added.providerId, RefreshTrigger.Manual)
        }
        assertTrue(liveStarted.await(5, TimeUnit.SECONDS))
        val fullJob = async(Dispatchers.IO) {
            coordinator.refreshProvider(added.providerId, RefreshTrigger.AutomaticStartup)
        }
        releaseLive.countDown()

        val sectionResult = sectionJob.await() as CatalogRefreshResult.Success
        val fullResult = fullJob.await() as CatalogRefreshResult.Success

        assertEquals(1, liveCalls.get())
        assertEquals(1, vodCalls.get())
        assertEquals(1, seriesCalls.get())
        assertEquals(2, sectionResult.liveCount)
        assertEquals(2, fullResult.liveCount)
        assertEquals(3, fullResult.movieCount)
        assertEquals(4, fullResult.seriesCount)
        assertEquals(setOf(ContentType.Live, ContentType.Movie, ContentType.Series), fullResult.changedSections)
    }

    @Test
    fun m3uSinglePassRefreshConsolidatesConcurrentSectionAndProviderRefreshes() = runBlocking {
        val playlistRequests = AtomicInteger(0)
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        var blockNextDownload = false

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                playlistRequests.incrementAndGet()
                if (blockNextDownload) {
                    startedLatch.countDown()
                    releaseLatch.await(5, TimeUnit.SECONDS)
                }
                return MockResponse()
                    .setHeader("Content-Type", "audio/x-mpegurl")
                    .setBody(sampleM3uPlaylist(liveTitle = if (blockNextDownload) "Live Updated" else "Live One"))
            }
        }

        val providerIdStr = m3uRepository.addUrlProvider(
            M3uUrlInput("M3U Provider", server.url("/playlist.m3u").toString()),
        )
        val providerId = ProviderId(providerIdStr)
        assertEquals(1, playlistRequests.get())

        blockNextDownload = true
        clock.set(5_000L)
        val liveJob = async(Dispatchers.IO) { coordinator.refreshLive(providerId) }
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
        val movieJob = async(Dispatchers.IO) { coordinator.refreshMovies(providerId) }
        val seriesJob = async(Dispatchers.IO) { coordinator.refreshSeries(providerId) }
        val fullJob = async(Dispatchers.IO) { coordinator.refreshProvider(providerId) }
        releaseLatch.countDown()

        val results = listOf(liveJob.await(), movieJob.await(), seriesJob.await(), fullJob.await())
        assertEquals(2, playlistRequests.get()) // 1 initial + 1 consolidated refresh download!
        assertTrue(results.all { it is CatalogRefreshResult.Success })
        assertEquals(5_000L, settings.sectionRefreshTime(providerId, ContentType.Live))
        assertEquals(5_000L, settings.sectionRefreshTime(providerId, ContentType.Movie))
        assertEquals(5_000L, settings.sectionRefreshTime(providerId, ContentType.Series))
    }

    @Test
    fun xtreamSectionRefreshUpdatesOnlyTargetSectionAndDistinguishesUpdatedFromUnchanged() = runBlocking {
        enqueueXtreamCatalog(live = 2, movies = 1, series = 1)
        val added = xtreamRepository.addProvider(xtreamInput())
        assertEquals(1_000L, settings.sectionRefreshTime(added.providerId, ContentType.Live))
        assertEquals(1_000L, settings.sectionRefreshTime(added.providerId, ContentType.Movie))
        assertEquals(1_000L, settings.sectionRefreshTime(added.providerId, ContentType.Series))

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                xtreamDispatcherResponse(request.path.orEmpty(), live = 2, movies = 3, series = 1)
        }

        clock.set(6_000L)
        val updated = coordinator.refreshMovies(added.providerId) as CatalogRefreshResult.Success
        assertEquals(RefreshStatus.Updated, updated.status)
        assertEquals(setOf(ContentType.Movie), updated.changedSections)
        assertEquals(3, database.vodDao().countByProvider(added.providerId.value))
        assertEquals(1_000L, settings.sectionRefreshTime(added.providerId, ContentType.Live))
        assertEquals(6_000L, settings.sectionRefreshTime(added.providerId, ContentType.Movie))
        assertEquals(1_000L, settings.sectionRefreshTime(added.providerId, ContentType.Series))

        clock.set(7_000L)
        val unchanged = coordinator.refreshMovies(added.providerId) as CatalogRefreshResult.Success
        assertEquals(RefreshStatus.Unchanged, unchanged.status)
        assertTrue(unchanged.changedSections.isEmpty())
        assertEquals(7_000L, settings.sectionRefreshTime(added.providerId, ContentType.Movie))
    }

    @Test
    fun repeatedSeriesCatalogRefreshesPreserveRetainedSeasonsEpisodesFavoritesAndHistory() = runBlocking {
        var seriesLastModified = "100"
        var episodeCount = 2
        val detailRequests = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("action=get_series_info") -> {
                        detailRequests.incrementAndGet()
                        val episodesJson = (1..episodeCount).joinToString(",") { ep ->
                            """{"id":"ep-10$ep","episode_num":"$ep","title":"Episode $ep","container_extension":"mp4","info":{"duration_secs":"1800","plot":"Episode plot $ep"}}"""
                        }
                        jsonResponse(
                            """
                            {
                              "info":{"name":"Series 1","plot":"Detailed Plot","cast":"Detailed Cast","director":"Detailed Director","genre":"Sci-Fi","releaseDate":"2025","rating":"9.2","rating_5based":"4.6","youtube_trailer":"detailed-trailer","episode_run_time":"50","backdrop_path":["http://example.invalid/backdrop1.jpg"],"tmdb_id":"999"},
                              "seasons":[{"season_number":1,"episode_count":$episodeCount,"name":"Season 1"}],
                              "episodes":{"1":[$episodesJson]}
                            }
                            """.trimIndent(),
                        )
                    }
                    path.contains("action=get_series") && !path.contains("categories") -> {
                        jsonResponse(
                            """
                            [
                              {"series_id":"1","name":"Series 1","cover":"http://example.invalid/s1.png","plot":null,"cast":"","director":null,"genre":"Drama","releaseDate":"2024","rating":"8.0","youtube_trailer":null,"episode_run_time":null,"category_id":"series-1","last_modified":"$seriesLastModified"},
                              {"series_id":"2","name":"Series 2","cover":"http://example.invalid/s2.png","plot":"Plot 2","cast":"Cast 2","director":"Dir 2","genre":"Action","releaseDate":"2024","rating":"7.5","youtube_trailer":"tr2","episode_run_time":"45","category_id":"series-1","last_modified":"100"}
                            ]
                            """.trimIndent(),
                        )
                    }
                    else -> xtreamDispatcherResponse(path, live = 1, movies = 1, series = 2)
                }
            }
        }

        val added = xtreamRepository.addProvider(xtreamInput())
        val providerId = added.providerId

        // Newly imported catalogue series has updatedAtEpochMs == 0L so first details() call fetches seasons & episodes
        val seriesItem1 = seriesRepository.item(providerId, "1")!!
        clock.set(200_000L)
        val initialDetails = seriesRepository.details(seriesItem1)
        assertEquals(1, detailRequests.get())
        assertEquals(2, initialDetails.episodes.size)
        assertEquals(1, initialDetails.seasons.size)
        assertEquals("http://example.invalid/backdrop1.jpg", initialDetails.backdropUrl)
        assertEquals(999, initialDetails.tmdbId)

        // Add series-1 to favourites and ep-101 to watch history
        database.favoriteDao().upsert(
            FavoriteEntity(providerId.value, ContentType.Series.persisted, "1", "", "Series 1", null, 200_000L),
        )
        database.watchHistoryDao().upsert(
            WatchHistoryEntity(providerId.value, ContentType.Episode.persisted, "1", "ep-101", "Episode 1", null, 300_000L, 1_800_000L, 200_000L),
        )

        database.seriesDaoImpl.resetWriteCounters()

        // Perform 3 repeated catalogue refreshes with unchanged series data
        repeat(3) { index ->
            clock.set(210_000L + index * 1_000L)
            val refreshResult = coordinator.refreshSeries(providerId) as CatalogRefreshResult.Success
            assertEquals(RefreshStatus.Unchanged, refreshResult.status)
        }

        // Unchanged parent series rows must NOT be deleted or reinserted!
        assertEquals(0, database.seriesDaoImpl.deleteByProviderCalls)
        assertEquals(0, database.seriesDaoImpl.deleteByIdsCalls)
        assertEquals(0, database.seriesDaoImpl.upsertCalls)

        // Cached seasons, episodes, detail-enriched fields, favourites, and watch history are all preserved!
        assertEquals(2, database.episodeDao().getBySeries(providerId.value, "1").size)
        assertEquals(1, database.seriesDao().getSeasons(providerId.value, "1").size)
        val preservedSeries = database.seriesDao().find(providerId.value, "1")!!
        assertEquals("http://example.invalid/backdrop1.jpg", preservedSeries.backdropUrl)
        assertEquals("4.6", preservedSeries.rating5Based)
        assertEquals(999, preservedSeries.tmdbId)
        assertEquals("Detailed Plot", preservedSeries.plot)
        assertEquals("Detailed Cast", preservedSeries.cast)
        assertNotNull(database.favoriteDao().find(providerId.value, ContentType.Series.persisted, "1"))
        assertNotNull(database.watchHistoryDao().find(providerId.value, ContentType.Episode.persisted, "1", "ep-101"))

        // Calling details() within TTL uses preserved cache without a network request
        val cachedDetails = seriesRepository.details(seriesRepository.item(providerId, "1")!!)
        assertEquals(1, detailRequests.get())
        assertEquals(2, cachedDetails.episodes.size)

        // Now server adds Episode 3 and bumps last_modified to 300 seconds (300_000 ms > 200_000 ms)
        seriesLastModified = "300"
        episodeCount = 3
        clock.set(250_000L)
        val updatedCatalog = coordinator.refreshSeries(providerId) as CatalogRefreshResult.Success
        assertEquals(RefreshStatus.Updated, updatedCatalog.status)
        // Existing 2 episodes are STILL preserved in Room before user opens details
        assertEquals(2, database.episodeDao().getBySeries(providerId.value, "1").size)
        // And opening details() now discovers Episode 3 because last_modified invalidated detail freshness!
        val refreshedDetails = seriesRepository.details(seriesRepository.item(providerId, "1")!!)
        assertEquals(2, detailRequests.get())
        assertEquals(3, refreshedDetails.episodes.size)
    }

    @Test
    fun removedSeriesCleanupDeletesObsoleteSeriesAndCascadesOrphanedEpisodesAcrossLargeCatalogs() = runBlocking {
        val providerId = ProviderId("xtream-large")
        database.providerDao().upsert(
            ProviderEntity(providerId.value, "Large", "XTREAM", server.url("/").toString(), 1_000L, 1_000L, 1_000L, true),
        )
        // Seed 1,200 series (> 999 SQLite bind parameter limit), each with a season and episode
        val initialSeries = (1..1_200).map { id ->
            SeriesEntity(
                providerId = providerId.value,
                seriesId = id.toString(),
                name = "Series $id",
                normalizedName = "series $id",
                coverUrl = null,
                plot = "Plot $id",
                cast = null,
                director = null,
                genre = "Drama",
                releaseDate = null,
                rating = null,
                trailer = null,
                runtime = null,
                categoryId = "series-1",
                tmdbId = null,
                serverOrder = id,
                updatedAtEpochMs = 1_000L,
            )
        }
        val initialSeasons = (1..1_200).map { id ->
            SeasonEntity(providerId.value, id.toString(), 1, "Season 1", "s1", null, null, null, null, null, 1, 1_000L)
        }
        val initialEpisodes = (1..1_200).map { id ->
            EpisodeEntity(providerId.value, id.toString(), "ep-$id", 1, 1, "Ep 1", "ep 1", null, null, "mp4", null, 1200, null, null, null, null, null, null, 1_000L)
        }
        initialSeries.chunked(SQLITE_MAX_BIND_PARAMS).forEach { database.seriesDao().upsertAll(it) }
        initialSeasons.chunked(SQLITE_MAX_BIND_PARAMS).forEach { database.seriesDao().upsertSeasons(it) }
        initialEpisodes.chunked(SQLITE_MAX_BIND_PARAMS).forEach { database.episodeDao().upsertAll(it) }
        database.seriesDaoImpl.resetWriteCounters()

        // Retain only series 1..100 and remove 1,100 obsolete series (101..1200)
        val retainedSeries = initialSeries.take(100)
        val changed = database.seriesDao().replaceSeriesWithDiff(providerId.value, retainedSeries)

        assertTrue(changed)
        assertEquals(100, database.seriesDao().countByProvider(providerId.value))
        assertTrue(database.seriesDaoImpl.maxDeleteBatchSize <= SQLITE_MAX_BIND_PARAMS)
        assertEquals(3, database.seriesDaoImpl.deleteByIdsCalls) // 1,100 obsolete IDs chunked by 500 -> 500 + 500 + 100
        assertEquals(0, database.seriesDaoImpl.upsertCalls) // Retained 100 series were unchanged, so 0 upserts!
        // Retained series keep their episodes, while removed series had their episodes cascaded
        assertEquals(1, database.episodeDao().getBySeries(providerId.value, "1").size)
        assertEquals(1, database.episodeDao().getBySeries(providerId.value, "100").size)
        assertTrue(database.episodeDao().getBySeries(providerId.value, "101").isEmpty())
        assertTrue(database.episodeDao().getBySeries(providerId.value, "1200").isEmpty())
        assertEquals(100, database.episodeDao().getByProvider(providerId.value).size)
    }

    @Test
    fun emptyResponseRejectionPreservesCachedCatalogWhileAllowingLegitimateEmptyInitialCatalog() = runBlocking {
        // 1. Legitimate empty initial Xtream catalogue is allowed
        enqueueXtreamCatalog(live = 0, movies = 0, series = 0)
        val emptyInitialXtream = xtreamRepository.addProvider(xtreamInput(displayName = "Empty Initial Xtream", username = "empty-user"))
        assertEquals(0, emptyInitialXtream.liveCount)
        assertEquals(0, emptyInitialXtream.movieCount)
        assertEquals(0, emptyInitialXtream.seriesCount)

        // 2. Legitimate empty initial M3U catalogue with valid #EXTM3U header is allowed
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/x-mpegurl").setBody("#EXTM3U\n"))
        val emptyInitialM3uId = m3uRepository.addUrlProvider(
            M3uUrlInput("Empty Initial M3U", server.url("/empty.m3u").toString()),
        )
        assertEquals(0, m3uRepository.counts(emptyInitialM3uId).liveCount)

        // 3. Populate non-empty Xtream and M3U providers
        enqueueXtreamCatalog(live = 2, movies = 2, series = 2)
        val populatedXtream = xtreamRepository.addProvider(xtreamInput(displayName = "Populated Xtream", username = "pop-user"))
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/x-mpegurl").setBody(sampleM3uPlaylist()))
        val populatedM3uId = m3uRepository.addUrlProvider(
            M3uUrlInput("Populated M3U", server.url("/populated.m3u").toString()),
        )

        // 4. Suspicious empty response during refresh of populated Xtream provider is rejected
        clock.set(9_000L)
        enqueueXtreamCatalog(live = 0, movies = 2, series = 2)
        val xtreamRefreshResult = coordinator.refreshProvider(populatedXtream.providerId)
        assertTrue(xtreamRefreshResult is CatalogRefreshResult.Failure)
        assertTrue((xtreamRefreshResult as CatalogRefreshResult.Failure).cause is EmptyCatalogResponseException)
        assertEquals(2, database.liveStreamDao().countByProvider(populatedXtream.providerId.value))
        assertEquals(1_000L, database.providerDao().findById(populatedXtream.providerId.value)?.lastRefreshAtEpochMs)
        assertEquals(1_000L, settings.sectionRefreshTime(populatedXtream.providerId, ContentType.Live))

        // 5. Suspicious empty response during refresh of populated M3U provider is rejected
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/x-mpegurl").setBody("#EXTM3U\n"))
        val m3uRefreshResult = coordinator.refreshProvider(ProviderId(populatedM3uId))
        assertTrue(m3uRefreshResult is CatalogRefreshResult.Failure)
        assertTrue((m3uRefreshResult as CatalogRefreshResult.Failure).cause is EmptyCatalogResponseException)
        assertEquals(1, m3uRepository.counts(populatedM3uId).liveCount)
        assertEquals(1_000L, database.providerDao().findById(populatedM3uId)?.lastRefreshAtEpochMs)
    }

    @Test
    fun malformedResponsePreservesCachedCatalogAndDoesNotAdvanceRefreshTimestampsOrMutateLoginState() = runBlocking {
        enqueueXtreamCatalog(live = 2, movies = 2, series = 2)
        val xtream = xtreamRepository.addProvider(xtreamInput())
        val stateAfterAdd = xtreamRepository.state

        server.enqueue(MockResponse().setHeader("Content-Type", "audio/x-mpegurl").setBody(sampleM3uPlaylist()))
        val m3uId = m3uRepository.addUrlProvider(M3uUrlInput("M3U", server.url("/valid.m3u").toString()))
        val m3uStateAfterAdd = m3uRepository.state.value
        assertTrue(m3uStateAfterAdd is M3uImportState.Success)

        // Malformed JSON on Xtream refresh
        clock.set(12_000L)
        server.enqueue(jsonResponse("""{"user_info":{"username":"fake-user","auth":1,"status":"Active"},"server_info":{}}"""))
        server.enqueue(jsonResponse("""[{"category_id":"live-1","category_name":"LIVE 1","parent_id":0}]"""))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"malformed_live_streams":"""))

        val xtreamFailure = coordinator.refreshLive(xtream.providerId, RefreshTrigger.AutomaticPeriodic)
        assertTrue(xtreamFailure is CatalogRefreshResult.Failure)
        assertEquals(2, database.liveStreamDao().countByProvider(xtream.providerId.value))
        assertEquals(1_000L, database.providerDao().findById(xtream.providerId.value)?.lastRefreshAtEpochMs)
        assertEquals(1_000L, settings.sectionRefreshTime(xtream.providerId, ContentType.Live))

        // Malformed HLS media playlist on M3U refresh
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "audio/x-mpegurl")
                .setBody("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment0.ts\n"),
        )
        val m3uFailure = coordinator.refreshProvider(ProviderId(m3uId), RefreshTrigger.AutomaticPeriodic)
        assertTrue(m3uFailure is CatalogRefreshResult.Failure)
        assertEquals(1, m3uRepository.counts(m3uId).liveCount)
        assertEquals(1_000L, database.providerDao().findById(m3uId)?.lastRefreshAtEpochMs)
        assertEquals(1_000L, settings.sectionRefreshTime(ProviderId(m3uId), ContentType.Live))
        // Login state was not overwritten by background refresh failure
        assertEquals(m3uStateAfterAdd, m3uRepository.state.value)
        assertNotNull(stateAfterAdd)
    }

    @Test
    fun searchAndSportsChannelCachesAreInvalidatedOnCommittedCatalogChanges() = runBlocking {
        enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
        val added = xtreamRepository.addProvider(xtreamInput())
        val providerId = added.providerId

        // Populate search repository snapshot and sports match cache for this provider
        val initialSearch = searchRepository.search(providerId, "Live", SearchScope.Global)
        assertEquals(1, initialSearch.live.size)

        sportsMatchCache.put(
            CachedSportsMatch(
                providerId = providerId.value,
                fixtureKey = "fixture-1",
                epgFingerprint = "fp-1",
                state = SportsBroadcastState.CHANNEL_MATCH_AVAILABLE,
                candidates = emptyList(),
                fetchedAtEpochMs = 1_000L,
                expiresAtEpochMs = 999_999L,
            ),
        )
        assertNotNull(sportsMatchCache.get(providerId, "fixture-1"))

        // Refresh Live with 2 channels ("Live 1" and "Live 2")
        clock.set(15_000L)
        server.enqueue(jsonResponse("""{"user_info":{"username":"fake-user","auth":1,"status":"Active"},"server_info":{}}"""))
        server.enqueue(jsonResponse(categoriesJson("live", 1)))
        server.enqueue(jsonResponse(liveStreamsJson(2)))

        val liveRefresh = coordinator.refreshLive(providerId)
        assertTrue(liveRefresh is CatalogRefreshResult.Success)

        // Sports match cache for providerId was invalidated on Live refresh
        assertNull(sportsMatchCache.get(providerId, "fixture-1"))
        // Search repository cache was invalidated so new channel "Live 2" is immediately searchable
        val updatedSearch = searchRepository.search(providerId, "Live 2", SearchScope.Global)
        assertEquals(2, updatedSearch.live.size)
        assertEquals("Live 2", updatedSearch.live.first().title)
    }

    @Test
    fun catalogUpdateEventsEmitOnlyOnChangedRefreshAndManualGetOrThrowPreservesCompatibility() = runBlocking {
        enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
        val added = xtreamRepository.addProvider(xtreamInput())
        val providerId = added.providerId

        val emittedEvents = mutableListOf<CatalogUpdateEvent>()
        val collectorJob = async(Dispatchers.Unconfined) {
            coordinator.updates.collect { emittedEvents += it }
        }

        try {
            // 1. Unchanged refresh: getOrThrow() succeeds for manual refresh, and event has status = Unchanged and empty changedSections
            clock.set(20_000L)
            server.enqueue(jsonResponse("""{"user_info":{"username":"fake-user","auth":1,"status":"Active"},"server_info":{}}"""))
            server.enqueue(jsonResponse(categoriesJson("live", 1)))
            server.enqueue(jsonResponse(liveStreamsJson(1)))
            val unchanged = coordinator.refreshLive(providerId, RefreshTrigger.Manual)
            val unwrappedUnchanged = unchanged.getOrThrow()
            assertEquals(RefreshStatus.Unchanged, unwrappedUnchanged.status)
            assertEquals(1, emittedEvents.size)
            assertEquals(RefreshStatus.Unchanged, emittedEvents.last().status)
            assertTrue(emittedEvents.last().changedSections.isEmpty())

            // 2. Updated refresh: getOrThrow() succeeds and CatalogUpdateEvent has status = Updated and changedSections = {Live}
            clock.set(25_000L)
            server.enqueue(jsonResponse("""{"user_info":{"username":"fake-user","auth":1,"status":"Active"},"server_info":{}}"""))
            server.enqueue(jsonResponse(categoriesJson("live", 1)))
            server.enqueue(jsonResponse(liveStreamsJson(2)))
            val updated = coordinator.refreshLive(providerId, RefreshTrigger.AutomaticPeriodic)
            val unwrappedUpdated = updated.getOrThrow()
            assertEquals(RefreshStatus.Updated, unwrappedUpdated.status)
            assertEquals(2, emittedEvents.size)
            assertEquals(providerId, emittedEvents.last().providerId)
            assertEquals(RefreshStatus.Updated, emittedEvents.last().status)
            assertEquals(setOf(ContentType.Live), emittedEvents.last().changedSections)
            assertEquals(RefreshTrigger.AutomaticPeriodic, emittedEvents.last().trigger)

            // 3. Disabled provider with automatic trigger returns Skipped, and missing provider with Manual returns Skipped; getOrThrow() throws
            val existingEntity = database.providerDao().findById(providerId.value)!!
            database.providerDao().upsert(existingEntity.copy(enabled = false))
            val skipped = coordinator.refreshProvider(providerId, RefreshTrigger.AutomaticPeriodic)
            assertTrue(skipped is CatalogRefreshResult.Skipped)
            assertTrue(runCatching { skipped.getOrThrow() }.isFailure)

            val missingManual = coordinator.refreshProvider(ProviderId("missing-provider"), RefreshTrigger.Manual)
            assertTrue(missingManual is CatalogRefreshResult.Skipped)
            assertTrue(runCatching { missingManual.getOrThrow() }.isFailure)
        } finally {
            collectorJob.cancel()
        }
    }

    private fun xtreamInput(
        displayName: String = "Provider A",
        username: String = "fake-user",
    ) = XtreamCredentialsInput(
        displayName = displayName,
        serverUrl = server.url("/").toString(),
        username = username,
        password = "fake-password",
    )

    private fun enqueueXtreamCatalog(live: Int, movies: Int, series: Int) {
        server.enqueue(jsonResponse("""{"user_info":{"username":"fake-user","auth":1,"status":"Active","active_cons":"1","max_connections":"2","allowed_output_formats":["ts","m3u8"]},"server_info":{"url":"example.invalid","server_protocol":"http"}}"""))
        server.enqueue(jsonResponse(categoriesJson("live", if (live > 0) 1 else 0)))
        server.enqueue(jsonResponse(liveStreamsJson(live)))
        server.enqueue(jsonResponse(categoriesJson("movie", if (movies > 0) 1 else 0)))
        server.enqueue(jsonResponse(vodStreamsJson(movies)))
        server.enqueue(jsonResponse(categoriesJson("series", if (series > 0) 1 else 0)))
        server.enqueue(jsonResponse(seriesJson(series)))
    }

    private fun xtreamDispatcherResponse(path: String, live: Int, movies: Int, series: Int): MockResponse = when {
        path.contains("action=get_live_categories") -> jsonResponse(categoriesJson("live", 1))
        path.contains("action=get_live_streams") -> jsonResponse(liveStreamsJson(live))
        path.contains("action=get_vod_categories") -> jsonResponse(categoriesJson("movie", 1))
        path.contains("action=get_vod_streams") -> jsonResponse(vodStreamsJson(movies))
        path.contains("action=get_series_categories") -> jsonResponse(categoriesJson("series", 1))
        path.contains("action=get_series") -> jsonResponse(seriesJson(series))
        else -> jsonResponse("""{"user_info":{"username":"fake-user","auth":1,"status":"Active"},"server_info":{}}""")
    }

    private fun categoriesJson(prefix: String, count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"category_id":"$prefix-$index","category_name":"${prefix.uppercase()} $index","parent_id":0}"""
        }

    private fun liveStreamsJson(count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"stream_id":$index,"name":"Live $index","stream_icon":"http://example.invalid/$index.png","category_id":"live-1","epg_channel_id":"live.$index","container_extension":"ts"}"""
        }

    private fun vodStreamsJson(count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"stream_id":"$index","name":"Movie $index","stream_icon":"http://example.invalid/m$index.png","category_id":"movie-1","rating":"7","container_extension":"mp4","genre":"Drama","youtube_trailer":"trailer"}"""
        }

    private fun seriesJson(count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"series_id":"$index","name":"Series $index","cover":"http://example.invalid/s$index.png","plot":"Plot","cast":"Cast","director":"Director","genre":"Drama","releaseDate":"2024","rating":"8","youtube_trailer":"trailer","episode_run_time":"45","category_id":"series-1","last_modified":"100"}"""
        }

    private fun sampleM3uPlaylist(liveTitle: String = "Live One"): String =
        """
        #EXTM3U url-tvg="http://example.invalid/epg.xml"
        #EXTINF:-1 tvg-id="live-one" group-title="Live",$liveTitle
        http://example.invalid/live/one.ts
        #EXTINF:-1 group-title="Movies",Movie One
        http://example.invalid/movie/1.mp4
        #EXTINF:-1 group-title="Series",Series One S01E01
        http://example.invalid/series/1.mkv
        """.trimIndent()

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    internal class MutableClock(initial: Long) : WatchioClock {
        private val now = AtomicLong(initial)
        override fun nowEpochMs(): Long = now.get()
        fun set(value: Long) { now.set(value) }
        fun advanceBy(deltaMs: Long) { now.addAndGet(deltaMs) }
    }

    internal class FakeSecretStore : SecretStore {
        private val map = LinkedHashMap<String, String>()
        override suspend fun putSecret(key: String, value: String) { map[key] = value }
        override suspend fun getSecret(key: String): String? = map[key]
        override suspend fun removeSecret(key: String) { map.remove(key) }
    }

    internal class InMemorySearchHistoryStore : SearchHistoryStore {
        private val items = HashMap<ProviderId, List<String>>()
        override fun get(providerId: ProviderId): List<String> = items[providerId].orEmpty()
        override fun put(providerId: ProviderId, queries: List<String>) { items[providerId] = queries }
    }

    internal class FakeSettingsRepository : SettingsRepository {
        private val selected = MutableStateFlow<ProviderId?>(null)
        private val autoContentRefresh = MutableStateFlow(true)
        private val sectionTimes = HashMap<Pair<ProviderId, ContentType>, MutableStateFlow<Long?>>()
        private val expiries = HashMap<ProviderId, MutableStateFlow<Long?>>()
        private val metadata = HashMap<ProviderId, MutableStateFlow<XtreamAccountMetadata>>()

        override val selectedProviderId: Flow<ProviderId?> = selected
        override val automaticContentRefreshEnabled: Flow<Boolean> = autoContentRefresh
        override val inputMode: Flow<InputMode> = MutableStateFlow(InputMode.Auto)
        override val streamFormat: Flow<StreamFormat> = MutableStateFlow(StreamFormat.Auto)

        override suspend fun setSelectedProviderId(providerId: ProviderId?) {
            selected.value = providerId
        }
        override suspend fun setAutomaticContentRefreshEnabled(enabled: Boolean) {
            autoContentRefresh.value = enabled
        }
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit

        override fun observeProviderExpiryEpochMs(providerId: ProviderId): Flow<Long?> =
            expiries.getOrPut(providerId) { MutableStateFlow(null) }

        override suspend fun setProviderExpiryEpochMs(providerId: ProviderId, expiryEpochMs: Long?) {
            expiries.getOrPut(providerId) { MutableStateFlow(null) }.value = expiryEpochMs
        }

        override fun observeSectionRefreshEpochMs(providerId: ProviderId, contentType: ContentType): Flow<Long?> =
            sectionTimes.getOrPut(providerId to contentType) { MutableStateFlow(null) }

        override suspend fun setSectionRefreshEpochMs(providerId: ProviderId, contentType: ContentType, epochMs: Long?) {
            sectionTimes.getOrPut(providerId to contentType) { MutableStateFlow(null) }.value = epochMs
        }

        fun sectionRefreshTime(providerId: ProviderId, contentType: ContentType): Long? =
            sectionTimes[providerId to contentType]?.value

        override fun observeXtreamAccountMetadata(providerId: ProviderId): Flow<XtreamAccountMetadata> =
            metadata.getOrPut(providerId) { MutableStateFlow(XtreamAccountMetadata()) }

        override suspend fun setXtreamAccountMetadata(providerId: ProviderId, metadata: XtreamAccountMetadata) {
            this.metadata.getOrPut(providerId) { MutableStateFlow(XtreamAccountMetadata()) }.value = metadata
        }
    }

    internal class FakeWatchioDatabase : WatchioDatabase() {
        val providerDaoImpl = FakeProviderDao()
        val categoryDaoImpl = FakeCategoryDao()
        val liveStreamDaoImpl = FakeLiveStreamDao()
        val vodDaoImpl = FakeVodDao()
        val episodeDaoImpl = FakeEpisodeDao()
        val seriesDaoImpl = FakeSeriesDao(episodeDaoImpl)
        val favoriteDaoImpl = FakeFavoriteDao()
        val watchHistoryDaoImpl = FakeWatchHistoryDao()
        val m3uItemDaoImpl = FakeM3uItemDao()
        val epgDaoImpl = FakeEpgDao()
        val movieDetailDaoImpl = FakeMovieDetailDao()
        val appMetadataDaoImpl = FakeAppMetadataDao()
        val sportsCacheDaoImpl = FakeSportsCacheDao()
        private val offlineRows = LinkedHashMap<String, OfflineDownloadEntity>()
        private val offlineFlow = MutableStateFlow<List<OfflineDownloadEntity>>(emptyList())
        private val offlineDownloadDaoImpl = object : OfflineDownloadDao {
            override fun observeAll(): Flow<List<OfflineDownloadEntity>> = offlineFlow
            override suspend fun getAll(): List<OfflineDownloadEntity> = offlineRows.values.toList()
            override suspend fun findById(downloadId: String): OfflineDownloadEntity? = offlineRows[downloadId]
            override suspend fun findByContent(providerId: String, contentId: String): OfflineDownloadEntity? =
                offlineRows.values.firstOrNull { it.providerId == providerId && it.contentId == contentId }
            override suspend fun getSeason(providerId: String, showId: String, seasonNumber: Int): List<OfflineDownloadEntity> =
                offlineRows.values.filter { it.providerId == providerId && it.showId == showId && it.seasonNumber == seasonNumber }
            override fun findByIdBlocking(downloadId: String): OfflineDownloadEntity? = offlineRows[downloadId]
            override suspend fun upsert(entity: OfflineDownloadEntity) {
                offlineRows[entity.downloadId] = entity
                offlineFlow.value = offlineRows.values.toList()
            }
            override suspend fun updateLocalPoster(downloadId: String, localPosterPath: String, updatedAtEpochMs: Long) {
                offlineRows[downloadId]?.let { offlineRows[downloadId] = it.copy(localPosterPath = localPosterPath, updatedAtEpochMs = updatedAtEpochMs) }
            }
            override suspend fun updateLocalBackdrop(downloadId: String, path: String, updatedAtEpochMs: Long) {
                offlineRows[downloadId]?.let { offlineRows[downloadId] = it.copy(localBackdropPath = path, updatedAtEpochMs = updatedAtEpochMs) }
            }
            override suspend fun updateLocalThumbnail(downloadId: String, path: String, updatedAtEpochMs: Long) {
                offlineRows[downloadId]?.let { offlineRows[downloadId] = it.copy(localThumbnailPath = path, updatedAtEpochMs = updatedAtEpochMs) }
            }
            override suspend fun updateOfflineMetadata(
                downloadId: String,
                title: String,
                synopsis: String?,
                releaseYear: String?,
                genre: String?,
                rating: String?,
                runtime: String?,
                showTitle: String?,
                episodeTitle: String?,
                updatedAtEpochMs: Long,
            ) {
                offlineRows[downloadId]?.let {
                    offlineRows[downloadId] = it.copy(
                        title = title, synopsis = synopsis, releaseYear = releaseYear, genre = genre,
                        rating = rating, runtime = runtime, showTitle = showTitle, episodeTitle = episodeTitle,
                        updatedAtEpochMs = updatedAtEpochMs,
                    )
                }
            }
            override suspend fun updateProgress(
                downloadId: String,
                state: String,
                bytesDownloaded: Long,
                contentLength: Long,
                percentDownloaded: Float,
                failureReason: String?,
                updatedAtEpochMs: Long,
                completedAtEpochMs: Long?,
            ) {
                offlineRows[downloadId]?.let { row ->
                    offlineRows[downloadId] = row.copy(
                        state = state,
                        bytesDownloaded = bytesDownloaded,
                        contentLength = contentLength,
                        percentDownloaded = percentDownloaded,
                        failureReason = failureReason,
                        updatedAtEpochMs = updatedAtEpochMs,
                        completedAtEpochMs = completedAtEpochMs,
                    )
                    offlineFlow.value = offlineRows.values.toList()
                }
            }
            override suspend fun delete(downloadId: String) {
                offlineRows.remove(downloadId)
                offlineFlow.value = offlineRows.values.toList()
            }
        }

        suspend fun runTransaction(block: suspend () -> Unit) {
            val snapshotProviders = providerDaoImpl.rows.toMap()
            val snapshotCategories = categoryDaoImpl.rows.toMap()
            val snapshotLive = liveStreamDaoImpl.rows.toMap()
            val snapshotVod = vodDaoImpl.rows.toMap()
            val snapshotSeries = seriesDaoImpl.rows.toMap()
            val snapshotSeasons = seriesDaoImpl.seasonRows.toMap()
            val snapshotEpisodes = episodeDaoImpl.rows.toMap()
            val snapshotM3u = m3uItemDaoImpl.rows.toMap()
            try {
                block()
            } catch (t: Throwable) {
                providerDaoImpl.rows.clear(); providerDaoImpl.rows.putAll(snapshotProviders)
                categoryDaoImpl.rows.clear(); categoryDaoImpl.rows.putAll(snapshotCategories)
                liveStreamDaoImpl.rows.clear(); liveStreamDaoImpl.rows.putAll(snapshotLive)
                vodDaoImpl.rows.clear(); vodDaoImpl.rows.putAll(snapshotVod)
                seriesDaoImpl.rows.clear(); seriesDaoImpl.rows.putAll(snapshotSeries)
                seriesDaoImpl.seasonRows.clear(); seriesDaoImpl.seasonRows.putAll(snapshotSeasons)
                episodeDaoImpl.rows.clear(); episodeDaoImpl.rows.putAll(snapshotEpisodes)
                m3uItemDaoImpl.rows.clear(); m3uItemDaoImpl.rows.putAll(snapshotM3u)
                throw t
            }
        }

        override fun appMetadataDao(): AppMetadataDao = appMetadataDaoImpl
        override fun providerDao(): ProviderDao = providerDaoImpl
        override fun categoryDao(): CategoryDao = categoryDaoImpl
        override fun liveStreamDao(): LiveStreamDao = liveStreamDaoImpl
        override fun vodDao(): VodDao = vodDaoImpl
        override fun seriesDao(): SeriesDao = seriesDaoImpl
        override fun episodeDao(): EpisodeDao = episodeDaoImpl
        override fun favoriteDao(): FavoriteDao = favoriteDaoImpl
        override fun watchHistoryDao(): WatchHistoryDao = watchHistoryDaoImpl
        override fun m3uItemDao(): M3uItemDao = m3uItemDaoImpl
        override fun epgDao(): EpgDao = epgDaoImpl
        override fun movieDetailDao(): MovieDetailDao = movieDetailDaoImpl
        override fun sportsCacheDao(): SportsCacheDao = sportsCacheDaoImpl
        override fun offlineDownloadDao(): OfflineDownloadDao = offlineDownloadDaoImpl

        @Deprecated("Deprecated in RoomDatabase")
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper =
            throw UnsupportedOperationException("Not used in unit test")

        override fun createInvalidationTracker(): InvalidationTracker =
            InvalidationTracker(this, emptyMap(), emptyMap())

        override fun clearAllTables() = Unit
    }

    internal class FakeProviderDao : ProviderDao {
        val rows = LinkedHashMap<String, ProviderEntity>()
        private val flow = MutableStateFlow<List<ProviderEntity>>(emptyList())

        override suspend fun upsert(provider: ProviderEntity) {
            rows[provider.id] = provider
            flow.value = rows.values.sortedBy { it.createdAtEpochMs }
        }

        override suspend fun findById(providerId: String): ProviderEntity? = rows[providerId]
        override fun observeAll(): Flow<List<ProviderEntity>> = flow
        override suspend fun getAll(): List<ProviderEntity> = rows.values.sortedBy { it.createdAtEpochMs }
        override suspend fun findByTypeAndServer(type: String, serverUrl: String): List<ProviderEntity> =
            rows.values.filter { it.type == type && it.serverUrl == serverUrl }.sortedBy { it.createdAtEpochMs }

        override suspend fun deleteProvider(providerId: String) {
            rows.remove(providerId)
            flow.value = rows.values.sortedBy { it.createdAtEpochMs }
        }
    }

    internal class FakeCategoryDao : CategoryDao {
        val rows = LinkedHashMap<Triple<String, String, String>, CategoryEntity>()

        override suspend fun upsertAll(categories: List<CategoryEntity>) {
            require(categories.size <= SQLITE_MAX_BIND_PARAMS) { "Exceeded SQLITE_MAX_BIND_PARAMS" }
            categories.forEach { rows[Triple(it.providerId, it.contentType, it.categoryId)] = it }
        }

        override suspend fun deleteByProviderAndType(providerId: String, contentType: String) {
            rows.keys.removeIf { it.first == providerId && it.second == contentType }
        }

        override fun observeByType(providerId: String, contentType: String): Flow<List<CategoryEntity>> =
            MutableStateFlow(rows.values.filter { it.providerId == providerId && it.contentType == contentType }.sortedWith(compareBy({ it.serverOrder }, { it.name })))

        override suspend fun getByType(providerId: String, contentType: String): List<CategoryEntity> =
            rows.values.filter { it.providerId == providerId && it.contentType == contentType }.sortedWith(compareBy({ it.serverOrder }, { it.name }))
    }

    internal class FakeLiveStreamDao : LiveStreamDao {
        val rows = LinkedHashMap<Pair<String, String>, LiveStreamEntity>()

        override suspend fun upsertAll(streams: List<LiveStreamEntity>) {
            require(streams.size <= SQLITE_MAX_BIND_PARAMS) { "Exceeded SQLITE_MAX_BIND_PARAMS" }
            streams.forEach { rows[it.providerId to it.streamId] = it }
        }

        override suspend fun deleteByProvider(providerId: String) {
            rows.keys.removeIf { it.first == providerId }
        }

        override suspend fun getByCategory(providerId: String, categoryId: String): List<LiveStreamEntity> =
            rows.values.filter { it.providerId == providerId && it.categoryId == categoryId }.sortedWith(compareBy({ it.serverOrder }, { it.name }))

        override suspend fun getByProvider(providerId: String): List<LiveStreamEntity> =
            rows.values.filter { it.providerId == providerId }.sortedWith(compareBy({ it.serverOrder }, { it.name }))

        override suspend fun getSearchRows(providerId: String): List<CatalogSearchRow> =
            getByProvider(providerId).map {
                CatalogSearchRow(it.streamId, it.name, it.normalizedName, it.iconUrl, it.categoryId, null, it.serverOrder, null)
            }

        override suspend fun search(providerId: String, query: String, limit: Int): List<LiveStreamEntity> =
            getByProvider(providerId).filter { it.normalizedName.contains(query) }.take(limit)

        override suspend fun countByProvider(providerId: String): Int =
            rows.values.count { it.providerId == providerId }
    }

    internal class FakeVodDao : VodDao {
        val rows = LinkedHashMap<Pair<String, String>, VodStreamEntity>()

        override suspend fun upsertAll(movies: List<VodStreamEntity>) {
            require(movies.size <= SQLITE_MAX_BIND_PARAMS) { "Exceeded SQLITE_MAX_BIND_PARAMS" }
            movies.forEach { rows[it.providerId to it.streamId] = it }
        }

        override suspend fun deleteByProvider(providerId: String) {
            rows.keys.removeIf { it.first == providerId }
        }

        override suspend fun getByProvider(providerId: String): List<VodStreamEntity> =
            rows.values.filter { it.providerId == providerId }.sortedWith(compareBy({ it.serverOrder }, { it.name }))

        override suspend fun getSearchRows(providerId: String): List<CatalogSearchRow> =
            getByProvider(providerId).map {
                CatalogSearchRow(it.streamId, it.name, it.normalizedName, it.posterUrl, it.genre, it.rating, it.serverOrder, null)
            }

        override suspend fun getPage(providerId: String, limit: Int, offset: Int): List<VodStreamEntity> =
            getByProvider(providerId).drop(offset).take(limit)

        override suspend fun getCategoryPage(providerId: String, categoryId: String, limit: Int, offset: Int): List<VodStreamEntity> =
            getByCategory(providerId, categoryId).drop(offset).take(limit)

        override suspend fun getByIds(providerId: String, streamIds: List<String>): List<VodStreamEntity> =
            streamIds.mapNotNull { rows[providerId to it] }

        override suspend fun getByCategory(providerId: String, categoryId: String): List<VodStreamEntity> =
            getByProvider(providerId).filter { it.categoryId == categoryId }

        override suspend fun search(providerId: String, query: String, limit: Int): List<VodStreamEntity> =
            getByProvider(providerId).filter { it.normalizedName.contains(query) }.take(limit)

        override suspend fun find(providerId: String, streamId: String): VodStreamEntity? = rows[providerId to streamId]

        override suspend fun countByProvider(providerId: String): Int =
            rows.values.count { it.providerId == providerId }
    }

    internal class FakeEpisodeDao : EpisodeDao {
        val rows = LinkedHashMap<Triple<String, String, String>, EpisodeEntity>()

        override suspend fun upsertAll(episodes: List<EpisodeEntity>) {
            require(episodes.size <= SQLITE_MAX_BIND_PARAMS) { "Exceeded SQLITE_MAX_BIND_PARAMS" }
            episodes.forEach { rows[Triple(it.providerId, it.seriesId, it.episodeId)] = it }
        }

        override suspend fun getBySeries(providerId: String, seriesId: String): List<EpisodeEntity> =
            rows.values.filter { it.providerId == providerId && it.seriesId == seriesId }
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

        override suspend fun getBySeason(providerId: String, seriesId: String, seasonNumber: Int): List<EpisodeEntity> =
            getBySeries(providerId, seriesId).filter { it.seasonNumber == seasonNumber }

        override suspend fun find(providerId: String, seriesId: String, episodeId: String): EpisodeEntity? =
            rows[Triple(providerId, seriesId, episodeId)]

        override suspend fun getByProvider(providerId: String): List<EpisodeEntity> =
            rows.values.filter { it.providerId == providerId }

        override suspend fun getByIds(providerId: String, episodeIds: List<String>): List<EpisodeEntity> =
            rows.values.filter { it.providerId == providerId && it.episodeId in episodeIds }
    }

    internal class FakeSeriesDao(private val episodeDao: FakeEpisodeDao) : SeriesDao {
        val rows = LinkedHashMap<Pair<String, String>, SeriesEntity>()
        val seasonRows = LinkedHashMap<Triple<String, String, Int>, SeasonEntity>()
        var deleteByProviderCalls = 0
        var deleteByIdsCalls = 0
        var maxDeleteBatchSize = 0
        var upsertCalls = 0

        fun resetWriteCounters() {
            deleteByProviderCalls = 0
            deleteByIdsCalls = 0
            maxDeleteBatchSize = 0
            upsertCalls = 0
        }

        override suspend fun upsertAll(series: List<SeriesEntity>) {
            require(series.size <= SQLITE_MAX_BIND_PARAMS) { "Exceeded SQLITE_MAX_BIND_PARAMS" }
            upsertCalls += 1
            series.forEach { rows[it.providerId to it.seriesId] = it }
        }

        override suspend fun upsertSeasons(seasons: List<SeasonEntity>) {
            require(seasons.size <= SQLITE_MAX_BIND_PARAMS) { "Exceeded SQLITE_MAX_BIND_PARAMS" }
            seasons.forEach { seasonRows[Triple(it.providerId, it.seriesId, it.seasonNumber)] = it }
        }

        override suspend fun deleteByProvider(providerId: String) {
            deleteByProviderCalls += 1
            rows.keys.removeIf { it.first == providerId }
            // Simulate Room ForeignKey.CASCADE on SeriesEntity deletion
            seasonRows.keys.removeIf { it.first == providerId }
            episodeDao.rows.keys.removeIf { it.first == providerId }
        }

        override suspend fun deleteByIds(providerId: String, seriesIds: List<String>) {
            require(seriesIds.size <= SQLITE_MAX_BIND_PARAMS) { "Exceeded SQLITE_MAX_BIND_PARAMS" }
            deleteByIdsCalls += 1
            maxDeleteBatchSize = maxOf(maxDeleteBatchSize, seriesIds.size)
            val idSet = seriesIds.toHashSet()
            rows.keys.removeIf { it.first == providerId && it.second in idSet }
            // Simulate Room ForeignKey.CASCADE on deleted seriesIds
            seasonRows.keys.removeIf { it.first == providerId && it.second in idSet }
            episodeDao.rows.keys.removeIf { it.first == providerId && it.second in idSet }
        }

        override suspend fun getByProvider(providerId: String): List<SeriesEntity> =
            rows.values.filter { it.providerId == providerId }.sortedWith(compareBy({ it.serverOrder }, { it.name }))

        override suspend fun getSearchRows(providerId: String): List<CatalogSearchRow> =
            getByProvider(providerId).map {
                CatalogSearchRow(it.seriesId, it.name, it.normalizedName, it.coverUrl, it.genre, it.rating, it.serverOrder, null)
            }

        override suspend fun getPage(providerId: String, limit: Int, offset: Int): List<SeriesEntity> =
            getByProvider(providerId).drop(offset).take(limit)

        override suspend fun getCategoryPage(providerId: String, categoryId: String, limit: Int, offset: Int): List<SeriesEntity> =
            getByCategory(providerId, categoryId).drop(offset).take(limit)

        override suspend fun getByIds(providerId: String, seriesIds: List<String>): List<SeriesEntity> =
            seriesIds.mapNotNull { rows[providerId to it] }

        override suspend fun find(providerId: String, seriesId: String): SeriesEntity? = rows[providerId to seriesId]

        override suspend fun getByCategory(providerId: String, categoryId: String): List<SeriesEntity> =
            getByProvider(providerId).filter { it.categoryId == categoryId }

        override suspend fun search(providerId: String, query: String, limit: Int): List<SeriesEntity> =
            getByProvider(providerId).filter { it.normalizedName.contains(query) }.take(limit)

        override suspend fun countByProvider(providerId: String): Int =
            rows.values.count { it.providerId == providerId }

        override suspend fun getSeasons(providerId: String, seriesId: String): List<SeasonEntity> =
            seasonRows.values.filter { it.providerId == providerId && it.seriesId == seriesId }.sortedBy { it.seasonNumber }

        override suspend fun deleteSeasons(providerId: String, seriesId: String) {
            seasonRows.keys.removeIf { it.first == providerId && it.second == seriesId }
        }

        override suspend fun updateDetails(
            providerId: String,
            seriesId: String,
            name: String,
            coverUrl: String?,
            plot: String?,
            cast: String?,
            director: String?,
            genre: String?,
            releaseDate: String?,
            rating: String?,
            rating5Based: String?,
            trailer: String?,
            runtime: String?,
            backdropUrl: String?,
            tmdbId: Int?,
            updatedAtEpochMs: Long,
        ) {
            val current = rows[providerId to seriesId] ?: return
            rows[providerId to seriesId] = current.copy(
                name = name,
                coverUrl = coverUrl,
                plot = plot,
                cast = cast,
                director = director,
                genre = genre,
                releaseDate = releaseDate,
                rating = rating,
                rating5Based = rating5Based,
                trailer = trailer,
                runtime = runtime,
                backdropUrl = backdropUrl,
                tmdbId = tmdbId,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }

        override suspend fun deleteEpisodes(providerId: String, seriesId: String) {
            episodeDao.rows.keys.removeIf { it.first == providerId && it.second == seriesId }
        }

        override suspend fun upsertEpisodes(episodes: List<EpisodeEntity>) {
            episodeDao.upsertAll(episodes)
        }
    }

    internal class FakeFavoriteDao : FavoriteDao {
        private val rows = LinkedHashMap<String, FavoriteEntity>()
        private fun key(providerId: String, contentType: String, contentId: String, subContentId: String) =
            "$providerId|$contentType|$contentId|$subContentId"

        override suspend fun upsert(favorite: FavoriteEntity) {
            rows[key(favorite.providerId, favorite.contentType, favorite.contentId, favorite.subContentId)] = favorite
        }

        override suspend fun delete(providerId: String, contentType: String, contentId: String, subContentId: String) {
            rows.remove(key(providerId, contentType, contentId, subContentId))
        }

        override fun observeByProvider(providerId: String): Flow<List<FavoriteEntity>> =
            MutableStateFlow(rows.values.filter { it.providerId == providerId })

        override suspend fun getByProvider(providerId: String): List<FavoriteEntity> =
            rows.values.filter { it.providerId == providerId }

        override suspend fun find(providerId: String, contentType: String, contentId: String, subContentId: String): FavoriteEntity? =
            rows[key(providerId, contentType, contentId, subContentId)]
    }

    internal class FakeWatchHistoryDao : WatchHistoryDao {
        private val rows = LinkedHashMap<String, WatchHistoryEntity>()
        private fun key(providerId: String, contentType: String, contentId: String, subContentId: String) =
            "$providerId|$contentType|$contentId|$subContentId"

        override suspend fun upsert(history: WatchHistoryEntity) {
            rows[key(history.providerId, history.contentType, history.contentId, history.subContentId)] = history
        }

        override fun observeRecent(providerId: String): Flow<List<WatchHistoryEntity>> =
            MutableStateFlow(rows.values.filter { it.providerId == providerId })

        override suspend fun getRecent(providerId: String): List<WatchHistoryEntity> =
            rows.values.filter { it.providerId == providerId }

        override suspend fun find(providerId: String, contentType: String, contentId: String, subContentId: String): WatchHistoryEntity? =
            rows[key(providerId, contentType, contentId, subContentId)]
    }

    internal class FakeM3uItemDao : M3uItemDao {
        val rows = LinkedHashMap<Pair<String, String>, M3uItemEntity>()
        val staged = LinkedHashMap<Pair<String, String>, M3uImportItemEntity>()

        override suspend fun upsertAll(items: List<M3uItemEntity>) {
            items.forEach { rows[it.providerId to it.itemId] = it }
        }

        override suspend fun upsertStaged(items: List<M3uImportItemEntity>) {
            items.forEach { staged[it.sessionId to it.itemId] = it }
        }

        override suspend fun deleteByProvider(providerId: String) {
            rows.keys.removeIf { it.first == providerId }
        }

        override suspend fun deleteByProviderAndType(providerId: String, contentType: String) {
            rows.entries.removeIf { it.value.providerId == providerId && it.value.contentType == contentType }
        }

        override suspend fun deleteStaged(sessionId: String) {
            staged.keys.removeIf { it.first == sessionId }
        }

        override suspend fun getStagedBatch(sessionId: String, limit: Int, offset: Int): List<M3uImportItemEntity> =
            staged.values.filter { it.sessionId == sessionId }.sortedBy { it.playlistOrder }.drop(offset).take(limit)

        override suspend fun getStagedBatchByType(sessionId: String, contentType: String, limit: Int, offset: Int): List<M3uImportItemEntity> =
            staged.values.filter { it.sessionId == sessionId && it.contentType == contentType }.sortedBy { it.playlistOrder }.drop(offset).take(limit)

        override suspend fun stagedCount(sessionId: String): Int =
            staged.values.count { it.sessionId == sessionId }

        override suspend fun countByProviderAndType(providerId: String, contentType: String): Int =
            rows.values.count { it.providerId == providerId && it.contentType == contentType }

        override suspend fun getFirstByProvider(providerId: String, limit: Int): List<M3uItemEntity> =
            rows.values.filter { it.providerId == providerId }.sortedBy { it.playlistOrder }.take(limit)

        override suspend fun getByProviderAndType(providerId: String, contentType: String): List<M3uItemEntity> =
            rows.values.filter { it.providerId == providerId && it.contentType == contentType }.sortedWith(compareBy({ it.playlistOrder }, { it.name }))

        override suspend fun getSearchRowsByType(providerId: String, contentType: String): List<CatalogSearchRow> =
            getByProviderAndType(providerId, contentType).map {
                CatalogSearchRow(it.itemId, it.name, it.normalizedName, it.tvgLogo, it.groupTitle, null, it.playlistOrder, it.seriesName)
            }

        override suspend fun getPageByType(providerId: String, contentType: String, limit: Int, offset: Int): List<M3uItemEntity> =
            getByProviderAndType(providerId, contentType).drop(offset).take(limit)

        override suspend fun getCategoryPageByType(providerId: String, contentType: String, categoryId: String, limit: Int, offset: Int): List<M3uItemEntity> =
            getByCategoryAndType(providerId, contentType, categoryId).drop(offset).take(limit)

        override suspend fun getSeriesPage(providerId: String, contentType: String, limit: Int, offset: Int): List<M3uItemEntity> =
            getByProviderAndType(providerId, contentType).drop(offset).take(limit)

        override suspend fun getSeriesCategoryPage(providerId: String, contentType: String, categoryId: String, limit: Int, offset: Int): List<M3uItemEntity> =
            getByCategoryAndType(providerId, contentType, categoryId).drop(offset).take(limit)

        override suspend fun searchByType(providerId: String, contentType: String, query: String, limit: Int): List<M3uItemEntity> =
            getByProviderAndType(providerId, contentType).filter { it.normalizedName.contains(query) }.take(limit)

        override suspend fun getByCategoryAndType(providerId: String, contentType: String, categoryId: String): List<M3uItemEntity> =
            getByProviderAndType(providerId, contentType).filter { it.categoryId == categoryId }

        override suspend fun find(providerId: String, itemId: String): M3uItemEntity? = rows[providerId to itemId]

        override suspend fun getByIds(providerId: String, itemIds: List<String>): List<M3uItemEntity> =
            itemIds.mapNotNull { rows[providerId to it] }
    }

    internal class FakeEpgDao : EpgDao {
        private val sources = LinkedHashMap<Pair<String, String>, EpgSourceEntity>()
        override suspend fun upsertSource(source: EpgSourceEntity) { sources[source.providerId to source.sourceId] = source }
        override suspend fun getEnabledSource(providerId: String): EpgSourceEntity? = sources.values.firstOrNull { it.providerId == providerId && it.enabled }
        override suspend fun getSources(providerId: String): List<EpgSourceEntity> = sources.values.filter { it.providerId == providerId }
        override suspend fun latestSuccess(providerId: String): Long? = null
        override suspend fun deleteSource(providerId: String, sourceId: String) { sources.remove(providerId to sourceId) }
        override suspend fun upsertStagedChannels(channels: List<EpgImportChannelEntity>) = Unit
        override suspend fun upsertStagedProgrammes(programmes: List<EpgImportProgrammeEntity>) = Unit
        override suspend fun deleteStagedChannels(sessionId: String) = Unit
        override suspend fun deleteStagedProgrammes(sessionId: String) = Unit
        override suspend fun deleteChannels(providerId: String) = Unit
        override suspend fun deleteProgrammes(providerId: String) = Unit
        override suspend fun getStagedChannelBatch(sessionId: String, limit: Int, offset: Int): List<EpgImportChannelEntity> = emptyList()
        override suspend fun getStagedProgrammeBatch(sessionId: String, limit: Int, offset: Int): List<EpgImportProgrammeEntity> = emptyList()
        override suspend fun stagedChannelCount(sessionId: String): Int = 0
        override suspend fun stagedProgrammeCount(sessionId: String): Int = 0
        override suspend fun channelCount(providerId: String): Int = 0
        override suspend fun programmeCount(providerId: String): Int = 0
        override suspend fun getChannels(providerId: String): List<EpgChannelEntity> = emptyList()
        override suspend fun getCurrentProgramme(providerId: String, epgChannelId: String, now: Long): EpgProgrammeEntity? = null
        override suspend fun getNextProgramme(providerId: String, epgChannelId: String, now: Long): EpgProgrammeEntity? = null
        override suspend fun getProgrammes(providerId: String, epgChannelId: String, fromEpochMs: Long, toEpochMs: Long, limit: Int): List<EpgProgrammeEntity> = emptyList()
        override suspend fun getGuide(providerId: String, epgChannelIds: List<String>, fromEpochMs: Long, toEpochMs: Long): List<EpgProgrammeEntity> = emptyList()
        override suspend fun prune(providerId: String, minEndEpochMs: Long, maxStartEpochMs: Long): Int = 0
        override suspend fun upsertChannels(channels: List<EpgChannelEntity>) = Unit
        override suspend fun upsertProgrammes(programmes: List<EpgProgrammeEntity>) = Unit
    }

    internal class FakeMovieDetailDao : MovieDetailDao {
        private val details = LinkedHashMap<Pair<String, String>, MovieDetailEntity>()
        private val trailers = LinkedHashMap<Pair<String, String>, TmdbTrailerCacheEntity>()
        override suspend fun upsertDetail(detail: MovieDetailEntity) { details[detail.providerId to detail.movieId] = detail }
        override suspend fun findDetail(providerId: String, movieId: String): MovieDetailEntity? = details[providerId to movieId]
        override suspend fun upsertTrailer(cache: TmdbTrailerCacheEntity) { trailers[cache.tmdbId to cache.type] = cache }
        override suspend fun findTrailer(tmdbId: String, type: String): TmdbTrailerCacheEntity? = trailers[tmdbId to type]
    }

    internal class FakeAppMetadataDao : AppMetadataDao {
        private val values = LinkedHashMap<String, String>()
        override suspend fun upsert(entity: AppMetadataEntity) { values[entity.key] = entity.value }
        override suspend fun valueForKey(key: String): String? = values[key]
    }

    internal class FakeSportsCacheDao : SportsCacheDao {
        override suspend fun upsertFixtures(fixtures: List<SportsFixtureCacheEntity>) = Unit
        override suspend fun fixtures(source: String, fromEpochMs: Long, toEpochMs: Long): List<SportsFixtureCacheEntity> = emptyList()
        override suspend fun fixture(source: String, sourceFixtureId: String): SportsFixtureCacheEntity? = null
        override suspend fun deleteRange(source: String, fromEpochMs: Long, toEpochMs: Long) = Unit
        override suspend fun deleteExpired(beforeEpochMs: Long): Int = 0
    }
}
