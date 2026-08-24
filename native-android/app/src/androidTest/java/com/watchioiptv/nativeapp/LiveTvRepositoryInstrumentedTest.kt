package com.watchioiptv.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.CategoryEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.database.ProviderEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.RoomFavoritesRepository
import com.watchioiptv.nativeapp.data.RoomHistoryRepository
import com.watchioiptv.nativeapp.data.live.LiveTvCategoryKind
import com.watchioiptv.nativeapp.data.live.LiveTvRepository
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.repository.FavoriteItem
import com.watchioiptv.nativeapp.domain.repository.HistoryItem
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTvRepositoryInstrumentedTest {
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
    fun m3uLiveChannelsCarryHeadersAndVirtualCategoriesFilterFavoritesHistory() = runBlocking {
        val providerId = ProviderId("provider-m3u")
        database.providerDao().upsert(ProviderEntity(providerId.value, "M3U", "m3u_url", "http://example.invalid/list.m3u", 1, 1, null, true))
        database.categoryDao().upsertAll(listOf(CategoryEntity(providerId.value, "news", "News", "news", null, "live", 1)))
        database.m3uItemDao().upsertAll(
            listOf(
                item(providerId.value, "one", "News One", "news", 1),
                item(providerId.value, "two", "News Two", "news", 2),
            ),
        )
        val favorites = RoomFavoritesRepository(database.favoriteDao())
        val history = RoomHistoryRepository(database.watchHistoryDao())
        favorites.toggle(FavoriteItem(providerId, ContentType.Live, "one", title = "News One", createdAtEpochMs = 1))
        history.upsert(HistoryItem(providerId, ContentType.Live, "two", title = "News Two", lastWatchedAtEpochMs = 2))
        val repository = LiveTvRepository(database, FakeSettings(providerId), favorites, history, FakeResolver)

        val categories = repository.categories(providerId)
        assertEquals(listOf(LiveTvCategoryKind.All, LiveTvCategoryKind.Favorites, LiveTvCategoryKind.History, LiveTvCategoryKind.Provider), categories.map { it.kind })

        val all = repository.channels(providerId, categories.first { it.kind == LiveTvCategoryKind.All })
        assertEquals(listOf("one", "two"), all.map { it.id })
        assertEquals("Agent", all.first().headers["User-Agent"])
        assertEquals("http://example.invalid/ref", all.first().headers["Referer"])

        assertEquals(listOf("one"), repository.channels(providerId, categories.first { it.kind == LiveTvCategoryKind.Favorites }).map { it.id })
        assertEquals(listOf("two"), repository.channels(providerId, categories.first { it.kind == LiveTvCategoryKind.History }).map { it.id })
        assertTrue(repository.playback(all.first()).url.endsWith("/one.ts"))
    }

    @Test
    fun largeLiveCatalogKeepsPlaylistOrder() = runBlocking {
        val providerId = ProviderId("provider-large")
        database.providerDao().upsert(ProviderEntity(providerId.value, "Large", "m3u_url", "http://example.invalid/list.m3u", 1, 1, null, true))
        database.categoryDao().upsertAll(listOf(CategoryEntity(providerId.value, "all", "All", "all", null, "live", 1)))
        val batchSize = 1_000
        (0 until 10_000).chunked(batchSize).forEach { chunk ->
            database.m3uItemDao().upsertAll(chunk.map { index -> item(providerId.value, "ch-$index", "Channel $index", "all", index) })
        }
        val repository = LiveTvRepository(
            database,
            FakeSettings(providerId),
            RoomFavoritesRepository(database.favoriteDao()),
            RoomHistoryRepository(database.watchHistoryDao()),
            FakeResolver,
        )

        val channels = repository.channels(providerId, repository.categories(providerId).first())

        assertEquals(10_000, channels.size)
        assertEquals("ch-0", channels.first().id)
        assertEquals("ch-9999", channels.last().id)
    }

    private fun item(providerId: String, id: String, name: String, category: String, order: Int) = M3uItemEntity(
        providerId = providerId,
        itemId = id,
        directUrl = "http://example.invalid/$id.ts",
        name = name,
        normalizedName = name.lowercase(),
        tvgId = id,
        tvgName = name,
        tvgLogo = null,
        tvgUrl = null,
        tvgRec = null,
        tvgShift = null,
        groupTitle = "News",
        groupName = null,
        categoryId = category,
        userAgent = "Agent",
        referrer = "http://example.invalid/ref",
        catchupType = null,
        catchupSource = null,
        catchupDays = null,
        timeshiftHours = null,
        channelNumber = null,
        contentType = "live",
        seriesName = null,
        seasonNumber = null,
        episodeNumber = null,
        playlistOrder = order,
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
}
