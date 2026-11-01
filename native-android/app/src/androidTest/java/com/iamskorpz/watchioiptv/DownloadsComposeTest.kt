package com.iamskorpz.watchioiptv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadState
import com.iamskorpz.watchioiptv.feature.downloads.DownloadItemUi
import com.iamskorpz.watchioiptv.feature.downloads.DownloadsScreen
import com.iamskorpz.watchioiptv.feature.downloads.DownloadsCategory
import com.iamskorpz.watchioiptv.feature.downloads.DownloadsUiState
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadsComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun libraryUsesMovieAndTvShowCategoriesWithPosterGrid() {
        var action = ""
        val active = item("active", OfflineDownloadState.Downloading, 42)
        val completed = item("complete", OfflineDownloadState.Completed, 100)
        composeRule.setContent {
            WatchioTheme {
                DownloadsScreen(
                    state = DownloadsUiState(loading = false, active = listOf(active), completed = listOf(completed), movies = listOf(active, completed)),
                    onBack = { action = "back" },
                    onCategory = { action = "category:$it" },
                    onMovie = { action = "movie:$it" },
                    onShow = { action = "show:$it" },
                    onSeason = { _, _ -> },
                    onPause = { action = "pause:$it" },
                    onResume = { action = "resume:$it" },
                    onRetry = { action = "retry:$it" },
                    onCancel = { action = "cancel:$it" },
                    onDelete = { action = "delete:${it.downloadId}" },
                    onPlay = { action = "play:$it" },
                    onDismissDelete = {},
                    onConfirmDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("downloads-category-movies").assertHasClickAction().assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-category-tv-shows").assertHasClickAction().performClick()
        assertEquals("category:${DownloadsCategory.TvShows}", action)
        composeRule.onNodeWithTag("download-movie-active").assertHasClickAction().performClick()
        assertEquals("movie:active", action)
    }

    @Test
    fun deleteRequiresConfirmationAndEmptyStateIsClear() {
        val pending = item("complete", OfflineDownloadState.Completed, 100)
        var confirmed = false
        composeRule.setContent {
            WatchioTheme {
                DownloadsScreen(
                    state = DownloadsUiState(loading = false, pendingDelete = pending),
                    onCategory = {}, onMovie = {}, onShow = {}, onSeason = { _, _ -> },
                    onBack = {}, onPause = {}, onResume = {}, onRetry = {}, onCancel = {}, onDelete = {}, onPlay = {},
                    onDismissDelete = {},
                    onConfirmDelete = { confirmed = true },
                )
            }
        }
        composeRule.onNodeWithTag("downloads-empty-movies").assertIsDisplayed()
        composeRule.onNodeWithText("Movies you download will appear here and remain ready to watch offline.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete download?").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-delete-confirm").performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun restoredMovieFocusTargetsNearestRemainingPoster() {
        val first = item("first", OfflineDownloadState.Completed, 100)
        val second = item("second", OfflineDownloadState.Completed, 100)
        composeRule.setContent {
            WatchioTheme {
                DownloadsScreen(
                    state = DownloadsUiState(loading = false, movies = listOf(first, second), focusRestoreKey = "second"),
                    onCategory = {}, onMovie = {}, onShow = {}, onSeason = { _, _ -> },
                    onBack = {}, onPause = {}, onResume = {}, onRetry = {}, onCancel = {}, onDelete = {}, onPlay = {},
                    onDismissDelete = {}, onConfirmDelete = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("download-movie-second").assertIsFocused()
    }

    private fun item(id: String, state: OfflineDownloadState, percent: Int) = DownloadItemUi(
        downloadId = id,
        title = "Movie $id",
        posterPath = null,
        state = state,
        bytesDownloaded = 1024L,
        contentLength = 2048L,
        progressPercent = percent,
        failureMessage = null,
    )
}
