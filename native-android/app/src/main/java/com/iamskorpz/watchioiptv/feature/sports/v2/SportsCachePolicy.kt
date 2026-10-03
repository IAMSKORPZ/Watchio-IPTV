package com.iamskorpz.watchioiptv.feature.sports.v2

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SportsCachePolicy {
    val competitionMetadataTtl: Duration = Duration.ofDays(14)
    val teamMetadataTtl: Duration = Duration.ofDays(14)
    val futureFixturesTtl: Duration = Duration.ofHours(8)
    val todayScheduledFixturesTtl: Duration = Duration.ofMinutes(10)
    val liveFixturesTtl: Duration = Duration.ofSeconds(45)
    val completedFixturesTtl: Duration = Duration.ofHours(24)

    fun fixtureTtl(fixture: SportsFixture, now: Instant, zoneId: ZoneId): Duration = when (fixture.state) {
        SportsFixtureState.Live, SportsFixtureState.Halftime -> liveFixturesTtl
        SportsFixtureState.Finished, SportsFixtureState.Cancelled -> completedFixturesTtl
        else -> if (fixture.kickoff.atZone(zoneId).toLocalDate() == LocalDate.ofInstant(now, zoneId)) {
            todayScheduledFixturesTtl
        } else {
            futureFixturesTtl
        }
    }

    fun freshness(now: Instant, fetchedAt: Instant, expiresAt: Instant): SportsFreshness = when {
        now.isBefore(expiresAt) -> SportsFreshness.Fresh
        now.isBefore(expiresAt.plusSeconds(completedFixturesTtl.seconds)) -> SportsFreshness.Stale
        else -> SportsFreshness.Expired
    }
}
