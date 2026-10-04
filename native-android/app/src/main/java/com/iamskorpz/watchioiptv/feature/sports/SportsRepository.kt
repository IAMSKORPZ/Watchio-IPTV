package com.iamskorpz.watchioiptv.feature.sports

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideRepository
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideWindow
import java.time.LocalDate
import java.time.ZoneId
import com.iamskorpz.watchioiptv.feature.sports.v2.ApiFootballCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcastRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcasterAliasCatalogue
import com.iamskorpz.watchioiptv.feature.sports.v2.CachedFixtureRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.MatchChannelConfidence
import com.iamskorpz.watchioiptv.feature.sports.v2.ProviderChannelMatcherV2
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsBroadcast
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsDataSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixtureSnapshot
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixtureState
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceError
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceIdentity
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceResult

class SportsRepository(
    private val scheduleSource: FootballScheduleSource,
    private val tvGuideRepository: TvGuideRepository,
    private val footballV2Repository: CachedFixtureRepository? = null,
    private val apiFootballV2Repository: CachedFixtureRepository? = null,
    private val apiFootballCredentialStore: ApiFootballCredentialStore? = null,
    private val broadcastRepository: BroadcastRepository? = null,
    private val matcherV2: ProviderChannelMatcherV2? = null,
) {
    suspend fun schedule(date: LocalDate): Result<SportsDateSchedule> {
        val apiConfigured = !apiFootballCredentialStore?.get().isNullOrBlank()
        val v2 = if (apiConfigured) apiFootballV2Repository else footballV2Repository
        if (v2 != null) {
            when (val result = v2.fixtures(date, date)) {
                is SportsSourceResult.Success -> return Result.success(result.data.toLegacySchedule(date))
                SportsSourceResult.NoData -> return Result.success(SportsDateSchedule(date, emptyList()))
                is SportsSourceResult.Failure -> if (apiConfigured) {
                    when (val fallback = footballV2Repository?.fixtures(date, date)) {
                        is SportsSourceResult.Success -> return Result.success(fallback.data.toLegacySchedule(date))
                        SportsSourceResult.NoData -> return Result.success(SportsDateSchedule(date, emptyList()))
                        else -> Unit
                    }
                } else if (result.error is SportsSourceError.RateLimited) {
                    val retry = result.error.retryAt?.toEpochMilli() ?: System.currentTimeMillis() + 60_000L
                    return Result.failure(SportsScheduleException.RateLimited(retry))
                }
            }
        }
        return scheduleSource.getFixtures(date).map { fixtures ->
        SportsDateSchedule(
            date,
            fixtures.groupBy { it.competitionId }.map { (id, rows) ->
                SportsCompetition(id, rows.first().competitionName, SportsCompetitionCatalog.displayOrder(id), rows.sortedBy { it.kickoffUtc })
            }.sortedWith(compareBy<SportsCompetition> { it.displayOrder }.thenBy { it.name }),
        )
    }
    }

    suspend fun broadcasts(fixtures: List<SportsFixture>): Pair<Map<String, List<SportsBroadcast>>, Set<String>> {
        val repository = broadcastRepository ?: return emptyMap<String, List<SportsBroadcast>>() to fixtures.map { it.id }.toSet()
        val found = mutableMapOf<String, List<SportsBroadcast>>()
        val unavailable = mutableSetOf<String>()
        fixtures.forEach { fixture ->
            val v2 = fixture.v2Fixture ?: fixture.toV2()
            when (val result = repository.broadcasts(v2)) {
                is SportsSourceResult.Success -> found[fixture.id] = result.data
                else -> unavailable += fixture.id
            }
        }
        return found to unavailable
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
        val v2Matcher = matcherV2
        val broadcasts = broadcastRepository?.broadcasts(fixture.v2Fixture ?: fixture.toV2())
        if (v2Matcher != null && broadcasts is SportsSourceResult.Success) {
            return v2Matcher.match(fixture.v2Fixture ?: fixture.toV2(), broadcasts.data, guide.channels, guide.programmes).map { row ->
                SportsChannelCandidate(
                    channel = row.channel,
                    score = row.score,
                    confidence = when (row.confidence) {
                        MatchChannelConfidence.VERIFIED -> SportsMatchConfidence.High
                        MatchChannelConfidence.STRONG -> SportsMatchConfidence.High
                        MatchChannelConfidence.POSSIBLE -> SportsMatchConfidence.Medium
                    },
                    matchedProgrammeTitle = row.epgEvidence.programmeTitle,
                    reasons = row.reasons,
                    v2Confidence = row.confidence,
                    broadcasterName = row.broadcasterName,
                    broadcasterCountry = row.broadcasterCountry,
                    isBackup = row.isBackup,
                )
            }
        }
        return emptyList()
    }

}

