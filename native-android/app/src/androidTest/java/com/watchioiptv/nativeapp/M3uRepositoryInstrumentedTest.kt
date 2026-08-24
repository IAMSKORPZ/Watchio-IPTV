package com.watchioiptv.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.FavoriteEntity
import com.watchioiptv.nativeapp.core.database.WatchHistoryEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.network.NetworkModule
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.m3u.M3uFileInput
import com.watchioiptv.nativeapp.data.m3u.M3uRepository
import com.watchioiptv.nativeapp.data.m3u.M3uUrlInput
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3uRepositoryInstrumentedTest {
    private lateinit var database: WatchioDatabase
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeSettingsRepository
    private lateinit var repository: M3uRepository
    private var localContent: String = ""

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WatchioDatabase::class.java,
        ).build()
        server = MockWebServer()
        server.start()
        settings = FakeSettingsRepository()
        repository = M3uRepository(
            database = database,
            okHttpClient = NetworkModule().okHttpClient,
            settingsRepository = settings,
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 2000L },
            openLocalInputStream = { ByteArrayInputStream(localContent.toByteArray(Charsets.UTF_8)) },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun importsUrlAndLocalProvidersWithCountsAndCategories() = runBlocking {
        server.enqueue(playlistResponse(samplePlaylist()))
        val urlProviderId = repository.addUrlProvider(M3uUrlInput("M3U URL", server.url("/playlist.m3u?token=fake").toString()))
        assertEquals(1, repository.counts(urlProviderId).liveCount)
        assertEquals(1, repository.counts(urlProviderId).movieCount)
        assertEquals(1, repository.counts(urlProviderId).seriesCount)
        assertEquals(3, database.m3uItemDao().getFirstByProvider(urlProviderId, 10).size)
        assertEquals("http://example.invalid/epg.xml", database.epgDao().getEnabledSource(urlProviderId)?.url)

        localContent = samplePlaylist()
        val fileProviderId = repository.addFileProvider(M3uFileInput("Local M3U", "content://watchio/fake.m3u"))
        assertEquals(ProviderId(fileProviderId), settings.selected.value)
        assertEquals(1, repository.counts(fileProviderId).liveCount)
    }

    @Test
    fun failedRefreshPreservesExistingCatalog() = runBlocking {
        server.enqueue(playlistResponse(samplePlaylist()))
        val providerId = repository.addUrlProvider(M3uUrlInput("M3U URL", server.url("/playlist.m3u").toString()))
        assertEquals(1, repository.counts(providerId).liveCount)

        server.enqueue(MockResponse().setResponseCode(500))
        val refresh = runCatching { repository.refresh(providerId) }
        assertTrue(refresh.isFailure)
        assertEquals(1, repository.counts(providerId).liveCount)
        assertEquals(1, repository.counts(providerId).movieCount)
    }

    @Test
    fun stableIdentityPreservesFavoritesAndHistoryAcrossRefresh() = runBlocking {
        server.enqueue(playlistResponse(samplePlaylist(urlSuffix = "one.ts")))
        val providerId = repository.addUrlProvider(M3uUrlInput("M3U URL", server.url("/playlist.m3u").toString()))
        val item = database.m3uItemDao().getFirstByProvider(providerId, 1).single()
        database.favoriteDao().upsert(
            FavoriteEntity(providerId, ContentType.Live.persisted, item.itemId, "", item.name, item.tvgLogo, 2000L),
        )
        database.watchHistoryDao().upsert(
            WatchHistoryEntity(providerId, ContentType.Live.persisted, item.itemId, "", item.name, item.tvgLogo, 0L, null, 2000L),
        )

        server.enqueue(playlistResponse(samplePlaylist(urlSuffix = "rotated.ts", displayName = "Live One HD")))
        repository.refresh(providerId)

        assertNotNull(database.favoriteDao().find(providerId, ContentType.Live.persisted, item.itemId))
        assertNotNull(database.watchHistoryDao().find(providerId, ContentType.Live.persisted, item.itemId))
    }

    @Test
    fun largePlaylistImportsFiftyThousandEntries() = runBlocking {
        server.enqueue(playlistResponse(largePlaylist(50_000)))
        val providerId = repository.addUrlProvider(M3uUrlInput("Large M3U", server.url("/large.m3u").toString()))
        assertEquals(50_000, repository.counts(providerId).liveCount)
        assertEquals(50_000, database.m3uItemDao().countByProviderAndType(providerId, ContentType.Live.persisted))
    }

    private fun samplePlaylist(
        urlSuffix: String = "one.ts",
        displayName: String = "Live One",
    ): String =
        """
        #EXTM3U url-tvg="http://example.invalid/epg.xml"
        #EXTINF:-1 tvg-id="live-one" tvg-logo="http://example.invalid/live.png" group-title="Live" catchup="default" catchup-days="3" tvg-shift="1",$displayName
        http://example.invalid/live/$urlSuffix
        #EXTINF:-1 group-title="Movies",Movie One
        http://example.invalid/movie/1.mp4
        #EXTINF:-1 group-title="Series",Series One S01E01
        http://example.invalid/series/1.mkv
        """.trimIndent()

    private fun largePlaylist(count: Int): String = buildString {
        appendLine("#EXTM3U")
        repeat(count) { index ->
            appendLine("""#EXTINF:-1 tvg-id="live-$index" group-title="Live",Live $index""")
            appendLine("http://example.invalid/live/$index.ts")
        }
    }

    private fun playlistResponse(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "audio/x-mpegurl").setBody(body)

    private class FakeSettingsRepository : SettingsRepository {
        val selected = MutableStateFlow<ProviderId?>(null)
        override val selectedProviderId: Flow<ProviderId?> = selected
        override val inputMode: Flow<InputMode> = MutableStateFlow(InputMode.Auto)
        override val streamFormat: Flow<StreamFormat> = MutableStateFlow(StreamFormat.Auto)
        override suspend fun setSelectedProviderId(providerId: ProviderId?) { selected.value = providerId }
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit
    }
}
