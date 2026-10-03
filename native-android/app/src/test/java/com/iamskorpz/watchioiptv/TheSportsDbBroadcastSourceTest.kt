package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.security.SensitiveUrlMasker
import com.iamskorpz.watchioiptv.feature.sports.v2.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class TheSportsDbBroadcastSourceTest {
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-10-04T10:00:00Z")

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun teardown() { server.shutdown() }

    @Test fun reconcilesEventThenLooksUpAndMapsMultipleTvResults() = runTest {
        server.enqueue(json(events(event())))
        server.enqueue(json(tvEnvelope(tv("10", "Sky Sports Main Event", "United Kingdom"), tv("11", "Peacock", "United States"))))
        val result = source().broadcasts(fixture()) as SportsSourceResult.Success
        assertEquals(2, result.data.size)
        assertEquals(listOf("GB", "US"), result.data.map { it.countryOrRegion })
        assertEquals(BroadcastReconciliationConfidence.Exact, result.data.first().evidence.single().confidence)
        val eventsRequest = server.takeRequest()
        val tvRequest = server.takeRequest()
        assertTrue(eventsRequest.path!!.startsWith("/api/v1/json/synthetic-key/eventsday.php"))
        assertEquals("Soccer", eventsRequest.requestUrl!!.queryParameter("s"))
        assertEquals("event-500", tvRequest.requestUrl!!.queryParameter("id"))
        assertFalse(SensitiveUrlMasker.mask(eventsRequest.requestUrl.toString()).contains("synthetic-key"))
        assertFalse(result.data.toString().contains("synthetic-key"))
    }

    @Test fun eventNameFallbackMissingCountryAndEmptyResultsAreSafe() = runTest {
        server.enqueue(json(events(event(homeAwayFields = false))))
        server.enqueue(json(tvEnvelope(tv("10", "BBC One", null))))
        val result = source().broadcasts(fixture()) as SportsSourceResult.Success
        assertNull(result.data.single().countryOrRegion)

        server.enqueue(json("{\"events\":null}"))
        assertEquals(SportsSourceResult.NoData, source().broadcasts(fixture()))
        server.enqueue(json(events(event())))
        server.enqueue(json("{\"tvevent\":null}"))
        assertEquals(SportsSourceResult.NoData, source().broadcasts(fixture()))
    }

    @Test fun wrongOrAmbiguousEventNeverTriggersTvLookup() = runTest {
        server.enqueue(json(events(event(league = "Championship"))))
        assertEquals(SportsSourceResult.NoData, source().broadcasts(fixture()))
        assertEquals(1, server.requestCount)
        server.enqueue(json(events(event(id = "1"), event(id = "2"))))
        assertEquals(SportsSourceResult.NoData, source().broadcasts(fixture()))
        assertEquals(2, server.requestCount)
    }

    @Test fun missingCredentialMalformedUnauthorizedRateLimitAndNetworkMapTypedErrors() = runTest {
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source(key = null).broadcasts(fixture()))
        server.enqueue(json("not-json"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.ParseFailure), source().broadcasts(fixture()))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source().broadcasts(fixture()))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.RateLimited(now.plusSeconds(30))), source().broadcasts(fixture()))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.NetworkFailure), source(timeoutMs = 100).broadcasts(fixture()))
    }

    @Test fun parsesUtcTimestampAndDocumentedFallbackFormat() {
        assertEquals(Instant.parse("2026-10-04T14:00:00Z"), parseTheSportsDbInstant("2026-10-04T14:00:00Z", null, null))
        assertEquals(Instant.parse("2026-10-04T14:00:00Z"), parseTheSportsDbInstant("2026-10-04T14:00:00", null, null))
        assertEquals(Instant.parse("2026-10-04T14:00:00Z"), parseTheSportsDbInstant("2026-10-04 14:00:00", null, null))
        assertEquals(Instant.parse("2026-10-04T14:00:00Z"), parseTheSportsDbInstant(null, "2026-10-04", "14:00:00"))
    }

    private fun source(key: String? = "synthetic-key", timeoutMs: Long = 2_000): TheSportsDbBroadcastSource {
        val client = OkHttpClient.Builder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(JSON.asConverterFactory("application/json".toMediaType())).build()
            .create(TheSportsDbBroadcastApi::class.java)
        return TheSportsDbBroadcastSource(api, object : TheSportsDbCredentialStore {
            override suspend fun get() = key
            override suspend fun save(value: String) = Unit
            override suspend fun remove() = Unit
        }, Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun fixture() = SportsFixture(
        SportsSourceIdentity(SportsDataSource.ApiFootball, "100"),
        competition = SportsCompetition(SportsSourceIdentity(SportsDataSource.ApiFootball, "39"), name = "Premier League"),
        homeTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "1"), displayName = "Arsenal"),
        awayTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "2"), displayName = "Chelsea"),
        kickoff = Instant.parse("2026-10-04T14:00:00Z"), state = SportsFixtureState.Scheduled, fetchedAt = now,
    )
    private fun event(id: String = "event-500", league: String = "Premier League", homeAwayFields: Boolean = true): String {
        val teams = if (homeAwayFields) "\"strHomeTeam\":\"Arsenal\",\"strAwayTeam\":\"Chelsea\"," else ""
        return """{"idEvent":"$id",$teams"strEvent":"Arsenal vs Chelsea","strLeague":"$league","strTimestamp":"2026-10-04T14:00:00Z","dateEvent":"2026-10-04","strTime":"14:00:00"}"""
    }
    private fun events(vararg values: String) = """{"events":[${values.joinToString(",")}] }"""
    private fun tv(id: String, channel: String, country: String?) =
        """{"id":"entry-$id","idEvent":"event-500","idChannel":"$id","strChannel":"$channel","strCountry":${country?.let { "\"$it\"" } ?: "null"},"strLogo":"https://example.invalid/$id.png"}"""
    private fun tvEnvelope(vararg values: String) = """{"tvevent":[${values.joinToString(",")}] }"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    companion object { private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false } }
}
