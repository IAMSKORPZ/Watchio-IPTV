package com.iamskorpz.watchioiptv.feature.sports.v2

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CachedBroadcasts(
    val broadcasts: List<SportsBroadcast>,
    val fetchedAt: Instant,
    val expiresAt: Instant,
    val negative: Boolean = false,
)

interface SportsBroadcastCache {
    fun get(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity): CachedBroadcasts?
    fun put(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity, value: CachedBroadcasts)
    fun clear() = Unit
    fun cleanup(now: Instant, maximumEntries: Int = 1_000) = Unit
}

class InMemorySportsBroadcastCache : SportsBroadcastCache {
    private val values = ConcurrentHashMap<String, CachedBroadcasts>()
    override fun get(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity) = values[key(source, fixtureIdentity)]
    override fun put(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity, value: CachedBroadcasts) {
        values[key(source, fixtureIdentity)] = value
    }
    override fun clear() = values.clear()
    private fun key(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity) = "${source.value}:${fixtureIdentity.stableKey}"
}

object SportsBroadcastCachePolicy {
    private val staleWindow = Duration.ofHours(24)

    fun ttl(fixture: SportsFixture, now: Instant): Duration = when {
        fixture.state == SportsFixtureState.Finished || fixture.state == SportsFixtureState.Cancelled -> Duration.ofHours(24)
        fixture.state == SportsFixtureState.Live || fixture.state == SportsFixtureState.Halftime -> Duration.ofMinutes(3)
        !fixture.kickoff.isAfter(now.plus(Duration.ofHours(2))) -> Duration.ofMinutes(15)
        else -> Duration.ofHours(6)
    }

    val negativeTtl: Duration = Duration.ofMinutes(30)

    fun isFresh(value: CachedBroadcasts, now: Instant) = now.isBefore(value.expiresAt)
    fun mayFallback(value: CachedBroadcasts, now: Instant) = now.isBefore(value.expiresAt.plus(staleWindow))
}

class BroadcastRepository(
    private val primary: BroadcastSource,
    private val secondary: BroadcastSource,
    private val cache: SportsBroadcastCache = InMemorySportsBroadcastCache(),
    private val clock: Clock = Clock.systemUTC(),
    private val sourceOrder: () -> List<SportsDataSource> = { listOf(primary.source, secondary.source) },
    private val refreshInterval: () -> Duration = { Duration.ofMinutes(30) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val requestLocks = ConcurrentHashMap<String, Mutex>()
    fun invalidateCache() = cache.clear()

    suspend fun broadcasts(fixture: SportsFixture, forceRefresh: Boolean = false): SportsSourceResult<List<SportsBroadcast>> = withContext(ioDispatcher) {
        val sources = sourceOrder().mapNotNull { selected -> listOf(primary, secondary).firstOrNull { it.source == selected } }.distinctBy { it.source }
        if (sources.isEmpty()) return@withContext SportsSourceResult.Failure(SportsSourceError.Unsupported)
        cache.cleanup(clock.instant())
        val results = coroutineScope { sources.map { async { load(it, fixture, forceRefresh) } }.awaitAll() }
        val successful = results.filterIsInstance<SportsSourceResult.Success<List<SportsBroadcast>>>()
        if (successful.isNotEmpty()) {
            val fetchedAt = successful.maxOf { it.fetchedAt }
            return@withContext SportsSourceResult.Success(deduplicateBroadcasts(successful.flatMap { it.data }), fetchedAt)
        }
        if (results.all { it is SportsSourceResult.NoData }) return@withContext SportsSourceResult.NoData
        val failures = results.filterIsInstance<SportsSourceResult.Failure>()
        return@withContext failures.firstOrNull { it.error == SportsSourceError.NoBroadcasterData }
            ?: failures.firstOrNull { it.error is SportsSourceError.RateLimited }
            ?: failures.firstOrNull { it.error == SportsSourceError.Unauthorized }
            ?: failures.firstOrNull { it.error == SportsSourceError.UnsupportedTier }
            ?: failures.firstOrNull()
            ?: SportsSourceResult.NoData
    }

    private suspend fun load(source: BroadcastSource, fixture: SportsFixture, forceRefresh: Boolean): SportsSourceResult<List<SportsBroadcast>> {
        val requestKey = "${source.source.value}:${fixture.identity.stableKey}"
        val newLock = Mutex()
        val lock = requestLocks.putIfAbsent(requestKey, newLock) ?: newLock
        if (lock !== newLock) SportsCacheDiagnostics.duplicatePrevented()
        return lock.withLock {
        val now = clock.instant()
        val cached = cache.get(source.source, fixture.identity)
        if (!forceRefresh && cached != null && SportsBroadcastCachePolicy.isFresh(cached, now)) {
            SportsCacheDiagnostics.cacheHit()
            return@withLock if (cached.negative) SportsSourceResult.Failure(SportsSourceError.NoBroadcasterData)
            else SportsSourceResult.Success(cached.broadcasts, cached.fetchedAt)
        }
        SportsCacheDiagnostics.cacheMiss()
        SportsCacheDiagnostics.apiCall()
        val startedAt = System.nanoTime()
        when (val remote = source.broadcasts(fixture)) {
            is SportsSourceResult.Success -> {
                val policyTtl = SportsBroadcastCachePolicy.ttl(fixture, remote.fetchedAt)
                val configuredTtl = refreshInterval().coerceAtLeast(Duration.ofMinutes(5))
                val ttl = if (configuredTtl < policyTtl) configuredTtl else policyTtl
                cache.put(source.source, fixture.identity, CachedBroadcasts(remote.data, remote.fetchedAt, remote.fetchedAt.plus(ttl)))
                remote
            }
            is SportsSourceResult.Failure -> if (remote.error == SportsSourceError.NoBroadcasterData) {
                cache.put(source.source, fixture.identity, CachedBroadcasts(emptyList(), now, now.plus(SportsBroadcastCachePolicy.negativeTtl), negative = true))
                remote
            } else if (cached != null && !cached.negative && SportsBroadcastCachePolicy.mayFallback(cached, now)) {
                SportsSourceResult.Success(cached.broadcasts, cached.fetchedAt)
            } else remote
            SportsSourceResult.NoData -> {
                cache.put(source.source, fixture.identity, CachedBroadcasts(emptyList(), now, now.plus(SportsBroadcastCachePolicy.negativeTtl), negative = true))
                SportsSourceResult.NoData
            }
        }.also { SportsCacheDiagnostics.broadcasterLookup((System.nanoTime() - startedAt) / 1_000_000L) }
        }.also { requestLocks.remove(requestKey) }
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
