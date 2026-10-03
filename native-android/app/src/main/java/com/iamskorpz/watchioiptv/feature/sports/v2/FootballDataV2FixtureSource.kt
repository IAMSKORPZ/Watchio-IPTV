package com.iamskorpz.watchioiptv.feature.sports.v2

import com.iamskorpz.watchioiptv.feature.sports.FootballDataApi
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.FootballGoalsDto
import com.iamskorpz.watchioiptv.feature.sports.FootballMatchDto
import com.iamskorpz.watchioiptv.feature.sports.SportsCompetitionCatalog
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class FootballDataV2FixtureSource(
    private val api: FootballDataApi,
    private val credentialStore: FootballDataCredentialStore,
    private val clock: Clock = Clock.systemUTC(),
) : FixtureSource {
    override val source = SportsDataSource.FootballData
    override val capabilities = setOf(SportsSourceCapability.DateRange)

    override suspend fun fixtures(from: LocalDate, toInclusive: LocalDate): SportsSourceResult<List<SportsFixture>> {
        val token = credentialStore.get()?.trim().takeIf { !it.isNullOrBlank() }
            ?: return SportsSourceResult.Failure(SportsSourceError.Unauthorized)
        val fetchedAt = clock.instant()
        return try {
            val fixtures = api.matches(
                token = token,
                dateFrom = from.toString(),
                dateTo = toInclusive.plusDays(1).toString(),
                competitions = SportsCompetitionCatalog.supportedCodes.joinToString(","),
            ).matches.map { it.toV2Fixture(fetchedAt) }
            if (fixtures.isEmpty()) SportsSourceResult.NoData else SportsSourceResult.Success(fixtures, fetchedAt)
        } catch (error: HttpException) {
            SportsSourceResult.Failure(error.toSportsSourceError(fetchedAt))
        } catch (_: IOException) {
            SportsSourceResult.Failure(SportsSourceError.NetworkFailure)
        } catch (error: CancellationException) {
            throw error
        } catch (_: kotlinx.serialization.SerializationException) {
            SportsSourceResult.Failure(SportsSourceError.ParseFailure)
        } catch (_: Throwable) {
            SportsSourceResult.Failure(SportsSourceError.Unavailable)
        }
    }

    private fun HttpException.toSportsSourceError(now: java.time.Instant): SportsSourceError = when (code()) {
        401, 403 -> SportsSourceError.Unauthorized
        429 -> SportsSourceError.RateLimited(parseRetryAfter(response()?.headers()?.get("Retry-After"), now))
        in 500..599 -> SportsSourceError.Unavailable
        else -> SportsSourceError.NetworkFailure
    }

    private fun parseRetryAfter(value: String?, now: java.time.Instant): java.time.Instant? {
        value ?: return null
        value.trim().toLongOrNull()?.let { return now.plusSeconds(it.coerceIn(0, 600)) }
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
    }
}

internal fun FootballMatchDto.toV2Fixture(fetchedAt: java.time.Instant): SportsFixture {
    val source = SportsDataSource.FootballData
    fun FootballGoalsDto?.line() = this?.let { SportsScoreLine(it.home, it.away) }?.takeIf { it.isKnown }
    val status = status.uppercase()
    val scoreModel = SportsScore(
        current = score?.fullTime.line(),
        halftime = score?.halfTime.line(),
        fulltime = score?.fullTime.line(),
        extraTime = score?.extraTime.line(),
        penalties = score?.penalties.line(),
    ).takeIf { it.current != null || it.halftime != null || it.fulltime != null || it.extraTime != null || it.penalties != null }
    return SportsFixture(
        identity = SportsSourceIdentity(source, id.toString()),
        competition = SportsCompetition(
            identity = SportsSourceIdentity(source, competition.id.toString()),
            name = competition.name,
            code = competition.code,
            country = competition.area?.name,
            logoUrl = competition.emblem,
            type = competition.type,
        ),
        homeTeam = SportsTeam(
            identity = SportsSourceIdentity(source, homeTeam.id?.toString() ?: "fixture-$id-home"),
            displayName = homeTeam.name,
            shortName = homeTeam.shortName,
            logoUrl = homeTeam.crest,
        ),
        awayTeam = SportsTeam(
            identity = SportsSourceIdentity(source, awayTeam.id?.toString() ?: "fixture-$id-away"),
            displayName = awayTeam.name,
            shortName = awayTeam.shortName,
            logoUrl = awayTeam.crest,
        ),
        kickoff = java.time.Instant.parse(utcDate),
        state = when (status) {
            "IN_PLAY", "LIVE" -> SportsFixtureState.Live
            "PAUSED" -> SportsFixtureState.Halftime
            "FINISHED" -> SportsFixtureState.Finished
            "POSTPONED" -> SportsFixtureState.Postponed
            "SUSPENDED" -> SportsFixtureState.Suspended
            "CANCELLED" -> SportsFixtureState.Cancelled
            "SCHEDULED", "TIMED" -> SportsFixtureState.Scheduled
            else -> SportsFixtureState.Unknown
        },
        rawStatus = status,
        score = scoreModel,
        fetchedAt = fetchedAt,
    )
}
