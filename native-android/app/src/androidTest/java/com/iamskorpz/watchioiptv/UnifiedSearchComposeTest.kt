package com.iamskorpz.watchioiptv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.library.WatchioSearchResult
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.ui.components.UnifiedSearchLayout
import com.iamskorpz.watchioiptv.ui.components.UnifiedSearchOverlay
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnifiedSearchComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun posterOverlayShowsProgressiveCountAndOnlyResultCardsActivate() {
        var activated = ""
        setContent(listOf(result(ContentType.Movie, "m1", "Exact Movie")), loading = true) { activated = it.contentId }

        composeRule.onNodeWithTag("unified-search-field").assertIsDisplayed()
        composeRule.onNodeWithText("1 strong results - refining...").assertIsDisplayed()
        composeRule.onNodeWithText("Exact Movie").performClick()
        composeRule.runOnIdle { assertEquals("m1", activated) }
    }

    @Test fun channelOverlayExposesSingleFocusableActivationTarget() {
        var activated = ""
        setContent(listOf(result(ContentType.Live, "l1", "BBC One")), layout = UnifiedSearchLayout.Channels) { activated = it.contentId }

        val card = composeRule.onAllNodesWithTag("unified-search-result", useUnmergedTree = true)[0]
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        card.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals("l1", activated) }
        composeRule.onAllNodesWithTag("unified-search-result").assertCountEquals(1)
    }

    @Test fun emptyAndNoMatchStatesRemainReadable() {
        setContent(emptyList(), query = "missing")
        composeRule.onNodeWithText("No results").assertIsDisplayed()
    }

    private fun setContent(
        results: List<WatchioSearchResult>,
        query: String = "exact",
        loading: Boolean = false,
        layout: UnifiedSearchLayout = UnifiedSearchLayout.Posters,
        onResult: (WatchioSearchResult) -> Unit = {},
    ) = composeRule.setContent {
        WatchioTheme {
            UnifiedSearchOverlay(
                title = "Search",
                query = query,
                results = results,
                loading = loading,
                layout = layout,
                testTagPrefix = "unified-search",
                onQuery = {},
                onResult = onResult,
                onDismiss = {},
            )
        }
    }

    private fun result(type: ContentType, id: String, title: String) = WatchioSearchResult(
        providerId = ProviderId("provider-a"),
        contentType = type,
        contentId = id,
        title = title,
        subtitle = null,
        imageUrl = null,
    )
}
