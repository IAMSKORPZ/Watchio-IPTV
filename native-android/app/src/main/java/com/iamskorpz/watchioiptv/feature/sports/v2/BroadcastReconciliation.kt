package com.iamskorpz.watchioiptv.feature.sports.v2

import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.Locale

data class BroadcastFixtureCandidate(
    val source: SportsDataSource,
    val sourceFixtureId: String,
    val homeTeam: String,
    val awayTeam: String,
    val competition: String?,
    val kickoff: Instant,
)

sealed interface FixtureReconciliationResult {
    data class Match(
        val candidate: BroadcastFixtureCandidate,
        val confidence: BroadcastReconciliationConfidence,
    ) : FixtureReconciliationResult
    data object Ambiguous : FixtureReconciliationResult
    data object NoMatch : FixtureReconciliationResult
}

object BroadcastFixtureReconciler {
    val kickoffTolerance: Duration = Duration.ofMinutes(15)

    fun reconcile(fixture: SportsFixture, candidates: List<BroadcastFixtureCandidate>): FixtureReconciliationResult {
        val ranked = candidates.mapNotNull { candidate ->
            val directTeams = normalizeWords(candidate.homeTeam) == normalizeWords(fixture.homeTeam.displayName) &&
                normalizeWords(candidate.awayTeam) == normalizeWords(fixture.awayTeam.displayName)
            val aliasedTeams = normalizeTeam(candidate.homeTeam) == normalizeTeam(fixture.homeTeam.displayName) &&
                normalizeTeam(candidate.awayTeam) == normalizeTeam(fixture.awayTeam.displayName)
            val competitionMatches = candidate.competition != null &&
                normalizeCompetition(candidate.competition) == normalizeCompetition(fixture.competition.name)
            val difference = Duration.between(candidate.kickoff, fixture.kickoff).abs()
            if (!aliasedTeams || !competitionMatches || difference > kickoffTolerance) null else {
                candidate to if (directTeams && difference.isZero) {
                    BroadcastReconciliationConfidence.Exact
                } else {
                    BroadcastReconciliationConfidence.Strong
                }
            }
        }
        val best = ranked.filter { it.second == BroadcastReconciliationConfidence.Exact }.ifEmpty { ranked }
        return when (best.size) {
            0 -> FixtureReconciliationResult.NoMatch
            1 -> FixtureReconciliationResult.Match(best.single().first, best.single().second)
            else -> FixtureReconciliationResult.Ambiguous
        }
    }
}

internal fun normalizeTeam(value: String): String {
    val key = normalizeWords(value)
    return TEAM_ALIASES[key] ?: key.removeSuffix("footballclub").removeSuffix("fc")
}

internal fun normalizeCompetition(value: String): String {
    val key = normalizeWords(value)
    return COMPETITION_ALIASES[key] ?: key
}

internal fun normalizeBroadcaster(value: String): String = normalizeWords(value)

internal fun normalizeCountryOrRegion(value: String?): String? {
    val clean = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    return when (normalizeWords(clean)) {
        "gb", "uk", "unitedkingdom", "greatbritain" -> "GB"
        "us", "usa", "unitedstates", "unitedstatesofamerica" -> "US"
        "england" -> "GB-ENG"
        "scotland" -> "GB-SCT"
        "wales" -> "GB-WLS"
        "northernireland" -> "GB-NIR"
        else -> if (clean.matches(Regex("[A-Za-z]{2}"))) clean.uppercase(Locale.ROOT) else clean
    }
}

private fun normalizeWords(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace("&", "and")
    .replace(Regex("[^a-z0-9]+"), "")

private val TEAM_ALIASES = mapOf(
    "manutd" to "manchesterunited",
    "manchesterunitedfc" to "manchesterunited",
    "psg" to "parissaintgermain",
    "internazionale" to "intermilan",
    "internazionalemilano" to "intermilan",
    "wolverhamptonwanderers" to "wolves",
    "tottenhamhotspur" to "tottenham",
)

private val COMPETITION_ALIASES = mapOf(
    "englishpremierleague" to "premierleague",
    "uefachampionsleague" to "championsleague",
)
