package com.iamskorpz.watchioiptv

import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.input.key.Key
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
import com.iamskorpz.watchioiptv.feature.sports.v2.MatchChannelConfidence
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsBroadcast
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsDataSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceIdentity
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
        composeRule.onAllNodesWithText("Premier League").assertCountEquals(2)
        composeRule.onNodeWithText("Arsenal").assertIsDisplayed()
        composeRule.onNodeWithText("Chelsea").assertIsDisplayed()
    }

    @Test fun fixtureRowIsCompact() {
        setContent(readyState())
        val bounds = composeRule.onNodeWithTag("fixture-1").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue(height <= 190.dp)
    }

    @Test fun emptyStateIsPolishedAndBoundedToContent() {
        val emptyDay = day.minusDays(1)
        setContent(SportsUiState(emptyDay, SportsLoadState.Ready(SportsDateSchedule(emptyDay, emptyList()))))
        composeRule.onNodeWithTag("sports-empty").assertIsDisplayed()
        composeRule.onNodeWithText("NO MATCHES ON THIS DATE").assertIsDisplayed()
        composeRule.onNodeWithText("Try another date using the controls above.").assertIsDisplayed()
    }

    @Test fun dateControlsExposeDpadFocusActions() {
        setContent(readyState())
        listOf("sports-previous", "sports-today", "sports-next").forEach { tag ->
            val semantics = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertTrue("$tag must accept D-pad focus", semantics.contains(SemanticsActions.RequestFocus))
        }
    }

    @Test fun shortCompetitionFilterListIsCentered() {
        setContent(readyState())
        val row = composeRule.onNodeWithTag("sports-competition-filters").getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithTag("sports-filter-all").getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithTag("sports-filter-PL").getUnclippedBoundsInRoot()
        val leftGap = first.left - row.left
        val rightGap = row.right - last.right
        assertTrue(kotlin.math.abs(leftGap.value - rightGap.value) < 3f)
    }

    @Test fun longCompetitionFilterListScrollsWithoutClippingEitherEdge() {
        val competitions = (0..11).map { SportsCompetition("C$it", "Competition $it", it, listOf(fixture.copy(id = "$it", competitionId = "C$it"))) }
        setContent(SportsUiState(day, SportsLoadState.Ready(SportsDateSchedule(day, competitions))))
        composeRule.onNodeWithTag("sports-filter-all").assertIsDisplayed()
        composeRule.onNodeWithTag("sports-competition-filters").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("sports-filter-C11").assertIsDisplayed()
    }

    @Test fun dpadTraversalMovesAcrossCompetitionFilters() {
        val competitions = (0..7).map { SportsCompetition("C$it", "Competition $it", it, listOf(fixture.copy(id = "$it", competitionId = "C$it"))) }
        setContent(SportsUiState(day, SportsLoadState.Ready(SportsDateSchedule(day, competitions))))
        composeRule.onNodeWithTag("sports-filter-all").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(8) { composeRule.onNodeWithTag("sports-competition-filters").performKeyInput { pressKey(Key.DirectionRight) } }
        composeRule.onNodeWithTag("sports-filter-C7").assertIsDisplayed()
    }

    @Test fun selectedDateOpensCalendarAndChoosingDayClosesIt() {
        var selected: LocalDate? = null
        setContent(readyState(), onSelectDate = { selected = it })
        composeRule.onNodeWithTag("sports-today").performClick()
        composeRule.onNodeWithTag("sports-calendar").assertIsDisplayed()
        composeRule.onNodeWithTag("sports-calendar-day-2026-09-12").performClick()
        composeRule.runOnIdle { assertEquals(LocalDate.of(2026, 9, 12), selected) }
        composeRule.onNodeWithTag("sports-calendar").assertDoesNotExist()
    }

    @Test fun calendarMonthNavigationAndBackDismissWork() {
        setContent(readyState())
        composeRule.onNodeWithTag("sports-today").performClick()
        composeRule.onNodeWithTag("sports-calendar-next-month").performClick()
        composeRule.onNodeWithText("October 2026").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("sports-calendar").assertDoesNotExist()
    }

    @Test fun upcomingFixtureReminderTogglesExplicitly() {
        var toggled: SportsFixture? = null
        setContent(readyState(), onToggleReminder = { toggled = it })
        composeRule.onNodeWithTag("fixture-reminder-1").performClick()
        composeRule.runOnIdle { assertEquals(fixture, toggled) }
    }

    @Test fun activeReminderStateIsVisible() {
        setContent(readyState().copy(reminderKeys = setOf("football-data:1")))
        composeRule.onNodeWithText("NOTIFICATION ON").assertIsDisplayed()
    }

    @Test fun backInvokesReturnToHome() {
        var backed = false
        setContent(readyState(), onBack = { backed = true })
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle { assertTrue(backed) }
    }

    @Test fun cardClickDoesNotStartPlaybackAndExplicitChannelActionDoes() {
        var watched: SportsFixture? = null
        setContent(readyState(withBroadcast = true), onWatch = { watched = it })
        composeRule.onNodeWithTag("fixture-1").assertHasNoClickAction()
        composeRule.runOnIdle { assertEquals(null, watched) }
        composeRule.onNodeWithTag("fixture-watch-1").performClick()
        composeRule.runOnIdle { assertEquals(fixture, watched) }
    }

    @Test fun verifiedCandidateExposesExplicitWatchLive() {
        val candidate = SportsChannelCandidate(channel, 130, SportsMatchConfidence.High, "Arsenal v Chelsea", v2Confidence = MatchChannelConfidence.VERIFIED)
        setContent(readyState().copy(selectedFixture = fixture, candidates = listOf(candidate)))
        composeRule.onNodeWithText("▶ WATCH LIVE").assertIsDisplayed()
    }

    @Test fun strongCandidateExposesExplicitWatchLive() {
        val candidate = SportsChannelCandidate(channel, 90, SportsMatchConfidence.High, "Arsenal v Chelsea", v2Confidence = MatchChannelConfidence.STRONG)
        setContent(readyState().copy(selectedFixture = fixture, candidates = listOf(candidate)))
        composeRule.onNodeWithText("▶ WATCH LIVE").assertIsDisplayed()
    }

    @Test fun possibleCandidateUsesChooserWording() {
        val candidate = SportsChannelCandidate(channel, 70, SportsMatchConfidence.Medium, v2Confidence = MatchChannelConfidence.POSSIBLE)
        setContent(readyState().copy(selectedFixture = fixture, candidates = listOf(candidate)))
        composeRule.onNodeWithText("CHOOSE").assertIsDisplayed()
    }

    @Test fun unavailableLiveFixtureShowsDisabledNoChannelAction() {
        val live = fixture.copy(status = SportsFixtureStatus.Live)
        val schedule = SportsDateSchedule(day, listOf(SportsCompetition("PL", "Premier League", 0, listOf(live))))
        setContent(SportsUiState(day, SportsLoadState.Ready(schedule, broadcastUnavailable = setOf(live.id))))
        composeRule.onNodeWithText("NO CHANNEL FOUND").assertIsNotEnabled()
    }

    @Test fun candidatePlayInvokesExistingChannelCallback() {
        var played: LiveTvChannel? = null
        val candidate = SportsChannelCandidate(channel, 130, SportsMatchConfidence.High, "Arsenal v Chelsea")
        setContent(readyState().copy(selectedFixture = fixture, candidates = listOf(candidate)), onPlay = { played = it })
        composeRule.onNodeWithText("Available channels").assertIsDisplayed()
        composeRule.onNodeWithText("▶ WATCH LIVE").performClick()
        composeRule.runOnIdle { assertEquals(channel, played) }
    }

    @Test fun noMatchMessageIsVisible() {
        setContent(readyState().copy(selectedFixture = fixture))
        composeRule.onNodeWithText("No matching channel found for this provider.").assertIsDisplayed()
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
        onSelectDate: (LocalDate) -> Unit = {},
        onToggleReminder: (SportsFixture) -> Unit = {},
    ) {
        composeRule.runOnUiThread {
            if (composeRule.activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        composeRule.waitForIdle()
        composeRule.setContent {
            WatchioTheme { SportsScreen(state, {}, {}, {}, {}, onWatch, onClose, onPlay, onConfigure, onRegister, onBack, onSelectDate, onToggleReminder) }
        }
    }

    private fun readyState(withBroadcast: Boolean = false): SportsUiState {
        val schedule = SportsDateSchedule(day, listOf(SportsCompetition("PL", "Premier League", 0, listOf(fixture))))
        val broadcasts = if (withBroadcast) mapOf(fixture.id to listOf(broadcast)) else emptyMap()
        return SportsUiState(day, SportsLoadState.Ready(schedule, broadcasts = broadcasts))
    }

    private companion object {
        val day: LocalDate = LocalDate.of(2026, 9, 6)
        val fixture = SportsFixture("1", "PL", "Premier League", Instant.parse("2026-09-06T16:30:00Z"), "Arsenal", "Chelsea", SportsFixtureStatus.Scheduled)
        val channel = LiveTvChannel(ProviderId("p1"), ProviderType.Xtream, "sports-1", "Sports One", null, "sports", "sports.one", "ts", null, emptyMap(), 1, false)
        val broadcast = SportsBroadcast(SportsSourceIdentity(SportsDataSource.SoccersApi, "b1"), displayName = "Sky Sports", countryOrRegion = "GB", fetchedAt = Instant.parse("2026-09-06T12:00:00Z"))
    }
}
