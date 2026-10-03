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

class SoccersApiBroadcastSourceTest {
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-10-04T10:00:00Z")

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun teardown() { server.shutdown() }

    @Test fun authenticatesPaginatesReconcilesAndMapsMultipleBroadcasters() = runTest {
        server.enqueue(json(envelope(emptyList(), page = 1, pages = 2)))
        server.enqueue(json(envelope(listOf(match())) , pageHeader = false))
        val result = source().broadcasts(fixture()) as SportsSourceResult.Success
        assertEquals(2, result.data.size)
        assertEquals(listOf("Sky Sports Main Event", "NBC"), result.data.map { it.displayName })
        assertEquals(listOf("GB", "US"), result.data.map { it.countryOrRegion })
        assertEquals(BroadcastReconciliationConfidence.Exact, result.data.first().evidence.single().confidence)
        assertEquals("500", result.data.first().evidence.single().sourceFixtureId)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("synthetic-user", first.requestUrl!!.queryParameter("user"))
        assertEquals("synthetic-token", first.requestUrl!!.queryParameter("token"))
        assertEquals("schedule", first.requestUrl!!.queryParameter("t"))
        assertEquals("tvs", first.requestUrl!!.queryParameter("include"))
        assertEquals("2026-10-04", first.requestUrl!!.queryParameter("d"))
        assertEquals("2", second.requestUrl!!.queryParameter("page"))
        assertFalse(SensitiveUrlMasker.mask(first.requestUrl.toString()).contains("synthetic-user"))
        assertFalse(SensitiveUrlMasker.mask(first.requestUrl.toString()).contains("synthetic-token"))
        assertFalse(result.data.toString().contains("synthetic-token"))
    }

    @Test fun missingCountryEmptyBroadcastsAndAmbiguousMatchesReturnSafely() = runTest {
        server.enqueue(json(envelope(listOf(match(tvs = tv("1", "Channel", null))))))
        val missing = (source().broadcasts(fixture()) as SportsSourceResult.Success).data.single()
        assertNull(missing.countryOrRegion)

        server.enqueue(json(envelope(listOf(match(tvs = "")))))
        assertEquals(SportsSourceResult.NoData, source().broadcasts(fixture()))

        server.enqueue(json(envelope(listOf(match(id = "1"), match(id = "2")))))
        assertEquals(SportsSourceResult.NoData, source().broadcasts(fixture()))
    }

    @Test fun missingCredentialsMalformedUnauthorizedUnsupportedRateLimitAndNetworkMapTypedErrors() = runTest {
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source(credentials = null).broadcasts(fixture()))
        server.enqueue(json("not-json"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.ParseFailure), source().broadcasts(fixture()))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unauthorized), source().broadcasts(fixture()))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unsupported), source().broadcasts(fixture()))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.RateLimited(now.plusSeconds(60))), source().broadcasts(fixture()))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.NetworkFailure), source(timeoutMs = 100).broadcasts(fixture()))
    }

    @Test fun apiLevelPlanAndQuotaMessagesMapWithoutInventingData() = runTest {
        server.enqueue(json("{\"data\":[],\"meta\":{\"msg\":\"Endpoint not available for your plan.\"}}"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unsupported), source().broadcasts(fixture()))
        server.enqueue(json("{\"data\":[],\"meta\":{\"msg\":\"Request allowance exhausted\"}}"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.RateLimited(null)), source().broadcasts(fixture()))
    }

    private fun source(credentials: SoccersApiCredentials? = SoccersApiCredentials("synthetic-user", "synthetic-token"), timeoutMs: Long = 2_000): SoccersApiBroadcastSource {
        val client = OkHttpClient.Builder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(JSON.asConverterFactory("application/json".toMediaType())).build()
            .create(SoccersApiBroadcastApi::class.java)
        return SoccersApiBroadcastSource(api, object : SoccersApiCredentialStore {
            override suspend fun get() = credentials
            override suspend fun save(username: String, token: String) = Unit
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

    private fun match(id: String = "500", tvs: String = "${tv("10", "Sky Sports Main Event", "gb")},${tv("11", "NBC", "us")}") =
        """{"id":"$id","time":{"timestamp":"1791122400"},"teams":{"home":{"id":1,"name":"Arsenal"},"away":{"id":2,"name":"Chelsea"}},"league":{"id":39,"name":"Premier League"},"tvs":[$tvs]}"""
    private fun tv(id: String, name: String, country: String?) =
        """{"id":"$id","name":"$name","type":"Tv / Cable / Satellite","img":"https://example.invalid/$id.png","country":${country?.let { "{\"name\":\"Country\",\"cc\":\"$it\"}" } ?: "null"}}"""
    private fun envelope(matches: List<String>, page: Int = 1, pages: Int = 1) =
        """{"data":[${matches.joinToString(",")}],"meta":{"page":$page,"pages":$pages,"requests_left":99,"msg":""}}"""
    private fun json(body: String, pageHeader: Boolean = false) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    companion object { private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false } }
}
