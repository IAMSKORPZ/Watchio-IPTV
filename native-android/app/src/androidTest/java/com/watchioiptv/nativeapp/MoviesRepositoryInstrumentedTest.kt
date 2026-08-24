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
