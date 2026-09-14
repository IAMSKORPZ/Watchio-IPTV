package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.data.movies.MovieCategory
import com.watchioiptv.nativeapp.data.movies.MovieCategoryKind
import com.watchioiptv.nativeapp.feature.movies.preferredInitialMovieCategory
import com.watchioiptv.nativeapp.feature.movies.resolveMovieCategory
import com.watchioiptv.nativeapp.feature.movies.resolveMovieCategoryLoad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoviesInitialCategoryTest {

    @Test
    fun exactNormalizedLatestReleasesHollywoodWins() {
        val broad = providerCategory("broad", "Hollywood Latest Releases 4K")
        val exact = providerCategory("exact", "  LATEST   releases  hollywood ")

        assertEquals(exact, preferredInitialMovieCategory(listOf(broad, exact)))
    }

    @Test
    fun exactLatestReleasesWinsWhenHollywoodVariantIsMissing() {
        val exact = providerCategory("latest", "Latest Releases")

        assertEquals(exact, preferredInitialMovieCategory(listOf(allMovies(), exact)))
    }

    @Test
    fun realProviderJustReleasedHollywoodAliasIsUsed() {
        val actualProviderCategory = providerCategory("416", "JUST RELEASED HOLLYWOOD")

        assertEquals(actualProviderCategory, preferredInitialMovieCategory(listOf(allMovies(), actualProviderCategory)))
    }

    @Test
    fun broadOrUnrelatedNamesAreNotMatched() {
        val categories = listOf(
            providerCategory("series", "Latest Series"),
            providerCategory("classics", "Hollywood Classics"),
            providerCategory("broad", "Latest Releases Hollywood 4K"),
        )

        assertNull(preferredInitialMovieCategory(categories))
    }

    @Test
    fun missingLatestReleasesHollywoodReturnsNullForSafeCallerFallback() {
        assertNull(preferredInitialMovieCategory(listOf(allMovies(), providerCategory("action", "Action"))))
    }

    @Test
    fun deferredPreferredCategoryReplacesAutomaticAllMoviesSelection() {
        val all = allMovies()
        val preferred = providerCategory("latest", "Latest Releases Hollywood")
        assertEquals(preferred, resolveMovieCategory(listOf(all, preferred), all, null, false))
    }

    @Test
    fun deferredPreferredCategoryNeverOverridesManualSelection() {
        val all = allMovies()
        val action = providerCategory("action", "Action")
        val preferred = providerCategory("latest", "Latest Releases Hollywood")
        assertEquals(action, resolveMovieCategory(listOf(all, action, preferred), all, action.id, true))
    }

    @Test
    fun legacyAutomaticAllIsReplacedAndRealProviderIdentityDrivesContentQuery() = runTest {
        val all = allMovies()
        val actualProviderCategory = providerCategory("416", "JUST RELEASED HOLLYWOOD")
        var queriedCategoryId: String? = null
        val load = resolveMovieCategoryLoad(
            categories = listOf(all, actualProviderCategory),
            restored = all,
            manualCategoryId = null,
            manualCategorySelected = false,
        ) { category ->
            queriedCategoryId = category.sourceCategoryId
            listOf("movie-from-${category.sourceCategoryId}")
        }

        assertEquals("416", load.selectedCategory?.id)
        assertEquals("416", queriedCategoryId)
        assertEquals(listOf("movie-from-416"), load.items)
    }

    @Test
    fun preferredCategoryAbsentFallsBackToAutomaticAll() {
        val all = allMovies()

        assertEquals(all, resolveMovieCategory(listOf(all, providerCategory("action", "Action")), all, null, false))
    }

    private fun allMovies() = MovieCategory("all", "ALL MOVIES", MovieCategoryKind.All)

    private fun providerCategory(id: String, name: String) =
        MovieCategory(id, name, MovieCategoryKind.Provider, id)
}
