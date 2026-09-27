package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.database.EpgChannelEntity
import com.iamskorpz.watchioiptv.core.database.EpgProgrammeEntity
import com.iamskorpz.watchioiptv.data.epg.EpgMatchIndex
import com.iamskorpz.watchioiptv.data.epg.EpgNowNextCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgNowNextCalculatorTest {

    @Test
    fun calculatesNowNextAndLaterWithProgress() {
        val now = 10_500L
        val programmes = listOf(
            programme("p1", "Now Show", 10_000L, 11_000L),
            programme("p2", "Next Show", 11_000L, 12_000L),
            programme("p3", "Later Show", 12_000L, 13_000L),
        )

        val result = EpgNowNextCalculator.calculate(programmes, now)

        assertEquals("Now Show", result.currentTitle)
        assertEquals(10_000L, result.currentStartEpochMs)
        assertEquals(11_000L, result.currentEndEpochMs)
        assertEquals(0.5f, result.progress, 0.001f)
        assertTrue(result.hasNow)

        assertEquals("Next Show", result.nextTitle)
        assertEquals(11_000L, result.nextStartEpochMs)
        assertEquals(12_000L, result.nextEndEpochMs)
        assertTrue(result.hasNext)

        assertEquals("Later Show", result.laterTitle)
        assertEquals(12_000L, result.laterStartEpochMs)
        assertEquals(13_000L, result.laterEndEpochMs)
        assertTrue(result.hasLater)
        assertTrue(result.hasAny)
    }

    @Test
    fun calculatesOnlyNowWhenNoSubsequentProgrammes() {
        val now = 10_250L
        val programmes = listOf(
            programme("p1", "Solo Show", 10_000L, 11_000L),
        )

        val result = EpgNowNextCalculator.calculate(programmes, now)

        assertEquals("Solo Show", result.currentTitle)
        assertEquals(0.25f, result.progress, 0.001f)
        assertTrue(result.hasNow)
        assertFalse(result.hasNext)
        assertFalse(result.hasLater)
        assertNull(result.nextTitle)
        assertNull(result.laterTitle)
    }

    @Test
    fun calculatesNowAndNextWhenNoLaterProgramme() {
        val now = 10_500L
        val programmes = listOf(
            programme("p1", "Show A", 10_000L, 11_000L),
            programme("p2", "Show B", 11_000L, 12_000L),
        )

        val result = EpgNowNextCalculator.calculate(programmes, now)

        assertEquals("Show A", result.currentTitle)
        assertEquals("Show B", result.nextTitle)
        assertNull(result.laterTitle)
        assertTrue(result.hasNow)
        assertTrue(result.hasNext)
        assertFalse(result.hasLater)
    }

    @Test
    fun handlesGapWithNoNowProgramme() {
        val now = 11_500L
        val programmes = listOf(
            programme("p1", "Past Show", 10_000L, 11_000L),
            programme("p2", "Upcoming Show", 12_000L, 13_000L),
            programme("p3", "Far Future Show", 13_000L, 14_000L),
        )

        val result = EpgNowNextCalculator.calculate(programmes, now)

        assertNull(result.currentTitle)
        assertFalse(result.hasNow)
        assertEquals(0f, result.progress, 0f)

        assertEquals("Upcoming Show", result.nextTitle)
        assertEquals(12_000L, result.nextStartEpochMs)
        assertTrue(result.hasNext)

        assertEquals("Far Future Show", result.laterTitle)
        assertEquals(13_000L, result.laterStartEpochMs)
        assertTrue(result.hasLater)
    }

    @Test
    fun handlesEmptyAndInvalidProgrammes() {
        val resultEmpty = EpgNowNextCalculator.calculate(emptyList(), 10_000L)
        assertFalse(resultEmpty.hasAny)
        assertNull(resultEmpty.currentTitle)
        assertNull(resultEmpty.nextTitle)
        assertNull(resultEmpty.laterTitle)
        assertEquals(0f, resultEmpty.progress, 0f)

        val invalid = listOf(
            programme("inv", "Invalid", 10_000L, 10_000L),
            programme("inv2", "Reversed", 12_000L, 11_000L),
        )
        val resultInvalid = EpgNowNextCalculator.calculate(invalid, 10_000L)
        assertFalse(resultInvalid.hasAny)
    }

    @Test
    fun handlesOverlappingProgrammesGracefully() {
        val now = 10_450L
        val programmes = listOf(
            programme("p1", "Broad Block", 10_000L, 12_000L),
            programme("p2", "Specific Sub-Block", 10_300L, 11_300L),
            programme("p3", "Afternoon Show", 12_000L, 13_000L),
        )

        val result = EpgNowNextCalculator.calculate(programmes, now)

        assertEquals("Specific Sub-Block", result.currentTitle)
        assertEquals("Afternoon Show", result.nextTitle)
    }

    @Test
    fun epgMatchIndexMatchesAcrossAllCriteria() {
        val channels = listOf(
            EpgChannelEntity("p1", "bbc.one", "BBC One", "bbc one", null, 0L),
            EpgChannelEntity("p1", "ITV.1", "ITV 1", "itv 1", null, 0L),
            EpgChannelEntity("p1", "ch4.uk", "Channel 4", "channel 4", null, 0L),
        )
        val index = EpgMatchIndex(channels)

        // Exact ID match
        assertEquals("bbc.one", index.match("bbc.one", "Different Name"))
        // Case-insensitive ID match
        assertEquals("ITV.1", index.match("itv.1", "Different Name"))
        // Exact name match
        assertEquals("ch4.uk", index.match(null, "Channel 4"))
        // Case-insensitive / normalized name match
        assertEquals("bbc.one", index.match(null, "BBC ONE"))
        // Compact name match
        assertEquals("ITV.1", index.match(null, "UK: ITV 1 HD"))
        // Compact ID match
        assertEquals("ch4.uk", index.match(null, "Channel 4 UK"))
        // Non-matching channel
        assertNull(index.match("unknown.id", "Unknown Channel"))
    }

    private fun programme(id: String, title: String, startMs: Long, endMs: Long) =
        EpgProgrammeEntity(
            providerId = "p1",
            epgChannelId = "ch1",
            programmeId = id,
            title = title,
            description = "Description for $title",
            startTimeEpochMs = startMs,
            endTimeEpochMs = endMs,
            updatedAtEpochMs = 1L,
        )
}
