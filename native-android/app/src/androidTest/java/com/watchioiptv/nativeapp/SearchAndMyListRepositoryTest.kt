package com.watchioiptv.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.EpisodeEntity
import com.watchioiptv.nativeapp.core.database.LiveStreamEntity
import com.watchioiptv.nativeapp.core.database.ProviderEntity
import com.watchioiptv.nativeapp.core.database.SeriesEntity
import com.watchioiptv.nativeapp.core.database.VodStreamEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.RoomFavoritesRepository
import com.watchioiptv.nativeapp.data.RoomHistoryRepository
import com.watchioiptv.nativeapp.data.library.MyListRepository
import com.watchioiptv.nativeapp.data.library.SearchRepository
import com.watchioiptv.nativeapp.data.library.SearchScope
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.repository.FavoriteItem
import com.watchioiptv.nativeapp.domain.repository.HistoryItem
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchAndMyListRepositoryTest {
    private lateinit var database: WatchioDatabase
    private lateinit var settings: FakeSettings
    private lateinit var favorites: RoomFavoritesRepository
    private lateinit var history: RoomHistoryRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
        settings = FakeSettings(ProviderId("provider-a"))
        favorites = RoomFavoritesRepository(database.favoriteDao())
        history = RoomHistoryRepository(database.watchHistoryDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun globalAndScopedSearchUseSelectedProviderOnly() = runBlocking {
        seedProvider("provider-a")
        seedProvider("provider-b")
        database.liveStreamDao().upsertAll(listOf(live("provider-a", "l1", "Alpha News"), live("provider-b", "l2", "Alpha Other")))
        database.vodDao().upsertAll(listOf(movie("provider-a", "m1", "Alpha Movie")))
        database.seriesDao().upsertAll(listOf(series("provider-a", "s1", "Alpha Series")))

        val repository = SearchRepository(database, settings)
        val global = repository.search("alpha", SearchScope.Global)
        val liveOnly = repository.search("alpha", SearchScope.Live)

        assertEquals(listOf("l1"), global.live.map { it.contentId })
        assertEquals(listOf("m1"), global.movies.map { it.contentId })
        assertEquals(listOf("s1"), global.series.map { it.contentId })
        assertEquals(1, liveOnly.live.size)
        assertTrue(liveOnly.movies.isEmpty())
        assertTrue(liveOnly.series.isEmpty())
    }

    @Test
    fun myListUsesSharedFavoritesAndHistoryWithoutLiveContinue() = runBlocking {
        seedProvider("provider-a")
        database.vodDao().upsertAll(listOf(movie("provider-a", "m1", "Movie One")))
        database.seriesDao().upsertAll(listOf(series("provider-a", "s1", "Series One")))
        database.episodeDao().upsertAll(listOf(episode("provider-a", "s1", "e1")))
        favorites.toggle(FavoriteItem(ProviderId("provider-a"), ContentType.Live, "l1", title = "Live One", createdAtEpochMs = 1))
        favorites.toggle(FavoriteItem(ProviderId("provider-a"), ContentType.Movie, "m1", title = "Movie One", createdAtEpochMs = 2))
        history.upsert(HistoryItem(ProviderId("provider-a"), ContentType.Live, "l1", title = "Live One", positionMs = 10_000, durationMs = 100_000, lastWatchedAtEpochMs = 3))
        history.upsert(HistoryItem(ProviderId("provider-a"), ContentType.Movie, "m1", title = "Movie One", positionMs = 61_000, durationMs = 600_000, lastWatchedAtEpochMs = 4))
        history.upsert(HistoryItem(ProviderId("provider-a"), ContentType.Episode, "s1", subContentId = "e1", title = "Episode One", positionMs = 70_000, durationMs = 700_000, lastWatchedAtEpochMs = 5))
        history.upsert(HistoryItem(ProviderId("provider-a"), ContentType.Movie, "m2", title = "Complete Movie", positionMs = 590_000, durationMs = 600_000, lastWatchedAtEpochMs = 6))

        val repository = MyListRepository(database, settings, favorites, history)
        val data = repository.load()

        assertEquals(listOf("s1", "m1"), data.continueWatching.map { it.contentId })
        assertEquals(listOf("l1"), data.liveFavorites.map { it.contentId })
        assertEquals(listOf("m1"), data.movieFavorites.map { it.contentId })
        assertEquals(4, data.history.size)
    }

    @Test
    fun removeFavoriteDeletesOnlyRequestedItem() = runBlocking {
        seedProvider("provider-a")
        favorites.toggle(FavoriteItem(ProviderId("provider-a"), ContentType.Movie, "m1", title = "Movie One", createdAtEpochMs = 1))
        favorites.toggle(FavoriteItem(ProviderId("provider-a"), ContentType.Series, "s1", title = "Series One", createdAtEpochMs = 2))

        val repository = MyListRepository(database, settings, favorites, history)
        val item = repository.load().movieFavorites.single()
        val data = repository.removeFavorite(item)

        assertTrue(data.movieFavorites.isEmpty())
        assertEquals(listOf("s1"), data.seriesFavorites.map { it.contentId })
    }

    @Test
    fun searchHandlesThirtyThousandLiveRowsWithLimitAndOrder() = runBlocking {
        seedProvider("provider-a")
        (0 until 30_000).chunked(1_000).forEach { chunk ->
            database.liveStreamDao().upsertAll(chunk.map { live("provider-a", "l-$it", "Search Channel $it", it) })
        }

        val results = SearchRepository(database, settings).search("search channel", SearchScope.Live, limitPerType = 40)

        assertEquals(40, results.live.size)
        assertEquals("l-0", results.live.first().contentId)
        assertEquals("l-39", results.live.last().contentId)
    }

    private suspend fun seedProvider(id: String, type: String = "xtream") {
        database.providerDao().upsert(ProviderEntity(id, id, type, "http://example.invalid", 1, 1, null, true))
    }

    private fun live(providerId: String, id: String, name: String, order: Int = 0) =
        LiveStreamEntity(providerId, id, name, name.lowercase(), null, null, id, "ts", order, 1, 1)

    private fun movie(providerId: String, id: String, name: String) =
        VodStreamEntity(providerId, id, name, name.lowercase(), null, null, null, "mp4", null, null, 1, 1)

    private fun series(providerId: String, id: String, name: String) = SeriesEntity(
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
        categoryId = null,
        tmdbId = null,
        serverOrder = 1,
        updatedAtEpochMs = 1,
    )

    private fun episode(providerId: String, seriesId: String, id: String) = EpisodeEntity(
        providerId = providerId,
        seriesId = seriesId,
        episodeId = id,
        seasonNumber = 1,
        episodeNumber = 1,
        title = "Episode One",
        normalizedTitle = "episode one",
        imageUrl = null,
        containerExtension = "mp4",
        durationSecs = null,
        rating = null,
    )

    private class FakeSettings(providerId: ProviderId) : SettingsRepository {
        private val selected = MutableStateFlow<ProviderId?>(providerId)
        override val selectedProviderId: Flow<ProviderId?> = selected
        override val inputMode: Flow<InputMode> = MutableStateFlow(InputMode.Auto)
        override val streamFormat: Flow<StreamFormat> = MutableStateFlow(StreamFormat.Auto)
        override suspend fun setSelectedProviderId(providerId: ProviderId?) {
            selected.value = providerId
        }
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit
    }
}
