package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class BroadcastReconciliationTest {
    private val fixture = fixture()

    @Test fun exactFixtureMatchesWithoutAssumingCrossSourceId() {
        val result = BroadcastFixtureReconciler.reconcile(fixture, listOf(candidate())) as FixtureReconciliationResult.Match
        assertEquals(BroadcastReconciliationConfidence.Exact, result.confidence)
        assertEquals("vendor-999", result.candidate.sourceFixtureId)
        assertNotEquals(fixture.identity.sourceId, result.candidate.sourceFixtureId)
    }

    @Test fun aliasesAndSmallKickoffDifferenceAreStrong() {
        val result = BroadcastFixtureReconciler.reconcile(
            fixture(home = "Manchester United", away = "Tottenham Hotspur"),
            listOf(candidate(home = "Man Utd", away = "Tottenham", kickoff = "2026-10-04T14:08:00Z")),
        ) as FixtureReconciliationResult.Match
        assertEquals(BroadcastReconciliationConfidence.Strong, result.confidence)
    }

    @Test fun documentedTeamAliasesNormalizeConservatively() {
        assertEquals(normalizeTeam("Paris Saint-Germain"), normalizeTeam("PSG"))
        assertEquals(normalizeTeam("Internazionale"), normalizeTeam("Inter Milan"))
        assertEquals(normalizeTeam("Wolverhampton Wanderers"), normalizeTeam("Wolves"))
    }

    @Test fun reversedTeamsWrongCompetitionAndWrongDateAreRejected() {
        assertEquals(FixtureReconciliationResult.NoMatch, BroadcastFixtureReconciler.reconcile(fixture, listOf(candidate(home = "Chelsea", away = "Arsenal"))))
        assertEquals(FixtureReconciliationResult.NoMatch, BroadcastFixtureReconciler.reconcile(fixture, listOf(candidate(competition = "Championship"))))
        assertEquals(FixtureReconciliationResult.NoMatch, BroadcastFixtureReconciler.reconcile(fixture, listOf(candidate(kickoff = "2026-10-05T14:00:00Z"))))
    }

    @Test fun duplicateBestCandidatesAreAmbiguous() {
        val result = BroadcastFixtureReconciler.reconcile(fixture, listOf(candidate(id = "1"), candidate(id = "2")))
        assertEquals(FixtureReconciliationResult.Ambiguous, result)
    }

    @Test fun countryNormalizationPreservesHomeNationDistinctionAndUnknowns() {
        assertEquals("GB", normalizeCountryOrRegion("GB"))
        assertEquals("GB", normalizeCountryOrRegion("UK"))
        assertEquals("GB", normalizeCountryOrRegion("United Kingdom"))
        assertEquals("GB-ENG", normalizeCountryOrRegion("en"))
        assertEquals("GB-ENG", normalizeCountryOrRegion("England"))
        assertEquals("US", normalizeCountryOrRegion("US"))
        assertEquals("CA", normalizeCountryOrRegion("ca"))
        assertEquals("Canada", normalizeCountryOrRegion("Canada"))
        assertNull(normalizeCountryOrRegion(null))
    }

    @Test fun deduplicationCombinesEvidenceButKeepsCountriesAndDistinctChannels() {
        val now = Instant.parse("2026-10-04T10:00:00Z")
        fun broadcast(source: SportsDataSource, id: String, name: String, country: String) = SportsBroadcast(
            SportsSourceIdentity(source, id), displayName = name, countryOrRegion = country, fetchedAt = now,
            evidence = listOf(SportsBroadcastEvidence(SportsSourceIdentity(source, id), "fixture-$id", BroadcastReconciliationConfidence.Strong, country, now)),
        )
        val result = deduplicateBroadcasts(listOf(
            broadcast(SportsDataSource.SoccersApi, "1", "Sky Sports Main Event", "GB"),
            broadcast(SportsDataSource.TheSportsDb, "2", "SKY SPORTS MAIN EVENT", "UK"),
            broadcast(SportsDataSource.TheSportsDb, "3", "Sky Sports Main Event", "US"),
            broadcast(SportsDataSource.SoccersApi, "4", "Sky Sports Premier League", "GB"),
        ))
        assertEquals(3, result.size)
        assertEquals(2, result.first { it.countryOrRegion == "GB" && it.displayName.contains("Main Event") }.evidence.size)
    }

    private fun fixture(home: String = "Arsenal", away: String = "Chelsea") = SportsFixture(
        SportsSourceIdentity(SportsDataSource.ApiFootball, "100"),
        competition = SportsCompetition(SportsSourceIdentity(SportsDataSource.ApiFootball, "39"), name = "Premier League"),
        homeTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "1"), displayName = home),
        awayTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.ApiFootball, "2"), displayName = away),
        kickoff = Instant.parse("2026-10-04T14:00:00Z"), state = SportsFixtureState.Scheduled,
        fetchedAt = Instant.parse("2026-10-04T10:00:00Z"),
    )

    private fun candidate(
        id: String = "vendor-999", home: String = "Arsenal", away: String = "Chelsea",
        competition: String = "Premier League", kickoff: String = "2026-10-04T14:00:00Z",
    ) = BroadcastFixtureCandidate(SportsDataSource.SoccersApi, id, home, away, competition, Instant.parse(kickoff))
}
