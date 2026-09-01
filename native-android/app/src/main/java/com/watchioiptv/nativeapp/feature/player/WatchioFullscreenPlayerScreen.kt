package com.watchioiptv.nativeapp.feature.player

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.platform.LocalContext
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
import com.watchioiptv.nativeapp.core.player.shouldKeepScreenOn
import com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack
import com.watchioiptv.nativeapp.data.series.NextEpisodeState
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

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
        val onChannelsClick: () -> Unit = {},
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
        val nextEpisodeState: NextEpisodeState = NextEpisodeState.None,
        val onPlayNext: () -> Unit = {},
        val onCancelNext: () -> Unit = {},
    ) : PlayerContentContext
}

enum class PlayerDialogType {
    Audio,
    Subtitles,
    AspectRatio,
    PlaybackSpeed,
    Settings,
}

enum class PlayerIconKind {
    Close,
    Channels,
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
    Settings,
    Back,
    Retry,
}

@Composable
fun PlayerIcon(
    kind: PlayerIconKind,
    color: Color = Color.White,
    modifier: Modifier = Modifier.size(20.dp),
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
            PlayerIconKind.Channels -> {
                val y1 = h * 0.28f
                val y2 = h * 0.50f
                val y3 = h * 0.72f
                val dotR = w * 0.07f
                drawCircle(color, dotR, Offset(w * 0.20f, y1))
                drawCircle(color, dotR, Offset(w * 0.20f, y2))
                drawCircle(color, dotR, Offset(w * 0.20f, y3))
                drawLine(color, Offset(w * 0.38f, y1), Offset(w * 0.82f, y1), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.38f, y2), Offset(w * 0.82f, y2), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.38f, y3), Offset(w * 0.82f, y3), strokeWidth = stroke, cap = StrokeCap.Round)
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
                val barW = w * 0.14f
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
                val barW = w * 0.14f
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.20f)
                    lineTo(w * 0.62f, h * 0.50f)
                    lineTo(w * 0.18f, h * 0.80f)
                    close()
                }
                drawPath(path, color, style = Fill)
                drawRoundRect(color, topLeft = Offset(w * 0.68f, h * 0.20f), size = Size(barW, h * 0.60f))
            }
            PlayerIconKind.Audio -> {
                drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.32f, h * 0.70f))
                drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.72f, h * 0.60f))
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
            PlayerIconKind.Settings -> {
                drawCircle(color, radius = w * 0.18f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(width = stroke))
                for (i in 0 until 6) {
                    val angle = (i * 60.0) * Math.PI / 180.0
                    val cx = w * 0.50f + (w * 0.32f * cos(angle)).toFloat()
                    val cy = h * 0.50f + (h * 0.32f * sin(angle)).toFloat()
                    drawCircle(color, radius = w * 0.07f, center = Offset(cx, cy))
                }
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
fun PlayerControlItem(
    title: String,
    icon: PlayerIconKind,
    modifier: Modifier = Modifier,
    accent: Color = LocalWatchioColors.current.focusGlow,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String = title,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val iconCircleSize: Dp = if (isPrimary) 54.dp else 44.dp
    val iconSize: Dp = if (isPrimary) 26.dp else 20.dp
    val fontSize = if (isPrimary) 13.5.sp else 12.5.sp
    val fontWeight = if (focused || isPrimary) FontWeight.Bold else FontWeight.Normal

    Column(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource, enabled = enabled)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconCircleSize)
                .background(
                    color = if (focused) {
                        accent.copy(alpha = if (isPrimary) 0.40f else 0.30f)
                    } else {
                        Color.White.copy(alpha = if (isPrimary) 0.08f else 0.035f)
                    },
                    shape = CircleShape,
                )
                .border(
                    width = if (focused) 2.5.dp else 0.dp,
                    color = if (focused) colors.focusBorder else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            PlayerIcon(
                kind = icon,
                color = if (enabled) (if (focused) Color.White else Color.White.copy(alpha = 0.90f)) else colors.textMuted,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = title,
            color = if (enabled) (if (focused) colors.textPrimary else colors.textSecondary) else colors.textMuted,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveChannelSwitchHud(
    contentContext: PlayerContentContext.Live,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    Box(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.72f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                ),
                shape = RoundedCornerShape(12.dp),
            )
            .border(1.dp, colors.liveTvAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag("player-channel-hud"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            contentContext.channelLogoUrl?.takeIf { it.isNotBlank() }?.let { logoUrl ->
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = contentContext.channelName,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("player-channel-hud-name"),
                )
                contentContext.programmeTitle?.let { prog ->
                    Text(
                        text = prog,
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("player-channel-hud-prog"),
                    )
                }
            }
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
    val context = LocalContext.current
    val colors = LocalWatchioColors.current
    val isTvDevice = remember(context) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var channelHudVisible by remember { mutableStateOf(false) }
    var channelHudJob by remember { mutableStateOf<Job?>(null) }
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
        if (contentContext !is PlayerContentContext.Episode || contentContext.nextEpisodeState is NextEpisodeState.None) {
            if (controlsVisible) {
                firstFocus.requestFocus()
            } else {
                surfaceFocus.requestFocus()
            }
        }
    }

    // Lightweight position timer (only updates positionMs)
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

    fun triggerChannelSwitch(previous: Boolean) {
        lastInteractionEpochMs = System.currentTimeMillis()
        if (contentContext !is PlayerContentContext.Live) return
        if (previous) {
            contentContext.onPreviousChannel()
        } else {
            contentContext.onNextChannel()
        }
        // Rapid channel surfing: keep full controls hidden, show lightweight transient HUD
        controlsVisible = false
        channelHudVisible = true
        channelHudJob?.cancel()
        channelHudJob = coroutineScope.launch {
            delay(2500L)
            channelHudVisible = false
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

    val screenMountEpochMs = remember { System.currentTimeMillis() }

    BackHandler(enabled = true) {
        if (System.currentTimeMillis() - screenMountEpochMs < 500L) {
            // Guard against back-release from long-press transition that opened fullscreen
            return@BackHandler
        }
        lastInteractionEpochMs = System.currentTimeMillis()
        if (contentContext is PlayerContentContext.Episode && contentContext.nextEpisodeState !is NextEpisodeState.None) {
            contentContext.onCancelNext()
        } else if (activeDialog != null) {
            activeDialog = null
        } else if (controlsVisible) {
            controlsVisible = false
        } else if (channelHudVisible) {
            channelHudJob?.cancel()
            channelHudVisible = false
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
                    Key.DirectionUp -> {
                        if (!controlsVisible && contentContext is PlayerContentContext.Live) {
                            triggerChannelSwitch(previous = true)
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (!controlsVisible && contentContext is PlayerContentContext.Live) {
                            triggerChannelSwitch(previous = false)
                            true
                        } else false
                    }
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
                            channelHudJob?.cancel()
                            channelHudVisible = false
                            controlsVisible = true
                            firstFocus.requestFocus()
                            true
                        } else false
                    }
                    Key.Escape, Key.Back -> {
                        val nativeEvent = event.nativeKeyEvent
                        if (nativeEvent.repeatCount > 0) {
                            // Ignore repeated auto-fire while back key is held down
                            return@onPreviewKeyEvent true
                        }
                        if (System.currentTimeMillis() - screenMountEpochMs < 500L) {
                            // Ignore back key from long-press transition that launched fullscreen
                            return@onPreviewKeyEvent true
                        }
                        if (contentContext is PlayerContentContext.Episode && contentContext.nextEpisodeState !is NextEpisodeState.None) {
                            contentContext.onCancelNext()
                            true
                        } else if (activeDialog != null) {
                            activeDialog = null
                            true
                        } else if (controlsVisible) {
                            controlsVisible = false
                            true
                        } else if (channelHudVisible) {
                            channelHudJob?.cancel()
                            channelHudVisible = false
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
                if (!controlsVisible) {
                    channelHudJob?.cancel()
                    channelHudVisible = false
                    controlsVisible = true
                } else {
                    controlsVisible = false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> FrameLayout(ctx).also { view ->
                view.keepScreenOn = shouldKeepScreenOn(playerState)
                playerManager.attachSurface(view)
            } },
            update = { view ->
                view.keepScreenOn = shouldKeepScreenOn(playerState)
                playerManager.attachSurface(view)
            },
            onRelease = { view ->
                view.keepScreenOn = false
                playerManager.detachSurface(view)
            },
        )

        // Top-left Lightweight Transient Channel Switch HUD (Surfing mode)
        if (channelHudVisible && !controlsVisible && contentContext is PlayerContentContext.Live) {
            LiveChannelSwitchHud(
                contentContext = contentContext,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = 28.dp),
            )
        }

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
                    Text(text = "Reconnecting...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PlayerControlItem(
                            title = "Retry",
                            icon = PlayerIconKind.Retry,
                            accent = colors.liveTvAccent,
                            onClick = onRetry,
                            modifier = Modifier.testTag("player-retry"),
                        )
                        PlayerControlItem(
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

        // Controls overlay: Subtle top close + bottom control deck with soft progressive gradient
        if (controlsVisible) {
            // Subtle top close button for touch/mobile devices
            if (!isTvDevice) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                            .clickable(onClick = onClose)
                            .testTag("player-close-button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayerIcon(kind = PlayerIconKind.Close, color = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Bottom Control Deck with soft, translucent progressive gradient and raised bottom breathing room
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.30f to Color.Black.copy(alpha = 0.20f),
                            0.65f to Color.Black.copy(alpha = 0.55f),
                            1.0f to Color.Black.copy(alpha = 0.78f),
                        ),
                    )
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp)
                    .testTag("player-bottom-deck"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Subtle Content Information Header (above timeline)
                when (contentContext) {
                    is PlayerContentContext.Live -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            contentContext.channelLogoUrl?.takeIf { it.isNotBlank() }?.let { logoUrl ->
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Text(
                                text = contentContext.channelName,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("player-header-title"),
                            )
                            contentContext.programmeTitle?.let { prog ->
                                Text(
                                    text = " \u2022  $prog",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("player-header-subtitle"),
                                )
                            }
                        }
                    }
                    is PlayerContentContext.Movie -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = contentContext.title,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("player-header-title"),
                            )
                            val metaList = listOfNotNull(contentContext.year, contentContext.rating, contentContext.runtime, contentContext.genre)
                                .filter { it.isNotBlank() }
                            if (metaList.isNotEmpty()) {
                                Text(
                                    text = " \u2022  ${metaList.joinToString("  \u2022  ")}",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = contentContext.seriesTitle,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("player-header-title"),
                            )
                            val epSubtitle = " \u2022  S${contentContext.seasonNumber} \u2022 E${contentContext.episodeNumber}  ${contentContext.episodeTitle}"
                            Text(
                                text = epSubtitle,
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("player-header-subtitle"),
                            )
                        }
                    }
                }

                // Breathing room between metadata and timeline
                Spacer(modifier = Modifier.height(2.dp))

                // Thin elegant timeline with current-position thumb indicator
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

                // Modern IPTV Icon Control Strip (Single horizontal strip with horizontal scroll protection)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (contentContext) {
                            is PlayerContentContext.Live -> {
                                PlayerControlItem(
                                    title = "Channels",
                                    icon = PlayerIconKind.Channels,
                                    accent = colors.focusGlow,
                                    contentDescription = "Channels",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        contentContext.onChannelsClick()
                                    },
                                    modifier = Modifier.testTag("player-channels-button"),
                                )
                                if (contentContext.hasPreviousChannel) {
                                    PlayerControlItem(
                                        title = "Previous",
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
                                    PlayerControlItem(
                                        title = "Rewind",
                                        icon = PlayerIconKind.Rewind10,
                                        accent = colors.focusGlow,
                                        contentDescription = "Rewind 10 seconds",
                                        onClick = { triggerSeek(-10_000L) },
                                        modifier = Modifier.testTag("player-rewind"),
                                    )
                                }
                                PlayerControlItem(
                                    title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    icon = if (playerState is WatchioPlayerState.Playing) PlayerIconKind.Pause else PlayerIconKind.Play,
                                    accent = colors.liveTvAccent,
                                    isPrimary = true,
                                    focusRequester = firstFocus,
                                    contentDescription = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onPlayPause()
                                    },
                                    modifier = Modifier.testTag("player-play-pause"),
                                )
                                if (metadata.isSeekable) {
                                    PlayerControlItem(
                                        title = "Forward",
                                        icon = PlayerIconKind.Forward10,
                                        accent = colors.focusGlow,
                                        contentDescription = "Fast forward 10 seconds",
                                        onClick = { triggerSeek(10_000L) },
                                        modifier = Modifier.testTag("player-forward"),
                                    )
                                }
                                if (contentContext.hasNextChannel) {
                                    PlayerControlItem(
                                        title = "Next",
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
                                PlayerControlItem(
                                    title = "Audio",
                                    icon = PlayerIconKind.Audio,
                                    accent = colors.focusGlow,
                                    contentDescription = "Audio tracks",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Audio
                                    },
                                    modifier = Modifier.testTag("player-audio-button"),
                                )
                                PlayerControlItem(
                                    title = "Subtitles",
                                    icon = PlayerIconKind.Subtitles,
                                    accent = colors.focusGlow,
                                    contentDescription = "Subtitles",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Subtitles
                                    },
                                    modifier = Modifier.testTag("player-subtitles-button"),
                                )
                                PlayerControlItem(
                                    title = "Aspect",
                                    icon = PlayerIconKind.AspectRatio,
                                    accent = colors.focusGlow,
                                    contentDescription = "Aspect ratio",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.AspectRatio
                                    },
                                    modifier = Modifier.testTag("player-aspect-button"),
                                )
                                PlayerControlItem(
                                    title = "Settings",
                                    icon = PlayerIconKind.Settings,
                                    accent = colors.focusGlow,
                                    contentDescription = "Player settings",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Settings
                                    },
                                    modifier = Modifier.testTag("player-settings-button"),
                                )
                            }
                            is PlayerContentContext.Movie -> {
                                PlayerControlItem(
                                    title = "Restart",
                                    icon = PlayerIconKind.Restart,
                                    accent = colors.moviesAccent,
                                    contentDescription = "Restart",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onRestart()
                                    },
                                    modifier = Modifier.testTag("player-restart"),
                                )
                                PlayerControlItem(
                                    title = "Rewind",
                                    icon = PlayerIconKind.Rewind10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Rewind 10 seconds",
                                    onClick = { triggerSeek(-10_000L) },
                                    modifier = Modifier.testTag("player-rewind"),
                                )
                                PlayerControlItem(
                                    title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    icon = if (playerState is WatchioPlayerState.Playing) PlayerIconKind.Pause else PlayerIconKind.Play,
                                    accent = colors.moviesAccent,
                                    isPrimary = true,
                                    focusRequester = firstFocus,
                                    contentDescription = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onPlayPause()
                                    },
                                    modifier = Modifier.testTag("player-play-pause"),
                                )
                                PlayerControlItem(
                                    title = "Forward",
                                    icon = PlayerIconKind.Forward10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Fast forward 10 seconds",
                                    onClick = { triggerSeek(10_000L) },
                                    modifier = Modifier.testTag("player-forward"),
                                )
                                PlayerControlItem(
                                    title = "Audio",
                                    icon = PlayerIconKind.Audio,
                                    accent = colors.focusGlow,
                                    contentDescription = "Audio tracks",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Audio
                                    },
                                    modifier = Modifier.testTag("player-audio-button"),
                                )
                                PlayerControlItem(
                                    title = "Subtitles",
                                    icon = PlayerIconKind.Subtitles,
                                    accent = colors.focusGlow,
                                    contentDescription = "Subtitles",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Subtitles
                                    },
                                    modifier = Modifier.testTag("player-subtitles-button"),
                                )
                                PlayerControlItem(
                                    title = "Aspect",
                                    icon = PlayerIconKind.AspectRatio,
                                    accent = colors.focusGlow,
                                    contentDescription = "Aspect ratio",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.AspectRatio
                                    },
                                    modifier = Modifier.testTag("player-aspect-button"),
                                )
                                PlayerControlItem(
                                    title = "Speed",
                                    icon = PlayerIconKind.Speed,
                                    accent = colors.focusGlow,
                                    contentDescription = "Playback speed",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.PlaybackSpeed
                                    },
                                    modifier = Modifier.testTag("player-speed-button"),
                                )
                                PlayerControlItem(
                                    title = "Settings",
                                    icon = PlayerIconKind.Settings,
                                    accent = colors.focusGlow,
                                    contentDescription = "Player settings",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Settings
                                    },
                                    modifier = Modifier.testTag("player-settings-button"),
                                )
                            }
                            is PlayerContentContext.Episode -> {
                                if (contentContext.hasPreviousEpisode) {
                                    PlayerControlItem(
                                        title = "Previous",
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
                                PlayerControlItem(
                                    title = "Rewind",
                                    icon = PlayerIconKind.Rewind10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Rewind 10 seconds",
                                    onClick = { triggerSeek(-10_000L) },
                                    modifier = Modifier.testTag("player-rewind"),
                                )
                                PlayerControlItem(
                                    title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    icon = if (playerState is WatchioPlayerState.Playing) PlayerIconKind.Pause else PlayerIconKind.Play,
                                    accent = colors.seriesAccent,
                                    isPrimary = true,
                                    focusRequester = firstFocus,
                                    contentDescription = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        onPlayPause()
                                    },
                                    modifier = Modifier.testTag("player-play-pause"),
                                )
                                PlayerControlItem(
                                    title = "Forward",
                                    icon = PlayerIconKind.Forward10,
                                    accent = colors.focusGlow,
                                    contentDescription = "Fast forward 10 seconds",
                                    onClick = { triggerSeek(10_000L) },
                                    modifier = Modifier.testTag("player-forward"),
                                )
                                if (contentContext.hasNextEpisode) {
                                    PlayerControlItem(
                                        title = "Next",
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
                                PlayerControlItem(
                                    title = "Audio",
                                    icon = PlayerIconKind.Audio,
                                    accent = colors.focusGlow,
                                    contentDescription = "Audio tracks",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Audio
                                    },
                                    modifier = Modifier.testTag("player-audio-button"),
                                )
                                PlayerControlItem(
                                    title = "Subtitles",
                                    icon = PlayerIconKind.Subtitles,
                                    accent = colors.focusGlow,
                                    contentDescription = "Subtitles",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Subtitles
                                    },
                                    modifier = Modifier.testTag("player-subtitles-button"),
                                )
                                PlayerControlItem(
                                    title = "Aspect",
                                    icon = PlayerIconKind.AspectRatio,
                                    accent = colors.focusGlow,
                                    contentDescription = "Aspect ratio",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.AspectRatio
                                    },
                                    modifier = Modifier.testTag("player-aspect-button"),
                                )
                                PlayerControlItem(
                                    title = "Speed",
                                    icon = PlayerIconKind.Speed,
                                    accent = colors.focusGlow,
                                    contentDescription = "Playback speed",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.PlaybackSpeed
                                    },
                                    modifier = Modifier.testTag("player-speed-button"),
                                )
                                PlayerControlItem(
                                    title = "Settings",
                                    icon = PlayerIconKind.Settings,
                                    accent = colors.focusGlow,
                                    contentDescription = "Player settings",
                                    onClick = {
                                        lastInteractionEpochMs = System.currentTimeMillis()
                                        activeDialog = PlayerDialogType.Settings
                                    },
                                    modifier = Modifier.testTag("player-settings-button"),
                                )
                            }
                        }
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
            PlayerDialogType.Settings -> {
                PlayerSettingsQuickDialog(
                    videoScalingMode = metadata.videoScalingMode,
                    onSelectAspect = { mode -> playerManager.setVideoScalingMode(mode) },
                    onDismiss = { activeDialog = null },
                )
            }
            null -> Unit
        }

        // Up Next Countdown / Autoplay Overlay (bottom-right TV card)
        if (contentContext is PlayerContentContext.Episode && contentContext.nextEpisodeState !is NextEpisodeState.None) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = if (controlsVisible) 130.dp else 40.dp)
                    .testTag("player-up-next-container"),
            ) {
                UpNextOverlay(
                    state = contentContext.nextEpisodeState,
                    onPlayNext = {
                        lastInteractionEpochMs = System.currentTimeMillis()
                        contentContext.onPlayNext()
                    },
                    onCancel = {
                        lastInteractionEpochMs = System.currentTimeMillis()
                        contentContext.onCancelNext()
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchioProgressBar(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    showThumb: Boolean = true,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val trackHeight = 3.5.dp.toPx()
        val centerY = size.height / 2f
        val trackRadius = trackHeight / 2f

        // Background track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.20f),
            topLeft = Offset(0f, centerY - trackRadius),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackRadius, trackRadius),
        )

        // Active progress bar
        val progressWidth = size.width * clampedProgress
        if (progressWidth > 0f) {
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(0f, centerY - trackRadius),
                size = Size(progressWidth, trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius),
            )
        }

        // Current-position subtle thumb indicator
        if (showThumb) {
            val thumbRadius = 4.5.dp.toPx()
            val thumbX = progressWidth.coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
            drawCircle(
                color = accentColor,
                radius = thumbRadius,
                center = Offset(thumbX, centerY),
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius * 0.55f,
                center = Offset(thumbX, centerY),
            )
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
            fontSize = 12.sp,
            modifier = Modifier.testTag("player-position"),
        )
        WatchioProgressBar(
            progress = progress,
            accentColor = accentColor,
            showThumb = true,
            modifier = Modifier
                .weight(1f)
                .testTag("player-progress-bar"),
        )
        Text(
            text = formatPlaybackTime(durationMs),
            color = colors.textSecondary,
            fontSize = 12.sp,
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
            Text(text = startTime, color = colors.textSecondary, fontSize = 12.sp)
        }
        WatchioProgressBar(
            progress = progress,
            accentColor = accentColor,
            showThumb = true,
            modifier = Modifier.weight(1f),
        )
        if (!endTime.isNullOrBlank()) {
            Text(text = endTime, color = colors.textSecondary, fontSize = 12.sp)
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

@Composable
private fun PlayerSettingsQuickDialog(
    videoScalingMode: VideoScalingMode,
    onSelectAspect: (VideoScalingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aspect Ratio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                VideoScalingMode.entries.forEach { mode ->
                    val isSelected = videoScalingMode == mode
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                            .clickable(interactionSource = interactionSource, indication = null) { onSelectAspect(mode) }
                            .focusable(interactionSource = interactionSource)
                            .background(if (focused) LocalWatchioColors.current.focusGlow.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectAspect(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = LocalWatchioColors.current.focusGlow),
                        )
                        Text(text = mode.label, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("player-dialog-close")) { Text("Close") } },
        modifier = Modifier.testTag("player-settings-dialog"),
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

@Composable
private fun UpNextOverlay(
    state: NextEpisodeState,
    onPlayNext: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val playNextFocus = remember { FocusRequester() }

    LaunchedEffect(state) {
        try {
            playNextFocus.requestFocus()
        } catch (_: Throwable) {}
    }

    val (nextEp, seriesTitle, countdownSecs) = when (state) {
        is NextEpisodeState.Countdown -> Triple(state.nextEpisode, state.seriesTitle, state.secondsRemaining)
        is NextEpisodeState.Ready -> Triple(state.nextEpisode, state.seriesTitle, null)
        NextEpisodeState.None -> return
    }

    Surface(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .testTag("up-next-overlay"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141722).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.seriesAccent.copy(alpha = 0.40f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "UP NEXT",
                    color = colors.seriesAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.testTag("up-next-header"),
                )
                if (countdownSecs != null) {
                    Text(
                        text = "Playing in ${countdownSecs}s",
                        color = colors.seriesAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("up-next-countdown-text"),
                    )
                } else {
                    Text(
                        text = "Episode Finished",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("up-next-ready-text"),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                nextEp.imageUrl?.takeIf { it.isNotBlank() }?.let { imgUrl ->
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 40.dp)
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = seriesTitle,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("up-next-series-title"),
                    )
                    Text(
                        text = "S${nextEp.seasonNumber} • E${nextEp.episodeNumber}  ${nextEp.title}",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("up-next-episode-title"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerControlItem(
                    title = "Play Next",
                    icon = PlayerIconKind.SkipNext,
                    accent = colors.seriesAccent,
                    isPrimary = true,
                    focusRequester = playNextFocus,
                    onClick = onPlayNext,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("up-next-play-button"),
                )
                PlayerControlItem(
                    title = "Cancel",
                    icon = PlayerIconKind.Close,
                    accent = colors.focusGlow,
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("up-next-cancel-button"),
                )
            }
        }
    }
}
