package com.watchioiptv.nativeapp.core.player

import android.content.Context
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutableState = MutableStateFlow<WatchioPlayerState>(WatchioPlayerState.Idle(currentMetadata))
    override val state: StateFlow<WatchioPlayerState> = mutableState

    init {
        scope.launch {
            settingsRepository.playerSettings.collect { settings ->
                playerSettings = settings
                playerView?.resizeMode = settings.videoScalingMode.toResizeMode()
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
            firstFrameRendered = false,
            hasVideo = false,
            hasAudio = false,
            loadGeneration = loadGeneration,
        )
        mutableState.value = WatchioPlayerState.Connecting(currentMetadata)
        loadIntoPlayer(media, currentMetadata.loadGeneration)
    }

    private fun loadIntoPlayer(media: PlaybackMedia, generation: Long) {
        val exoPlayer = ensurePlayer()
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
        currentMetadata = currentMetadata.copy(currentMedia = null, positionMs = 0L, durationMs = null, firstFrameRendered = false)
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
            .let { target -> if (snapshot.currentMedia?.isLive == true) target else snapshot.durationMs?.let { target.coerceAtMost(it) } ?: target }
        player?.seekTo(safePosition)
        currentMetadata = snapshot.copy(positionMs = safePosition)
        mutableState.value = when (mutableState.value) {
            is WatchioPlayerState.Playing -> WatchioPlayerState.Playing(currentMetadata)
            is WatchioPlayerState.Paused -> WatchioPlayerState.Paused(currentMetadata)
            else -> mutableState.value
        }
    }

    override fun snapshot(): WatchioPlayerMetadata {
        val exoPlayer = player
        val duration = exoPlayer?.duration?.takeIf { it != C.TIME_UNSET && it >= 0L }
        return currentMetadata.copy(
            positionMs = exoPlayer?.currentPosition ?: currentMetadata.positionMs,
            durationMs = duration,
        )
    }

    override fun attachSurface(container: ViewGroup) {
        val view = playerView ?: PlayerView(context).also {
            it.useController = false
            it.resizeMode = playerSettings.videoScalingMode.toResizeMode()
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
private fun VideoScalingMode.toResizeMode(): Int = when (this) {
    VideoScalingMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoScalingMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    VideoScalingMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
