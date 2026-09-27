package com.iamskorpz.watchioiptv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun WatchioAppBackground(appearance: WatchioThemeDefinition, modifier: Modifier = Modifier) {
    val background = appearance.background
    val colors = LocalWatchioColors.current
    Box(modifier.fillMaxSize().background(appearance.colors.appBackground.toComposeColor())) {
        if (background.imageUri.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(colors.surfaceBase, colors.surfaceCard, colors.surfaceBase))),
            )
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.liveTvAccent.copy(alpha = 0.22f), size.minDimension * 0.42f, Offset(size.width * 0.12f, size.height * 0.46f))
                drawCircle(colors.moviesAccent.copy(alpha = 0.22f), size.minDimension * 0.46f, Offset(size.width * 0.46f, size.height * 0.36f))
                drawCircle(colors.seriesAccent.copy(alpha = 0.20f), size.minDimension * 0.42f, Offset(size.width * 0.88f, size.height * 0.46f))
                drawCircle(colors.liveTvAccent.copy(alpha = 0.12f), size.minDimension * 0.22f, Offset(size.width * 0.02f, size.height * 0.18f))
                drawCircle(colors.seriesAccent.copy(alpha = 0.11f), size.minDimension * 0.24f, Offset(size.width * 0.98f, size.height * 0.24f))
                for (index in 0..10) {
                    val y = size.height * (0.18f + index * 0.08f)
                    drawLine(
                        color = listOf(colors.liveTvAccent, colors.moviesAccent, colors.seriesAccent)[index % 3].copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y + (index % 3 - 1) * 54f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
                drawRect(colors.surfaceBase.copy(alpha = 0.58f), size = size)
            }
        }
        background.imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(background.imageOpacity),
                alignment = when (background.alignment) {
                    BackgroundAlignment.Center -> Alignment.Center
                    BackgroundAlignment.Top -> Alignment.TopCenter
                    BackgroundAlignment.Bottom -> Alignment.BottomCenter
                },
                contentScale = when (background.scale) {
                    BackgroundScale.Fit -> ContentScale.Fit
                    BackgroundScale.FillCrop -> ContentScale.Crop
                    BackgroundScale.Stretch -> ContentScale.FillBounds
                },
            )
        }
        if (background.overlayOpacity > 0f) {
            Box(Modifier.fillMaxSize().background(background.overlayColor.toComposeColor().copy(alpha = background.overlayOpacity)))
        }
    }
}

@Composable
fun watchioScreenBackgroundColor(): Color =
    Color.Transparent
