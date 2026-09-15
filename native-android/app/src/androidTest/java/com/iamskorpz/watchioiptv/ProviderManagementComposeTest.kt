package com.iamskorpz.watchioiptv

import androidx.activity.compose.setContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.model.WatchioProvider
import com.iamskorpz.watchioiptv.feature.provider.ManagedServerChoice
import com.iamskorpz.watchioiptv.feature.provider.ProviderManagementUiState
import com.iamskorpz.watchioiptv.feature.provider.ProviderRowUiState
import com.iamskorpz.watchioiptv.ui.ProviderManagementScreen
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderManagementComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun populatedScreenShowsSafeHierarchyAndActions() {
        val providerId = ProviderId("xtream-managed-primary")
        showScreen(
            ProviderManagementUiState(
                providers = listOf(providerRow(providerId)),
                selectedProviderId = providerId,
                serverChoices = listOf(ManagedServerChoice("private-id", "AW999", active = true)),
            ),
        )

        composeRule.onNodeWithText("PROVIDER MANAGEMENT").assertIsDisplayed()
        composeRule.onNodeWithText("Manage your providers").assertIsDisplayed()
        composeRule.onNodeWithText("SAVED PROVIDERS").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-select-${providerId.value}").assertIsDisplayed()
        composeRule.onNodeWithText("AW999").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-refresh-${providerId.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-management-content").performScrollToNode(hasTestTag("provider-switch-${providerId.value}"))
        composeRule.onNodeWithTag("provider-switch-${providerId.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-management-content").performScrollToNode(hasTestTag("provider-remove-${providerId.value}"))
        composeRule.onNodeWithTag("provider-remove-${providerId.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-management-content").performScrollToNode(hasTestTag("providers-add-xtream"))
        composeRule.onNodeWithText("ADD PROVIDER").assertIsDisplayed()
        composeRule.onNodeWithTag("providers-add-xtream").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("https://secret.example:8880", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("private-id", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun providerAndAddActionsRemainOperableByRemoteAndTouch() {
        val providerId = ProviderId("xtream-managed-primary")
        var selected: ProviderId? = null
        var refreshed: ProviderId? = null
        var added = ""
        showScreen(
            state = ProviderManagementUiState(providers = listOf(providerRow(providerId))),
            onSelect = { selected = it },
            onRefresh = { refreshed = it },
            onAddXtream = { added = "xtream" },
        )

        composeRule.onNodeWithTag("provider-select-${providerId.value}")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performClick()
        composeRule.onNodeWithTag("provider-refresh-${providerId.value}").performClick()
        composeRule.onNodeWithTag("provider-select-${providerId.value}").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Switch Server").assertIsFocused()
        composeRule.onNodeWithTag("provider-management-content").performScrollToNode(hasTestTag("providers-add-xtream"))
        composeRule.onNodeWithTag("providers-add-xtream").performClick()
        assertEquals(providerId, selected)
        assertEquals(providerId, refreshed)
        assertEquals("xtream", added)
    }

    @Test
    fun emptyStateSeparatesAllAddProviderRoutesAndHasNoContentBackTile() {
        var opened = ""
        composeRule.activity.setContent {
            WatchioTheme {
                ProviderManagementScreen(
                    state = ProviderManagementUiState(),
                    onSelect = {},
                    onRefresh = {},
                    onSwitchServer = { _, _ -> },
                    onDelete = {},
                    onAddXtreamProvider = { opened = "xtream" },
                    onAddM3uUrlProvider = { opened = "url" },
                    onAddM3uFileProvider = { opened = "file" },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("providers-back-icon").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("providers-back").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("No providers added yet.").assertIsDisplayed()
        composeRule.onNodeWithTag("providers-add-xtream").performClick()
        assertEquals("xtream", opened)
        composeRule.onNodeWithTag("providers-add-m3u-url").performClick()
        assertEquals("url", opened)
        composeRule.onNodeWithTag("providers-add-m3u-file").performClick()
        assertEquals("file", opened)
    }

    @Test
    fun multipleProvidersAndManagedActionsKeepExistingCallbacks() {
        val managedId = ProviderId("xtream-managed-primary")
        val m3uId = ProviderId("m3u-secondary")
        var switched: Pair<ProviderId, String>? = null
        var removed: ProviderId? = null
        showScreen(
            state = ProviderManagementUiState(
                providers = listOf(providerRow(managedId), providerRow(m3uId, ProviderType.M3uUrl, "Kitchen")),
                selectedProviderId = managedId,
                serverChoices = listOf(
                    ManagedServerChoice("primary", "AW999", active = true),
                    ManagedServerChoice("backup", "MediaTitans", active = false),
                ),
            ),
            onSwitch = { provider, server -> switched = provider to server },
            onDelete = { removed = it },
        )

        composeRule.onNodeWithTag("provider-select-${managedId.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-select-${m3uId.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-switch-${managedId.value}").performClick()
        composeRule.onNodeWithText("MediaTitans").performClick()
        assertEquals(managedId to "backup", switched)
        composeRule.onNodeWithTag("provider-remove-${managedId.value}").performClick()
        composeRule.onNodeWithTag("provider-remove-confirm").performClick()
        assertEquals(managedId, removed)
        assertTrue(composeRule.onAllNodesWithTag("provider-switch-${m3uId.value}").fetchSemanticsNodes().isEmpty())
    }

    private fun showScreen(
        state: ProviderManagementUiState,
        onSelect: (ProviderId) -> Unit = {},
        onRefresh: (ProviderId) -> Unit = {},
        onAddXtream: () -> Unit = {},
        onSwitch: (ProviderId, String) -> Unit = { _, _ -> },
        onDelete: (ProviderId) -> Unit = {},
    ) {
        lateinit var inputModeManager: InputModeManager
        composeRule.activity.setContent {
            inputModeManager = LocalInputModeManager.current
            WatchioTheme {
                ProviderManagementScreen(
                    state = state,
                    onSelect = onSelect,
                    onRefresh = onRefresh,
                    onSwitchServer = onSwitch,
                    onDelete = onDelete,
                    onAddXtreamProvider = onAddXtream,
                    onAddM3uUrlProvider = {},
                    onAddM3uFileProvider = {},
                    onBack = {},
                )
            }
        }
        composeRule.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
        }
    }

    private fun providerRow(
        id: ProviderId,
        type: ProviderType = ProviderType.Xtream,
        name: String = "Family TV",
    ) = ProviderRowUiState(
        provider = WatchioProvider(
            id = id,
            displayName = name,
            type = type,
            serverUrl = "https://secret.example:8880",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
            lastRefreshAtEpochMs = 3L,
            enabled = true,
        ),
        liveCount = 120,
        movieCount = 45,
        seriesCount = 23,
        refreshState = "Updated",
    )
}
