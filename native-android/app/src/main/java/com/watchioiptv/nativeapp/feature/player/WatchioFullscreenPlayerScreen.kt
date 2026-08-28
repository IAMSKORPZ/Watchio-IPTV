package com.watchioiptv.nativeapp.feature.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.watchioiptv.nativeapp.core.player.WatchioAudioTrack
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerMetadata
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface PlayerContentContext {
    data class Live(
        val channelName: String,
        val channelLogoUrl: String? = null,
        val programmeTitle: String? = null,
        val programmeStartTime: String? = null,
        val programmeEndTime: String? = null,
        val programmeProgress: Float? = null,
        val hasPreviousChannel: Boolean = false,
        val hasNextChannel: Boolean = false,
        val onPreviousChannel: () -> Unit = {},
        val onNextChannel: () -> Unit = {},
    ) : PlayerContentContext

    data class Movie(
        val title: String,
        val year: String? = null,
        val rating: String? = null,
        val runtime: String? = null,
        val genre: String? = null,
        val posterUrl: String? = null,
    ) : PlayerContentContext

    data class Episode(
        val seriesTitle: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val episodeTitle: String,
        val duration: String? = null,
        val posterUrl: String? = null,
        val hasPreviousEpisode: Boolean = false,
        val hasNextEpisode: Boolean = false,
        val onPreviousEpisode: () -> Unit = {},
        val onNextEpisode: () -> Unit = {},
    ) : PlayerContentContext
}

enum class PlayerDialogType {
    Audio,
    Subtitles,
    AspectRatio,
    PlaybackSpeed,
}

enum class PlayerIconKind {
    Close,
    Play,
    Pause,
    Rewind10,
    Forward10,
    Restart,
    SkipPrevious,
    SkipNext,
    Audio,
    Subtitles,
    AspectRatio,
    Speed,
    VolumeMute,
    VolumeUp,
    Back,
    Retry,
}

