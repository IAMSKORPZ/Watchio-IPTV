package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.feature.sports.SportsChannelMatcher
import com.iamskorpz.watchioiptv.feature.sports.SportsFixture
import com.iamskorpz.watchioiptv.feature.sports.SportsFixtureStatus
import com.iamskorpz.watchioiptv.feature.sports.SportsMatchConfidence
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideProgramme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsChannelMatcherTest {
    private val matcher = SportsChannelMatcher()
    private val fixture = fixture("Arsenal", "Chelsea")

    @Test fun exactEpgMatchRanksFirst() {
        val results = matcher.match(fixture, listOf(channel("sports", "Sports One"), channel("other", "Sports Two")), mapOf("sports" to listOf(programme("sports", "Arsenal v Chelsea")), "other" to listOf(programme("other", "Premier League News"))))
        assertEquals("sports", results.first().channel.id)
        assertEquals(SportsMatchConfidence.High, results.first().confidence)
    }

    @Test fun reverseOrderMatches() = assertHigh(fixture, "Chelsea vs Arsenal")
    @Test fun manUtdAliasMatches() = assertHigh(fixture("Manchester United", "Liverpool"), "Man Utd v Liverpool")
    @Test fun spursAliasMatches() = assertHigh(fixture("Tottenham Hotspur", "Arsenal"), "Spurs v Arsenal")
    @Test fun wolvesAliasMatches() = assertHigh(fixture("Wolverhampton Wanderers", "Chelsea"), "Wolves v Chelsea")

    @Test fun competitionAliasContributesScore() {
        val candidate = result(fixture, "EPL Matchday")
        assertTrue(candidate.score >= 35)
        assertEquals(SportsMatchConfidence.Medium, candidate.confidence)
    }

    @Test fun wrongTimeProgrammeIsRejected() {
        val wrong = programme("sports", "Arsenal v Chelsea", start = kickoff - 6 * HOUR, end = kickoff - 4 * HOUR)
        val result = matcher.match(fixture, listOf(channel("sports", "Sports One")), mapOf("sports" to listOf(wrong))).single()
        assertEquals(SportsMatchConfidence.Low, result.confidence)
    }

    @Test fun sportsCategoryFallbackWorks() {
        val result = matcher.match(fixture, listOf(channel("sports", "Arena", "Sports")), emptyMap()).single()
        assertEquals(SportsMatchConfidence.Low, result.confidence)
    }

    @Test fun unrelatedChannelDoesNotRank() = assertTrue(matcher.match(fixture, listOf(channel("news", "News", "News")), emptyMap()).isEmpty())

    @Test fun noEpgReturnsManualSportsFallback() = assertEquals("sports", matcher.match(fixture, listOf(channel("sports", "Football Live")), emptyMap()).single().channel.id)

    @Test fun differentProviderChannelsCanBeScopedBeforeMatching() {
        val p1 = channel("one", "Sports One", provider = "p1")
        val p2 = channel("two", "Sports Two", provider = "p2")
        val scoped = listOf(p1, p2).filter { it.providerId == ProviderId("p1") }
        assertTrue(matcher.match(fixture, scoped, emptyMap()).all { it.channel.providerId == ProviderId("p1") })
    }

    private fun assertHigh(fixture: SportsFixture, title: String) = assertEquals(SportsMatchConfidence.High, result(fixture, title).confidence)
    private fun result(fixture: SportsFixture, title: String) = matcher.match(fixture, listOf(channel("sports", "Sports One")), mapOf("sports" to listOf(programme("sports", title)))).single()
    private fun fixture(home: String, away: String) = SportsFixture("1", "PL", "Premier League", Instant.ofEpochMilli(kickoff), home, away, SportsFixtureStatus.Scheduled)
    private fun channel(id: String, name: String, category: String = "sports", provider: String = "p1"): WatchioGuideChannel {
        val live = LiveTvChannel(ProviderId(provider), ProviderType.Xtream, id, name, null, category, id, "ts", null, emptyMap(), 1, false)
        return WatchioGuideChannel(live.providerId, id, name, null, null, category, false, false, id, live)
    }
    private fun programme(channel: String, title: String, start: Long = kickoff, end: Long = kickoff + 2 * HOUR) = WatchioGuideProgramme("pr-$channel", channel, channel, title, startUtcMs = start, endUtcMs = end, progress = 0f, isLiveNow = false)

    private companion object { const val HOUR = 3_600_000L; const val kickoff = 1_800_000_000_000L }
}
