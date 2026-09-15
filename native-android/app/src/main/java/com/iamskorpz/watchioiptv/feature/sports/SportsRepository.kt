package com.iamskorpz.watchioiptv.feature.sports

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideRepository
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideWindow
import java.time.LocalDate
import java.time.ZoneId

class SportsRepository(
    private val scheduleSource: FootballScheduleSource,
    private val tvGuideRepository: TvGuideRepository,
    private val matcher: SportsChannelMatcher = SportsChannelMatcher(),
) {
    suspend fun schedule(date: LocalDate): Result<SportsDateSchedule> = scheduleSource.getFixtures(date).map { fixtures ->
        SportsDateSchedule(
            date,
            fixtures.groupBy { it.competitionId }.map { (id, rows) ->
                SportsCompetition(id, rows.first().competitionName, SportsCompetitionCatalog.displayOrder(id), rows.sortedBy { it.kickoffUtc })
            }.sortedWith(compareBy<SportsCompetition> { it.displayOrder }.thenBy { it.name }),
        )
    }

    suspend fun candidates(fixture: SportsFixture): Result<List<SportsChannelCandidate>> = runCatching {
        val providerId = tvGuideRepository.selectedProviderId() ?: error("Add a provider first.")
        candidatesForProvider(providerId, fixture)
    }

    suspend fun candidatesForProvider(providerId: ProviderId, fixture: SportsFixture): List<SportsChannelCandidate> {
        val kickoff = fixture.kickoffUtc.toEpochMilli()
        val zone = ZoneId.systemDefault()
        val day = fixture.kickoffUtc.atZone(zone).toLocalDate()
        val guide = tvGuideRepository.guideForProvider(
            providerId,
            WatchioGuideWindow(kickoff - 30L * 60_000L, kickoff + 3L * 60L * 60_000L, day, emptyList()),
            kickoff,
            "all",
        )
        return matcher.match(fixture, guide.channels, guide.programmes)
    }

}
