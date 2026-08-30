package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.movies.MovieDetails
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.movies.WatchioMovieItem
import com.watchioiptv.nativeapp.data.xtream.XtreamVodInfoResponseDto
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.feature.movies.extractReleaseYear
import com.watchioiptv.nativeapp.feature.movies.formatMovieMetaLine
import com.watchioiptv.nativeapp.feature.movies.formatRating
import com.watchioiptv.nativeapp.feature.movies.formatRuntime
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

    // --------------------------------------------------------------------------
    // Movie Details UI Refresh — extractReleaseYear unit tests
    // --------------------------------------------------------------------------

    @Test
    fun extractReleaseYearHandlesStandardIsoDates() {
        assertEquals("2016", extractReleaseYear("2016-01-01"))
        assertEquals("1999", extractReleaseYear("1999-12-31"))
        assertEquals("2024", extractReleaseYear("2024/05/20"))
    }

    @Test
    fun extractReleaseYearHandlesYearOnlyOrText() {
        assertEquals("2016", extractReleaseYear("2016"))
        assertEquals("2016", extractReleaseYear("Jan 2016"))
        assertEquals("2020", extractReleaseYear(" 2020 "))
    }

    @Test
    fun extractReleaseYearRejectsInvalidOrNull() {
        assertNull(extractReleaseYear(null))
        assertNull(extractReleaseYear(""))
        assertNull(extractReleaseYear("   "))
        assertNull(extractReleaseYear("N/A"))
        assertNull(extractReleaseYear("unknown"))
        assertNull(extractReleaseYear("123"))
    }

    // --------------------------------------------------------------------------
    // Movie Details UI Refresh — formatRuntime unit tests
    // --------------------------------------------------------------------------

    @Test
    fun formatRuntimeHandlesHms() {
        assertEquals("1h 51m", formatRuntime("01:51:15"))
        assertEquals("1h 51m", formatRuntime("1:51:15"))
        assertEquals("2h 5m", formatRuntime("02:05:00"))
        assertEquals("45m", formatRuntime("00:45:00"))
        assertEquals("1h", formatRuntime("01:00:00"))
    }

    @Test
    fun formatRuntimeHandlesMmSs() {
        assertEquals("1h 51m", formatRuntime("111:00"))
        assertEquals("45m", formatRuntime("45:00"))
    }

    @Test
    fun formatRuntimeHandlesMinutesWord() {
        assertEquals("1h 51m", formatRuntime("111 min"))
        assertEquals("1h 51m", formatRuntime("111 mins"))
        assertEquals("45m", formatRuntime("45 minutes"))
        assertEquals("2h", formatRuntime("120m"))
    }

    @Test
    fun formatRuntimeHandlesRawNumber() {
        assertEquals("1h 51m", formatRuntime("111"))
        assertEquals("45m", formatRuntime("45"))
        assertEquals("1h 51m", formatRuntime("6660")) // 6660 seconds
    }

    @Test
    fun formatRuntimePreservesCleanFormattedStrings() {
        assertEquals("1h 51m", formatRuntime("1h 51m"))
        assertEquals("2h", formatRuntime("2h"))
        assertEquals("45m", formatRuntime("45m"))
    }

    @Test
    fun formatRuntimeRejectsNullOrInvalid() {
        assertNull(formatRuntime(null))
        assertNull(formatRuntime(""))
        assertNull(formatRuntime("   "))
        assertNull(formatRuntime("invalid"))
        assertNull(formatRuntime("0"))
    }

    // --------------------------------------------------------------------------
    // Movie Details UI Refresh — formatMovieMetaLine unit tests
    // --------------------------------------------------------------------------

    @Test
    fun formatMovieMetaLineCombinesValidParts() {
        val dummyMovie = WatchioMovieItem(
            providerId = ProviderId("test"),
            providerType = ProviderType.Xtream,
            id = "1",
            name = "Test Movie",
            posterUrl = null,
            categoryId = null,
            rating = "5.023",
            genre = "Crime, Thriller",
            containerExtension = "mp4",
            trailerKey = null,
            serverOrder = 0,
            directUrl = null,
        )
        val details = MovieDetails(
            movie = dummyMovie,
            title = "Test Movie",
            posterUrl = null,
            backdropUrl = null,
            plot = "A great movie plot.",
            cast = "Actor One, Actor Two",
            director = "Director Name",
            genre = "Crime, Thriller",
            releaseDate = "2016-01-01",
            rating = "5.023",
            runtime = "01:51:15",
            trailerKey = "trailer123",
            tmdbId = 12345,
        )

        assertEquals("2016 • 1h 51m • Crime, Thriller • ★ 5.0", formatMovieMetaLine(details))
    }

    @Test
    fun formatMovieMetaLineOmitsNullOrBlankPartsWithoutExtraBullets() {
        val dummyMovie = WatchioMovieItem(
            providerId = ProviderId("test"),
            providerType = ProviderType.Xtream,
            id = "1",
            name = "Test Movie",
            posterUrl = null,
            categoryId = null,
            rating = null,
            genre = null,
            containerExtension = "mp4",
            trailerKey = null,
            serverOrder = 0,
            directUrl = null,
        )
        val details = MovieDetails(
            movie = dummyMovie,
            title = "Test Movie",
            posterUrl = null,
            backdropUrl = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            releaseDate = "2024",
            rating = null,
            runtime = "45",
            trailerKey = null,
            tmdbId = null,
        )

        assertEquals("2024 • 45m", formatMovieMetaLine(details))
    }
}
