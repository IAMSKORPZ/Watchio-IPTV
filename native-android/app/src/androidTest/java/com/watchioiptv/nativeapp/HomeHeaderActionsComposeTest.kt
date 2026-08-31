package com.watchioiptv.nativeapp

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.watchioiptv.nativeapp.ui.HomeTopBar
import com.watchioiptv.nativeapp.ui.theme.WatchioTheme
import java.time.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeHeaderActionsComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tvHeaderActionsAreIconOnlyAccessibleFocusableEvenAndInteractive() {
        var searchClicked = false
        var sportsClicked = false
        var announcementsClicked = false
        var playlistClicked = false

        composeRule.runOnUiThread {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        composeRule.setContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeTopBar(
                        now = LocalDateTime.of(2026, 8, 30, 12, 0),
                        onSearch = { searchClicked = true },
                        onSports = { sportsClicked = true },
                        onAnnouncements = { announcementsClicked = true },
                        onProviders = { playlistClicked = true },
                        announcementUnreadCount = 12,
                    )
                }
            }
        }

        val actions = listOf(
            "Search" to "home-search",
            "Sports" to "home-action-sports",
            "Announcements" to "home-action-announcements",
            "Playlist" to "home-action-playlist",
        )
        val leftEdges = actions.map { (label, tag) ->
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
            assertTrue(
                "$label must not render visible header text",
                composeRule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            )
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag width must remain 52dp", bounds.right - bounds.left == 52.dp)
            assertTrue("$tag height must remain 52dp", bounds.bottom - bounds.top == 52.dp)
            val semantics = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertTrue("$tag must remain focusable", semantics.contains(SemanticsProperties.Focused))
            assertTrue("$tag must expose RequestFocus", semantics.contains(SemanticsActions.RequestFocus))
            composeRule.onNodeWithTag(tag).performClick()
            bounds.left
        }
        assertTrue(searchClicked && sportsClicked && announcementsClicked && playlistClicked)
        assertTrue("header actions must preserve left-to-right DPAD order", leftEdges == leftEdges.sorted())
        composeRule.onNodeWithTag("home-announcements-badge").assertIsDisplayed()
        composeRule.onNodeWithText("9+").assertIsDisplayed()
    }
}
