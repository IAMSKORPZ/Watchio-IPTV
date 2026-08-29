package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.library.ContinueWatchingItem
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.series.SeriesRepository
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.ui.components.formatPlaybackTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodResumePlaybackTest {

    @Test
    fun completionDerivedNaturallyFromPositionAndDuration() {
        // Under 90% and remaining > 2 minutes -> not completed
        assertFalse(MoviesRepository.isCompletedPosition(positionMs = 100_000L, durationMs = 600_000L))
        assertFalse(MoviesRepository.isCompletedPosition(positionMs = 500_000L, durationMs = 1_000_000L))

        // Equal or exceeding duration -> completed
        assertTrue(MoviesRepository.isCompletedPosition(positionMs = 600_000L, durationMs = 600_000L))
        assertTrue(MoviesRepository.isCompletedPosition(positionMs = 650_000L, durationMs = 600_000L))

        // Progress >= 90% -> completed
        assertTrue(MoviesRepository.isCompletedPosition(positionMs = 900_000L, durationMs = 1_000_000L))
        assertTrue(MoviesRepository.isCompletedPosition(positionMs = 950_000L, durationMs = 1_000_000L))

        // Remaining <= 2 minutes (120_000ms) -> completed
        assertTrue(MoviesRepository.isCompletedPosition(positionMs = 490_000L, durationMs = 600_000L))
        assertTrue(MoviesRepository.isCompletedPosition(positionMs = 480_000L, durationMs = 600_000L))

        // Invalid inputs
        assertFalse(MoviesRepository.isCompletedPosition(positionMs = null, durationMs = 600_000L))
        assertFalse(MoviesRepository.isCompletedPosition(positionMs = 100_000L, durationMs = null))
        assertFalse(MoviesRepository.isCompletedPosition(positionMs = -10_000L, durationMs = 600_000L))
        assertFalse(MoviesRepository.isCompletedPosition(positionMs = 100_000L, durationMs = 0L))
    }

    @Test
    fun shouldResumeThresholds() {
        // Less than 30 seconds -> false
        assertFalse(MoviesRepository.shouldResumePosition(positionMs = 20_000L, durationMs = 600_000L))
        assertFalse(MoviesRepository.shouldResumePosition(positionMs = 29_999L, durationMs = 600_000L))
        assertFalse(MoviesRepository.shouldResumePosition(positionMs = 0L, durationMs = 600_000L))

        // >= 30 seconds and remaining > 60 seconds (and not completed) -> true
        assertTrue(MoviesRepository.shouldResumePosition(positionMs = 30_000L, durationMs = 600_000L))
        assertTrue(MoviesRepository.shouldResumePosition(positionMs = 300_000L, durationMs = 600_000L))

        // Unknown duration with position >= 30s -> true
        assertTrue(MoviesRepository.shouldResumePosition(positionMs = 45_000L, durationMs = null))
        assertTrue(MoviesRepository.shouldResumePosition(positionMs = 45_000L, durationMs = 0L))

        // Remaining <= 60 seconds -> false
        assertFalse(MoviesRepository.shouldResumePosition(positionMs = 550_000L, durationMs = 600_000L))

        // Near completion (e.g. >= 90% or rem <= 120s) -> false
        assertFalse(MoviesRepository.shouldResumePosition(positionMs = 500_000L, durationMs = 600_000L))

        // Position >= duration -> false
        assertFalse(MoviesRepository.shouldResumePosition(positionMs = 600_000L, durationMs = 600_000L))
    }

    @Test
    fun clampedResumePositionSanitizesInvalidPositions() {
        // Under 30s -> returns 0L
        assertEquals(0L, MoviesRepository.clampedResumePosition(positionMs = 25_000L, durationMs = 600_000L))
        assertEquals(0L, MoviesRepository.clampedResumePosition(positionMs = 0L, durationMs = 600_000L))
        assertEquals(0L, MoviesRepository.clampedResumePosition(positionMs = -5_000L, durationMs = 600_000L))

        // Null position -> 0L
        assertEquals(0L, MoviesRepository.clampedResumePosition(positionMs = null, durationMs = 600_000L))

        // Valid middle position -> returns exact position
        assertEquals(150_000L, MoviesRepository.clampedResumePosition(positionMs = 150_000L, durationMs = 600_000L))

        // Completed or near end -> returns 0L
        assertEquals(0L, MoviesRepository.clampedResumePosition(positionMs = 590_000L, durationMs = 600_000L))
        assertEquals(0L, MoviesRepository.clampedResumePosition(positionMs = 600_000L, durationMs = 600_000L))
        assertEquals(0L, MoviesRepository.clampedResumePosition(positionMs = 700_000L, durationMs = 600_000L))

        // Valid position with null duration -> returns position
        assertEquals(150_000L, MoviesRepository.clampedResumePosition(positionMs = 150_000L, durationMs = null))
    }

    @Test
    fun seriesRepositoryDelegatesThresholdsAccurately() {
        assertFalse(SeriesRepository.shouldResumePosition(20_000L, 600_000L))
        assertTrue(SeriesRepository.shouldResumePosition(120_000L, 600_000L))
        assertFalse(SeriesRepository.shouldResumePosition(590_000L, 600_000L))

        assertTrue(SeriesRepository.isCompletedPosition(600_000L, 600_000L))
        assertEquals(120_000L, SeriesRepository.clampedResumePosition(120_000L, 600_000L))
    }

    @Test
    fun seriesContinueWatchingItemPreservesEpisodeIdentity() {
        val item = ContinueWatchingItem(
            providerId = ProviderId("provider-1"),
            contentType = ContentType.Episode,
            contentId = "series-101",
            subContentId = "episode-202",
            title = "Breaking Bad",
            subtitle = "S2 • E4",
            imageUrl = "http://example.invalid/ep202.jpg",
            positionMs = 180_000L,
            durationMs = 2_400_000L,
            seasonNumber = 2,
            episodeNumber = 4,
            episodeTitle = "Down",
        )

        assertEquals(ContentType.Episode, item.contentType)
        assertEquals("series-101", item.contentId)
        assertEquals("episode-202", item.subContentId)
        assertEquals(2, item.seasonNumber)
        assertEquals(4, item.episodeNumber)
        assertEquals("Down", item.episodeTitle)
        assertEquals(180_000L, item.positionMs)
        assertEquals(2_400_000L, item.durationMs)
    }

    @Test
    fun homeContinueWatchingDeduplicatesPerSeries() {
        val ep1 = ContinueWatchingItem(
            providerId = ProviderId("p1"),
            contentType = ContentType.Episode,
            contentId = "series-1",
            subContentId = "ep-1",
            title = "Show A",
            subtitle = "S1 • E1",
            imageUrl = null,
            positionMs = 100_000L,
            durationMs = 600_000L,
        )
        val ep2 = ContinueWatchingItem(
            providerId = ProviderId("p1"),
            contentType = ContentType.Episode,
            contentId = "series-1",
            subContentId = "ep-2",
            title = "Show A",
            subtitle = "S1 • E2",
            imageUrl = null,
            positionMs = 200_000L,
            durationMs = 600_000L,
        )
        val movie = ContinueWatchingItem(
            providerId = ProviderId("p1"),
            contentType = ContentType.Movie,
            contentId = "movie-1",
            subContentId = null,
            title = "Movie 1",
            subtitle = "Movie",
            imageUrl = null,
            positionMs = 300_000L,
            durationMs = 6_000_000L,
        )

        val rawList = listOf(ep2, ep1, movie)
        val homeList = rawList.distinctBy {
            if (it.contentType == ContentType.Episode) "series_${it.contentId}" else "movie_${it.contentId}"
        }

        assertEquals(2, homeList.size)
        assertEquals("ep-2", homeList[0].subContentId)
        assertEquals("movie-1", homeList[1].contentId)
    }

    @Test
    fun formatPlaybackTimeOutputsCorrectFormat() {
        assertEquals("0:25", formatPlaybackTime(25_000L))
        assertEquals("1:30", formatPlaybackTime(90_000L))
        assertEquals("25:40", formatPlaybackTime((25 * 60 + 40) * 1000L))
        assertEquals("1:05:12", formatPlaybackTime((3600 + 5 * 60 + 12) * 1000L))
    }
}
