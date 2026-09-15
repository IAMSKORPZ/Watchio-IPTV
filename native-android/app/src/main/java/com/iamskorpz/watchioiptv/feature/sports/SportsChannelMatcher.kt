package com.iamskorpz.watchioiptv.feature.sports

import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideProgramme

class SportsChannelMatcher {
    fun match(
        fixture: SportsFixture,
        channels: List<WatchioGuideChannel>,
        programmes: Map<String, List<WatchioGuideProgramme>>,
    ): List<SportsChannelCandidate> = channels.mapNotNull { channel ->
        val sportsChannel = isSports(channel.displayName) || isSports(channel.category.orEmpty())
        val relevant = programmes[channel.channelId].orEmpty().filter { programme ->
            programme.startUtcMs < fixture.kickoffUtc.toEpochMilli() + THREE_HOURS &&
                programme.endUtcMs > fixture.kickoffUtc.toEpochMilli() - THIRTY_MINUTES
        }
        val best = relevant.map { it to programmeScore(fixture, it.title) }.maxByOrNull { it.second }
        val score = (best?.second ?: 0) + if (sportsChannel) 10 else 0
        if (score < 10) return@mapNotNull null
        val confidence = when {
            score >= 100 -> SportsMatchConfidence.High
            score >= 35 -> SportsMatchConfidence.Medium
            else -> SportsMatchConfidence.Low
        }
        SportsChannelCandidate(
            channel = channel.liveChannel,
            score = score,
            confidence = confidence,
            matchedProgrammeTitle = best?.first?.title,
            reasons = buildList {
                if ((best?.second ?: 0) >= 100) add("Both teams match the TV guide")
                else if ((best?.second ?: 0) > 0) add("Team or competition matches the TV guide")
                if (sportsChannel) add("Sports channel")
            },
        )
    }.sortedWith(compareByDescending<SportsChannelCandidate> { it.score }.thenBy { it.channel.serverOrder })

    private fun programmeScore(fixture: SportsFixture, title: String): Int {
        val text = normalize(title)
        val home = aliases(fixture.homeTeam).any { containsPhrase(text, it) }
        val away = aliases(fixture.awayTeam).any { containsPhrase(text, it) }
        val competition = competitionAliases(fixture.competitionId, fixture.competitionName).any { containsPhrase(text, it) }
        return when {
            home && away -> 130 + if (competition) 25 else 0
            (home || away) && competition -> 45
            home || away -> 20
            competition -> 25
            else -> 0
        }
    }

    private fun aliases(team: String): Set<String> {
        val base = normalize(team).replace(Regex("\\b(fc|afc)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        val known = when (base) {
            "manchester united" -> setOf("man utd", "man united")
            "manchester city" -> setOf("man city")
            "tottenham hotspur" -> setOf("tottenham", "spurs")
            "wolverhampton wanderers" -> setOf("wolves")
            "brighton hove albion" -> setOf("brighton")
            "nottingham forest" -> setOf("nottm forest")
            else -> emptySet()
        }
        return known + base
    }

    private fun competitionAliases(id: String, name: String): Set<String> = when (id.uppercase()) {
        "PL" -> setOf("premier league", "epl", "english premier league")
        "CL" -> setOf("champions league", "uefa champions league", "ucl")
        "ELC" -> setOf("championship", "efl championship")
        "PD" -> setOf("la liga", "laliga")
        "BL1" -> setOf("bundesliga")
        "SA" -> setOf("serie a")
        "FL1" -> setOf("ligue 1")
        else -> setOf(normalize(name))
    }

    private fun containsPhrase(text: String, phrase: String) = Regex("(^| )${Regex.escape(phrase)}( |$)").containsMatchIn(text)
    private fun isSports(value: String) = Regex("\\b(sport|sports|football|soccer)\\b").containsMatchIn(normalize(value))
    private fun normalize(value: String) = value.lowercase().replace("&", " ").replace(Regex("[^a-z0-9]+"), " ").trim()

    private companion object {
        const val THIRTY_MINUTES = 30L * 60L * 1000L
        const val THREE_HOURS = 3L * 60L * 60L * 1000L
    }
}
