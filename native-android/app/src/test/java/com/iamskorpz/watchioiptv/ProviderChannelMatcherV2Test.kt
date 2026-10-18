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
import kotlin.system.measureNanoTime

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

    @Test fun conservativeFuzzyAllowsSingleTypoButKeepsDistinctServicesSeparate() {
        assertEquals(1, match(name = "Sky Sports Main Evnt HD").size)
        assertTrue(match(name = "Sky Sports Football HD").isEmpty())
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

    @Test fun manualMappingIsProviderScopedAndExplainsSuggestion() {
        val mapping = ManualChannelMapping("active", "Sky Sports Main Event", "mapped", "Provider Main Event")
        val channels = listOf(channel("mapped", "Provider Main Event", provider = "active"), channel("other", "Provider Main Event", provider = "other"))
        val result = matcher.match(fixture, listOf(broadcast()), channels, emptyMap(), listOf(mapping))
        assertEquals(listOf("mapped"), result.map { it.channel.id })
        assertTrue(result.single().reasons.contains("Saved manual mapping"))
    }

    @Test fun renamedOrRemovedManualChannelDoesNotSelectUnrelatedChannel() {
        val mapping = ManualChannelMapping("active", "Sky Sports Main Event", "removed", "Old channel")
        assertTrue(matcher.match(fixture, listOf(broadcast()), listOf(channel("new", "Unrelated Sports")), emptyMap(), listOf(mapping)).isEmpty())
    }

    @Test fun reusableIndexPrunesLargeCatalogueBeforeAliasMatching() {
        val channels = (0 until 10_000).map { channel("generic-$it", "General Channel $it") } +
            channel("sky", "UK: Sky Sports Main Event FHD")
        lateinit var index: ProviderChannelIndex
        val indexNs = measureNanoTime { index = matcher.buildIndex(channels, emptyMap()) }
        val canonical = requireNotNull(catalogue.find("Sky Sports Main Event"))
        assertEquals(10_001, index.channels.size)
        assertEquals(listOf("sky"), index.candidatesFor(canonical).map { it.channel.channelId })
        lateinit var result: List<MatchChannelCandidate>
        val matchNs = measureNanoTime {
            repeat(100) { result = matcher.match(fixture, listOf(broadcast()), index) }
        }
        assertEquals("sky", result.single().channel.id)
        println("SPORTS_PROVIDER_INDEX channels=10001 buildMs=${indexNs / 1_000_000.0} repeatedMatchMs=${matchNs / 1_000_000.0} repeats=100 candidates=1")
    }

    @Test fun eplCategoryRecognitionHandlesSupportedNamesOnly() {
        listOf("EPL", "Premier League", "ENGLISH-PREMIER LEAGUE", "UK | EPL", "UK | EPL HUB", "EPL Channels", "Premier League Teams")
            .forEach { assertTrue(it, isEplCategory(it)) }
        assertFalse(isEplCategory("Championship"))
        assertFalse(isEplCategory("EPL Highlights"))
        assertFalse(isEplCategory("UK | EPL REPLAY"))
    }

    @Test fun premierLeagueFixtureFindsBothTeamChannelsInEplCategory() {
        val game = fixture(home = "Manchester United", away = "Liverpool")
        val channels = listOf(
            channel("home", "MUFC", category = "epl-id"),
            channel("away", "Liverpool FC", category = "epl-id"),
            channel("other", "Arsenal", category = "epl-id"),
        )
        val index = matcher.buildIndex(channels, emptyMap(), mapOf("epl-id" to "UK EPL"))
        val result = matcher.matchTeamChannels(game, index)
        assertEquals(setOf("home", "away"), result.map { it.channel.id }.toSet())
        assertTrue(result.all { it.evidenceSource == SportsChannelEvidenceSource.TEAM_CHANNEL })
        assertTrue(result.all { it.confidence == MatchChannelConfidence.POSSIBLE })
    }

    @Test fun realProviderEplHubSuffixMatchesBothFixtureTeams() {
        val channels = listOf(
            channel("home", "Newcastle United - EPL Premier League", category = "hub"),
            channel("away", "Aston Villa - EPL Premier League", category = "hub"),
            channel("other", "Arsenal - EPL Premier League", category = "hub"),
        )
        val index = matcher.buildIndex(channels, emptyMap(), mapOf("hub" to "UK | EPL HUB"))
        val result = matcher.matchTeamChannels(fixture(home = "Newcastle United FC", away = "Aston Villa FC"), index)
        assertEquals(setOf("home", "away"), result.map { it.channel.id }.toSet())
        assertTrue(result.all { it.confidence == MatchChannelConfidence.POSSIBLE })
    }

    @Test fun teamSuffixMatchingRejectsUnrelatedContent() {
        val channels = listOf(
            channel("highlights", "Arsenal - EPL Highlights", category = "hub"),
            channel("wrong", "Arsenal - EPL Premier League News", category = "hub"),
            channel("tv", "Arsenal TV - EPL Premier League", category = "hub"),
        )
        val index = matcher.buildIndex(channels, emptyMap(), mapOf("hub" to "UK | EPL HUB"))
        assertTrue(matcher.matchTeamChannels(fixture(), index).isEmpty())
    }

    @Test fun fixtureSpecificChannelRequiresBothTeamsAndFootballContext() {
        val channels = listOf(
            channel("match", "FA Cup | Arsenal vs Chelsea 15/02 16:30", category = "fa"),
            channel("one-team", "Arsenal Match 1", category = "fa"),
            channel("wrong-category", "Arsenal vs Chelsea", category = "general"),
            channel("other-fixture", "Arsenal vs Liverpool", category = "fa"),
        )
        val index = matcher.buildIndex(channels, emptyMap(), mapOf("fa" to "UK | FA CUP GAMES", "general" to "Entertainment"))
        val result = matcher.matchFixtureChannels(fixture(), index)
        assertEquals(listOf("match"), result.map { it.channel.id })
        assertEquals(SportsChannelEvidenceSource.MATCH_SPECIFIC_CHANNEL, result.single().evidenceSource)
        assertEquals(MatchChannelConfidence.POSSIBLE, result.single().confidence)
    }

    @Test fun fixtureSpecificChannelBecomesVerifiedOnlyWithMatchingEpg() {
        val channel = channel("match", "Arsenal vs Chelsea", category = "fa")
        val index = matcher.buildIndex(
            listOf(channel),
            mapOf("match" to listOf(programme("Arsenal v Chelsea"))),
            mapOf("fa" to "UK | FA CUP GAMES"),
        )
        assertEquals(MatchChannelConfidence.VERIFIED, matcher.matchFixtureChannels(fixture(), index).single().confidence)
    }

    @Test fun contextualFootballCategoriesRejectReplayAndOtherSports() {
        listOf("UK | FA CUP GAMES", "UK | UEFA TV", "UK | LA LIGA HUB", "Football Events")
            .forEach { assertTrue(it, isFootballEventCategory(it)) }
        listOf("UK | EPL REPLAY", "UK | SPORTS REPLAY", "UK | RACING", "UK | CRICKET")
            .forEach { assertFalse(it, isFootballEventCategory(it)) }
    }

    @Test fun teamChannelsRequireEplCategoryAndPremierLeagueFixture() {
        val channel = channel("team", "Arsenal", category = "general")
        val wrongCategory = matcher.buildIndex(listOf(channel), emptyMap(), mapOf("general" to "Sports"))
        assertTrue(matcher.matchTeamChannels(fixture(), wrongCategory).isEmpty())
        val epl = matcher.buildIndex(listOf(channel.copy(category = "epl", liveChannel = channel.liveChannel.copy(categoryId = "epl"))), emptyMap(), mapOf("epl" to "EPL"))
        assertTrue(matcher.matchTeamChannels(fixture().copy(competition = fixture().competition.copy(code = "CL", name = "Champions League")), epl).isEmpty())
    }

    @Test fun ambiguousAndNonFirstTeamChannelsAreRejected() {
        val channels = listOf(
            channel("united", "United", category = "epl"),
            channel("city", "City", category = "epl"),
            channel("women", "Manchester United Women", category = "epl"),
            channel("youth", "Liverpool U21", category = "epl"),
            channel("tv", "Arsenal TV", category = "epl"),
        )
        val index = matcher.buildIndex(channels, emptyMap(), mapOf("epl" to "EPL"))
        assertTrue(matcher.matchTeamChannels(fixture(home = "Manchester United", away = "Liverpool"), index).isEmpty())
    }

    @Test fun teamChannelBecomesVerifiedOnlyWithFixtureEpgEvidence() {
        val game = fixture(home = "Manchester United", away = "Liverpool")
        val channels = listOf(channel("home", "Man Utd", category = "epl"))
        val programmes = mapOf("home" to listOf(programme("Manchester United v Liverpool")))
        val index = matcher.buildIndex(channels, programmes, mapOf("epl" to "Premier League Teams"))
        assertEquals(MatchChannelConfidence.VERIFIED, matcher.matchTeamChannels(game, index).single().confidence)
    }

    @Test fun teamMatchingRemainsIndexedForLargeProviderCatalogue() {
        val channels = (0 until 10_000).map { channel("generic-$it", "General Channel $it", category = "general") } +
            channel("arsenal", "Arsenal FC FHD", category = "epl")
        val index = matcher.buildIndex(channels, emptyMap(), mapOf("general" to "General", "epl" to "EPL"))
        lateinit var result: List<MatchChannelCandidate>
        val elapsed = measureNanoTime { repeat(100) { result = matcher.matchTeamChannels(fixture(), index) } }
        assertEquals(listOf("arsenal"), result.map { it.channel.id })
        println("SPORTS_TEAM_MATCH channels=10001 repeatedMatchMs=${elapsed / 1_000_000.0} repeats=100")
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

    private fun channel(id: String, name: String, order: Int = 1, provider: String = "active", category: String = "sports"): WatchioGuideChannel {
        val live = LiveTvChannel(ProviderId(provider), ProviderType.Xtream, id, name, null, category, id, "ts", null, emptyMap(), order, false)
        return WatchioGuideChannel(live.providerId, id, name, null, null, category, false, false, id, live)
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
