package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SportsV2CacheTest {
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val zone = ZoneId.of("Europe/London")

    @Test fun freshnessBoundariesAreDeterministic() {
        val expiry = now.plusSeconds(60)
        assertEquals(SportsFreshness.Fresh, SportsCachePolicy.freshness(now, now, expiry))
        assertEquals(SportsFreshness.Stale, SportsCachePolicy.freshness(expiry, now, expiry))
        assertEquals(SportsFreshness.Expired, SportsCachePolicy.freshness(expiry.plus(Duration.ofHours(24)), now, expiry))
    }

    @Test fun policiesDistinguishLiveTodayFutureAndCompleted() {
        assertEquals(Duration.ofSeconds(45), SportsCachePolicy.fixtureTtl(fixture(SportsFixtureState.Live), now, zone))
        assertEquals(Duration.ofMinutes(10), SportsCachePolicy.fixtureTtl(fixture(SportsFixtureState.Scheduled), now, zone))
        assertEquals(Duration.ofHours(8), SportsCachePolicy.fixtureTtl(fixture(SportsFixtureState.Scheduled, now.plus(Duration.ofDays(2))), now, zone))
        assertEquals(Duration.ofHours(24), SportsCachePolicy.fixtureTtl(fixture(SportsFixtureState.Finished), now, zone))
    }

    @Test fun persistenceMappingRoundTripsWithoutSecretsOrProviderData() {
        val original = CachedSportsFixture(fixture(SportsFixtureState.Finished), now.plusSeconds(600))
        val restored = original.toEntity().toCachedFixture()
        assertEquals(original, restored)
        val fields = original.toEntity()::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fields.any { it.contains("password") || it.contains("token") || it.contains("provider") || it.contains("streamurl") })
    }

    @Test fun freshCacheAvoidsNetwork() = runTest {
        val cache = FakeCache(mutableListOf(CachedSportsFixture(fixture(SportsFixtureState.Scheduled), now.plusSeconds(60))))
        val source = FakeSource(SportsSourceResult.Failure(SportsSourceError.NetworkFailure))
        val result = repository(source, cache).fixtures(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3))
        assertTrue(result is SportsSourceResult.Success)
        assertEquals(0, source.calls)
        assertTrue((result as SportsSourceResult.Success).data.fromCache)
    }

    @Test fun staleCacheFallsBackWhenNetworkFails() = runTest {
        val cache = FakeCache(mutableListOf(CachedSportsFixture(fixture(SportsFixtureState.Scheduled, fetchedAt = now.minusSeconds(120)), now.minusSeconds(60))))
        val source = FakeSource(SportsSourceResult.Failure(SportsSourceError.NetworkFailure))
        val result = repository(source, cache).fixtures(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3)) as SportsSourceResult.Success
        assertEquals(SportsFreshness.Stale, result.data.freshness)
        assertTrue(result.data.fromCache)
        assertEquals(1, source.calls)
    }

    @Test fun expiredCacheStillProvidesExplicitOfflineFallback() = runTest {
        val cache = FakeCache(mutableListOf(CachedSportsFixture(fixture(SportsFixtureState.Scheduled, fetchedAt = now.minus(Duration.ofDays(2))), now.minus(Duration.ofDays(1)))))
        val result = repository(FakeSource(SportsSourceResult.Failure(SportsSourceError.NetworkFailure)), cache)
            .fixtures(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3)) as SportsSourceResult.Success
        assertEquals(SportsFreshness.Expired, result.data.freshness)
    }

    @Test fun successfulNetworkReplacesCache() = runTest {
        val cache = FakeCache()
        val source = FakeSource(SportsSourceResult.Success(listOf(fixture(SportsFixtureState.Live)), now))
        val result = repository(source, cache).fixtures(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3)) as SportsSourceResult.Success
        assertFalse(result.data.fromCache)
        assertEquals(1, cache.values.size)
        assertEquals(now.plusSeconds(45), cache.values.single().expiresAt)
    }

    @Test fun fixtureLookupUsesFreshCacheWithoutNetwork() = runTest {
        val cached = fixture(SportsFixtureState.Scheduled)
        val cache = FakeCache(mutableListOf(CachedSportsFixture(cached, now.plusSeconds(60))))
        val source = object : FixtureSource {
            override val source = SportsDataSource.FootballData
            override val capabilities = setOf(SportsSourceCapability.FixtureById)
            var calls = 0
            override suspend fun fixtures(from: LocalDate, toInclusive: LocalDate) = SportsSourceResult.NoData
            override suspend fun fixture(sourceFixtureId: String): SportsSourceResult<SportsFixture> { calls++; return SportsSourceResult.NoData }
        }
        val result = CachedFixtureRepository(source, cache, Clock.fixed(now, ZoneOffset.UTC), zone).fixture("42") as SportsSourceResult.Success
        assertEquals(cached, result.data)
        assertEquals(0, source.calls)
    }

    @Test fun liveFetchUsesLiveTtlAndPersistsWithoutReplacingDateRange() = runTest {
        val cache = FakeCache()
        val live = fixture(SportsFixtureState.Live)
        val source = object : FixtureSource {
            override val source = SportsDataSource.FootballData
            override val capabilities = setOf(SportsSourceCapability.LiveFixtures)
            override suspend fun fixtures(from: LocalDate, toInclusive: LocalDate) = SportsSourceResult.NoData
            override suspend fun liveFixtures() = SportsSourceResult.Success(listOf(live), now)
        }
        val result = CachedFixtureRepository(source, cache, Clock.fixed(now, ZoneOffset.UTC), zone).liveFixtures() as SportsSourceResult.Success
        assertEquals(false, result.data.fromCache)
        assertEquals(now.plusSeconds(45), cache.values.single().expiresAt)
    }

    @Test fun utcKickoffPresentsAcrossGmtBstAndDstEdge() {
        val london = ZoneId.of("Europe/London")
        assertEquals(12, Instant.parse("2026-01-15T12:00:00Z").atZone(london).hour)
        assertEquals(13, Instant.parse("2026-07-15T12:00:00Z").atZone(london).hour)
        assertEquals(1, Instant.parse("2026-10-25T00:30:00Z").atZone(london).hour)
        assertEquals(1, Instant.parse("2026-10-25T01:30:00Z").atZone(london).hour)
        assertNotEquals(Instant.parse("2026-10-25T00:30:00Z").atZone(london).offset, Instant.parse("2026-10-25T01:30:00Z").atZone(london).offset)
    }

    private fun repository(source: FakeSource, cache: FakeCache) = CachedFixtureRepository(source, cache, Clock.fixed(now, ZoneOffset.UTC), zone)

    private fun fixture(state: SportsFixtureState, kickoff: Instant = now.plusSeconds(3600), fetchedAt: Instant = now): SportsFixture {
        val source = SportsDataSource.FootballData
        return SportsFixture(
            SportsSourceIdentity(source, "42"), competition = SportsCompetition(SportsSourceIdentity(source, "PL"), name = "Premier League", code = "PL"),
            homeTeam = SportsTeam(SportsSourceIdentity(source, "1"), displayName = "Home"),
            awayTeam = SportsTeam(SportsSourceIdentity(source, "2"), displayName = "Away"), kickoff = kickoff, state = state,
            score = SportsScore(fulltime = SportsScoreLine(2, 1)), fetchedAt = fetchedAt,
        )
    }

    private class FakeSource(private val result: SportsSourceResult<List<SportsFixture>>) : FixtureSource {
        override val source = SportsDataSource.FootballData
        override val capabilities = setOf(SportsSourceCapability.DateRange)
        var calls = 0
        override suspend fun fixtures(from: LocalDate, toInclusive: LocalDate): SportsSourceResult<List<SportsFixture>> { calls++; return result }
    }

    private class FakeCache(val values: MutableList<CachedSportsFixture> = mutableListOf()) : SportsFixtureCache {
        override suspend fun fixtures(source: SportsDataSource, from: Instant, toExclusive: Instant) = values.toList()
        override suspend fun fixture(source: SportsDataSource, sourceFixtureId: String) = values.firstOrNull { it.fixture.identity.source == source && it.fixture.identity.sourceId == sourceFixtureId }
        override suspend fun replace(source: SportsDataSource, from: Instant, toExclusive: Instant, fixtures: List<CachedSportsFixture>) { values.removeAll { it.fixture.identity.source == source && !it.fixture.kickoff.isBefore(from) && it.fixture.kickoff.isBefore(toExclusive) }; values.addAll(fixtures) }
        override suspend fun upsert(fixtures: List<CachedSportsFixture>) { values.removeAll { old -> fixtures.any { it.fixture.identity == old.fixture.identity } }; values.addAll(fixtures) }
        override suspend fun prune(before: Instant) = 0
    }
}
