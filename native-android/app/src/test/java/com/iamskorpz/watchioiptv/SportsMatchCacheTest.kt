package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.feature.sports.*
import org.junit.Assert.*
import org.junit.Test

class SportsMatchCacheTest {
    @Test fun providerScopedEntriesNeverLeakAcrossProviders() {
        val cache = InMemorySportsMatchCache()
        cache.put(entry("one", "fixture"))
        assertNotNull(cache.get(ProviderId("one"), "fixture"))
        assertNull(cache.get(ProviderId("two"), "fixture"))
    }

    @Test fun cleanupRemovesExpiredAndBoundsStorage() {
        val cache = InMemorySportsMatchCache()
        cache.put(entry("one", "expired", expires = 1))
        repeat(5) { cache.put(entry("one", "fixture-$it", fetched = it.toLong(), expires = 100_000)) }
        cache.cleanup(nowEpochMs = 90_000, maximumEntries = 2)
        assertNull(cache.get(ProviderId("one"), "expired"))
        assertEquals(2, (0 until 5).count { cache.get(ProviderId("one"), "fixture-$it") != null })
    }

    private fun entry(provider: String, fixture: String, fetched: Long = 10, expires: Long = 100) = CachedSportsMatch(
        provider, fixture, "epg", SportsBroadcastState.NO_BROADCASTER_DATA, emptyList(), fetched, expires,
    )
}
