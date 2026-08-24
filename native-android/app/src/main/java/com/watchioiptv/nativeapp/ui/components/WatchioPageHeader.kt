package com.watchioiptv.nativeapp.ui.components

import androidx.compose.foundation.Canvas
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
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioSpacing
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun WatchioPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "page",
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(58.dp).testTag("$testTagPrefix-header")) {
        val compact = maxWidth < 900.dp
        val brandMaxWidth = if (compact) 230.dp else 360.dp
        val clockWidth = if (compact) 112.dp else 150.dp
        val actionGap = if (compact) 6.dp else spacing.sm
        Row(
            modifier = Modifier.align(Alignment.CenterStart).widthIn(max = brandMaxWidth).testTag("$testTagPrefix-branding"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            WatchioHeaderBackButton(onClick = onBack, testTag = "$testTagPrefix-back-icon")
            WatchioLogoMark()
            Text("Watchio", color = colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(title, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).testTag("$testTagPrefix-title"), maxLines = 1)
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(actionGap),
        ) {
            Column(modifier = Modifier.width(clockWidth).testTag("$testTagPrefix-clock"), horizontalAlignment = Alignment.End) {
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold)
                Text(now.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), color = colors.liveTvAccent, style = type.label)
            }
            actions()
        }
    }
}

@Composable
fun WatchioHeaderBackButton(onClick: () -> Unit, testTag: String) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = Modifier.size(48.dp).testTag(testTag),
        accent = colors.liveTvAccent,
        minWidth = 48.dp,
        minHeight = 48.dp,
        contentDescription = "Back",
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                drawLine(colors.textPrimary, Offset(size.width * 0.72f, size.height * 0.18f), Offset(size.width * 0.28f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(colors.textPrimary, Offset(size.width * 0.28f, size.height * 0.50f), Offset(size.width * 0.72f, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun WatchioLogoMark() {
    val colors = LocalWatchioColors.current
    Canvas(Modifier.size(34.dp)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(colors.liveTvAccent, topLeft = Offset(size.width * 0.12f, size.height * 0.24f), size = Size(size.width * 0.76f, size.height * 0.58f), style = stroke)
        drawLine(colors.liveTvAccent, Offset(size.width * 0.30f, size.height * 0.24f), Offset(size.width * 0.20f, size.height * 0.06f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(colors.liveTvAccent, Offset(size.width * 0.70f, size.height * 0.24f), Offset(size.width * 0.82f, size.height * 0.06f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.48f), radius = size.minDimension * 0.20f, center = center)
        val path = Path().apply {
            moveTo(size.width * 0.44f, size.height * 0.42f)
            lineTo(size.width * 0.44f, size.height * 0.64f)
            lineTo(size.width * 0.64f, size.height * 0.53f)
            close()
        }
        drawPath(path, colors.textPrimary)
    }
}
