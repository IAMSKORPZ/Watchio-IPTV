package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.feature.sports.v2.*
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideProgramme
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureNanoTime

class EpgFixtureDiscoveryTest {
    private val discovery = EpgFixtureDiscovery()

    @Test fun bothTeamsInTitleProducesVerifiedProviderEpgCandidate() {
        val result = discover(programme("Sabah FK v Slavia Prague"))
        assertEquals(MatchChannelConfidence.VERIFIED, result.single().confidence)
        assertTrue(result.single().reasons.contains("Provider EPG discovery"))
    }

    @Test fun titleAndDescriptionProducesPossibleCandidate() {
        val result = discover(programme("Sabah FK live", description = "UEFA Champions League against SK Slavia Praha"))
        assertEquals(MatchChannelConfidence.POSSIBLE, result.single().confidence)
    }

    @Test fun oneTeamDescriptionOnlyAndWrongTimeAreRejected() {
        assertTrue(discover(programme("Football", description = "Sabah FK coverage")).isEmpty())
        assertTrue(discover(programme("Sabah FK v Slavia Prague", start = kickoff + 4 * 3_600_000L, end = kickoff + 6 * 3_600_000L)).isEmpty())
    }

    @Test fun providerIsolationExcludesOtherProvider() {
        val active = channel("active", "one")
        val other = channel("other", "two")
        val result = discovery.discover(fixture(), listOf(active, other), mapOf("one" to listOf(programme("Sabah v Slavia Praha", channel = "one")), "two" to listOf(programme("Sabah v Slavia Praha", channel = "two"))))
        assertEquals(listOf("one"), result.map { it.channel.channelId })
    }

    @Test fun instantMatchingSurvivesGmtBstAndDstDates() {
        listOf("2026-01-10T15:00:00Z", "2026-07-10T14:00:00Z", "2026-10-25T14:00:00Z").forEach { instant ->
            val time = Instant.parse(instant)
            assertEquals(1, discovery.discover(fixture(time), listOf(channel("active", "one")), mapOf("one" to listOf(programme("Sabah v Slavia Prague", time.toEpochMilli() - 900_000L, time.toEpochMilli() + 7_200_000L)))).size)
        }
    }

    @Test fun reusableEpgIndexNormalizesLargeGuideOnce() {
        val channels = (0 until 2_000).map { channel("active", "channel-$it") }
        val programmes = channels.associate { row ->
            row.channelId to listOf(programme("General football coverage", channel = row.channelId))
        }.toMutableMap()
        programmes["channel-1999"] = listOf(programme("Sabah FK v Slavia Prague", channel = "channel-1999"))
        lateinit var index: EpgFixtureIndex
        val indexNs = measureNanoTime { index = discovery.buildIndex(channels, programmes) }
        lateinit var result: List<EpgFixtureCandidate>
        val discoveryNs = measureNanoTime { repeat(25) { result = discovery.discover(fixture(), index) } }
        assertEquals("channel-1999", result.single().channel.channelId)
        println("SPORTS_EPG_INDEX programmes=2000 buildMs=${indexNs / 1_000_000.0} repeatedDiscoveryMs=${discoveryNs / 1_000_000.0} repeats=25")
    }

    private val kickoff = Instant.parse("2026-10-13T19:00:00Z").toEpochMilli()
    private fun discover(programme: WatchioGuideProgramme) = discovery.discover(fixture(), listOf(channel("active", "one")), mapOf("one" to listOf(programme)))
    private fun fixture(time: Instant = Instant.ofEpochMilli(kickoff)) = SportsFixture(
        identity = SportsSourceIdentity(SportsDataSource.FootballData, "2049579"),
        competition = SportsCompetition(SportsSourceIdentity(SportsDataSource.FootballData, "CL"), name = "UEFA Champions League", code = "CL"),
        homeTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.FootballData, "home"), displayName = "Sabah FK"),
        awayTeam = SportsTeam(SportsSourceIdentity(SportsDataSource.FootballData, "away"), displayName = "SK Slavia Praha"),
        kickoff = time, state = SportsFixtureState.Scheduled, fetchedAt = time.minusSeconds(60),
    )
    private fun channel(provider: String, id: String): WatchioGuideChannel {
        val live = LiveTvChannel(ProviderId(provider), ProviderType.Xtream, id, "UK: TNT Sports 1 HD", null, "sports", id, "ts", null, emptyMap(), 1, false)
        return WatchioGuideChannel(live.providerId, id, live.name, null, null, "sports", false, false, id, live)
    }
    private fun programme(title: String, start: Long = kickoff - 900_000L, end: Long = kickoff + 7_200_000L, description: String? = null, channel: String = "one") =
        WatchioGuideProgramme("p-$channel", channel, channel, title, description = description, startUtcMs = start, endUtcMs = end, progress = 0f, isLiveNow = false)
}
