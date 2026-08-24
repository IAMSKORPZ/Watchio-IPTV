package com.watchioiptv.nativeapp

import android.content.Context
import android.view.ViewGroup
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.EpgSourceEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.database.ProviderEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerMetadata
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.RoomFavoritesRepository
import com.watchioiptv.nativeapp.data.RoomHistoryRepository
import com.watchioiptv.nativeapp.data.epg.EpgRepository
import com.watchioiptv.nativeapp.data.live.LiveTvRepository
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideRepository
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvGuideViewModelInstrumentedTest {
    private lateinit var database: WatchioDatabase
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WatchioDatabase::class.java,
        ).build()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun noCacheFailedRefreshLeavesLoadingFalse() = runBlocking {
        val providerId = ProviderId("p1")
        database.providerDao().upsert(ProviderEntity("p1", "Provider", "m3u_url", "http://example.invalid/list.m3u", 1, 1, null, true))
        database.epgDao().upsertSource(EpgSourceEntity("p1", "source", "custom_url", server.url("/guide.xml").toString(), true, 1, null, null, null, null, null, null, 0, 0, 1, 1))
        database.m3uItemDao().upsertAll(listOf(item("p1", "ch-1", "Channel One", "channel.one")))
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = TvGuideViewModel(tvGuideRepository(providerId), FakePlayerManager(), FixedClock)
        repeat(40) {
            val state = viewModel.state.value
            if (!state.loading && !state.refreshing && state.errorMessage != null) return@repeat
            delay(100)
        }
        val state = viewModel.state.value

        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertTrue(state.channels.map { it.channelId }.contains("ch-1"))
        assertTrue(state.errorMessage.orEmpty().isNotBlank())
    }

    private fun tvGuideRepository(providerId: ProviderId): TvGuideRepository {
        val live = LiveTvRepository(
            database,
            FakeSettings(providerId),
            RoomFavoritesRepository(database.favoriteDao()),
            RoomHistoryRepository(database.watchHistoryDao()),
            FakeResolver,
        )
        val epg = EpgRepository(database, com.watchioiptv.nativeapp.core.network.NetworkModule().okHttpClient, ProviderCredentialStore(FakeSecretStore), FixedClock)
        return TvGuideRepository(database, live, epg)
    }

    private fun item(providerId: String, id: String, name: String, tvgId: String) = M3uItemEntity(
        providerId = providerId,
        itemId = id,
        directUrl = "http://example.invalid/$id.ts",
        name = name,
        normalizedName = name.lowercase(),
        tvgId = tvgId,
        tvgName = name,
        tvgLogo = null,
        tvgUrl = null,
        tvgRec = null,
        tvgShift = null,
        groupTitle = "All",
        groupName = null,
        categoryId = "all",
        userAgent = null,
        referrer = null,
        catchupType = null,
        catchupSource = null,
        catchupDays = null,
        timeshiftHours = null,
        channelNumber = "1",
        contentType = "live",
        seriesName = null,
        seasonNumber = null,
        episodeNumber = null,
        playlistOrder = 1,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private class FakeSettings(providerId: ProviderId) : SettingsRepository {
        override val selectedProviderId: Flow<ProviderId?> = flowOf(providerId)
        override val inputMode: Flow<InputMode> = flowOf(InputMode.Auto)
        override val streamFormat: Flow<StreamFormat> = flowOf(StreamFormat.Auto)
        override suspend fun setSelectedProviderId(providerId: ProviderId?) = Unit
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit
    }

    private object FakeResolver : PlaybackUrlResolver {
        override suspend fun resolve(request: PlaybackUrlRequest): String = "http://example.invalid/${request.contentId}.ts"
    }

    private class FakePlayerManager : WatchioPlayerManager {
        override val state: StateFlow<WatchioPlayerState> = MutableStateFlow(WatchioPlayerState.Idle())
        override suspend fun load(media: PlaybackMedia) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun retry() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun snapshot(): WatchioPlayerMetadata = WatchioPlayerMetadata()
        override fun attachSurface(container: ViewGroup) = Unit
        override fun detachSurface(container: ViewGroup) = Unit
        override fun release() = Unit
    }

    private object FixedClock : WatchioClock {
        override fun nowEpochMs(): Long = 90 * 60_000L
    }

    private object FakeSecretStore : SecretStore {
        override suspend fun putSecret(key: String, value: String) = Unit
        override suspend fun getSecret(key: String): String? = null
        override suspend fun removeSecret(key: String) = Unit
    }
}
