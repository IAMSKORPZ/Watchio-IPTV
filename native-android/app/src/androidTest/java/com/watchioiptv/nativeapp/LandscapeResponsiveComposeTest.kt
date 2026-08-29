package com.watchioiptv.nativeapp

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerMetadata
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import com.watchioiptv.nativeapp.data.live.LiveTvCategory
import com.watchioiptv.nativeapp.data.live.LiveTvCategoryKind
import com.watchioiptv.nativeapp.data.live.LiveTvChannel
import com.watchioiptv.nativeapp.data.live.LiveTvNowNext
import com.watchioiptv.nativeapp.data.movies.MovieCategory
import com.watchioiptv.nativeapp.data.movies.MovieCategoryKind
import com.watchioiptv.nativeapp.data.movies.WatchioMovieItem
import com.watchioiptv.nativeapp.data.series.SeriesCategory
import com.watchioiptv.nativeapp.data.series.SeriesCategoryKind
import com.watchioiptv.nativeapp.data.series.WatchioSeriesItem
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.feature.live.LiveTvScreen
import com.watchioiptv.nativeapp.feature.live.LiveTvUiState
import com.watchioiptv.nativeapp.feature.movies.MoviesScreen
import com.watchioiptv.nativeapp.feature.movies.MoviesUiState
import com.watchioiptv.nativeapp.feature.series.SeriesScreen
import com.watchioiptv.nativeapp.feature.series.SeriesUiState
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideScreen
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideTimeline
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideUiState
import com.watchioiptv.nativeapp.data.library.SearchScope
import com.watchioiptv.nativeapp.data.library.SearchResults
import com.watchioiptv.nativeapp.data.library.WatchioSearchResult
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.feature.library.GlobalSearchScreen
import com.watchioiptv.nativeapp.feature.library.SearchUiState
import com.watchioiptv.nativeapp.feature.library.contentRoute
import com.watchioiptv.nativeapp.feature.tvguide.WatchioGuideChannel
import com.watchioiptv.nativeapp.ui.theme.WatchioTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LandscapeResponsiveComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun s22LandscapeLiveTvKeepsReadableChannelColumnAndPreview() {
        val channel = liveChannel("1", "BBC One HD Very Long Channel Name")
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    LiveTvScreen(
                        uiState = LiveTvUiState(
                            loading = false,
                            categories = listOf(liveCategory("all", "All Channels"), liveCategory("news", "News")),
                            selectedCategory = liveCategory("all", "All Channels"),
                            channels = listOf(channel, liveChannel("2", "Second Channel")),
                            selectedChannel = channel,
                            nowNext = LiveTvNowNext("Current programme", "Next programme", 0.4f),
                        ),
                        playerState = WatchioPlayerState.Idle(),
                        playerManager = FakePlayerManager(),
                        onCategory = {},
                        onChannel = {},
                        onCategorySearch = {},
                        onLiveSearch = {},
                        onFavorite = {},
                        onRetry = {},
                        onRefreshEpg = {},
                        onFullscreen = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val channelBounds = composeRule.onAllNodesWithTag("live-channel-card", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val previewBounds = composeRule.onAllNodesWithTag("live-preview", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val channelWidth = channelBounds.right - channelBounds.left
        val previewWidth = previewBounds.right - previewBounds.left
        assertTrue("channel column should stay readable; width=$channelWidth", channelWidth >= 150.dp)
        assertTrue("preview should keep useful compact width; width=$previewWidth", previewWidth >= 230.dp)
    }

    @Test
    fun s22LandscapeMoviesShowsFivePostersOnFirstRow() {
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = (1..10).map { movie("$it") },
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        assertFiveCardsOnFirstRow("movie-card", "movie-grid", "movie-poster")
    }

    @Test
    fun moviesHeaderSearchOpensOverlayAndKeepsCategorySearchSeparate() {
        val action = movie("1").copy(name = "Action Movie", posterUrl = "https://example.invalid/action.jpg")
        val drama = movie("2").copy(name = "Drama Movie", posterUrl = null)
        var movieQuery by mutableStateOf("")
        var categoryQuery by mutableStateOf("")
        var openedMovie: WatchioMovieItem? by mutableStateOf(null)
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all"), movieCategory("favorites").copy(name = "FAVOURITES"), movieCategory("history").copy(name = "HISTORY"), movieCategory("action").copy(name = "ACTION", kind = MovieCategoryKind.Provider, sourceCategoryId = "action")),
                            selectedCategory = movieCategory("all"),
                            movies = if (movieQuery.isBlank()) listOf(action, drama) else listOf(action, drama).filter { it.name.contains(movieQuery, ignoreCase = true) },
                            searchQuery = movieQuery,
                            categorySearchQuery = categoryQuery,
                        ),
                        onCategory = {},
                        onCategorySearch = { categoryQuery = it },
                        onSearch = { movieQuery = it },
                        onMovie = { openedMovie = it },
                        onBack = {},
                        initialSearchVisible = true,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("movies-search", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-search-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("movie-search-field").assertIsDisplayed()
        composeRule.onNodeWithTag("movies-title").assertIsDisplayed()
        composeRule.onNodeWithTag("movies-clock").assertIsDisplayed()
        val headerBounds = composeRule.onNodeWithTag("movies-header", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val fieldBounds = composeRule.onNodeWithTag("movie-search-field", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("movie search field must not be embedded in header", fieldBounds.top > headerBounds.bottom)

        composeRule.runOnUiThread { movieQuery = "Action" }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("movie-search-result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("movie-search-result", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        assertTrue("movie search result should open details path", openedMovie?.id == action.id)
        assertTrue(composeRule.onAllNodesWithTag("movie-search-panel").fetchSemanticsNodes().isEmpty())
        // Phase 14.2I.1: left category search field has been removed from Movies rail.
        // Header Search is the only primary movie search control.
        assertTrue(
            "left category search field must be absent from Movies rail (Phase 14.2I.1)",
            composeRule.onAllNodesWithTag("movie-category-search").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun s22LandscapeSeriesShowsFivePostersOnFirstRow() {
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    SeriesScreen(
                        state = SeriesUiState(
                            loading = false,
                            categories = listOf(seriesCategory("all")),
                            selectedCategory = seriesCategory("all"),
                            series = (1..10).map { series("$it") },
                        ),
                        onCategory = {},
                        onSearch = {},
                        onSeries = {},
                        onBack = {},
                    )
                }
            }
        }

        assertFiveCardsOnFirstRow("series-card", "series-grid", "series-poster")
    }

    @Test
    fun tvGuideShowsPerChannelNoProgrammeInformation() {
        val now = 1_700_000_000_000L
        val channel = guideChannel(liveChannel("1", "Guide Channel"))
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    TvGuideScreen(
                        state = TvGuideUiState(
                            loading = false,
                            nowEpochMs = now,
                            window = TvGuideTimeline.defaultWindow(now),
                            categories = listOf(liveCategory("all", "All Channels"), liveCategory("sports", "Sports")),
                            selectedCategory = liveCategory("all", "All Channels"),
                            channels = listOf(channel),
                            programmes = emptyMap(),
                            hasProvider = true,
                            hasEpgSource = true,
                            epgChannelCount = 1,
                            epgProgrammeCount = 0,
                        ),
                        onJumpToNow = {},
                        onDay = {},
                        onCategory = {},
                        onRefresh = {},
                        onChannel = {},
                        onProgramme = { _, _ -> },
                        onPlayLive = {},
                        onCloseDetails = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("No programme information").assertIsDisplayed()
    }

    @Test
    fun tvGuideCategorySelectorIsVisibleAndUsable() {
        val now = 1_700_000_000_000L
        val all = liveCategory("all", "All Channels")
        val sports = liveCategory("sports", "Sports")
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    TvGuideScreen(
                        state = TvGuideUiState(
                            loading = false,
                            nowEpochMs = now,
                            window = TvGuideTimeline.defaultWindow(now),
                            categories = listOf(all, sports),
                            selectedCategory = all,
                            channels = listOf(guideChannel(liveChannel("1", "Guide Channel"))),
                            programmes = emptyMap(),
                            hasProvider = true,
                            hasEpgSource = true,
                        ),
                        onJumpToNow = {},
                        onDay = {},
                        onCategory = {},
                        onRefresh = {},
                        onChannel = {},
                        onProgramme = { _, _ -> },
                        onPlayLive = {},
                        onCloseDetails = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Category: All Channels").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Sports").assertIsDisplayed()
    }

    @Test
    fun liveTvPermanentActionsAreRemoved() {
        val channel = liveChannel("1", "BBC One HD")
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    LiveTvScreen(
                        uiState = LiveTvUiState(
                            loading = false,
                            categories = listOf(liveCategory("all", "All Channels")),
                            selectedCategory = liveCategory("all", "All Channels"),
                            channels = listOf(channel),
                            selectedChannel = channel,
                            nowNext = LiveTvNowNext(null, null, 0f),
                        ),
                        playerState = WatchioPlayerState.Idle(),
                        playerManager = FakePlayerManager(),
                        onCategory = {},
                        onChannel = {},
                        onCategorySearch = {},
                        onLiveSearch = {},
                        onFavorite = {},
                        onRetry = {},
                        onRefreshEpg = {},
                        onFullscreen = {},
                        onBack = {},
                    )
                }
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Fullscreen").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Favorite").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun liveTvHeaderSearchOpensOverlayWithoutHeaderCollision() {
        val bbc = liveChannel("1", "BBC One HD")
        val sky = liveChannel("2", "Sky Cinema Action HD")
        var query by mutableStateOf("")
        var selected: LiveTvChannel? by mutableStateOf(bbc)
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    LiveTvScreen(
                        uiState = LiveTvUiState(
                            loading = false,
                            categories = listOf(liveCategory("all", "All Channels")),
                            selectedCategory = liveCategory("all", "All Channels"),
                            channels = if (query.isBlank()) listOf(bbc, sky) else listOf(bbc, sky).filter { it.name.contains(query, ignoreCase = true) },
                            selectedChannel = selected,
                            nowNext = LiveTvNowNext("Current programme", "Next programme", 0.4f),
                            liveSearchQuery = query,
                        ),
                        playerState = WatchioPlayerState.Playing(WatchioPlayerMetadata()),
                        playerManager = FakePlayerManager(),
                        onCategory = {},
                        onChannel = { selected = it },
                        onCategorySearch = {},
                        onLiveSearch = { query = it },
                        onFavorite = {},
                        onRetry = {},
                        onRefreshEpg = {},
                        onFullscreen = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("live-search").performClick()
        composeRule.onNodeWithTag("live-search-overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("live-title").assertIsDisplayed()
        composeRule.onNodeWithTag("live-clock").assertIsDisplayed()
        val headerBounds = composeRule.onNodeWithTag("live-header", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val fieldBounds = composeRule.onNodeWithTag("live-search-field", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("search field must not be embedded in header", fieldBounds.top > headerBounds.bottom)

        composeRule.runOnUiThread { query = "Sky" }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("live-search-result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("live-search-result", useUnmergedTree = true)[0].assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertTrue("search result should select channel", selected?.id == sky.id)
        assertTrue(composeRule.onAllNodesWithTag("live-search-overlay").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun liveTvSelectedChannelShowsCurrentProgrammeDetails() {
        val channel = liveChannel("1", "[SPFL] William Hill League 1")
        renderLiveTv(
            selectedChannel = channel,
            channels = listOf(channel),
            nowNext = LiveTvNowNext(
                currentTitle = "Alloa Athletic v Montrose",
                nextTitle = "Post Match Live",
                progress = 0.5f,
                currentDescription = "League 1 coverage from Alloa.",
                currentStartEpochMs = 1_704_070_800_000L,
                currentEndEpochMs = 1_704_078_000_000L,
            ),
        )

        assertTrue(composeRule.onAllNodesWithText("[SPFL] William Hill League 1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Alloa Athletic v Montrose").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("01:00 - 03:00").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("NEXT").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Post Match Live").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun liveTvCompactChannelInfoShowsOnlyChannelIdentity() {
        val channel = liveChannel("1", "Sky Cinema Action HD")
        renderLiveTv(
            selectedChannel = channel,
            channels = listOf(channel),
            nowNext = LiveTvNowNext(
                currentTitle = "Demolition Man (1993)",
                nextTitle = "Terminator 2: Judgment Day",
                progress = 0.45f,
                currentStartEpochMs = 1_704_070_800_000L,
                currentEndEpochMs = 1_704_078_000_000L,
            ),
        )

        composeRule.waitForIdle()
        val infoBounds = composeRule.onAllNodesWithTag("live-channel-info", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val channelNodes = composeRule.onAllNodesWithText("Sky Cinema Action HD", useUnmergedTree = true)
        val channelBounds = (0 until channelNodes.fetchSemanticsNodes().size)
            .map { channelNodes[it].getUnclippedBoundsInRoot() }
            .first { it.left >= infoBounds.left && it.right <= infoBounds.right && it.top >= infoBounds.top && it.bottom <= infoBounds.bottom }
        assertTrue("channel name should be inside compact info card", channelBounds.left >= infoBounds.left)
        assertTrue("channel name should be inside compact info card", channelBounds.right <= infoBounds.right)
        assertTrue("channel name should be inside compact info card", channelBounds.top >= infoBounds.top)
        assertTrue("channel name should be inside compact info card", channelBounds.bottom <= infoBounds.bottom)

        val programmeBounds = composeRule.onNodeWithText("Demolition Man (1993)").getUnclippedBoundsInRoot()
        val timeBounds = composeRule.onNodeWithText("01:00 - 03:00").getUnclippedBoundsInRoot()
        val nextBounds = composeRule.onNodeWithText("Terminator 2: Judgment Day").getUnclippedBoundsInRoot()
        assertTrue("programme title should belong to lower EPG panel", programmeBounds.top > infoBounds.bottom)
        assertTrue("programme time should belong to lower EPG panel", timeBounds.top > infoBounds.bottom)
        assertTrue("next programme should belong to lower EPG panel", nextBounds.top > infoBounds.bottom)
    }

    @Test
    fun liveTvPreviewIsAboveEpgAndBesideChannelInfo() {
        val channel = liveChannel("1", "[SPFL] William Hill League 1")
        renderLiveTv(
            selectedChannel = channel,
            channels = listOf(channel),
            nowNext = LiveTvNowNext("Alloa Athletic v Montrose", "Post Match Live", 0.5f),
        )

        composeRule.waitForIdle()
        val previewBounds = composeRule.onAllNodesWithTag("live-preview", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val infoBounds = composeRule.onAllNodesWithTag("live-channel-info", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val epgBounds = composeRule.onAllNodesWithTag("live-epg-panel", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()

        assertTrue("preview should sit above EPG", previewBounds.top < epgBounds.top)
        assertTrue("channel info should sit above EPG", infoBounds.top < epgBounds.top)
        assertTrue("preview and channel info should share top row", kotlin.math.abs((previewBounds.top - infoBounds.top).value) < 4f)
        assertTrue("preview and channel info should align bottom", kotlin.math.abs((previewBounds.bottom - infoBounds.bottom).value) < 4f)
        assertTrue("preview should be left of compact channel info", previewBounds.right <= infoBounds.left + 2.dp)
        assertTrue("preview should be wider than compact info", previewBounds.right - previewBounds.left > infoBounds.right - infoBounds.left)
        assertTrue("channel info should get meaningful compact width", infoBounds.right - infoBounds.left >= 110.dp)
    }

    @Test
    fun liveTvBufferingMessageStaysInsidePreviewSurface() {
        val channel = liveChannel("1", "BBC One HD")
        renderLiveTv(
            selectedChannel = channel,
            channels = listOf(channel),
            nowNext = LiveTvNowNext("Current programme", "Next programme", 0.4f),
            playerState = WatchioPlayerState.Buffering(WatchioPlayerMetadata()),
        )

        composeRule.waitForIdle()
        val previewBounds = composeRule.onAllNodesWithTag("live-preview", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val bufferingBounds = composeRule.onNodeWithText("Buffering...").getUnclippedBoundsInRoot()

        assertTrue(bufferingBounds.left >= previewBounds.left)
        assertTrue(bufferingBounds.right <= previewBounds.right)
        assertTrue(bufferingBounds.top >= previewBounds.top)
        assertTrue(bufferingBounds.bottom <= previewBounds.bottom)
    }

    @Test
    fun liveTvSelectedChannelWithoutEpgShowsExplicitFallback() {
        val channel = liveChannel("1", "BBC One HD")
        renderLiveTv(
            selectedChannel = channel,
            channels = listOf(channel),
            nowNext = LiveTvNowNext(null, null, 0f),
        )

        assertTrue(composeRule.onAllNodesWithText("BBC One HD").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("No EPG Information Available").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh EPG").assertIsDisplayed()
    }

    @Test
    fun liveTvNoSelectedChannelShowsNoStaleProgramme() {
        val channel = liveChannel("1", "BBC One HD")
        renderLiveTv(
            selectedChannel = null,
            channels = listOf(channel),
            nowNext = LiveTvNowNext("Old programme", "Old next", 0.5f),
        )

        composeRule.onNodeWithText("No channel selected").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("No programme selected").fetchSemanticsNodes().isNotEmpty())
    }

    private fun renderLiveTv(
        selectedChannel: LiveTvChannel?,
        channels: List<LiveTvChannel>,
        nowNext: LiveTvNowNext,
        playerState: WatchioPlayerState = WatchioPlayerState.Idle(),
    ) {
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    LiveTvScreen(
                        uiState = LiveTvUiState(
                            loading = false,
                            categories = listOf(liveCategory("all", "All Channels")),
                            selectedCategory = liveCategory("all", "All Channels"),
                            channels = channels,
                            selectedChannel = selectedChannel,
                            nowNext = nowNext,
                        ),
                        playerState = playerState,
                        playerManager = FakePlayerManager(),
                        onCategory = {},
                        onChannel = {},
                        onCategorySearch = {},
                        onLiveSearch = {},
                        onFavorite = {},
                        onRetry = {},
                        onRefreshEpg = {},
                        onFullscreen = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }


    // --------------------------------------------------------------------------
    // Phase 14.2I.1 — Movies Polish tests
    // --------------------------------------------------------------------------

    @Test
    fun moviesMoreButtonIsCompactNotText() {
        // The More control must be a compact three-dot button, not a wide text "More" card.
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // The More button must exist with its test tag.
        composeRule.onNodeWithTag("movies-more", useUnmergedTree = true).assertIsDisplayed()
        // The More button must NOT display the word "More" as text (it should be a ⋮ icon).
        assertTrue(
            "More button must not display text 'More' — it should be a compact icon",
            composeRule.onAllNodesWithText("More").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun moviesRailShowsSystemCategoriesFirst() {
        // ALL MOVIES, FAVOURITES, HISTORY must appear at the top of the category rail
        // before any provider categories.
        val allMovies = MovieCategory("all", "ALL MOVIES", MovieCategoryKind.All)
        val favourites = MovieCategory("favorites", "FAVOURITES", MovieCategoryKind.Favorites)
        val history = MovieCategory("history", "HISTORY", MovieCategoryKind.History)
        val provider1 = MovieCategory("p1", "ACTION", MovieCategoryKind.Provider, "p1")
        val provider2 = MovieCategory("p2", "DRAMA", MovieCategoryKind.Provider, "p2")
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(allMovies, favourites, history, provider1, provider2),
                            selectedCategory = allMovies,
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-category-all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-favorites", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-history", useUnmergedTree = true).assertIsDisplayed()
        // Verify ordering: ALL MOVIES top, then FAVOURITES, then HISTORY, then provider.
        val allBounds = composeRule.onNodeWithTag("movie-category-all", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val favBounds = composeRule.onNodeWithTag("movie-category-favorites", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val histBounds = composeRule.onNodeWithTag("movie-category-history", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("ALL MOVIES must be above FAVOURITES", allBounds.top < favBounds.top)
        assertTrue("FAVOURITES must be above HISTORY", favBounds.top < histBounds.top)
    }

    @Test
    fun moviesRailHasNoLeftCategorySearchField() {
        // The visible category search field must be absent from the Movies rail.
        // Header Search is the only primary movie search control (Phase 14.2I.1).
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertTrue(
            "movie-category-search must not be present in Movies rail",
            composeRule.onAllNodesWithTag("movie-category-search").fetchSemanticsNodes().isEmpty(),
        )
        // Left rail should not contain a "Search categories" label either.
        assertTrue(
            "Search categories text must not appear in Movies rail",
            composeRule.onAllNodesWithText("Search categories").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun moviesRatingZeroIsHidden() {
        // A raw rating of "0" must not appear on a movie card.
        val zeroRatedMovie = movie("zero").copy(name = "Zero Rated Movie", rating = "0")
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = listOf(zeroRatedMovie),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // The zero rating must not appear as text.
        assertTrue(
            "rating '0' must not be displayed on movie card",
            composeRule.onAllNodesWithText("0").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "rating '★ 0.0' must not be displayed on movie card",
            composeRule.onAllNodesWithText("★ 0.0").fetchSemanticsNodes().isEmpty(),
        )
        // No movie-rating tag should exist for a zero-rated movie.
        assertTrue(
            "movie-rating tag must be absent for zero-rated movie",
            composeRule.onAllNodesWithTag("movie-rating").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun moviesRatingIsFormattedWithStarAndOneDecimal() {
        // Raw rating "6.458" must display as "★ 6.5".
        val ratedMovie = movie("rated").copy(name = "Rated Movie", rating = "6.458")
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = listOf(ratedMovie),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-rating", useUnmergedTree = true).assertIsDisplayed()
        // Raw value must not appear.
        assertTrue(
            "raw rating '6.458' must not be displayed",
            composeRule.onAllNodesWithText("6.458").fetchSemanticsNodes().isEmpty(),
        )
        // Formatted value must appear.
        composeRule.onNodeWithText("★ 6.5").assertIsDisplayed()
    }

    @Test
    fun moviesTitleRegionHasConsistentHeight() {
        // All movie cards must reserve the same vertical height for the title region
        // so poster rows align cleanly.
        //
        // We render exactly 5 cards — one full first row on S22 landscape (5 columns).
        // LazyVerticalGrid only composes visible items, so a 6th card on a second row
        // below the viewport would not be in the semantic tree. Using 5 cards keeps the
        // assertion reliable across S22 and larger devices (which show >= 5 cards on row 1).
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = listOf(
                                movie("1").copy(name = "A"),
                                movie("2").copy(name = "A Very Long Movie Title That Might Wrap Onto Two Lines"),
                                movie("3").copy(name = "Short"),
                                movie("4").copy(name = "Another Long Title: The Sequel"),
                                movie("5").copy(name = "Panda Plan: The Magical Adventure Returns Again"),
                            ),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val titleNodes = composeRule.onAllNodesWithTag("movie-title-region", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected at least 5 title regions (one full row); found ${titleNodes.size}", titleNodes.size >= 5)
        // Check only the first 5 — they share the same visible row.
        val firstRowNodes = titleNodes.take(5)
        val heights = firstRowNodes.map { it.boundsInRoot.bottom - it.boundsInRoot.top }
        val maxHeight = heights.max()
        val minHeight = heights.min()
        assertTrue(
            "all title regions on first row must have consistent height (max=$maxHeight, min=$minHeight)",
            maxHeight - minHeight < 4f,
        )
    }

    @Test
    fun moviesSearchOverlayOpensAndClosesWithBack() {
        var searchVisible = false
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = listOf(movie("1")),
                            searchQuery = "",
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                        initialSearchVisible = false,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Overlay should not be visible initially.
        assertTrue(
            "search overlay must not be visible before Search is clicked",
            composeRule.onAllNodesWithTag("movie-search-overlay").fetchSemanticsNodes().isEmpty(),
        )
        // Click header Search to open overlay.
        composeRule.onNodeWithTag("movies-search", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-search-overlay").assertIsDisplayed()
        // Click Close button to dismiss.
        composeRule.onNodeWithTag("movie-search-close").performClick()
        composeRule.waitForIdle()
        assertTrue(
            "search overlay must be dismissed after Close",
            composeRule.onAllNodesWithTag("movie-search-overlay").fetchSemanticsNodes().isEmpty(),
        )
    }

    // --------------------------------------------------------------------------
    // End of Phase 14.2I.1 tests
    // --------------------------------------------------------------------------

    // --------------------------------------------------------------------------
    // Phase 14.2I.2 — Movies Category Height + Overflow Text tests
    // --------------------------------------------------------------------------

    @Test
    fun moviesCategoryCardHeightIsReduced() {
        // Phase 14.2I.2: Category cards must be shorter (<= 60dp, around 54dp)
        // instead of the previous oversized 92dp+ height.
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val cardBounds = composeRule.onNodeWithTag("movie-category-all", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardHeight = cardBounds.bottom - cardBounds.top
        assertTrue("Category card height must be <= 60.dp; measured=${cardHeight}", cardHeight <= 60.dp)
        assertTrue("Category card height must be >= 44.dp for touch/focus target; measured=${cardHeight}", cardHeight >= 44.dp)
    }

    @Test
    fun moviesMultipleCategoryRowsFitOnS22Landscape() {
        // Phase 14.2I.2: Multiple category cards (at least 4) must be visible
        // simultaneously in the category rail on S22 landscape.
        val all = MovieCategory("all", "ALL MOVIES", MovieCategoryKind.All)
        val fav = MovieCategory("favorites", "FAVOURITES", MovieCategoryKind.Favorites)
        val hist = MovieCategory("history", "HISTORY", MovieCategoryKind.History)
        val act = MovieCategory("action", "ACTION", MovieCategoryKind.Provider, "action")
        val com = MovieCategory("comedy", "COMEDY", MovieCategoryKind.Provider, "comedy")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(all, fav, hist, act, com),
                            selectedCategory = all,
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-category-all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-favorites", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-history", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-action", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun moviesCategorySelectionAndDpadNavigationWorks() {
        // Phase 14.2I.2: Category selection must continue to trigger onCategory.
        val all = MovieCategory("all", "ALL MOVIES", MovieCategoryKind.All)
        val fav = MovieCategory("favorites", "FAVOURITES", MovieCategoryKind.Favorites)
        var selectedCategory: MovieCategory? = null

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(all, fav),
                            selectedCategory = all,
                            movies = emptyList(),
                        ),
                        onCategory = { selectedCategory = it },
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-category-favorites", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue("Clicking category must invoke onCategory with selected item", selectedCategory?.id == fav.id)
    }

    @Test
    fun moviesShortAndLongCategoryTextRenderCleanly() {
        // Phase 14.2I.2: Short category text (ACTION) and long category text
        // (JUST RELEASED HOLLYWOOD 4K ULTRA HD MOVIES) must both render cleanly without crash.
        val shortCat = MovieCategory("action", "ACTION", MovieCategoryKind.Provider, "action")
        val longCat = MovieCategory("long", "JUST RELEASED HOLLYWOOD 4K ULTRA HD MOVIES", MovieCategoryKind.Provider, "long")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(shortCat, longCat),
                            selectedCategory = shortCat,
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-category-text-action", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-text-long", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun moviesHeaderAndGridRemainIntactInPhase14_2I_2() {
        // Phase 14.2I.2: Header, Search, More, and Movie Grid must remain completely intact.
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = listOf(movie("1"), movie("2")),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movies-header", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movies-title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movies-search", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movies-more", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-grid", useUnmergedTree = true).assertIsDisplayed()
    }

    // --------------------------------------------------------------------------
    // End of Phase 14.2I.2 tests
    // --------------------------------------------------------------------------

    // --------------------------------------------------------------------------
    // Phase 14.2I.3 — Movies Search Icon + Poster Card Visual Polish tests
    // --------------------------------------------------------------------------

    @Test
    fun moviesSearchButtonIsIconNotText() {
        // Phase 14.2I.3: Search in header must be an icon button matching More button (44x44dp),
        // with no visible text "Search" in normal header.
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Search button must exist with its test tag.
        composeRule.onNodeWithTag("movies-search", useUnmergedTree = true).assertIsDisplayed()
        // Search button must NOT display text "Search".
        assertTrue(
            "Search button must not display text 'Search' in header — must be an icon",
            composeRule.onAllNodesWithText("Search").fetchSemanticsNodes().isEmpty(),
        )
        // Search button and More button should match in size (44x44dp).
        val searchBounds = composeRule.onNodeWithTag("movies-search", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val moreBounds = composeRule.onNodeWithTag("movies-more", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val searchWidth = searchBounds.right - searchBounds.left
        val searchHeight = searchBounds.bottom - searchBounds.top
        val moreWidth = moreBounds.right - moreBounds.left
        val moreHeight = moreBounds.bottom - moreBounds.top
        assertTrue("Search and More buttons should have matching dimensions", (searchWidth - moreWidth).value < 2f && (searchHeight - moreHeight).value < 2f)

        // Click search icon opens search overlay.
        composeRule.onNodeWithTag("movies-search", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-search-overlay").assertIsDisplayed()
    }

    @Test
    fun moviesRatingBadgeIsOnPosterAndZeroIsHidden() {
        // Phase 14.2I.3: Rating must render as a badge on the poster, not a separate line below title.
        val ratedMovie = movie("rated").copy(name = "Rated Movie", rating = "7.7")
        val zeroMovie = movie("zero").copy(name = "Zero Movie", rating = "0")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = listOf(ratedMovie, zeroMovie),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Rated movie must show "★ 7.7" badge on poster.
        composeRule.onNodeWithText("★ 7.7").assertIsDisplayed()
        // Exactly one rating badge rendered for the two movies (zero rating is hidden).
        val ratingBadges = composeRule.onAllNodesWithTag("movie-rating-badge", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expected exactly 1 rating badge for rated movie; found ${ratingBadges.size}", ratingBadges.size == 1)
        // Rating badge must be located inside the poster area (above the title region).
        val badgeBounds = composeRule.onNodeWithTag("movie-rating-badge", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds = composeRule.onAllNodesWithTag("movie-title-region", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertTrue("Rating badge must be above the title region (on the poster)", badgeBounds.bottom <= titleBounds.top + 2.dp)
    }

    @Test
    fun moviesNoDuplicateRatingBelowTitle() {
        // Phase 14.2I.3: Ensure there is only 1 rating element per rated card and no separate rating text row below title.
        val ratedMovie = movie("rated").copy(name = "Rated Movie", rating = "6.458")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = listOf(ratedMovie),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val ratingNodes = composeRule.onAllNodesWithTag("movie-rating", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expected exactly 1 rating element; found ${ratingNodes.size}", ratingNodes.size == 1)
        composeRule.onNodeWithText("★ 6.5").assertIsDisplayed()
    }

    // --------------------------------------------------------------------------
    // End of Phase 14.2I.3 tests
    // --------------------------------------------------------------------------

    // --------------------------------------------------------------------------
    // Phase 14.2I.4 — Movies Category Card Compact + Text Centering tests
    // --------------------------------------------------------------------------

    @Test
    fun moviesCategoryCardHeightIsCompact48to52Dp() {
        // Phase 14.2I.4: Category cards must render at compact 48dp–52dp height.
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val cardBounds = composeRule.onNodeWithTag("movie-category-all", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardHeight = cardBounds.bottom - cardBounds.top
        assertTrue("Category card height must be between 47.dp and 52.dp; measured=${cardHeight}", cardHeight in 47.dp..52.dp)
    }

    @Test
    fun moviesCategoryTextIsVerticallyCentered() {
        // Phase 14.2I.4: Category text must be vertically centered inside the card
        // and horizontally aligned to the left.
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(movieCategory("all")),
                            selectedCategory = movieCategory("all"),
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val cardBounds = composeRule.onNodeWithTag("movie-category-all", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val textBounds = composeRule.onNodeWithTag("movie-category-text-all", useUnmergedTree = true).getUnclippedBoundsInRoot()

        val topGap = (textBounds.top - cardBounds.top).value
        val bottomGap = (cardBounds.bottom - textBounds.bottom).value

        // Top gap and bottom gap must be approximately equal (centered vertically within 4dp tolerance).
        val diff = kotlin.math.abs(topGap - bottomGap)
        assertTrue("Text must be vertically centered; topGap=$topGap, bottomGap=$bottomGap, diff=$diff", diff <= 4f)

        // Text must be left-aligned (left gap is around 12dp padding).
        val leftGap = (textBounds.left - cardBounds.left).value
        assertTrue("Text must be left-aligned; leftGap=$leftGap", leftGap in 8f..18f)
    }

    @Test
    fun moviesMultipleCategoryRowsFitOnS22LandscapePhase14_2I_4() {
        // Phase 14.2I.4: At 48dp card height + 6dp gap, at least 5 category rows
        // must be simultaneously visible in the rail on S22 landscape.
        val all = MovieCategory("all", "ALL MOVIES", MovieCategoryKind.All)
        val fav = MovieCategory("favorites", "FAVOURITES", MovieCategoryKind.Favorites)
        val hist = MovieCategory("history", "HISTORY", MovieCategoryKind.History)
        val act = MovieCategory("action", "ACTION", MovieCategoryKind.Provider, "action")
        val com = MovieCategory("comedy", "COMEDY", MovieCategoryKind.Provider, "comedy")
        val drm = MovieCategory("drama", "DRAMA", MovieCategoryKind.Provider, "drama")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(all, fav, hist, act, com, drm),
                            selectedCategory = all,
                            movies = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onMovie = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-category-all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-favorites", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-history", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-action", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("movie-category-comedy", useUnmergedTree = true).assertIsDisplayed()
    }

    // --------------------------------------------------------------------------
    // End of Phase 14.2I.4 tests
    // --------------------------------------------------------------------------

    // --------------------------------------------------------------------------
    // Phase 14.2J — Series Tab Redesign to match Movies tests
    // --------------------------------------------------------------------------

    @Test
    fun seriesScreenUsesSharedHeaderAndCorrectTitle() {
        // Phase 14.2J: Series must use shared Watchio header with title "SERIES",
        // compact search icon button (44x44dp), and More button (44x44dp).
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    SeriesScreen(
                        state = SeriesUiState(
                            loading = false,
                            categories = listOf(seriesCategory("all")),
                            selectedCategory = seriesCategory("all"),
                            series = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onSeries = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series-header", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("series-title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("SERIES").assertIsDisplayed()
        composeRule.onNodeWithTag("series-search", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("series-more", useUnmergedTree = true).assertIsDisplayed()

        // Header must NOT display text "Search" or "More" as buttons.
        assertTrue(
            "Header must not display text 'Search' — must be an icon",
            composeRule.onAllNodesWithText("Search").fetchSemanticsNodes().isEmpty(),
        )

        val searchBounds = composeRule.onNodeWithTag("series-search", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val moreBounds = composeRule.onNodeWithTag("series-more", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val searchWidth = searchBounds.right - searchBounds.left
        val searchHeight = searchBounds.bottom - searchBounds.top
        val moreWidth = moreBounds.right - moreBounds.left
        val moreHeight = moreBounds.bottom - moreBounds.top
        assertTrue("Search and More buttons should have matching dimensions", (searchWidth - moreWidth).value < 2f && (searchHeight - moreHeight).value < 2f)
    }

    @Test
    fun seriesCategoryRailHasCompactHeightAndSystemCategoriesFirst() {
        // Phase 14.2J: Category cards must render at compact 48dp–52dp height with vertically centered text.
        val all = SeriesCategory("all", "ALL SERIES", SeriesCategoryKind.All)
        val fav = SeriesCategory("favorites", "FAVOURITES", SeriesCategoryKind.Favorites)
        val hist = SeriesCategory("history", "HISTORY", SeriesCategoryKind.History)
        val act = SeriesCategory("action", "ACTION", SeriesCategoryKind.Provider, "action")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    SeriesScreen(
                        state = SeriesUiState(
                            loading = false,
                            categories = listOf(all, fav, hist, act),
                            selectedCategory = all,
                            series = emptyList(),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onSeries = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series-category-all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("series-category-favorites", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("series-category-history", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("series-category-action", useUnmergedTree = true).assertIsDisplayed()

        val cardBounds = composeRule.onNodeWithTag("series-category-all", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardHeight = cardBounds.bottom - cardBounds.top
        assertTrue("Series category card height must be in 47.dp..52.dp; measured=${cardHeight}", cardHeight in 47.dp..52.dp)

        val textBounds = composeRule.onNodeWithTag("series-category-text-all", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val topGap = (textBounds.top - cardBounds.top).value
        val bottomGap = (cardBounds.bottom - textBounds.bottom).value
        val diff = kotlin.math.abs(topGap - bottomGap)
        assertTrue("Category text must be vertically centered; diff=${diff}", diff <= 4f)
    }

    @Test
    fun seriesGridPosterCardAspectAndRatingBadge() {
        // Phase 14.2J: Series card must have 2:3 aspect poster, top-right rating badge, fixed 52dp title region.
        val ratedSeries = series("1").copy(name = "Breaking Bad", rating = "8.234")
        val zeroSeries = series("2").copy(name = "Unrated Show", rating = "0")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    SeriesScreen(
                        state = SeriesUiState(
                            loading = false,
                            categories = listOf(seriesCategory("all")),
                            selectedCategory = seriesCategory("all"),
                            series = listOf(ratedSeries, zeroSeries),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onSeries = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Rated series shows "★ 8.2" badge on poster.
        composeRule.onNodeWithText("★ 8.2").assertIsDisplayed()

        // Zero rated series shows no rating badge.
        val badges = composeRule.onAllNodesWithTag("series-rating-badge", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expected exactly 1 rating badge for rated series; found ${badges.size}", badges.size == 1)

        // Rating badge must be located above the title region.
        val badgeBounds = composeRule.onNodeWithTag("series-rating-badge", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds = composeRule.onAllNodesWithTag("series-title-region", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertTrue("Rating badge must be above title region", badgeBounds.bottom <= titleBounds.top + 2.dp)
    }

    @Test
    fun seriesTapOpensDetailsAndLongPressOpensOptions() {
        var openedSeries: WatchioSeriesItem? = null
        val target = series("target").copy(name = "Target Series")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    SeriesScreen(
                        state = SeriesUiState(
                            loading = false,
                            categories = listOf(seriesCategory("all")),
                            selectedCategory = seriesCategory("all"),
                            series = listOf(target),
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = {},
                        onSeries = { openedSeries = it },
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Click opens details
        composeRule.onNodeWithTag("series-card", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue("Click should trigger onSeries callback", openedSeries?.id == target.id)

        // Long press opens options dialog
        composeRule.onNodeWithTag("series-card", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Series Options").assertIsDisplayed()
        composeRule.onNodeWithText("View Details").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun seriesSearchOverlayOpensAndResultsWork() {
        var openedSeries: WatchioSeriesItem? by mutableStateOf(null)
        var searchQuery by mutableStateOf("")
        val target = series("found").copy(name = "Found Series")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    SeriesScreen(
                        state = SeriesUiState(
                            loading = false,
                            categories = listOf(seriesCategory("all")),
                            selectedCategory = seriesCategory("all"),
                            series = if (searchQuery.isNotBlank()) listOf(target) else emptyList(),
                            searchQuery = searchQuery,
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = { searchQuery = it },
                        onSeries = { openedSeries = it },
                        onBack = {},
                        initialSearchVisible = true,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series-search-overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("series-search-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("series-search-field").assertIsDisplayed()

        composeRule.runOnUiThread { searchQuery = "Found" }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("series-search-result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("series-search-result", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        assertTrue("Selecting search result opens details", openedSeries?.id == target.id)
    }

    // --------------------------------------------------------------------------
    // End of Phase 14.2J tests
    // --------------------------------------------------------------------------

    // --------------------------------------------------------------------------
    // Phase 14.2H.6 — Live TV Header Search + More Icon Consistency tests
    // --------------------------------------------------------------------------

    @Test
    fun liveTvHeaderSearchAndMoreAreCompactIconsMatchingMoviesAndSeries() {
        var searchOpened = false
        var moreTriggered = false
        val channel = liveChannel("1", "BBC One HD")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    LiveTvScreen(
                        uiState = LiveTvUiState(
                            loading = false,
                            categories = listOf(liveCategory("all", "All Channels")),
                            selectedCategory = liveCategory("all", "All Channels"),
                            channels = listOf(channel),
                            selectedChannel = channel,
                            nowNext = LiveTvNowNext(null, null, 0f),
                        ),
                        playerState = WatchioPlayerState.Idle(),
                        playerManager = FakePlayerManager(),
                        onCategory = {},
                        onChannel = {},
                        onCategorySearch = {},
                        onLiveSearch = { searchOpened = true },
                        onFavorite = {},
                        onRetry = {},
                        onRefreshEpg = { moreTriggered = true },
                        onFullscreen = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live-header", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("live-title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("live-search", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("live-more", useUnmergedTree = true).assertIsDisplayed()

        // Visible text "Search" or "More" as buttons must NOT exist in the normal header.
        assertTrue(
            "Live TV header must not render text button 'Search'",
            composeRule.onAllNodesWithText("Search").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "Live TV header must not render text button 'More'",
            composeRule.onAllNodesWithText("More").fetchSemanticsNodes().isEmpty(),
        )

        // Verify 44x44dp square icon button dimensions matching Movies and Series.
        val searchBounds = composeRule.onNodeWithTag("live-search", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val moreBounds = composeRule.onNodeWithTag("live-more", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val searchWidth = searchBounds.right - searchBounds.left
        val searchHeight = searchBounds.bottom - searchBounds.top
        val moreWidth = moreBounds.right - moreBounds.left
        val moreHeight = moreBounds.bottom - moreBounds.top

        assertTrue("Search icon button must be 44dp size; measured=${searchWidth}x${searchHeight}", searchWidth in 43.dp..45.dp && searchHeight in 43.dp..45.dp)
        assertTrue("More icon button must be 44dp size; measured=${moreWidth}x${moreHeight}", moreWidth in 43.dp..45.dp && moreHeight in 43.dp..45.dp)

        // Verify click behaviors: Search opens search overlay, More triggers EPG refresh
        composeRule.onNodeWithTag("live-search", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live-search-overlay").assertIsDisplayed()

        composeRule.onNodeWithTag("live-search-close", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("live-more", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue("Clicking More icon triggers onRefreshEpg", moreTriggered)
    }

    // --------------------------------------------------------------------------
    // End of Phase 14.2H.6 tests
    // --------------------------------------------------------------------------

    // --------------------------------------------------------------------------
    // Phase 14.2K — Unified Search Architecture tests
    // --------------------------------------------------------------------------

    @Test
    fun liveTvSearchSearchesEntireCatalogRegardlessOfSelectedCategory() {
        val sportsChannel = liveChannel("ch1", "Sky Sports 1").copy(categoryId = "sports")
        val newsChannel = liveChannel("ch2", "BBC News HD").copy(categoryId = "news")
        val sportsCategory = liveCategory("sports", "Sports")
        val newsCategory = liveCategory("news", "News")
        var selectedChannel: LiveTvChannel? = null
        var searchQuery by mutableStateOf("")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    LiveTvScreen(
                        uiState = LiveTvUiState(
                            loading = false,
                            categories = listOf(liveCategory("all", "All Channels"), sportsCategory, newsCategory),
                            selectedCategory = sportsCategory,
                            channels = if (searchQuery.isBlank()) listOf(sportsChannel) else listOf(sportsChannel, newsChannel).filter { it.name.contains(searchQuery, ignoreCase = true) },
                            selectedChannel = sportsChannel,
                            nowNext = LiveTvNowNext(null, null, 0f),
                            liveSearchQuery = searchQuery,
                        ),
                        playerState = WatchioPlayerState.Idle(),
                        playerManager = FakePlayerManager(),
                        onCategory = {},
                        onChannel = { selectedChannel = it },
                        onCategorySearch = {},
                        onLiveSearch = { searchQuery = it },
                        onFavorite = {},
                        onRetry = {},
                        onRefreshEpg = {},
                        onFullscreen = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Open search from Live TV header
        composeRule.onNodeWithTag("live-search", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live-search-overlay").assertIsDisplayed()

        // Search for "BBC News" which belongs to News, not Sports
        composeRule.runOnUiThread { searchQuery = "BBC" }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("live-search-result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // News channel must be found even though Sports was the active category
        assertTrue(composeRule.onAllNodesWithText("BBC News HD", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onAllNodesWithTag("live-search-result", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()

        assertTrue("Selecting channel from search selects it", selectedChannel?.id == newsChannel.id)
    }

    @Test
    fun moviesSearchSearchesEntireCatalogRegardlessOfSelectedCategory() {
        val actionMovie = movie("1").copy(name = "Die Hard", categoryId = "action")
        val dramaMovie = movie("2").copy(name = "Titanic", categoryId = "drama")
        val actionCategory = MovieCategory("action", "ACTION", MovieCategoryKind.Provider, "action")
        val allCategory = MovieCategory("all", "ALL MOVIES", MovieCategoryKind.All, "all")
        var openedMovie: WatchioMovieItem? = null
        var searchQuery by mutableStateOf("")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    MoviesScreen(
                        state = MoviesUiState(
                            loading = false,
                            categories = listOf(allCategory, actionCategory),
                            selectedCategory = actionCategory,
                            movies = if (searchQuery.isBlank()) listOf(actionMovie) else listOf(actionMovie, dramaMovie).filter { it.name.contains(searchQuery, ignoreCase = true) },
                            searchQuery = searchQuery,
                        ),
                        onCategory = {},
                        onCategorySearch = {},
                        onSearch = { searchQuery = it },
                        onMovie = { openedMovie = it },
                        onBack = {},
                        initialSearchVisible = true,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movie-search-overlay").assertIsDisplayed()

        // Search for "Titanic" which is Drama, while Action was selected
        composeRule.runOnUiThread { searchQuery = "Titanic" }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("movie-search-result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(composeRule.onAllNodesWithText("Titanic", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onAllNodesWithTag("movie-search-result", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()

        assertTrue("Selecting movie opens details", openedMovie?.id == dramaMovie.id)
    }

    @Test
    fun seriesSearchSearchesEntireCatalogRegardlessOfSelectedCategory() {
        val dramaSeries = series("1").copy(name = "Breaking Bad", categoryId = "drama")
        val comedySeries = series("2").copy(name = "Ted Lasso", categoryId = "comedy")
        val dramaCategory = SeriesCategory("drama", "DRAMA", SeriesCategoryKind.Provider, "drama")
        val allCategory = SeriesCategory("all", "ALL SERIES", SeriesCategoryKind.All, "all")
        var openedSeries: WatchioSeriesItem? = null
        var searchQuery by mutableStateOf("")

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    SeriesScreen(
                        state = SeriesUiState(
                            loading = false,
                            categories = listOf(allCategory, dramaCategory),
                            selectedCategory = dramaCategory,
                            series = if (searchQuery.isBlank()) listOf(dramaSeries) else listOf(dramaSeries, comedySeries).filter { it.name.contains(searchQuery, ignoreCase = true) },
                            searchQuery = searchQuery,
                        ),
                        onCategory = {},
                        onSearch = { searchQuery = it },
                        onSeries = { openedSeries = it },
                        onBack = {},
                        initialSearchVisible = true,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series-search-overlay").assertIsDisplayed()

        // Search for "Ted Lasso" which is Comedy, while Drama was selected
        composeRule.runOnUiThread { searchQuery = "Ted" }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("series-search-result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(composeRule.onAllNodesWithText("Ted Lasso", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onAllNodesWithTag("series-search-result", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()

        assertTrue("Selecting series opens details", openedSeries?.id == comedySeries.id)
    }

    @Test
    fun globalSearchGroupsResultsByLiveMoviesAndSeries() {
        val liveResult = WatchioSearchResult(ProviderId("p1"), ContentType.Live, "ch1", "BBC One HD", "Entertainment", null)
        val movieResult = WatchioSearchResult(ProviderId("p1"), ContentType.Movie, "m1", "Batman Begins", "Action", null)
        val seriesResult = WatchioSearchResult(ProviderId("p1"), ContentType.Series, "s1", "Better Call Saul", "Drama", null)
        var selectedResult: WatchioSearchResult? = null
        var currentScope by mutableStateOf(SearchScope.Global)

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    GlobalSearchScreen(
                        state = SearchUiState(
                            query = "B",
                            scope = currentScope,
                            loading = false,
                            results = SearchResults(
                                live = listOf(liveResult),
                                movies = listOf(movieResult),
                                series = listOf(seriesResult),
                            ),
                        ),
                        onQuery = {},
                        onScope = { currentScope = it },
                        onResult = { selectedResult = it },
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global-search-overlay", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-panel", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-field", useUnmergedTree = true).assertIsDisplayed()

        // Verify live group and result
        composeRule.onNodeWithTag("global-search-group-live", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("BBC One HD", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        // Click live result in Global scope
        composeRule.onAllNodesWithText("BBC One HD", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        assertTrue("Selecting result delivers clicked item", selectedResult?.contentId == "ch1")

        // Test scope switching
        composeRule.onNodeWithTag("global-search-scope-movies").performClick()
        composeRule.waitForIdle()
        assertTrue("Scope filter switches to Movies", currentScope == SearchScope.Movies)

        // Test contentRoute mapping
        assertTrue(contentRoute(ContentType.Live, "ch1") == "live/ch1")
        assertTrue(contentRoute(ContentType.Movie, "m1") == "movies/m1")
        assertTrue(contentRoute(ContentType.Series, "s1") == "series/s1")
    }

    // --------------------------------------------------------------------------
    // Phase 14.2K.1 — Search Result Artwork + Clean Media Titles Tests
    // --------------------------------------------------------------------------

    @Test
    fun globalSearchResultArtworkRendersLogosPostersAndFallbacks() {
        setLandscapeContent {
            WatchioTheme {
                Column {
                    com.watchioiptv.nativeapp.feature.library.SearchResultArtwork(
                        contentType = ContentType.Live,
                        imageUrl = "https://example.invalid/bbcone.png",
                    )
                    com.watchioiptv.nativeapp.feature.library.SearchResultArtwork(
                        contentType = ContentType.Live,
                        imageUrl = null,
                    )
                    com.watchioiptv.nativeapp.feature.library.SearchResultArtwork(
                        contentType = ContentType.Movie,
                        imageUrl = "https://example.invalid/alien.jpg",
                    )
                    com.watchioiptv.nativeapp.feature.library.SearchResultArtwork(
                        contentType = ContentType.Series,
                        imageUrl = null,
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Assert Artwork Logo and Poster containers are present
        val logos = composeRule.onAllNodesWithTag("search-artwork-logo", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expect 2 live channel logo containers", logos.size == 2)

        val posters = composeRule.onAllNodesWithTag("search-artwork-poster", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expect 2 movie/series poster containers", posters.size == 2)

        // Fallbacks for null image URLs
        val fallbacks = composeRule.onAllNodesWithTag("search-artwork-fallback", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expect fallbacks for items without artwork", fallbacks.size == 2)
    }

    @Test
    fun globalSearchScreenDisplaysArtworkAndMetadata() {
        val movieResult = WatchioSearchResult(
            providerId = ProviderId("p1"),
            contentType = ContentType.Movie,
            contentId = "m1",
            title = "Alien Romulus",
            subtitle = "Sci-Fi",
            imageUrl = "https://example.invalid/alien.jpg",
            year = "2024",
            rating = "7.5",
        )
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    GlobalSearchScreen(
                        state = SearchUiState(
                            query = "alien",
                            scope = SearchScope.Movies,
                            results = SearchResults(movies = listOf(movieResult)),
                            loading = false,
                        ),
                        onQuery = {},
                        onScope = {},
                        onResult = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global-search-result-movie").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Alien Romulus", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("2024", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("★ 7.5", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    // --------------------------------------------------------------------------
    // Phase 14.2K.2 — Global Search Layout Expansion & Density Tests
    // --------------------------------------------------------------------------

    @Test
    fun globalSearchOverlayUsesExpandedResponsiveDimensions() {
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    GlobalSearchScreen(
                        state = SearchUiState(
                            query = "test",
                            scope = SearchScope.Global,
                            results = SearchResults(),
                            loading = false,
                        ),
                        onQuery = {},
                        onScope = {},
                        onResult = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val overlayBounds = composeRule.onNodeWithTag("global-search-overlay").getUnclippedBoundsInRoot()
        val panelBounds = composeRule.onNodeWithTag("global-search-panel").getUnclippedBoundsInRoot()

        val overlayWidth = (overlayBounds.right - overlayBounds.left).value
        val overlayHeight = (overlayBounds.bottom - overlayBounds.top).value
        val panelWidth = (panelBounds.right - panelBounds.left).value
        val panelHeight = (panelBounds.bottom - panelBounds.top).value

        // Verify overlay uses majority of the landscape screen (>80% width, >70% height)
        assertTrue(
            "Global Search panel width ($panelWidth) should be > 80% of screen ($overlayWidth)",
            panelWidth > overlayWidth * 0.80f,
        )
        assertTrue(
            "Global Search panel height ($panelHeight) should be > 70% of screen ($overlayHeight)",
            panelHeight > overlayHeight * 0.70f,
        )
    }

    @Test
    fun globalSearchResultDensityAndMultiColumnLayout() {
        val liveResults = (1..4).map {
            WatchioSearchResult(
                providerId = ProviderId("p1"),
                contentType = ContentType.Live,
                contentId = "ch$it",
                title = "Channel $it HD",
                subtitle = "Entertainment",
                imageUrl = if (it % 2 == 0) "https://example.invalid/logo$it.png" else null,
            )
        }
        val movieResults = (1..4).map {
            WatchioSearchResult(
                providerId = ProviderId("p1"),
                contentType = ContentType.Movie,
                contentId = "m$it",
                title = "Movie $it",
                subtitle = "Action",
                imageUrl = if (it % 2 == 0) "https://example.invalid/poster$it.jpg" else null,
                year = "202$it",
                rating = "7.$it",
            )
        }
        val seriesResults = (1..4).map {
            WatchioSearchResult(
                providerId = ProviderId("p1"),
                contentType = ContentType.Series,
                contentId = "s$it",
                title = "Series $it",
                subtitle = "Drama",
                imageUrl = if (it % 2 == 0) "https://example.invalid/series$it.jpg" else null,
                year = "201$it",
                rating = "8.$it",
            )
        }

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    GlobalSearchScreen(
                        state = SearchUiState(
                            query = "test",
                            scope = SearchScope.Global,
                            results = SearchResults(
                                live = liveResults,
                                movies = movieResults,
                                series = seriesResults,
                            ),
                            loading = false,
                        ),
                        onQuery = {},
                        onScope = {},
                        onResult = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Section header for Live TV is displayed at top
        composeRule.onNodeWithTag("global-search-group-live", useUnmergedTree = true).assertIsDisplayed()

        // Multiple results are composed in the viewport simultaneously
        val liveCards = composeRule.onAllNodesWithTag("global-search-result-live", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expect multiple live TV cards rendered simultaneously in multi-column rows", liveCards.size >= 2)

        // Scroll to Movies section
        composeRule.onNodeWithTag("global-search-results").performScrollToNode(hasTestTag("global-search-group-movies"))
        composeRule.onNodeWithTag("global-search-group-movies", useUnmergedTree = true).assertIsDisplayed()

        val movieCards = composeRule.onAllNodesWithTag("global-search-result-movie", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expect multiple movie cards rendered simultaneously in multi-column rows", movieCards.size >= 2)

        // Scroll to Series section
        composeRule.onNodeWithTag("global-search-results").performScrollToNode(hasTestTag("global-search-group-series"))
        composeRule.onNodeWithTag("global-search-group-series", useUnmergedTree = true).assertIsDisplayed()
    }

    // --------------------------------------------------------------------------
    // Phase 14.2K.3 — Global Search Space & Density Tests
    // --------------------------------------------------------------------------

    @Test
    fun globalSearchTopControlsAreCompactAndResultsConsumeMajorityOfHeight() {
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    GlobalSearchScreen(
                        state = SearchUiState(
                            query = "test",
                            scope = SearchScope.Global,
                            results = SearchResults(
                                movies = (1..6).map {
                                    WatchioSearchResult(
                                        providerId = ProviderId("p1"),
                                        contentType = ContentType.Movie,
                                        contentId = "m$it",
                                        title = "Movie $it",
                                        subtitle = "Action",
                                        imageUrl = null,
                                        year = "2024",
                                        rating = "7.5",
                                    )
                                },
                            ),
                            loading = false,
                        ),
                        onQuery = {},
                        onScope = {},
                        onResult = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()

        val panelBounds = composeRule.onNodeWithTag("global-search-panel").getUnclippedBoundsInRoot()
        val resultsBounds = composeRule.onNodeWithTag("global-search-results").getUnclippedBoundsInRoot()

        val panelHeight = (panelBounds.bottom - panelBounds.top).value
        val resultsHeight = (resultsBounds.bottom - resultsBounds.top).value

        // Results area consumes majority of the overlay height (>60% on phone landscape, typically ~75% on tablet/TV)
        assertTrue(
            "Results area height ($resultsHeight) should be > 60% of panel height ($panelHeight)",
            resultsHeight > panelHeight * 0.60f,
        )

        // Top controls are present and compact
        composeRule.onNodeWithTag("global-search-header-row").assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-field").assertIsDisplayed()
        composeRule.onNodeWithTag("global-search-filter-row").assertIsDisplayed()
    }

    @Test
    fun globalSearchContentTypeFiltersUseFullResultAreaWithoutEmptySpace() {
        val movies = (1..4).map {
            WatchioSearchResult(
                providerId = ProviderId("p1"),
                contentType = ContentType.Movie,
                contentId = "m$it",
                title = "Movie $it",
                subtitle = "Action",
                imageUrl = null,
                year = "2024",
                rating = "8.0",
            )
        }
        val series = (1..4).map {
            WatchioSearchResult(
                providerId = ProviderId("p1"),
                contentType = ContentType.Series,
                contentId = "s$it",
                title = "Series $it",
                subtitle = "Drama",
                imageUrl = null,
                year = "2023",
                rating = "8.5",
            )
        }
        val live = (1..4).map {
            WatchioSearchResult(
                providerId = ProviderId("p1"),
                contentType = ContentType.Live,
                contentId = "ch$it",
                title = "Live Channel $it",
                subtitle = "News",
                imageUrl = null,
            )
        }

        // Test Movies scope
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    GlobalSearchScreen(
                        state = SearchUiState(
                            query = "test",
                            scope = SearchScope.Movies,
                            results = SearchResults(live = live, movies = movies, series = series),
                            loading = false,
                        ),
                        onQuery = {},
                        onScope = {},
                        onResult = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global-search-group-movies", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Live group should not exist in Movies-only scope", composeRule.onAllNodesWithTag("global-search-group-live").fetchSemanticsNodes().isEmpty())
        assertTrue("Series group should not exist in Movies-only scope", composeRule.onAllNodesWithTag("global-search-group-series").fetchSemanticsNodes().isEmpty())
    }

    // --------------------------------------------------------------------------
    // End of Phase 14.2K, 14.2K.1, 14.2K.2 & 14.2K.3 tests
    // --------------------------------------------------------------------------

    private fun assertFiveCardsOnFirstRow(cardTag: String, gridTag: String, posterTag: String) {
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodesWithTag(cardTag, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected at least five visible cards; found=${nodes.size}", nodes.size >= 5)
        val firstRowTop = nodes.take(5).map { it.boundsInRoot.top }
        assertTrue("first five cards should share first row", firstRowTop.max() - firstRowTop.min() < 2f)
        val gridBounds = composeRule.onAllNodesWithTag(gridTag, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("first card should start inside grid", nodes[0].boundsInRoot.left >= gridBounds.left)
        assertTrue("fifth card should end inside grid", nodes[4].boundsInRoot.right <= gridBounds.right + 1f)
        for (index in 0 until 4) {
            assertTrue("cards should not overlap", nodes[index].boundsInRoot.right <= nodes[index + 1].boundsInRoot.left + 1f)
        }
        val posterBounds = composeRule.onAllNodesWithTag(posterTag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val ratio = (posterBounds.bottom - posterBounds.top).value / (posterBounds.right - posterBounds.left).value
        assertTrue("poster should keep 2:3 ratio; ratio=$ratio", ratio in 1.45f..1.55f)
        if (nodes.size > 5) {
            assertTrue("sixth card should wrap to next row", nodes[5].boundsInRoot.top > firstRowTop.first() + 20f)
        }
    }

    private fun setLandscapeContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.runOnUiThread {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        composeRule.setContent(content)
    }

    private fun liveCategory(id: String, name: String) = LiveTvCategory(id, name, LiveTvCategoryKind.All)
    private fun movieCategory(id: String) = MovieCategory(id, "All Movies", MovieCategoryKind.All)
    private fun seriesCategory(id: String) = SeriesCategory(id, "All Series", SeriesCategoryKind.All)

    private fun liveChannel(id: String, name: String) = LiveTvChannel(
        providerId = ProviderId("provider"),
        providerType = ProviderType.Xtream,
        id = id,
        name = name,
        logoUrl = null,
        categoryId = "all",
        epgChannelId = id,
        extension = "ts",
        directUrl = null,
        headers = emptyMap(),
        serverOrder = id.toIntOrNull() ?: 0,
        isFavorite = false,
    )

    private fun movie(id: String) = WatchioMovieItem(
        providerId = ProviderId("provider"),
        providerType = ProviderType.Xtream,
        id = id,
        name = "Movie $id",
        posterUrl = "https://example.invalid/movie$id.jpg",
        categoryId = "all",
        rating = "7.$id",
        genre = "Drama",
        containerExtension = "mp4",
        trailerKey = null,
        serverOrder = id.toIntOrNull() ?: 0,
        directUrl = null,
        headers = emptyMap(),
        isFavorite = false,
        resumePositionMs = null,
        resumeDurationMs = null,
    )

    private fun series(id: String) = WatchioSeriesItem(
        providerId = ProviderId("provider"),
        providerType = ProviderType.Xtream,
        id = id,
        name = "Series $id",
        coverUrl = "https://example.invalid/series$id.jpg",
        categoryId = "all",
        plot = null,
        cast = null,
        director = null,
        genre = "Drama",
        releaseDate = "2024",
        rating = "8.$id",
        runtime = null,
        trailerKey = null,
        tmdbId = null,
        serverOrder = id.toIntOrNull() ?: 0,
        isFavorite = false,
        lastEpisodeId = null,
    )

    private fun guideChannel(channel: LiveTvChannel) = WatchioGuideChannel(
        providerId = channel.providerId,
        channelId = channel.id,
        displayName = channel.name,
        logo = channel.logoUrl,
        channelNumber = null,
        category = null,
        isFavourite = false,
        isCurrentlyPlaying = false,
        epgChannelId = channel.epgChannelId,
        liveChannel = channel,
    )

    // --------------------------------------------------------------------------
    // Full Player Controls + TV Remote UX tests
    // --------------------------------------------------------------------------

    @Test
    fun fullscreenPlayerVodShowsSeekAndProgressControls() {
        val fakePlayer = FakePlayerManager().apply {
            setMetadata(
                WatchioPlayerMetadata(
                    currentMedia = PlaybackMedia("http://example.invalid/movie.mp4", "Sample Movie", isLive = false),
                    positionMs = 120_000L,
                    durationMs = 600_000L,
                    isSeekable = true,
                )
            )
        }
        var seekDelta = 0L
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = WatchioPlayerState.Playing(fakePlayer.snapshot()),
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Movie(
                            title = "Sample Movie",
                            year = "2024",
                            rating = "★ 8.5",
                            runtime = "1h 40m",
                            genre = "Sci-Fi",
                        ),
                        onPlayPause = {},
                        onSeek = { seekDelta = it },
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-play-pause", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-rewind", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-forward", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-restart", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-position", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-duration", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-progress-bar", useUnmergedTree = true).assertIsDisplayed()

        // Clicking rewind triggers seek feedback
        composeRule.onNodeWithTag("player-rewind", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue("seekDelta should be -10s; was $seekDelta", seekDelta == -10_000L)
        composeRule.onNodeWithTag("player-seek-feedback", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fullscreenPlayerLiveHidesFakeSeekControls() {
        val fakePlayer = FakePlayerManager().apply {
            setMetadata(
                WatchioPlayerMetadata(
                    currentMedia = PlaybackMedia("http://example.invalid/live.ts", "Live Channel", isLive = true),
                    isSeekable = false,
                )
            )
        }
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = WatchioPlayerState.Playing(fakePlayer.snapshot()),
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Live(
                            channelName = "BBC One HD",
                            programmeTitle = "Evening News",
                            programmeStartTime = "20:00",
                            programmeEndTime = "21:00",
                            programmeProgress = 0.5f,
                            hasPreviousChannel = true,
                            hasNextChannel = true,
                        ),
                        onPlayPause = {},
                        onSeek = {},
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-play-pause", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-prev-channel", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-next-channel", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-live-epg-progress", useUnmergedTree = true).assertIsDisplayed()

        // Standard live must not have rewind or forward controls
        assertTrue(composeRule.onAllNodesWithTag("player-rewind").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("player-forward").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun fullscreenPlayerLiveSeekableShowsSeekControls() {
        val fakePlayer = FakePlayerManager().apply {
            setMetadata(
                WatchioPlayerMetadata(
                    currentMedia = PlaybackMedia("http://example.invalid/timeshift.ts", "Timeshift Channel", isLive = true),
                    positionMs = 30_000L,
                    durationMs = 120_000L,
                    isSeekable = true,
                )
            )
        }
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = WatchioPlayerState.Playing(fakePlayer.snapshot()),
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Live(
                            channelName = "Timeshift Channel",
                            programmeTitle = "Catchup Programme",
                        ),
                        onPlayPause = {},
                        onSeek = {},
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-play-pause", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-rewind", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-forward", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fullscreenPlayerEpisodeShowsEpisodeNavigation() {
        val fakePlayer = FakePlayerManager().apply {
            setMetadata(
                WatchioPlayerMetadata(
                    currentMedia = PlaybackMedia("http://example.invalid/ep2.mp4", "Episode 2", isLive = false),
                    positionMs = 60_000L,
                    durationMs = 300_000L,
                    isSeekable = true,
                )
            )
        }
        var prevEpClicked = false
        var nextEpClicked = false
        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = WatchioPlayerState.Playing(fakePlayer.snapshot()),
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Episode(
                            seriesTitle = "Great Series",
                            seasonNumber = 1,
                            episodeNumber = 2,
                            episodeTitle = "The Next Chapter",
                            hasPreviousEpisode = true,
                            hasNextEpisode = true,
                            onPreviousEpisode = { prevEpClicked = true },
                            onNextEpisode = { nextEpClicked = true },
                        ),
                        onPlayPause = {},
                        onSeek = {},
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-prev-episode", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-next-episode", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-prev-episode", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("player-next-episode", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue("prevEpClicked should be true", prevEpClicked)
        assertTrue("nextEpClicked should be true", nextEpClicked)
    }

    @Test
    fun fullscreenPlayerAudioAndSubtitleDialogsOpenAndDismiss() {
        val audio1 = com.watchioiptv.nativeapp.core.player.WatchioAudioTrack("a1", "English • 5.1", isSelected = true)
        val audio2 = com.watchioiptv.nativeapp.core.player.WatchioAudioTrack("a2", "Spanish • Stereo", isSelected = false)
        val sub1 = com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack("s1", "English", isSelected = true)

        val fakePlayer = FakePlayerManager().apply {
            setMetadata(
                WatchioPlayerMetadata(
                    audioTracks = listOf(audio1, audio2),
                    selectedAudioTrack = audio1,
                    subtitleTracks = listOf(sub1),
                    selectedSubtitleTrack = sub1,
                    isSeekable = true,
                )
            )
        }

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = WatchioPlayerState.Playing(fakePlayer.snapshot()),
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Movie(
                            title = "Movie With Tracks",
                        ),
                        onPlayPause = {},
                        onSeek = {},
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-audio-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-audio-dialog", useUnmergedTree = true).assertIsDisplayed()

        // Close audio dialog
        composeRule.onNodeWithTag("player-dialog-close", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Open subtitles dialog
        composeRule.onNodeWithTag("player-subtitles-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-subtitles-dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fullscreenPlayerRecoveringShowsReconnectingAndNotFailureErrorPanel() {
        val fakePlayer = FakePlayerManager()
        val recoveringState = WatchioPlayerState.Recovering("Stream reconnecting...", fakePlayer.snapshot())

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = recoveringState,
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Live(
                            channelName = "Live Channel",
                        ),
                        onPlayPause = {},
                        onSeek = {},
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-recovering", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("player-error should not be displayed when recovering", composeRule.onAllNodesWithTag("player-error").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun fullscreenPlayerFailedShowsRetryAndBackButtons() {
        val fakePlayer = FakePlayerManager()
        val failedState = WatchioPlayerState.Failed("Stream unreachable", fakePlayer.snapshot())

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = failedState,
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Live(
                            channelName = "Live Channel",
                        ),
                        onPlayPause = {},
                        onSeek = {},
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-error", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-retry", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-error-back", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fullscreenPlayerLiveChannelsAndSettingsOpen() {
        val fakePlayer = FakePlayerManager()
        var channelsClicked = false

        setLandscapeContent {
            WatchioTheme {
                Box(Modifier.fillMaxSize()) {
                    com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen(
                        playerState = WatchioPlayerState.Playing(fakePlayer.snapshot()),
                        playerSettings = com.watchioiptv.nativeapp.domain.repository.PlayerSettings(),
                        playerManager = fakePlayer,
                        contentContext = com.watchioiptv.nativeapp.feature.player.PlayerContentContext.Live(
                            channelName = "BBC One HD",
                            onChannelsClick = { channelsClicked = true },
                        ),
                        onPlayPause = {},
                        onSeek = {},
                        onRestart = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-channels-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player-channels-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue("channelsClicked should be true", channelsClicked)

        // Open settings dialog
        composeRule.onNodeWithTag("player-settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-settings-dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    private class FakePlayerManager : WatchioPlayerManager {
        private var metadata = WatchioPlayerMetadata()
        private val mutableState = MutableStateFlow<WatchioPlayerState>(WatchioPlayerState.Idle(metadata))
        override val state: StateFlow<WatchioPlayerState> = mutableState

        fun setMetadata(newMetadata: WatchioPlayerMetadata) {
            metadata = newMetadata
            mutableState.value = WatchioPlayerState.Playing(newMetadata)
        }

        override suspend fun load(media: PlaybackMedia) {
            metadata = metadata.copy(currentMedia = media, isSeekable = !media.isLive)
            mutableState.value = WatchioPlayerState.Playing(metadata)
        }
        override fun play() {
            mutableState.value = WatchioPlayerState.Playing(metadata)
        }
        override fun pause() {
            mutableState.value = WatchioPlayerState.Paused(metadata)
        }
        override fun stop() {
            metadata = metadata.copy(currentMedia = null)
            mutableState.value = WatchioPlayerState.Idle(metadata)
        }
        override fun retry() = Unit
        override fun seekTo(positionMs: Long) {
            metadata = metadata.copy(positionMs = positionMs)
        }
        override fun seekBy(deltaMs: Long) {
            metadata = metadata.copy(positionMs = (metadata.positionMs + deltaMs).coerceAtLeast(0L))
        }
        override fun selectAudioTrack(track: com.watchioiptv.nativeapp.core.player.WatchioAudioTrack) {
            metadata = metadata.copy(selectedAudioTrack = track)
        }
        override fun selectSubtitleTrack(track: com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack?) {
            metadata = metadata.copy(selectedSubtitleTrack = track)
        }
        override fun setVideoScalingMode(mode: com.watchioiptv.nativeapp.domain.repository.VideoScalingMode) {
            metadata = metadata.copy(videoScalingMode = mode)
        }
        override fun setPlaybackSpeed(speed: Float) {
            metadata = metadata.copy(playbackSpeed = speed)
        }
        override fun setMuted(muted: Boolean) {
            metadata = metadata.copy(isMuted = muted)
        }
        override fun restart() {
            seekTo(0L)
            play()
        }
        override fun snapshot(): WatchioPlayerMetadata = metadata
        override fun attachSurface(container: ViewGroup) = Unit
        override fun detachSurface(container: ViewGroup) = Unit
        override fun release() = Unit
    }
}
