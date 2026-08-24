package com.watchioiptv.nativeapp.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class WatchioSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 28.dp,
)

data class WatchioRadii(
    val sm: Dp = 6.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 12.dp,
)

data class WatchioBorders(
    val normal: Dp = 1.dp,
    val focused: Dp = 3.dp,
)

data class WatchioComponentSizes(
    val cardMinWidth: Dp = 160.dp,
    val cardMinHeight: Dp = 92.dp,
    val buttonMinHeight: Dp = 50.dp,
    val compactButtonMinHeight: Dp = 44.dp,
    val listRowMinHeight: Dp = 64.dp,
    val tvSafePadding: Dp = 20.dp,
)

data class WatchioIconSizes(
    val sm: Dp = 18.dp,
    val md: Dp = 24.dp,
    val lg: Dp = 32.dp,
)

data class WatchioPosterTokens(
    val aspectRatio: Float = 2f / 3f,
    val minWidth: Dp = 104.dp,
    val maxWidth: Dp = 180.dp,
)

data class WatchioMotion(
    val focusMillis: Int = 120,
    val overlayHideMillis: Int = 4_000,
)

data class WatchioTypography(
    val screenTitle: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    val cardTitle: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontSize = 14.sp),
    val label: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
)

enum class WatchioResponsiveClass {
    Compact,
    Medium,
    Expanded,
    Tv,
}

fun watchioResponsiveClass(widthDp: Dp): WatchioResponsiveClass =
    when {
        widthDp < 600.dp -> WatchioResponsiveClass.Compact
        widthDp < 960.dp -> WatchioResponsiveClass.Medium
        widthDp < 1280.dp -> WatchioResponsiveClass.Expanded
        else -> WatchioResponsiveClass.Tv
    }

val LocalWatchioSpacing = compositionLocalOf { WatchioSpacing() }
val LocalWatchioRadii = compositionLocalOf { WatchioRadii() }
val LocalWatchioBorders = compositionLocalOf { WatchioBorders() }
val LocalWatchioComponentSizes = compositionLocalOf { WatchioComponentSizes() }
val LocalWatchioIconSizes = compositionLocalOf { WatchioIconSizes() }
val LocalWatchioPosterTokens = compositionLocalOf { WatchioPosterTokens() }
val LocalWatchioMotion = compositionLocalOf { WatchioMotion() }
val LocalWatchioTypography = compositionLocalOf { WatchioTypography() }
