package com.iamskorpz.watchioiptv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iamskorpz.watchioiptv.data.live.LiveTvCategory
import com.iamskorpz.watchioiptv.data.live.LiveTvCategoryKind
import com.iamskorpz.watchioiptv.feature.tvguide.EpgCategoriesScreen
import com.iamskorpz.watchioiptv.feature.tvguide.EpgCategoriesUiState
import com.iamskorpz.watchioiptv.feature.tvguide.EpgCategoryEntry
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EpgCategoriesComposeTest {
    @get:Rule val composeRule = createComposeRule()

    private val entries = listOf(
        EpgCategoryEntry(LiveTvCategory("all", "ALL CHANNELS", LiveTvCategoryKind.All), 120),
        EpgCategoryEntry(LiveTvCategory("favorites", "FAVOURITES", LiveTvCategoryKind.Favorites), 8),
        EpgCategoryEntry(LiveTvCategory("movies", "UK | MOVIES", LiveTvCategoryKind.Provider, "movies"), 58),
    )

    @Test
    fun headerCountsAndTouchSelectionWork() {
        var selected: String? = null
        var backed = false
        composeRule.setContent {
            WatchioTheme { EpgCategoriesScreen(EpgCategoriesUiState(loading = false, entries = entries), { selected = it.id }, { backed = true }) }
        }

        composeRule.onNodeWithTag("epg-categories-header").assertIsDisplayed()
        composeRule.onNodeWithText("EPG CATEGORIES").assertIsDisplayed()
        composeRule.onNodeWithText("ALL CHANNELS").assertIsDisplayed()
        composeRule.onNodeWithText("FAVOURITES").assertIsDisplayed()
        composeRule.onNodeWithText("UK | MOVIES").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("FAVOURITES, 8 channels").performClick()
        assertEquals("favorites", selected)

        selected = null
        composeRule.onNodeWithContentDescription("ALL CHANNELS, 120 channels").performClick()
        assertEquals("all", selected)

        composeRule.onNodeWithTag("epg-categories-back-icon").performClick()
        assertEquals(true, backed)
    }
}
