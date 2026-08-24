package com.watchioiptv.nativeapp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsMenuComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun settingsRootShowsRequestedCategoryMenu() {
        openSettingsOrSkip()
        if (composeRule.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isEmpty()) return

        composeRule.onNodeWithText("SETTINGS").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-back-icon").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-branding").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-clock").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("settings-back").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("settings-provider-management").assertIsFocused()
        composeRule.onNodeWithText("Provider Management").assertIsDisplayed()
        composeRule.onNodeWithText("Account Information").assertIsDisplayed()
        composeRule.onNodeWithText("Player Settings").assertIsDisplayed()
        composeRule.onNodeWithText("EPG Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Parental Controls").assertIsDisplayed()
        composeRule.onNodeWithText("Stream Format").assertIsDisplayed()
        composeRule.onNodeWithText("Input Mode").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Backup & Restore").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-check-updates").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("My List")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun settingsCategoryRoutesReturnToRoot() {
        openSettingsOrSkip()
        if (composeRule.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isEmpty()) return

        composeRule.onNodeWithText("Account Information").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("ACCOUNT INFORMATION"), 5_000)
        assertSingleSettingsBack()
        composeRule.onNodeWithTag("account-information-content").assertIsDisplayed()
        composeRule.onNodeWithText("Provider Name").assertIsDisplayed()
        composeRule.onNodeWithText("Account Status").assertIsDisplayed()
        composeRule.onNodeWithText("Expiration Date").assertIsDisplayed()
        composeRule.onNodeWithText("Provider Type").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("Coming in next Settings phase.")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasText("password")).fetchSemanticsNodes().isEmpty())
        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)

        openSettingsDestination("Player Settings", "PLAYER SETTINGS")
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("APPEARANCE"), 5_000)
        assertSingleSettingsBack()
        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)

        composeRule.onNodeWithText("EPG Settings").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("EPG SETTINGS"), 5_000)
        assertSingleSettingsBack()
        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)

        openSettingsDestination("Parental Controls", "PARENTAL CONTROLS")
        openSettingsDestination("Stream Format", "STREAM FORMAT")
        openSettingsDestination("Input Mode", "INPUT MODE")
        openSettingsDestination("Backup & Restore", "BACKUP & RESTORE")
        composeRule.onNodeWithTag("settings-check-updates").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("CHECK FOR UPDATES"), 5_000)
        assertSingleSettingsBack()
        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun settingsBackReturnsHome() {
        openSettingsOrSkip()
        if (composeRule.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isEmpty()) return

        composeRule.onNodeWithTag("settings-back-icon").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun playerSettingsPageShowsRealControls() {
        openSettingsOrSkip()
        if (composeRule.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isEmpty()) return

        composeRule.onNodeWithText("Player Settings").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("PLAYER SETTINGS"), 5_000)

        assertSingleSettingsBack()
        composeRule.onNodeWithTag("player-settings-content").assertIsDisplayed()
        composeRule.onNodeWithText("PLAYBACK").assertIsDisplayed()
        composeRule.onNodeWithText("Auto Resume: ON").assertIsDisplayed()
        composeRule.onNodeWithText("Auto Play Live Channel: OFF").assertIsDisplayed()
        composeRule.onNodeWithText("Remember Last Live Channel: ON").assertIsDisplayed()
        composeRule.onNodeWithText("CONTROLS").assertIsDisplayed()
        composeRule.onNodeWithText("RECOVERY").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Auto Retry Streams: ON").assertIsDisplayed()
        composeRule.onNodeWithText("VIDEO").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Video Scaling").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("Playback and video settings will be expanded in a later phase.")).fetchSemanticsNodes().isEmpty())

        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun playerSettingsChoiceCardsSelectAndPersist() {
        openPlayerSettingsOrSkip()
        if (composeRule.onAllNodesWithTag("player-settings-content").fetchSemanticsNodes().isEmpty()) return

        composeRule.onNodeWithTag("player-autohide-3").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("player-autohide-3").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true }
        composeRule.onNodeWithTag("player-autohide-3").assertIsSelected()
        composeRule.onNodeWithTag("player-autohide-5").assertIsNotSelected()

        composeRule.onNodeWithTag("player-autohide-8").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("player-autohide-8").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true }
        composeRule.onNodeWithTag("player-autohide-8").assertIsSelected()
        composeRule.onNodeWithTag("player-autohide-3").assertIsNotSelected()

        composeRule.onNodeWithTag("player-retry-1").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("player-retry-1").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true }
        composeRule.onNodeWithTag("player-retry-1").assertIsSelected()

        composeRule.onNodeWithTag("player-retry-3").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("player-retry-3").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true }
        composeRule.onNodeWithTag("player-retry-3").assertIsSelected()
        composeRule.onNodeWithTag("player-retry-1").assertIsNotSelected()

        composeRule.onNodeWithTag("player-scaling-fill").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("player-scaling-fill").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true }
        composeRule.onNodeWithTag("player-scaling-fill").assertIsSelected()

        composeRule.onNodeWithTag("player-scaling-zoom").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("player-scaling-zoom").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true }
        composeRule.onNodeWithTag("player-scaling-zoom").assertIsSelected()
        composeRule.onNodeWithTag("player-scaling-fill").assertIsNotSelected()

        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)
        composeRule.onNodeWithText("Player Settings").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("PLAYER SETTINGS"), 5_000)

        composeRule.onNodeWithTag("player-autohide-8").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("player-retry-3").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("player-scaling-zoom").performScrollTo().assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun openSettingsOrSkip() {
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
        if (composeRule.onAllNodesWithTag("home-settings").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("home-settings").performClick()
            composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun openSettingsDestination(cardTitle: String, pageTitle: String) {
        composeRule.onNodeWithText(cardTitle).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText(pageTitle), 5_000)
        assertSingleSettingsBack()
        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasText("SETTINGS"), 5_000)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun openPlayerSettingsOrSkip() {
        openSettingsOrSkip()
        if (composeRule.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isEmpty()) return
        composeRule.onNodeWithText("Player Settings").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("PLAYER SETTINGS"), 5_000)
    }

    private fun assertSingleSettingsBack() {
        composeRule.onNodeWithTag("settings-back-icon").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("Back")).fetchSemanticsNodes().isEmpty())
    }
}
