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
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.RoomFavoritesRepository
import com.watchioiptv.nativeapp.data.RoomHistoryRepository
import com.watchioiptv.nativeapp.data.movies.MovieCategoryKind
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
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
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

@RunWith(AndroidJUnit4::class)
class MoviesRepositoryInstrumentedTest {
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
    fun m3uMoviesUseVirtualCategoriesHeadersAndLargeCatalogOrder() = runBlocking {
        val providerId = ProviderId("m3u-movies")
        database.providerDao().upsert(ProviderEntity(providerId.value, "M3U", "m3u_url", "http://example.invalid/list.m3u", 1, 1, null, true))
        database.categoryDao().upsertAll(listOf(CategoryEntity(providerId.value, "movies", "Movies", "movies", null, "movie", 1)))
        (0 until 10_000).chunked(1_000).forEach { chunk ->
            database.m3uItemDao().upsertAll(chunk.map { index -> item(providerId.value, "m-$index", index) })
        }
        val repository = repository(providerId)
        val categories = repository.categories(providerId)
        val movies = repository.movies(providerId, categories.first { it.kind == MovieCategoryKind.All })

        assertEquals(10_000, movies.size)
        assertEquals("m-0", movies.first().id)
        assertEquals("m-9999", movies.last().id)
        assertEquals("Agent", movies.first().headers["User-Agent"])
        assertEquals("http://example.invalid/ref", movies.first().headers["Referer"])
        assertTrue(repository.playback(movies.first(), resume = false).url.endsWith("/m-0.mp4"))
    }

    @Test
    fun allMoviesAndProviderCategoryUseSameM3uMapping() = runBlocking {
        val providerId = ProviderId("m3u-all-mapping")
        database.providerDao().upsert(ProviderEntity(providerId.value, "M3U", "m3u_url", "http://example.invalid/list.m3u", 1, 1, null, true))
        database.categoryDao().upsertAll(listOf(CategoryEntity(providerId.value, "movies", "Movies", "movies", null, "movie", 1)))
        database.m3uItemDao().upsertAll(
            listOf(
                M3uItemEntity(
                    providerId = providerId.value,
                    itemId = "movie-1",
                    directUrl = "http://example.invalid/movie-1.mkv",
                    name = "The.Yearling.1946.1080p.BluRay.x265",
                    normalizedName = "the yearling 1946",
                    tvgId = "movie-1",
                    tvgName = "The Yearling",
                    tvgLogo = "http://example.invalid/poster.jpg",
                    tvgUrl = null,
                    tvgRec = null,
                    tvgShift = null,
                    groupTitle = "Movies",
                    groupName = null,
                    categoryId = "movies",
                    userAgent = "Agent",
                    referrer = "http://example.invalid/ref",
                    catchupType = null,
                    catchupSource = null,
                    catchupDays = null,
                    timeshiftHours = null,
                    channelNumber = null,
                    contentType = "movie",
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    playlistOrder = 1,
                    createdAtEpochMs = 1,
                    updatedAtEpochMs = 1,
                ),
            ),
        )
        val repository = repository(providerId)
        val categories = repository.categories(providerId)
        val allMovie = repository.movies(providerId, categories.first { it.kind == MovieCategoryKind.All }).single()
        val categoryMovie = repository.movies(providerId, categories.first { it.kind == MovieCategoryKind.Provider }).single()

        assertEquals(categoryMovie.id, allMovie.id)
        assertEquals(categoryMovie.name, allMovie.name)
        assertEquals("The Yearling", allMovie.name)
        assertEquals(categoryMovie.posterUrl, allMovie.posterUrl)
        assertEquals("http://example.invalid/poster.jpg", allMovie.posterUrl)
        assertEquals(categoryMovie.headers, allMovie.headers)
    }

