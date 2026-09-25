package com.iamskorpz.watchioiptv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioSpacing
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioTypography
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioAppearance
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeState
import com.iamskorpz.watchioiptv.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun WatchioPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "page",
    artworkReadability: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val appearance = LocalWatchioAppearance.current
    val primaryColor = if (artworkReadability) colors.artworkTextPrimary else colors.textPrimary
    val accentColor = if (artworkReadability) colors.artworkTextSecondary else colors.liveTvAccent
    val headerShape = RoundedCornerShape(appearance.surfaces.cornerRadiusDp.dp)
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(
                if (appearance.surfaces.outlineWidthDp > 0f) {
                    Modifier.border(appearance.surfaces.outlineWidthDp.dp, appearance.surfaces.outlineColor.toComposeColor(), headerShape)
                } else {
                    Modifier
                },
            )
            .testTag("$testTagPrefix-header"),
    ) {
        val isMobile = maxWidth < 700.dp
        val isMedium = maxWidth in 700.dp..980.dp
        val actionGap = if (isMobile) 6.dp else spacing.sm

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("$testTagPrefix-branding"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isMobile) spacing.xs else spacing.sm),
        ) {
            WatchioHeaderBackButton(onClick = onBack, testTag = "$testTagPrefix-back-icon")
            if (!isMobile) {
                WatchioLogoMark(accentColor = accentColor, playColor = primaryColor)
                if (!isMedium) {
                    Text(
                        "Watchio",
                        color = primaryColor,
                        style = type.cardTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            title,
            color = primaryColor,
            style = if (isMobile) type.cardTitle else type.screenTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .testTag("$testTagPrefix-title"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .zIndex(2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(actionGap),
        ) {
            Column(
                modifier = Modifier.testTag("$testTagPrefix-clock"),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = primaryColor,
                    style = if (isMobile) type.body else type.cardTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (!isMobile) {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        color = accentColor,
                        style = type.label,
                        maxLines = 1,
                    )
                }
            }
            actions()
        }
    }
}

@Composable
fun WatchioHeaderBackButton(onClick: () -> Unit, testTag: String) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = Modifier.size(44.dp).testTag(testTag),
        surfaceRole = WatchioSurfaceRole.Control,
        accent = colors.liveTvAccent,
        minWidth = 44.dp,
        minHeight = 44.dp,
        contentDescription = "Back",
        backgroundColor = colors.buttonSurface,
        outlineColor = colors.buttonOutline,
        onClick = onClick,
    ) { focused ->
        val iconColor = headerBackIconColor(colors, focused)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(iconColor, Offset(size.width * 0.70f, size.height * 0.20f), Offset(size.width * 0.30f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(iconColor, Offset(size.width * 0.30f, size.height * 0.50f), Offset(size.width * 0.70f, size.height * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

internal fun headerBackIconColor(colors: WatchioThemeState, focused: Boolean): Color =
    if (focused) colors.focusedContent else colors.buttonText

@Composable
private fun WatchioLogoMark(accentColor: Color, playColor: Color) {
    Canvas(Modifier.size(34.dp)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(accentColor, topLeft = Offset(size.width * 0.12f, size.height * 0.24f), size = Size(size.width * 0.76f, size.height * 0.58f), style = stroke)
        drawLine(accentColor, Offset(size.width * 0.30f, size.height * 0.24f), Offset(size.width * 0.20f, size.height * 0.06f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(accentColor, Offset(size.width * 0.70f, size.height * 0.24f), Offset(size.width * 0.82f, size.height * 0.06f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.48f), radius = size.minDimension * 0.20f, center = center)
        val path = Path().apply {
            moveTo(size.width * 0.44f, size.height * 0.42f)
            lineTo(size.width * 0.44f, size.height * 0.64f)
            lineTo(size.width * 0.64f, size.height * 0.53f)
            close()
        }
        drawPath(path, playColor)
    }
}
