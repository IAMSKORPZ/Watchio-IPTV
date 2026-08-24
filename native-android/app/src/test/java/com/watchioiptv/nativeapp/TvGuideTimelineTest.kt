package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.feature.tvguide.TvGuideTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TvGuideTimelineTest {
    private val windowStart = 1_000_000L
    private val minute = 60_000L

    @Test
    fun programmeWidthsReflectDuration() {
        assertEquals(120f, TvGuideTimeline.widthDp(windowStart, windowStart + 30 * minute, windowStart, windowStart + 4 * 60 * minute))
        assertEquals(240f, TvGuideTimeline.widthDp(windowStart, windowStart + 60 * minute, windowStart, windowStart + 4 * 60 * minute))
        assertEquals(480f, TvGuideTimeline.widthDp(windowStart, windowStart + 120 * minute, windowStart, windowStart + 4 * 60 * minute))
    }

    @Test
    fun widthClipsToVisibleWindowAndHandlesBadDurations() {
        val windowEnd = windowStart + 120 * minute
        assertEquals(120f, TvGuideTimeline.widthDp(windowStart - 30 * minute, windowStart + 30 * minute, windowStart, windowEnd))
        assertEquals(120f, TvGuideTimeline.widthDp(windowEnd - 30 * minute, windowEnd + 30 * minute, windowStart, windowEnd))
        assertEquals(20f, TvGuideTimeline.widthDp(windowStart + 10 * minute, windowStart + 10 * minute, windowStart, windowEnd))
        assertEquals(20f, TvGuideTimeline.widthDp(windowStart + 20 * minute, windowStart + 10 * minute, windowStart, windowEnd))
        assertEquals(480f, TvGuideTimeline.widthDp(windowStart, windowStart + 24 * 60 * minute, windowStart, windowEnd))
        assertEquals(20f, TvGuideTimeline.widthDp(windowEnd + minute, windowEnd + 2 * minute, windowStart, windowEnd))
        assertEquals(20f, TvGuideTimeline.widthDp(windowStart - 2 * minute, windowStart - minute, windowStart, windowEnd))
    }

    @Test
    fun nowLineOffsetIsHiddenBeforeScrollableViewport() {
        assertEquals(420f, TvGuideTimeline.nowLineOffsetDp(windowStart + 60 * minute, windowStart, 180f, 0))
        assertEquals(180f, TvGuideTimeline.nowLineOffsetDp(windowStart + 60 * minute, windowStart, 180f, 240))
        assertNull(TvGuideTimeline.nowLineOffsetDp(windowStart + 60 * minute, windowStart, 180f, 241))
    }

    @Test
    fun gapWidthNeverGoesNegativeOrUnbounded() {
        assertEquals(0f, TvGuideTimeline.widthForGapDp(-minute))
        assertEquals(0f, TvGuideTimeline.widthForGapDp(0L))
        assertEquals(120f, TvGuideTimeline.widthForGapDp(30 * minute))
        assertEquals(1920f, TvGuideTimeline.widthForGapDp(24 * 60 * minute))
    }

    @Test
    fun progressClamps() {
        assertEquals(0f, TvGuideTimeline.progress(windowStart - minute, windowStart, windowStart + 10 * minute))
        assertEquals(0.5f, TvGuideTimeline.progress(windowStart + 5 * minute, windowStart, windowStart + 10 * minute))
        assertEquals(1f, TvGuideTimeline.progress(windowStart + 20 * minute, windowStart, windowStart + 10 * minute))
        assertEquals(0f, TvGuideTimeline.progress(windowStart, windowStart, windowStart))
    }

    @Test
    fun dayWindowUsesZoneRules() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val window = TvGuideTimeline.windowForDay(java.time.LocalDate.of(2026, 3, 29), now, zone)
        assertEquals(23 * 60 * minute, window.endUtcMs - window.startUtcMs)
    }
}
