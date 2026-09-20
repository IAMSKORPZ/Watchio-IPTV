package com.iamskorpz.watchioiptv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.iamskorpz.watchioiptv.uitest.StartupFixtureState
import com.iamskorpz.watchioiptv.uitest.StartupNotificationFixture
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeComposeTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val announcementFixtureRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                val fixture = StartupNotificationFixture(InstrumentationRegistry.getInstrumentation().targetContext)
                fixture.reset()
                if (description.methodName == "announcementsBellOpensListDetailsAndBackReturnsHome") {
                    fixture.select(StartupFixtureState.ANNOUNCEMENT)
                }
                try {
                    base.evaluate()
                } finally {
                    fixture.reset()
                }
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(announcementFixtureRule).around(composeRule)

    @Test
    fun homeShowsPolishedNavigationHierarchy() {
        enterConfiguredOrProviderSetup()

        if (composeRule.onAllNodes(hasText("Provider Name")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Provider Name").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
        if (composeRule.onAllNodesWithTag("home-live-tv").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("home-no-provider").assertIsDisplayed()
            composeRule.onNodeWithTag("home-add-provider").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Sports").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Notifications, no unread notifications").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("Notifications, no unread notifications").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Playlist").assertIsDisplayed()
        composeRule.onNodeWithTag("home-settings").assertIsDisplayed()
    }

    @Test
    fun homeLiveTvActionActivatesRoute() {
        enterConfiguredOrProviderSetup()

        if (composeRule.onAllNodes(hasText("Provider Name")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Provider Name").assertIsDisplayed()
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
        composeRule.onNodeWithTag("live-title").assertIsDisplayed()
        composeRule.onNodeWithTag("live-clock").assertIsDisplayed()
        composeRule.onNodeWithTag("live-category-search").assertIsDisplayed()
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
        if (composeRule.onAllNodes(hasText("Provider Name")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Provider Name").assertIsDisplayed()
            return
        }
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sports").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Notifications, no unread notifications").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Playlist").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("PROVIDER MANAGEMENT")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeSearchActionActivatesGlobalSearchOverlay() {
        enterConfiguredOrProviderSetup()
        if (composeRule.onAllNodes(hasText("Provider Name")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Provider Name").assertIsDisplayed()
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
        val fixture = StartupNotificationFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        val expectedAnnouncementTag = "announcement-${fixture.announcementId()}"
        enterConfiguredOrProviderSetup()
        if (composeRule.onAllNodes(hasText("Provider Name")).fetchSemanticsNodes().isNotEmpty()) return

        composeRule.onNodeWithContentDescription("Notifications, 1 unread notification").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("announcements-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(expectedAnnouncementTag)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
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
                composeRule.onAllNodes(hasText("Provider Name")).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasText("How will you use Watchio?")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("TV / REMOTE\nAndroid TV, Fire TV & Remote").performClick()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("Provider Name")).fetchSemanticsNodes().isNotEmpty()
        }
    }

}
