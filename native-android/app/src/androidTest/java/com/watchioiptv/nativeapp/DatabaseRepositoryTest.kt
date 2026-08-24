package com.watchioiptv.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.CategoryEntity
import com.watchioiptv.nativeapp.core.database.EpisodeEntity
import com.watchioiptv.nativeapp.core.database.FavoriteEntity
import com.watchioiptv.nativeapp.core.database.LiveStreamEntity
import com.watchioiptv.nativeapp.core.database.ProviderEntity
import com.watchioiptv.nativeapp.core.database.SeasonEntity
import com.watchioiptv.nativeapp.core.database.SeriesEntity
import com.watchioiptv.nativeapp.core.database.VodStreamEntity
import com.watchioiptv.nativeapp.core.database.WatchHistoryEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import com.watchioiptv.nativeapp.data.RoomFavoritesRepository
import com.watchioiptv.nativeapp.data.RoomHistoryRepository
import com.watchioiptv.nativeapp.data.RoomProviderRepository
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.repository.FavoriteItem
import com.watchioiptv.nativeapp.domain.repository.HistoryItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseRepositoryTest {
    private lateinit var database: WatchioDatabase
    private lateinit var secrets: InMemorySecretStore

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
        secrets = InMemorySecretStore()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun providerCrudAndDeletionCascadesDataAndSecrets() = runBlocking {
        val credentialStore = ProviderCredentialStore(secrets)
        val repository = RoomProviderRepository(database.providerDao(), credentialStore)
        val provider = provider("provider-a")
        database.providerDao().upsert(provider)
        credentialStore.saveXtreamCredentials("provider-a", XtreamCredentials("fake-user", "fake-pass"))
        database.liveStreamDao().upsertAll(listOf(live("provider-a", "1")))

        assertEquals("Provider a", repository.getProvider(ProviderId("provider-a"))?.displayName)
        repository.deleteProvider(ProviderId("provider-a"))

        assertNull(repository.getProvider(ProviderId("provider-a")))
        assertTrue(database.liveStreamDao().getByProvider("provider-a").isEmpty())
        assertNull(credentialStore.getXtreamCredentials("provider-a"))
    }

    @Test
    fun categoryAndCatalogReplaceAreProviderScopedAndOrdered() = runBlocking {
        database.providerDao().upsert(provider("provider-a"))
        database.providerDao().upsert(provider("provider-b"))

        database.categoryDao().replaceCategories(
            "provider-a",
            "live",
            listOf(category("provider-a", "news", "News", 2), category("provider-a", "sports", "Sports", 1)),
        )
        database.liveStreamDao().replaceLiveStreams("provider-a", listOf(live("provider-a", "2", order = 2), live("provider-a", "1", order = 1)))
        database.liveStreamDao().replaceLiveStreams("provider-b", listOf(live("provider-b", "9")))

        assertEquals(listOf("sports", "news"), database.categoryDao().getByType("provider-a", "live").map { it.categoryId })
        assertEquals(listOf("1", "2"), database.liveStreamDao().getByProvider("provider-a").map { it.streamId })
        assertEquals(listOf("9"), database.liveStreamDao().getByProvider("provider-b").map { it.streamId })
    }

    @Test
    fun movieSeriesSeasonAndEpisodeQueriesUseStableIdentity() = runBlocking {
        database.providerDao().upsert(provider("provider-a"))
        database.vodDao().replaceMovies("provider-a", listOf(movie("provider-a", "m2", 2), movie("provider-a", "m1", 1)))
        database.seriesDao().replaceSeries("provider-a", listOf(series("provider-a", "s1", 1)))
        database.seriesDao().upsertSeasons(listOf(SeasonEntity(providerId = "provider-a", seriesId = "s1", seasonNumber = 1, name = "Season 1", overview = null, coverUrl = null, episodeCount = 2)))
        database.episodeDao().upsertAll(
            listOf(
                episode("provider-a", "s1", "e2", 1, 2),
                episode("provider-a", "s1", "e1", 1, 1),
            ),
        )

        assertEquals(listOf("m1", "m2"), database.vodDao().getByProvider("provider-a").map { it.streamId })
        assertEquals(1, database.seriesDao().getSeasons("provider-a", "s1").single().seasonNumber)
        assertEquals(listOf("e1", "e2"), database.episodeDao().getBySeries("provider-a", "s1").map { it.episodeId })
    }

    @Test
    fun favoritesTogglePreventsDuplicatesAndIsolatesProviders() = runBlocking {
        database.providerDao().upsert(provider("provider-a"))
        database.providerDao().upsert(provider("provider-b"))
        val repository = RoomFavoritesRepository(database.favoriteDao())
        val fav = FavoriteItem(ProviderId("provider-a"), ContentType.Movie, "movie-1", title = "Movie", createdAtEpochMs = 1)

        assertTrue(repository.toggle(fav))
        assertTrue(repository.isFavorite(ProviderId("provider-a"), ContentType.Movie, "movie-1"))
        assertFalse(repository.isFavorite(ProviderId("provider-b"), ContentType.Movie, "movie-1"))
        assertFalse(repository.toggle(fav))
        assertFalse(repository.isFavorite(ProviderId("provider-a"), ContentType.Movie, "movie-1"))
    }

    @Test
    fun historyUpsertSupportsEpisodeIdentityAndRecentSorting() = runBlocking {
        database.providerDao().upsert(provider("provider-a"))
        val repository = RoomHistoryRepository(database.watchHistoryDao())

        repository.upsert(HistoryItem(ProviderId("provider-a"), ContentType.Episode, "series-1", "episode-1", "Ep 1", positionMs = 10, durationMs = 100, lastWatchedAtEpochMs = 2))
        repository.upsert(HistoryItem(ProviderId("provider-a"), ContentType.Episode, "series-1", "episode-2", "Ep 2", positionMs = 20, durationMs = 100, lastWatchedAtEpochMs = 3))

        assertEquals(10L, repository.find(ProviderId("provider-a"), ContentType.Episode, "series-1", "episode-1")?.positionMs)
        assertEquals(listOf("episode-2", "episode-1"), repository.recent(ProviderId("provider-a")).map { it.subContentId })
    }

    private fun provider(id: String) = ProviderEntity(id, id.replaceFirstChar { it.uppercase() }.replace("-", " "), "xtream", "http://example.invalid", 1, 1, null, true)
    private fun category(providerId: String, id: String, name: String, order: Int) = CategoryEntity(providerId, id, name, name.lowercase(), null, "live", order)
    private fun live(providerId: String, id: String, order: Int = 0) = LiveStreamEntity(providerId, id, "Live $id", "live $id", null, null, null, "ts", order, 1, 1)
    private fun movie(providerId: String, id: String, order: Int) = VodStreamEntity(providerId, id, "Movie $id", "movie $id", null, null, null, "mp4", null, null, order, 1)
    private fun series(providerId: String, id: String, order: Int) = SeriesEntity(
        providerId = providerId,
        seriesId = id,
        name = "Series $id",
        normalizedName = "series $id",
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
        serverOrder = order,
        updatedAtEpochMs = 1,
    )
    private fun episode(providerId: String, seriesId: String, id: String, season: Int, episode: Int) = EpisodeEntity(
        providerId = providerId,
        seriesId = seriesId,
        episodeId = id,
        seasonNumber = season,
        episodeNumber = episode,
        title = "Episode $episode",
        normalizedTitle = "episode $episode",
        imageUrl = null,
        containerExtension = "mp4",
        durationSecs = null,
        rating = null,
    )

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun putSecret(key: String, value: String) {
            values[key] = value
        }
        override suspend fun getSecret(key: String): String? = values[key]
        override suspend fun removeSecret(key: String) {
            values.remove(key)
        }
    }
}
