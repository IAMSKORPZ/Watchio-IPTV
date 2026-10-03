package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.FootballAreaDto
import com.iamskorpz.watchioiptv.feature.sports.FootballCompetitionDto
import com.iamskorpz.watchioiptv.feature.sports.FootballGoalsDto
import com.iamskorpz.watchioiptv.feature.sports.FootballMatchDto
import com.iamskorpz.watchioiptv.feature.sports.FootballScoreDto
import com.iamskorpz.watchioiptv.feature.sports.FootballTeamDto
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsDataSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixtureState
import com.iamskorpz.watchioiptv.feature.sports.v2.toV2Fixture
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportsV2DomainTest {
    private val fetchedAt = Instant.parse("2026-10-03T12:00:00Z")

    @Test fun mapsFootballDataMetadataAndUtcKickoff() {
        val fixture = dto().toV2Fixture(fetchedAt)
        assertEquals("football-data:42", fixture.identity.stableKey)
        assertEquals("Premier League", fixture.competition.name)
        assertEquals("PL", fixture.competition.code)
        assertEquals("England", fixture.competition.country)
        assertEquals("Arsenal", fixture.homeTeam.displayName)
        assertEquals("ARS", fixture.homeTeam.shortName)
        assertEquals(Instant.parse("2026-10-03T15:00:00Z"), fixture.kickoff)
    }

    @Test fun mapsNormalizedStatusesWithoutLeakingVendorStrings() {
        val expected = mapOf(
            "TIMED" to SportsFixtureState.Scheduled,
            "IN_PLAY" to SportsFixtureState.Live,
            "PAUSED" to SportsFixtureState.Halftime,
            "FINISHED" to SportsFixtureState.Finished,
            "POSTPONED" to SportsFixtureState.Postponed,
            "SUSPENDED" to SportsFixtureState.Suspended,
            "CANCELLED" to SportsFixtureState.Cancelled,
            "MYSTERY" to SportsFixtureState.Unknown,
        )
        expected.forEach { (raw, state) ->
            val fixture = dto(status = raw).toV2Fixture(fetchedAt)
            assertEquals(state, fixture.state)
            assertEquals(raw, fixture.rawStatus)
        }
    }

    @Test fun missingScoreRemainsUnknown() {
        val fixture = dto(score = null).toV2Fixture(fetchedAt)
        assertNull(fixture.score)
    }

    @Test fun mapsHalftimeFulltimeExtraTimeAndPenalties() {
        val fixture = dto(score = FootballScoreDto(
            halfTime = FootballGoalsDto(1, 0),
            fullTime = FootballGoalsDto(2, 2),
            extraTime = FootballGoalsDto(3, 2),
            penalties = FootballGoalsDto(4, 3),
        )).toV2Fixture(fetchedAt)
        assertEquals(1, fixture.score?.halftime?.home)
        assertEquals(2, fixture.score?.fulltime?.away)
        assertEquals(3, fixture.score?.extraTime?.home)
        assertEquals(3, fixture.score?.penalties?.away)
    }

    @Test fun identitiesFromDifferentSourcesDoNotCollide() {
        val id = dto().toV2Fixture(fetchedAt).identity
        assertEquals(false, id.stableKey == "api-football:42")
        assertEquals(SportsDataSource.FootballData, id.source)
    }

    private fun dto(status: String = "TIMED", score: FootballScoreDto? = FootballScoreDto(fullTime = FootballGoalsDto(2, 1))) =
        FootballMatchDto(
            id = 42,
            competition = FootballCompetitionDto(1, "Premier League", "PL", "https://example.invalid/pl.png", "LEAGUE", FootballAreaDto("England")),
            utcDate = "2026-10-03T15:00:00Z",
            status = status,
            homeTeam = FootballTeamDto(10, "Arsenal", "ARS", "https://example.invalid/ars.png"),
            awayTeam = FootballTeamDto(11, "Chelsea", "CHE", "https://example.invalid/che.png"),
            score = score,
        )
}
