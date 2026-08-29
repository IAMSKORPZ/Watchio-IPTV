package com.watchioiptv.nativeapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioBorders
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioComponentSizes
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioPosterTokens
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioRadii
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioSpacing
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography

enum class WatchioButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Danger,
    CompactAction,
}

@Composable
fun WatchioCard(
    modifier: Modifier = Modifier,
    accent: Color = LocalWatchioColors.current.focusGlow,
    selected: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = LocalWatchioComponentSizes.current.cardMinWidth,
    minHeight: Dp = LocalWatchioComponentSizes.current.cardMinHeight,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val radii = LocalWatchioRadii.current
    val borders = LocalWatchioBorders.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radii.md)
    val active = focused || selected
    val clickableModifier = if (onClick != null && enabled) {
        Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .shadow(
                elevation = if (focused) 16.dp else 0.dp,
                shape = shape,
                ambientColor = colors.focusGlow,
                spotColor = colors.focusGlow,
            )
            .border(
                BorderStroke(if (focused) borders.focused else borders.normal, if (active) colors.focusBorder else colors.surfaceElevated),
                shape,
            )
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .then(clickableModifier),
        color = if (enabled) colors.surfaceCard else colors.surfaceElevated,
        shape = shape,
    ) {
        Box(Modifier.background(if (active) accent.copy(alpha = 0.16f) else colors.surfaceCard)) {
            content(focused)
        }
    }
}

@Composable
fun WatchioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WatchioButtonVariant = WatchioButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val sizes = LocalWatchioComponentSizes.current
    val type = LocalWatchioTypography.current
    val accent = when (variant) {
        WatchioButtonVariant.Primary -> colors.liveTvAccent
        WatchioButtonVariant.Secondary -> colors.seriesAccent
        WatchioButtonVariant.Ghost -> colors.focusGlow
        WatchioButtonVariant.Danger -> colors.moviesAccent
        WatchioButtonVariant.CompactAction -> colors.liveTvAccent
    }
    val minHeight = if (variant == WatchioButtonVariant.CompactAction) sizes.compactButtonMinHeight else sizes.buttonMinHeight
    WatchioCard(
        modifier = modifier,
        accent = accent,
        enabled = enabled && !loading,
        minWidth = 0.dp,
        minHeight = minHeight,
        contentDescription = text,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(color = colors.textPrimary, modifier = Modifier.sizeIn(maxWidth = 20.dp, maxHeight = 20.dp))
                Spacer(Modifier.padding(horizontal = spacing.xs))
            }
            Text(
                text = text,
                color = if (enabled) colors.textPrimary else colors.textMuted,
                style = if (variant == WatchioButtonVariant.CompactAction) type.label else type.cardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun WatchioIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val spacing = LocalWatchioSpacing.current
    WatchioCard(
        modifier = modifier,
        enabled = enabled,
        minWidth = 48.dp,
        minHeight = 48.dp,
        contentDescription = contentDescription,
        onClick = onClick,
    ) {
        Box(Modifier.padding(spacing.sm), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun WatchioChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    WatchioCard(
        modifier = modifier,
        accent = if (selected) colors.seriesAccent else colors.focusGlow,
        selected = selected,
        minWidth = 0.dp,
        minHeight = 40.dp,
        contentDescription = text,
        onClick = onClick,
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun WatchioPosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val poster = LocalWatchioPosterTokens.current
    WatchioCard(
        modifier = modifier,
        minWidth = poster.minWidth,
        minHeight = 0.dp,
        contentDescription = title,
        onClick = onClick,
    ) {
        Column(Modifier.padding(spacing.sm)) {
            Box(Modifier.fillMaxWidth().aspectRatio(poster.aspectRatio).background(colors.surfaceElevated)) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(poster.aspectRatio),
                    )
                }
            }
            Spacer(Modifier.height(spacing.sm))
            Text(title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun WatchioListRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    WatchioCard(
        modifier = modifier.fillMaxWidth(),
        minWidth = 0.dp,
        minHeight = LocalWatchioComponentSizes.current.listRowMinHeight,
        contentDescription = title,
        onClick = onClick,
    ) {
        Column(Modifier.padding(spacing.md)) {
            Text(title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun WatchioScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    Column(modifier) {
        Text(title, color = colors.textPrimary, style = type.screenTitle)
        if (subtitle != null) {
            Spacer(Modifier.height(spacing.xs))
            Text(subtitle, color = colors.textSecondary, style = type.body)
        }
    }
}

@Composable
fun WatchioLoading(text: String, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = colors.liveTvAccent)
        Spacer(Modifier.height(LocalWatchioSpacing.current.md))
        Text(text, color = colors.textSecondary)
    }
}

@Composable
fun WatchioErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = colors.textPrimary)
        if (onRetry != null) {
            Spacer(Modifier.height(spacing.md))
            WatchioButton("Retry", onClick = onRetry, variant = WatchioButtonVariant.Secondary)
        }
    }
}

@Composable
fun WatchioEmptyState(message: String, modifier: Modifier = Modifier) {
    Text(message, color = LocalWatchioColors.current.textSecondary, modifier = modifier)
}

@Composable
fun WatchioProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    if (progress == null) {
        LinearProgressIndicator(color = colors.liveTvAccent, modifier = modifier)
    } else {
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = colors.liveTvAccent, modifier = modifier)
    }
}

fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

data class ResumePlaybackRequest(
    val title: String,
    val subtitle: String? = null,
    val resumePositionMs: Long,
    val durationMs: Long? = null,
    val onResume: () -> Unit,
    val onRestart: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun ResumePlaybackDialog(
    request: ResumePlaybackRequest,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val typography = LocalWatchioTypography.current
    val radii = LocalWatchioRadii.current
    val resumeFocus = remember { FocusRequester() }

    BackHandler(onBack = request.onDismiss)

    LaunchedEffect(Unit) {
        resumeFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .testTag("resume-dialog-backdrop"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = 360.dp, max = 500.dp)
                .padding(24.dp)
                .testTag("resume-playback-dialog"),
            shape = RoundedCornerShape(radii.lg),
            color = colors.surfaceCard,
            border = BorderStroke(1.5.dp, colors.moviesAccent.copy(alpha = 0.6f)),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Resume Playback",
                    style = typography.screenTitle,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("resume-dialog-title"),
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = request.title,
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("resume-dialog-content-title"),
                )
                if (!request.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = request.subtitle,
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("resume-dialog-subtitle"),
                    )
                }
                Spacer(Modifier.height(spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    WatchioFocusableCard(
                        title = "Resume\n${formatPlaybackTime(request.resumePositionMs)}",
                        accent = colors.moviesAccent,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .focusRequester(resumeFocus)
                            .testTag("resume-dialog-resume-button"),
                        onClick = request.onResume,
                    )
                    WatchioFocusableCard(
                        title = "Restart\nFrom Beginning",
                        accent = colors.focusGlow,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("resume-dialog-restart-button"),
                        onClick = request.onRestart,
                    )
                }
            }
        }
    }
}

