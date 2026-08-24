package com.watchioiptv.nativeapp.core.player

import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

interface WatchioPlayerManager {
    val state: StateFlow<WatchioPlayerState>

    suspend fun load(media: PlaybackMedia)
    fun play()
    fun pause()
    fun stop()
    fun retry()
    fun seekTo(positionMs: Long)
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

data class WatchioPlayerMetadata(
    val currentMedia: PlaybackMedia? = null,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val firstFrameRendered: Boolean = false,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val sessionId: Long = 0L,
    val loadGeneration: Long = 0L,
)
