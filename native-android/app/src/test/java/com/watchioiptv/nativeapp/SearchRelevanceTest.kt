package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.library.WatchioSearchResult
import com.watchioiptv.nativeapp.data.library.rankedForSearch
import com.watchioiptv.nativeapp.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceTest {
    @Test
    fun carsMatchesWholeWordsButNotScars() {
        val ranked = listOf(
            result("scars", "Scars of Dracula"),
            result("riding", "Riding in Cars with Boys"),
            result("cars2", "Cars 2"),
            result("cars", "Cars"),
        ).rankedForSearch("cars", 40)

        assertEquals(listOf("Cars", "Cars 2", "Riding in Cars with Boys"), ranked.map { it.title })
        assertFalse(ranked.any { it.title == "Scars of Dracula" })
    }

    @Test
    fun matchingIsCaseInsensitivePunctuationTolerantAndMultiWordAware() {
        val candidates = listOf(result("1", "Riding-in CARS with Boys"), result("2", "Cars Racing"))
        assertTrue(candidates.rankedForSearch("RIDING IN cars", 40).any { it.contentId == "1" })
        assertTrue(candidates.rankedForSearch("cars racing", 40).any { it.contentId == "2" })
    }

    @Test
    fun exactTitleRanksBeforeOtherWholeWordMatchesAndDuplicatesAreRemoved() {
        val exact = result("cars", "Cars")
        val ranked = listOf(result("riding", "Riding in Cars with Boys"), exact, exact).rankedForSearch("cars", 40)
        assertEquals("Cars", ranked.first().title)
        assertEquals(2, ranked.size)
    }

    private fun result(id: String, title: String) = WatchioSearchResult(
        providerId = ProviderId("provider"),
        contentType = ContentType.Movie,
        contentId = id,
        title = title,
        subtitle = null,
        imageUrl = null,
    )
}
