package com.iamskorpz.watchioiptv.feature.sports.v2

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ApiFootballApi {
    @GET("fixtures")
    suspend fun fixtures(
        @Header("x-apisports-key") apiKey: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("id") fixtureId: Long? = null,
        @Query("live") live: String? = null,
        @Query("league") league: Int? = null,
        @Query("season") season: Int? = null,
        @Query("timezone") timezone: String = "UTC",
    ): Response<ApiFootballEnvelope>
}

@Serializable
data class ApiFootballEnvelope(
    val get: String? = null,
    val errors: JsonElement = JsonArray(emptyList()),
    val results: Int = 0,
    val paging: ApiFootballPagingDto? = null,
    val response: List<ApiFootballFixtureDto> = emptyList(),
)

@Serializable data class ApiFootballPagingDto(val current: Int = 1, val total: Int = 1)
@Serializable data class ApiFootballFixtureDto(
    val fixture: ApiFootballFixtureInfoDto,
    val league: ApiFootballLeagueDto,
    val teams: ApiFootballTeamsDto,
    val goals: ApiFootballGoalsDto? = null,
    val score: ApiFootballScoreDto? = null,
    val events: List<ApiFootballEventDto> = emptyList(),
)
@Serializable data class ApiFootballFixtureInfoDto(
    val id: Long,
    val date: String,
    val timestamp: Long? = null,
    val timezone: String? = null,
    val venue: ApiFootballVenueDto? = null,
    val status: ApiFootballStatusDto,
)
@Serializable data class ApiFootballVenueDto(val id: Long? = null, val name: String? = null, val city: String? = null)
@Serializable data class ApiFootballStatusDto(
    val long: String? = null,
    val short: String,
    val elapsed: Int? = null,
    val extra: Int? = null,
)
@Serializable data class ApiFootballLeagueDto(
    val id: Long,
    val name: String,
    val country: String? = null,
    val logo: String? = null,
    val season: Int? = null,
    val type: String? = null,
)
@Serializable data class ApiFootballTeamsDto(val home: ApiFootballTeamDto, val away: ApiFootballTeamDto)
@Serializable data class ApiFootballTeamDto(
    val id: Long,
    val name: String,
    val logo: String? = null,
)
@Serializable data class ApiFootballGoalsDto(val home: Int? = null, val away: Int? = null)
@Serializable data class ApiFootballScoreDto(
    val halftime: ApiFootballGoalsDto? = null,
    val fulltime: ApiFootballGoalsDto? = null,
    val extratime: ApiFootballGoalsDto? = null,
    val penalty: ApiFootballGoalsDto? = null,
)
@Serializable data class ApiFootballEventDto(
    val time: ApiFootballEventTimeDto? = null,
    val team: ApiFootballEventPartyDto? = null,
    val player: ApiFootballEventPartyDto? = null,
    val assist: ApiFootballEventPartyDto? = null,
    val type: String? = null,
    val detail: String? = null,
    val comments: String? = null,
)
@Serializable data class ApiFootballEventTimeDto(val elapsed: Int? = null, val extra: Int? = null)
@Serializable data class ApiFootballEventPartyDto(val id: Long? = null, val name: String? = null)

data class ApiFootballCompetition(
    val id: Int,
    val name: String,
    val country: String,
)

object ApiFootballCompetitionCatalog {
    // Stable IDs are returned by API-Football's official /leagues catalogue.
    val supported = listOf(
        ApiFootballCompetition(39, "Premier League", "England"),
        ApiFootballCompetition(2, "UEFA Champions League", "World"),
        ApiFootballCompetition(40, "Championship", "England"),
        ApiFootballCompetition(140, "La Liga", "Spain"),
        ApiFootballCompetition(78, "Bundesliga", "Germany"),
        ApiFootballCompetition(135, "Serie A", "Italy"),
        ApiFootballCompetition(61, "Ligue 1", "France"),
    )
    val supportedIds: Set<Long> = supported.mapTo(linkedSetOf()) { it.id.toLong() }

    /** API-Football names a split season by its starting year: 2026-27 is season 2026. */
    fun seasonStartYear(date: LocalDate): Int = if (date.month >= Month.JULY) date.year else date.year - 1
}

data class ApiFootballQuota(
    val requestsRemaining: Long?,
    val requestsLimit: Long?,
    val rateLimitRemaining: Long?,
    val rateLimit: Long?,
)

