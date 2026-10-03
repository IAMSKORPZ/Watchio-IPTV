package com.iamskorpz.watchioiptv.feature.sports.v2

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class CachedBroadcasts(
    val broadcasts: List<SportsBroadcast>,
    val fetchedAt: Instant,
    val expiresAt: Instant,
)

interface SportsBroadcastCache {
    fun get(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity): CachedBroadcasts?
    fun put(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity, value: CachedBroadcasts)
}

class InMemorySportsBroadcastCache : SportsBroadcastCache {
    private val values = ConcurrentHashMap<String, CachedBroadcasts>()
    override fun get(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity) = values[key(source, fixtureIdentity)]
    override fun put(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity, value: CachedBroadcasts) {
        values[key(source, fixtureIdentity)] = value
    }
    private fun key(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity) = "${source.value}:${fixtureIdentity.stableKey}"
}

object SportsBroadcastCachePolicy {
    private val staleWindow = Duration.ofHours(24)

    fun ttl(fixture: SportsFixture, now: Instant): Duration = when {
        fixture.state == SportsFixtureState.Finished || fixture.state == SportsFixtureState.Cancelled -> Duration.ofHours(24)
        fixture.state == SportsFixtureState.Live || fixture.state == SportsFixtureState.Halftime -> Duration.ofHours(1)
        !fixture.kickoff.isAfter(now.plus(Duration.ofHours(6))) -> Duration.ofMinutes(30)
        !fixture.kickoff.isAfter(now.plus(Duration.ofHours(24))) -> Duration.ofHours(1)
        else -> Duration.ofHours(8)
    }

    fun isFresh(value: CachedBroadcasts, now: Instant) = now.isBefore(value.expiresAt)
    fun mayFallback(value: CachedBroadcasts, now: Instant) = now.isBefore(value.expiresAt.plus(staleWindow))
}

class BroadcastRepository(
    private val primary: BroadcastSource,
    private val secondary: BroadcastSource,
    private val cache: SportsBroadcastCache = InMemorySportsBroadcastCache(),
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun broadcasts(fixture: SportsFixture): SportsSourceResult<List<SportsBroadcast>> {
        val primaryResult = load(primary, fixture)
        val secondaryResult = load(secondary, fixture)
        val successful = listOf(primaryResult, secondaryResult).filterIsInstance<SportsSourceResult.Success<List<SportsBroadcast>>>()
        if (successful.isNotEmpty()) {
            val fetchedAt = successful.maxOf { it.fetchedAt }
            return SportsSourceResult.Success(deduplicateBroadcasts(successful.flatMap { it.data }), fetchedAt)
        }
        if (primaryResult is SportsSourceResult.NoData && secondaryResult is SportsSourceResult.NoData) return SportsSourceResult.NoData
        val failures = listOf(primaryResult, secondaryResult).filterIsInstance<SportsSourceResult.Failure>()
        return failures.firstOrNull { it.error is SportsSourceError.RateLimited }
            ?: failures.firstOrNull()
            ?: SportsSourceResult.NoData
    }

    private suspend fun load(source: BroadcastSource, fixture: SportsFixture): SportsSourceResult<List<SportsBroadcast>> {
        val now = clock.instant()
        val cached = cache.get(source.source, fixture.identity)
        if (cached != null && SportsBroadcastCachePolicy.isFresh(cached, now)) {
            return SportsSourceResult.Success(cached.broadcasts, cached.fetchedAt)
        }
        return when (val remote = source.broadcasts(fixture)) {
            is SportsSourceResult.Success -> {
                cache.put(source.source, fixture.identity, CachedBroadcasts(remote.data, remote.fetchedAt, remote.fetchedAt.plus(SportsBroadcastCachePolicy.ttl(fixture, remote.fetchedAt))))
                remote
            }
            is SportsSourceResult.Failure -> if (cached != null && SportsBroadcastCachePolicy.mayFallback(cached, now)) {
                SportsSourceResult.Success(cached.broadcasts, cached.fetchedAt)
            } else remote
            SportsSourceResult.NoData -> SportsSourceResult.NoData
        }
    }
}

internal fun deduplicateBroadcasts(values: List<SportsBroadcast>): List<SportsBroadcast> = values
    .groupBy { normalizeBroadcaster(it.displayName) to normalizeCountryOrRegion(it.countryOrRegion) }
    .values
    .map { duplicates ->
        val first = duplicates.first()
        first.copy(
            evidence = duplicates.flatMap { it.evidence }.distinctBy { "${it.sourceIdentity.stableKey}:${it.sourceFixtureId}" },
            fetchedAt = duplicates.maxOf { it.fetchedAt },
        )
    }