@Composable
fun PlayerIcon(
    kind: PlayerIconKind,
    color: Color = Color.White,
    modifier: Modifier = Modifier.size(18.dp),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.10f).coerceAtLeast(1.5f)
        when (kind) {
            PlayerIconKind.Close -> {
                drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            PlayerIconKind.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.25f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.50f)
                    lineTo(w * 0.25f, h * 0.82f)
                    close()
                }
                drawPath(path, color, style = Fill)
            }
            PlayerIconKind.Pause -> {
                val barW = w * 0.22f
                drawRoundRect(color, topLeft = Offset(w * 0.22f, h * 0.18f), size = Size(barW, h * 0.64f))
                drawRoundRect(color, topLeft = Offset(w * 0.56f, h * 0.18f), size = Size(barW, h * 0.64f))
            }
            PlayerIconKind.Rewind10 -> {
                drawArc(
                    color = color,
                    startAngle = 60f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.76f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val arrow = Path().apply {
                    moveTo(w * 0.40f, h * 0.05f)
                    lineTo(w * 0.22f, h * 0.24f)
                    lineTo(w * 0.45f, h * 0.32f)
                }
                drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            PlayerIconKind.Forward10 -> {
                drawArc(
                    color = color,
                    startAngle = 260f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.76f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val arrow = Path().apply {
                    moveTo(w * 0.60f, h * 0.05f)
                    lineTo(w * 0.78f, h * 0.24f)
                    lineTo(w * 0.55f, h * 0.32f)
                }
                drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            PlayerIconKind.Restart, PlayerIconKind.Retry -> {
                drawArc(
                    color = color,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.76f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val arrow = Path().apply {
                    moveTo(w * 0.55f, h * 0.04f)
                    lineTo(w * 0.32f, h * 0.22f)
                    lineTo(w * 0.55f, h * 0.35f)
                }
                drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            PlayerIconKind.SkipPrevious -> {
                val barW = w * 0.15f
                drawRoundRect(color, topLeft = Offset(w * 0.18f, h * 0.20f), size = Size(barW, h * 0.60f))
                val path = Path().apply {
                    moveTo(w * 0.82f, h * 0.20f)
                    lineTo(w * 0.38f, h * 0.50f)
                    lineTo(w * 0.82f, h * 0.80f)
                    close()
                }
                drawPath(path, color, style = Fill)
            }
            PlayerIconKind.SkipNext -> {
                val barW = w * 0.15f
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.20f)
                    lineTo(w * 0.62f, h * 0.50f)
                    lineTo(w * 0.18f, h * 0.80f)
                    close()
                }
                drawPath(path, color, style = Fill)
                drawRoundRect(color, topLeft = Offset(w * 0.67f, h * 0.20f), size = Size(barW, h * 0.60f))
            }
            PlayerIconKind.Audio -> {
                drawCircle(color, radius = w * 0.18f, center = Offset(w * 0.32f, h * 0.70f))
                drawCircle(color, radius = w * 0.18f, center = Offset(w * 0.72f, h * 0.60f))
                drawLine(color, Offset(w * 0.44f, h * 0.70f), Offset(w * 0.44f, h * 0.25f), strokeWidth = stroke)
                drawLine(color, Offset(w * 0.84f, h * 0.60f), Offset(w * 0.84f, h * 0.15f), strokeWidth = stroke)
                drawLine(color, Offset(w * 0.44f, h * 0.25f), Offset(w * 0.84f, h * 0.15f), strokeWidth = stroke * 1.5f)
            }
            PlayerIconKind.Subtitles -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.10f, h * 0.20f),
                    size = Size(w * 0.80f, h * 0.60f),
                    style = Stroke(width = stroke),
                )
                drawLine(color, Offset(w * 0.24f, h * 0.42f), Offset(w * 0.46f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.54f, h * 0.42f), Offset(w * 0.76f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.58f), Offset(w * 0.62f, h * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            PlayerIconKind.AspectRatio -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.22f),
                    size = Size(w * 0.76f, h * 0.56f),
                    style = Stroke(width = stroke),
                )
                drawLine(color, Offset(w * 0.12f, h * 0.35f), Offset(w * 0.25f, h * 0.22f), strokeWidth = stroke)
                drawLine(color, Offset(w * 0.88f, h * 0.65f), Offset(w * 0.75f, h * 0.78f), strokeWidth = stroke)
            }
            PlayerIconKind.Speed -> {
                drawArc(
                    color = color,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.14f),
                    size = Size(w * 0.72f, h * 0.72f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawLine(color, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.72f, h * 0.32f), strokeWidth = stroke * 1.3f, cap = StrokeCap.Round)
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.54f))
            }
            PlayerIconKind.VolumeMute -> {
                val spk = Path().apply {
                    moveTo(w * 0.12f, h * 0.38f)
                    lineTo(w * 0.30f, h * 0.38f)
                    lineTo(w * 0.50f, h * 0.20f)
                    lineTo(w * 0.50f, h * 0.80f)
                    lineTo(w * 0.30f, h * 0.62f)
                    lineTo(w * 0.12f, h * 0.62f)
                    close()
                }
                drawPath(spk, color, style = Fill)
                drawLine(color, Offset(w * 0.62f, h * 0.38f), Offset(w * 0.86f, h * 0.62f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.38f), Offset(w * 0.62f, h * 0.62f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            PlayerIconKind.VolumeUp -> {
                val spk = Path().apply {
                    moveTo(w * 0.12f, h * 0.38f)
                    lineTo(w * 0.30f, h * 0.38f)
                    lineTo(w * 0.50f, h * 0.20f)
                    lineTo(w * 0.50f, h * 0.80f)
                    lineTo(w * 0.30f, h * 0.62f)
                    lineTo(w * 0.12f, h * 0.62f)
                    close()
                }
                drawPath(spk, color, style = Fill)
                drawArc(
                    color = color,
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.40f, h * 0.30f),
                    size = Size(w * 0.38f, h * 0.40f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = -50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(w * 0.50f, h * 0.20f),
                    size = Size(w * 0.45f, h * 0.60f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            PlayerIconKind.Back -> {
                drawLine(color, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.78f, h * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.48f, h * 0.26f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.48f, h * 0.74f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun PlayerControlButton(
    title: String,
    icon: PlayerIconKind,
    modifier: Modifier = Modifier,
    accent: Color = LocalWatchioColors.current.focusGlow,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String = title,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val height: Dp = if (isPrimary) 52.dp else 42.dp
    val hPadding: Dp = if (isPrimary) 20.dp else 14.dp
    val iconSize: Dp = if (isPrimary) 22.dp else 16.dp
    val fontSize = if (isPrimary) 16.sp else 13.sp
    val fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium

    Surface(
        modifier = modifier
            .height(height)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) colors.focusBorder else if (enabled) colors.surfaceElevated else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource, enabled = enabled)
            .semantics { this.contentDescription = contentDescription },
        color = if (focused) accent.copy(alpha = if (isPrimary) 0.35f else 0.22f) else if (isPrimary) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = if (enabled) 0.08f else 0.03f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = hPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerIcon(
                kind = icon,
                color = if (enabled) (if (focused) colors.textPrimary else colors.textPrimary.copy(alpha = 0.90f)) else colors.textMuted,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = title,
                color = if (enabled) (if (focused) colors.textPrimary else colors.textPrimary.copy(alpha = 0.90f)) else colors.textMuted,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun WatchioFullscreenPlayerScreen(
    playerState: WatchioPlayerState,
    playerSettings: PlayerSettings,
    playerManager: WatchioPlayerManager,
    contentContext: PlayerContentContext,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    var controlsVisible by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf<PlayerDialogType?>(null) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackJob by remember { mutableStateOf<Job?>(null) }
    var lastInteractionEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val coroutineScope = rememberCoroutineScope()

    val firstFocus = remember { FocusRequester() }
    val surfaceFocus = remember { FocusRequester() }

    // Authoritative reactive metadata from player state
    val metadata = playerState.metadata
    var currentPositionMs by remember { mutableLongStateOf(metadata.positionMs) }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            firstFocus.requestFocus()
        } else {
            surfaceFocus.requestFocus()
        }
    }

    // Lightweight position timer (only updates positionMs, does not re-extract tracks)
    LaunchedEffect(controlsVisible, playerState) {
        if (controlsVisible) {
            while (true) {
                currentPositionMs = playerManager.snapshot().positionMs
                delay(500L)
            }
        }
    }

    // Auto-hide timer: only when playing, idle, and no dialog open
    LaunchedEffect(controlsVisible, playerState, activeDialog, lastInteractionEpochMs) {
        if (controlsVisible && playerState is WatchioPlayerState.Playing && activeDialog == null) {
            val delaySeconds = when (playerSettings.controlAutoHideDelay) {
                ControlAutoHideDelay.ThreeSeconds -> 3L
                ControlAutoHideDelay.FiveSeconds -> 5L
                ControlAutoHideDelay.EightSeconds -> 8L
                ControlAutoHideDelay.Never -> -1L
            }
            if (delaySeconds > 0) {
                delay(delaySeconds * 1_000L)
                controlsVisible = false
            }
        }
    }

    fun triggerSeek(deltaMs: Long) {
        lastInteractionEpochMs = System.currentTimeMillis()
        onSeek(deltaMs)
        seekFeedbackText = if (deltaMs < 0) "-10s" else "+10s"
        seekFeedbackJob?.cancel()
        seekFeedbackJob = coroutineScope.launch {
            delay(1000L)
            seekFeedbackText = null
        }
    }

    BackHandler(enabled = true) {
        lastInteractionEpochMs = System.currentTimeMillis()
        if (activeDialog != null) {
            activeDialog = null
        } else if (controlsVisible) {
            controlsVisible = false
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("fullscreen-player")
            .focusRequester(surfaceFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInteractionEpochMs = System.currentTimeMillis()
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (!controlsVisible && (metadata.isSeekable || contentContext !is PlayerContentContext.Live)) {
                            triggerSeek(-10_000L)
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (!controlsVisible && (metadata.isSeekable || contentContext !is PlayerContentContext.Live)) {
                            triggerSeek(10_000L)
                            true
                        } else false
                    }
                    Key.Spacebar -> {
                        onPlayPause()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            firstFocus.requestFocus()
                            true
                        } else false
                    }
                    Key.Escape, Key.Back -> {
                        if (activeDialog != null) {
                            activeDialog = null
                            true
                        } else if (controlsVisible) {
                            controlsVisible = false
                            true
                        } else {
                            onClose()
                            true
                        }
                    }
                    else -> false
                }
            }
            .clickable {
                lastInteractionEpochMs = System.currentTimeMillis()
                controlsVisible = !controlsVisible
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> FrameLayout(ctx).also { playerManager.attachSurface(it) } },
            update = { playerManager.attachSurface(it) },
            onRelease = { playerManager.detachSurface(it) },
        )

        // Center HUD: Seek feedback badge
        seekFeedbackText?.let { feedback ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .testTag("player-seek-feedback"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayerIcon(
                        kind = if (feedback.startsWith("-")) PlayerIconKind.Rewind10 else PlayerIconKind.Forward10,
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = feedback,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Center HUD: Buffering / Connecting
        if (playerState is WatchioPlayerState.Buffering || playerState is WatchioPlayerState.Connecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.60f), shape = CircleShape)
                    .padding(24.dp)
                    .testTag("player-buffering"),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = colors.liveTvAccent,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        // Center HUD: Recovering / Reconnecting (non-terminal compact indicator)
        if (playerState is WatchioPlayerState.Recovering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("player-recovering"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = colors.liveTvAccent, modifier = Modifier.size(24.dp))
                    Text(text = "Reconnectingâ€¦", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Center HUD: Actual Error / Failure state (Terminal with Retry & Back)
        if (playerState is WatchioPlayerState.Failed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .testTag("player-error"),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = playerState.message, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerControlButton(
                            title = "Retry",
                            icon = PlayerIconKind.Retry,
                            accent = colors.liveTvAccent,
                            onClick = onRetry,
                            modifier = Modifier.testTag("player-retry"),
                        )
                        PlayerControlButton(
                            title = "Back",
                            icon = PlayerIconKind.Back,
                            accent = colors.focusGlow,
                            onClick = onClose,
                            modifier = Modifier.testTag("player-error-back"),
                        )
                    }
                }
            }
        }

        // Overlays: Top Header and Bottom Control Deck
        if (controlsVisible) {
            // Top Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag("player-top-bar"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (contentContext) {
                        is PlayerContentContext.Live -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                contentContext.channelLogoUrl?.takeIf { it.isNotBlank() }?.let { logoUrl ->
                                    AsyncImage(
                                        model = logoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = contentContext.channelName,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("player-header-title"),
                                    )
                                    contentContext.programmeTitle?.let { prog ->
                                        Text(
                                            text = prog,
                                            color = colors.textSecondary,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("player-header-subtitle"),
                                        )
                                    }
                                }
                            }
                        }
                        is PlayerContentContext.Movie -> {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contentContext.title,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("player-header-title"),
                                )
                                val metaList = listOfNotNull(contentContext.year, contentContext.rating, contentContext.runtime, contentContext.genre)
                                    .filter { it.isNotBlank() }
                                if (metaList.isNotEmpty()) {
                                    Text(
                                        text = metaList.joinToString("  â€¢  "),
                                        color = colors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("player-header-meta"),
                                    )
                                }
                            }
                        }
                        is PlayerContentContext.Episode -> {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contentContext.seriesTitle,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("player-header-title"),
                                )
                                val epSubtitle = "S${contentContext.seasonNumber} â€¢ E${contentContext.episodeNumber}  ${contentContext.episodeTitle}"
                                Text(
                                    text = epSubtitle,
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("player-header-subtitle"),
                                )
                            }
                        }
                    }

                    PlayerControlButton(
                        title = "Close",
                        icon = PlayerIconKind.Close,
                        accent = colors.focusGlow,
                        contentDescription = "Close player",
                        onClick = onClose,
                        modifier = Modifier.testTag("player-close-button"),
                    )
                }
            }

            // Bottom Control Deck
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f)),
                        ),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag("player-bottom-deck"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Timeline
                when (contentContext) {
                    is PlayerContentContext.Live -> {
                        if (metadata.isSeekable && metadata.durationMs != null && metadata.durationMs > 0L) {
                            VodTimeline(
                                currentPositionMs = currentPositionMs,
                                durationMs = metadata.durationMs,
                                accentColor = colors.liveTvAccent,
                            )
                        } else {
                            LiveEpgTimeline(
                                startTime = contentContext.programmeStartTime,
                                endTime = contentContext.programmeEndTime,
                                progress = contentContext.programmeProgress ?: 0f,
                                accentColor = colors.liveTvAccent,
                            )
                        }
                    }
                    is PlayerContentContext.Movie -> {
                        VodTimeline(
                            currentPositionMs = currentPositionMs,
                            durationMs = metadata.durationMs ?: 0L,
                            accentColor = colors.moviesAccent,
                        )
                    }
                    is PlayerContentContext.Episode -> {
                        VodTimeline(
                            currentPositionMs = currentPositionMs,
                            durationMs = metadata.durationMs ?: 0L,
                            accentColor = colors.seriesAccent,
                        )
                    }
                }

                // Primary Controls Row (Responsive wrapping / adaptive spacing)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (contentContext) {
                            is PlayerContentContext.Live -> {
                                if (contentContext.hasPreviousChannel) {
                                    PlayerControlButton(
                                        title = "Prev Channel",
                                        icon = PlayerIconKind.SkipPrevious,
                                        accent = colors.focusGlow,
                                        contentDescription = "Previous channel",
                                        onClick = {
                                            lastInteractionEpochMs = System.currentTimeMillis()
                                            contentContext.onPreviousChannel()
                                        },
                                        modifier = Modifier.testTag("player-prev-channel"),
                                    )
                                }
                                if (metadata.isSeekable) {
                                    PlayerControlButton(
                                        title = "10s",
                                        icon = PlayerIconKind.Rewind10,
                                        accent = colors.focusGlow,
                                        contentDescription = "Rewind 10 seconds",
                                        onClick = { triggerSeek(-10_000L) },
                                        modifier = Modifier.testTag("player-rewind"),
                                    )
                                }
                                PlayerControlButton(
                                    title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    icon = if (playerState is WatchioPlayerState.Playing) PlayerIconKind.Pause else PlayerIconKind.Play,
                                    accent = colors.liveTvAccent,
                                    isPrimary = true,
                                    contentDescription = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onPlayPause()
                                    },
                                    modifier = Modifier
                                        .focusRequester(firstFocus)
                                        .testTag("player-play-pause"),
                                )
                                if (metadata.isSeekable) {
                                    PlayerControlButton(
                                        title = "10s",
                                        icon = PlayerIconKind.Forward10,
                                        accent = colors.focusGlow,
                                        contentDescription = "Fast forward 10 seconds",
                                        onClick = { triggerSeek(10_000L) },
                                        modifier = Modifier.testTag("player-forward"),
                                    )
                                }
                                if (contentContext.hasNextChannel) {
                                    PlayerControlButton(
                                        title = "Next Channel",
                                        icon = PlayerIconKind.SkipNext,
                                        accent = colors.focusGlow,
                                        contentDescription = "Next channel",
                                        onClick = {
                                            lastInteractionEpochMs = System.currentTimeMillis()
                                            contentContext.onNextChannel()
                                        },
                                        modifier = Modifier.testTag("player-next-channel"),
                                    )
                                }
                            }
                            is PlayerContentContext.Movie -> {
                                PlayerControlButton(
                                    title = "Restart",
                                    icon = PlayerIconKind.Restart,
                                    accent = colors.seriesAccent,
                                    contentDescription = "Restart",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onRestart()
                                    },
                                    modifier = Modifier.testTag("player-restart"),
                                )
                                PlayerControlButton(
                                    title = "10s",
                                    icon = PlayerIconKind.Rewind10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Rewind 10 seconds",
                                    onClick = { triggerSeek(-10_000L) },
                                    modifier = Modifier.testTag("player-rewind"),
                                )
                                PlayerControlButton(
                                    title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    icon = if (playerState is WatchioPlayerState.Playing) PlayerIconKind.Pause else PlayerIconKind.Play,
                                    accent = colors.moviesAccent,
                                    isPrimary = true,
                                    contentDescription = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onPlayPause()
                                    },
                                    modifier = Modifier
                                        .focusRequester(firstFocus)
                                        .testTag("player-play-pause"),
                                )
                                PlayerControlButton(
                                    title = "10s",
                                    icon = PlayerIconKind.Forward10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Fast forward 10 seconds",
                                    onClick = { triggerSeek(10_000L) },
                                    modifier = Modifier.testTag("player-forward"),
                                )
                            }
                            is PlayerContentContext.Episode -> {
                                if (contentContext.hasPreviousEpisode) {
                                    PlayerControlButton(
                                        title = "Prev Ep",
                                        icon = PlayerIconKind.SkipPrevious,
                                        accent = colors.focusGlow,
                                        contentDescription = "Previous episode",
                                        onClick = {
                                            lastInteractionEpochMs = System.currentTimeMillis()
                                            contentContext.onPreviousEpisode()
                                        },
                                        modifier = Modifier.testTag("player-prev-episode"),
                                    )
                                }
                                PlayerControlButton(
                                    title = "10s",
                                    icon = PlayerIconKind.Rewind10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Rewind 10 seconds",
                                    onClick = { triggerSeek(-10_000L) },
                                    modifier = Modifier.testTag("player-rewind"),
                                )
                                PlayerControlButton(
                                    title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    icon = if (playerState is WatchioPlayerState.Playing) PlayerIconKind.Pause else PlayerIconKind.Play,
                                    accent = colors.seriesAccent,
                                    isPrimary = true,
                                    contentDescription = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onPlayPause()
                                    },
                                    modifier = Modifier
                                        .focusRequester(firstFocus)
                                        .testTag("player-play-pause"),
                                )
                                PlayerControlButton(
                                    title = "10s",
                                    icon = PlayerIconKind.Forward10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Fast forward 10 seconds",
                                    onClick = { triggerSeek(10_000L) },
                                    modifier = Modifier.testTag("player-forward"),
                                )
                                if (contentContext.hasNextEpisode) {
                                    PlayerControlButton(
                                        title = "Next Ep",
                                        icon = PlayerIconKind.SkipNext,
                                        accent = colors.focusGlow,
                                        contentDescription = "Next episode",
                                        onClick = {
                                            lastInteractionEpochMs = System.currentTimeMillis()
                                            contentContext.onNextEpisode()
                                        },
                                        modifier = Modifier.testTag("player-next-episode"),
                                    )
                                }
                            }
                        }
                    }
                }

                // Secondary Controls Row (Horizontal scroll / wrapping protection)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val audioLabel = if (metadata.audioTracks.isEmpty()) "Audio unavailable" else (metadata.selectedAudioTrack?.label ?: "Audio")
                        PlayerControlButton(
                            title = audioLabel,
                            icon = PlayerIconKind.Audio,
                            accent = colors.focusGlow,
                            enabled = metadata.audioTracks.isNotEmpty(),
                            contentDescription = "Audio tracks",
                            onClick = {
                                lastInteractionEpochMs = System.currentTimeMillis()
                                activeDialog = PlayerDialogType.Audio
                            },
                            modifier = Modifier.testTag("player-audio-button"),
                        )

                        val subLabel = if (metadata.subtitleTracks.isEmpty()) "No subtitles" else (metadata.selectedSubtitleTrack?.label ?: "Subtitles: Off")
                        PlayerControlButton(
                            title = subLabel,
                            icon = PlayerIconKind.Subtitles,
                            accent = colors.focusGlow,
                            contentDescription = "Subtitles",
                            onClick = {
                                lastInteractionEpochMs = System.currentTimeMillis()
                                activeDialog = PlayerDialogType.Subtitles
                            },
                            modifier = Modifier.testTag("player-subtitles-button"),
                        )

                        PlayerControlButton(
                            title = "Aspect: ${metadata.videoScalingMode.label}",
                            icon = PlayerIconKind.AspectRatio,
                            accent = colors.focusGlow,
                            contentDescription = "Aspect ratio",
                            onClick = {
                                lastInteractionEpochMs = System.currentTimeMillis()
                                activeDialog = PlayerDialogType.AspectRatio
                            },
                            modifier = Modifier.testTag("player-aspect-button"),
                        )

                        if (contentContext !is PlayerContentContext.Live) {
                            PlayerControlButton(
                                title = "Speed: ${metadata.playbackSpeed}x",
                                icon = PlayerIconKind.Speed,
                                accent = colors.focusGlow,
                                contentDescription = "Playback speed",
                                onClick = {
                                    lastInteractionEpochMs = System.currentTimeMillis()
                                    activeDialog = PlayerDialogType.PlaybackSpeed
                                },
                                modifier = Modifier.testTag("player-speed-button"),
                            )
                        }

                        PlayerControlButton(
                            title = if (metadata.isMuted) "Unmute" else "Mute",
                            icon = if (metadata.isMuted) PlayerIconKind.VolumeMute else PlayerIconKind.VolumeUp,
                            accent = colors.focusGlow,
                            contentDescription = if (metadata.isMuted) "Unmute" else "Mute",
                            onClick = {
                                lastInteractionEpochMs = System.currentTimeMillis()
                                playerManager.setMuted(!metadata.isMuted)
                            },
                            modifier = Modifier.testTag("player-mute-button"),
                        )

                        PlayerControlButton(
                            title = "Back",
                            icon = PlayerIconKind.Back,
                            accent = colors.focusGlow,
                            contentDescription = "Back",
                            onClick = onClose,
                            modifier = Modifier.testTag("player-back-button"),
                        )
                    }
                }
            }
        }

        // Dialogs with TV focus support
        when (activeDialog) {
            PlayerDialogType.Audio -> {
                AudioTracksDialog(
                    tracks = metadata.audioTracks,
                    selectedTrack = metadata.selectedAudioTrack,
                    onSelect = { track ->
                        playerManager.selectAudioTrack(track)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }
            PlayerDialogType.Subtitles -> {
                SubtitleTracksDialog(
                    tracks = metadata.subtitleTracks,
                    selectedTrack = metadata.selectedSubtitleTrack,
                    onSelect = { track ->
                        playerManager.selectSubtitleTrack(track)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }
            PlayerDialogType.AspectRatio -> {
                AspectRatioDialog(
                    currentMode = metadata.videoScalingMode,
                    onSelect = { mode ->
                        playerManager.setVideoScalingMode(mode)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }
            PlayerDialogType.PlaybackSpeed -> {
                PlaybackSpeedDialog(
                    currentSpeed = metadata.playbackSpeed,
                    onSelect = { speed ->
                        playerManager.setPlaybackSpeed(speed)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }
            null -> Unit
        }
    }
}

@Composable
private fun VodTimeline(
    currentPositionMs: Long,
    durationMs: Long,
    accentColor: Color,
) {
    val colors = LocalWatchioColors.current
    val progress = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = formatPlaybackTime(currentPositionMs),
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.testTag("player-position"),
        )
        LinearProgressIndicator(
            progress = { progress },
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .testTag("player-progress-bar"),
        )
        Text(
            text = formatPlaybackTime(durationMs),
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.testTag("player-duration"),
        )
    }
}

@Composable
private fun LiveEpgTimeline(
    startTime: String?,
    endTime: String?,
    progress: Float,
    accentColor: Color,
) {
    val colors = LocalWatchioColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("player-live-epg-progress"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!startTime.isNullOrBlank()) {
            Text(text = startTime, color = colors.textSecondary, fontSize = 13.sp)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
        )
        if (!endTime.isNullOrBlank()) {
            Text(text = endTime, color = colors.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AudioTracksDialog(
    tracks: List<WatchioAudioTrack>,
    selectedTrack: WatchioAudioTrack?,
    onSelect: (WatchioAudioTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Audio Track") },
        text = {
            if (tracks.isEmpty()) {
                Text("No audio tracks found for this stream.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(tracks, key = { it.id }) { track ->
                        val isSelected = selectedTrack?.id == track.id || (selectedTrack == null && track.isSelected)
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                                .clickable(interactionSource = interactionSource, indication = null) { onSelect(track) }
                                .focusable(interactionSource = interactionSource)
                                .background(if (focused) LocalWatchioColors.current.focusGlow.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(track) },
                                colors = RadioButtonDefaults.colors(selectedColor = LocalWatchioColors.current.focusGlow),
                            )
                            Text(text = track.label, color = Color.White, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("player-dialog-close")) { Text("Close") } },
        modifier = Modifier.testTag("player-audio-dialog"),
    )
}

@Composable
private fun SubtitleTracksDialog(
    tracks: List<WatchioSubtitleTrack>,
    selectedTrack: WatchioSubtitleTrack?,
    onSelect: (WatchioSubtitleTrack?) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Subtitles") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    val isOffSelected = selectedTrack == null
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isOffSelected) Modifier.focusRequester(firstFocus) else Modifier)
                            .clickable(interactionSource = interactionSource, indication = null) { onSelect(null) }
                            .focusable(interactionSource = interactionSource)
                            .background(if (focused) LocalWatchioColors.current.focusGlow.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = isOffSelected,
                            onClick = { onSelect(null) },
                            colors = RadioButtonDefaults.colors(selectedColor = LocalWatchioColors.current.focusGlow),
                        )
                        Text(text = "Off", color = Color.White, fontSize = 15.sp, fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            text = "No subtitle tracks available for this stream.",
                            color = LocalWatchioColors.current.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(tracks, key = { it.id }) { track ->
                        val isSelected = selectedTrack?.id == track.id
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                                .clickable(interactionSource = interactionSource, indication = null) { onSelect(track) }
                                .focusable(interactionSource = interactionSource)
                                .background(if (focused) LocalWatchioColors.current.focusGlow.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(track) },
                                colors = RadioButtonDefaults.colors(selectedColor = LocalWatchioColors.current.focusGlow),
                            )
                            Text(text = track.label, color = Color.White, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("player-dialog-close")) { Text("Close") } },
        modifier = Modifier.testTag("player-subtitles-dialog"),
    )
}

@Composable
private fun AspectRatioDialog(
    currentMode: VideoScalingMode,
    onSelect: (VideoScalingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aspect Ratio") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                VideoScalingMode.entries.forEach { mode ->
                    val isSelected = currentMode == mode
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                            .clickable(interactionSource = interactionSource, indication = null) { onSelect(mode) }
                            .focusable(interactionSource = interactionSource)
                            .background(if (focused) LocalWatchioColors.current.focusGlow.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = LocalWatchioColors.current.focusGlow),
                        )
                        Text(text = mode.label, color = Color.White, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("player-dialog-close")) { Text("Close") } },
        modifier = Modifier.testTag("player-aspect-dialog"),
    )
}

@Composable
private fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                speeds.forEach { speed ->
                    val isSelected = (currentSpeed - speed).let { if (it < 0) -it else it } < 0.05f
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                            .clickable(interactionSource = interactionSource, indication = null) { onSelect(speed) }
                            .focusable(interactionSource = interactionSource)
                            .background(if (focused) LocalWatchioColors.current.focusGlow.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(speed) },
                            colors = RadioButtonDefaults.colors(selectedColor = LocalWatchioColors.current.focusGlow),
                        )
                        Text(text = "${speed}x", color = Color.White, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("player-dialog-close")) { Text("Close") } },
        modifier = Modifier.testTag("player-speed-dialog"),
    )
}

fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}