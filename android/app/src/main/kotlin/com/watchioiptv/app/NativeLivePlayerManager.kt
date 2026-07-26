package com.watchioiptv.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.flutter.plugin.common.StandardMessageCodec

class NativeLivePlayerManager(private val appContext: Context) : EventChannel.StreamHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessions = mutableMapOf<String, NativeLivePlayerSession>()
    private var eventSink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    fun handle(call: MethodCall, result: MethodChannel.Result) {
        val playerId = call.argument<String>("playerId")
        if (playerId.isNullOrBlank()) {
            result.error("missing_player_id", "playerId missing", null)
            return
        }

        try {
            val session = sessions.getOrPut(playerId) {
                NativeLivePlayerSession(
                    context = appContext,
                    playerId = playerId,
                    mainHandler = mainHandler,
                    emit = ::emitState,
                )
            }

            when (call.method) {
                "initialize" -> {
                    session.emit()
                    result.success(null)
                }
                "setDataSource" -> {
                    val url = call.argument<String>("url")
                    if (url.isNullOrBlank()) {
                        result.error("missing_url", "url missing", null)
                        return
                    }
                    val headers = call.argument<Map<String, String>>("headers") ?: emptyMap()
                    val startMs = call.argument<Number>("startPositionMs")?.toLong() ?: 0L
                    session.setDataSource(url, headers, startMs)
                    result.success(null)
                }
                "play" -> {
                    session.play()
                    result.success(null)
                }
                "pause" -> {
                    session.pause()
                    result.success(null)
                }
                "stop" -> {
                    session.stop()
                    result.success(null)
                }
                "seek" -> {
                    session.seek(call.argument<Number>("positionMs")?.toLong() ?: 0L)
                    result.success(null)
                }
                "setVolume" -> {
                    session.setVolume(call.argument<Number>("volume")?.toFloat() ?: 1f)
                    result.success(null)
                }
                "setAspectRatio" -> {
                    session.setAspectRatio(call.argument<String>("fit"))
                    result.success(null)
                }
                "dispose" -> {
                    session.dispose()
                    sessions.remove(playerId)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("native_live_player_error", e.message, null)
        }
    }

    fun attachView(playerId: String, playerView: PlayerView, fit: String) {
        val session = sessions.getOrPut(playerId) {
            NativeLivePlayerSession(
                context = appContext,
                playerId = playerId,
                mainHandler = mainHandler,
                emit = ::emitState,
            )
        }
        session.attachView(playerView, fit)
    }

    fun detachView(playerId: String, playerView: PlayerView) {
        sessions[playerId]?.detachView(playerView)
    }

    private fun emitState(state: Map<String, Any?>) {
        mainHandler.post {
            eventSink?.success(state)
        }
    }
}

class NativeLivePlayerSession(
    private val context: Context,
    private val playerId: String,
    private val mainHandler: Handler,
    private val emit: (Map<String, Any?>) -> Unit,
) {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var error: String? = null
    private var hasVideo = false
    private var progressLoopActive = false
    private var viewFit = "contain"

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            emit()
            scheduleProgress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            emit()
            scheduleProgress()
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            emit()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            hasVideo = videoSize.width > 0 && videoSize.height > 0
            emit()
        }

        override fun onPlayerError(error: PlaybackException) {
            this@NativeLivePlayerSession.error = error.message ?: error.errorCodeName
            emit()
        }
    }

    fun attachView(view: PlayerView, fit: String) {
        playerView = view
        view.useController = false
        view.keepScreenOn = true
        viewFit = fit
        applyResizeMode()
        view.player = player
    }

    fun detachView(view: PlayerView) {
        if (playerView === view) {
            view.player = null
            playerView = null
        }
    }

    fun setDataSource(url: String, headers: Map<String, String>, startMs: Long) {
        currentUrl = url
        currentTitle = null
        error = null
        hasVideo = false
        releasePlayerOnly()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exoPlayer ->
                exoPlayer.addListener(listener)
                exoPlayer.setMediaItem(buildMediaItem(url))
                playerView?.player = exoPlayer
                exoPlayer.prepare()
                if (startMs > 0L) exoPlayer.seekTo(startMs)
                exoPlayer.playWhenReady = true
            }
        emit()
        scheduleProgress()
    }

    fun play() {
        player?.play()
        emit()
        scheduleProgress()
    }

    fun pause() {
        player?.pause()
        emit()
    }

    fun stop() {
        currentUrl = null
        error = null
        hasVideo = false
        releasePlayerOnly()
        emit()
    }

    fun seek(positionMs: Long) {
        player?.seekTo(positionMs)
        emit()
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    fun setAspectRatio(fit: String?) {
        viewFit = fit ?: "contain"
        applyResizeMode()
    }

    fun emit() {
        val p = player
        val playbackState = p?.playbackState ?: Player.STATE_IDLE
        val selectedTracks = p?.currentTracks
        val audioSelected = selectedTracks?.groups?.any {
            it.type == C.TRACK_TYPE_AUDIO && it.isSelected
        } ?: false
        val videoSelected = selectedTracks?.groups?.any {
            it.type == C.TRACK_TYPE_VIDEO && it.isSelected
        } ?: false
        val isReady = playbackState == Player.STATE_READY
        val videoReady = hasVideo || videoSelected || (isReady && p?.isPlaying == true)
        val firstFrame = videoReady && isReady

        emit(
            mapOf(
                "playerId" to playerId,
                "isPlaying" to (p?.isPlaying ?: false),
                "isBuffering" to (playbackState == Player.STATE_BUFFERING),
                "positionMs" to (p?.currentPosition ?: 0L),
                "durationMs" to ((p?.duration ?: 0L).takeIf { it != C.TIME_UNSET } ?: 0L),
                "hasAudio" to audioSelected,
                "hasVideo" to videoReady,
                "firstFrame" to firstFrame,
                "error" to error,
            )
        )
    }

    fun dispose() {
        playerView?.player = null
        playerView = null
        releasePlayerOnly()
    }

    private fun applyResizeMode() {
        playerView?.resizeMode = when (viewFit) {
            "cover" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun scheduleProgress() {
        val p = player ?: return
        if (!p.isPlaying || progressLoopActive) return
        progressLoopActive = true
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val current = player
                if (current == null || !current.isPlaying) {
                    progressLoopActive = false
                    emit()
                    return
                }
                emit()
                mainHandler.postDelayed(this, 1000L)
            }
        }, 1000L)
    }

    private fun releasePlayerOnly() {
        progressLoopActive = false
        player?.removeListener(listener)
        player?.release()
        player = null
        playerView?.player = null
    }

    private fun buildMediaItem(url: String): MediaItem {
        val lowerUrl = url.lowercase()
        val mimeType = when {
            ".m3u8" in lowerUrl -> MimeTypes.APPLICATION_M3U8
            ".mpd" in lowerUrl -> MimeTypes.APPLICATION_MPD
            ".ts" in lowerUrl -> MimeTypes.VIDEO_MP2T
            ".mp4" in lowerUrl -> MimeTypes.VIDEO_MP4
            else -> null
        }

        val builder = MediaItem.Builder().setUri(Uri.parse(url))
        if (mimeType != null) builder.setMimeType(mimeType)
        return builder.build()
    }
}

class NativeLivePlayerViewFactory(
    private val manager: NativeLivePlayerManager,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val params = args as? Map<*, *>
        val playerId = params?.get("playerId") as? String ?: "native-live-$viewId"
        val fit = params?.get("fit") as? String ?: "contain"
        return NativeLivePlayerPlatformView(context, manager, playerId, fit)
    }
}

class NativeLivePlayerPlatformView(
    context: Context,
    private val manager: NativeLivePlayerManager,
    private val playerId: String,
    fit: String,
) : PlatformView {
    private val playerView = PlayerView(context)

    init {
        playerView.setBackgroundColor(android.graphics.Color.BLACK)
        manager.attachView(playerId, playerView, fit)
    }

    override fun getView(): View = playerView

    override fun dispose() {
        manager.detachView(playerId, playerView)
    }
}
