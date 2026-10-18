package com.iamskorpz.watchioiptv.feature.sports.v2

import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideProgramme
import java.time.Duration

enum class SportsChannelEvidenceSource { OFFICIAL_BROADCASTER, PROVIDER_EPG, TEAM_CHANNEL, MATCH_SPECIFIC_CHANNEL }

data class EpgFixtureCandidate(
    val channel: WatchioGuideChannel,
    val programme: WatchioGuideProgramme,
    val score: Int,
    val confidence: MatchChannelConfidence,
    val reasons: List<String>,
)

class IndexedEpgProgramme internal constructor(
    val channel: WatchioGuideChannel,
    val programme: WatchioGuideProgramme,
    val normalizedTitle: String,
    val normalizedDetail: String,
)

class EpgFixtureIndex internal constructor(
    internal val programmes: List<IndexedEpgProgramme>,
)

class EpgFixtureDiscovery {
    fun buildIndex(
        channels: List<WatchioGuideChannel>,
        programmes: Map<String, List<WatchioGuideProgramme>>,
    ): EpgFixtureIndex {
        val provider = channels.firstOrNull()?.providerId ?: return EpgFixtureIndex(emptyList())
        return EpgFixtureIndex(channels.asSequence()
            .filter { it.providerId == provider }
            .flatMap { channel -> programmes[channel.channelId].orEmpty().asSequence().map { programme ->
                IndexedEpgProgramme(
                    channel = channel,
                    programme = programme,
                    normalizedTitle = normalizePhrase(programme.title),
                    normalizedDetail = normalizePhrase(listOfNotNull(programme.title, programme.subtitle, programme.description).joinToString(" ")),
                )
            } }
            .toList())
    }

    fun discover(
        fixture: SportsFixture,
        channels: List<WatchioGuideChannel>,
        programmes: Map<String, List<WatchioGuideProgramme>>,
    ): List<EpgFixtureCandidate> = discover(fixture, buildIndex(channels, programmes))

    fun discover(fixture: SportsFixture, index: EpgFixtureIndex): List<EpgFixtureCandidate> {
        return index.programmes.asSequence()
            .filter { row ->
                row.programme.endUtcMs > fixture.kickoff.minus(Duration.ofMinutes(45)).toEpochMilli() &&
                    row.programme.startUtcMs < fixture.kickoff.plus(Duration.ofHours(3)).toEpochMilli()
            }
            .mapNotNull { candidate(fixture, it) }
            .groupBy { it.channel.channelId }
            .mapNotNull { (_, candidates) ->
                val ordered = candidates.sortedByDescending(EpgFixtureCandidate::score)
                val best = ordered.firstOrNull() ?: return@mapNotNull null
                best.takeUnless { ordered.drop(1).any { other -> other.score == best.score && other.programme.programmeId != best.programme.programmeId } }
            }
            .sortedWith(compareByDescending<EpgFixtureCandidate> { it.score }.thenBy { it.channel.displayName })
            .toList()
    }

    private fun candidate(
        fixture: SportsFixture,
        indexed: IndexedEpgProgramme,
    ): EpgFixtureCandidate? {
        val channel = indexed.channel
        val programme = indexed.programme
        val kickoff = fixture.kickoff.toEpochMilli()
        val title = indexed.normalizedTitle
        val detail = indexed.normalizedDetail
        val homeInTitle = teamAliases(fixture.homeTeam.displayName).any { containsWholePhrase(title, it) }
        val awayInTitle = teamAliases(fixture.awayTeam.displayName).any { containsWholePhrase(title, it) }
        val homeInDetail = teamAliases(fixture.homeTeam.displayName).any { containsWholePhrase(detail, it) }
        val awayInDetail = teamAliases(fixture.awayTeam.displayName).any { containsWholePhrase(detail, it) }
        if (!homeInDetail || !awayInDetail || (!homeInTitle && !awayInTitle)) return null
        val spansKickoff = programme.startUtcMs <= kickoff && programme.endUtcMs > kickoff
        val startsNear = programme.startUtcMs in fixture.kickoff.minus(Duration.ofMinutes(45)).toEpochMilli()..fixture.kickoff.plus(Duration.ofMinutes(15)).toEpochMilli()
        if (!spansKickoff && !startsNear) return null
        val bothInTitle = homeInTitle && awayInTitle
        val score = if (bothInTitle) 100 else 80
        return EpgFixtureCandidate(
            channel = channel,
            programme = programme,
            score = score,
            confidence = if (bothInTitle) MatchChannelConfidence.VERIFIED else MatchChannelConfidence.POSSIBLE,
            reasons = buildList {
                add(if (bothInTitle) "Both teams match EPG title" else "Both teams match EPG title and description")
                add("EPG time matches kickoff")
                add("Provider EPG discovery")
            },
        )
    }
}

private fun containsWholePhrase(text: String, phrase: String): Boolean =
    text == phrase || " $text ".contains(" $phrase ")
