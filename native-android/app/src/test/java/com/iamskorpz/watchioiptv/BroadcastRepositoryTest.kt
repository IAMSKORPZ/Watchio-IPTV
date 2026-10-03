package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BroadcastRepositoryTest {
    private val now = Instant.parse("2026-10-04T10:00:00Z")
    private val fixture = SportsFixture(
        SportsSourceIdentity(SportsDataSource.ApiFootball, "100"),
        competition = SportsCompetition(SportsSourceIdentity(SportsDataSource.ApiFootball, "39"), name = "Premier League"),
        homeTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "1"), displayName = "Arsenal"),
        awayTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "2"), displayName = "Chelsea"),
        kickoff = now.plusSeconds(7200), state = SportsFixtureState.Scheduled, fetchedAt = now,
    )

    @Test fun primaryAndSecondaryAgreeAndEvidenceIsCombined() = runTest {
        val primary = source(SportsDataSource.SoccersApi, success(broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports", "GB")))
        val secondary = source(SportsDataSource.TheSportsDb, success(broadcast(SportsDataSource.TheSportsDb, "2", "SKY SPORTS", "UK")))
        val result = BroadcastRepository(primary, secondary, clock = fixedClock()).broadcasts(fixture) as SportsSourceResult.Success
        assertEquals(1, result.data.size)
        assertEquals(2, result.data.single().evidence.size)
    }

    @Test fun primaryNoDataSecondarySuccessAndDisagreementsArePreserved() = runTest {
        val fallback = BroadcastRepository(
            source(SportsDataSource.SoccersApi, SportsSourceResult.NoData),
            source(SportsDataSource.TheSportsDb, success(broadcast(SportsDataSource.TheSportsDb, "2", "BBC One", "GB"))),
            clock = fixedClock(),
        ).broadcasts(fixture) as SportsSourceResult.Success
        assertEquals("BBC One", fallback.data.single().displayName)

        val disagreement = BroadcastRepository(
            source(SportsDataSource.SoccersApi, success(broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports", "GB"))),
            source(SportsDataSource.TheSportsDb, success(broadcast(SportsDataSource.TheSportsDb, "2", "BBC One", "GB"))),
            clock = fixedClock(),
        ).broadcasts(fixture) as SportsSourceResult.Success
        assertEquals(2, disagreement.data.size)
    }

    @Test fun rateLimitWinsWhenNoSourceSucceedsAndBothUnavailableRemainFailure() = runTest {
        val rate = SportsSourceResult.Failure(SportsSourceError.RateLimited(now.plusSeconds(60)))
        val result = BroadcastRepository(source(SportsDataSource.SoccersApi, rate), source(SportsDataSource.TheSportsDb, SportsSourceResult.NoData), clock = fixedClock()).broadcasts(fixture)
        assertEquals(rate, result)
        assertTrue(BroadcastRepository(
            source(SportsDataSource.SoccersApi, SportsSourceResult.Failure(SportsSourceError.Unavailable)),
            source(SportsDataSource.TheSportsDb, SportsSourceResult.Failure(SportsSourceError.NetworkFailure)),
            clock = fixedClock(),
        ).broadcasts(fixture) is SportsSourceResult.Failure)
    }

    @Test fun freshCacheAvoidsCallsAndStaleCacheFallsBackOnFailure() = runTest {
        val cache = InMemorySportsBroadcastCache()
        val primary = MutableSource(SportsDataSource.SoccersApi, success(broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports", "GB")))
        val secondary = MutableSource(SportsDataSource.TheSportsDb, SportsSourceResult.NoData)
        val repository = BroadcastRepository(primary, secondary, cache, fixedClock())
        repository.broadcasts(fixture)
        repository.broadcasts(fixture)
        assertEquals(1, primary.calls)
        primary.result = SportsSourceResult.Failure(SportsSourceError.NetworkFailure)
        val later = BroadcastRepository(primary, secondary, cache, Clock.fixed(now.plusSeconds(1900), ZoneOffset.UTC))
        assertTrue(later.broadcasts(fixture) is SportsSourceResult.Success)
    }

    @Test fun cachePolicyUsesNearLiveCompletedAndFutureTtls() {
        assertEquals(1800, SportsBroadcastCachePolicy.ttl(fixture, now).seconds)
        assertEquals(3600, SportsBroadcastCachePolicy.ttl(fixture.copy(state = SportsFixtureState.Live), now).seconds)
        assertEquals(86400, SportsBroadcastCachePolicy.ttl(fixture.copy(state = SportsFixtureState.Finished), now).seconds)
        assertEquals(28800, SportsBroadcastCachePolicy.ttl(fixture.copy(kickoff = now.plusSeconds(172800)), now).seconds)
    }

    private fun fixedClock() = Clock.fixed(now, ZoneOffset.UTC)
    private fun success(item: SportsBroadcast) = SportsSourceResult.Success(listOf(item), now)
    private fun broadcast(source: SportsDataSource, id: String, name: String, country: String): SportsBroadcast {
        val identity = SportsSourceIdentity(source, id)
        return SportsBroadcast(identity, displayName = name, countryOrRegion = normalizeCountryOrRegion(country), fetchedAt = now,
            evidence = listOf(SportsBroadcastEvidence(identity, "fixture", BroadcastReconciliationConfidence.Strong, country, now)))
    }
    private fun source(source: SportsDataSource, result: SportsSourceResult<List<SportsBroadcast>>) = MutableSource(source, result)

    private class MutableSource(
        override val source: SportsDataSource,
        var result: SportsSourceResult<List<SportsBroadcast>>,
    ) : BroadcastSource {
        override val capabilities = setOf(SportsSourceCapability.Broadcasts)
        var calls = 0
        override suspend fun broadcasts(fixture: SportsFixture): SportsSourceResult<List<SportsBroadcast>> { calls++; return result }
    }
}