class ApiFootballFixtureSource(
    private val api: ApiFootballApi,
    private val credentialStore: ApiFootballCredentialStore,
    private val clock: Clock = Clock.systemUTC(),
    private val supportedLeagueIds: Set<Long> = ApiFootballCompetitionCatalog.supportedIds,
) : FixtureSource {
    override val source = SportsDataSource.ApiFootball
    override val capabilities = setOf(
        SportsSourceCapability.DateRange,
        SportsSourceCapability.FixtureById,
        SportsSourceCapability.LiveFixtures,
    )

    @Volatile var lastQuota: ApiFootballQuota? = null
        private set

    override suspend fun fixtures(from: LocalDate, toInclusive: LocalDate): SportsSourceResult<List<SportsFixture>> {
        if (toInclusive.isBefore(from)) return SportsSourceResult.NoData
        return request { key -> api.fixtures(apiKey = key, from = from.toString(), to = toInclusive.toString()) }
            .mapFixtures(filterSupportedCompetitions = true)
    }

    override suspend fun fixture(sourceFixtureId: String): SportsSourceResult<SportsFixture> {
        val id = sourceFixtureId.toLongOrNull()
            ?: return SportsSourceResult.Failure(SportsSourceError.ParseFailure)
        return when (val result = request { key -> api.fixtures(apiKey = key, fixtureId = id) }.mapFixtures(false)) {
            is SportsSourceResult.Success -> result.data.singleOrNull()?.let { SportsSourceResult.Success(it, result.fetchedAt) }
                ?: SportsSourceResult.NoData
            SportsSourceResult.NoData -> SportsSourceResult.NoData
            is SportsSourceResult.Failure -> result
        }
    }

    override suspend fun liveFixtures(): SportsSourceResult<List<SportsFixture>> =
        request { key -> api.fixtures(apiKey = key, live = "all") }.mapFixtures(filterSupportedCompetitions = true)

    private suspend fun request(call: suspend (String) -> Response<ApiFootballEnvelope>): SportsSourceResult<ApiFootballEnvelope> {
        val key = credentialStore.get()?.trim().takeIf { !it.isNullOrBlank() }
            ?: return SportsSourceResult.Failure(SportsSourceError.Unauthorized)
        val fetchedAt = clock.instant()
        return try {
            val response = call(key)
            lastQuota = response.toQuota()
            when {
                response.code() == 401 || response.code() == 403 -> SportsSourceResult.Failure(SportsSourceError.Unauthorized)
                response.code() == 429 -> SportsSourceResult.Failure(SportsSourceError.RateLimited(response.retryAt(fetchedAt)))
                !response.isSuccessful -> SportsSourceResult.Failure(
                    if (response.code() in 500..599) SportsSourceError.Unavailable else SportsSourceError.NetworkFailure,
                )
                response.body() == null -> SportsSourceResult.NoData
                response.body()!!.errors.hasErrors() -> SportsSourceResult.Failure(response.body()!!.errors.toSourceError(response.retryAt(fetchedAt)))
                else -> SportsSourceResult.Success(response.body()!!, fetchedAt)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SocketTimeoutException) {
            SportsSourceResult.Failure(SportsSourceError.NetworkFailure)
        } catch (_: IOException) {
            SportsSourceResult.Failure(SportsSourceError.NetworkFailure)
        } catch (_: kotlinx.serialization.SerializationException) {
            SportsSourceResult.Failure(SportsSourceError.ParseFailure)
        } catch (_: Throwable) {
            SportsSourceResult.Failure(SportsSourceError.ParseFailure)
        }
    }

    private fun SportsSourceResult<ApiFootballEnvelope>.mapFixtures(filterSupportedCompetitions: Boolean): SportsSourceResult<List<SportsFixture>> = when (this) {
        is SportsSourceResult.Success -> runCatching {
            data.response
                .asSequence()
                .filter { !filterSupportedCompetitions || it.league.id in supportedLeagueIds }
                .map { fixture -> fixture.toDomain(fetchedAt) }
                .toList()
        }.fold(
            onSuccess = { if (it.isEmpty()) SportsSourceResult.NoData else SportsSourceResult.Success(it, fetchedAt) },
            onFailure = { SportsSourceResult.Failure(SportsSourceError.ParseFailure) },
        )
        SportsSourceResult.NoData -> SportsSourceResult.NoData
        is SportsSourceResult.Failure -> this
    }
}

internal fun String.toApiFootballFixtureState(): SportsFixtureState = when (uppercase()) {
    "TBD", "NS" -> SportsFixtureState.Scheduled
    "1H", "2H", "ET", "BT", "P", "LIVE" -> SportsFixtureState.Live
    "HT" -> SportsFixtureState.Halftime
    "FT", "AET", "PEN", "AWD", "WO" -> SportsFixtureState.Finished
    "PST" -> SportsFixtureState.Postponed
    "SUSP", "INT" -> SportsFixtureState.Suspended
    "CANC", "ABD" -> SportsFixtureState.Cancelled
    else -> SportsFixtureState.Unknown
}

internal fun ApiFootballFixtureDto.toDomain(fetchedAt: Instant): SportsFixture {
    val source = SportsDataSource.ApiFootball
    fun ApiFootballGoalsDto?.line() = this?.let { SportsScoreLine(it.home, it.away) }?.takeIf { it.isKnown }
    val fixtureId = fixture.id.toString()
    val scoreModel = SportsScore(
        current = goals.line(),
        halftime = score?.halftime.line(),
        fulltime = score?.fulltime.line(),
        extraTime = score?.extratime.line(),
        penalties = score?.penalty.line(),
    ).takeIf { it.current != null || it.halftime != null || it.fulltime != null || it.extraTime != null || it.penalties != null }
    return SportsFixture(
        identity = SportsSourceIdentity(source, fixtureId),
        competition = SportsCompetition(
            identity = SportsSourceIdentity(source, league.id.toString()),
            name = league.name,
            code = league.id.toString(),
            country = league.country,
            logoUrl = league.logo,
            type = league.type,
        ),
        homeTeam = SportsTeam(SportsSourceIdentity(source, teams.home.id.toString()), displayName = teams.home.name, logoUrl = teams.home.logo),
        awayTeam = SportsTeam(SportsSourceIdentity(source, teams.away.id.toString()), displayName = teams.away.name, logoUrl = teams.away.logo),
        kickoff = fixture.timestamp?.let(Instant::ofEpochSecond) ?: Instant.parse(fixture.date),
        venue = fixture.venue?.name,
        state = fixture.status.short.toApiFootballFixtureState(),
        rawStatus = fixture.status.short,
        score = scoreModel,
        minute = fixture.status.elapsed,
        events = events.mapIndexed { eventIndex, event -> event.toDomain(fixtureId, eventIndex) },
        broadcasts = emptyList(),
        fetchedAt = fetchedAt,
    )
}

private fun ApiFootballEventDto.toDomain(fixtureId: String, eventIndex: Int): SportsFixtureEvent {
    val detailValue = detail.orEmpty()
    val typeValue = type.orEmpty()
    val eventType = when {
        typeValue.equals("Goal", true) && detailValue.contains("Missed Penalty", true) -> SportsFixtureEventType.MissedPenalty
        typeValue.equals("Goal", true) && detailValue.contains("Own Goal", true) -> SportsFixtureEventType.OwnGoal
        typeValue.equals("Goal", true) && detailValue.contains("Penalty", true) -> SportsFixtureEventType.PenaltyGoal
        typeValue.equals("Goal", true) -> SportsFixtureEventType.Goal
        typeValue.equals("Card", true) && detailValue.contains("Red", true) -> SportsFixtureEventType.RedCard
        typeValue.equals("Card", true) && detailValue.contains("Yellow", true) -> SportsFixtureEventType.YellowCard
        typeValue.equals("subst", true) -> SportsFixtureEventType.Substitution
        typeValue.equals("Var", true) -> SportsFixtureEventType.Var
        else -> SportsFixtureEventType.Other
    }
    return SportsFixtureEvent(
        sourceIdentity = SportsSourceIdentity(SportsDataSource.ApiFootball, "$fixtureId:$eventIndex"),
        type = eventType,
        minute = time?.elapsed,
        addedTime = time?.extra,
        teamIdentity = team?.id?.let { SportsSourceIdentity(SportsDataSource.ApiFootball, it.toString()) },
        player = player?.name,
        assist = assist?.name,
        detail = listOfNotNull(detail, comments).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
    )
}

private fun JsonElement.hasErrors(): Boolean = when (this) {
    is JsonArray -> isNotEmpty()
    is JsonObject -> isNotEmpty()
    is JsonPrimitive -> content.isNotBlank()
}

private fun JsonElement.toSourceError(retryAt: Instant?): SportsSourceError {
    val value = toString().lowercase()
    return when {
        "rate limit" in value || "request limit" in value || "quota" in value -> SportsSourceError.RateLimited(retryAt)
        "api key" in value || "authentication" in value || "unauthorized" in value -> SportsSourceError.Unauthorized
        else -> SportsSourceError.Unavailable
    }
}

private fun Response<*>.retryAt(now: Instant): Instant? {
    val raw = headers()["Retry-After"] ?: return null
    raw.trim().toLongOrNull()?.let { return now.plusSeconds(it.coerceIn(0, 3600)) }
    return runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
}

private fun Response<*>.toQuota() = ApiFootballQuota(
    requestsRemaining = headers()["x-ratelimit-requests-remaining"]?.toLongOrNull(),
    requestsLimit = headers()["x-ratelimit-requests-limit"]?.toLongOrNull(),
    rateLimitRemaining = headers()["X-RateLimit-Remaining"]?.toLongOrNull(),
    rateLimit = headers()["X-RateLimit-Limit"]?.toLongOrNull(),
)
