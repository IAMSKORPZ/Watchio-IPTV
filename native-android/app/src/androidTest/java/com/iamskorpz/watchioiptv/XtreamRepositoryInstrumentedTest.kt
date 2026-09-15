package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.network.NetworkModule
import com.iamskorpz.watchioiptv.core.security.ProviderCredentialStore
import com.iamskorpz.watchioiptv.core.security.SecretStore
import com.iamskorpz.watchioiptv.core.util.WatchioClock
import com.iamskorpz.watchioiptv.data.RoomProviderRepository
import com.iamskorpz.watchioiptv.data.xtream.XtreamCredentialsInput
import com.iamskorpz.watchioiptv.data.xtream.CatalogSyncKey
import com.iamskorpz.watchioiptv.data.xtream.CatalogSyncState
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.domain.model.StreamFormat
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.domain.repository.XtreamAccountMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class XtreamRepositoryInstrumentedTest {
    private lateinit var database: WatchioDatabase
    private lateinit var server: MockWebServer
    private lateinit var secretStore: InMemorySecretStore
    private lateinit var credentialStore: ProviderCredentialStore
    private lateinit var settings: FakeSettingsRepository
    private lateinit var repository: XtreamRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WatchioDatabase::class.java,
        ).build()
        server = MockWebServer()
        server.start()
        secretStore = InMemorySecretStore()
        credentialStore = ProviderCredentialStore(secretStore)
        settings = FakeSettingsRepository()
        repository = XtreamRepository(
            database = database,
            credentialStore = credentialStore,
            settingsRepository = settings,
            retrofitFactory = NetworkModule()::retrofit,
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun importsXtreamCatalogAndRefreshReplacesProviderRows() = runBlocking {
        enqueueSuccessfulCatalog(live = 2, movies = 2, series = 1)
        val result = repository.addProvider(input())

        assertEquals(2, result.liveCount)
        assertEquals(2, result.movieCount)
        assertEquals(1, result.seriesCount)
        assertEquals(result.providerId, settings.selected.value)
        assertNotNull(secretStore.getSecret("provider.${result.providerId.value}.xtream_password"))
        assertEquals(2, database.liveStreamDao().countByProvider(result.providerId.value))
        assertEquals(2, database.vodDao().countByProvider(result.providerId.value))
        assertEquals(1, database.seriesDao().countByProvider(result.providerId.value))
        assertEquals("2", settings.metadata(result.providerId).value.maxConnections)
        assertEquals("1", settings.metadata(result.providerId).value.activeConnections)
        assertEquals(listOf("ts", "m3u8"), settings.metadata(result.providerId).value.allowedOutputFormats)

        enqueueSuccessfulCatalog(live = 1, movies = 1, series = 2)
        val refresh = repository.refreshProvider(result.providerId)
        assertEquals(1, refresh.liveCount)
        assertEquals(1, database.liveStreamDao().countByProvider(result.providerId.value))
        assertEquals(2, database.seriesDao().countByProvider(result.providerId.value))
    }

    @Test
    fun duplicateProviderIsRejectedBeforeNetworkAndDeleteRemovesXtreamSecrets() = runBlocking {
        enqueueSuccessfulCatalog(live = 1, movies = 1, series = 1)
        val result = repository.addProvider(input())

        val duplicate = runCatching { repository.addProvider(input()) }
        assertTrue(duplicate.isFailure)
        assertEquals(0, server.requestCount - 7)

        RoomProviderRepository(database.providerDao(), credentialStore).deleteProvider(result.providerId)
        assertEquals(0, database.liveStreamDao().countByProvider(result.providerId.value))
        assertNull(credentialStore.getXtreamCredentials(result.providerId.value))
    }

    @Test
    fun invalidAuthDoesNotCreateProviderOrSecrets() = runBlocking {
        server.enqueue(json("""{"user_info":{"auth":0,"status":"Disabled"}}"""))
        val failure = runCatching { repository.addProvider(input()) }

        assertTrue(failure.isFailure)
        assertTrue(database.providerDao().getAll().isEmpty())
        assertTrue(secretStore.isEmpty())
    }

    @Test
    fun failedSectionRefreshPreservesPreviousAccountMetadata() = runBlocking {
        enqueueSuccessfulCatalog(live = 1, movies = 1, series = 1)
        val result = repository.addProvider(input())
        assertEquals("2", settings.metadata(result.providerId).value.maxConnections)

        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val failure = runCatching { repository.refreshLive(result.providerId) }

        assertTrue(failure.isFailure)
        assertEquals("2", settings.metadata(result.providerId).value.maxConnections)
        assertEquals("1", settings.metadata(result.providerId).value.activeConnections)
    }

    @Test
    fun largeSyntheticCatalogImportsWithoutDuplicates() = runBlocking {
        enqueueSuccessfulCatalog(live = 10_000, movies = 10_000, series = 5_000)
        val result = repository.addProvider(input(displayName = "Large Provider"))

        assertEquals(10_000, database.liveStreamDao().countByProvider(result.providerId.value))
        assertEquals(10_000, database.vodDao().countByProvider(result.providerId.value))
        assertEquals(5_000, database.seriesDao().countByProvider(result.providerId.value))
    }

    @Test
    fun twoPhaseReturnsAfterLiveWhileMoviesAndSeriesRunConcurrently() = runBlocking {
        val deferredRequests = CountDownLatch(2)
        val releaseDeferred = CountDownLatch(1)
        server.dispatcher = twoPhaseDispatcher(deferredRequests, releaseDeferred)

        val result = repository.addProviderTwoPhase(input())

        assertEquals(1, result.liveCount)
        assertEquals(0, result.movieCount)
        assertEquals(0, result.seriesCount)
        assertEquals(result.providerId, settings.selected.value)
        assertNotNull(credentialStore.getXtreamCredentials(result.providerId.value))
        assertEquals(1, database.liveStreamDao().countByProvider(result.providerId.value))
        assertEquals(0, database.vodDao().countByProvider(result.providerId.value))
        assertEquals(0, database.seriesDao().countByProvider(result.providerId.value))
        assertEquals(0, database.episodeDao().getByProvider(result.providerId.value).size)
        assertEquals(CatalogSyncState.Syncing, repository.catalogSyncStates.value[CatalogSyncKey(result.providerId, com.iamskorpz.watchioiptv.domain.model.ContentType.Movie)])
        assertEquals(CatalogSyncState.Syncing, repository.catalogSyncStates.value[CatalogSyncKey(result.providerId, com.iamskorpz.watchioiptv.domain.model.ContentType.Series)])
        assertTrue(deferredRequests.await(5, TimeUnit.SECONDS))

        releaseDeferred.countDown()
        repository.awaitDeferredCatalogSync(result.providerId)
        assertEquals(1, database.vodDao().countByProvider(result.providerId.value))
        assertEquals(1, database.seriesDao().countByProvider(result.providerId.value))
        assertEquals(CatalogSyncState.Ready, repository.catalogSyncStates.value[CatalogSyncKey(result.providerId, com.iamskorpz.watchioiptv.domain.model.ContentType.Movie)])
        assertEquals(CatalogSyncState.Ready, repository.catalogSyncStates.value[CatalogSyncKey(result.providerId, com.iamskorpz.watchioiptv.domain.model.ContentType.Series)])
    }

    @Test
    fun duplicateTwoPhaseImportReusesProviderAndDoesNotDuplicateActiveDeferredSync() = runBlocking {
        val deferredRequests = CountDownLatch(2)
        val releaseDeferred = CountDownLatch(1)
        val deferredStarts = AtomicInteger()
        repository.onDeferredSyncStarted = { deferredStarts.incrementAndGet() }
        server.dispatcher = twoPhaseDispatcher(deferredRequests, releaseDeferred)

        val first = repository.addProviderTwoPhase(input())
        assertTrue(deferredRequests.await(5, TimeUnit.SECONDS))
        val second = repository.addProviderTwoPhase(input())

        assertEquals(first.providerId, second.providerId)
        assertEquals(1, database.providerDao().getAll().size)
        assertEquals(1, deferredStarts.get())
        releaseDeferred.countDown()
        repository.awaitDeferredCatalogSync(first.providerId)
    }

    @Test
    fun deferredFailureKeepsPhaseOneProviderUsable() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.contains("action=get_vod_categories") == true -> MockResponse().setResponseCode(500)
                else -> twoPhaseResponse(request)
            }
        }
        val result = repository.addProviderTwoPhase(input())
        repository.awaitDeferredCatalogSync(result.providerId)

        assertNotNull(database.providerDao().findById(result.providerId.value))
        assertEquals(result.providerId, settings.selected.value)
        assertEquals(1, database.liveStreamDao().countByProvider(result.providerId.value))
        assertEquals(CatalogSyncState.Failed, repository.catalogSyncStates.value[CatalogSyncKey(result.providerId, com.iamskorpz.watchioiptv.domain.model.ContentType.Movie)])
    }

    @Test
    fun twoPhaseAuthenticationFailureCreatesNothing() = runBlocking {
        server.enqueue(json("""{"user_info":{"auth":0,"status":"Disabled"}}"""))
        assertTrue(runCatching { repository.addProviderTwoPhase(input()) }.isFailure)
        assertTrue(database.providerDao().getAll().isEmpty())
        assertTrue(secretStore.isEmpty())
    }

    private fun twoPhaseDispatcher(started: CountDownLatch, release: CountDownLatch) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path?.contains("action=get_vod_categories") == true || request.path?.contains("action=get_series_categories") == true) {
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            return twoPhaseResponse(request)
        }
    }

    private fun twoPhaseResponse(request: RecordedRequest): MockResponse = when {
        request.path?.contains("action=get_live_categories") == true -> json(categories("live", 1))
        request.path?.contains("action=get_live_streams") == true -> json(liveStreams(1))
        request.path?.contains("action=get_vod_categories") == true -> json(categories("movie", 1))
        request.path?.contains("action=get_vod_streams") == true -> json(vodStreams(1))
        request.path?.contains("action=get_series_categories") == true -> json(categories("series", 1))
        request.path?.contains("action=get_series") == true -> json(series(1))
        else -> json("""{"user_info":{"username":"fake-user","auth":1,"status":"Active"},"server_info":{}}""")
    }

    private fun input(displayName: String = "Provider A") = XtreamCredentialsInput(
        displayName = displayName,
        serverUrl = server.url("/").toString(),
        username = "fake-user",
        password = "fake-pass",
    )

    private fun enqueueSuccessfulCatalog(live: Int, movies: Int, series: Int) {
        server.enqueue(json("""{"user_info":{"username":"fake-user","auth":1,"status":"Active","active_cons":"1","max_connections":"2","allowed_output_formats":["ts","m3u8"]},"server_info":{"url":"example.invalid","server_protocol":"http"}}"""))
        server.enqueue(json(categories("live", 3)))
        server.enqueue(json(liveStreams(live)))
        server.enqueue(json(categories("movie", 2)))
        server.enqueue(json(vodStreams(movies)))
        server.enqueue(json(categories("series", 2)))
        server.enqueue(json(series(series)))
    }

    private fun categories(prefix: String, count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"category_id":"$prefix-$index","category_name":"${prefix.uppercase()} $index","parent_id":0}"""
        }

    private fun liveStreams(count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"stream_id":$index,"name":"Live $index","stream_icon":"http://example.invalid/$index.png","category_id":"live-1","epg_channel_id":"live.$index","container_extension":"ts"}"""
        }

    private fun vodStreams(count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"stream_id":"$index","name":"Movie $index","stream_icon":"http://example.invalid/m$index.png","category_id":"movie-1","rating":"7","container_extension":"mp4","genre":"Drama","youtube_trailer":"trailer"}"""
        }

    private fun series(count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"series_id":"$index","name":"Series $index","cover":"http://example.invalid/s$index.png","plot":"Plot","cast":"Cast","director":"Director","genre":"Drama","releaseDate":"2024","rating":"8","youtube_trailer":"trailer","episode_run_time":"45","category_id":"series-1","last_modified":"100"}"""
        }

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun putSecret(key: String, value: String) { values[key] = value }
        override suspend fun getSecret(key: String): String? = values[key]
        override suspend fun removeSecret(key: String) { values.remove(key) }
        fun isEmpty(): Boolean = values.isEmpty()
    }

    private class FakeSettingsRepository : SettingsRepository {
        val selected = MutableStateFlow<ProviderId?>(null)
        private val metadata = mutableMapOf<ProviderId, MutableStateFlow<XtreamAccountMetadata>>()
        override val selectedProviderId: Flow<ProviderId?> = selected
        override val inputMode: Flow<InputMode> = MutableStateFlow(InputMode.Auto)
        override val streamFormat: Flow<StreamFormat> = MutableStateFlow(StreamFormat.Auto)
        override suspend fun setSelectedProviderId(providerId: ProviderId?) { selected.value = providerId }
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit
        override fun observeXtreamAccountMetadata(providerId: ProviderId): Flow<XtreamAccountMetadata> = metadata(providerId)
        override suspend fun setXtreamAccountMetadata(providerId: ProviderId, metadata: XtreamAccountMetadata) {
            metadataFlow(providerId).value = metadata
        }
        fun metadata(providerId: ProviderId): MutableStateFlow<XtreamAccountMetadata> = metadataFlow(providerId)
        private fun metadataFlow(providerId: ProviderId): MutableStateFlow<XtreamAccountMetadata> =
            metadata.getOrPut(providerId) { MutableStateFlow(XtreamAccountMetadata()) }
    }
}
