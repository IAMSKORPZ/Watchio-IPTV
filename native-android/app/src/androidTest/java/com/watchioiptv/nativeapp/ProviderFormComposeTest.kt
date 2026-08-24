package com.watchioiptv.nativeapp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderFormComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun xtreamLandscapeFormLowerControlsAreReachable() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("How will you use Watchio?")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("Providers")).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasText("How will you use Watchio?")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("TV / REMOTE\nAndroid TV, Fire TV & Remote").performClick()
        }
        if (composeRule.onAllNodes(hasText("Providers")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.waitUntilAtLeastOneExists(hasContentDescription("Add Xtream"), 5_000)
            composeRule.onNodeWithContentDescription("Add Xtream").performClick()
        }

        composeRule.waitUntilAtLeastOneExists(hasText("XTREAM CODES"), 5_000)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Password"))
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Connect"))
        composeRule.onNodeWithContentDescription("Connect").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Cancel"))
        composeRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun xtreamLoginFieldsAcceptTextAndKeepValuesAfterFocusChanges() {
        openXtreamLogin()

        composeRule.onNodeWithTag("xtream-provider-name").performTextInput("Test IPTV")
        composeRule.onNodeWithTag("xtream-provider-name").assertTextContains("Test IPTV")
        composeRule.onNodeWithTag("xtream-server-url").performTextInput("https://example.invalid")
        composeRule.onNodeWithTag("xtream-server-url").assertTextContains("https://example.invalid")
        composeRule.onNodeWithTag("xtream-username").performTextInput("testuser")
        composeRule.onNodeWithTag("xtream-username").assertTextContains("testuser")
        composeRule.onNodeWithTag("xtream-password").performTextInput("testpass")

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Connect"))
        composeRule.onNodeWithContentDescription("Connect").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Test IPTV"))
        composeRule.onNodeWithTag("xtream-provider-name").assertTextContains("Test IPTV")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("https://example.invalid"))
        composeRule.onNodeWithTag("xtream-server-url").assertTextContains("https://example.invalid")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("testuser"))
        composeRule.onNodeWithTag("xtream-username").assertTextContains("testuser")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun openXtreamLogin() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("How will you use Watchio?")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("Providers")).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasText("How will you use Watchio?")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("MOBILE / TOUCH\nPhones & tablets").performClick()
        }
        if (composeRule.onAllNodes(hasText("Providers")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.waitUntilAtLeastOneExists(hasContentDescription("Add Xtream"), 5_000)
            composeRule.onNodeWithContentDescription("Add Xtream").performClick()
        }
        composeRule.waitUntilAtLeastOneExists(hasText("XTREAM CODES"), 5_000)
    }
}