    @Test
    fun continueWatchingCategoryFiltersAndSortsResumableMoviesOnly() = runBlocking {
        val providerId = ProviderId("m3u-cw")
        database.providerDao().upsert(ProviderEntity(providerId.value, "M3U", "m3u_url", "http://example.invalid/list.m3u", 1, 1, null, true))
        database.categoryDao().upsertAll(listOf(CategoryEntity(providerId.value, "movies", "Movies", "movies", null, "movie", 1)))
        database.m3uItemDao().upsertAll(
            listOf(
                item(providerId.value, "m-1", 1),
                item(providerId.value, "m-2", 2),
                item(providerId.value, "m-3", 3),
            ),
        )
        val historyRepo = RoomHistoryRepository(database.watchHistoryDao())
        // m-1: resumable (newest)
        historyRepo.upsert(com.watchioiptv.nativeapp.domain.repository.HistoryItem(providerId, com.watchioiptv.nativeapp.domain.model.ContentType.Movie, "m-1", title = "Movie 1", positionMs = 120_000L, durationMs = 600_000L, lastWatchedAtEpochMs = 100))
        // s-1: episode (should be excluded)
        historyRepo.upsert(com.watchioiptv.nativeapp.domain.repository.HistoryItem(providerId, com.watchioiptv.nativeapp.domain.model.ContentType.Episode, "s-1", subContentId = "e-1", title = "Show 1", positionMs = 120_000L, durationMs = 600_000L, lastWatchedAtEpochMs = 90))
        // m-deleted: not in catalog (should be excluded)
        historyRepo.upsert(com.watchioiptv.nativeapp.domain.repository.HistoryItem(providerId, com.watchioiptv.nativeapp.domain.model.ContentType.Movie, "m-deleted", title = "Deleted", positionMs = 120_000L, durationMs = 600_000L, lastWatchedAtEpochMs = 80))
        // m-2: completed (should be excluded)
        historyRepo.upsert(com.watchioiptv.nativeapp.domain.repository.HistoryItem(providerId, com.watchioiptv.nativeapp.domain.model.ContentType.Movie, "m-2", title = "Movie 2", positionMs = 590_000L, durationMs = 600_000L, lastWatchedAtEpochMs = 70))
        // m-3: resumable (older)
        historyRepo.upsert(com.watchioiptv.nativeapp.domain.repository.HistoryItem(providerId, com.watchioiptv.nativeapp.domain.model.ContentType.Movie, "m-3", title = "Movie 3", positionMs = 80_000L, durationMs = 600_000L, lastWatchedAtEpochMs = 60))

        val repository = repository(providerId)
        val categories = repository.categories(providerId)
        val cwCategory = categories.first { it.kind == MovieCategoryKind.ContinueWatching }
        assertEquals("continue_watching", cwCategory.id)
        assertEquals("CONTINUE WATCHING", cwCategory.name)
        assertEquals(1, categories.indexOf(cwCategory))

        val cwMovies = repository.movies(providerId, cwCategory)
        assertEquals(listOf("m-1", "m-3"), cwMovies.map { it.id })
    }

    @Test
    fun catalogInMemeoryCacheReusesObjectsAndInvalidatesCorrectly() = runBlocking {
        val provider1 = ProviderId("m3u-cache-1")
        val provider2 = ProviderId("m3u-cache-2")

        database.providerDao().upsert(ProviderEntity(provider1.value, "M3U 1", "m3u_url", "http://example.invalid/list1.m3u", 1, 1, null, true))
        database.providerDao().upsert(ProviderEntity(provider2.value, "M3U 2", "m3u_url", "http://example.invalid/list2.m3u", 1, 1, null, true))

        database.m3uItemDao().upsertAll(listOf(item(provider1.value, "m-p1-1", 1)))
        database.m3uItemDao().upsertAll(listOf(item(provider2.value, "m-p2-1", 1)))

        val repo = repository(provider1)
        val catalog1 = repo.getOrLoadCatalog(provider1)
        assertEquals(1, catalog1.movies.size)
        assertEquals("m-p1-1", catalog1.movies.first().id)

        // Second call should return exact same cache instance
        val catalog1Again = repo.getOrLoadCatalog(provider1)
        assertTrue(catalog1 === catalog1Again)

        // Load provider 2
        val catalog2 = repo.getOrLoadCatalog(provider2)
        assertEquals(1, catalog2.movies.size)
        assertEquals("m-p2-1", catalog2.movies.first().id)

        // Invalidate provider 1 only
        repo.invalidateCache(provider1)
        // Provider 2 cache is not provider 1, so loading provider 1 re-fetches
        val catalog1Reloaded = repo.getOrLoadCatalog(provider1)
        assertEquals("m-p1-1", catalog1Reloaded.movies.first().id)
    }

    private fun repository(providerId: ProviderId) = MoviesRepository(
        database = database,
        settingsRepository = FakeSettings(providerId),
        favoritesRepository = RoomFavoritesRepository(database.favoriteDao()),
        historyRepository = RoomHistoryRepository(database.watchHistoryDao()),
        playbackUrlResolver = FakeResolver,
        credentialStore = ProviderCredentialStore(FakeSecretStore()),
        retrofitFactory = ::retrofit,
        tmdbRetrofitFactory = ::retrofit,
        clock = object : WatchioClock { override fun nowEpochMs(): Long = 1 },
    )

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient())
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun item(providerId: String, id: String, order: Int) = M3uItemEntity(
        providerId, id, "http://example.invalid/$id.mp4", "Movie $order", "movie $order", id, "Movie $order", null, null, null, null,
        "Movies", null, "movies", "Agent", "http://example.invalid/ref", null, null, null, null, null, "movie", null, null, null, order, 1, 1,
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
        override suspend fun resolve(request: PlaybackUrlRequest): String = "http://example.invalid/${request.contentId}.mp4"
    }

    private class FakeSecretStore : SecretStore {
        override suspend fun putSecret(key: String, value: String) = Unit
        override suspend fun getSecret(key: String): String? = null
        override suspend fun removeSecret(key: String) = Unit
    }
}
