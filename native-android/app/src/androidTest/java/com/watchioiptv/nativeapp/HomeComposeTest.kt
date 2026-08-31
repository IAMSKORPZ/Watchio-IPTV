package com.watchioiptv.nativeapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsPolishedNavigationHierarchy() {
        enterConfiguredOrProviderSetup()

        if (composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("XTREAM CODES").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
        if (composeRule.onAllNodesWithTag("home-live-tv").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("home-no-provider").assertIsDisplayed()
            composeRule.onNodeWithTag("home-add-provider").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Sports").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Announcements").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Playlist").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithTag("home-live-tv").assertIsDisplayed()
        composeRule.onNodeWithTag("home-movies").assertIsDisplayed()
        composeRule.onNodeWithTag("home-series").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home-my-list").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("home-tv-guide").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sports").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Announcements").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Playlist").assertIsDisplayed()
        composeRule.onNodeWithTag("home-settings").assertIsDisplayed()
    }

    @Test
    fun homeLiveTvActionActivatesRoute() {
        enterConfiguredOrProviderSetup()

        if (composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("XTREAM CODES").assertIsDisplayed()
            return
        }
        if (composeRule.onAllNodesWithTag("home-live-tv").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("home-no-provider").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithTag("home-live-tv").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("LIVE TV")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("live-tv-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("live-header").assertIsDisplayed()
        composeRule.onNodeWithTag("live-back-icon").assertIsDisplayed()
        composeRule.onNodeWithTag("live-branding").assertIsDisplayed()
        composeRule.onNodeWithText("Watchio").assertIsDisplayed()
        composeRule.onNodeWithTag("live-title").assertIsDisplayed()
        composeRule.onNodeWithTag("live-clock").assertIsDisplayed()
        composeRule.onNodeWithTag("live-category-search").assertIsDisplayed()
        composeRule.onNodeWithTag("live-category-all").assertIsDisplayed()
        composeRule.onNodeWithTag("live-category-favorites").assertIsDisplayed()
        composeRule.onNodeWithTag("live-category-history").assertIsDisplayed()
        composeRule.onNodeWithTag("live-channel-list").assertIsDisplayed()
        composeRule.onNodeWithTag("live-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("live-channel-info").assertIsDisplayed()
        composeRule.onNodeWithTag("live-epg-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("live-search").assertIsDisplayed()
        composeRule.onNodeWithTag("live-more").assertIsDisplayed()
    }

    @Test
    fun homeTopBarAndSecondaryRoutesUseExistingDestinations() {
        enterConfiguredOrProviderSetup()
        if (composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("XTREAM CODES").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sports").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Announcements").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Playlist").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Providers")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeSearchActionActivatesGlobalSearchOverlay() {
        enterConfiguredOrProviderSetup()
        if (composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("XTREAM CODES").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("global-search-overlay").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("global-search-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-field").assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-close").performClick()
    }

    @Test
    fun announcementsBellOpensListDetailsAndBackReturnsHome() {
        enterConfiguredOrProviderSetup()
        if (composeRule.onAllNodes(hasText("XTREAM CODES")).fetchSemanticsNodes().isNotEmpty()) return

        composeRule.onNodeWithContentDescription("Announcements").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("announcements-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("announcement-uitest-update").assertIsFocused()
        composeRule.onNodeWithTag("announcement-uitest-update").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("announcement-details").assertIsDisplayed()
        composeRule.onNodeWithTag("announcement-detail-back-icon").performClick()
        composeRule.onNodeWithTag("announcements-list").assertIsDisplayed()
        composeRule.onNodeWithTag("announcements-back-icon").performClick()
        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
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
