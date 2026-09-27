package com.iamskorpz.watchioiptv.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.core.player.shouldKeepScreenOn
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors

@Composable
fun WatchioPlayerSurface(
    playerManager: WatchioPlayerManager,
    playerState: WatchioPlayerState,
    modifier: Modifier,
    onClick: () -> Unit,
    fallbackLogoUrl: String? = null,
    fallbackLabel: String? = null,
    showLogoFallbackOnUnavailable: Boolean = false,
    useFitScaling: Boolean = false,
    contentDescription: String = "Open fullscreen player",
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = LocalWatchioColors.current
    val showFallback = showLogoFallbackOnUnavailable &&
        (playerState is WatchioPlayerState.Idle || playerState is WatchioPlayerState.Failed)

    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var currentContainer by remember { mutableStateOf<ViewGroup?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val resumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
            isResumed = resumed
            if (!resumed) {
                currentContainer?.let { playerManager.detachSurface(it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentContainer?.let { playerManager.detachSurface(it) }
            currentContainer = null
        }
    }

    Box(
        modifier
            .background(if (showFallback) colors.surfaceStatus else Color.Black)
            .then(if (focused) Modifier.border(2.dp, colors.focusGlow, RoundedCornerShape(6.dp)) else Modifier)
            .semantics { this.contentDescription = contentDescription }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        if (!showFallback && isResumed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    FrameLayout(context).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }.also { view ->
                        currentContainer = view
                        view.keepScreenOn = shouldKeepScreenOn(playerState)
                        if (useFitScaling) playerManager.attachPreviewSurface(view)
                        else playerManager.attachSurface(view)
                    }
                },
                update = { view ->
                    currentContainer = view
                    view.keepScreenOn = shouldKeepScreenOn(playerState)
                    if (isResumed) {
                        if (useFitScaling) playerManager.attachPreviewSurface(view)
                        else playerManager.attachSurface(view)
                    }
                },
                onRelease = { view ->
                    view.keepScreenOn = false
                    playerManager.detachSurface(view)
                    if (currentContainer === view) {
                        currentContainer = null
                    }
                },
            )
        }
        if (showFallback || !isResumed) {
            Box(Modifier.fillMaxSize().testTag("watchio-player-fallback"), contentAlignment = Alignment.Center) {
            if (!fallbackLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = fallbackLogoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(colors.surfaceStatus).padding(12.dp),
                )
            } else {
                Text(fallbackLabel ?: "Select a channel", color = colors.textSecondary)
            }
            }
        } else {
            when (playerState) {
                is WatchioPlayerState.Connecting -> Text("Connecting...", color = Color.White)
                is WatchioPlayerState.Buffering -> Text("Buffering...", color = Color.White)
                is WatchioPlayerState.Recovering -> Text("Reconnecting...", color = Color.White)
                is WatchioPlayerState.Failed -> Text(playerState.message, color = Color.White)
                is WatchioPlayerState.Idle -> Text("Select a channel", color = Color.White)
                else -> Unit
            }
        }
    }
}
