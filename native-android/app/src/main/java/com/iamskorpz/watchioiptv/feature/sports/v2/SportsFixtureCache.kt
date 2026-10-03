package com.iamskorpz.watchioiptv.feature.sports.v2

import com.iamskorpz.watchioiptv.core.database.SportsCacheDao
import com.iamskorpz.watchioiptv.core.database.SportsFixtureCacheEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CachedSportsFixture(val fixture: SportsFixture, val expiresAt: Instant)

interface SportsFixtureCache {
    suspend fun fixtures(source: SportsDataSource, from: Instant, toExclusive: Instant): List<CachedSportsFixture>
    suspend fun replace(source: SportsDataSource, from: Instant, toExclusive: Instant, fixtures: List<CachedSportsFixture>)
    suspend fun prune(before: Instant): Int
}

class RoomSportsFixtureCache(private val dao: SportsCacheDao) : SportsFixtureCache {
    override suspend fun fixtures(source: SportsDataSource, from: Instant, toExclusive: Instant) =
        dao.fixtures(source.value, from.toEpochMilli(), toExclusive.toEpochMilli()).map { it.toCachedFixture() }

    override suspend fun replace(source: SportsDataSource, from: Instant, toExclusive: Instant, fixtures: List<CachedSportsFixture>) =
        dao.replaceRange(source.value, from.toEpochMilli(), toExclusive.toEpochMilli(), fixtures.map { it.toEntity() })

    override suspend fun prune(before: Instant): Int = dao.deleteExpired(before.toEpochMilli())
}

class CachedFixtureRepository(
    private val source: FixtureSource,
    private val cache: SportsFixtureCache,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun fixtures(from: LocalDate, toInclusive: LocalDate): SportsSourceResult<SportsFixtureSnapshot> {
        val now = clock.instant()
        val fromInstant = from.atStartOfDay(zoneId).toInstant()
        val toExclusive = toInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()
        val cached = cache.fixtures(source.source, fromInstant, toExclusive)
        val cachedFreshness = cached.overallFreshness(now)
        if (cached.isNotEmpty() && cachedFreshness == SportsFreshness.Fresh) {
            return SportsSourceResult.Success(cached.toSnapshot(source.source, now, cachedFreshness), now)
        }

        return when (val remote = source.fixtures(from, toInclusive)) {
            is SportsSourceResult.Success -> {
                val records = remote.data.map { fixture ->
                    CachedSportsFixture(fixture, remote.fetchedAt.plus(SportsCachePolicy.fixtureTtl(fixture, remote.fetchedAt, zoneId)))
                }
                cache.replace(source.source, fromInstant, toExclusive, records)
                SportsSourceResult.Success(records.toSnapshot(source.source, remote.fetchedAt, SportsFreshness.Fresh, false), remote.fetchedAt)
            }
            SportsSourceResult.NoData -> SportsSourceResult.NoData
            is SportsSourceResult.Failure -> if (cached.isNotEmpty()) {
                SportsSourceResult.Success(cached.toSnapshot(source.source, now, cachedFreshness), now)
            } else {
                remote
            }
        }
    }

    private fun List<CachedSportsFixture>.overallFreshness(now: Instant): SportsFreshness =
        maxOfOrNull { SportsCachePolicy.freshness(now, it.fixture.fetchedAt, it.expiresAt).ordinal }
            ?.let(SportsFreshness.entries::get) ?: SportsFreshness.Expired

    private fun List<CachedSportsFixture>.toSnapshot(
        source: SportsDataSource,
        now: Instant,
        freshness: SportsFreshness,
        fromCache: Boolean = true,
    ) = SportsFixtureSnapshot(
        fixtures = map { it.fixture },
        source = source,
        fetchedAt = minOf { it.fixture.fetchedAt },
        expiresAt = minOf { it.expiresAt },
        freshness = freshness,
        fromCache = fromCache,
    )
}

