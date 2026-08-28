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

    @Test
    fun trackAndPlaybackMetadataHoldAuthoritativeState() {
        val audioTrack1 = com.watchioiptv.nativeapp.core.player.WatchioAudioTrack("a1", "English • 5.1", "en", 6, isSelected = true)
        val audioTrack2 = com.watchioiptv.nativeapp.core.player.WatchioAudioTrack("a2", "Spanish • Stereo", "es", 2, isSelected = false)
        val subTrack1 = com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack("s1", "English", "en", isSelected = false)
        val subTrack2 = com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack("s2", "Spanish", "es", isSelected = true)

        val metadata = WatchioPlayerMetadata(
            audioTracks = listOf(audioTrack1, audioTrack2),
            selectedAudioTrack = audioTrack1,
            subtitleTracks = listOf(subTrack1, subTrack2),
            selectedSubtitleTrack = subTrack2,
            playbackSpeed = 1.25f,
            videoScalingMode = com.watchioiptv.nativeapp.domain.repository.VideoScalingMode.Zoom,
            isMuted = true,
            isSeekable = true,
        )

        assertEquals("English • 5.1", metadata.selectedAudioTrack?.label)
        assertEquals("Spanish", metadata.selectedSubtitleTrack?.label)
        assertEquals(1.25f, metadata.playbackSpeed)
        assertEquals(com.watchioiptv.nativeapp.domain.repository.VideoScalingMode.Zoom, metadata.videoScalingMode)
        assertTrue(metadata.isMuted)
        assertTrue(metadata.isSeekable)
    }

    @Test
    fun subtitleOffAndReselectionCyclePreservesTrackState() {
        val subTrack = com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack("s1", "French", "fr", isSelected = false)
        var metadata = WatchioPlayerMetadata(
            subtitleTracks = listOf(subTrack),
            selectedSubtitleTrack = subTrack.copy(isSelected = true),
        )
        assertEquals("French", metadata.selectedSubtitleTrack?.label)

        // Select Off (null)
        metadata = metadata.copy(selectedSubtitleTrack = null)
        assertEquals(null, metadata.selectedSubtitleTrack)

        // Reselect Subtitle
        metadata = metadata.copy(selectedSubtitleTrack = subTrack.copy(isSelected = true))
        assertEquals("French", metadata.selectedSubtitleTrack?.label)
    }

    @Test
    fun newMediaLoadResetsSpeedToOnePointZero() {
        var metadata = WatchioPlayerMetadata(
            currentMedia = PlaybackMedia("http://example.invalid/movie.mp4", "Movie", isLive = false),
            playbackSpeed = 2.0f,
        )
        assertEquals(2.0f, metadata.playbackSpeed)

        // Switching to Live TV stream resets speed to 1.0f
        val liveMedia = PlaybackMedia("http://example.invalid/live.ts", "Live News", isLive = true)
        metadata = metadata.copy(
            currentMedia = liveMedia,
            playbackSpeed = 1.0f,
            isSeekable = false,
        )
        assertEquals(1.0f, metadata.playbackSpeed)
        assertFalse(metadata.isSeekable)
    }

    @Test
    fun recoveringStateIsDistinctFromFailedTerminalState() {
        val metadata = WatchioPlayerMetadata(sessionId = 1)
        val recovering: WatchioPlayerState = WatchioPlayerState.Recovering("Reconnecting…", metadata)
        val failed: WatchioPlayerState = WatchioPlayerState.Failed("Stream unreachable", metadata)

        assertTrue(recovering is WatchioPlayerState.Recovering)
        assertFalse(recovering is WatchioPlayerState.Failed)
        assertEquals("Reconnecting…", (recovering as WatchioPlayerState.Recovering).message)
        assertEquals("Stream unreachable", (failed as WatchioPlayerState.Failed).message)
    }
}
