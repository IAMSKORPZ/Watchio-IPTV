package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import org.junit.Assert.*
import org.junit.Test

class SportsSettingsV3Test {
    @Test fun defaultsPreferConfiguredModernFixtureAndBroadcastSourcesWithFallbacks() {
        val settings = SportsSettingsSnapshot()
        assertEquals(listOf(SportsDataSource.ApiFootball, SportsDataSource.FootballData), settings.enabledSources(SportsCapability.FIXTURES))
        assertEquals(listOf(SportsDataSource.SoccersApi, SportsDataSource.TheSportsDb), settings.enabledSources(SportsCapability.BROADCASTS))
    }

    @Test fun disabledProviderIsExcludedWithoutChangingOtherPriorities() {
        val settings = SportsSettingsSnapshot(providers = SportsSettingsSnapshot().providers.map {
            if (it.source == SportsDataSource.ApiFootball.value) it.copy(enabled = false) else it
        })
        assertEquals(listOf(SportsDataSource.FootballData), settings.enabledSources(SportsCapability.FIXTURES))
        assertEquals(2, settings.enabledSources(SportsCapability.BROADCASTS).size)
    }

    @Test fun disablingEveryFixtureProviderProducesNoHiddenFallbackSelection() {
        val settings = SportsSettingsSnapshot(providers = SportsSettingsSnapshot().providers.map {
            if (SportsCapability.FIXTURES in providerCapabilities(SportsDataSource(it.source))) it.copy(enabled = false) else it
        })
        assertTrue(settings.enabledSources(SportsCapability.FIXTURES).isEmpty())
    }

    @Test fun capabilityChoiceMovesSupportedSourceFirst() {
        val settings = SportsSettingsSnapshot(capabilities = listOf(
            SportsCapabilityPreference(SportsCapability.FIXTURES.name, SportsDataSource.FootballData.value),
        ))
        assertEquals(SportsDataSource.FootballData, settings.enabledSources(SportsCapability.FIXTURES).first())
    }

    @Test fun manualMappingsRemainIsolatedByIptvProvider() {
        val first = ManualChannelMapping("one", "Sky Sports Main Event", "1", "Sky")
        val second = ManualChannelMapping("two", "Sky Sports Main Event", "2", "Sky")
        val settings = SportsSettingsSnapshot(manualMappings = listOf(first, second))
        assertEquals(listOf(first), settings.manualMappings("one"))
        assertEquals(listOf(second), settings.manualMappings("two"))
    }

    @Test fun advertisedCapabilitiesMatchRealAdaptersOnly() {
        assertTrue(SportsCapability.BROADCASTS in providerCapabilities(SportsDataSource.SoccersApi))
        assertFalse(SportsCapability.STANDINGS in providerCapabilities(SportsDataSource.ApiFootball))
        assertTrue(providerCapabilities(SportsDataSource.Sportmonks).isEmpty())
    }


    @Test fun everySupportedProviderHasOfficialCredentialAccessMetadata() {
        val expected = mapOf(
            SportsDataSource.FootballData to "https://www.football-data.org/client/register",
            SportsDataSource.ApiFootball to "https://dashboard.api-football.com/register",
            SportsDataSource.SoccersApi to "https://admin.soccersapi.com/register",
            SportsDataSource.TheSportsDb to "https://www.thesportsdb.com/api.php",
        )
        expected.forEach { (source, url) ->
            val info = sportsProviderInfo(source)
            assertEquals(url, info.registrationUrl)
            assertTrue(info.purpose.isNotBlank())
            assertTrue(info.features.isNotBlank())
            assertTrue(info.accessNote.isNotBlank())
        }
        assertTrue(sportsProviderInfo(SportsDataSource.SoccersApi).accessNote.contains("one API key"))
        assertTrue(sportsProviderInfo(SportsDataSource.TheSportsDb).accessNote.contains("Free API access"))
    }
}
