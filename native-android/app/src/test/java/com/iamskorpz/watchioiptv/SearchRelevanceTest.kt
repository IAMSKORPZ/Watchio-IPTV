package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.library.WatchioSearchResult
import com.iamskorpz.watchioiptv.data.library.rankedForSearch
import com.iamskorpz.watchioiptv.data.library.normalizeSearchQuery
import com.iamskorpz.watchioiptv.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceTest {
    @Test fun normalizationHandlesUnicodePunctuationApostrophesPrefixesQualityAndNumbers() {
        assertEquals("spider man", normalizeSearchQuery("Spider-Man"))
        assertEquals("schitts creek", normalizeSearchQuery("Schitt’s Creek"))
        assertEquals("cafe 4", normalizeSearchQuery("  CAFÉ__4 "))
        assertEquals("sky sports main event", normalizeSearchQuery("UK | SKY SPORTS MAIN EVENT FHD"))
        assertEquals("formula 1", normalizeSearchQuery("Formula 1"))
    }

    @Test fun rankingOrdersExactPrefixTokenContainsThenFuzzyDeterministically() {
        val ranked = listOf(
            result("fuzzy", "Harry Pottor"),
            result("contains", "TheHarry Potter Collection"),
            result("token", "Potter Harry Archive"),
            result("prefix", "Harry Potter Films"),
            result("exact", "Harry Potter"),
        ).rankedForSearch("harry potter", 40)
        assertEquals(listOf("exact", "prefix", "token", "contains", "fuzzy"), ranked.map { it.contentId })
    }

    @Test fun conservativeFuzzyMatchesTyposButRejectsUnrelatedTitles() {
        val ranked = listOf(result("potter", "Harry Potter"), result("unrelated", "Happy Feet"))
            .rankedForSearch("harry poter", 40)
        assertEquals(listOf("potter"), ranked.map { it.contentId })
    }

    @Test fun oneCharacterQueryUsesOnlyExactOrPrefixMatching() {
        val ranked = listOf(result("f1", "F1"), result("film", "Film Four"), result("inside", "Life"))
            .rankedForSearch("f", 40)
        assertEquals(listOf("f1", "film"), ranked.map { it.contentId })
    }
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
