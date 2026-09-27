package com.iamskorpz.watchioiptv.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

enum class WatchioIconKind {
    LiveTv,
    Movies,
    TvShows,
    Football,
    Settings,
    Favourite,
    Search,
    ComingSoon,
    History,
    Guide,
    Provider,
    Account,
    QuickLogin,
    Player,
    Parental,
    StreamFormat,
    InputMode,
    Appearance,
    BackupRestore,
    Updates,
    Announcements,
    More,
    Back,
}

object WatchioIconColors {
    val LiveTv = Color(0xFF1976D2)
    val Movies = Color(0xFF9146D8)
    val TvShows = Color(0xFFF59E0B)
    val Football = Color(0xFF16A34A)
    val Settings = Color(0xFF64748B)
    val Favourite = Color(0xFFEF4444)
    val Search = Color(0xFF0891B2)
    val ComingSoon = Color(0xFFDB2777)
    val History = Color(0xFF0D9488)
}

fun WatchioIconKind.identityColor(): Color = when (this) {
    WatchioIconKind.LiveTv, WatchioIconKind.Guide -> WatchioIconColors.LiveTv
    WatchioIconKind.Movies, WatchioIconKind.Player -> WatchioIconColors.Movies
    WatchioIconKind.TvShows -> WatchioIconColors.TvShows
    WatchioIconKind.Football -> WatchioIconColors.Football
    WatchioIconKind.Settings -> WatchioIconColors.Settings
    WatchioIconKind.Favourite -> WatchioIconColors.Favourite
    WatchioIconKind.Search -> WatchioIconColors.Search
    WatchioIconKind.ComingSoon -> WatchioIconColors.ComingSoon
    WatchioIconKind.History -> WatchioIconColors.History
    WatchioIconKind.Provider, WatchioIconKind.Account, WatchioIconKind.QuickLogin,
    WatchioIconKind.InputMode, WatchioIconKind.Back -> WatchioIconColors.LiveTv
    WatchioIconKind.Parental, WatchioIconKind.Appearance -> WatchioIconColors.TvShows
    WatchioIconKind.StreamFormat, WatchioIconKind.BackupRestore -> WatchioIconColors.History
    WatchioIconKind.Updates, WatchioIconKind.Announcements, WatchioIconKind.More -> WatchioIconColors.Search
}

