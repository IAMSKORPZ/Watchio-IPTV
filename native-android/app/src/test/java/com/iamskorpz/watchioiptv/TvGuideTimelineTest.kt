package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideLayoutDimensions
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideCategoryNavigation
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate

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
        assertEquals(420f, TvGuideTimeline.nowLineOffsetDp(windowStart + 60 * minute, windowStart, 180f, 0f))
        assertEquals(180f, TvGuideTimeline.nowLineOffsetDp(windowStart + 60 * minute, windowStart, 180f, 240f))
        assertNull(TvGuideTimeline.nowLineOffsetDp(windowStart + 60 * minute, windowStart, 180f, 241f))
    }

    @Test
    fun nowLineRespectsExplicitScrollDpAndBounds() {
        val windowEnd = windowStart + 180 * minute
        val channelWidthDp = 140f

        // zero scroll
        val offsetZero = TvGuideTimeline.nowLineOffsetDp(
            nowUtcMs = windowStart + 30 * minute,
            windowStartUtcMs = windowStart,
            windowEndUtcMs = windowEnd,
            channelWidthDp = channelWidthDp,
            scrollDp = 0f,
        )
        // 30 min * 4dp/min = 120dp. 140dp channel width + 120dp = 260dp
        assertEquals(260f, offsetZero)

        // positive scroll of 60dp
        val offsetScrolled = TvGuideTimeline.nowLineOffsetDp(
            nowUtcMs = windowStart + 30 * minute,
            windowStartUtcMs = windowStart,
            windowEndUtcMs = windowEnd,
            channelWidthDp = channelWidthDp,
            scrollDp = 60f,
        )
        // 260dp - 60dp = 200dp
        assertEquals(200f, offsetScrolled)

        // before-window NOW returns null
        assertNull(
            TvGuideTimeline.nowLineOffsetDp(
                nowUtcMs = windowStart - 10 * minute,
                windowStartUtcMs = windowStart,
                windowEndUtcMs = windowEnd,
                channelWidthDp = channelWidthDp,
                scrollDp = 0f,
            ),
        )

        // after-window NOW returns null
        assertNull(
            TvGuideTimeline.nowLineOffsetDp(
                nowUtcMs = windowEnd + 10 * minute,
                windowStartUtcMs = windowStart,
                windowEndUtcMs = windowEnd,
                channelWidthDp = channelWidthDp,
                scrollDp = 0f,
            ),
        )

        // scrolled past channel column returns null
        assertNull(
            TvGuideTimeline.nowLineOffsetDp(
                nowUtcMs = windowStart + 30 * minute,
                windowStartUtcMs = windowStart,
                windowEndUtcMs = windowEnd,
                channelWidthDp = channelWidthDp,
                scrollDp = 121f, // 140 + 120 - 121 = 139 < 140
            ),
        )
    }

    @Test
    fun nowLineDensityConversionYieldsConsistentDp() {
        val now = windowStart + 60 * minute
        val channelWidth = 200f
        // 60 min * 4 dp/min = 240 dp timeline offset -> total 440 dp
        // Scroll by 100 dp equivalent in pixels at different screen densities:
        // Density 1.0 (mdpi): 100 px = 100 dp -> 440 - 100 = 340 dp
        val offsetDensity1 = TvGuideTimeline.nowLineOffsetDp(now, windowStart, channelWidth, scrollPx = 100, density = 1.0f)
        assertEquals(340f, offsetDensity1)

        // Density 2.0 (xhdpi): 200 px = 100 dp -> 440 - 100 = 340 dp
        val offsetDensity2 = TvGuideTimeline.nowLineOffsetDp(now, windowStart, channelWidth, scrollPx = 200, density = 2.0f)
        assertEquals(340f, offsetDensity2)

        // Density 3.0 (xxhdpi/S22): 300 px = 100 dp -> 440 - 100 = 340 dp
        val offsetDensity3 = TvGuideTimeline.nowLineOffsetDp(now, windowStart, channelWidth, scrollPx = 300, density = 3.0f)
        assertEquals(340f, offsetDensity3)

        // Density 3.75: 375 px = 100 dp -> 440 - 100 = 340 dp
        val offsetDensity375 = TvGuideTimeline.nowLineOffsetDp(now, windowStart, channelWidth, scrollPx = 375, density = 3.75f)
        assertEquals(340f, offsetDensity375)
    }

    @Test
    fun responsiveLayoutDimensionsMatchBreakpoints() {
        val phonePortrait = TvGuideLayoutDimensions.calculate(400f)
        assertEquals(120f, phonePortrait.channelWidthDp)
        assertEquals(48f, phonePortrait.rowHeightDp)
        assertEquals(74f, phonePortrait.heroHeightDp)
        assertTrue(phonePortrait.isCompact)
        assertFalse(phonePortrait.isMedium)

        val phoneLandscape = TvGuideLayoutDimensions.calculate(800f)
        assertEquals(160f, phoneLandscape.channelWidthDp)
        assertEquals(46f, phoneLandscape.rowHeightDp)
        assertEquals(74f, phoneLandscape.heroHeightDp)
        assertFalse(phoneLandscape.isCompact)
        assertTrue(phoneLandscape.isMedium)

        val tvLarge = TvGuideLayoutDimensions.calculate(1200f)
        assertEquals(200f, tvLarge.channelWidthDp)
        assertEquals(54f, tvLarge.rowHeightDp)
        assertEquals(90f, tvLarge.heroHeightDp)
        assertFalse(tvLarge.isCompact)
        assertFalse(tvLarge.isMedium)
    }

    @Test
    fun compactTvLayoutLeavesRoomForMultipleGuideRows() {
        // Phone landscape (S22 at 360dp height): 74dp hero + 56dp header + 4dp spacer + 12dp outer padding + 30dp category = 176dp fixed content -> 4 full rows
        assertEquals(4, TvGuideLayoutDimensions.visibleRowCapacity(360f, 74f + 56f + 4f + 12f + 30f, 46f))
        // 16:9 TV (540dp height): 90dp hero + 60dp header + 6dp spacer + 24dp outer padding + 32dp category = 212dp fixed content -> 6 full rows
        assertEquals(6, TvGuideLayoutDimensions.visibleRowCapacity(540f, 90f + 60f + 6f + 24f + 32f, 54f))
    }

    @Test
    fun miniPlayerMaintainsTrueSixteenByNineRatioAcrossTiers() {
        val landscape = TvGuideLayoutDimensions.calculate(800f)
        val landscapePlayerHeight = landscape.heroHeightDp - 4f
        val landscapePlayerWidth = landscapePlayerHeight * 16f / 9f
        assertEquals(70f, landscapePlayerHeight, 0.01f)
        assertEquals(124.44f, landscapePlayerWidth, 0.1f)
        assertEquals(16f / 9f, landscapePlayerWidth / landscapePlayerHeight, 0.01f)

        val tv = TvGuideLayoutDimensions.calculate(1200f)
        val tvPlayerHeight = tv.heroHeightDp - 4f
        val tvPlayerWidth = tvPlayerHeight * 16f / 9f
        assertEquals(86f, tvPlayerHeight, 0.01f)
        assertEquals(152.88f, tvPlayerWidth, 0.1f)
        assertEquals(16f / 9f, tvPlayerWidth / tvPlayerHeight, 0.01f)
    }

    @Test
    fun thirtyMinuteSlotAlignment() {
        assertEquals(30L, TvGuideTimeline.SlotMinutes)
        assertEquals(4f, TvGuideTimeline.MinuteWidthDp)
        val slotWidthDp = TvGuideTimeline.SlotMinutes * TvGuideTimeline.MinuteWidthDp
        assertEquals(120f, slotWidthDp)

        // Widths for 30m, 60m, 90m match exact slot increments
        assertEquals(120f, TvGuideTimeline.widthDp(windowStart, windowStart + 30 * minute, windowStart, windowStart + 180 * minute))
        assertEquals(240f, TvGuideTimeline.widthDp(windowStart, windowStart + 60 * minute, windowStart, windowStart + 180 * minute))
        assertEquals(360f, TvGuideTimeline.widthDp(windowStart, windowStart + 90 * minute, windowStart, windowStart + 180 * minute))
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

    @Test
    fun defaultWindowOffersTodayAndFollowingSixDays() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 10, 24, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val window = TvGuideTimeline.defaultWindow(now, zone)

        assertEquals(7, window.days.size)
        assertEquals("Today", window.days.first().label)
        assertEquals(LocalDate.of(2026, 10, 24), window.days.first().date)
        assertEquals(LocalDate.of(2026, 10, 30), window.days.last().date)
    }

    @Test
    fun dayWindowHonoursAutumnTimezoneBoundary() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val window = TvGuideTimeline.windowForDay(LocalDate.of(2026, 10, 25), now, zone)
        assertEquals(25 * 60 * minute, window.endUtcMs - window.startUtcMs)
    }

    @Test
    fun categoryNavigationPreservesOrderAndStopsAtBoundaries() {
        val ids = listOf("all", "news", "sport")
        assertNull(TvGuideCategoryNavigation.previousIndex(ids, "all"))
        assertEquals(0, TvGuideCategoryNavigation.previousIndex(ids, "news"))
        assertEquals(2, TvGuideCategoryNavigation.nextIndex(ids, "news"))
        assertNull(TvGuideCategoryNavigation.nextIndex(ids, "sport"))
        assertEquals(0, TvGuideCategoryNavigation.selectedIndex(ids, "missing"))
    }
}
