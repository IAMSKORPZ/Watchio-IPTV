package com.watchioiptv.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.feature.provider.XtreamProviderFormState
import com.watchioiptv.nativeapp.ui.XtreamProviderScreen
import com.watchioiptv.nativeapp.ui.theme.WatchioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderFormComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun xtreamTvDpadTraversalAndSingleConnectActivation() {
        var submissions = 0
        composeRule.activity.setContent {
            WatchioTheme {
                XtreamProviderScreen(
                    state = XtreamProviderFormState(providerName = "Test", username = "User", password = "Password"),
                    onProviderName = {},
                    onUsername = {},
                    onPassword = {},
                    onConnect = { submissions++ },
                    onQuickLogin = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("xtream-provider-name").assertIsFocused()
        composeRule.onNodeWithTag("xtream-provider-name").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("xtream-username").assertIsFocused()
        composeRule.onNodeWithTag("xtream-username").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("xtream-password").assertIsFocused()
        composeRule.onNodeWithTag("xtream-password").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodes(hasContentDescription("SIGN IN")).fetchSemanticsNodes().singleOrNull()?.config
                ?.let { it[androidx.compose.ui.semantics.SemanticsProperties.Focused] } == true
        }
        composeRule.onNodeWithContentDescription("SIGN IN").assertIsFocused()

        composeRule.onNodeWithContentDescription("SIGN IN").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("QUICK LOGIN").assertIsFocused()
        composeRule.onNodeWithContentDescription("QUICK LOGIN").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Cancel").assertIsFocused()
        composeRule.onNodeWithContentDescription("Cancel").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithContentDescription("QUICK LOGIN").assertIsFocused()
        composeRule.onNodeWithContentDescription("QUICK LOGIN").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithContentDescription("SIGN IN").assertIsFocused()

        composeRule.onNodeWithContentDescription("SIGN IN").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("xtream-password").assertIsFocused()
        composeRule.onNodeWithTag("xtream-password").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("xtream-username").assertIsFocused()
        composeRule.onNodeWithTag("xtream-username").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("xtream-provider-name").assertIsFocused()

        composeRule.onNodeWithTag("xtream-provider-name").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("xtream-username").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("xtream-password").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("SIGN IN").performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun xtreamLandscapeFormLowerControlsAreReachable() {
        composeRule.activity.setContent {
            WatchioTheme {
                XtreamProviderScreen(
                    state = XtreamProviderFormState(providerName = "Living Room", username = "User", password = "Password"),
                    onProviderName = {}, onUsername = {}, onPassword = {}, onConnect = {}, onQuickLogin = {}, onBack = {},
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Password"))
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("SIGN IN"))
        composeRule.onNodeWithText("SIGN IN").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Cancel"))
        composeRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun xtreamLoginFieldsAcceptTextAndKeepValuesAfterFocusChanges() {
        var state by mutableStateOf(XtreamProviderFormState())
        composeRule.activity.setContent {
            WatchioTheme {
                XtreamProviderScreen(
                    state = state,
                    onProviderName = { state = state.copy(providerName = it) },
                    onUsername = { state = state.copy(username = it) },
                    onPassword = { state = state.copy(password = it) },
                    onConnect = {}, onQuickLogin = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("xtream-provider-name").performTextInput("Test IPTV")
        composeRule.onNodeWithTag("xtream-provider-name").assertTextContains("Test IPTV")
        composeRule.onAllNodesWithTag("xtream-server-url").assertCountEquals(0)
        composeRule.onNodeWithTag("xtream-username").performTextInput("testuser")
        composeRule.onNodeWithTag("xtream-username").assertTextContains("testuser")
        composeRule.onNodeWithTag("xtream-password").performTextInput("testpass")

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("SIGN IN"))
        composeRule.onNodeWithText("SIGN IN").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Test IPTV"))
        composeRule.onNodeWithTag("xtream-provider-name").assertTextContains("Test IPTV")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("testuser"))
        composeRule.onNodeWithTag("xtream-username").assertTextContains("testuser")
    }

    @Test
    fun xtreamFormHasCompactFieldsAllActionsAndNoUrl() {
        composeRule.activity.setContent {
            WatchioTheme {
                XtreamProviderScreen(
                    state = XtreamProviderFormState(providerName = "Living Room", username = "User", password = "secret"),
                    onProviderName = {}, onUsername = {}, onPassword = {}, onConnect = {}, onQuickLogin = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Provider Name").assertIsDisplayed()
        composeRule.onNodeWithText("Username").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        assertTrue(
            "password field must remain masked",
            composeRule.onNodeWithTag("xtream-password").fetchSemanticsNode().config
                .contains(androidx.compose.ui.semantics.SemanticsProperties.Password),
        )
        composeRule.onNodeWithText("SIGN IN").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("QUICK LOGIN"))
        composeRule.onNodeWithText("QUICK LOGIN").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Cancel"))
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onAllNodesWithTag("xtream-server-url").assertCountEquals(0)
        assertTrue(composeRule.onAllNodes(hasText("http://", substring = true)).fetchSemanticsNodes().isEmpty())
        val screenBounds = composeRule.onNodeWithTag("xtream-login-screen").getUnclippedBoundsInRoot()
        val fieldBounds = composeRule.onNodeWithTag("xtream-provider-name").getUnclippedBoundsInRoot()
        assertTrue("field must remain narrower than screen", fieldBounds.right - fieldBounds.left < screenBounds.right - screenBounds.left)
        assertEquals(56f, (fieldBounds.bottom - fieldBounds.top).value, 1f)
    }
}