internal fun CachedSportsFixture.toEntity(): SportsFixtureCacheEntity {
    val fixture = fixture
    fun SportsScoreLine?.home() = this?.home
    fun SportsScoreLine?.away() = this?.away
    return SportsFixtureCacheEntity(
        source = fixture.identity.source.value,
        sourceFixtureId = fixture.identity.sourceId,
        canonicalFixtureId = fixture.canonicalId,
        competitionSourceId = fixture.competition.identity.sourceId,
        competitionCanonicalId = fixture.competition.canonicalId,
        competitionName = fixture.competition.name,
        competitionCode = fixture.competition.code,
        competitionCountry = fixture.competition.country,
        competitionLogoUrl = fixture.competition.logoUrl,
        competitionType = fixture.competition.type,
        homeTeamSourceId = fixture.homeTeam.identity.sourceId,
        homeTeamCanonicalId = fixture.homeTeam.canonicalId,
        homeTeamName = fixture.homeTeam.displayName,
        homeTeamShortName = fixture.homeTeam.shortName,
        homeTeamLogoUrl = fixture.homeTeam.logoUrl,
        homeTeamCountry = fixture.homeTeam.country,
        awayTeamSourceId = fixture.awayTeam.identity.sourceId,
        awayTeamCanonicalId = fixture.awayTeam.canonicalId,
        awayTeamName = fixture.awayTeam.displayName,
        awayTeamShortName = fixture.awayTeam.shortName,
        awayTeamLogoUrl = fixture.awayTeam.logoUrl,
        awayTeamCountry = fixture.awayTeam.country,
        kickoffEpochMs = fixture.kickoff.toEpochMilli(),
        venue = fixture.venue,
        state = fixture.state.name,
        rawStatus = fixture.rawStatus,
        minute = fixture.minute,
        currentHomeScore = fixture.score?.current.home(), currentAwayScore = fixture.score?.current.away(),
        halftimeHomeScore = fixture.score?.halftime.home(), halftimeAwayScore = fixture.score?.halftime.away(),
        fulltimeHomeScore = fixture.score?.fulltime.home(), fulltimeAwayScore = fixture.score?.fulltime.away(),
        extraTimeHomeScore = fixture.score?.extraTime.home(), extraTimeAwayScore = fixture.score?.extraTime.away(),
        penaltiesHomeScore = fixture.score?.penalties.home(), penaltiesAwayScore = fixture.score?.penalties.away(),
        fetchedAtEpochMs = fixture.fetchedAt.toEpochMilli(),
        expiresAtEpochMs = expiresAt.toEpochMilli(),
    )
}

internal fun SportsFixtureCacheEntity.toCachedFixture(): CachedSportsFixture {
    val sourceId = SportsDataSource(source)
    fun scoreLine(home: Int?, away: Int?) = if (home == null && away == null) null else SportsScoreLine(home, away)
    val score = SportsScore(
        current = scoreLine(currentHomeScore, currentAwayScore),
        halftime = scoreLine(halftimeHomeScore, halftimeAwayScore),
        fulltime = scoreLine(fulltimeHomeScore, fulltimeAwayScore),
        extraTime = scoreLine(extraTimeHomeScore, extraTimeAwayScore),
        penalties = scoreLine(penaltiesHomeScore, penaltiesAwayScore),
    ).takeIf { it.current != null || it.halftime != null || it.fulltime != null || it.extraTime != null || it.penalties != null }
    return CachedSportsFixture(
        fixture = SportsFixture(
            identity = SportsSourceIdentity(sourceId, sourceFixtureId), canonicalId = canonicalFixtureId,
            competition = SportsCompetition(SportsSourceIdentity(sourceId, competitionSourceId), competitionCanonicalId, competitionName, competitionCode, competitionCountry, competitionLogoUrl, competitionType),
            homeTeam = SportsTeam(SportsSourceIdentity(sourceId, homeTeamSourceId), homeTeamCanonicalId, homeTeamName, homeTeamShortName, homeTeamLogoUrl, homeTeamCountry),
            awayTeam = SportsTeam(SportsSourceIdentity(sourceId, awayTeamSourceId), awayTeamCanonicalId, awayTeamName, awayTeamShortName, awayTeamLogoUrl, awayTeamCountry),
            kickoff = Instant.ofEpochMilli(kickoffEpochMs), venue = venue,
            state = runCatching { SportsFixtureState.valueOf(state) }.getOrDefault(SportsFixtureState.Unknown),
            rawStatus = rawStatus, score = score, minute = minute,
            fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMs),
        ),
        expiresAt = Instant.ofEpochMilli(expiresAtEpochMs),
    )
}
