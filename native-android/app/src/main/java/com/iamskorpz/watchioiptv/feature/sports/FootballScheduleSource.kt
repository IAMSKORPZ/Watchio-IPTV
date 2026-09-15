package com.iamskorpz.watchioiptv.feature.sports

import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.Response
import retrofit2.HttpException

interface FootballScheduleSource {
    suspend fun getFixtures(date: LocalDate): Result<List<SportsFixture>>
}

interface FootballDataApi {
    @GET("v4/competitions/PL")
    suspend fun validate(@Header("X-Auth-Token") token: String): Response<Unit>

    @GET("v4/matches")
    suspend fun matches(
        @Header("X-Auth-Token") token: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Query("competitions") competitions: String,
    ): FootballMatchesDto
}

@Serializable data class FootballMatchesDto(val matches: List<FootballMatchDto> = emptyList())
@Serializable data class FootballMatchDto(
    val id: Long,
    val competition: FootballCompetitionDto,
    val utcDate: String,
    val status: String,
    val homeTeam: FootballTeamDto,
    val awayTeam: FootballTeamDto,
    val score: FootballScoreDto? = null,
)
@Serializable data class FootballCompetitionDto(val id: Long, val name: String, val code: String? = null)
@Serializable data class FootballTeamDto(val name: String, val shortName: String? = null)
@Serializable data class FootballScoreDto(@SerialName("fullTime") val fullTime: FootballGoalsDto? = null)
@Serializable data class FootballGoalsDto(val home: Int? = null, val away: Int? = null)

class FootballDataScheduleSource(
    private val api: FootballDataApi,
    private val credentialStore: FootballDataCredentialStore,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val fallbackCooldownMs: Long = 60_000L,
) : FootballScheduleSource {
    private val cache = ConcurrentHashMap<LocalDate, List<SportsFixture>>()
    private val requestMutex = Mutex()
    @Volatile private var retryAvailableAtEpochMs: Long = 0L

    override suspend fun getFixtures(date: LocalDate): Result<List<SportsFixture>> = requestMutex.withLock {
        cache[date]?.let { return@withLock Result.success(it) }
        val apiKey = credentialStore.get()?.trim().takeIf { !it.isNullOrBlank() }
            ?: return@withLock Result.failure(SportsScheduleException.MissingCredential)
        val now = clock.millis()
        if (now < retryAvailableAtEpochMs) {
            return@withLock Result.failure(SportsScheduleException.RateLimited(retryAvailableAtEpochMs))
        }
        try {
            val fixtures = api.matches(apiKey, date.toString(), date.plusDays(1).toString(), SportsCompetitionCatalog.supportedCodes.joinToString(","))
                .matches.map { it.toDomain() }
                .filter { it.kickoffUtc.atZone(zoneId).toLocalDate() == date }
            cache[date] = fixtures
            Result.success(fixtures)
        } catch (error: HttpException) {
            Result.failure(mapHttpError(error, now))
        } catch (_: IOException) {
            Result.failure(SportsScheduleException.NetworkUnavailable)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Result.failure(SportsScheduleException.NetworkUnavailable)
        }
    }

    private fun mapHttpError(error: HttpException, now: Long): SportsScheduleException = when (error.code()) {
        429 -> {
            val retryAt = parseRetryAfter(error.response()?.headers()?.get("Retry-After"), now)
            retryAvailableAtEpochMs = maxOf(retryAvailableAtEpochMs, retryAt)
            SportsScheduleException.RateLimited(retryAvailableAtEpochMs)
        }
        401, 403 -> SportsScheduleException.InvalidCredential
        in 500..599 -> SportsScheduleException.TemporarilyUnavailable
        else -> SportsScheduleException.NetworkUnavailable
    }

    fun invalidateCache() {
        cache.clear()
    }

    private fun parseRetryAfter(value: String?, now: Long): Long {
        val seconds = value?.trim()?.toLongOrNull()
        if (seconds != null) return now + seconds.coerceIn(0L, 600L) * 1_000L
        val date = value?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
        return date?.coerceAtLeast(now) ?: now + fallbackCooldownMs
    }

    private fun FootballMatchDto.toDomain() = SportsFixture(
        id = id.toString(),
        competitionId = competition.code ?: competition.id.toString(),
        competitionName = competition.name,
        kickoffUtc = Instant.parse(utcDate),
        homeTeam = homeTeam.name,
        awayTeam = awayTeam.name,
        status = status.toSportsStatus(),
        homeScore = score?.fullTime?.home,
        awayScore = score?.fullTime?.away,
    )

    companion object {
        fun String.toSportsStatus(): SportsFixtureStatus = when (uppercase()) {
            "IN_PLAY", "PAUSED", "LIVE" -> SportsFixtureStatus.Live
            "FINISHED" -> SportsFixtureStatus.Finished
            "POSTPONED", "SUSPENDED" -> SportsFixtureStatus.Postponed
            "CANCELLED" -> SportsFixtureStatus.Cancelled
            else -> SportsFixtureStatus.Scheduled
        }
    }
}

class UitestFootballScheduleSource : FootballScheduleSource {
    override suspend fun getFixtures(date: LocalDate) = Result.success(
        listOf(
            SportsFixture("uitest-1", "PL", "Premier League", date.atTime(16, 30).atZone(java.time.ZoneId.systemDefault()).toInstant(), "Arsenal", "Chelsea", SportsFixtureStatus.Scheduled),
            SportsFixture("uitest-2", "CL", "Champions League", date.atTime(20, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(), "Manchester City", "Real Madrid", SportsFixtureStatus.Scheduled),
        ),
    )
}
