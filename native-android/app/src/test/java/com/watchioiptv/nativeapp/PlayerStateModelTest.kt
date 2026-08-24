package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.PlayerReliability
import com.watchioiptv.nativeapp.core.player.WatchioPlayerMetadata
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateModelTest {
    @Test
    fun stateCarriesSingleSessionMetadataAcrossSurfaceHandoff() {
        val media = PlaybackMedia("http://example.invalid/live/1.ts", "News")
        val metadata = WatchioPlayerMetadata(
            currentMedia = media,
            firstFrameRendered = true,
            hasVideo = true,
            hasAudio = true,
            sessionId = 42,
            loadGeneration = 7,
        )

        val preview = WatchioPlayerState.Playing(metadata)
        val fullscreen = WatchioPlayerState.Playing(metadata.copy(positionMs = 1000))

        assertEquals(preview.metadata.sessionId, fullscreen.metadata.sessionId)
        assertEquals(preview.metadata.loadGeneration, fullscreen.metadata.loadGeneration)
        assertTrue(fullscreen.metadata.firstFrameRendered)
        assertFalse(WatchioPlayerState.Failed("Playback failed.", metadata).message.contains("http://"))
    }

    @Test
    fun seekTargetsClampForVodAndStayNonNegativeForLive() {
        assertEquals(0L, PlayerReliability.clampedSeekTarget(5_000L, -10_000L, 100_000L, isLive = false))
        assertEquals(15_000L, PlayerReliability.clampedSeekTarget(5_000L, 10_000L, 100_000L, isLive = false))
        assertEquals(100_000L, PlayerReliability.clampedSeekTarget(95_000L, 10_000L, 100_000L, isLive = false))
        assertEquals(105_000L, PlayerReliability.clampedSeekTarget(95_000L, 10_000L, null, isLive = false))
        assertEquals(0L, PlayerReliability.clampedSeekTarget(0L, -10_000L, null, isLive = true))
    }

    @Test
    fun reliabilityStatesPreserveSafeMessagesOnly() {
        val media = PlaybackMedia(
            url = "http://example.invalid/live/user/pass/1.ts",
            title = "News",
            headers = mapOf("User-Agent" to "WatchioTest", "Referer" to "http://example.invalid"),
        )
        val metadata = WatchioPlayerMetadata(currentMedia = media, loadGeneration = 3)
        val recovering = WatchioPlayerState.Recovering("Unable to connect to stream.", metadata)
        val failed = WatchioPlayerState.Failed("Stream returned an HTTP error.", metadata)

        assertFalse(recovering.message.contains("user"))
        assertFalse(recovering.message.contains("pass"))
        assertFalse(failed.message.contains("http://"))
        assertEquals(media.headers, recovering.metadata.currentMedia?.headers)
    }
}
