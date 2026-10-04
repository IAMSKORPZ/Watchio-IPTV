package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.feature.sports.v2.*
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideProgramme
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ProviderChannelMatcherV2Test {
    private val catalogue = BroadcasterAliasCatalogue.parse(ALIASES)
    private val matcher = ProviderChannelMatcherV2(catalogue)
    private val fixture = fixture()

    @Test fun normalizationRemovesCountryQualityAndOperationalLabels() {
        val value = ProviderChannelNormalizer.normalize("UK | SKY SPORTS MAIN EVENT FHD BACKUP")
        assertEquals("sky sports main event", value.value)
        assertEquals("FHD", value.quality)
        assertTrue(value.isBackup)
    }

    @Test fun aliasesMatchCommonProviderNames() {
        listOf("SKY MAIN EVENT UHD", "SKY SPORTS ME 4K", "UK: SKY SPORTS MAIN EVENT HD").forEach { name ->
            assertEquals(1, match(name = name).size)
        }
    }

    @Test fun numberedTntChannelsRemainDistinct() {
        assertTrue(match(name = "TNT SPORTS 2 HD", broadcaster = "TNT Sports 1").isEmpty())
        assertEquals(1, match(name = "TNT1 FHD", broadcaster = "TNT Sports 1").size)
    }

    @Test fun genericSportsAndWrongBrandNeverBecomeRecommendations() {
        assertTrue(match(name = "UK SPORTS LIVE").isEmpty())
        assertTrue(match(name = "SKY CINEMA PREMIERE").isEmpty())
    }

    @Test fun streamingAndRadioEntriesNeverCreateProviderPlaybackCandidates() {
        assertTrue(match(name = "NOW TV", broadcaster = "NOW").isEmpty())
        assertTrue(match(name = "BBC RADIO 5 LIVE", broadcaster = "BBC Radio 5 Live").isEmpty())
    }

    @Test fun bothTeamsAndKickoffCreateVerifiedCandidate() {
        val result = match(programmes = listOf(programme("Arsenal v Chelsea"))).single()
        assertEquals(MatchChannelConfidence.VERIFIED, result.confidence)
        assertTrue(result.epgEvidence.teamsMatched)
        assertTrue(result.epgEvidence.kickoffMatched)
    }

    @Test fun exactBroadcasterWithoutEpgRemainsPossible() {
        assertEquals(MatchChannelConfidence.POSSIBLE, match().single().confidence)
    }

    @Test fun conflictingFixtureIsRejected() {
        assertTrue(match(programmes = listOf(programme("Arsenal v Liverpool"))).isEmpty())
    }

    @Test fun teamAliasesUsePhraseBoundaries() {
        val united = fixture(home = "Manchester United", away = "Tottenham Hotspur")
        assertEquals(MatchChannelConfidence.VERIFIED, match(fixture = united, programmes = listOf(programme("Man Utd v Spurs"))).single().confidence)
        assertFalse(teamAliases("Inter Milan").any { "international" == it })
    }

    @Test fun ukRegionOutranksInternationalAndBackup() {
        val channels = listOf(channel("normal", "Sky Sports Main Event HD", 2), channel("backup", "Sky Sports Main Event BACKUP", 1))
        val broadcasts = listOf(broadcast("Sky Sports Main Event", "US"), broadcast("Sky Sports Main Event", "GB"))
        val result = matcher.match(fixture, broadcasts, channels, emptyMap())
        assertEquals("normal", result.first().channel.id)
        assertFalse(result.first().isBackup)
    }

    @Test fun activeProviderIsolationExcludesSecondProvider() {
        val channels = listOf(channel("one", "Sky Sports Main Event", provider = "active"), channel("two", "Sky Sports Main Event", provider = "other"))
        assertTrue(matcher.match(fixture, listOf(broadcast()), channels, emptyMap()).all { it.channel.providerId == ProviderId("active") })
    }

    @Test fun multipleOfficialBroadcastersRemainAvailable() {
        val broadcasts = listOf(broadcast("Sky Sports Main Event"), broadcast("TNT Sports 1"))
        val channels = listOf(channel("sky", "Sky Sports Main Event"), channel("tnt", "TNT1"))
        assertEquals(setOf("sky", "tnt"), matcher.match(fixture, broadcasts, channels, emptyMap()).map { it.channel.id }.toSet())
    }

    @Test fun duplicateVariantsAreBoundedButFallbackRetained() {
        val channels = listOf(channel("hd", "Sky Sports Main Event HD"), channel("fhd", "Sky Sports Main Event FHD"), channel("backup", "Sky Sports Main Event BACKUP"))
        val result = matcher.match(fixture, listOf(broadcast()), channels, emptyMap())
        assertEquals(2, result.size)
        assertFalse(result.first().isBackup)
    }

    @Test fun epgTimeMatchingUsesInstantsAcrossGmtBstAndDst() {
        listOf(
            Instant.parse("2026-01-10T15:00:00Z"),
            Instant.parse("2026-07-10T14:00:00Z"),
            Instant.parse("2026-10-25T14:00:00Z"),
        ).forEach { kickoff ->
            val f = fixture(kickoff = kickoff)
            val p = programme("Arsenal v Chelsea", kickoff.toEpochMilli() - 15 * 60_000L, kickoff.toEpochMilli() + 2 * 3_600_000L)
            assertTrue(EpgVerifierV2().verify(f, listOf(p)).kickoffMatched)
        }
    }

    private fun match(
        name: String = "Sky Sports Main Event",
        broadcaster: String = "Sky Sports Main Event",
        programmes: List<WatchioGuideProgramme> = emptyList(),
        fixture: SportsFixture = this.fixture,
    ) = matcher.match(fixture, listOf(broadcast(broadcaster)), listOf(channel("one", name)), mapOf("one" to programmes))

    private fun fixture(home: String = "Arsenal", away: String = "Chelsea", kickoff: Instant = Instant.parse("2026-10-04T14:00:00Z")) = SportsFixture(
        SportsSourceIdentity(SportsDataSource.ApiFootball, "fixture"),
        competition = SportsCompetition(SportsSourceIdentity(SportsDataSource.ApiFootball, "39"), name = "Premier League", code = "PL"),
        homeTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "1"), displayName = home),
        awayTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "2"), displayName = away),
        kickoff = kickoff, state = SportsFixtureState.Scheduled, fetchedAt = kickoff.minusSeconds(60),
    )

    private fun broadcast(name: String = "Sky Sports Main Event", country: String = "GB") = SportsBroadcast(
        SportsSourceIdentity(SportsDataSource.SoccersApi, name), displayName = name, countryOrRegion = country, fetchedAt = fixture.fetchedAt,
    )

    private fun channel(id: String, name: String, order: Int = 1, provider: String = "active"): WatchioGuideChannel {
        val live = LiveTvChannel(ProviderId(provider), ProviderType.Xtream, id, name, null, "sports", id, "ts", null, emptyMap(), order, false)
        return WatchioGuideChannel(live.providerId, id, name, null, null, "sports", false, false, id, live)
    }

    private fun programme(title: String, start: Long = fixture.kickoff.toEpochMilli() - 15 * 60_000L, end: Long = fixture.kickoff.toEpochMilli() + 2 * 3_600_000L) =
        WatchioGuideProgramme("p", "one", "one", title, startUtcMs = start, endUtcMs = end, progress = 0f, isLiveNow = false)

    companion object {
        private const val ALIASES = """{"schemaVersion":1,"broadcasters":[
          {"canonical":"Sky Sports Main Event","serviceType":"LINEAR_TV","aliases":["sky main event","sky sports me"]},
          {"canonical":"TNT Sports 1","serviceType":"LINEAR_TV","aliases":["tnt1","tnt sport 1"]},
          {"canonical":"NOW","serviceType":"STREAMING","aliases":["now tv"]},
          {"canonical":"BBC Radio 5 Live","serviceType":"RADIO","aliases":["radio 5 live"]}
        ]}"""
    }
}
