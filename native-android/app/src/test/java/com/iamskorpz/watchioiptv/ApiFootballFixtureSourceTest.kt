package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

class ApiFootballFixtureSourceTest {
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun teardown() { server.shutdown() }

    @Test fun dateRangeUsesUtcAndAuthenticationHeader() = runTest {
        server.enqueue(jsonResponse(envelope(fixture())))
        val result = source().fixtures(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4)) as SportsSourceResult.Success
        assertEquals(1, result.data.size)
        val request = server.takeRequest()
        assertEquals("synthetic-key", request.getHeader("x-apisports-key"))
        assertTrue(request.path!!.contains("from=2026-10-03"))
        assertTrue(request.path!!.contains("to=2026-10-04"))
        assertTrue(request.path!!.contains("timezone=UTC"))
        assertFalse(result.data.toString().contains("synthetic-key"))
    }

    @Test fun fixtureByIdAndLiveDeclareAndUseSupportedCapabilities() = runTest {
        server.enqueue(jsonResponse(envelope(fixture())))
        val fixture = source().fixture("100") as SportsSourceResult.Success
        assertEquals("100", fixture.data.identity.sourceId)
        assertTrue(server.takeRequest().path!!.contains("id=100"))

        server.enqueue(jsonResponse(envelope(fixture(status = "1H"))))
        val live = source().liveFixtures() as SportsSourceResult.Success
        assertEquals(SportsFixtureState.Live, live.data.single().state)
        assertTrue(server.takeRequest().path!!.contains("live=all"))
        assertEquals(setOf(SportsSourceCapability.DateRange, SportsSourceCapability.FixtureById, SportsSourceCapability.LiveFixtures), source().capabilities)
    }

    @Test fun missingAndInvalidFixtureIdsReturnTypedFailures() = runTest {
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source(key = null).fixtures(LocalDate.now(), LocalDate.now()))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.ParseFailure), source().fixture("not-a-number"))
        assertEquals(0, server.requestCount)
    }

    @Test fun mapsAllDocumentedStatuses() {
        val expected = mapOf(
            "TBD" to SportsFixtureState.Scheduled, "NS" to SportsFixtureState.Scheduled,
            "1H" to SportsFixtureState.Live, "HT" to SportsFixtureState.Halftime,
            "2H" to SportsFixtureState.Live, "ET" to SportsFixtureState.Live,
            "BT" to SportsFixtureState.Live, "P" to SportsFixtureState.Live,
            "SUSP" to SportsFixtureState.Suspended, "INT" to SportsFixtureState.Suspended,
            "FT" to SportsFixtureState.Finished, "AET" to SportsFixtureState.Finished,
            "PEN" to SportsFixtureState.Finished, "PST" to SportsFixtureState.Postponed,
            "CANC" to SportsFixtureState.Cancelled, "ABD" to SportsFixtureState.Cancelled,
            "AWD" to SportsFixtureState.Finished, "WO" to SportsFixtureState.Finished,
            "LIVE" to SportsFixtureState.Live, "NEW" to SportsFixtureState.Unknown,
        )
        expected.forEach { (status, state) -> assertEquals(status, state, status.toApiFootballFixtureState()) }
    }

    @Test fun mapsFixtureTeamsCompetitionLiveScoreTimeAndVenue() = runTest {
        server.enqueue(jsonResponse(envelope(fixture(status = "2H", elapsed = 67, extra = 2))))
        val mapped = (source().fixtures(LocalDate.now(), LocalDate.now()) as SportsSourceResult.Success).data.single()
        assertEquals(SportsDataSource.ApiFootball, mapped.identity.source)
        assertEquals(Instant.parse("2026-10-03T15:00:00Z"), mapped.kickoff)
        assertEquals("Test Stadium", mapped.venue)
        assertEquals(67, mapped.minute)
        assertEquals("Premier League", mapped.competition.name)
        assertEquals("England", mapped.competition.country)
        assertEquals("https://example.invalid/league.png", mapped.competition.logoUrl)
        assertEquals("Home", mapped.homeTeam.displayName)
        assertEquals("https://example.invalid/home.png", mapped.homeTeam.logoUrl)
        assertEquals(2, mapped.score?.current?.home)
        assertEquals(1, mapped.score?.halftime?.home)
        assertEquals(2, mapped.score?.fulltime?.home)
        assertEquals(3, mapped.score?.extraTime?.home)
        assertEquals(5, mapped.score?.penalties?.home)
        assertTrue(mapped.broadcasts.isEmpty())
    }

    @Test fun mapsAllEventTypesAndUnknownSafely() = runTest {
        val events = listOf(
            event("Goal", "Normal Goal"), event("Goal", "Penalty"), event("Goal", "Own Goal"),
            event("Goal", "Missed Penalty"), event("Card", "Yellow Card"), event("Card", "Red Card"),
            event("subst", "Substitution 1"), event("Var", "Goal cancelled"), event("Something New", "Future detail"),
        ).joinToString(",")
        server.enqueue(jsonResponse(envelope(fixture(events = events))))
        val mapped = (source().fixture("100") as SportsSourceResult.Success).data.events
        assertEquals(
            listOf(
                SportsFixtureEventType.Goal, SportsFixtureEventType.PenaltyGoal, SportsFixtureEventType.OwnGoal,
                SportsFixtureEventType.MissedPenalty, SportsFixtureEventType.YellowCard, SportsFixtureEventType.RedCard,
                SportsFixtureEventType.Substitution, SportsFixtureEventType.Var, SportsFixtureEventType.Other,
            ),
            mapped.map { it.type },
        )
        assertEquals(67, mapped.first().minute)
        assertEquals(2, mapped.first().addedTime)
        assertEquals("Player", mapped.first().player)
        assertEquals("Assist", mapped.first().assist)
    }

    @Test fun missingScoresRemainUnknown() = runTest {
        server.enqueue(jsonResponse(envelope(fixture(scores = false))))
        val mapped = (source().fixture("100") as SportsSourceResult.Success).data
        assertNull(mapped.score)
    }

    @Test fun filtersUnsupportedCompetitionsFromRangesAndLiveButNotIdLookup() = runTest {
        server.enqueue(jsonResponse(envelope(fixture(leagueId = 999))))
        assertEquals(SportsSourceResult.NoData, source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(jsonResponse(envelope(fixture(leagueId = 999))))
        assertTrue(source().fixture("100") is SportsSourceResult.Success)
    }

    @Test fun handlesEmptyMalformedAndApiErrorEnvelopes() = runTest {
        server.enqueue(jsonResponse("{\"errors\":[],\"results\":0,\"response\":[]}"))
        assertEquals(SportsSourceResult.NoData, source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(jsonResponse("not-json"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.ParseFailure), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(jsonResponse("{\"errors\":{\"token\":\"Invalid API key\"},\"results\":0,\"response\":[]}"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(jsonResponse("{\"errors\":{\"requests\":\"Request limit reached\"},\"results\":0,\"response\":[]}").setHeader("Retry-After", "30"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.RateLimited(now.plusSeconds(30))), source().fixtures(LocalDate.now(), LocalDate.now()))
    }

    @Test fun mapsHttpAuthenticationRateLimitServerAndTimeout() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.RateLimited(now.plusSeconds(60))), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unavailable), source().fixtures(LocalDate.now(), LocalDate.now()))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.NetworkFailure), source(timeoutMs = 100).fixtures(LocalDate.now(), LocalDate.now()))
    }

    @Test fun capturesQuotaHeadersWithoutCredential() = runTest {
        server.enqueue(jsonResponse(envelope(fixture()))
            .setHeader("x-ratelimit-requests-remaining", "88")
            .setHeader("x-ratelimit-requests-limit", "100")
            .setHeader("X-RateLimit-Remaining", "9")
            .setHeader("X-RateLimit-Limit", "10"))
        val source = source()
        source.fixtures(LocalDate.now(), LocalDate.now())
        assertEquals(ApiFootballQuota(88, 100, 9, 10), source.lastQuota)
        assertFalse(source.lastQuota.toString().contains("synthetic-key"))
    }

    @Test fun sourceIdentityCannotCollideWithFootballData() = runTest {
        server.enqueue(jsonResponse(envelope(fixture())))
        val identity = (source().fixture("100") as SportsSourceResult.Success).data.identity
        assertEquals("api-football:100", identity.stableKey)
        assertNotEquals("football-data:100", identity.stableKey)
    }

    @Test fun seasonStartYearHandlesSplitSeasonBoundary() {
        assertEquals(2025, ApiFootballCompetitionCatalog.seasonStartYear(LocalDate.of(2026, 1, 1)))
        assertEquals(2026, ApiFootballCompetitionCatalog.seasonStartYear(LocalDate.of(2026, 8, 1)))
        assertEquals(setOf(39L, 2L, 40L, 140L, 78L, 135L, 61L), ApiFootballCompetitionCatalog.supportedIds)
    }

    @Test fun instantPresentationHandlesGmtBstAndDstOverlap() = runTest {
        server.enqueue(jsonResponse(envelope(fixture())))
        val instant = (source().fixture("100") as SportsSourceResult.Success).data.kickoff
        assertEquals(15, instant.atZone(ZoneOffset.UTC).hour)
        assertEquals(16, instant.atZone(ZoneId.of("Europe/London")).hour)
        val first = Instant.parse("2026-10-25T00:30:00Z").atZone(ZoneId.of("Europe/London"))
        val second = Instant.parse("2026-10-25T01:30:00Z").atZone(ZoneId.of("Europe/London"))
        assertNotEquals(first.offset, second.offset)
    }

    private fun source(key: String? = "synthetic-key", timeoutMs: Long = 2_000): ApiFootballFixtureSource {
        val client = OkHttpClient.Builder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(JSON.asConverterFactory("application/json".toMediaType())).build()
            .create(ApiFootballApi::class.java)
        return ApiFootballFixtureSource(api, object : ApiFootballCredentialStore {
            override suspend fun get() = key
            override suspend fun save(value: String) = Unit
            override suspend fun remove() = Unit
        }, Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun jsonResponse(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun envelope(item: String) = "{\"get\":\"fixtures\",\"errors\":[],\"results\":1,\"paging\":{\"current\":1,\"total\":1},\"response\":[$item]}"
    private fun fixture(status: String = "NS", elapsed: Int? = null, extra: Int? = null, leagueId: Int = 39, scores: Boolean = true, events: String = "") = """
        {"fixture":{"id":100,"timezone":"UTC","date":"2026-10-03T15:00:00+00:00","timestamp":1791039600,"venue":{"id":1,"name":"Test Stadium","city":"London"},"status":{"long":"Status","short":"$status","elapsed":${elapsed ?: "null"},"extra":${extra ?: "null"}}},"league":{"id":$leagueId,"name":"Premier League","country":"England","logo":"https://example.invalid/league.png","season":2026,"type":"League"},"teams":{"home":{"id":10,"name":"Home","logo":"https://example.invalid/home.png"},"away":{"id":11,"name":"Away","logo":null}},"goals":${if (scores) "{\"home\":2,\"away\":1}" else "{\"home\":null,\"away\":null}"},"score":${if (scores) "{\"halftime\":{\"home\":1,\"away\":0},\"fulltime\":{\"home\":2,\"away\":1},\"extratime\":{\"home\":3,\"away\":2},\"penalty\":{\"home\":5,\"away\":4}}" else "{\"halftime\":null,\"fulltime\":null,\"extratime\":null,\"penalty\":null}"},"events":[$events]}
    """.trimIndent()
    private fun event(type: String, detail: String) = "{\"time\":{\"elapsed\":67,\"extra\":2},\"team\":{\"id\":10,\"name\":\"Home\"},\"player\":{\"id\":20,\"name\":\"Player\"},\"assist\":{\"id\":21,\"name\":\"Assist\"},\"type\":\"$type\",\"detail\":\"$detail\",\"comments\":null}"

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
