package com.iamskorpz.watchioiptv.feature.sports.v2

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TheSportsDbBroadcastApi {
    @GET("api/v1/json/{apiKey}/eventsday.php")
    suspend fun events(
        @Path("apiKey") apiKey: String,
        @Query("d") date: String,
        @Query("s") sport: String = "Soccer",
    ): Response<TheSportsDbEventsEnvelope>

    @GET("api/v1/json/{apiKey}/lookuptv.php")
    suspend fun broadcasts(
        @Path("apiKey") apiKey: String,
        @Query("id") eventId: String,
    ): Response<TheSportsDbTvEnvelope>
}

@Serializable data class TheSportsDbEventsEnvelope(val events: List<TheSportsDbEventDto>? = null)
@Serializable data class TheSportsDbTvEnvelope(val tvevent: List<TheSportsDbTvDto>? = null)

@Serializable
data class TheSportsDbEventDto(
    @SerialName("idEvent") val eventId: String,
    @SerialName("strHomeTeam") val homeTeam: String? = null,
    @SerialName("strAwayTeam") val awayTeam: String? = null,
    @SerialName("strEvent") val event: String? = null,
    @SerialName("strLeague") val league: String? = null,
    @SerialName("strTimestamp") val timestamp: String? = null,
    val dateEvent: String? = null,
    @SerialName("strTime") val time: String? = null,
)

@Serializable
data class TheSportsDbTvDto(
    val id: String? = null,
    @SerialName("idEvent") val eventId: String,
    @SerialName("idChannel") val channelId: String? = null,
    @SerialName("strChannel") val channel: String? = null,
    @SerialName("strCountry") val country: String? = null,
    @SerialName("strLogo") val logo: String? = null,
)

class TheSportsDbBroadcastSource(
    private val api: TheSportsDbBroadcastApi,
    private val credentialStore: TheSportsDbCredentialStore,
    private val clock: Clock = Clock.systemUTC(),
) : BroadcastSource {
    override val source = SportsDataSource.TheSportsDb
    override val capabilities = setOf(SportsSourceCapability.Broadcasts)

    override suspend fun broadcasts(fixture: SportsFixture): SportsSourceResult<List<SportsBroadcast>> {
        val key = credentialStore.get()
            ?: return SportsSourceResult.Failure(SportsSourceError.Unauthorized)
        val fetchedAt = clock.instant()
        return try {
            val eventResponse = api.events(key, fixture.kickoff.atZone(ZoneOffset.UTC).toLocalDate().toString())
            eventResponse.toTheSportsDbError(fetchedAt)?.let { return it }
            val events = eventResponse.body()?.events.orEmpty()
            val candidates = events.mapNotNull(TheSportsDbEventDto::toCandidate)
            when (val reconciliation = BroadcastFixtureReconciler.reconcile(fixture, candidates)) {
                FixtureReconciliationResult.Ambiguous,
                FixtureReconciliationResult.NoMatch -> SportsSourceResult.NoData
                is FixtureReconciliationResult.Match -> {
                    val response = api.broadcasts(key, reconciliation.candidate.sourceFixtureId)
                    response.toTheSportsDbError(fetchedAt)?.let { return it }
                    val broadcasts = response.body()?.tvevent.orEmpty().mapNotNull {
                        it.toDomain(reconciliation.confidence, fetchedAt)
                    }
                    if (broadcasts.isEmpty()) SportsSourceResult.NoData else SportsSourceResult.Success(broadcasts, fetchedAt)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            SportsSourceResult.Failure(SportsSourceError.NetworkFailure)
        } catch (_: kotlinx.serialization.SerializationException) {
            SportsSourceResult.Failure(SportsSourceError.ParseFailure)
        } catch (_: Throwable) {
            SportsSourceResult.Failure(SportsSourceError.ParseFailure)
        }
    }
}

private fun TheSportsDbEventDto.toCandidate(): BroadcastFixtureCandidate? {
    val split = event?.split(Regex("\\s+vs\\.?\\s+", RegexOption.IGNORE_CASE), limit = 2).orEmpty()
    val home = homeTeam ?: split.getOrNull(0) ?: return null
    val away = awayTeam ?: split.getOrNull(1) ?: return null
    val kickoff = parseTheSportsDbInstant(timestamp, dateEvent, time) ?: return null
    return BroadcastFixtureCandidate(SportsDataSource.TheSportsDb, eventId, home, away, league, kickoff)
}

private fun TheSportsDbTvDto.toDomain(
    confidence: BroadcastReconciliationConfidence,
    fetchedAt: Instant,
): SportsBroadcast? {
    val name = channel?.trim()?.takeIf(String::isNotBlank) ?: return null
    val sourceId = channelId?.takeIf(String::isNotBlank) ?: id?.takeIf(String::isNotBlank) ?: return null
    val identity = SportsSourceIdentity(SportsDataSource.TheSportsDb, sourceId)
    return SportsBroadcast(
        identity = identity,
        displayName = name,
        countryOrRegion = normalizeCountryOrRegion(country),
        logoUrl = logo,
        fetchedAt = fetchedAt,
        evidence = listOf(SportsBroadcastEvidence(identity, eventId, confidence, country, fetchedAt)),
    )
}

internal fun parseTheSportsDbInstant(timestamp: String?, date: String?, time: String?): Instant? {
    timestamp?.trim()?.takeIf(String::isNotBlank)?.let { raw ->
        runCatching { return Instant.parse(raw) }
        runCatching { return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }
        runCatching { return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(ZoneOffset.UTC) }
    }
    if (date.isNullOrBlank() || time.isNullOrBlank()) return null
    return runCatching {
        LocalDateTime.parse("$date ${time.take(8)}", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(ZoneOffset.UTC)
    }.getOrNull()
}

private fun Response<*>.toTheSportsDbError(now: Instant): SportsSourceResult.Failure? = when (code()) {
    401, 403 -> SportsSourceResult.Failure(SportsSourceError.Unauthorized)
    429 -> SportsSourceResult.Failure(SportsSourceError.RateLimited(parseRetryAt(headers()["Retry-After"], now)))
    in 500..599 -> SportsSourceResult.Failure(SportsSourceError.Unavailable)
    else -> if (isSuccessful) null else SportsSourceResult.Failure(SportsSourceError.NetworkFailure)
}

internal fun parseRetryAt(value: String?, now: Instant): Instant? = value?.trim()?.toLongOrNull()?.let {
    now.plusSeconds(it.coerceIn(0, 3600))
}
