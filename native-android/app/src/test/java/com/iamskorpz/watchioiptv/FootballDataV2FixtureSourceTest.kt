package com.iamskorpz.watchioiptv

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.iamskorpz.watchioiptv.feature.sports.FootballDataApi
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FootballDataV2FixtureSourceTest {
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun teardown() { server.shutdown() }

    @Test fun mapsSuccessfulResponseAndPreservesRequestFilters() = runTest {
        server.enqueue(MockResponse().setBody(validResponse()).setHeader("Content-Type", "application/json"))
        val result = source().fixtures(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4)) as SportsSourceResult.Success
        assertEquals(1, result.data.size)
        assertEquals(now, result.fetchedAt)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("dateFrom=2026-10-03"))
        assertTrue(request.path!!.contains("dateTo=2026-10-05"))
        assertEquals("test-key", request.getHeader("X-Auth-Token"))
    }

    @Test fun missingCredentialIsUnauthorizedWithoutNetworkCall() = runTest {
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source(null).fixtures(LocalDate.now(), LocalDate.now()))
        assertEquals(0, server.requestCount)
    }

    @Test fun mapsUnauthorizedRateLimitNetworkAndParseErrors() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        val limited = source().fixtures(LocalDate.now(), LocalDate.now()) as SportsSourceResult.Failure
        assertEquals(SportsSourceError.RateLimited(now.plusSeconds(60)), limited.error)
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unavailable), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(MockResponse().setBody("not-json").setHeader("Content-Type", "application/json"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.ParseFailure), source().fixtures(LocalDate.now(), LocalDate.now()))
    }

    private fun source(key: String? = "test-key"): FootballDataV2FixtureSource {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FootballDataApi::class.java)
        return FootballDataV2FixtureSource(api, object : FootballDataCredentialStore {
            override suspend fun get() = key
            override suspend fun save(value: String) = Unit
            override suspend fun remove() = Unit
        }, Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun validResponse() = """
        {"matches":[{"id":42,"competition":{"id":1,"name":"Premier League","code":"PL","area":{"name":"England"}},"utcDate":"2026-10-03T15:00:00Z","status":"TIMED","homeTeam":{"id":10,"name":"Arsenal","shortName":"ARS"},"awayTeam":{"id":11,"name":"Chelsea","shortName":"CHE"},"score":{"fullTime":{"home":null,"away":null}}}]}
    """.trimIndent()
}
