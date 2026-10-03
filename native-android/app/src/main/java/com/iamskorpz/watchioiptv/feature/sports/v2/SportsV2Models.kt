package com.iamskorpz.watchioiptv.feature.sports.v2

import java.time.Instant

@JvmInline
value class SportsDataSource(val value: String) {
    init { require(value.isNotBlank()) }

    companion object {
        val FootballData = SportsDataSource("football-data")
        val ApiFootball = SportsDataSource("api-football")
        val SoccersApi = SportsDataSource("soccers-api")
        val TheSportsDb = SportsDataSource("the-sports-db")
        val Sportmonks = SportsDataSource("sportmonks")
        val WatchioApi = SportsDataSource("watchio-api")
        val WatchioCache = SportsDataSource("watchio-cache")
        val Unknown = SportsDataSource("unknown")
    }
}

data class SportsSourceIdentity(
    val source: SportsDataSource,
    val sourceId: String,
) {
    init { require(sourceId.isNotBlank()) }
    val stableKey: String get() = "${source.value}:$sourceId"
}

data class SportsTeam(
    val identity: SportsSourceIdentity,
    val canonicalId: String? = null,
    val displayName: String,
    val shortName: String? = null,
    val logoUrl: String? = null,
    val country: String? = null,
)

data class SportsCompetition(
    val identity: SportsSourceIdentity,
    val canonicalId: String? = null,
    val name: String,
    val code: String? = null,
    val country: String? = null,
    val logoUrl: String? = null,
    val type: String? = null,
)

enum class SportsFixtureState {
    Scheduled,
    Live,
    Halftime,
    Finished,
    Postponed,
    Suspended,
    Cancelled,
    Unknown,
}

data class SportsScoreLine(val home: Int?, val away: Int?) {
    val isKnown: Boolean get() = home != null || away != null
}

data class SportsScore(
    val current: SportsScoreLine? = null,
    val halftime: SportsScoreLine? = null,
    val fulltime: SportsScoreLine? = null,
    val extraTime: SportsScoreLine? = null,
    val penalties: SportsScoreLine? = null,
)

enum class SportsFixtureEventType {
    Goal,
    OwnGoal,
    PenaltyGoal,
    MissedPenalty,
    YellowCard,
    RedCard,
    Substitution,
    Var,
    Other,
}

data class SportsFixtureEvent(
    val sourceIdentity: SportsSourceIdentity,
    val type: SportsFixtureEventType,
    val minute: Int? = null,
    val addedTime: Int? = null,
    val teamIdentity: SportsSourceIdentity? = null,
    val player: String? = null,
    val assist: String? = null,
    val detail: String? = null,
)

data class SportsBroadcast(
    val identity: SportsSourceIdentity,
    val canonicalId: String? = null,
    val displayName: String,
    val countryOrRegion: String? = null,
    val logoUrl: String? = null,
    val fetchedAt: Instant,
    val evidence: List<SportsBroadcastEvidence> = emptyList(),
)

enum class BroadcastReconciliationConfidence { Exact, Strong }

data class SportsBroadcastEvidence(
    val sourceIdentity: SportsSourceIdentity,
    val sourceFixtureId: String,
    val confidence: BroadcastReconciliationConfidence,
    val originalCountryOrRegion: String? = null,
    val fetchedAt: Instant,
)

data class SportsFixture(
    val identity: SportsSourceIdentity,
    val canonicalId: String? = null,
    val competition: SportsCompetition,
    val homeTeam: SportsTeam,
    val awayTeam: SportsTeam,
    val kickoff: Instant,
    val venue: String? = null,
    val state: SportsFixtureState,
    val rawStatus: String? = null,
    val score: SportsScore? = null,
    val minute: Int? = null,
    val events: List<SportsFixtureEvent> = emptyList(),
    val broadcasts: List<SportsBroadcast> = emptyList(),
    val fetchedAt: Instant,
)

enum class SportsFreshness { Fresh, Stale, Expired }

data class SportsFixtureSnapshot(
    val fixtures: List<SportsFixture>,
    val source: SportsDataSource,
    val fetchedAt: Instant,
    val expiresAt: Instant,
    val freshness: SportsFreshness,
    val fromCache: Boolean,
)
