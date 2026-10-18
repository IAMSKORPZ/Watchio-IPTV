package com.iamskorpz.watchioiptv

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.feature.sports.*
import com.iamskorpz.watchioiptv.feature.sports.v2.*
import java.time.Instant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SportsPersistentCacheInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before @After fun clear() {
        context.getSharedPreferences("sports_broadcast_cache_v1", 0).edit().clear().commit()
        context.getSharedPreferences("sports_match_cache_v1", 0).edit().clear().commit()
    }

    @Test fun broadcasterCacheSurvivesNewCacheInstanceWithoutSecretsOrUrls() {
        val identity = SportsSourceIdentity(SportsDataSource.FootballData, "fixture")
        val source = SportsDataSource.SoccersApi
        val now = Instant.parse("2026-10-12T12:00:00Z")
        val broadcast = SportsBroadcast(SportsSourceIdentity(source, "channel"), displayName = "BBC One", countryOrRegion = "GB", fetchedAt = now)
        PersistentSportsBroadcastCache(context).put(source, identity, CachedBroadcasts(listOf(broadcast), now, now.plusSeconds(3600)))
        val restored = PersistentSportsBroadcastCache(context).get(source, identity)
        assertEquals("BBC One", restored?.broadcasts?.single()?.displayName)
        val raw = context.getSharedPreferences("sports_broadcast_cache_v1", 0).all.values.joinToString()
        assertFalse(raw.contains("http://")); assertFalse(raw.contains("https://")); assertFalse(raw.contains("password"))
    }

    @Test fun providerMatchCacheSurvivesNewInstanceAndStaysProviderScoped() {
        PersistentSportsMatchCache(context).put(CachedSportsMatch("provider-one", "fixture", "epg", SportsBroadcastState.NO_BROADCASTER_DATA, emptyList(), 1, 10_000))
        val restarted = PersistentSportsMatchCache(context)
        assertNotNull(restarted.get(ProviderId("provider-one"), "fixture"))
        assertNull(restarted.get(ProviderId("provider-two"), "fixture"))
    }
}
