package com.iamskorpz.watchioiptv.feature.sports.v2

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SoccersApiBroadcastApi {
    @GET("v2.2/broadcast/")
    suspend fun schedule(
        @Query("user") username: String,
        @Query("token") token: String,
        @Query("t") operation: String = "schedule",
        @Query("d") date: String,
        @Query("include") include: String = "tvs",
        @Query("utc") utc: Int = 0,
        @Query("page") page: Int = 1,
    ): Response<SoccersApiScheduleEnvelope>
}

@Serializable
data class SoccersApiScheduleEnvelope(
    val data: List<SoccersApiMatchDto> = emptyList(),
    val meta: SoccersApiMetaDto = SoccersApiMetaDto(),
)

@Serializable
data class SoccersApiMetaDto(
    val page: Int = 1,
    val pages: Int = 1,
    val msg: String = "",
    @SerialName("requests_left") val requestsLeft: Int? = null,
)

@Serializable
data class SoccersApiMatchDto(
    val id: JsonElement,
    val time: SoccersApiTimeDto,
    val teams: SoccersApiTeamsDto,
    val league: SoccersApiLeagueDto,
    val tvs: List<SoccersApiTvDto> = emptyList(),
)

@Serializable data class SoccersApiTimeDto(val timestamp: JsonElement? = null)
@Serializable data class SoccersApiTeamsDto(val home: SoccersApiTeamDto, val away: SoccersApiTeamDto)
@Serializable data class SoccersApiTeamDto(val id: JsonElement? = null, val name: String)
@Serializable data class SoccersApiLeagueDto(val id: JsonElement? = null, val name: String)
@Serializable data class SoccersApiTvDto(
    val id: JsonElement,
    val name: String,
    val type: String? = null,
    val img: String? = null,
    val country: SoccersApiCountryDto? = null,
)
@Serializable data class SoccersApiCountryDto(val id: JsonElement? = null, val name: String? = null, val cc: String? = null)

class SoccersApiBroadcastSource(
    private val api: SoccersApiBroadcastApi,
    private val credentialStore: SoccersApiCredentialStore,
    private val clock: Clock = Clock.systemUTC(),
) : BroadcastSource {
    override val source = SportsDataSource.SoccersApi
    override val capabilities = setOf(SportsSourceCapability.Broadcasts)

    @Volatile var requestsLeft: Int? = null
        private set

    override suspend fun broadcasts(fixture: SportsFixture): SportsSourceResult<List<SportsBroadcast>> {
        val credentials = credentialStore.get()
            ?: return SportsSourceResult.Failure(SportsSourceError.Unauthorized)
        val fetchedAt = clock.instant()
        return try {
            val matches = mutableListOf<SoccersApiMatchDto>()
            var page = 1
            var pages: Int
            do {
                val response = api.schedule(
                    username = credentials.username,
                    token = credentials.token,
                    date = fixture.kickoff.atZone(ZoneOffset.UTC).toLocalDate().toString(),
                    page = page,
                )
                val failure = response.toSoccersError(fetchedAt)
                if (failure != null) return failure
                val body = response.body() ?: return SportsSourceResult.NoData
                if (body.meta.msg.isNotBlank()) return body.meta.msg.toSoccersError()
                requestsLeft = body.meta.requestsLeft
                matches += body.data
                pages = body.meta.pages.coerceAtLeast(1)
                page++
            } while (page <= pages)

            val candidates = matches.mapNotNull(SoccersApiMatchDto::toCandidate)
            when (val reconciliation = BroadcastFixtureReconciler.reconcile(fixture, candidates)) {
                FixtureReconciliationResult.Ambiguous,
                FixtureReconciliationResult.NoMatch -> SportsSourceResult.NoData
                is FixtureReconciliationResult.Match -> {
                    val match = matches.single { it.id.text() == reconciliation.candidate.sourceFixtureId }
                    val broadcasts = match.tvs.mapNotNull { it.toDomain(match.id.text(), reconciliation.confidence, fetchedAt) }
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

private fun SoccersApiMatchDto.toCandidate(): BroadcastFixtureCandidate? {
    val sourceId = id.text().takeIf(String::isNotBlank) ?: return null
    val kickoff = time.timestamp?.text()?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: return null
    return BroadcastFixtureCandidate(SportsDataSource.SoccersApi, sourceId, teams.home.name, teams.away.name, league.name, kickoff)
}

private fun SoccersApiTvDto.toDomain(
    fixtureId: String,
    confidence: BroadcastReconciliationConfidence,
    fetchedAt: Instant,
): SportsBroadcast? {
    val sourceId = id.text().takeIf(String::isNotBlank) ?: return null
    val display = name.trim().takeIf(String::isNotBlank) ?: return null
    val originalCountry = country?.cc?.takeIf(String::isNotBlank) ?: country?.name
    val identity = SportsSourceIdentity(SportsDataSource.SoccersApi, sourceId)
    return SportsBroadcast(
        identity = identity,
        displayName = display,
        countryOrRegion = normalizeCountryOrRegion(originalCountry),
        logoUrl = img,
        fetchedAt = fetchedAt,
        evidence = listOf(SportsBroadcastEvidence(identity, fixtureId, confidence, originalCountry, fetchedAt)),
    )
}

private fun Response<*>.toSoccersError(now: Instant): SportsSourceResult.Failure? = when (code()) {
    401 -> SportsSourceResult.Failure(SportsSourceError.Unauthorized)
    403 -> SportsSourceResult.Failure(SportsSourceError.Unsupported)
    429 -> SportsSourceResult.Failure(SportsSourceError.RateLimited(parseRetryAt(headers()["Retry-After"], now)))
    in 500..599 -> SportsSourceResult.Failure(SportsSourceError.Unavailable)
    else -> if (isSuccessful) null else SportsSourceResult.Failure(SportsSourceError.NetworkFailure)
}

private fun String.toSoccersError(): SportsSourceResult.Failure {
    val normalized = lowercase()
    return SportsSourceResult.Failure(when {
        "request" in normalized && ("limit" in normalized || "allowance" in normalized) -> SportsSourceError.RateLimited(null)
        "credential" in normalized || "token" in normalized || "unauthorized" in normalized -> SportsSourceError.Unauthorized
        "plan" in normalized || "league" in normalized || "endpoint" in normalized -> SportsSourceError.Unsupported
        else -> SportsSourceError.Unavailable
    })
}

internal fun JsonElement.text(): String = (this as? JsonPrimitive)?.content.orEmpty()
