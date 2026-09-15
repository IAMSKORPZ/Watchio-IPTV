package com.iamskorpz.watchioiptv.feature.sports

import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import java.time.Instant
import java.time.LocalDate

enum class SportsFixtureStatus { Scheduled, Live, Finished, Postponed, Cancelled }

data class SportsFixture(
    val id: String,
    val competitionId: String,
    val competitionName: String,
    val kickoffUtc: Instant,
    val homeTeam: String,
    val awayTeam: String,
    val status: SportsFixtureStatus,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
)

data class SportsCompetition(val id: String, val name: String, val displayOrder: Int, val fixtures: List<SportsFixture>)
data class SportsDateSchedule(val date: LocalDate, val competitions: List<SportsCompetition>)

enum class SportsMatchConfidence { High, Medium, Low }

data class SportsChannelCandidate(
    val channel: LiveTvChannel,
    val score: Int,
    val confidence: SportsMatchConfidence,
    val matchedProgrammeTitle: String? = null,
    val reasons: List<String> = emptyList(),
)

sealed interface SportsLoadState {
    data object Loading : SportsLoadState
    data object SetupRequired : SportsLoadState
    data object CredentialNeedsAttention : SportsLoadState
    data class Ready(val schedule: SportsDateSchedule) : SportsLoadState
    data class Error(
        val message: String,
        val detail: String? = null,
        val retryEnabled: Boolean = true,
        val retryAvailableAtEpochMs: Long? = null,
    ) : SportsLoadState
}

sealed class SportsScheduleException(message: String) : Exception(message) {
    class RateLimited(val retryAvailableAtEpochMs: Long) : SportsScheduleException("Too many fixture requests")
    data object ServiceUnavailable : SportsScheduleException("Sports service unavailable")
    data object MissingCredential : SportsScheduleException("Football Data API key required")
    data object InvalidCredential : SportsScheduleException("Football Data API key needs attention")
    data object TemporarilyUnavailable : SportsScheduleException("Fixtures are temporarily unavailable")
    data object NetworkUnavailable : SportsScheduleException("Unable to load fixtures")
}

object SportsCompetitionCatalog {
    val supportedCodes = listOf("PL", "CL", "ELC", "PD", "BL1", "SA", "FL1")
    fun displayOrder(code: String): Int = supportedCodes.indexOf(code.uppercase()).let { if (it < 0) Int.MAX_VALUE else it }
}
