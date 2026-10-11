package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.library.WatchioSearchResult
import com.iamskorpz.watchioiptv.data.library.rankedForSearch
import com.iamskorpz.watchioiptv.data.movies.WatchioMovieItem
import com.iamskorpz.watchioiptv.data.movies.rankMovieSearchResults
import com.iamskorpz.watchioiptv.data.movies.rankActiveProviderMovieSearchResults
import com.iamskorpz.watchioiptv.data.movies.prepareMovieSearchSnapshot
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.feature.movies.debouncedMovieSearchQueries
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class MovieSearchRankingTest {
    @Test
    fun profilePreparedSyntheticSearchPipeline() {
        listOf(1_000, 10_000, 30_000).forEach { size ->
            val rawCatalogue = List(size) { index -> movie(index, "Synthetic Movie $index") } +
                listOf(movie(size + 1, "Cars"), movie(size + 2, "Cars 2"), movie(size + 3, "Riding in Cars with Boys"))
            var catalogue = emptyList<WatchioMovieItem>()
            val snapshotNs = measureNanoTime { catalogue = prepareMovieSearchSnapshot(rawCatalogue) }
            println("MOVIE_SEARCH_AFTER candidateCount=${catalogue.size} queryType=snapshot_build totalMs=${snapshotNs / 1_000_000.0} resultCount=0")
            listOf("Cars", "Synthetic", "Movie 29999", "Synthetiq", "No Match Anywhere").forEach { query ->
                var count = 0
                val elapsedNs = measureNanoTime {
                    count = rankMovieSearchResults(catalogue, query, 500).size
                }
                println("MOVIE_SEARCH_AFTER candidateCount=${catalogue.size} queryType=${queryType(query)} totalMs=${elapsedNs / 1_000_000.0} resultCount=$count")
            }
        }
    }

    @Test
    fun rapidTypingDebouncesToLatestQuery() = runTest {
        val emitted = flow {
            listOf("c", "ca", "car", "cars").forEach { query ->
                emit(query)
                delay(50L)
            }
        }.debouncedMovieSearchQueries().toList()

        assertEquals(listOf("cars"), emitted)
    }

    @Test
    fun progressiveStrongResultsRemainStableWhenFuzzyResultsArrive() {
        val catalogue = prepareMovieSearchSnapshot(
            listOf(
                movie(0, "Union"),
                movie(1, "Union County"),
                movie(2, "The Union"),
                movie(3, "Unio"),
                movie(4, "Unuon"),
                movie(5, "Unrelated"),
            ),
        )

        val strong = rankMovieSearchResults(catalogue, "union", 500, includeFuzzy = false)
        val complete = rankMovieSearchResults(catalogue, "union", 500, includeFuzzy = true)

        assertEquals(listOf("Union", "Union County", "The Union"), strong.map { it.name })
        assertEquals(strong.map { it.id }, complete.take(strong.size).map { it.id })
        assertTrue(complete.map { it.name }.containsAll(listOf("Unio", "Unuon")))
    }

    @Test
    fun moviesTabUsesSameTitleRankingAsGlobalSearch() {
        val titles = listOf("Scars of Dracula", "Riding in Cars with Boys", "Cars 3", "Cars 2", "Cars")
        val movieRanked = rankMovieSearchResults(titles.mapIndexed(::movie), "cars", 40).map { it.name }
        val globalRanked = titles.mapIndexed { index, title ->
            WatchioSearchResult(
                providerId = ProviderId("provider"),
                contentType = ContentType.Movie,
                contentId = index.toString(),
                title = title,
                subtitle = null,
                imageUrl = null,
            )
        }.rankedForSearch("cars", 40).map { it.title }

        assertEquals(listOf("Cars", "Cars 3", "Cars 2", "Riding in Cars with Boys"), movieRanked)
        assertEquals(globalRanked, movieRanked)
        assertFalse(movieRanked.contains("Scars of Dracula"))
    }

    @Test
    fun moviesSearchUsesSameProviderWideCandidatesForEverySelectedCategory() {
        val providerA = ProviderId("provider-a")
        val providerB = ProviderId("provider-b")
        val catalogue = listOf(
            movie("die-hard", "Die Hard", providerA, "action"),
            movie("john-wick", "John Wick", providerA, "action"),
            movie("cars", "Cars", providerA, "family"),
            movie("cars-2", "Cars 2", providerA, "family"),
            movie("cars-3", "Cars 3", providerA, "family"),
            movie("riding", "Riding in Cars with Boys", providerA, "drama"),
            movie("foreign-cars", "Cars", providerB, "family"),
        )
        val expected = listOf("cars", "cars-2", "cars-3", "riding")

        listOf("action", "family", "drama", "all").forEach { selectedCategory ->
            val ranked = rankActiveProviderMovieSearchResults(catalogue, providerA, "cars", 40).map { it.id }
            assertEquals("selected category $selectedCategory must not constrain search", expected, ranked)
        }
    }

    @Test
    fun moviesSearchMatchesGlobalRankingAndIsolatesActiveProvider() {
        val providerA = ProviderId("provider-a")
        val providerB = ProviderId("provider-b")
        val catalogue = listOf(
            movie("cars", "Cars", providerA, "family"),
            movie("cars-2", "Cars 2", providerA, "family"),
            movie("cars-3", "Cars 3", providerA, "family"),
            movie("riding", "Riding in Cars with Boys", providerA, "drama"),
            movie("foreign", "Cars", providerB, "family"),
        )
        val movieResults = rankActiveProviderMovieSearchResults(catalogue, providerA, "cars", 40)
        val globalResults = catalogue.filter { it.providerId == providerA }.map { item ->
            WatchioSearchResult(
                providerId = item.providerId,
                contentType = ContentType.Movie,
                contentId = item.id,
                title = item.name,
                subtitle = null,
                imageUrl = null,
            )
        }.rankedForSearch("cars", 40)

        assertEquals(globalResults.map { it.contentId }, movieResults.map { it.id })
        assertFalse(movieResults.any { it.providerId == providerB })
    }

    private fun movie(index: Int, title: String) = WatchioMovieItem(
        providerId = ProviderId("provider"),
        providerType = ProviderType.Xtream,
        id = index.toString(),
        name = title,
        posterUrl = null,
        categoryId = null,
        rating = null,
        genre = "Cars",
        containerExtension = null,
        trailerKey = null,
        serverOrder = index,
        directUrl = null,
    )

    private fun movie(id: String, title: String, providerId: ProviderId, categoryId: String) = WatchioMovieItem(
        providerId = providerId,
        providerType = ProviderType.Xtream,
        id = id,
        name = title,
        posterUrl = null,
        categoryId = categoryId,
        rating = null,
        genre = null,
        containerExtension = null,
        trailerKey = null,
        serverOrder = 0,
        directUrl = null,
    )

    private fun queryType(query: String): String = when (query) {
        "Cars" -> "exact"
        "Synthetic" -> "prefix"
        "Movie 29999" -> "contains"
        "Synthetiq" -> "fuzzy"
        else -> "no_match"
    }
}
