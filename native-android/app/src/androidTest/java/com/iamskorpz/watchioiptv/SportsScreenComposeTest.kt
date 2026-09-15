package com.iamskorpz.watchioiptv

import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.feature.sports.SportsChannelCandidate
import com.iamskorpz.watchioiptv.feature.sports.SportsCompetition
import com.iamskorpz.watchioiptv.feature.sports.SportsDateSchedule
import com.iamskorpz.watchioiptv.feature.sports.SportsFixture
import com.iamskorpz.watchioiptv.feature.sports.SportsFixtureStatus
import com.iamskorpz.watchioiptv.feature.sports.SportsLoadState
import com.iamskorpz.watchioiptv.feature.sports.SportsMatchConfidence
import com.iamskorpz.watchioiptv.feature.sports.SportsScreen
import com.iamskorpz.watchioiptv.feature.sports.SportsUiState
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SportsScreenComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun loadingStateIsVisible() {
        setContent(SportsUiState(day, SportsLoadState.Loading))
        composeRule.onNodeWithTag("sports-loading").assertIsDisplayed()
    }

    @Test fun sharedMainTabBackgroundIsVisible() {
        setContent(readyState())
        composeRule.onNodeWithTag("sports-background").assertIsDisplayed()
    }

    @Test fun standardHeaderAndDateControlsAreVisible() {
        setContent(readyState())
        composeRule.onNodeWithTag("sports-header").assertIsDisplayed()
        composeRule.onNodeWithTag("sports-branding").assertIsDisplayed()
        composeRule.onNodeWithTag("sports-title").assertIsDisplayed()
        composeRule.onNodeWithTag("sports-previous").assertIsDisplayed()
        composeRule.onNodeWithTag("sports-today").assertIsDisplayed()
        composeRule.onNodeWithTag("sports-next").assertIsDisplayed()
    }

    @Test fun competitionGroupingAndFixtureAreVisible() {
        setContent(readyState())
        composeRule.onNodeWithText("Premier League").assertIsDisplayed()
        composeRule.onNodeWithText("Arsenal").assertIsDisplayed()
        composeRule.onNodeWithText("Chelsea").assertIsDisplayed()
    }

    @Test fun fixtureRowIsCompact() {
        setContent(readyState())
        val bounds = composeRule.onNodeWithTag("fixture-1").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue(height <= 72.dp)
    }

    @Test fun emptyStateIsPolishedAndBoundedToContent() {
        val emptyDay = day.minusDays(1)
        setContent(SportsUiState(emptyDay, SportsLoadState.Ready(SportsDateSchedule(emptyDay, emptyList()))))
        composeRule.onNodeWithTag("sports-empty").assertIsDisplayed()
        composeRule.onNodeWithText("No football fixtures on this date").assertIsDisplayed()
        composeRule.onNodeWithText("Try another date using the controls above.").assertIsDisplayed()
    }

    @Test fun dateControlsExposeDpadFocusActions() {
        setContent(readyState())
        listOf("sports-previous", "sports-today", "sports-next").forEach { tag ->
            val semantics = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertTrue("$tag must accept D-pad focus", semantics.contains(SemanticsActions.RequestFocus))
        }
    }

    @Test fun backInvokesReturnToHome() {
        var backed = false
        setContent(readyState(), onBack = { backed = true })
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle { assertTrue(backed) }
    }

    @Test fun watchInvokesFixtureSelection() {
        var watched: SportsFixture? = null
        setContent(readyState(), onWatch = { watched = it })
        composeRule.onNodeWithText("WATCH").performClick()
        composeRule.runOnIdle { assertEquals(fixture, watched) }
    }

    @Test fun candidatePlayInvokesExistingChannelCallback() {
        var played: LiveTvChannel? = null
        val candidate = SportsChannelCandidate(channel, 130, SportsMatchConfidence.High, "Arsenal v Chelsea")
        setContent(readyState().copy(selectedFixture = fixture, candidates = listOf(candidate)), onPlay = { played = it })
        composeRule.onNodeWithText("Available channels").assertIsDisplayed()
        composeRule.onNodeWithText("PLAY").performClick()
        composeRule.runOnIdle { assertEquals(channel, played) }
    }

    @Test fun noMatchMessageIsVisible() {
        setContent(readyState().copy(selectedFixture = fixture))
        composeRule.onNodeWithText("No matching TV guide entries were found. No sports channels are available in this provider.").assertIsDisplayed()
    }

    @Test fun rateLimitShowsFriendlyMessageAndDisablesRetry() {
        setContent(SportsUiState(day, SportsLoadState.Error("Too many fixture requests", "Please wait a moment and try again.", retryEnabled = false)))
        composeRule.onNodeWithText("Too many fixture requests").assertIsDisplayed()
        composeRule.onNodeWithText("Please wait a moment and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again shortly").assertHasNoClickAction()
    }

    @Test fun missingKeyShowsSetupActions() {
        var configure = false
        var register = false
        setContent(
            SportsUiState(day, SportsLoadState.SetupRequired),
            onConfigure = { configure = true },
            onRegister = { register = true },
        )
        composeRule.onNodeWithTag("sports-api-setup").assertIsDisplayed()
        composeRule.onNodeWithText("Get Free API Key").performClick()
        composeRule.onNodeWithText("Enter API Key").performClick()
        composeRule.runOnIdle { assertTrue(register && configure) }
    }

    @Test fun rejectedKeyShowsUpdateAction() {
        setContent(SportsUiState(day, SportsLoadState.CredentialNeedsAttention))
        composeRule.onNodeWithText("API key needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("Enter API Key").assertIsDisplayed()
    }

    @Test fun dialogCloseReturnsToFixtureList() {
        var closed = false
        setContent(readyState().copy(selectedFixture = fixture), onClose = { closed = true })
        composeRule.onNodeWithText("Close").performClick()
        composeRule.runOnIdle { assertTrue(closed) }
    }

    private fun setContent(
        state: SportsUiState,
        onWatch: (SportsFixture) -> Unit = {},
        onClose: () -> Unit = {},
        onPlay: (LiveTvChannel) -> Unit = {},
        onConfigure: () -> Unit = {},
        onRegister: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.runOnUiThread {
            if (composeRule.activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        composeRule.waitForIdle()
        composeRule.setContent {
            WatchioTheme { SportsScreen(state, {}, {}, {}, {}, onWatch, onClose, onPlay, onConfigure, onRegister, onBack) }
        }
    }

    private fun readyState() = SportsUiState(day, SportsLoadState.Ready(SportsDateSchedule(day, listOf(SportsCompetition("PL", "Premier League", 0, listOf(fixture))))))

    private companion object {
        val day: LocalDate = LocalDate.of(2026, 9, 6)
        val fixture = SportsFixture("1", "PL", "Premier League", Instant.parse("2026-09-06T16:30:00Z"), "Arsenal", "Chelsea", SportsFixtureStatus.Scheduled)
        val channel = LiveTvChannel(ProviderId("p1"), ProviderType.Xtream, "sports-1", "Sports One", null, "sports", "sports.one", "ts", null, emptyMap(), 1, false)
    }
}