private fun SportsFixtureSnapshot.toLegacySchedule(date: LocalDate): SportsDateSchedule {
    val rows = fixtures.filter { it.kickoff.atZone(ZoneId.systemDefault()).toLocalDate() == date }.map { it.toLegacy() }
    return SportsDateSchedule(date, rows.groupBy { it.competitionId }.map { (id, fixtures) ->
        SportsCompetition(id, fixtures.first().competitionName, SportsCompetitionCatalog.displayOrder(id), fixtures.sortedBy { it.kickoffUtc })
    }.sortedWith(compareBy<SportsCompetition> { it.displayOrder }.thenBy { it.name }))
}

private fun com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixture.toLegacy() = SportsFixture(
    id = identity.stableKey,
    competitionId = competition.code ?: competition.identity.sourceId,
    competitionName = competition.name,
    kickoffUtc = kickoff,
    homeTeam = homeTeam.displayName,
    awayTeam = awayTeam.displayName,
    status = when (state) {
        SportsFixtureState.Live, SportsFixtureState.Halftime -> SportsFixtureStatus.Live
        SportsFixtureState.Finished -> SportsFixtureStatus.Finished
        SportsFixtureState.Postponed, SportsFixtureState.Suspended -> SportsFixtureStatus.Postponed
        SportsFixtureState.Cancelled -> SportsFixtureStatus.Cancelled
        else -> SportsFixtureStatus.Scheduled
    },
    homeScore = score?.current?.home ?: score?.fulltime?.home,
    awayScore = score?.current?.away ?: score?.fulltime?.away,
    homeLogoUrl = homeTeam.logoUrl,
    awayLogoUrl = awayTeam.logoUrl,
    minute = minute,
    v2Fixture = this,
)

private fun SportsFixture.toV2(): com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixture {
    val source = SportsDataSource.FootballData
    return com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixture(
        identity = SportsSourceIdentity(source, id),
        competition = com.iamskorpz.watchioiptv.feature.sports.v2.SportsCompetition(SportsSourceIdentity(source, competitionId), name = competitionName, code = competitionId),
        homeTeam = com.iamskorpz.watchioiptv.feature.sports.v2.SportsTeam(SportsSourceIdentity(source, "$id-home"), displayName = homeTeam, logoUrl = homeLogoUrl),
        awayTeam = com.iamskorpz.watchioiptv.feature.sports.v2.SportsTeam(SportsSourceIdentity(source, "$id-away"), displayName = awayTeam, logoUrl = awayLogoUrl),
        kickoff = kickoffUtc,
        state = when (status) {
            SportsFixtureStatus.Live -> SportsFixtureState.Live
            SportsFixtureStatus.Finished -> SportsFixtureState.Finished
            SportsFixtureStatus.Postponed -> SportsFixtureState.Postponed
            SportsFixtureStatus.Cancelled -> SportsFixtureState.Cancelled
            SportsFixtureStatus.Scheduled -> SportsFixtureState.Scheduled
        },
        minute = minute,
        fetchedAt = java.time.Instant.now(),
    )
}
