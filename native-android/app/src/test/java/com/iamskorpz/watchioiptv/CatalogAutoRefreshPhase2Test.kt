package com.iamskorpz.watchioiptv

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.CatalogRefreshPhase1Test.FakeSecretStore
import com.iamskorpz.watchioiptv.CatalogRefreshPhase1Test.FakeSettingsRepository
import com.iamskorpz.watchioiptv.CatalogRefreshPhase1Test.FakeWatchioDatabase
import com.iamskorpz.watchioiptv.CatalogRefreshPhase1Test.MutableClock
import com.iamskorpz.watchioiptv.core.datastore.WatchioSettingsRepository
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerMetadata
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.core.security.ProviderCredentialStore
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshCoordinator
import com.iamskorpz.watchioiptv.data.epg.EpgRepository
import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.m3u.M3uUrlInput
import com.iamskorpz.watchioiptv.data.refresh.CatalogAutoRefreshScheduler
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshCoordinator
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshResult
import com.iamskorpz.watchioiptv.data.refresh.NetworkConnectivityChecker
import com.iamskorpz.watchioiptv.data.refresh.RefreshTrigger
import com.iamskorpz.watchioiptv.data.refresh.isActivePlaybackForRefreshDeferral
import com.iamskorpz.watchioiptv.data.refresh.isPlaybackRouteForAutoRefreshDeferral
import com.iamskorpz.watchioiptv.data.xtream.XtreamCredentialsInput
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.feature.settings.SettingsViewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogAutoRefreshPhase2Test {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: FakeWatchioDatabase
    private lateinit var server: MockWebServer
    private lateinit var secretStore: FakeSecretStore
    private lateinit var credentialStore: ProviderCredentialStore
    private lateinit var settings: FakeSettingsRepository
    private lateinit var clock: MutableClock
    private lateinit var xtreamRepository: XtreamRepository
    private lateinit var m3uRepository: M3uRepository
    private lateinit var coordinator: CatalogRefreshCoordinator
    private lateinit var onlineState: AtomicBoolean
    private lateinit var connectivityChecker: NetworkConnectivityChecker
    private lateinit var playerStateFlow: MutableStateFlow<WatchioPlayerState>

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
        Dispatchers.setMain(Dispatchers.Unconfined)
        database = FakeWatchioDatabase()
        server = MockWebServer()
        server.start()
        secretStore = FakeSecretStore()
        credentialStore = ProviderCredentialStore(secretStore)
        settings = FakeSettingsRepository()
        clock = MutableClock(1_000L)
        onlineState = AtomicBoolean(true)
        connectivityChecker = NetworkConnectivityChecker { onlineState.get() }
        playerStateFlow = MutableStateFlow(WatchioPlayerState.Idle())

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
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun startupDelayShowsCachedContentImmediatelyAndPreventsDuplicateStartupJobsOnRecreation() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val added = xtreamRepository.addProvider(xtreamInput())
            val providerId = added.providerId
            val requestsAfterAdd = server.requestCount

            // Advance clock past Live 2h threshold while Movies/Series (< 6h) remain fresh
            clock.advanceBy(CatalogAutoRefreshScheduler.LIVE_STALE_THRESHOLD_MS + 1_000L)

            // Cached content is immediately available before startup refresh runs
            assertEquals(1, database.liveStreamDao().countByProvider(providerId.value))
            assertEquals(1, database.vodDao().countByProvider(providerId.value))
            assertEquals(1, database.seriesDao().countByProvider(providerId.value))

            scheduler.onAppForegrounded()
            scheduler.onHomeReady(providerId)
            awaitSchedulerIdle(scheduler)

            // Before 3 seconds have elapsed, no automatic refresh request has been sent
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS - 500L)
            awaitSchedulerIdle(scheduler)
            assertEquals(requestsAfterAdd, server.requestCount)
            assertEquals(1, database.liveStreamDao().countByProvider(providerId.value))

            // Simulate Activity recreation before startup timer completes
            scheduler.onAppBackgrounded()
            scheduler.onAppForegrounded()
            scheduler.onHomeReady(providerId)
            awaitSchedulerIdle(scheduler)

            // Enqueue updated Live response (2 live channels)
            enqueueXtreamLiveOnly(liveCount = 2)

            // Advance past the 3-second startup readiness delay
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS + 100L)
            awaitSchedulerIdle(scheduler)

            assertEquals(2, database.liveStreamDao().countByProvider(providerId.value))
            // Only 3 Xtream requests (account + live categories + live streams) were made
            assertEquals(requestsAfterAdd + 3, server.requestCount)

            // Re-invoking onHomeReady for the same provider does not schedule a duplicate startup check
            scheduler.onHomeReady(providerId)
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS + 100L)
            awaitSchedulerIdle(scheduler)
            assertEquals(requestsAfterAdd + 3, server.requestCount)
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun stalenessThresholdsRefreshOnlyStaleXtreamSectionsAndSkipFreshCatalog() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerId = xtreamRepository.addProvider(xtreamInput()).providerId
            val baseTime = clock.nowEpochMs()
            val requestsAfterAdd = server.requestCount

            // 1. At +1 hour: all sections are fresh (Live < 2h, Movies < 6h, Series < 6h)
            clock.set(baseTime + 60 * 60 * 1_000L)
            assertFalse(scheduler.isSectionStale(providerId, ContentType.Live))
            assertFalse(scheduler.isSectionStale(providerId, ContentType.Movie))
            assertFalse(scheduler.isSectionStale(providerId, ContentType.Series))
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertEquals(requestsAfterAdd, server.requestCount)

            // 2. At +2 hours + 1s: only Live is stale (> 2h); Movies & Series (< 6h) are fresh
            val liveRefreshTime = baseTime + CatalogAutoRefreshScheduler.LIVE_STALE_THRESHOLD_MS + 1_000L
            clock.set(liveRefreshTime)
            assertTrue(scheduler.isSectionStale(providerId, ContentType.Live))
            assertFalse(scheduler.isSectionStale(providerId, ContentType.Movie))
            assertFalse(scheduler.isSectionStale(providerId, ContentType.Series))
            enqueueXtreamLiveOnly(liveCount = 3)
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertEquals(3, database.liveStreamDao().countByProvider(providerId.value))
            assertEquals(liveRefreshTime, settings.sectionRefreshTime(providerId, ContentType.Live))
            assertEquals(baseTime, settings.sectionRefreshTime(providerId, ContentType.Movie))
            assertEquals(baseTime, settings.sectionRefreshTime(providerId, ContentType.Series))

            // 3. Refresh Live at +5h so Live is fresh (1h old) when Movies & Series become stale at +6h + 1s
            val secondLiveRefreshTime = baseTime + 5 * 60 * 60 * 1_000L
            clock.set(secondLiveRefreshTime)
            enqueueXtreamLiveOnly(liveCount = 3)
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)

            // Now at +6 hours + 1s from baseTime: Live is 1h old (fresh), Movies & Series are 6h + 1s old (stale)
            val vodSeriesRefreshTime = baseTime + CatalogAutoRefreshScheduler.MOVIES_STALE_THRESHOLD_MS + 1_000L
            clock.set(vodSeriesRefreshTime)
            assertFalse(scheduler.isSectionStale(providerId, ContentType.Live))
            assertTrue(scheduler.isSectionStale(providerId, ContentType.Movie))
            assertTrue(scheduler.isSectionStale(providerId, ContentType.Series))
            enqueueXtreamMoviesOnly(movieCount = 2)
            enqueueXtreamSeriesOnly(seriesCount = 2)
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertEquals(2, database.vodDao().countByProvider(providerId.value))
            assertEquals(2, database.seriesDao().countByProvider(providerId.value))
            assertEquals(secondLiveRefreshTime, settings.sectionRefreshTime(providerId, ContentType.Live))
            assertEquals(vodSeriesRefreshTime, settings.sectionRefreshTime(providerId, ContentType.Movie))
            assertEquals(vodSeriesRefreshTime, settings.sectionRefreshTime(providerId, ContentType.Series))
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun foregroundPeriodicChecksRunEvery15MinutesAndSuspendWhenBackgrounded() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerId = xtreamRepository.addProvider(xtreamInput()).providerId

            scheduler.onAppForegrounded()
            scheduler.onHomeReady(providerId)
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS + 100L)
            awaitSchedulerIdle(scheduler)
            val baselineRequests = server.requestCount

            // Advance 7 intervals of 15m (1h 45m): still fresh (< 2h), so no network calls
            repeat(7) {
                clock.advanceBy(CatalogAutoRefreshScheduler.FOREGROUND_CHECK_INTERVAL_MS)
                advanceTimeBy(CatalogAutoRefreshScheduler.FOREGROUND_CHECK_INTERVAL_MS)
                awaitSchedulerIdle(scheduler)
            }
            assertEquals(baselineRequests, server.requestCount)

            // 8th interval (+15m -> 2h 00m) + 1s: Live is now stale and refreshed on the 15-minute check
            clock.advanceBy(CatalogAutoRefreshScheduler.FOREGROUND_CHECK_INTERVAL_MS + 1_000L)
            enqueueXtreamLiveOnly(liveCount = 2)
            advanceTimeBy(CatalogAutoRefreshScheduler.FOREGROUND_CHECK_INTERVAL_MS)
            awaitSchedulerIdle(scheduler)
            assertEquals(2, database.liveStreamDao().countByProvider(providerId.value))
            val requestsAfterPeriodicRefresh = server.requestCount

            // Background the app: periodic checks must suspend even if clock advances 10 hours
            scheduler.onAppBackgrounded()
            clock.advanceBy(10 * 60 * 60 * 1_000L)
            advanceTimeBy(4 * CatalogAutoRefreshScheduler.FOREGROUND_CHECK_INTERVAL_MS)
            awaitSchedulerIdle(scheduler)
            assertEquals(requestsAfterPeriodicRefresh, server.requestCount)
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun appResumeChecksStaleContentAndEnforces15MinuteResumeAttemptCooldown() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerId = xtreamRepository.addProvider(xtreamInput()).providerId

            scheduler.onAppForegrounded()
            scheduler.onHomeReady(providerId)
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS + 100L)
            awaitSchedulerIdle(scheduler)

            // Background app, advance clock by 2h + 5s so Live is stale
            scheduler.onAppBackgrounded()
            clock.advanceBy(CatalogAutoRefreshScheduler.LIVE_STALE_THRESHOLD_MS + 5_000L)
            enqueueXtreamLiveOnly(liveCount = 2)

            // Resume app: triggers immediate AutomaticResume refresh for stale Live
            scheduler.onAppForegrounded()
            awaitSchedulerIdle(scheduler)
            assertEquals(2, database.liveStreamDao().countByProvider(providerId.value))
            val requestsAfterFirstResume = server.requestCount

            // Background and resume 5 minutes later (< 15m cooldown), even if Live timestamp is manually marked old
            scheduler.onAppBackgrounded()
            settings.setSectionRefreshEpochMs(providerId, ContentType.Live, 500L)
            clock.advanceBy(5 * 60 * 1_000L)
            scheduler.onAppForegrounded()
            awaitSchedulerIdle(scheduler)
            assertEquals(requestsAfterFirstResume, server.requestCount)

            // Background again and advance past remaining 10m + 1s of the 15-minute resume attempt cooldown
            scheduler.onAppBackgrounded()
            clock.advanceBy(10 * 60 * 1_000L + 1_000L)
            enqueueXtreamLiveOnly(liveCount = 3)
            scheduler.onAppForegrounded()
            awaitSchedulerIdle(scheduler)
            assertEquals(3, database.liveStreamDao().countByProvider(providerId.value))
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun providerSwitchingChecksNewlySelectedProviderAndNeverRefreshesInactiveProvider() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            // Add Provider A and Provider B at t = 1_000L
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerA = xtreamRepository.addProvider(xtreamInput(displayName = "Provider A", username = "user-a")).providerId
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerB = xtreamRepository.addProvider(xtreamInput(displayName = "Provider B", username = "user-b")).providerId

            // Select Provider A first
            settings.setSelectedProviderId(providerA)
            scheduler.onAppForegrounded()
            scheduler.onHomeReady(providerA)
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS + 100L)
            awaitSchedulerIdle(scheduler)

            // Advance clock by 3 hours so both providers have stale Live, but refresh Provider A manually
            clock.advanceBy(3 * 60 * 60 * 1_000L)
            enqueueXtreamLiveOnly(liveCount = 2, username = "user-a")
            coordinator.refreshLive(providerA, RefreshTrigger.Manual)
            assertEquals(2, database.liveStreamDao().countByProvider(providerA.value))
            assertEquals(1, database.liveStreamDao().countByProvider(providerB.value))

            // Switch selected provider to Provider B: scheduler automatically checks Provider B and refreshes its stale Live section
            enqueueXtreamLiveOnly(liveCount = 4, username = "user-b")
            settings.setSelectedProviderId(providerB)
            awaitSchedulerIdle(scheduler)

            assertEquals(4, database.liveStreamDao().countByProvider(providerB.value))
            // Provider A was inactive during the switch and remained untouched at 2 channels
            assertEquals(2, database.liveStreamDao().countByProvider(providerA.value))
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun m3uProviderPerformsSingleDownloadAndParsePerAutomaticRefreshCycle() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/x-mpegurl").setBody(sampleM3uPlaylist("Live One")))
            val rawId = m3uRepository.addUrlProvider(M3uUrlInput("M3U Provider", server.url("/list.m3u").toString()))
            val providerId = ProviderId(rawId)
            settings.setSelectedProviderId(providerId)
            val requestsAfterAdd = server.requestCount

            // Advance clock by 2h + 10s (only Live threshold exceeded)
            clock.advanceBy(CatalogAutoRefreshScheduler.LIVE_STALE_THRESHOLD_MS + 10_000L)
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "audio/x-mpegurl")
                    .setBody(sampleM3uPlaylist("Live One") + "\n#EXTINF:-1 group-title=\"Live\",Live Two\nhttp://example.invalid/live/two.ts\n"),
            )

            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            // Exactly 1 HTTP request was made for the entire M3U refresh cycle
            assertEquals(requestsAfterAdd + 1, server.requestCount)
            assertEquals(2, m3uRepository.counts(rawId).liveCount)
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun automaticContentRefreshSettingDefaultOnAndDisablesAutoRefreshWhenOffPreservingManualAndEpg() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            // 1. Verify DataStore-backed WatchioSettingsRepository and SettingsViewModel default to ON (true) and persist OFF/ON
            val dataStoreFile = File(tempFolder.newFolder(), "phase2_settings.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { dataStoreFile },
            )
            val realSettingsRepo = WatchioSettingsRepository(dataStore)
            assertTrue(realSettingsRepo.automaticContentRefreshEnabled.first())
            assertTrue(realSettingsRepo.epgAutoRefreshEnabled.first())

            val epgRepository = EpgRepository(database, testOkHttpClient, credentialStore, clock)
            val epgCoordinator = EpgRefreshCoordinator(database, epgRepository)
            val settingsViewModel = SettingsViewModel(realSettingsRepo, epgCoordinator)
            try {
                assertTrue(settingsViewModel.state.first().automaticContentRefreshEnabled)
                assertTrue(settingsViewModel.state.first().epgAutoRefreshEnabled)

                settingsViewModel.setAutomaticContentRefreshEnabled(false)
                assertFalse(realSettingsRepo.automaticContentRefreshEnabled.first { !it })
                assertFalse(settingsViewModel.state.first { !it.automaticContentRefreshEnabled }.automaticContentRefreshEnabled)
                // EPG auto-refresh setting remains unaffected
                assertTrue(realSettingsRepo.epgAutoRefreshEnabled.first())
                assertTrue(settingsViewModel.state.first().epgAutoRefreshEnabled)
            } finally {
                settingsViewModel.viewModelScope.cancel()
            }

            // 2. When Automatic Content Refresh is OFF, automatic checks are disabled even when catalogue is 24h stale
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerId = xtreamRepository.addProvider(xtreamInput()).providerId
            val requestsAfterAdd = server.requestCount

            settings.setAutomaticContentRefreshEnabled(false)
            clock.advanceBy(24 * 60 * 60 * 1_000L)

            scheduler.onAppForegrounded()
            scheduler.onHomeReady(providerId)
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS + 100L)
            awaitSchedulerIdle(scheduler)
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            scheduler.checkNow(RefreshTrigger.AutomaticResume)
            assertEquals(requestsAfterAdd, server.requestCount)

            // 3. Manual catalogue refresh remains available while Automatic Content Refresh is OFF
            enqueueXtreamLiveOnly(liveCount = 3)
            val manualResult = coordinator.refreshLive(providerId, RefreshTrigger.Manual)
            assertTrue(manualResult is CatalogRefreshResult.Success)
            assertEquals(3, database.liveStreamDao().countByProvider(providerId.value))
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun playbackProtectionDefersRefreshAcrossAllPlaybackModesAndResumesWhenPlaybackEnds() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            // Verify route and player-state deferral predicates cover all required playback surfaces
            assertTrue(isPlaybackRouteForAutoRefreshDeferral("live", hasPendingSportsChannel = false))
            assertTrue(isPlaybackRouteForAutoRefreshDeferral("live/fullscreen?origin=live", hasPendingSportsChannel = false))
            assertTrue(isPlaybackRouteForAutoRefreshDeferral("live/fullscreen?origin=sports", hasPendingSportsChannel = false))
            assertTrue(isPlaybackRouteForAutoRefreshDeferral("movies/player/42", hasPendingSportsChannel = false))
            assertTrue(isPlaybackRouteForAutoRefreshDeferral("series/player/99", hasPendingSportsChannel = false))
            assertTrue(isPlaybackRouteForAutoRefreshDeferral("sports", hasPendingSportsChannel = true))
            assertFalse(isPlaybackRouteForAutoRefreshDeferral("home", hasPendingSportsChannel = false))
            assertFalse(isPlaybackRouteForAutoRefreshDeferral("movies", hasPendingSportsChannel = false))
            assertFalse(isPlaybackRouteForAutoRefreshDeferral("series", hasPendingSportsChannel = false))
            assertFalse(isPlaybackRouteForAutoRefreshDeferral("sports", hasPendingSportsChannel = false))

            val meta = WatchioPlayerMetadata(
                currentMedia = PlaybackMedia(url = "http://example.invalid/live/1.ts", title = "Sky Sports Main Event"),
            )
            assertTrue(WatchioPlayerState.Playing(meta).isActivePlaybackForRefreshDeferral())
            assertTrue(WatchioPlayerState.Buffering(meta).isActivePlaybackForRefreshDeferral())
            assertTrue(WatchioPlayerState.Connecting(meta).isActivePlaybackForRefreshDeferral())
            assertTrue(WatchioPlayerState.Paused(meta).isActivePlaybackForRefreshDeferral())
            assertTrue(WatchioPlayerState.Recovering("Reconnecting", meta).isActivePlaybackForRefreshDeferral())
            assertFalse(WatchioPlayerState.Idle().isActivePlaybackForRefreshDeferral())
            assertFalse(WatchioPlayerState.Ended(meta).isActivePlaybackForRefreshDeferral())
            assertFalse(WatchioPlayerState.Failed("Failed", meta).isActivePlaybackForRefreshDeferral())
            assertFalse(WatchioPlayerState.Released().isActivePlaybackForRefreshDeferral())

            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerId = xtreamRepository.addProvider(xtreamInput()).providerId
            scheduler.onAppForegrounded()
            scheduler.onHomeReady(providerId)
            advanceTimeBy(CatalogAutoRefreshScheduler.STARTUP_READY_DELAY_MS + 100L)
            awaitSchedulerIdle(scheduler)
            val baselineRequests = server.requestCount

            // Advance clock by 3 hours so Live is stale while user is watching Live TV / Sports / VOD
            clock.advanceBy(3 * 60 * 60 * 1_000L)
            val activePlayerState = WatchioPlayerState.Playing(meta)
            playerStateFlow.value = activePlayerState
            scheduler.setPlaybackRouteActive(true)
            awaitSchedulerIdle(scheduler)
            assertTrue(scheduler.isPlaybackCurrentlyActive())

            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertTrue(scheduler.isDeferredByPlayback.value)
            scheduler.checkNow(RefreshTrigger.AutomaticResume)
            assertTrue(scheduler.isDeferredByPlayback.value)
            assertEquals(baselineRequests, server.requestCount)
            // Player state was never interrupted or reset
            assertEquals(activePlayerState, playerStateFlow.value)

            // When playback ends, the deferred refresh is immediately evaluated and executed
            enqueueXtreamLiveOnly(liveCount = 2)
            playerStateFlow.value = WatchioPlayerState.Idle()
            scheduler.setPlaybackRouteActive(false)
            awaitSchedulerIdle(scheduler)

            assertFalse(scheduler.isDeferredByPlayback.value)
            assertEquals(2, database.liveStreamDao().countByProvider(providerId.value))
        } finally {
            schedulerScope.cancel()
        }
    }

    @Test
    fun offlineSkippingRateLimitRetryAfterAndExponentialBackoffPreserveCachedContentAndTrackFailedTimestampsSeparately() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = createScheduler(schedulerScope)
        try {
            enqueueXtreamCatalog(live = 1, movies = 1, series = 1)
            val providerId = xtreamRepository.addProvider(xtreamInput()).providerId
            val initialRefreshTime = clock.nowEpochMs()
            val baselineRequests = server.requestCount

            // Make Live stale (> 2h)
            clock.advanceBy(CatalogAutoRefreshScheduler.LIVE_STALE_THRESHOLD_MS + 1_000L)

            // 1. Offline: skip automatic request without network traffic or failure penalty
            onlineState.set(false)
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertEquals(baselineRequests, server.requestCount)
            assertNull(scheduler.failureStateFor(providerId, ContentType.Live))

            // 2. Online, but server returns HTTP 500 -> failure tracked separately; cached data & successful timestamp preserved
            onlineState.set(true)
            server.enqueue(MockResponse().setResponseCode(500))
            val firstFailTime = clock.nowEpochMs()
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertEquals(1, database.liveStreamDao().countByProvider(providerId.value))
            assertEquals(initialRefreshTime, settings.sectionRefreshTime(providerId, ContentType.Live))

            val failure1 = scheduler.failureStateFor(providerId, ContentType.Live)
            assertNotNull(failure1)
            assertEquals(1, failure1!!.consecutiveFailures)
            assertEquals(firstFailTime + CatalogAutoRefreshScheduler.INITIAL_RETRY_BACKOFF_MS, failure1.nextRetryAllowedAtEpochMs)

            // Attempting another automatic refresh 30s later (< 60s backoff) is blocked by backoff cooldown
            val requestsAfterFail1 = server.requestCount
            clock.advanceBy(30_000L)
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertEquals(requestsAfterFail1, server.requestCount)

            // 3. Advance past 60s backoff; server returns HTTP 429 with Retry-After: 300 (5 minutes)
            clock.advanceBy(31_000L)
            val secondFailTime = clock.nowEpochMs()
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "300"))
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertEquals(1, database.liveStreamDao().countByProvider(providerId.value))
            assertEquals(initialRefreshTime, settings.sectionRefreshTime(providerId, ContentType.Live))

            val failure2 = scheduler.failureStateFor(providerId, ContentType.Live)
            assertNotNull(failure2)
            assertEquals(2, failure2!!.consecutiveFailures)
            // Retry-After (300s) is respected over 2nd exponential backoff (120s)
            assertEquals(secondFailTime + 300_000L, failure2.nextRetryAllowedAtEpochMs)

            // 4. Advance past 300s Retry-After; server succeeds -> failure state cleared and timestamp updated
            clock.advanceBy(301_000L)
            val recoveryTime = clock.nowEpochMs()
            enqueueXtreamLiveOnly(liveCount = 2)
            scheduler.checkNow(RefreshTrigger.AutomaticPeriodic)
            assertNull(scheduler.failureStateFor(providerId, ContentType.Live))
            assertEquals(2, database.liveStreamDao().countByProvider(providerId.value))
            assertEquals(recoveryTime, settings.sectionRefreshTime(providerId, ContentType.Live))
        } finally {
            schedulerScope.cancel()
        }
    }

    private fun TestScope.awaitSchedulerIdle(scheduler: CatalogAutoRefreshScheduler) {
        runCurrent()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (scheduler.isEvaluating() && System.nanoTime() < deadline) {
            Thread.sleep(5)
            runCurrent()
        }
        runCurrent()
    }

    private fun createScheduler(scope: CoroutineScope): CatalogAutoRefreshScheduler =
        CatalogAutoRefreshScheduler(
            database = database,
            settingsRepository = settings,
            coordinator = coordinator,
            clock = clock,
            connectivityChecker = connectivityChecker,
            playerStateFlow = playerStateFlow,
            schedulerScope = scope,
        )

    private fun xtreamInput(
        displayName: String = "Provider A",
        username: String = "fake-user",
    ) = XtreamCredentialsInput(
        displayName = displayName,
        serverUrl = server.url("/").toString(),
        username = username,
        password = "fake-password",
    )

    private fun enqueueXtreamCatalog(live: Int, movies: Int, series: Int, username: String = "fake-user") {
        server.enqueue(jsonResponse("""{"user_info":{"username":"$username","auth":1,"status":"Active","active_cons":"1","max_connections":"2","allowed_output_formats":["ts","m3u8"]},"server_info":{"url":"example.invalid","server_protocol":"http"}}"""))
        server.enqueue(jsonResponse(categoriesJson("live", if (live > 0) 1 else 0)))
        server.enqueue(jsonResponse(liveStreamsJson(live)))
        server.enqueue(jsonResponse(categoriesJson("movie", if (movies > 0) 1 else 0)))
        server.enqueue(jsonResponse(vodStreamsJson(movies)))
        server.enqueue(jsonResponse(categoriesJson("series", if (series > 0) 1 else 0)))
        server.enqueue(jsonResponse(seriesJson(series)))
    }

    private fun enqueueXtreamLiveOnly(liveCount: Int, username: String = "fake-user") {
        server.enqueue(jsonResponse("""{"user_info":{"username":"$username","auth":1,"status":"Active"},"server_info":{}}"""))
        server.enqueue(jsonResponse(categoriesJson("live", 1)))
        server.enqueue(jsonResponse(liveStreamsJson(liveCount)))
    }

    private fun enqueueXtreamMoviesOnly(movieCount: Int, username: String = "fake-user") {
        server.enqueue(jsonResponse("""{"user_info":{"username":"$username","auth":1,"status":"Active"},"server_info":{}}"""))
        server.enqueue(jsonResponse(categoriesJson("movie", 1)))
        server.enqueue(jsonResponse(vodStreamsJson(movieCount)))
    }

    private fun enqueueXtreamSeriesOnly(seriesCount: Int, username: String = "fake-user") {
        server.enqueue(jsonResponse("""{"user_info":{"username":"$username","auth":1,"status":"Active"},"server_info":{}}"""))
        server.enqueue(jsonResponse(categoriesJson("series", 1)))
        server.enqueue(jsonResponse(seriesJson(seriesCount)))
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
}
