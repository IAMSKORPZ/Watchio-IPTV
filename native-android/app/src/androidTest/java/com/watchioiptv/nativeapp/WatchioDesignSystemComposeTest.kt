package com.watchioiptv.nativeapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.watchioiptv.nativeapp.ui.components.WatchioButton
import com.watchioiptv.nativeapp.ui.components.WatchioButtonVariant
import com.watchioiptv.nativeapp.ui.components.WatchioChip
import com.watchioiptv.nativeapp.ui.components.WatchioEmptyState
import com.watchioiptv.nativeapp.ui.components.WatchioErrorState
import com.watchioiptv.nativeapp.ui.components.WatchioListRow
import com.watchioiptv.nativeapp.ui.components.WatchioLoading
import com.watchioiptv.nativeapp.ui.components.WatchioPosterCard
import com.watchioiptv.nativeapp.ui.components.WatchioProgressBar
import com.watchioiptv.nativeapp.ui.components.WatchioScreenHeader
import com.watchioiptv.nativeapp.ui.theme.WatchioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WatchioDesignSystemComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun designSystemCoreComponentsRenderAccessibleLabels() {
        composeRule.setContent {
            WatchioTheme {
                Column {
                    WatchioScreenHeader("Design", "Tokens")
                    WatchioButton("Primary Action", onClick = {}, variant = WatchioButtonVariant.Primary)
                    WatchioButton("Compact", onClick = {}, variant = WatchioButtonVariant.CompactAction)
                    WatchioChip("Selected Chip", selected = true, onClick = {})
                    WatchioListRow("Row Title", "Row subtitle", onClick = {})
                    WatchioPosterCard("Poster Title", imageUrl = null, onClick = {}, modifier = Modifier.testTag("poster-card"))
                }
            }
        }

        composeRule.onNodeWithText("Design").assertIsDisplayed()
        composeRule.onNodeWithText("Primary Action").assertIsDisplayed()
        composeRule.onNodeWithText("Compact").assertIsDisplayed()
        composeRule.onNodeWithText("Selected Chip").assertIsDisplayed()
        composeRule.onNodeWithText("Row Title").assertIsDisplayed()
        composeRule.onNodeWithText("Poster Title").assertIsDisplayed()
    }

    @Test
    fun designSystemStateComponentsRenderAccessibleLabels() {
        composeRule.setContent {
            WatchioTheme {
                Column {
                    WatchioEmptyState("Nothing here")
                    WatchioLoading("Loading now")
                    WatchioErrorState("Error message", onRetry = {})
                    WatchioProgressBar(progress = 0.5f, modifier = Modifier.fillMaxWidth().testTag("progress"))
                }
            }
        }

        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Loading now").assertIsDisplayed()
        composeRule.onNodeWithText("Error message").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithTag("progress").assertIsDisplayed()
    }

    @Test
    fun posterCardKeepsTwoByThreeRatio() {
        composeRule.setContent {
            WatchioTheme {
                WatchioPosterCard("Ratio", imageUrl = null, onClick = {}, modifier = Modifier.fillMaxWidth(0.25f).testTag("poster-card"))
            }
        }

        val bounds = composeRule.onNodeWithTag("poster-card", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val ratio = (bounds.bottom - bounds.top).value / (bounds.right - bounds.left).value
        assertTrue("poster shell should stay close to 2:3 plus title chrome; ratio=$ratio", ratio in 1.2f..2.2f)
    }
}
