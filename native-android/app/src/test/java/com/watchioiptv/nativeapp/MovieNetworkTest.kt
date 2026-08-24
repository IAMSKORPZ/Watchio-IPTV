package com.watchioiptv.nativeapp

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.watchioiptv.nativeapp.data.xtream.XtreamApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit

class MovieNetworkTest {
    @Test
    fun xtreamVodInfoParsesValidFixture() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"info":{"name":"Example Movie","movie_image":"http://example.invalid/poster.jpg","tmdb_id":"123","youtube_trailer":"abc"}}""",
                ),
            )
            server.start()

            val info = api(server).vodInfo("user", "pass", vodId = "77").info

            assertEquals("Example Movie", info?.name)
            assertEquals(123, info?.tmdbId)
            assertEquals("abc", info?.youtubeTrailer)
        }
    }

    @Test
    fun xtreamVodInfoHandlesAbsentOptionalFields() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"info":{"name":"Sparse"}}"""))
            server.start()

            val info = api(server).vodInfo("user", "pass", vodId = "77").info

            assertEquals("Sparse", info?.name)
            assertNull(info?.tmdbId)
        }
    }

    private fun api(server: MockWebServer): XtreamApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(XtreamApi::class.java)
}
