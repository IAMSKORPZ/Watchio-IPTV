package com.watchioiptv.nativeapp.core.player

import android.content.Context
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

@UnstableApi
class Media3WatchioPlayerManager(
    private val context: Context,
    settingsRepository: SettingsRepository,
) : WatchioPlayerManager {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var lastMedia: PlaybackMedia? = null
    private var currentMetadata = WatchioPlayerMetadata(sessionId = SESSION_ID.incrementAndGet())
    private var loadGeneration = 0L
    private var retryCount = 0
    private var retryJob: Job? = null
    private var playerSettings = PlayerSettings()
    private var currentVideoScalingMode = VideoScalingMode.Fit
    private var lastNonZeroVolume = 1.0f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutableState = MutableStateFlow<WatchioPlayerState>(WatchioPlayerState.Idle(currentMetadata))
    override val state: StateFlow<WatchioPlayerState> = mutableState

    init {
        scope.launch {
            settingsRepository.playerSettings.collect { settings ->
                playerSettings = settings
                currentVideoScalingMode = settings.videoScalingMode
                playerView?.resizeMode = settings.videoScalingMode.toResizeMode()
                updateMetadataState()
            }
        }
    }

    override suspend fun load(media: PlaybackMedia) {
        retryJob?.cancel()
        retryCount = 0
        lastMedia = media
        loadGeneration += 1
        currentMetadata = currentMetadata.copy(
            currentMedia = media,
            positionMs = media.startPositionMs,
            durationMs = null,
            isSeekable = !media.isLive,
            firstFrameRendered = false,
            hasVideo = false,
            hasAudio = false,
            loadGeneration = loadGeneration,
            audioTracks = emptyList(),
            selectedAudioTrack = null,
            subtitleTracks = emptyList(),
            selectedSubtitleTrack = null,
            playbackSpeed = 1.0f,
            videoScalingMode = currentVideoScalingMode,
            isMuted = player?.volume == 0f,
        )
        mutableState.value = WatchioPlayerState.Connecting(currentMetadata)
        loadIntoPlayer(media, currentMetadata.loadGeneration)
    }

    private fun loadIntoPlayer(media: PlaybackMedia, generation: Long) {
        val exoPlayer = ensurePlayer()
        exoPlayer.setPlaybackSpeed(1.0f)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(media.headers)
        val mediaSource = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpFactory)
            .createMediaSource(MediaItem.Builder().setUri(media.url).build())
        exoPlayer.setMediaSource(mediaSource)
        if (media.startPositionMs > 0L) exoPlayer.seekTo(media.startPositionMs)
        currentMetadata = currentMetadata.copy(loadGeneration = generation)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun play() {
        retryJob?.cancel()
        ensurePlayer().play()
    }

    override fun pause() {
        retryJob?.cancel()
        player?.pause()
        mutableState.value = WatchioPlayerState.Paused(snapshot())
    }

    override fun stop() {
        retryJob?.cancel()
        player?.stop()
        player?.clearMediaItems()
        lastMedia = null
        retryCount = 0
        currentMetadata = currentMetadata.copy(
            currentMedia = null,
            positionMs = 0L,
            durationMs = null,
            isSeekable = false,
            firstFrameRendered = false,
            audioTracks = emptyList(),
            selectedAudioTrack = null,
            subtitleTracks = emptyList(),
            selectedSubtitleTrack = null,
        )
        mutableState.value = WatchioPlayerState.Idle(currentMetadata)
    }

    override fun retry() {
        retryJob?.cancel()
        retryCount = 0
        lastMedia?.let { media ->
            loadGeneration += 1
            currentMetadata = snapshot().copy(
                currentMedia = media,
                firstFrameRendered = false,
                loadGeneration = loadGeneration,
            )
            mutableState.value = WatchioPlayerState.Connecting(currentMetadata)
            player?.stop()
            loadIntoPlayer(media, loadGeneration)
        }
    }

    override fun seekTo(positionMs: Long) {
        val snapshot = snapshot()
        val safePosition = positionMs
            .coerceAtLeast(0L)
            .let { target ->
                if (snapshot.currentMedia?.isLive == true && !snapshot.isSeekable) target
                else snapshot.durationMs?.let { target.coerceAtMost(it) } ?: target
            }
        player?.seekTo(safePosition)
        currentMetadata = snapshot.copy(positionMs = safePosition)
        mutableState.value = when (mutableState.value) {
            is WatchioPlayerState.Playing -> WatchioPlayerState.Playing(currentMetadata)
            is WatchioPlayerState.Paused -> WatchioPlayerState.Paused(currentMetadata)
            else -> mutableState.value
        }
    }

    override fun seekBy(deltaMs: Long) {
        val snapshot = snapshot()
        val isLiveNonSeekable = snapshot.currentMedia?.isLive == true && !snapshot.isSeekable
        val target = PlayerReliability.clampedSeekTarget(
            snapshot.positionMs,
            deltaMs,
            snapshot.durationMs,
            isLiveNonSeekable,
        )
        seekTo(target)
    }

    override fun selectAudioTrack(track: WatchioAudioTrack) {
        val exoPlayer = player ?: return
        val currentTracks = exoPlayer.currentTracks
        if (track.groupIndex in 0 until currentTracks.groups.size) {
            val group = currentTracks.groups[track.groupIndex]
            if (track.trackIndex in 0 until group.length) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(override)
                    .build()
                updateMetadataState()
            }
        }
    }

    override fun selectSubtitleTrack(track: WatchioSubtitleTrack?) {
        val exoPlayer = player ?: return
        if (track == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            val currentTracks = exoPlayer.currentTracks
            if (track.groupIndex in 0 until currentTracks.groups.size) {
                val group = currentTracks.groups[track.groupIndex]
                if (track.trackIndex in 0 until group.length) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(override)
                        .build()
                }
            }
        }
        updateMetadataState()
    }

    override fun setVideoScalingMode(mode: VideoScalingMode) {
        currentVideoScalingMode = mode
        playerView?.resizeMode = mode.toResizeMode()
        updateMetadataState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        val exoPlayer = player ?: return
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        exoPlayer.setPlaybackSpeed(safeSpeed)
        updateMetadataState()
    }

    override fun setMuted(muted: Boolean) {
        val exoPlayer = player ?: return
        if (muted) {
            val currentVol = exoPlayer.volume
            if (currentVol > 0f) lastNonZeroVolume = currentVol
            exoPlayer.volume = 0f
        } else {
            exoPlayer.volume = if (lastNonZeroVolume > 0f) lastNonZeroVolume else 1.0f
        }
        updateMetadataState()
    }

    override fun restart() {
        seekTo(0L)
        play()
    }

    override fun snapshot(): WatchioPlayerMetadata {
        val exoPlayer = player
        val duration = exoPlayer?.duration?.takeIf { it != C.TIME_UNSET && it >= 0L }
        val isSeekable = exoPlayer?.isCurrentMediaItemSeekable ?: (currentMetadata.currentMedia?.isLive == false)
        val audioTracks = exoPlayer?.let { extractAudioTracks(it) } ?: currentMetadata.audioTracks
        val subtitleTracks = exoPlayer?.let { extractSubtitleTracks(it) } ?: currentMetadata.subtitleTracks
        val speed = exoPlayer?.playbackParameters?.speed ?: currentMetadata.playbackSpeed
        val isMuted = exoPlayer?.volume == 0f

        return currentMetadata.copy(
            positionMs = exoPlayer?.currentPosition ?: currentMetadata.positionMs,
            durationMs = duration,
            isSeekable = isSeekable,
            audioTracks = audioTracks,
            selectedAudioTrack = audioTracks.firstOrNull { it.isSelected },
            subtitleTracks = subtitleTracks,
            selectedSubtitleTrack = subtitleTracks.firstOrNull { it.isSelected },
            playbackSpeed = speed,
            videoScalingMode = currentVideoScalingMode,
            isMuted = isMuted,
        )
    }

    private fun updateMetadataState() {
        currentMetadata = snapshot()
        mutableState.value = when (val state = mutableState.value) {
            is WatchioPlayerState.Playing -> WatchioPlayerState.Playing(currentMetadata)
            is WatchioPlayerState.Paused -> WatchioPlayerState.Paused(currentMetadata)
            is WatchioPlayerState.Buffering -> WatchioPlayerState.Buffering(currentMetadata)
            is WatchioPlayerState.Connecting -> WatchioPlayerState.Connecting(currentMetadata)
            is WatchioPlayerState.Recovering -> WatchioPlayerState.Recovering(state.message, currentMetadata)
            is WatchioPlayerState.Failed -> WatchioPlayerState.Failed(state.message, currentMetadata)
            is WatchioPlayerState.Ended -> WatchioPlayerState.Ended(currentMetadata)
            is WatchioPlayerState.Released -> WatchioPlayerState.Released(currentMetadata)
            is WatchioPlayerState.Idle -> WatchioPlayerState.Idle(currentMetadata)
        }
    }

    private fun extractAudioTracks(exoPlayer: ExoPlayer): List<WatchioAudioTrack> {
        val tracks = mutableListOf<WatchioAudioTrack>()
        val currentTracks = exoPlayer.currentTracks
        var trackCount = 0
        for (groupIndex in 0 until currentTracks.groups.size) {
            val group = currentTracks.groups[groupIndex]
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)
                trackCount++
                val lang = format.language?.takeIf { it.isNotBlank() }
                val channels = format.channelCount.takeIf { it > 0 }
                val baseLabel = format.label?.takeIf { it.isNotBlank() }
                    ?: lang?.let { formatLanguage(it) }
                    ?: "Audio Track $trackCount"
                val displayLabel = if (channels != null && channels > 0) {
                    val channelDesc = if (channels == 6) "5.1" else if (channels == 2) "Stereo" else if (channels == 1) "Mono" else "${channels}ch"
                    "$baseLabel • $channelDesc"
                } else baseLabel

                tracks.add(
                    WatchioAudioTrack(
                        id = "audio_${groupIndex}_$trackIndex",
                        label = displayLabel,
                        language = lang,
                        channelCount = channels,
                        isSelected = isSelected,
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                    )
                )
            }
        }
        return tracks
    }

    private fun extractSubtitleTracks(exoPlayer: ExoPlayer): List<WatchioSubtitleTrack> {
        val tracks = mutableListOf<WatchioSubtitleTrack>()
        val currentTracks = exoPlayer.currentTracks
        var trackCount = 0
        for (groupIndex in 0 until currentTracks.groups.size) {
            val group = currentTracks.groups[groupIndex]
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)
                trackCount++
                val lang = format.language?.takeIf { it.isNotBlank() }
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: lang?.let { formatLanguage(it) }
                    ?: "Subtitle $trackCount"
                tracks.add(
                    WatchioSubtitleTrack(
                        id = "text_${groupIndex}_$trackIndex",
                        label = label,
                        language = lang,
                        isSelected = isSelected,
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                    )
                )
            }
        }
        return tracks
    }

    private fun formatLanguage(code: String): String {
        return try {
            val locale = Locale.forLanguageTag(code)
            val name = locale.getDisplayLanguage(Locale.ENGLISH)
            if (name.isNotBlank()) name else code.uppercase()
        } catch (_: Exception) {
            code.uppercase()
        }
    }

    override fun attachSurface(container: ViewGroup) {
        val view = playerView ?: PlayerView(context).also {
            it.useController = false
            it.resizeMode = currentVideoScalingMode.toResizeMode()
            it.player = ensurePlayer()
            playerView = it
        }
        if (view.parent !== container) {
            (view.parent as? ViewGroup)?.removeView(view)
            container.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    override fun detachSurface(container: ViewGroup) {
        val view = playerView ?: return
        if (view.parent === container) container.removeView(view)
    }

    override fun release() {
        retryJob?.cancel()
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        currentMetadata = WatchioPlayerMetadata(sessionId = SESSION_ID.incrementAndGet())
        retryCount = 0
        mutableState.value = WatchioPlayerState.Released(currentMetadata)
    }

    private fun ensurePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(context).build().also { exoPlayer ->
            exoPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    currentMetadata = snapshot()
                    mutableState.value = when (playbackState) {
                        Player.STATE_BUFFERING -> WatchioPlayerState.Buffering(currentMetadata)
                        Player.STATE_READY -> if (exoPlayer.isPlaying) {
                            currentMetadata = currentMetadata.copy(firstFrameRendered = true)
                            WatchioPlayerState.Playing(currentMetadata)
                        } else {
                            WatchioPlayerState.Paused(currentMetadata)
                        }
                        Player.STATE_ENDED -> WatchioPlayerState.Ended(currentMetadata)
                        else -> mutableState.value
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    currentMetadata = snapshot().copy(firstFrameRendered = currentMetadata.firstFrameRendered || isPlaying)
                    if (isPlaying) {
                        retryJob?.cancel()
                        retryCount = 0
                        mutableState.value = WatchioPlayerState.Playing(currentMetadata)
                    } else if (mutableState.value !is WatchioPlayerState.Buffering && mutableState.value !is WatchioPlayerState.Connecting) {
                        mutableState.value = WatchioPlayerState.Paused(currentMetadata)
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    updateMetadataState()
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    updateMetadataState()
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    updateMetadataState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateMetadataState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    currentMetadata = snapshot()
                    val message = PlayerReliability.userMessage(error)
                    if (
                        playerSettings.autoRetryStreams &&
                        PlayerReliability.shouldAutoRetry(error) &&
                        retryCount < playerSettings.retryAttempts.coerceIn(1, PlayerReliability.MaxAutomaticRetries)
                    ) {
                        scheduleRetry(message, currentMetadata.loadGeneration)
                    } else {
                        mutableState.value = WatchioPlayerState.Failed(message, currentMetadata)
                    }
                }
            })
            player = exoPlayer
        }
    }

    private fun scheduleRetry(message: String, generation: Long) {
        retryJob?.cancel()
        retryCount += 1
        mutableState.value = WatchioPlayerState.Recovering(message, currentMetadata)
        val delayMs = retryCount * 1_500L
        retryJob = scope.launch {
            delay(delayMs)
            val media = lastMedia ?: return@launch
            if (currentMetadata.loadGeneration != generation) return@launch
            player?.stop()
            loadIntoPlayer(media, generation)
        }
    }

    companion object {
        private val SESSION_ID = AtomicLong(0L)
    }
}

@UnstableApi
fun VideoScalingMode.toResizeMode(): Int = when (this) {
    VideoScalingMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoScalingMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    VideoScalingMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
