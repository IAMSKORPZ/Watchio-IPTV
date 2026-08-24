package com.watchioiptv.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.EpgChannelEntity
import com.watchioiptv.nativeapp.core.database.EpgProgrammeEntity
import com.watchioiptv.nativeapp.core.database.EpgSourceEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.database.ProviderEntity
import com.watchioiptv.nativeapp.core.database.CategoryEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
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
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideTimeline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvGuideRepositoryInstrumentedTest {
    private lateinit var database: WatchioDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun guideIsProviderScopedAndKeepsNoEpgChannels() = runBlocking {
        val selected = ProviderId("p1")
        seedProvider("p1")
        seedProvider("p2")
        database.m3uItemDao().upsertAll(
            listOf(
                item("p1", "bbc", "BBC One", 1, "bbc.uk"),
                item("p1", "no-epg", "No Match", 2, null),
                item("p2", "other", "Other Provider", 1, "other"),
            ),
        )
        database.epgDao().upsertChannels(
            listOf(
                epgChannel("p1", "bbc.uk", "BBC One"),
                epgChannel("p2", "other", "Other Provider"),
            ),
        )
        database.epgDao().upsertProgrammes(
            listOf(
                programme("p1", "bbc.uk", "now", "Current News", 0, 60),
                programme("p2", "other", "leak", "Wrong Provider", 0, 60),
            ),
        )
        val repository = tvGuideRepository(selected)

        val data = repository.guide(TvGuideTimeline.defaultWindow(30 * 60_000L), 30 * 60_000L, "all")

        assertEquals(listOf("bbc", "no-epg"), data.channels.map { it.channelId })
        assertEquals(listOf("Current News"), data.programmes["bbc"]?.map { it.title })
        assertTrue(data.programmes["no-epg"].isNullOrEmpty())
        assertTrue(data.programmes.values.flatten().none { it.title == "Wrong Provider" })
    }

    @Test
    fun largeGuideWindowStaysBounded() = runBlocking {
        val providerId = ProviderId("large")
        seedProvider(providerId.value)
        val channels = 2_000
        val programmesPerChannel = 5
        (0 until channels).chunked(500).forEach { chunk ->
            database.m3uItemDao().upsertAll(chunk.map { item(providerId.value, "ch-$it", "Channel $it", it, "epg-$it") })
            database.epgDao().upsertChannels(chunk.map { epgChannel(providerId.value, "epg-$it", "Channel $it") })
            database.epgDao().upsertProgrammes(
                chunk.flatMap { channel ->
                    (0 until programmesPerChannel).map { slot ->
                        programme(providerId.value, "epg-$channel", "p-$channel-$slot", "Show $channel $slot", slot * 60, (slot + 1) * 60)
                    }
                },
            )
        }
        val repository = tvGuideRepository(providerId)

        val data = repository.guide(TvGuideTimeline.defaultWindow(90 * 60_000L), 90 * 60_000L, "all")

        assertEquals(2_000, data.channels.size)
        assertEquals(10_000, data.programmes.values.sumOf { it.size })
        assertTrue(data.programmes["ch-42"]!!.any { it.isLiveNow })
    }

    @Test
    fun guideFiltersByLiveCategoryAndPreservesNoEpgRows() = runBlocking {
        val providerId = ProviderId("p1")
        seedProvider(providerId.value)
        seedProvider("other")
        database.categoryDao().upsertAll(
            listOf(
                category("p1", "sports", "Sports", 1),
                category("p1", "news", "News", 2),
                category("other", "sports", "Other Sports", 1),
            ),
        )
        database.m3uItemDao().upsertAll(
            listOf(
                item("p1", "sport-1", "Sport One", 1, "sport.one", "sports"),
                item("p1", "news-1", "News One", 2, "news.one", "news"),
                item("p1", "sport-no-epg", "Sport No EPG", 3, null, "sports"),
                item("other", "other-sport", "Other Provider Sport", 1, "other.sport", "sports"),
            ),
        )
        database.epgDao().upsertChannels(listOf(epgChannel("p1", "sport.one", "Sport One"), epgChannel("p1", "news.one", "News One")))
        database.epgDao().upsertProgrammes(listOf(programme("p1", "sport.one", "sport-now", "Live Sport", 0, 60), programme("p1", "news.one", "news-now", "Live News", 0, 60)))
        val repository = tvGuideRepository(providerId)
        val window = TvGuideTimeline.defaultWindow(30 * 60_000L)

        val all = repository.guide(window, 30 * 60_000L, "all")
        val sports = repository.guide(window, 30 * 60_000L, "sports")
        val news = repository.guide(window, 30 * 60_000L, "news")

        assertEquals(listOf("sport-1", "news-1", "sport-no-epg"), all.channels.map { it.channelId })
        assertEquals(listOf("sport-1", "sport-no-epg"), sports.channels.map { it.channelId })
        assertEquals(listOf("news-1"), news.channels.map { it.channelId })
        assertTrue(sports.programmes["sport-no-epg"].isNullOrEmpty())
        assertTrue(all.channels.none { it.providerId.value == "other" })
    }

    @Test
    fun cachedGuideLoadsAfterRepositoryRecreationAndCategoryChange() = runBlocking {
        val providerId = ProviderId("p1")
        seedProvider(providerId.value)
        database.categoryDao().upsertAll(listOf(category("p1", "sports", "Sports", 1)))
        database.m3uItemDao().upsertAll(
            listOf(
                item("p1", "sport-1", "Sport One", 1, "sport.one", "sports"),
                item("p1", "no-epg", "No Match", 2, null, "sports"),
            ),
        )
        database.epgDao().upsertChannels(listOf(epgChannel("p1", "sport.one", "Sport One")))
        database.epgDao().upsertProgrammes(listOf(programme("p1", "sport.one", "sport-now", "Cached Sport", 0, 60)))
        val window = TvGuideTimeline.defaultWindow(30 * 60_000L)

        val first = tvGuideRepository(providerId).guide(window, 30 * 60_000L, "sports")
        val reopened = tvGuideRepository(providerId).guide(window, 30 * 60_000L, "sports")

        assertEquals(listOf("sport-1", "no-epg"), reopened.channels.map { it.channelId })
        assertEquals(first.programmes["sport-1"]?.map { it.title }, reopened.programmes["sport-1"]?.map { it.title })
        assertEquals(listOf("Cached Sport"), reopened.programmes["sport-1"]?.map { it.title })
        assertTrue(reopened.programmes["no-epg"].isNullOrEmpty())
        assertEquals(1, reopened.epgProgrammeCount)
    }

    private fun tvGuideRepository(providerId: ProviderId): TvGuideRepository {
        val live = LiveTvRepository(
            database,
            FakeSettings(providerId),
            RoomFavoritesRepository(database.favoriteDao()),
            RoomHistoryRepository(database.watchHistoryDao()),
            FakeResolver,
        )
        val epg = EpgRepository(database, OkHttpClient(), ProviderCredentialStore(FakeSecretStore), FixedClock)
        return TvGuideRepository(database, live, epg)
    }

    private suspend fun seedProvider(id: String) {
        database.providerDao().upsert(ProviderEntity(id, id, "m3u_url", "http://example.invalid/list.m3u", 1, 1, null, true))
        database.epgDao().upsertSource(
            EpgSourceEntity(id, "source", "custom_url", "http://example.invalid/guide.xml", true, 1, null, null, null, null, null, null, 0, 0, 1, 1),
        )
    }

    private fun item(providerId: String, id: String, name: String, order: Int, tvgId: String?, categoryId: String = "all") = M3uItemEntity(
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
        categoryId = categoryId,
        userAgent = null,
        referrer = null,
        catchupType = null,
        catchupSource = null,
        catchupDays = null,
        timeshiftHours = null,
        channelNumber = order.toString(),
        contentType = "live",
        seriesName = null,
        seasonNumber = null,
        episodeNumber = null,
        playlistOrder = order,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private fun category(providerId: String, id: String, name: String, order: Int) =
        CategoryEntity(providerId, id, name, name.lowercase(), null, "live", order)

    private fun epgChannel(providerId: String, id: String, name: String) =
        EpgChannelEntity(providerId, id, name, name.lowercase(), null, 1)

    private fun programme(providerId: String, channel: String, id: String, title: String, startMinutes: Int, endMinutes: Int) =
        EpgProgrammeEntity(providerId, channel, id, title, "Description", startMinutes * 60_000L, endMinutes * 60_000L, 1)

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

    private object FixedClock : WatchioClock {
        override fun nowEpochMs(): Long = 90 * 60_000L
    }

    private object FakeSecretStore : SecretStore {
        override suspend fun putSecret(key: String, value: String) = Unit
        override suspend fun getSecret(key: String): String? = null
        override suspend fun removeSecret(key: String) = Unit
    }
}
