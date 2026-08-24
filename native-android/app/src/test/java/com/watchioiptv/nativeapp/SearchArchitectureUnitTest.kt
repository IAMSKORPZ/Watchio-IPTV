package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.util.TextNormalizer
import com.watchioiptv.nativeapp.data.library.SearchScope
import com.watchioiptv.nativeapp.data.library.SearchResults
import com.watchioiptv.nativeapp.data.library.WatchioSearchResult
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.feature.library.contentRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchArchitectureUnitTest {

    @Test
    fun contentRouteResolvesCorrectScreenDestinations() {
        assertEquals("live/ch123", contentRoute(ContentType.Live, "ch123"))
        assertEquals("live", contentRoute(ContentType.Live, ""))
        assertEquals("movies/mov456", contentRoute(ContentType.Movie, "mov456"))
        assertEquals("series/ser789", contentRoute(ContentType.Series, "ser789"))
        assertEquals("series/ser789", contentRoute(ContentType.Episode, "ser789"))
    }

    @Test
    fun textNormalizerNormalizesWhitespaceAndCasing() {
        assertEquals("batman", TextNormalizer.normalizeForSearch("Batman"))
        assertEquals("batman begins", TextNormalizer.normalizeForSearch("  Batman   Begins  "))
        assertEquals("sky sports 1", TextNormalizer.normalizeForSearch("Sky SPORTS 1"))
    }

    @Test
    fun searchResultsProperlyTracksEmptyAndGroupedCounts() {
        val empty = SearchResults()
        assertTrue(empty.isEmpty)

        val liveResult = WatchioSearchResult(ProviderId("p1"), ContentType.Live, "1", "Sky Sports 1", "Sports", null)
        val movieResult = WatchioSearchResult(ProviderId("p1"), ContentType.Movie, "2", "Die Hard", "Action", null)
        val seriesResult = WatchioSearchResult(ProviderId("p1"), ContentType.Series, "3", "Breaking Bad", "Drama", null)

        val populated = SearchResults(
            live = listOf(liveResult),
            movies = listOf(movieResult),
            series = listOf(seriesResult),
        )

        assertFalse(populated.isEmpty)
        assertEquals(1, populated.live.size)
        assertEquals(1, populated.movies.size)
        assertEquals(1, populated.series.size)
    }

    @Test
    fun searchScopeEnumValuesAreExhaustive() {
        val scopes = SearchScope.entries.map { it.name }
        assertTrue(scopes.contains("Global"))
        assertTrue(scopes.contains("Live"))
        assertTrue(scopes.contains("Movies"))
        assertTrue(scopes.contains("Series"))
    }

    @Test
    fun mediaTitleNormalizerCleansSceneReleaseFilenames() {
        val dracula = com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer.cleanTitle(
            "Scars.of.Dracula.1970.720.BluRay.x264-x0r[FEATURETTE-Blood.Rites.Inside.Scars.of.Dracula]"
        )
        assertEquals("Scars of Dracula", dracula.displayTitle)
        assertEquals("1970", dracula.detectedYear)

        val toyStory = com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer.cleanTitle(
            "Toy.Story.5.2026.1080p.WEB-DL.x264.mkv"
        )
        assertEquals("Toy Story 5", toyStory.displayTitle)
        assertEquals("2026", toyStory.detectedYear)

        val matrix = com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer.cleanTitle(
            "The.Matrix.1999.2160p.UHD.BluRay.x265.mkv"
        )
        assertEquals("The Matrix", matrix.displayTitle)
        assertEquals("1999", matrix.detectedYear)

        val breakingBad = com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer.cleanTitle(
            "Breaking.Bad.S01E01.1080p.WEBRip.x264"
        )
        assertEquals("Breaking Bad", breakingBad.displayTitle)
        assertEquals(null, breakingBad.detectedYear)

        val alienRomulus = com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer.cleanTitle(
            "Alien.Romulus.2024.1080p.WEB-DL.DDP5.1.H.264"
        )
        assertEquals("Alien Romulus", alienRomulus.displayTitle)
        assertEquals("2024", alienRomulus.detectedYear)
    }

    @Test
    fun mediaTitleNormalizerPreservesLegitimateTitles() {
        val titles = listOf(
            "1917",
            "2001: A Space Odyssey",
            "Blade Runner 2049",
            "Se7en",
            "Catch-22",
            "Spider-Man: No Way Home",
            "Mission: Impossible",
            "F9",
            "28 Years Later",
        )
        for (title in titles) {
            val result = com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer.cleanTitle(title)
            assertEquals(title, result.displayTitle)
        }
    }

    @Test
    fun searchResultIncludesArtworkAndMetadataProperties() {
        val movieResult = WatchioSearchResult(
            providerId = ProviderId("p1"),
            contentType = ContentType.Movie,
            contentId = "101",
            title = "Alien Romulus",
            subtitle = "Sci-Fi",
            imageUrl = "https://example.com/poster.jpg",
            year = "2024",
            rating = "7.5",
        )
        assertEquals("https://example.com/poster.jpg", movieResult.imageUrl)
        assertEquals("2024", movieResult.year)
        assertEquals("7.5", movieResult.rating)

        val liveResult = WatchioSearchResult(
            providerId = ProviderId("p1"),
            contentType = ContentType.Live,
            contentId = "202",
            title = "BBC One HD",
            subtitle = "General",
            imageUrl = "https://example.com/logo.png",
        )
        assertEquals("https://example.com/logo.png", liveResult.imageUrl)
        assertEquals(null, liveResult.year)
        assertEquals(null, liveResult.rating)
    }
}
