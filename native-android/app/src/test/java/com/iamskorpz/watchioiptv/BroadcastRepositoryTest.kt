package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import com.iamskorpz.watchioiptv.feature.sports.SportsBroadcastState
import com.iamskorpz.watchioiptv.feature.sports.toBroadcastState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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

    @Test fun noBroadcasterDataIsNotCollapsedIntoFixtureOrNetworkFailure() = runTest {
        val noBroadcaster = SportsSourceResult.Failure(SportsSourceError.NoBroadcasterData)
        val result = BroadcastRepository(
            source(SportsDataSource.SoccersApi, SportsSourceResult.Failure(SportsSourceError.FixtureNotMatched)),
            source(SportsDataSource.TheSportsDb, noBroadcaster),
            clock = fixedClock(),
        ).broadcasts(fixture)
        assertEquals(noBroadcaster, result)
    }

    @Test fun explicitInvalidationDropsOnlyBroadcastCache() = runTest {
        val primary = MutableSource(SportsDataSource.SoccersApi, success(broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports", "GB")))
        val repository = BroadcastRepository(primary, source(SportsDataSource.TheSportsDb, SportsSourceResult.NoData), clock = fixedClock())
        repository.broadcasts(fixture)
        repository.broadcasts(fixture)
        assertEquals(1, primary.calls)
        repository.invalidateCache()
        repository.broadcasts(fixture)
        assertEquals(2, primary.calls)
    }

    @Test fun concurrentIdenticalRequestsAreDeduplicated() = runTest {
        val primary = MutableSource(SportsDataSource.SoccersApi, success(broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports", "GB")), delayMs = 100)
        val repository = BroadcastRepository(primary, source(SportsDataSource.TheSportsDb, SportsSourceResult.NoData), clock = fixedClock())
        listOf(async { repository.broadcasts(fixture) }, async { repository.broadcasts(fixture) }).awaitAll()
        assertEquals(1, primary.calls)
    }

    @Test fun negativeResultIsCachedBrieflyAndForceRefreshBypassesIt() = runTest {
        val primary = MutableSource(SportsDataSource.SoccersApi, SportsSourceResult.Failure(SportsSourceError.NoBroadcasterData))
        val secondary = MutableSource(SportsDataSource.TheSportsDb, SportsSourceResult.NoData)
        val repository = BroadcastRepository(primary, secondary, clock = fixedClock())
        repository.broadcasts(fixture)
        repository.broadcasts(fixture)
        assertEquals(1, primary.calls)
        repository.broadcasts(fixture, forceRefresh = true)
        assertEquals(2, primary.calls)
    }

    @Test fun sourcesLoadConcurrentlyInsteadOfSerially() = runTest {
        val primary = MutableSource(SportsDataSource.SoccersApi, SportsSourceResult.NoData, delayMs = 100)
        val secondary = MutableSource(SportsDataSource.TheSportsDb, SportsSourceResult.NoData, delayMs = 100)
        val started = testScheduler.currentTime
        BroadcastRepository(primary, secondary, clock = fixedClock(), ioDispatcher = StandardTestDispatcher(testScheduler)).broadcasts(fixture)
        assertEquals(100, testScheduler.currentTime - started)
    }

    @Test fun cachePolicyUsesNearLiveCompletedAndFutureTtls() {
        assertEquals(900, SportsBroadcastCachePolicy.ttl(fixture, now).seconds)
        assertEquals(180, SportsBroadcastCachePolicy.ttl(fixture.copy(state = SportsFixtureState.Live), now).seconds)
        assertEquals(86400, SportsBroadcastCachePolicy.ttl(fixture.copy(state = SportsFixtureState.Finished), now).seconds)
        assertEquals(21600, SportsBroadcastCachePolicy.ttl(fixture.copy(kickoff = now.plusSeconds(172800)), now).seconds)
        assertEquals(1800, SportsBroadcastCachePolicy.negativeTtl.seconds)
    }

    @Test fun diagnosticOutcomesStayDistinct() {
        assertEquals(SportsBroadcastState.NO_BROADCASTER_DATA, SportsSourceResult.Failure(SportsSourceError.NoBroadcasterData).toBroadcastState())
        assertEquals(SportsBroadcastState.FIXTURE_NOT_MATCHED, SportsSourceResult.Failure(SportsSourceError.FixtureNotMatched).toBroadcastState())
        assertEquals(SportsBroadcastState.UNSUPPORTED_SUBSCRIPTION, SportsSourceResult.Failure(SportsSourceError.UnsupportedTier).toBroadcastState())
        assertEquals(SportsBroadcastState.AUTHENTICATION_FAILED, SportsSourceResult.Failure(SportsSourceError.Unauthorized).toBroadcastState())
        assertEquals(SportsBroadcastState.NETWORK_FAILURE, SportsSourceResult.Failure(SportsSourceError.NetworkFailure).toBroadcastState())
        assertEquals(SportsBroadcastState.BROADCASTER_FOUND_NO_CHANNEL, success(broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports", "GB")).toBroadcastState())
        assertEquals(SportsBroadcastState.CHANNEL_MATCH_AVAILABLE, success(broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports", "GB")).toBroadcastState(hasChannelMatch = true))
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
        private val delayMs: Long = 0,
    ) : BroadcastSource {
        override val capabilities = setOf(SportsSourceCapability.Broadcasts)
        var calls = 0
        override suspend fun broadcasts(fixture: SportsFixture): SportsSourceResult<List<SportsBroadcast>> { calls++; if (delayMs > 0) delay(delayMs); return result }
    }
}
