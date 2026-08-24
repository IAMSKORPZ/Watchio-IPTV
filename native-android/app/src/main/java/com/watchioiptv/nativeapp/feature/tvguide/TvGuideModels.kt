package com.watchioiptv.nativeapp.feature.tvguide

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.live.LiveTvChannel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
            days = listOf(
                WatchioGuideDay(today.minusDays(1), "Yesterday"),
                WatchioGuideDay(today, "Today"),
                WatchioGuideDay(today.plusDays(1), "Tomorrow"),
            ),
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

    fun nowLineOffsetDp(nowUtcMs: Long, windowStartUtcMs: Long, channelWidthDp: Float, scrollPx: Int): Float? {
        val offset = channelWidthDp + offsetDp(nowUtcMs, windowStartUtcMs) - scrollPx
        return offset.takeIf { it >= channelWidthDp }
    }

    fun progress(nowUtcMs: Long, startUtcMs: Long, endUtcMs: Long): Float {
        val duration = endUtcMs - startUtcMs
        if (duration <= 0L) return 0f
        return ((nowUtcMs - startUtcMs).toFloat() / duration).coerceIn(0f, 1f)
    }
}