@Composable
fun WatchioIcon(kind: WatchioIconKind, tint: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Canvas(modifier) {
        val unit = size.minDimension
        val strokeWidth = unit * 0.085f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = strokeWidth) =
            drawLine(tint, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), width, StrokeCap.Round)

        when (kind) {
            WatchioIconKind.LiveTv -> {
                drawRoundRect(tint, Offset(size.width * .12f, size.height * .23f), Size(size.width * .76f, size.height * .58f), CornerRadius(unit * .08f), style = stroke)
                line(.34f, .23f, .25f, .08f); line(.66f, .23f, .75f, .08f)
                val play = Path().apply { moveTo(size.width * .43f, size.height * .39f); lineTo(size.width * .43f, size.height * .65f); lineTo(size.width * .66f, size.height * .52f); close() }
                drawPath(play, tint)
            }
            WatchioIconKind.Movies -> {
                drawRoundRect(tint, Offset(size.width * .14f, size.height * .32f), Size(size.width * .72f, size.height * .52f), CornerRadius(unit * .06f), style = stroke)
                val clap = Path().apply { moveTo(size.width * .16f, size.height * .31f); lineTo(size.width * .24f, size.height * .12f); lineTo(size.width * .88f, size.height * .12f); lineTo(size.width * .80f, size.height * .31f); close() }
                drawPath(clap, tint, style = stroke)
                line(.34f, .13f, .27f, .30f, strokeWidth * .72f); line(.57f, .13f, .50f, .30f, strokeWidth * .72f); line(.80f, .13f, .73f, .30f, strokeWidth * .72f)
            }
            WatchioIconKind.TvShows -> {
                drawRoundRect(tint, Offset(size.width * .12f, size.height * .15f), Size(size.width * .76f, size.height * .70f), CornerRadius(unit * .07f), style = stroke)
                line(.12f, .37f, .88f, .37f); line(.42f, .37f, .42f, .85f)
                drawCircle(tint, unit * .055f, Offset(size.width * .27f, size.height * .26f))
                line(.51f, .56f, .77f, .56f); line(.51f, .70f, .70f, .70f)
            }
            WatchioIconKind.Football -> {
                drawPath(Path().apply { moveTo(size.width * .27f, size.height * .16f); lineTo(size.width * .73f, size.height * .16f); lineTo(size.width * .68f, size.height * .46f); quadraticTo(size.width * .64f, size.height * .64f, size.width * .50f, size.height * .64f); quadraticTo(size.width * .36f, size.height * .64f, size.width * .32f, size.height * .46f); close() }, tint, style = stroke)
                drawArc(tint, 90f, 180f, false, Offset(size.width * .10f, size.height * .20f), Size(size.width * .25f, size.height * .28f), style = stroke)
                drawArc(tint, -90f, 180f, false, Offset(size.width * .65f, size.height * .20f), Size(size.width * .25f, size.height * .28f), style = stroke)
                line(.50f, .65f, .50f, .78f); line(.35f, .84f, .65f, .84f)
            }
            WatchioIconKind.Settings -> {
                val rOuter = unit * 0.42f
                val rRoot = unit * 0.28f
                val toothHalfAngle = Math.toRadians(10.0)
                val flankAngle = Math.toRadians(6.0)
                val gear = Path().apply {
                    for (i in 0 until 6) {
                        val centerAngle = -Math.PI / 2 + i * (Math.PI / 3)
                        val a1 = centerAngle - toothHalfAngle
                        val a2 = centerAngle + toothHalfAngle
                        val a3 = a2 + flankAngle
                        val a4 = centerAngle + (Math.PI / 3) - toothHalfAngle - flankAngle

                        val x1 = center.x + (rOuter * kotlin.math.cos(a1)).toFloat()
                        val y1 = center.y + (rOuter * kotlin.math.sin(a1)).toFloat()
                        val x2 = center.x + (rOuter * kotlin.math.cos(a2)).toFloat()
                        val y2 = center.y + (rOuter * kotlin.math.sin(a2)).toFloat()
                        val x3 = center.x + (rRoot * kotlin.math.cos(a3)).toFloat()
                        val y3 = center.y + (rRoot * kotlin.math.sin(a3)).toFloat()
                        val x4 = center.x + (rRoot * kotlin.math.cos(a4)).toFloat()
                        val y4 = center.y + (rRoot * kotlin.math.sin(a4)).toFloat()

                        if (i == 0) moveTo(x1, y1) else lineTo(x1, y1)
                        lineTo(x2, y2)
                        lineTo(x3, y3)
                        lineTo(x4, y4)
                    }
                    close()
                }
                drawPath(gear, tint, style = stroke)
                drawCircle(tint, unit * 0.13f, center, style = stroke)
            }
            WatchioIconKind.Favourite -> {
                val heart = Path().apply { moveTo(size.width * .50f, size.height * .82f); cubicTo(size.width * .16f, size.height * .62f, size.width * .10f, size.height * .34f, size.width * .29f, size.height * .22f); cubicTo(size.width * .40f, size.height * .15f, size.width * .48f, size.height * .23f, size.width * .50f, size.height * .30f); cubicTo(size.width * .52f, size.height * .23f, size.width * .60f, size.height * .15f, size.width * .71f, size.height * .22f); cubicTo(size.width * .90f, size.height * .34f, size.width * .84f, size.height * .62f, size.width * .50f, size.height * .82f); close() }
                drawPath(heart, tint, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
            }
            WatchioIconKind.Search -> {
                drawCircle(tint, unit * .28f, Offset(size.width * .42f, size.height * .42f), style = stroke)
                line(.62f, .62f, .86f, .86f)
            }
            WatchioIconKind.ComingSoon -> {
                val star = Path().apply { repeat(8) { i -> val angle = -Math.PI / 2 + i * Math.PI / 4; val radius = if (i % 2 == 0) unit * .34f else unit * .14f; val x = center.x + kotlin.math.cos(angle).toFloat() * radius; val y = center.y + kotlin.math.sin(angle).toFloat() * radius; if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }
                drawPath(star, tint, style = stroke); drawCircle(tint, unit * .04f, Offset(size.width * .79f, size.height * .19f)); drawCircle(tint, unit * .03f, Offset(size.width * .20f, size.height * .76f))
            }
            WatchioIconKind.History -> {
                drawArc(tint, -65f, 300f, false, Offset(size.width * .15f, size.height * .15f), Size(size.width * .70f, size.height * .70f), style = stroke)
                val arrow = Path().apply { moveTo(size.width * .12f, size.height * .20f); lineTo(size.width * .32f, size.height * .19f); lineTo(size.width * .22f, size.height * .36f); close() }
                drawPath(arrow, tint); line(.50f, .31f, .50f, .52f); line(.50f, .52f, .68f, .63f)
            }
            WatchioIconKind.Guide -> {
                drawRoundRect(tint, Offset(size.width * .12f, size.height * .17f), Size(size.width * .76f, size.height * .66f), CornerRadius(unit * .07f), style = stroke)
                line(.12f, .36f, .88f, .36f); line(.38f, .36f, .38f, .83f); line(.14f, .57f, .86f, .57f)
            }
            WatchioIconKind.Provider -> {
                repeat(3) { i -> val y = .27f + i * .23f; drawCircle(tint, unit * .04f, Offset(size.width * .19f, size.height * y)); line(.32f, y, .82f, y) }
            }
            WatchioIconKind.Account -> {
                drawCircle(tint, unit * .16f, Offset(size.width * .50f, size.height * .31f), style = stroke)
                drawArc(tint, 205f, 130f, false, Offset(size.width * .18f, size.height * .46f), Size(size.width * .64f, size.height * .38f), style = stroke)
            }
            WatchioIconKind.QuickLogin -> {
                drawRoundRect(tint, Offset(size.width * .12f, size.height * .20f), Size(size.width * .32f, size.height * .60f), CornerRadius(unit * .05f), style = stroke)
                drawRoundRect(tint, Offset(size.width * .56f, size.height * .20f), Size(size.width * .32f, size.height * .60f), CornerRadius(unit * .05f), style = stroke)
                line(.39f, .50f, .61f, .50f); line(.54f, .41f, .63f, .50f); line(.54f, .59f, .63f, .50f)
            }
            WatchioIconKind.Player -> {
                drawCircle(tint, unit * .36f, center, style = stroke)
                drawPath(Path().apply { moveTo(size.width * .43f, size.height * .34f); lineTo(size.width * .43f, size.height * .66f); lineTo(size.width * .69f, size.height * .50f); close() }, tint)
            }
            WatchioIconKind.Parental -> {
                drawPath(Path().apply { moveTo(size.width * .50f, size.height * .10f); lineTo(size.width * .80f, size.height * .23f); lineTo(size.width * .76f, size.height * .58f); quadraticTo(size.width * .70f, size.height * .78f, size.width * .50f, size.height * .90f); quadraticTo(size.width * .30f, size.height * .78f, size.width * .24f, size.height * .58f); lineTo(size.width * .20f, size.height * .23f); close() }, tint, style = stroke)
                line(.38f, .50f, .47f, .59f); line(.47f, .59f, .65f, .39f)
            }
            WatchioIconKind.StreamFormat -> {
                line(.18f, .28f, .82f, .28f); line(.18f, .50f, .68f, .50f); line(.18f, .72f, .54f, .72f)
            }
            WatchioIconKind.InputMode -> {
                drawRoundRect(tint, Offset(size.width * .13f, size.height * .17f), Size(size.width * .74f, size.height * .52f), CornerRadius(unit * .06f), style = stroke)
                line(.34f, .83f, .66f, .83f); line(.50f, .69f, .50f, .83f)
            }
            WatchioIconKind.Appearance -> {
                drawCircle(tint, unit * .34f, center, style = stroke)
                drawArc(tint, 90f, 180f, true, Offset(size.width * .16f, size.height * .16f), Size(unit * .68f, unit * .68f))
            }
            WatchioIconKind.BackupRestore -> {
                drawRoundRect(tint, Offset(size.width * .16f, size.height * .34f), Size(size.width * .68f, size.height * .48f), CornerRadius(unit * .06f), style = stroke)
                line(.50f, .10f, .50f, .58f); line(.35f, .25f, .50f, .10f); line(.65f, .25f, .50f, .10f)
            }
            WatchioIconKind.Updates -> {
                drawArc(tint, 30f, 290f, false, Offset(size.width * .15f, size.height * .15f), Size(size.width * .70f, size.height * .70f), style = stroke)
                drawPath(Path().apply { moveTo(size.width * .77f, size.height * .14f); lineTo(size.width * .88f, size.height * .38f); lineTo(size.width * .63f, size.height * .33f); close() }, tint)
            }
            WatchioIconKind.Announcements -> {
                drawPath(Path().apply { moveTo(size.width * .16f, size.height * .43f); lineTo(size.width * .69f, size.height * .22f); lineTo(size.width * .69f, size.height * .74f); lineTo(size.width * .16f, size.height * .57f); close() }, tint, style = stroke)
                line(.20f, .58f, .30f, .82f); line(.79f, .34f, .90f, .25f); line(.80f, .64f, .91f, .73f)
            }
            WatchioIconKind.More -> repeat(3) { i -> drawCircle(tint, unit * .055f, Offset(size.width * .50f, size.height * (.22f + i * .28f))) }
            WatchioIconKind.Back -> { line(.72f, .18f, .28f, .50f); line(.28f, .50f, .72f, .82f) }
        }
    }
}
