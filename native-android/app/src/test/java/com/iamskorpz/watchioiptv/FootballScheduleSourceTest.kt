package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.FootballDataApi
import com.iamskorpz.watchioiptv.feature.sports.FootballDataScheduleSource
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.SportsCompetitionCatalog
import com.iamskorpz.watchioiptv.feature.sports.SportsFixtureStatus
import com.iamskorpz.watchioiptv.feature.sports.SportsScheduleException
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class FootballScheduleSourceTest {
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    @Test fun mapsFixtureDateTimezoneStatusAndScore() = runTest {
        server.enqueue(MockResponse().setBody(JSON).setHeader("Content-Type", "application/json"))
        val result = source("key").getFixtures(LocalDate.of(2026, 9, 6)).getOrThrow().single()
        assertEquals("2026-09-06T16:30:00Z", result.kickoffUtc.toString())
        assertEquals(SportsFixtureStatus.Finished, result.status)
        assertEquals(2, result.homeScore)
        assertEquals(1, result.awayScore)
        val path = server.takeRequest().path!!
        assertTrue(path.contains("dateFrom=2026-09-06"))
        assertTrue(path.contains("dateTo=2026-09-07"))
    }

    @Test fun emptyResultMapsCleanly() = runTest {
        server.enqueue(MockResponse().setBody("{\"matches\":[]}").setHeader("Content-Type", "application/json"))
        assertTrue(source("key").getFixtures(LocalDate.of(2026, 9, 6)).getOrThrow().isEmpty())
    }

    @Test fun localDateFilterIncludesOnlyRequestedLondonDay() = runTest {
        server.enqueue(jsonResponse(CROSS_MIDNIGHT_JSON))
        val fixtures = source("key", zoneId = ZoneId.of("Europe/London")).getFixtures(LocalDate.of(2026, 9, 6)).getOrThrow()
        assertEquals(listOf("1", "2"), fixtures.map { it.id })
    }

    @Test fun cacheIsPerDateAndAvoidsRefetch() = runTest {
        server.enqueue(jsonResponse(JSON))
        server.enqueue(jsonResponse("{\"matches\":[]}"))
        val source = source("key")
        val date = LocalDate.of(2026, 9, 6)
        assertEquals(1, source.getFixtures(date).getOrThrow().size)
        assertEquals(1, source.getFixtures(date).getOrThrow().size)
        assertTrue(source.getFixtures(date.plusDays(1)).getOrThrow().isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test fun rateLimitUsesRetryAfterAndBlocksNetworkDuringCooldown() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-06T12:00:00Z"))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        server.enqueue(jsonResponse(JSON))
        val source = source("key", clock = clock)
        val date = LocalDate.of(2026, 9, 6)
        val first = source.getFixtures(date).exceptionOrNull() as SportsScheduleException.RateLimited
        assertEquals(clock.millis() + 30_000L, first.retryAvailableAtEpochMs)
        assertTrue(source.getFixtures(date).exceptionOrNull() is SportsScheduleException.RateLimited)
        assertEquals(1, server.requestCount)
        clock.advance(30_000L)
        assertEquals(1, source.getFixtures(date).getOrThrow().size)
        assertEquals(2, server.requestCount)
    }

    @Test fun duplicateInFlightDateSharesOneRequest() = runBlocking {
        server.enqueue(jsonResponse(JSON).setBodyDelay(100, TimeUnit.MILLISECONDS))
        val source = source("key")
        val date = LocalDate.of(2026, 9, 6)
        val first = async { source.getFixtures(date).getOrThrow() }
        val second = async { source.getFixtures(date).getOrThrow() }
        assertEquals(first.await(), second.await())
        assertEquals(1, server.requestCount)
    }

    @Test fun authenticationFailureMapsToInvalidCredential() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(source("key").getFixtures(LocalDate.of(2026, 9, 6)).exceptionOrNull() is SportsScheduleException.InvalidCredential)
    }

    @Test fun forbiddenFailureMapsToInvalidCredentialWithoutLeakingKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val error = source("test-token").getFixtures(LocalDate.of(2026, 9, 6)).exceptionOrNull()
        assertTrue(error is SportsScheduleException.InvalidCredential)
        assertTrue(error?.message?.contains("test-token") == false)
    }

    @Test fun serverFailureMapsToTemporarilyUnavailable() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(source("key").getFixtures(LocalDate.of(2026, 9, 6)).exceptionOrNull() is SportsScheduleException.TemporarilyUnavailable)
    }

    @Test fun missingKeyRequiresSetupWithoutNetwork() = runTest {
        assertTrue(source("").getFixtures(LocalDate.of(2026, 9, 6)).exceptionOrNull() is SportsScheduleException.MissingCredential)
        assertEquals(0, server.requestCount)
    }
    @Test fun competitionOrderPrioritizesConfiguredCodes() { assertTrue(SportsCompetitionCatalog.displayOrder("PL") < SportsCompetitionCatalog.displayOrder("XYZ")) }

    private fun source(
        key: String,
        clock: Clock = Clock.systemUTC(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): FootballDataScheduleSource {
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType())).build()
        return FootballDataScheduleSource(retrofit.create(FootballDataApi::class.java), FakeCredentialStore(key), clock, zoneId)
    }

    private class FakeCredentialStore(private val value: String?) : FootballDataCredentialStore {
        override suspend fun get() = value
        override suspend fun save(value: String) = Unit
        override suspend fun remove() = Unit
    }

    private fun jsonResponse(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(milliseconds: Long) { now = now.plusMillis(milliseconds) }
    }

    private companion object {
        const val JSON = """{"matches":[{"id":1,"competition":{"id":2021,"name":"Premier League","code":"PL"},"utcDate":"2026-09-06T16:30:00Z","status":"FINISHED","homeTeam":{"name":"Arsenal"},"awayTeam":{"name":"Chelsea"},"score":{"fullTime":{"home":2,"away":1}}}]}"""
        const val CROSS_MIDNIGHT_JSON = """{"matches":[
            {"id":1,"competition":{"id":2021,"name":"Premier League","code":"PL"},"utcDate":"2026-09-05T23:30:00Z","status":"SCHEDULED","homeTeam":{"name":"A"},"awayTeam":{"name":"B"}},
            {"id":2,"competition":{"id":2021,"name":"Premier League","code":"PL"},"utcDate":"2026-09-06T22:30:00Z","status":"SCHEDULED","homeTeam":{"name":"C"},"awayTeam":{"name":"D"}},
            {"id":3,"competition":{"id":2021,"name":"Premier League","code":"PL"},"utcDate":"2026-09-06T23:30:00Z","status":"SCHEDULED","homeTeam":{"name":"E"},"awayTeam":{"name":"F"}}
        ]}"""
    }
}
