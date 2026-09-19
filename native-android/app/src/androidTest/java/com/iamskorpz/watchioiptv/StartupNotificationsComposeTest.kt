package com.iamskorpz.watchioiptv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iamskorpz.watchioiptv.data.updates.UpdateApk
import com.iamskorpz.watchioiptv.data.updates.UpdateManifest
import com.iamskorpz.watchioiptv.domain.model.Announcement
import com.iamskorpz.watchioiptv.domain.model.AnnouncementPriority
import com.iamskorpz.watchioiptv.domain.model.AnnouncementType
import com.iamskorpz.watchioiptv.feature.startup.StartupAnnouncementModal
import com.iamskorpz.watchioiptv.feature.startup.StartupUpdateModal
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StartupNotificationsComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun optionalUpdateExposesReachableActions() {
        var update = false
        composeRule.setContent {
            WatchioTheme { StartupUpdateModal(manifest(), onLater = {}, onUpdate = { update = true }) }
        }
        composeRule.onNodeWithTag("startup-update-modal").assertIsDisplayed()
        composeRule.onNodeWithTag("startup-update-later").assertIsDisplayed()
        composeRule.onNodeWithTag("startup-update-action").assertIsDisplayed()
        assertInsideBackdrop("startup-update-later", "startup-update-modal-backdrop")
        assertInsideBackdrop("startup-update-action", "startup-update-modal-backdrop")
        assertActionsDoNotOverlap("startup-update-later", "startup-update-action")

        composeRule.onNodeWithTag("startup-update-action").performClick()
        composeRule.runOnIdle { assertTrue(update) }
    }

    @Test
    fun mandatoryUpdateHasNoLaterAndKeepsActionBounded() {
        composeRule.setContent {
            WatchioTheme { StartupUpdateModal(manifest().copy(mandatory = true), onLater = {}, onUpdate = {}) }
        }
        composeRule.onNodeWithTag("startup-update-later").assertDoesNotExist()
        composeRule.onNodeWithTag("startup-update-modal").assertIsDisplayed()
        assertInsideBackdrop("startup-update-action", "startup-update-modal-backdrop")
    }

    @Test
    fun updateActionRoutesThroughProvidedSecureUpdaterCallback() {
        var update = false
        composeRule.setContent {
            WatchioTheme { StartupUpdateModal(manifest(), onLater = {}, onUpdate = { update = true }) }
        }
        composeRule.onNodeWithTag("startup-update-action").performClick()
        composeRule.runOnIdle { assertTrue(update) }
    }

    @Test
    fun announcementFocusesDismissAndPersistsThroughCallback() {
        var dismissed = false
        composeRule.setContent {
            WatchioTheme { StartupAnnouncementModal(announcement(), onDismiss = { dismissed = true }) }
        }
        composeRule.onNodeWithTag("startup-announcement-modal").assertIsDisplayed()
        composeRule.onNodeWithTag("startup-announcement-dismiss").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun longUpdateContentKeepsActionsVisibleAndBounded() {
        composeRule.setContent {
            WatchioTheme {
                StartupUpdateModal(
                    manifest().copy(
                        versionName = "99.0.0-uitest-with-a-long-responsive-version-label",
                        releaseNotes = List(30) { "Long release note ${it + 1} verifies bounded scrolling content." },
                    ),
                    onLater = {},
                    onUpdate = {},
                )
            }
        }

        assertInsideBackdrop("startup-update-later", "startup-update-modal-backdrop")
        assertInsideBackdrop("startup-update-action", "startup-update-modal-backdrop")
    }

    @Test
    fun longAnnouncementKeepsDismissVisibleAndBounded() {
        composeRule.setContent {
            WatchioTheme {
                StartupAnnouncementModal(
                    announcement().copy(
                        title = "Watchio announcement with a deliberately long responsive title",
                        body = List(40) { "Long announcement line ${it + 1}." }.joinToString("\n"),
                    ),
                    onDismiss = {},
                )
            }
        }

        assertInsideBackdrop("startup-announcement-dismiss", "startup-announcement-modal-backdrop")
    }

    private fun assertInsideBackdrop(actionTag: String, backdropTag: String) {
        val backdrop = composeRule.onNodeWithTag(backdropTag).getUnclippedBoundsInRoot()
        val action = composeRule.onNodeWithTag(actionTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(action.right > action.left && action.bottom > action.top)
        assertTrue(action.left >= backdrop.left && action.top >= backdrop.top)
        assertTrue(action.right <= backdrop.right && action.bottom <= backdrop.bottom)
    }

    private fun assertActionsDoNotOverlap(leftTag: String, rightTag: String) {
        val left = composeRule.onNodeWithTag(leftTag).getUnclippedBoundsInRoot()
        val right = composeRule.onNodeWithTag(rightTag).getUnclippedBoundsInRoot()
        assertTrue(left.right <= right.left)
    }

    private fun announcement() = Announcement(
        id = "maintenance-2026-09-18",
        title = "Watchio Announcement",
        body = "A long plain-text message that remains readable and scrollable inside the startup modal.",
        publishedAt = "2026-09-18T12:00:00Z",
        type = AnnouncementType.MAINTENANCE,
        priority = AnnouncementPriority.IMPORTANT,
    )

    private fun manifest() = UpdateManifest(
        schemaVersion = 1,
        channel = "dev",
        versionCode = 16,
        versionName = "0.1.3",
        minimumSupportedVersionCode = 1,
        mandatory = false,
        publishedAt = "2026-09-18T12:00:00Z",
        releaseNotes = listOf("Fix one", "Fix two"),
        githubRelease = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.3",
        apk = UpdateApk("Watchio-IPTV.apk", "https://example.com/Watchio-IPTV.apk", "a".repeat(64)),
    )
}
