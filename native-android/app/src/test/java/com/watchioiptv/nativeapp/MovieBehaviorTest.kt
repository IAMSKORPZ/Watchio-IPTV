package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.xtream.XtreamVodInfoResponseDto
import com.watchioiptv.nativeapp.feature.movies.formatRating
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieBehaviorTest {
    @Test
    fun vodInfoAcceptsStringOrArrayBackdropAndFlexibleTmdbId() {
        val json = Json { ignoreUnknownKeys = true }
        val array = json.decodeFromString<XtreamVodInfoResponseDto>(
            """{"info":{"name":"Movie","tmdb_id":"123","backdrop_path":["http://example.invalid/back.jpg"]}}""",
        )
        val string = json.decodeFromString<XtreamVodInfoResponseDto>(
            """{"info":{"name":"Movie","tmdb_id":123,"backdrop_path":"http://example.invalid/back.jpg"}}""",
        )

        assertEquals(123, array.info?.tmdbId)
        assertEquals(listOf("http://example.invalid/back.jpg"), array.info?.backdropPath)
        assertEquals(123, string.info?.tmdbId)
        assertEquals(listOf("http://example.invalid/back.jpg"), string.info?.backdropPath)
    }

    @Test
    fun resumeThresholdsAvoidStartAndNearComplete() {
        assertFalse(MoviesRepository.shouldResumePosition(10_000, 100_000))
        assertTrue(MoviesRepository.shouldResumePosition(90_000, 600_000))
        assertFalse(MoviesRepository.shouldResumePosition(590_000, 600_000))
    }

    // --------------------------------------------------------------------------
    // Phase 14.2I.1 — formatRating unit tests
    // --------------------------------------------------------------------------

    @Test
    fun formatRatingNullReturnsNull() {
        assertNull(formatRating(null))
    }

    @Test
    fun formatRatingBlankReturnsNull() {
        assertNull(formatRating(""))
        assertNull(formatRating("   "))
    }

    @Test
    fun formatRatingZeroStringReturnsNull() {
        assertNull(formatRating("0"))
        assertNull(formatRating("0.0"))
        assertNull(formatRating("0.000"))
    }

    @Test
    fun formatRatingNegativeReturnsNull() {
        assertNull(formatRating("-1"))
        assertNull(formatRating("-0.5"))
    }

    @Test
    fun formatRatingNonNumericReturnsNull() {
        assertNull(formatRating("N/A"))
        assertNull(formatRating("unrated"))
        assertNull(formatRating("--"))
    }

    @Test
    fun formatRatingDecimalRoundsToOnePlace() {
        assertEquals("★ 6.5", formatRating("6.458"))
        assertEquals("★ 6.5", formatRating("6.50"))
        assertEquals("★ 8.2", formatRating("8.2"))
    }

    @Test
    fun formatRatingWholeNumberShowsDecimalPoint() {
        assertEquals("★ 7.0", formatRating("7"))
        assertEquals("★ 5.0", formatRating("5"))
        assertEquals("★ 10.0", formatRating("10"))
    }

    @Test
    fun formatRatingTrimmedInput() {
        assertEquals("★ 7.5", formatRating(" 7.5 "))
    }
}
