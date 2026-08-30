package com.watchioiptv.nativeapp.core.player

import android.view.ViewGroup
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import kotlinx.coroutines.flow.StateFlow

interface WatchioPlayerManager {
    val state: StateFlow<WatchioPlayerState>

    suspend fun load(media: PlaybackMedia)
    fun play()
    fun pause()
    fun stop()
    fun retry()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun selectAudioTrack(track: WatchioAudioTrack)
    fun selectSubtitleTrack(track: WatchioSubtitleTrack?)
    fun setVideoScalingMode(mode: VideoScalingMode)
    fun setPlaybackSpeed(speed: Float)
    fun setMuted(muted: Boolean)
    fun restart()
    fun snapshot(): WatchioPlayerMetadata
    fun attachSurface(container: ViewGroup)
    fun detachSurface(container: ViewGroup)
    fun release()
}

data class PlaybackMedia(
    val url: String,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0L,
    val isLive: Boolean = true,
)

data class WatchioAudioTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val channelCount: Int? = null,
    val isSelected: Boolean = false,
    val groupIndex: Int = 0,
    val trackIndex: Int = 0,
)

data class WatchioSubtitleTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val groupIndex: Int = 0,
    val trackIndex: Int = 0,
)

data class WatchioPlayerMetadata(
    val currentMedia: PlaybackMedia? = null,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val isSeekable: Boolean = false,
    val firstFrameRendered: Boolean = false,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val sessionId: Long = 0L,
    val loadGeneration: Long = 0L,
    val audioTracks: List<WatchioAudioTrack> = emptyList(),
    val selectedAudioTrack: WatchioAudioTrack? = null,
    val subtitleTracks: List<WatchioSubtitleTrack> = emptyList(),
    val selectedSubtitleTrack: WatchioSubtitleTrack? = null,
    val playbackSpeed: Float = 1.0f,
    val videoScalingMode: VideoScalingMode = VideoScalingMode.Fit,
    val isMuted: Boolean = false,
)
