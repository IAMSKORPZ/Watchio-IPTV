package com.iamskorpz.watchioiptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WatchioTheme(
    themeState: WatchioThemeState = WatchioThemeState(),
    appearance: WatchioThemeDefinition = WatchioThemeDefinition.WatchioDefault,
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
        LocalWatchioAppearance provides appearance,
        LocalWatchioSpacing provides WatchioSpacing().scaled(appearance.layout.spacingScale),
        LocalWatchioRadii provides WatchioRadii(
            sm = (appearance.cards.cornerRadiusDp * 0.75f).dp,
            md = appearance.cards.cornerRadiusDp.dp,
            lg = appearance.surfaces.cornerRadiusDp.dp,
        ),
        LocalWatchioBorders provides WatchioBorders(
            normal = appearance.cards.outlineWidthDp.dp,
            focused = appearance.focus.outlineWidthDp.dp,
        ),
        LocalWatchioComponentSizes provides WatchioComponentSizes(),
        LocalWatchioIconSizes provides WatchioIconSizes(),
        LocalWatchioPosterTokens provides WatchioPosterTokens(),
        LocalWatchioMotion provides when (appearance.effects.animations) {
            AnimationChoice.Full -> WatchioMotion()
            AnimationChoice.Reduced -> WatchioMotion(focusMillis = 60)
            AnimationChoice.Off -> WatchioMotion(focusMillis = 0)
        },
        LocalWatchioTypography provides appearance.typography.toTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

private fun WatchioSpacing.scaled(scale: Float) = WatchioSpacing(
    xs = xs * scale, sm = sm * scale, md = md * scale, lg = lg * scale, xl = xl * scale, xxl = xxl * scale,
)

private fun WatchioTypographyStyle.toTokens() = WatchioTypography(
    screenTitle = androidx.compose.ui.text.TextStyle(fontSize = sectionSp.sp, fontWeight = sectionWeight.compose),
    cardTitle = androidx.compose.ui.text.TextStyle(fontSize = buttonSp.sp, fontWeight = buttonWeight.compose),
    body = androidx.compose.ui.text.TextStyle(fontSize = bodySp.sp, fontWeight = bodyWeight.compose),
    label = androidx.compose.ui.text.TextStyle(fontSize = metadataSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
)

private val FontWeightChoice.compose: androidx.compose.ui.text.font.FontWeight
    get() = when (this) {
        FontWeightChoice.Normal -> androidx.compose.ui.text.font.FontWeight.Normal
        FontWeightChoice.Medium -> androidx.compose.ui.text.font.FontWeight.Medium
        FontWeightChoice.SemiBold -> androidx.compose.ui.text.font.FontWeight.SemiBold
        FontWeightChoice.Bold -> androidx.compose.ui.text.font.FontWeight.Bold
    }
