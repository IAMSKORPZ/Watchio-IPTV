package com.iamskorpz.watchioiptv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun WatchioAppBackground(appearance: WatchioThemeDefinition, modifier: Modifier = Modifier) {
    val background = appearance.background
    Box(modifier.fillMaxSize().background(appearance.colors.appBackground.toComposeColor())) {
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
