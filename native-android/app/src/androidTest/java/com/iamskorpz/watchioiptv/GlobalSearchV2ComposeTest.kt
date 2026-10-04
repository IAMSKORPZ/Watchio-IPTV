package com.iamskorpz.watchioiptv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.library.SearchResults
import com.iamskorpz.watchioiptv.data.library.WatchioSearchResult
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.feature.library.GlobalSearchScreen
import com.iamskorpz.watchioiptv.feature.library.SearchStatus
import com.iamskorpz.watchioiptv.feature.library.SearchUiState
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlobalSearchV2ComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun emptyQueryShowsProviderScopedRecentSearchesAndClearAll() {
        var selected = ""
        var cleared = false
        setContent(SearchUiState(recentQueries = listOf("Batman", "BBC One")), onRecent = { selected = it }, onClear = { cleared = true })
        composeRule.onNodeWithTag("global-search-home").assertIsDisplayed()
        composeRule.onNodeWithText("Batman").performClick()
        composeRule.onNodeWithTag("global-search-clear-recents").performClick()
        composeRule.runOnIdle { assertEquals("Batman", selected); assertTrue(cleared) }
    }

    @Test fun noResultsAndErrorStatesAreClear() {
        setContent(SearchUiState(query = "Missing", status = SearchStatus.NoResults))
        composeRule.onNodeWithText("NO RESULTS FOR \"Missing\"").assertIsDisplayed()
    }

    @Test fun sectionedResultsActivateExistingNavigationCallback() {
        val live = result(ContentType.Live, "live", "BBC One")
        val movie = result(ContentType.Movie, "movie", "Batman")
        val series = result(ContentType.Series, "series", "Batman Beyond")
        var activated: WatchioSearchResult? = null
        setContent(SearchUiState(query = "bat", status = SearchStatus.Results, results = SearchResults(listOf(live), listOf(movie), listOf(series))), onResult = { activated = it })
        composeRule.onNodeWithTag("global-search-group-live").assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-group-movies").assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-group-series").assertIsDisplayed()
        composeRule.onNodeWithText("Batman").performClick()
        composeRule.runOnIdle { assertEquals(movie, activated) }
    }

    @Test fun focusAloneDoesNotActivateResult() {
        val movie = result(ContentType.Movie, "movie", "Batman")
        var activated = false
        setContent(SearchUiState(query = "bat", status = SearchStatus.Results, results = SearchResults(movies = listOf(movie))), onResult = { activated = true })
        composeRule.onNodeWithText("Batman").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(!activated) }
    }

    private fun setContent(
        state: SearchUiState,
        onRecent: (String) -> Unit = {},
        onClear: () -> Unit = {},
        onResult: (WatchioSearchResult) -> Unit = {},
    ) = composeRule.setContent {
        WatchioTheme {
            GlobalSearchScreen(state, {}, {}, onResult, {}, onRecent, {}, onClear)
        }
    }

    private fun result(type: ContentType, id: String, title: String) = WatchioSearchResult(
        ProviderId("provider-a"), type, id, title, null, null,
    )
}
