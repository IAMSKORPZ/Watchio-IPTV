package com.watchioiptv.nativeapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioComponentSizes
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography

@Composable
fun WatchioFocusableCard(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalWatchioColors.current.liveTvAccent,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
    minWidth: Dp = LocalWatchioComponentSizes.current.cardMinWidth,
    minHeight: Dp = LocalWatchioComponentSizes.current.cardMinHeight,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.merge(LocalWatchioTypography.current.cardTitle),
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier,
        accent = accent,
        minWidth = minWidth,
        minHeight = minHeight,
        contentDescription = title,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                style = textStyle,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}
