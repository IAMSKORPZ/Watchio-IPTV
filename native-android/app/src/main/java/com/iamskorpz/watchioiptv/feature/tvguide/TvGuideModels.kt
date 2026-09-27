package com.iamskorpz.watchioiptv.feature.tvguide

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class WatchioGuideChannel(
    val providerId: ProviderId,
    val channelId: String,
    val displayName: String,
    val logo: String?,
    val channelNumber: String?,
    val category: String?,
    val isFavourite: Boolean,
    val isCurrentlyPlaying: Boolean,
    val epgChannelId: String?,
    val liveChannel: LiveTvChannel,
)

data class WatchioGuideProgramme(
    val programmeId: String,
    val channelId: String,
    val epgChannelId: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val category: String? = null,
    val icon: String? = null,
    val rating: String? = null,
    val episodeInfo: String? = null,
    val progress: Float,
    val isLiveNow: Boolean,
)

data class WatchioGuideWindow(
    val startUtcMs: Long,
    val endUtcMs: Long,
    val day: LocalDate,
    val days: List<WatchioGuideDay>,
)

data class WatchioGuideDay(
    val date: LocalDate,
    val label: String,
)

data class ProgrammeDetails(
    val programme: WatchioGuideProgramme,
    val channel: WatchioGuideChannel,
)

data class TvGuideLayoutDimensions(
    val channelWidthDp: Float,
    val rowHeightDp: Float,
    val isCompact: Boolean,
    val isMedium: Boolean,
    val heroHeightDp: Float = 74f,
) {
    companion object {
        fun calculate(maxWidthDp: Float): TvGuideLayoutDimensions = when {
            maxWidthDp < 600f -> TvGuideLayoutDimensions(
                channelWidthDp = 120f,
                rowHeightDp = 48f,
                isCompact = true,
                isMedium = false,
                heroHeightDp = 74f,
            )
            maxWidthDp < 980f -> TvGuideLayoutDimensions(
                channelWidthDp = 160f,
                rowHeightDp = 46f,
                isCompact = false,
                isMedium = true,
                heroHeightDp = 74f,
            )
            else -> TvGuideLayoutDimensions(
                channelWidthDp = 200f,
                rowHeightDp = 54f,
                isCompact = false,
                isMedium = false,
                heroHeightDp = 90f,
            )
        }

        fun visibleRowCapacity(viewportHeightDp: Float, fixedContentHeightDp: Float, rowHeightDp: Float): Int =
            ((viewportHeightDp - fixedContentHeightDp).coerceAtLeast(0f) / rowHeightDp).toInt()
    }
}

object TvGuideTimeline {
    const val MinProgrammeMinutes: Long = 5L
    const val MaxProgrammeHours: Long = 8L
    const val MinuteWidthDp: Float = 4f
    const val SlotMinutes: Long = 30L
    const val PastContextMinutes: Long = 60L
    const val FutureContextHours: Long = 5L

    fun defaultWindow(nowEpochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): WatchioGuideWindow {
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val startMinute = (now.minute / 30) * 30
        val start = now.withMinute(startMinute).withSecond(0).withNano(0).minusMinutes(PastContextMinutes)
        val end = start.plusHours(FutureContextHours + 1)
        val today = now.toLocalDate()
        return WatchioGuideWindow(
            startUtcMs = start.toInstant().toEpochMilli(),
            endUtcMs = end.toInstant().toEpochMilli(),
            day = today,
            days = (0L..6L).map { offset ->
                val date = today.plusDays(offset)
                WatchioGuideDay(
                    date = date,
                    label = if (offset == 0L) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                )
            },
        )
    }

    fun windowForDay(day: LocalDate, nowEpochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): WatchioGuideWindow {
        val current = defaultWindow(nowEpochMs, zoneId)
        if (day == current.day) return current
        val start = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return current.copy(startUtcMs = start, endUtcMs = end, day = day)
    }

    fun widthDp(startUtcMs: Long, endUtcMs: Long, windowStartUtcMs: Long, windowEndUtcMs: Long): Float {
        val clippedStart = startUtcMs.coerceAtLeast(windowStartUtcMs)
        val clippedEnd = endUtcMs.coerceAtMost(windowEndUtcMs)
        val minutes = ((clippedEnd - clippedStart) / 60_000f)
            .coerceIn(0f, MaxProgrammeHours * 60f)
        return (minutes * MinuteWidthDp).roundToInt().coerceAtLeast((MinProgrammeMinutes * MinuteWidthDp).roundToInt()).toFloat()
    }

    fun offsetDp(startUtcMs: Long, windowStartUtcMs: Long): Float =
        (((startUtcMs - windowStartUtcMs).coerceAtLeast(0L) / 60_000f) * MinuteWidthDp)

    fun widthForGapDp(durationMs: Long): Float =
        ((durationMs.coerceAtLeast(0L) / 60_000f) * MinuteWidthDp)
            .coerceAtMost(MaxProgrammeHours * 60f * MinuteWidthDp)
    fun nowLineOffsetDp(
        nowUtcMs: Long,
        windowStartUtcMs: Long,
        windowEndUtcMs: Long,
        channelWidthDp: Float,
        scrollDp: Float,
    ): Float? {
        if (nowUtcMs < windowStartUtcMs || nowUtcMs > windowEndUtcMs) return null
        val offset = channelWidthDp + offsetDp(nowUtcMs, windowStartUtcMs) - scrollDp
        return offset.takeIf { it >= channelWidthDp }
    }

    fun nowLineOffsetDp(
        nowUtcMs: Long,
        windowStartUtcMs: Long,
        channelWidthDp: Float,
        scrollDp: Float,
    ): Float? = nowLineOffsetDp(
        nowUtcMs = nowUtcMs,
        windowStartUtcMs = windowStartUtcMs,
        windowEndUtcMs = Long.MAX_VALUE,
        channelWidthDp = channelWidthDp,
        scrollDp = scrollDp,
    )

    fun nowLineOffsetDp(
        nowUtcMs: Long,
        windowStartUtcMs: Long,
        channelWidthDp: Float,
        scrollPx: Int,
        density: Float = 1.0f,
    ): Float? {
        val scrollDp = if (density > 0f) scrollPx / density else scrollPx.toFloat()
        return nowLineOffsetDp(
            nowUtcMs = nowUtcMs,
            windowStartUtcMs = windowStartUtcMs,
            windowEndUtcMs = Long.MAX_VALUE,
            channelWidthDp = channelWidthDp,
            scrollDp = scrollDp,
        )
    }

    fun progress(nowUtcMs: Long, startUtcMs: Long, endUtcMs: Long): Float {
        val duration = endUtcMs - startUtcMs
        if (duration <= 0L) return 0f
        return ((nowUtcMs - startUtcMs).toFloat() / duration).coerceIn(0f, 1f)
    }
}

object TvGuideCategoryNavigation {
    fun selectedIndex(categoryIds: List<String>, selectedCategoryId: String?): Int =
        categoryIds.indexOf(selectedCategoryId).takeIf { it >= 0 } ?: 0

    fun previousIndex(categoryIds: List<String>, selectedCategoryId: String?): Int? =
        (selectedIndex(categoryIds, selectedCategoryId) - 1).takeIf { it >= 0 }

    fun nextIndex(categoryIds: List<String>, selectedCategoryId: String?): Int? =
        (selectedIndex(categoryIds, selectedCategoryId) + 1).takeIf { it < categoryIds.size }
}
