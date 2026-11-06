package com.iamskorpz.watchioiptv.core.player

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iamskorpz.watchioiptv.R
import com.iamskorpz.watchioiptv.domain.repository.PlayerSettings
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.domain.repository.VideoScalingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

@UnstableApi
class Media3WatchioPlayerManager(
    private val context: Context,
    settingsRepository: SettingsRepository,
    private val offlineDataSourceFactory: DataSource.Factory? = null,
    private val onRemotePlaybackAvailable: () -> Unit = {},
    private val onRemotePlaybackFailure: (Throwable) -> Unit = {},
    private val endpointRecovery: PlaybackEndpointRecovery? = null,
    private val failoverDiagnostics: PlaybackFailoverDiagnostics = NoOpPlaybackFailoverDiagnostics,
) : WatchioPlayerManager {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var fullscreenContainer: ViewGroup? = null
    private var lastMedia: PlaybackMedia? = null
    private var currentMetadata = WatchioPlayerMetadata(sessionId = SESSION_ID.incrementAndGet())
    private var loadGeneration = 0L
    private var retryCount = 0
    private var retryJob: Job? = null
    private var endpointRecoveryJob: Job? = null
    private var endpointRecoveryInProgress = false
    private var pendingTerminalError: PlaybackException? = null
    private val endpointRecoveryGate = PlaybackEndpointRecoveryGate()
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
        endpointRecoveryJob?.cancel()
        endpointRecoveryInProgress = false
        pendingTerminalError = null
        retryCount = 0
        endpointRecoveryGate.reset()
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
        failoverDiagnostics.trace("load generation=$loadGeneration source=${media.sourceType} targetMs=${media.startPositionMs}")
        loadIntoPlayer(media, currentMetadata.loadGeneration)
    }

    private fun loadIntoPlayer(media: PlaybackMedia, generation: Long) {
        val exoPlayer = ensurePlayer()
        exoPlayer.setPlaybackSpeed(1.0f)
        val dataSourceFactory = when (media.sourceType) {
            PlaybackSourceType.Remote -> DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(media.headers)
            PlaybackSourceType.LocalFile -> DefaultDataSource.Factory(context)
            PlaybackSourceType.OfflineCache -> requireNotNull(offlineDataSourceFactory) {
                "Offline playback is unavailable."
            }
        }
        val mediaItem = MediaItem.Builder()
            .setUri(media.url)
            .setMimeType(media.mimeType)
            .apply { media.cacheKey?.let(::setCustomCacheKey) }
            .build()
        val mediaSource = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .createMediaSource(mediaItem)
        exoPlayer.setMediaSource(mediaSource)
        if (media.startPositionMs > 0L) {
            failoverDiagnostics.trace("seek-apply generation=$generation targetMs=${media.startPositionMs}")
            exoPlayer.seekTo(media.startPositionMs)
        }
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
        endpointRecoveryJob?.cancel()
        endpointRecoveryInProgress = false
        pendingTerminalError = null
        player?.stop()
        player?.clearMediaItems()
        lastMedia = null
        retryCount = 0
        endpointRecoveryGate.reset()
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
        endpointRecoveryJob?.cancel()
        endpointRecoveryInProgress = false
        pendingTerminalError = null
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
        failoverDiagnostics.trace("seek-request generation=$loadGeneration requestedMs=$positionMs targetMs=$safePosition durationMs=${snapshot.durationMs ?: -1L}")
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
        val currentTracks = exoPlayer?.currentTracks
        val hasVideo = currentTracks?.groups?.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected } ?: false
        val hasAudio = currentTracks?.groups?.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: false

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
            hasVideo = hasVideo,
            hasAudio = hasAudio,
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

    override fun attachSurface(container: ViewGroup) = attachSurface(container, currentVideoScalingMode)

    override fun attachPreviewSurface(container: ViewGroup) {
        if (fullscreenContainer != null && fullscreenContainer !== container) return
        attachSurface(container, VideoScalingMode.Fit)
    }

    override fun attachFullscreenSurface(container: ViewGroup) {
        fullscreenContainer = container
        attachSurface(container, currentVideoScalingMode)
    }

    private fun attachSurface(container: ViewGroup, scalingMode: VideoScalingMode) {
        val exoPlayer = ensurePlayer()
        val view = playerView ?: (LayoutInflater.from(context)
            .inflate(R.layout.watchio_player_view, container, false) as PlayerView).also {
            it.useController = false
            it.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            it.resizeMode = scalingMode.toResizeMode()
            it.isFocusable = false
            it.isFocusableInTouchMode = false
            it.isClickable = false
            it.player = exoPlayer
            playerView = it
        }
        val matchParentParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        if (view.parent !== container) {
            (view.parent as? ViewGroup)?.removeView(view)
            container.removeAllViews()
            container.addView(view, matchParentParams)
            // Essential on Android TV: reparenting SurfaceView requires resetting player on PlayerView
            // so ExoPlayer reattaches to the newly created SurfaceHolder.
            view.player = null
            view.player = exoPlayer
        } else if (view.player !== exoPlayer) {
            view.player = exoPlayer
        }
        // PlayerView is shared by preview and fullscreen. Never retain measured preview bounds.
        view.layoutParams = matchParentParams
        view.resizeMode = scalingMode.toResizeMode()
        container.requestLayout()
        view.requestLayout()
        container.invalidate()
        view.invalidate()
    }

    override fun detachSurface(container: ViewGroup) {
        if (fullscreenContainer === container) fullscreenContainer = null
        val view = playerView ?: return
        if (view.parent === container) {
            container.removeView(view)
            view.requestLayout()
        }
    }

    override fun release() {
        retryJob?.cancel()
        endpointRecoveryJob?.cancel()
        endpointRecoveryInProgress = false
        pendingTerminalError = null
        playerView?.player = null
        playerView = null
        fullscreenContainer = null
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
                    if (playbackState == Player.STATE_READY) {
                        failoverDiagnostics.trace(
                            "ready generation=${currentMetadata.loadGeneration} positionMs=${currentMetadata.positionMs} " +
                                "durationMs=${currentMetadata.durationMs ?: -1L} playing=${exoPlayer.isPlaying}",
                        )
                    }
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
                    currentMetadata = snapshot().copy(firstFrameRendered = currentMetadata.firstFrameRendered)
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
                    val trace = snapshot()
                    failoverDiagnostics.trace(
                        "tracks generation=${trace.loadGeneration} audio=${trace.selectedAudioTrack?.language ?: trace.selectedAudioTrack?.id ?: "none"} " +
                            "subtitle=${trace.selectedSubtitleTrack?.language ?: trace.selectedSubtitleTrack?.id ?: "none"}",
                    )
                }

                override fun onRenderedFirstFrame() {
                    if (lastMedia?.sourceType == PlaybackSourceType.Remote) onRemotePlaybackAvailable()
                    currentMetadata = snapshot().copy(firstFrameRendered = true)
                    failoverDiagnostics.trace(
                        "first-frame generation=${currentMetadata.loadGeneration} positionMs=${currentMetadata.positionMs} " +
                            "durationMs=${currentMetadata.durationMs ?: -1L}",
                    )
                    mutableState.value = if (exoPlayer.isPlaying) {
                        WatchioPlayerState.Playing(currentMetadata)
                    } else {
                        WatchioPlayerState.Paused(currentMetadata)
                    }
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
                    if (lastMedia?.sourceType == PlaybackSourceType.Remote) onRemotePlaybackFailure(error)
                    currentMetadata = snapshot()
                    failoverDiagnostics.trace(
                        "player-error generation=${currentMetadata.loadGeneration} positionMs=${currentMetadata.positionMs} " +
                            "code=${error.errorCode} type=${error.javaClass.simpleName}",
                    )
                    lastMedia = lastMedia?.withRecoveryPosition(currentMetadata.positionMs)
                    val message = PlayerReliability.userMessage(error)
                    val failure = error.toPlaybackFailure()
                    val attemptCount = retryCount + 1
                    if (endpointRecoveryInProgress) {
                        pendingTerminalError = error
                        return
                    }
                    if (
                        endpointRecovery != null &&
                        lastMedia?.recoveryRequest != null &&
                        PlaybackEndpointFailureClassifier.shouldAttemptRecovery(failure, attemptCount) &&
                        endpointRecoveryGate.tryStart()
                    ) {
                        attemptEndpointRecovery(failure, error, message, currentMetadata.loadGeneration, attemptCount)
                    } else {
                        handleStandardPlayerError(error, message, currentMetadata.loadGeneration)
                    }
                }
            })
            exoPlayer.addAnalyticsListener(object : AnalyticsListener {
                override fun onLoadError(
                    eventTime: AnalyticsListener.EventTime,
                    loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData,
                    error: IOException,
                    wasCanceled: Boolean,
                ) {
                    if (wasCanceled || endpointRecoveryInProgress || endpointRecoveryGate.hasStarted()) return
                    val media = lastMedia ?: return
                    if (media.sourceType != PlaybackSourceType.Remote || media.recoveryRequest == null) return
                    val failure = error.toPlaybackFailure()
                    if (!PlaybackEndpointFailureClassifier.shouldAttemptRecovery(failure, attemptCount = 1)) return
                    currentMetadata = snapshot()
                    lastMedia = media.withRecoveryPosition(currentMetadata.positionMs)
                    if (!endpointRecoveryGate.tryStart()) return
                    attemptEndpointRecovery(
                        failure = failure,
                        terminalError = null,
                        message = "Unable to connect to stream.",
                        generation = currentMetadata.loadGeneration,
                        attemptCount = 1,
                    )
                }
            })
            player = exoPlayer
        }
    }

    private fun attemptEndpointRecovery(
        failure: PlaybackFailure,
        terminalError: PlaybackException?,
        message: String,
        generation: Long,
        attemptCount: Int,
    ) {
        val recovery = endpointRecovery ?: run {
            terminalError?.let { handleStandardPlayerError(it, message, generation) }
            return
        }
        val media = lastMedia ?: return
        val capturedPositionMs = snapshot().positionMs
        failoverDiagnostics.trace(
            "recovery-start generation=$generation capturedMs=$capturedPositionMs attempt=$attemptCount failure=${failure.javaClass.simpleName}",
        )
        endpointRecoveryInProgress = true
        mutableState.value = WatchioPlayerState.Recovering("Checking provider connection...", currentMetadata)
        endpointRecoveryJob = scope.launch {
            val result = recovery.recover(media, failure, attemptCount, capturedPositionMs)
            if (currentMetadata.loadGeneration != generation) {
                endpointRecoveryInProgress = false
                pendingTerminalError = null
                return@launch
            }
            endpointRecoveryInProgress = false
            when (result) {
                is PlaybackEndpointRecoveryResult.Retry -> {
                    failoverDiagnostics.trace(
                        "recovery-retry oldGeneration=$generation targetMs=${result.media.startPositionMs} source=${result.media.sourceType}",
                    )
                    pendingTerminalError = null
                    lastMedia = result.media
                    loadGeneration += 1
                    currentMetadata = snapshot().copy(
                        currentMedia = result.media,
                        positionMs = result.media.startPositionMs,
                        firstFrameRendered = false,
                        loadGeneration = loadGeneration,
                    )
                    mutableState.value = WatchioPlayerState.Connecting(currentMetadata)
                    player?.stop()
                    loadIntoPlayer(result.media, loadGeneration)
                }
                is PlaybackEndpointRecoveryResult.AccountBlocked -> {
                    failoverDiagnostics.trace("recovery-account-blocked generation=$generation")
                    pendingTerminalError = null
                    mutableState.value = WatchioPlayerState.Failed(result.message, currentMetadata)
                }
                PlaybackEndpointRecoveryResult.NotRecovered -> {
                    failoverDiagnostics.trace("recovery-not-recovered generation=$generation")
                    val fallback = pendingTerminalError ?: terminalError
                    pendingTerminalError = null
                    fallback?.let { handleStandardPlayerError(it, message, generation) }
                }
            }
        }
    }

    private fun handleStandardPlayerError(error: PlaybackException, message: String, generation: Long) {
        if (
            playerSettings.autoRetryStreams &&
            PlayerReliability.shouldAutoRetry(error) &&
            retryCount < playerSettings.retryAttempts.coerceIn(1, PlayerReliability.MaxAutomaticRetries)
        ) {
            scheduleRetry(message, generation)
        } else {
            mutableState.value = WatchioPlayerState.Failed(message, currentMetadata)
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

internal fun PlaybackMedia.withRecoveryPosition(positionMs: Long): PlaybackMedia =
    if (isLive) copy(startPositionMs = 0L) else if (positionMs <= 0L) this else copy(startPositionMs = positionMs)

@UnstableApi
fun VideoScalingMode.toResizeMode(): Int = when (this) {
    VideoScalingMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoScalingMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    VideoScalingMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
