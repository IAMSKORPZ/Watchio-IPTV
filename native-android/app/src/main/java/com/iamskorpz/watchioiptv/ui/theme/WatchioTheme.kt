package com.iamskorpz.watchioiptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance

@Composable
fun WatchioTheme(
    themeState: WatchioThemeState = WatchioThemeState(),
    appearance: WatchioThemeDefinition = WatchioThemeDefinition.WatchioDefault,
    content: @Composable () -> Unit,
) {
    val resolvedThemeState = resolveWatchioThemeState(themeState, appearance)
    val colors = appearance.colors
    val isLight = colors.appBackground.toComposeColor().luminance() > 0.5f
    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = colors.accent.toComposeColor(), onPrimary = colors.selectedButtonText.toComposeColor(),
            secondary = colors.selectedCardOutline.toComposeColor(), onSecondary = colors.primaryText.toComposeColor(),
            tertiary = colors.navigationSelectedIcon.toComposeColor(), onTertiary = colors.navigationSelectedBackground.toComposeColor(),
            background = colors.appBackground.toComposeColor(), onBackground = colors.primaryText.toComposeColor(),
            surface = colors.primaryPanel.toComposeColor(), onSurface = colors.primaryText.toComposeColor(),
            surfaceVariant = colors.secondaryPanel.toComposeColor(), onSurfaceVariant = colors.secondaryText.toComposeColor(),
            surfaceContainer = colors.primaryPanel.toComposeColor(), surfaceContainerHigh = colors.dialog.toComposeColor(),
            surfaceContainerHighest = colors.popup.toComposeColor(),
            outline = appearance.surfaces.outlineColor.toComposeColor(), error = resolvedThemeState.liveTvAccent,
        )
    } else {
        darkColorScheme(
            primary = colors.accent.toComposeColor(), onPrimary = colors.selectedButtonText.toComposeColor(),
            secondary = colors.selectedCardOutline.toComposeColor(), onSecondary = colors.primaryText.toComposeColor(),
            tertiary = colors.navigationSelectedIcon.toComposeColor(), onTertiary = colors.navigationSelectedBackground.toComposeColor(),
            background = colors.appBackground.toComposeColor(), onBackground = colors.primaryText.toComposeColor(),
            surface = colors.primaryPanel.toComposeColor(), onSurface = colors.primaryText.toComposeColor(),
            surfaceVariant = colors.secondaryPanel.toComposeColor(), onSurfaceVariant = colors.secondaryText.toComposeColor(),
            surfaceContainer = colors.primaryPanel.toComposeColor(), surfaceContainerHigh = colors.dialog.toComposeColor(),
            surfaceContainerHighest = colors.popup.toComposeColor(),
            outline = appearance.surfaces.outlineColor.toComposeColor(), error = resolvedThemeState.liveTvAccent,
        )
    }
    val semanticTypography = appearance.typography.toMaterialTypography()

    CompositionLocalProvider(
        LocalWatchioColors provides resolvedThemeState,
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
        LocalWatchioComponentSizes provides appearance.layout.toComponentSizes(),
        LocalWatchioIconSizes provides WatchioIconSizes(),
        LocalWatchioPosterTokens provides appearance.layout.toPosterTokens(appearance.cards.posterCornerRadiusDp.dp),
        LocalWatchioMotion provides when (appearance.effects.animations) {
            AnimationChoice.Full -> WatchioMotion()
            AnimationChoice.Reduced -> WatchioMotion(focusMillis = 60)
            AnimationChoice.Off -> WatchioMotion(focusMillis = 0)
        },
        LocalWatchioTypography provides appearance.typography.toTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = semanticTypography,
            shapes = Shapes(
                small = RoundedCornerShape(appearance.controls.cornerRadiusDp.dp),
                medium = RoundedCornerShape(appearance.cards.cornerRadiusDp.dp),
                large = RoundedCornerShape(appearance.surfaces.cornerRadiusDp.dp),
            ),
            content = content,
        )
    }
}

fun resolveWatchioThemeState(
    legacy: WatchioThemeState,
    appearance: WatchioThemeDefinition,
): WatchioThemeState = if (appearance.id == WATCHIO_DEFAULT_THEME_ID) legacy else WatchioThemeState.fromDefinition(appearance)

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

private fun WatchioTypographyStyle.toMaterialTypography() = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(fontSize = displaySp.sp, fontWeight = displayWeight.compose),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = sectionSp.sp, fontWeight = sectionWeight.compose),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = buttonSp.sp, fontWeight = buttonWeight.compose),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = bodySp.sp, fontWeight = bodyWeight.compose),
    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = metadataSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = buttonSp.sp, fontWeight = buttonWeight.compose),
)

private fun WatchioLayoutStyle.toComponentSizes(): WatchioComponentSizes {
    val densityScale = when (density) {
        DensityChoice.Compact -> 0.90f
        DensityChoice.Standard -> 1f
        DensityChoice.Spacious -> 1.10f
    } * uiScale
    return WatchioComponentSizes(
        cardMinWidth = 160.dp * densityScale,
        cardMinHeight = 92.dp * densityScale,
        buttonMinHeight = 50.dp * densityScale,
        compactButtonMinHeight = 44.dp * densityScale,
        listRowMinHeight = 64.dp * densityScale,
        tvSafePadding = 20.dp * densityScale,
    )
}

private fun WatchioLayoutStyle.toPosterTokens(radius: androidx.compose.ui.unit.Dp): WatchioPosterTokens {
    val sizeScale = when (cardSize) {
        CardSizeChoice.Compact -> 0.85f
        CardSizeChoice.Standard -> 1f
        CardSizeChoice.Large -> 1.15f
    } * uiScale
    return WatchioPosterTokens(
        minWidth = 104.dp * sizeScale,
        maxWidth = 180.dp * sizeScale,
        cornerRadius = radius,
        gridMinWidth = 132.dp * sizeScale,
    )
}
