package com.watchioiptv.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun WatchioTheme(
    themeState: WatchioThemeState = WatchioThemeState(),
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = themeState.liveTvAccent,
        secondary = themeState.moviesAccent,
        tertiary = themeState.seriesAccent,
        background = themeState.surfaceBase,
        surface = themeState.surfaceCard,
        onPrimary = themeState.textPrimary,
        onSecondary = themeState.textPrimary,
        onTertiary = themeState.surfaceBase,
        onBackground = themeState.textPrimary,
        onSurface = themeState.textPrimary,
    )

    CompositionLocalProvider(
        LocalWatchioColors provides themeState,
        LocalWatchioSpacing provides WatchioSpacing(),
        LocalWatchioRadii provides WatchioRadii(),
        LocalWatchioBorders provides WatchioBorders(),
        LocalWatchioComponentSizes provides WatchioComponentSizes(),
        LocalWatchioIconSizes provides WatchioIconSizes(),
        LocalWatchioPosterTokens provides WatchioPosterTokens(),
        LocalWatchioMotion provides WatchioMotion(),
        LocalWatchioTypography provides WatchioTypography(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
