package com.watchioiptv.nativeapp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvGuideComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun tvGuideOpensFromHome() {
        enterConfiguredOrProviderSetup()
        if (composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithText("XTREAM CODES").assertIsDisplayed()
            return
        }
        if (composeRule.onAllNodesWithTag("home-tv-guide").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("home-no-provider").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithTag("home-tv-guide").assertIsDisplayed()
        composeRule.onNodeWithTag("home-tv-guide").performClick()
        composeRule.onNodeWithTag("tv-guide-screen").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("Watchio").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun settingsLowerOptionsCanScrollIntoView() {
        enterConfiguredOrProviderSetup()
        if (composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithText("XTREAM CODES").assertIsDisplayed()
            return
        }
        if (composeRule.onAllNodesWithTag("home-settings").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("home-no-provider").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithTag("home-settings").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)

        composeRule.onNodeWithText("Provider Management").assertIsDisplayed()
        composeRule.onNodeWithText("Account Information").assertIsDisplayed()
        composeRule.onNodeWithText("Player Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-check-updates").performScrollTo().assertIsDisplayed()
    }

    private fun enterConfiguredOrProviderSetup() {
        composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("How will you use Watchio?")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasText("How will you use Watchio?")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("TV / REMOTE\nAndroid TV, Fire TV & Remote").performClick()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
