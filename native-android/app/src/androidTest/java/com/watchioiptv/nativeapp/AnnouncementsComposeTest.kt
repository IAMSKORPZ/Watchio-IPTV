package com.watchioiptv.nativeapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.watchioiptv.nativeapp.domain.model.Announcement
import com.watchioiptv.nativeapp.domain.model.AnnouncementAction
import com.watchioiptv.nativeapp.domain.model.AnnouncementItem
import com.watchioiptv.nativeapp.domain.model.AnnouncementPriority
import com.watchioiptv.nativeapp.domain.model.AnnouncementSnapshot
import com.watchioiptv.nativeapp.domain.model.AnnouncementType
import com.watchioiptv.nativeapp.feature.announcements.AnnouncementsScreen
import com.watchioiptv.nativeapp.feature.announcements.AnnouncementsUiState
import com.watchioiptv.nativeapp.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnnouncementsComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listOpensItem() {
        var opened = ""
        setContent(onOpen = { opened = it })

        composeRule.onNodeWithTag("announcement-first").assertIsDisplayed().performClick()
        assertEquals("first", opened)
    }

    @Test
    fun headerDisplaysAnnouncementsTitleAndNoInboxText() {
        setContent()
        composeRule.onNodeWithTag("announcements-header").assertIsDisplayed()
        composeRule.onNodeWithTag("announcements-title").assertIsDisplayed()
        composeRule.onNodeWithText("ANNOUNCEMENTS").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("INBOX").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun headerBackAndArchiveToggleButtonsArePresentAndAccessible() {
        var backCalled = false
        var archiveToggled = false
        composeRule.setContent {
            WatchioTheme {
                AnnouncementsScreen(
                    state = AnnouncementsUiState(
                        snapshot = AnnouncementSnapshot(listOf(AnnouncementItem(announcement, false, false)), true),
                    ),
                    onBack = { backCalled = true },
                    onRefresh = {},
                    onOpen = {},
                    onCloseDetails = {},
                    onDismiss = {},
                    onToggleArchived = { archiveToggled = true },
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithTag("announcements-back-icon").assertIsDisplayed().performClick()
        assertTrue(backCalled)
        composeRule.onNodeWithTag("announcements-archive-toggle").assertIsDisplayed().performClick()
        assertTrue(archiveToggled)
    }

    @Test
    fun headerElementsDoNotOverlap() {
        setContent()
        val brandingBounds = composeRule.onNodeWithTag("announcements-branding").getUnclippedBoundsInRoot()
        val titleBounds = composeRule.onNodeWithTag("announcements-title").getUnclippedBoundsInRoot()
        val clockBounds = composeRule.onNodeWithTag("announcements-clock").getUnclippedBoundsInRoot()
        val toggleBounds = composeRule.onNodeWithTag("announcements-archive-toggle").getUnclippedBoundsInRoot()

        assertTrue(
            "branding should sit to the left of the title (branding.right <= title.left + 8.dp)",
            brandingBounds.right <= titleBounds.left + 8.dp,
        )
        assertTrue(
            "title should sit to the left of the clock/actions (title.right <= clockBounds.left + 8.dp)",
            titleBounds.right <= clockBounds.left + 8.dp,
        )
        assertTrue(
            "clock should sit to the left of archive toggle",
            clockBounds.right <= toggleBounds.left + 8.dp,
        )
    }

    @Test
    fun detailsRunsSafeAction() {
        var action: AnnouncementAction? = null
        setContent(selectedId = "first", onAction = { action = it })
        composeRule.onNodeWithTag("announcement-details").assertIsDisplayed()
        composeRule.onNodeWithTag("announcement-action").performClick()
        assertTrue(action is AnnouncementAction.OpenUpdater)
    }

    @Test
    fun detailBackReturnsAndOptionalActionSupportsFocus() {
        var back = false
        composeRule.setContent {
            WatchioTheme {
                AnnouncementsScreen(
                    state = AnnouncementsUiState(
                        snapshot = AnnouncementSnapshot(listOf(AnnouncementItem(announcement, true, false)), true),
                        selectedId = "first",
                    ),
                    onBack = {}, onRefresh = {}, onOpen = {}, onCloseDetails = { back = true },
                    onDismiss = {}, onToggleArchived = {}, onAction = {},
                )
            }
        }
        val actionSemantics = composeRule.onNodeWithTag("announcement-action").fetchSemanticsNode().config
        assertTrue(actionSemantics.contains(SemanticsProperties.Focused))
        assertTrue(actionSemantics.contains(SemanticsActions.RequestFocus))
        composeRule.onNodeWithTag("announcement-detail-back-icon").performClick()
        assertTrue(back)
    }

    @Test
    fun firstCardSupportsTvFocus() {
        setContent()
        val cardSemantics = composeRule.onNodeWithTag("announcement-first").fetchSemanticsNode().config
        assertTrue(cardSemantics.contains(SemanticsProperties.Focused))
        assertTrue(cardSemantics.contains(SemanticsActions.RequestFocus))
    }

    @Test
    fun emptyStateIsFriendly() {
        composeRule.setContent {
            WatchioTheme {
                AnnouncementsScreen(
                    state = AnnouncementsUiState(),
                    onBack = {}, onRefresh = {}, onOpen = {}, onCloseDetails = {},
                    onDismiss = {}, onToggleArchived = {}, onAction = {},
                )
            }
        }
        composeRule.onNodeWithTag("announcements-empty").assertIsDisplayed()
    }

    private fun setContent(
        selectedId: String? = null,
        onOpen: (String) -> Unit = {},
        onAction: (AnnouncementAction) -> Unit = {},
    ) {
        composeRule.setContent {
            WatchioTheme {
                AnnouncementsScreen(
                    state = AnnouncementsUiState(
                        snapshot = AnnouncementSnapshot(listOf(AnnouncementItem(announcement, false, false)), true),
                        selectedId = selectedId,
                    ),
                    onBack = {}, onRefresh = {}, onOpen = onOpen, onCloseDetails = {},
                    onDismiss = {}, onToggleArchived = {}, onAction = onAction,
                )
            }
        }
    }

    private val announcement = Announcement(
        id = "first", title = "Welcome", body = "Announcements are ready.",
        publishedAt = "2026-08-30T12:00:00Z", type = AnnouncementType.GENERAL,
        priority = AnnouncementPriority.NORMAL, action = AnnouncementAction.OpenUpdater(),
    )
}
