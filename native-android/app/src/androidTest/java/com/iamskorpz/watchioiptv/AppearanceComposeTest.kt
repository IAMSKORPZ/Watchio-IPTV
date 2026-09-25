package com.iamskorpz.watchioiptv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.feature.settings.AppearanceEditorState
import com.iamskorpz.watchioiptv.feature.settings.AppearanceScreen
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import com.iamskorpz.watchioiptv.ui.theme.WatchioBuiltInThemes
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeDefinition
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioComponentSizes
import com.iamskorpz.watchioiptv.ui.theme.toAppearanceLong
import com.iamskorpz.watchioiptv.ui.components.WatchioButton
import com.iamskorpz.watchioiptv.ui.components.WatchioCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun categoriesPreviewAndHexEditorAreReachable() {
        composeRule.setContent { testScreen() }
        composeRule.onNodeWithTag("appearance-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-category-Colours").performClick()
        composeRule.onNodeWithTag("appearance-hex-input").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-colour-chart-Background").performClick()
        composeRule.onNodeWithTag("appearance-colour-chart-dialog").assertIsDisplayed()
    }

    @Test fun dirtyBackRequiresDiscardConfirmation() {
        composeRule.setContent { testScreen(dirty = true) }
        composeRule.onNodeWithTag("appearance-back").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").assertIsDisplayed()
    }

    @Test fun applyInvokesExplicitCallback() {
        var applied = false
        composeRule.setContent { testScreen(dirty = true, onApply = { applied = true }) }
        composeRule.onNodeWithTag("appearance-apply").performClick()
        assertTrue(applied)
    }

    @Test fun headerActionsExposeFocusAndTransferInBothDirections() {
        composeRule.setContent { testScreen(dirty = true) }
        val back = composeRule.onNodeWithTag("appearance-back")
        val apply = composeRule.onNodeWithTag("appearance-apply")

        back.performSemanticsAction(SemanticsActions.RequestFocus)
        back.assertIsFocused().assertStateDescription("Focused")
        apply.assertIsNotFocused().assertStateDescription("Not focused")

        back.performKeyInput { pressKey(Key.DirectionRight) }
        apply.assertIsFocused().assertStateDescription("Focused")
        back.assertIsNotFocused().assertStateDescription("Not focused")

        apply.performKeyInput { pressKey(Key.DirectionLeft) }
        back.assertIsFocused().assertStateDescription("Focused")
        apply.assertIsNotFocused().assertStateDescription("Not focused")

        back.performKeyInput { pressKey(Key.DirectionDown) }
        back.assertIsNotFocused().assertStateDescription("Not focused")
        apply.assertIsNotFocused().assertStateDescription("Not focused")
    }

    @Test fun disabledApplyIsDistinctAndHeaderActionsRemainBounded() {
        composeRule.setContent { testScreen(dirty = false) }
        composeRule.onNodeWithTag("appearance-back")
            .assertIsEnabled()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("appearance-apply")
            .assertIsNotEnabled()
            .assertStateDescription("Disabled")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("appearance-back")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val focusedBounds = composeRule.onNodeWithTag("appearance-back").fetchSemanticsNode().boundsInRoot
        assertTrue(focusedBounds.left >= rootBounds.left)
        assertTrue(focusedBounds.top >= rootBounds.top)
        assertTrue(focusedBounds.right <= rootBounds.right)
        assertTrue(focusedBounds.bottom <= rootBounds.bottom)
    }

    @Test fun resetAllRequiresConfirmation() {
        composeRule.setContent { testScreen() }
        composeRule.onNodeWithTag("appearance-controls")
            .performScrollToNode(hasTestTag("appearance-reset-all"))
        composeRule.onNodeWithTag("appearance-reset-all").performClick()
        composeRule.onNodeWithText("Reset entire theme?").assertIsDisplayed()
    }

    @Test fun tvFocusControlsAndSlidersAreReachable() {
        composeRule.setContent { testScreen() }
        composeRule.onNodeWithTag("appearance-categories")
            .performScrollToNode(hasTestTag("appearance-category-TvFocus"))
        composeRule.onNodeWithTag("appearance-category-TvFocus").performClick()
        composeRule.onNodeWithTag("appearance-slider-Focus-outline").assertIsDisplayed()
        composeRule.onNodeWithText("Glow intensity", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Focus scale", substring = true).assertIsDisplayed()
    }

    @Test fun builtInPresetCardsUpdateDraftPreviewOnly() {
        var applied = false
        composeRule.setContent {
            val draft = remember { mutableStateOf(WatchioThemeDefinition.WatchioDefault) }
            WatchioTheme {
                AppearanceScreen(
                    state = AppearanceEditorState(draft = draft.value, dirty = draft.value.id != WatchioThemeDefinition.WatchioDefault.id),
                    onBack = {}, onUpdate = { draft.value = it(draft.value) },
                    onSelect = { id -> WatchioBuiltInThemes.byId(id)?.let { draft.value = it } },
                    onApply = { applied = true }, onDiscard = {}, onResetSection = {}, onResetAll = {},
                    onDuplicate = {}, onRename = {}, onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("appearance-preset-builtin-midnight").performClick()
        composeRule.onNodeWithTag("appearance-preview-theme-builtin-midnight").assertIsDisplayed()
        assertTrue(!applied)
    }

    @Test fun realSharedComponentsReceiveLightAmoledAndOceanTokens() {
        val presetState = mutableStateOf(WatchioBuiltInThemes.Light)
        var observedBackground = 0L
        var observedCard = 0L
        var observedButton = 0L
        var observedCardWidth = 0f
        composeRule.setContent {
            WatchioTheme(appearance = presetState.value) {
                val colors = LocalWatchioColors.current
                observedBackground = colors.surfaceBase.toAppearanceLong()
                observedCard = colors.cardSurface.toAppearanceLong()
                observedButton = colors.buttonSurface.toAppearanceLong()
                observedCardWidth = LocalWatchioComponentSizes.current.cardMinWidth.value
                androidx.compose.foundation.layout.Column {
                    WatchioCard(modifier = androidx.compose.ui.Modifier.testTag("phase2-real-card"), onClick = {}) { }
                    WatchioButton("Action", onClick = {}, modifier = androidx.compose.ui.Modifier.testTag("phase2-real-button"))
                }
            }
        }
        composeRule.onNodeWithTag("phase2-real-card").assertIsDisplayed()
        composeRule.onNodeWithTag("phase2-real-button").assertIsDisplayed()

        listOf(WatchioBuiltInThemes.Light, WatchioBuiltInThemes.AmoledBlack, WatchioBuiltInThemes.Ocean).forEach { preset ->
            composeRule.runOnIdle { presetState.value = preset }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("phase2-real-card").assertIsDisplayed()
            composeRule.onNodeWithTag("phase2-real-button").assertIsDisplayed()
            composeRule.runOnIdle {
                assertTrue(observedBackground == preset.colors.appBackground)
                assertTrue(observedCard == preset.colors.cardBackground)
                assertTrue(observedButton == preset.colors.buttonBackground)
                assertTrue(observedCardWidth > 0f)
            }
        }
    }

    @Composable
    private fun testScreen(dirty: Boolean = false, onApply: () -> Unit = {}) =
        WatchioTheme {
            AppearanceScreen(
                state = AppearanceEditorState(draft = WatchioThemeDefinition.WatchioDefault, dirty = dirty),
                onBack = {}, onUpdate = {}, onSelect = {}, onApply = onApply, onDiscard = {},
                onResetSection = {}, onResetAll = {}, onDuplicate = {}, onRename = {}, onDelete = {},
            )
        }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertStateDescription(value: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))
