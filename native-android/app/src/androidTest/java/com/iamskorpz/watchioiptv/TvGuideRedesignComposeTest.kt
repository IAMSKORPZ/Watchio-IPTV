package com.iamskorpz.watchioiptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerMetadata
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.data.live.LiveTvCategory
import com.iamskorpz.watchioiptv.data.live.LiveTvCategoryKind
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.feature.tvguide.ProgrammeDetails
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideScreen
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideTimeline
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideUiState
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideProgramme
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class TvGuideRedesignComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stripRemovedAndCategoriesTimeRulerDirectlyBeneathHero() {
        val now = ZonedDateTime.of(2026, 9, 26, 12, 0, 0, 0, ZoneId.of("Europe/London")).toInstant().toEpochMilli()
        val all = category("all", "All Channels")
        val channel = channel()
        val programme = programme(now)
        val state = state(categories = listOf(all), selectedCategory = all, now = now).copy(
            channels = listOf(channel),
            programmes = mapOf(channel.channelId to listOf(programme)),
            selectedChannelId = channel.channelId,
            selectedProgrammeId = programme.programmeId,
        )
        setGuide(stateProvider = { state })

        // 1. Control strip row containing NOW, days, and REFRESH must be completely removed from UI
        composeRule.onAllNodesWithTag("tv-guide-now").assertCountEquals(0)
        composeRule.onAllNodesWithTag("tv-guide-day-${state.window.day}").assertCountEquals(0)

        // 2. Hero is displayed, followed immediately by category navigation
        composeRule.onNodeWithTag("tv-guide-hero").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-guide-category-selector").assertIsDisplayed()

        // 3. Pink NOW timeline indicator and label remain intact in the timeline
        composeRule.onNodeWithTag("tv-guide-now-line").assertIsDisplayed()
        composeRule.onNodeWithText("NOW").assertIsDisplayed()
    }

    @Test
    fun firstClickStartsMiniPreviewWithoutFullscreenOrPopup() {
        val now = 1_700_000_000_000L
        val all = category("all", "All Channels")
        val ch = channel()
        val prog = programme(now)
        var previewedChannel: WatchioGuideChannel? = null
        var fullscreenTriggered = false
        var state by mutableStateOf(
            state(categories = listOf(all), selectedCategory = all, now = now).copy(
                channels = listOf(ch),
                programmes = mapOf(ch.channelId to listOf(prog)),
                previewChannelId = null,
            ),
        )
        setGuide(
            stateProvider = { state },
            onPreviewChannel = { previewedChannel = it },
            onWatchLive = { _, _ -> fullscreenTriggered = true },
        )

        // First click on programme
        composeRule.onNodeWithTag("tv-guide-programme-programme-1").performClick()
        composeRule.waitForIdle()

        // Mini preview starts
        assertEquals(ch.channelId, previewedChannel?.channelId)
        // Fullscreen NOT opened
        assertFalse(fullscreenTriggered)
        // No details popup dialog opened
        composeRule.onAllNodesWithText("Play Live").assertCountEquals(0)
        composeRule.onAllNodesWithText("Close").assertCountEquals(0)
    }

    @Test
    fun movingFocusUpdatesHeroWithoutChangingPreviewStream() {
        val now = 1_700_000_000_000L
        val all = category("all", "All Channels")
        val ch1 = channel("1", "BBC One")
        val ch2 = channel("2", "BBC Two")
        val prog1 = programme(now).copy(channelId = "1", title = "BBC One Show")
        val prog2 = programme(now).copy(programmeId = "p2", channelId = "2", title = "BBC Two Film")
        var previewCallCount = 0
        var state by mutableStateOf(
            state(categories = listOf(all), selectedCategory = all, now = now).copy(
                channels = listOf(ch1, ch2),
                programmes = mapOf("1" to listOf(prog1), "2" to listOf(prog2)),
                selectedChannelId = "1",
                selectedProgrammeId = prog1.programmeId,
                previewChannelId = "1",
            ),
        )
        setGuide(
            stateProvider = { state },
            onPreviewChannel = { previewCallCount++ },
            onProgrammeFocusedWithChannel = { channel, focused ->
                state = state.copy(selectedChannelId = channel.channelId, selectedProgrammeId = focused.programmeId)
            },
        )
        composeRule.waitForIdle()
        assertEquals(0, previewCallCount)

        // Move focus to channel 2 programme
        composeRule.onNodeWithTag("tv-guide-programme-p2").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        // Hero text updated to focused programme
        composeRule.onNodeWithTag("tv-guide-hero-title").assertTextEquals("BBC Two Film")
        composeRule.onNodeWithTag("tv-guide-hero-channel-name").assertTextEquals("BBC Two")

        // Moving focus alone did NOT change preview stream
        assertEquals(0, previewCallCount)
    }

    @Test
    fun sameChannelNavigationDoesNotReloadStream() {
        val now = 1_700_000_000_000L
        val all = category("all", "All Channels")
        val ch = channel()
        val prog1 = programme(now).copy(programmeId = "p1", title = "News at Six")
        val prog2 = programme(now).copy(programmeId = "p2", title = "Weather", startUtcMs = prog1.endUtcMs, endUtcMs = prog1.endUtcMs + 15 * 60_000L)
        var previewCallCount = 0
        var state by mutableStateOf(
            state(categories = listOf(all), selectedCategory = all, now = now).copy(
                channels = listOf(ch),
                programmes = mapOf(ch.channelId to listOf(prog1, prog2)),
                selectedChannelId = ch.channelId,
                selectedProgrammeId = prog1.programmeId,
                previewChannelId = ch.channelId,
            ),
        )
        setGuide(
            stateProvider = { state },
            onPreviewChannel = { previewCallCount++ },
            onProgrammeFocusedWithChannel = { channel, focused ->
                state = state.copy(selectedProgrammeId = focused.programmeId)
            },
        )

        composeRule.onNodeWithTag("tv-guide-programme-p2").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tv-guide-hero-title").assertTextEquals("Weather")
        assertEquals(0, previewCallCount)
    }

    @Test
    fun firstClickOnDifferentChannelSwitchesPreviewWithoutFullscreen() {
        val now = 1_700_000_000_000L
        val all = category("all", "All Channels")
        val ch1 = channel("1", "BBC One")
        val ch2 = channel("2", "BBC Two")
        val prog1 = programme(now).copy(channelId = "1")
        val prog2 = programme(now).copy(programmeId = "p2", channelId = "2")
        var previewedChannelId: String? = null
        var fullscreenTriggered = false
        var state by mutableStateOf(
            state(categories = listOf(all), selectedCategory = all, now = now).copy(
                channels = listOf(ch1, ch2),
                programmes = mapOf("1" to listOf(prog1), "2" to listOf(prog2)),
                selectedChannelId = "1",
                selectedProgrammeId = prog1.programmeId,
                previewChannelId = "1",
            ),
        )
        setGuide(
            stateProvider = { state },
            onPreviewChannel = { previewedChannelId = it?.channelId },
            onWatchLive = { _, _ -> fullscreenTriggered = true },
            onProgrammeFocusedWithChannel = { channel, focused ->
                state = state.copy(selectedChannelId = channel.channelId, selectedProgrammeId = focused.programmeId)
            },
        )

        // Click on channel 2
        composeRule.onNodeWithTag("tv-guide-programme-p2").performClick()
        composeRule.waitForIdle()

        assertEquals("2", previewedChannelId)
        assertFalse(fullscreenTriggered)
    }

    @Test
    fun secondClickOnPreviewingChannelOpensFullscreen() {
        val now = 1_700_000_000_000L
        val all = category("all", "All Channels")
        val ch = channel()
        val prog = programme(now)
        val player = FakePlayerManager()
        player.setPlaying()
        var fullscreenChannelId: String? = null
        var state by mutableStateOf(
            state(categories = listOf(all), selectedCategory = all, now = now).copy(
                channels = listOf(ch),
                programmes = mapOf(ch.channelId to listOf(prog)),
                selectedChannelId = ch.channelId,
                selectedProgrammeId = prog.programmeId,
                previewChannelId = ch.channelId,
            ),
        )
        setGuide(
            stateProvider = { state },
            playerManager = player,
            playerState = player.state.value,
            onWatchLive = { channel, _ -> fullscreenChannelId = channel.channelId },
        )

        // Second click on previewing programme
        composeRule.onNodeWithTag("tv-guide-programme-programme-1").performClick()
        composeRule.waitForIdle()

        assertEquals(ch.channelId, fullscreenChannelId)
    }

    @Test
    fun failedPreviewDoesNotActivateFullscreen() {
        val now = 1_700_000_000_000L
        val all = category("all", "All Channels")
        val ch = channel()
        val prog = programme(now)
        val player = FakePlayerManager()
        player.setFailed()
        var previewReloadCount = 0
        var fullscreenTriggered = false
        var state by mutableStateOf(
            state(categories = listOf(all), selectedCategory = all, now = now).copy(
                channels = listOf(ch),
                programmes = mapOf(ch.channelId to listOf(prog)),
                selectedChannelId = ch.channelId,
                selectedProgrammeId = prog.programmeId,
                previewChannelId = ch.channelId,
            ),
        )
        setGuide(
            stateProvider = { state },
            playerManager = player,
            playerState = player.state.value,
            onPreviewChannel = { previewReloadCount++ },
            onWatchLive = { _, _ -> fullscreenTriggered = true },
        )

        // Click when preview is failed
        composeRule.onNodeWithTag("tv-guide-programme-programme-1").performClick()
        composeRule.waitForIdle()

        assertFalse(fullscreenTriggered)
        assertEquals(1, previewReloadCount)
    }

    @Test
    fun categoryArrowsSwitchImmediatelyAndStopAtBoundaries() {
        val all = category("all", "All Channels")
        val news = category("news", "News")
        var chosen = all
        var state by mutableStateOf(state(categories = listOf(all, news), selectedCategory = all))
        setGuide(stateProvider = { state }, onCategory = { chosen = it; state = state.copy(selectedCategory = it) })

        composeRule.onNodeWithTag("tv-guide-category-previous").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithTag("tv-guide-category-next").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(news, chosen) }
        composeRule.onNodeWithTag("tv-guide-category-next").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithTag("tv-guide-category-previous").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(all, chosen) }
    }

    @Test
    fun categoryPickerSupportsSelectionBackAndFocusRestoration() {
        val all = category("all", "All Channels")
        val news = category("news", "News")
        var state by mutableStateOf(state(categories = listOf(all, news), selectedCategory = all))
        setGuide(stateProvider = { state }, onCategory = { state = state.copy(selectedCategory = it) })

        composeRule.onNodeWithTag("tv-guide-category-selector").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("tv-guide-category-option-all").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv-guide-category-selector").assertIsFocused()

        composeRule.onNodeWithTag("tv-guide-category-selector").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("tv-guide-category-option-all")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        composeRule.onNodeWithContentDescription("TV Guide category News").assertIsFocused()
    }

    @Test
    fun focusedProgrammeUpdatesHeroChannelAndArtworkFallback() {
        val now = ZonedDateTime.of(2026, 9, 26, 12, 0, 0, 0, ZoneId.of("Europe/London")).toInstant().toEpochMilli()
        val all = category("all", "All Channels")
        val channel = channel()
        val first = programme(now)
        val second = first.copy(
            programmeId = "programme-2",
            title = "Evening Film",
            description = null,
            icon = null,
            startUtcMs = first.endUtcMs,
            endUtcMs = first.endUtcMs + 60 * 60_000L,
            isLiveNow = false,
        )
        var state by mutableStateOf(
            state(categories = listOf(all), selectedCategory = all, now = now).copy(
                channels = listOf(channel),
                programmes = mapOf(channel.channelId to listOf(first, second)),
                selectedChannelId = channel.channelId,
                selectedProgrammeId = first.programmeId,
            ),
        )
        setGuide(
            stateProvider = { state },
            onProgrammeFocused = { focused ->
                state = state.copy(selectedChannelId = channel.channelId, selectedProgrammeId = focused.programmeId)
            },
        )

        composeRule.onNodeWithTag("tv-guide-programme-programme-2")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("tv-guide-hero-title").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-guide-hero-title").assertTextEquals("Evening Film")
        composeRule.onNodeWithTag("tv-guide-hero-channel-name").assertTextEquals("BBC One")
        composeRule.onNodeWithTag("tv-guide-hero-fallback").assertIsDisplayed()
        composeRule.onNodeWithText("Browse programmes on BBC One.").assertIsDisplayed()
    }

    @Test
    fun standardHeaderAndEmptyFilteredCategoryKeepRecoveryControlsVisible() {
        val all = category("all", "All Channels")
        val favourites = LiveTvCategory("favorites", "Favourites", LiveTvCategoryKind.Favorites)
        var state by mutableStateOf(
            state(categories = listOf(all, favourites), selectedCategory = favourites).copy(channels = emptyList()),
        )
        setGuide(stateProvider = { state }, onCategory = { state = state.copy(selectedCategory = it) })

        composeRule.onNodeWithTag("tv-guide-header").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-guide-title").assertTextEquals("TV GUIDE")
        composeRule.onNodeWithText("No channels in this category.").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-guide-category-selector").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv-guide-category-option-all").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("all", state.selectedCategory?.id) }
    }

    @Test
    fun unavailablePreviewFallsBackToSelectedChannelIdentity() {
        val all = category("all", "All Channels")
        val player = FakePlayerManager()
        setGuide(
            stateProvider = { state(categories = listOf(all), selectedCategory = all) },
            playerManager = player,
            playerState = player.state.value,
        )

        composeRule.onNodeWithTag("tv-guide-mini-player").assertIsDisplayed()
        composeRule.onNodeWithTag("watchio-player-fallback", useUnmergedTree = true).fetchSemanticsNode()
    }

    private fun setGuide(
        stateProvider: () -> TvGuideUiState,
        onJumpToNow: () -> Unit = {},
        onCategory: (LiveTvCategory) -> Unit = {},
        onDay: (java.time.LocalDate) -> Unit = {},
        onProgrammeFocused: (WatchioGuideProgramme) -> Unit = {},
        onProgrammeFocusedWithChannel: (WatchioGuideChannel, WatchioGuideProgramme) -> Unit = { _, programme -> onProgrammeFocused(programme) },
        onPreviewChannel: (WatchioGuideChannel?) -> Unit = {},
        onProgramme: () -> Unit = {},
        onWatchLive: (WatchioGuideChannel, WatchioGuideProgramme) -> Unit = { _, _ -> },
        onPlay: () -> Unit = {},
        onCloseDetails: () -> Unit = {},
        playerManager: WatchioPlayerManager? = null,
        playerState: WatchioPlayerState? = null,
    ) {
        composeRule.setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
            WatchioTheme {
                TvGuideScreen(
                    state = stateProvider(),
                    playerManager = playerManager,
                    playerState = playerState,
                    onJumpToNow = onJumpToNow,
                    onDay = onDay,
                    onCategory = onCategory,
                    onRefresh = {},
                    onPreviewChannel = onPreviewChannel,
                    onChannel = {},
                    onProgrammeFocused = onProgrammeFocusedWithChannel,
                    onProgramme = { _, _ -> onProgramme() },
                    onWatchLive = onWatchLive,
                    onPlayLive = onPlay,
                    onCloseDetails = onCloseDetails,
                    onBack = {},
                )
            }
        }
    }

    private fun state(
        categories: List<LiveTvCategory>,
        selectedCategory: LiveTvCategory,
        now: Long = 1_700_000_000_000L,
    ): TvGuideUiState = TvGuideUiState(
        loading = false,
        nowEpochMs = now,
        window = TvGuideTimeline.defaultWindow(now, ZoneId.of("Europe/London")),
        categories = categories,
        selectedCategory = selectedCategory,
        channels = listOf(channel()),
        hasProvider = true,
        hasEpgSource = true,
    )

    private fun category(id: String, name: String) = LiveTvCategory(id, name, LiveTvCategoryKind.All)

    private fun channel(id: String = "1", name: String = "BBC One"): WatchioGuideChannel {
        val live = LiveTvChannel(
            providerId = ProviderId("provider"), providerType = ProviderType.Xtream, id = id, name = name,
            logoUrl = null, categoryId = "all", epgChannelId = "bbc.one", extension = "ts", directUrl = null,
            headers = emptyMap(), serverOrder = 1, isFavorite = false,
        )
        return WatchioGuideChannel(
            providerId = live.providerId, channelId = live.id, displayName = live.name, logo = null,
            channelNumber = "1", category = "all", isFavourite = false, isCurrentlyPlaying = false,
            epgChannelId = live.epgChannelId, liveChannel = live,
        )
    }

    private fun programme(now: Long) = WatchioGuideProgramme(
        programmeId = "programme-1", channelId = "1", epgChannelId = "bbc.one", title = "Morning News",
        startUtcMs = now - 30 * 60_000L, endUtcMs = now + 30 * 60_000L, progress = 0.5f, isLiveNow = true,
    )

    private class FakePlayerManager : WatchioPlayerManager {
        private var metadata = WatchioPlayerMetadata()
        private val mutableState = MutableStateFlow<WatchioPlayerState>(WatchioPlayerState.Idle(metadata))
        override val state: StateFlow<WatchioPlayerState> = mutableState
        override suspend fun load(media: PlaybackMedia) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun retry() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun seekBy(deltaMs: Long) = Unit
        override fun selectAudioTrack(track: com.iamskorpz.watchioiptv.core.player.WatchioAudioTrack) = Unit
        override fun selectSubtitleTrack(track: com.iamskorpz.watchioiptv.core.player.WatchioSubtitleTrack?) = Unit
        override fun setVideoScalingMode(mode: com.iamskorpz.watchioiptv.domain.repository.VideoScalingMode) = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun restart() = Unit
        override fun snapshot(): WatchioPlayerMetadata = metadata
        override fun attachSurface(container: ViewGroup) = Unit
        override fun detachSurface(container: ViewGroup) = Unit
        override fun release() = Unit

        fun setPlaying() {
            mutableState.value = WatchioPlayerState.Playing(metadata)
        }

        fun setFailed() {
            mutableState.value = WatchioPlayerState.Failed("Playback error", metadata)
        }
    }
}
