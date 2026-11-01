package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.data.downloads.DownloadAuthorization
import com.iamskorpz.watchioiptv.data.downloads.EnqueueResult
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadState
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadsRepository
import com.iamskorpz.watchioiptv.data.downloads.OfflineMovieDownloadCandidate
import com.iamskorpz.watchioiptv.data.downloads.UitestMovieDownloadAuthorizationResolver
import com.iamskorpz.watchioiptv.feature.downloads.DownloadsViewModel
import com.iamskorpz.watchioiptv.feature.downloads.DownloadsCategory
import com.iamskorpz.watchioiptv.feature.downloads.DownloadsDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun groupsDownloadsAndForwardsQueueControls() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository()
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(
            entity("active", OfflineDownloadState.Downloading, 41.8f),
            entity("complete", OfflineDownloadState.Completed, 100f),
        )
        advanceUntilIdle()

        assertEquals(listOf("active"), viewModel.state.value.active.map { it.downloadId })
        assertEquals(41, viewModel.state.value.active.single().progressPercent)
        assertEquals(listOf("complete"), viewModel.state.value.completed.map { it.downloadId })
        viewModel.pause("active")
        viewModel.resume("active")
        viewModel.retry("active")
        viewModel.cancel("active")
        viewModel.requestDelete(viewModel.state.value.completed.single())
        viewModel.confirmDelete()
        assertEquals(listOf("pause:active", "resume:active", "retry:active", "cancel:active", "remove:complete"), repository.actions)
    }

    @Test
    fun uitestAuthorizationIsExactAndDoesNotEnableOtherMovies() {
        assertEquals(
            DownloadAuthorization.ProviderApproved,
            UitestMovieDownloadAuthorizationResolver.authorizationFor(
                UitestMovieDownloadAuthorizationResolver.PROVIDER_ID,
                UitestMovieDownloadAuthorizationResolver.CONTENT_ID,
            ),
        )
        assertEquals(
            DownloadAuthorization.NotEstablished,
            UitestMovieDownloadAuthorizationResolver.authorizationFor("another-provider", "another-movie"),
        )
    }

    @Test
    fun groupsEpisodesAsShowsAndNavigatesLibraryDetailsOffline() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository()
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(
            entity("episode-2", OfflineDownloadState.Completed, 100f).copy(
                contentType = "Episode", showId = "show", showTitle = "Offline Show", seasonNumber = 1,
                episodeNumber = 2, episodeTitle = "Second", synopsis = "Stored synopsis", localPosterPath = "/private/poster",
            ),
            entity("episode-1", OfflineDownloadState.Downloading, 50f).copy(
                contentType = "Episode", showId = "show", showTitle = "Offline Show", seasonNumber = 1,
                episodeNumber = 1, episodeTitle = "First", synopsis = "Stored synopsis", localPosterPath = "/private/poster",
            ),
        )
        advanceUntilIdle()

        val show = viewModel.state.value.shows.single()
        assertEquals("Offline Show", show.title)
        assertEquals(listOf(1, 2), show.seasons.single().episodes.map { it.episodeNumber })
        viewModel.selectCategory(DownloadsCategory.TvShows)
        viewModel.openShow(show.showKey)
        assertEquals(DownloadsDestination.Show(show.showKey), viewModel.state.value.destination)
        viewModel.openSeason(show.showKey, 1)
        assertEquals(DownloadsDestination.Season(show.showKey, 1), viewModel.state.value.destination)
        assertEquals(true, viewModel.navigateBack())
        assertEquals(DownloadsDestination.Show(show.showKey), viewModel.state.value.destination)
    }

    @Test
    fun seasonNavigationHandlesSingleEpisodeAndMissingOfflineMetadata() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository()
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(
            episode("only", 3, 1).copy(
                episodeTitle = null,
                synopsis = null,
                localPosterPath = null,
                localBackdropPath = null,
                localThumbnailPath = null,
            ),
        )
        advanceUntilIdle()

        val show = viewModel.state.value.shows.single()
        viewModel.openShow(show.showKey)
        viewModel.openSeason(show.showKey, 3)

        val season = viewModel.state.value.shows.single().seasons.single()
        assertEquals(DownloadsDestination.Season(show.showKey, 3), viewModel.state.value.destination)
        assertEquals(listOf(1), season.episodes.map { it.episodeNumber })
        assertEquals(null, season.episodes.single().thumbnailPath)
        assertEquals(true, viewModel.navigateBack())
        assertEquals(DownloadsDestination.Show(show.showKey), viewModel.state.value.destination)
    }

    @Test
    fun deletingMovieWaitsForRemovalThenReturnsToMoviesAndRestoresNearestFocus() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository(autoRemove = true)
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(entity("first", OfflineDownloadState.Completed, 100f), entity("second", OfflineDownloadState.Completed, 100f))
        advanceUntilIdle()

        viewModel.openMovie("first")
        viewModel.requestDelete(viewModel.state.value.movies.first())
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(DownloadsDestination.Library, viewModel.state.value.destination)
        assertEquals(DownloadsCategory.Movies, viewModel.state.value.selectedCategory)
        assertEquals("second", viewModel.state.value.focusRestoreKey)
    }

    @Test
    fun deletingLastMovieReturnsToNormalEmptyMoviesLibrary() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository(autoRemove = true)
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(entity("only", OfflineDownloadState.Completed, 100f))
        advanceUntilIdle()

        viewModel.openMovie("only")
        viewModel.requestDelete(viewModel.state.value.movies.single())
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(DownloadsDestination.Library, viewModel.state.value.destination)
        assertEquals(emptyList<Any>(), viewModel.state.value.movies)
    }

    @Test
    fun deletingEpisodeKeepsSeasonOrMovesToClosestValidTvDestination() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository(autoRemove = true)
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(episode("s1e1", 1, 1), episode("s1e2", 1, 2), episode("s2e1", 2, 1))
        advanceUntilIdle()
        val showKey = viewModel.state.value.shows.single().showKey

        viewModel.openSeason(showKey, 1)
        viewModel.requestDelete(viewModel.state.value.shows.single().seasons.first().episodes.first())
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(DownloadsDestination.Season(showKey, 1), viewModel.state.value.destination)
        assertEquals("s1e2", viewModel.state.value.focusRestoreKey)

        viewModel.requestDelete(viewModel.state.value.shows.single().seasons.first().episodes.single())
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(DownloadsDestination.Show(showKey), viewModel.state.value.destination)
    }

    @Test
    fun deletingLastSeasonReturnsToTvLibrary() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository(autoRemove = true)
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(episode("only", 1, 1))
        advanceUntilIdle()
        val show = viewModel.state.value.shows.single()
        viewModel.openSeason(show.showKey, 1)
        viewModel.requestDeleteSeason(show.seasons.single())
        viewModel.confirmDeleteSeason()
        advanceUntilIdle()

        assertEquals(DownloadsDestination.Library, viewModel.state.value.destination)
        assertEquals(DownloadsCategory.TvShows, viewModel.state.value.selectedCategory)
    }

    @Test
    fun deletingSeasonOrWholeShowReturnsToClosestValidTvDestination() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository(autoRemove = true)
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(episode("s1", 1, 1), episode("s2", 2, 1))
        advanceUntilIdle()
        var show = viewModel.state.value.shows.single()
        viewModel.openSeason(show.showKey, 1)
        viewModel.requestDeleteSeason(show.seasons.first())
        viewModel.confirmDeleteSeason()
        advanceUntilIdle()
        assertEquals(DownloadsDestination.Show(show.showKey), viewModel.state.value.destination)

        show = viewModel.state.value.shows.single()
        viewModel.requestDeleteShow(show)
        viewModel.confirmDeleteShow()
        advanceUntilIdle()
        assertEquals(DownloadsDestination.Library, viewModel.state.value.destination)
        assertEquals(DownloadsCategory.TvShows, viewModel.state.value.selectedCategory)
    }

    @Test
    fun deletionFailureStaysOnDetailsAndReportsUsefulError() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository(removeFailure = IllegalStateException("Storage is busy"))
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(entity("movie", OfflineDownloadState.Completed, 100f))
        advanceUntilIdle()
        viewModel.openMovie("movie")
        viewModel.requestDelete(viewModel.state.value.movies.single())
        viewModel.confirmDelete()

        assertEquals(DownloadsDestination.Movie("movie"), viewModel.state.value.destination)
        assertNotNull(viewModel.state.value.deletionErrorMessage)
    }

    @Test
    fun staleDetailsRoutesReturnToSafeDownloadsDestination() = runTest(dispatcher) {
        val repository = FakeDownloadsRepository()
        val viewModel = DownloadsViewModel(repository)
        repository.records.value = listOf(entity("movie", OfflineDownloadState.Completed, 100f), episode("episode", 1, 1))
        advanceUntilIdle()
        viewModel.openMovie("movie")
        repository.records.value = repository.records.value.filterNot { it.downloadId == "movie" }
        advanceUntilIdle()
        assertEquals(DownloadsDestination.Library, viewModel.state.value.destination)

        val show = viewModel.state.value.shows.single()
        viewModel.openSeason(show.showKey, 1)
        repository.records.value = emptyList()
        advanceUntilIdle()
        assertEquals(DownloadsDestination.Library, viewModel.state.value.destination)
        assertEquals(DownloadsCategory.TvShows, viewModel.state.value.selectedCategory)
    }

    private fun entity(id: String, state: OfflineDownloadState, percent: Float) = OfflineDownloadEntity(
        downloadId = id,
        providerId = "provider",
        contentId = "movie-$id",
        title = "Movie $id",
        localPosterPath = null,
        containerExtension = "mp4",
        mimeType = "video/mp4",
        state = state.name,
        bytesDownloaded = 1024L,
        contentLength = 2048L,
        percentDownloaded = percent,
        failureReason = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        completedAtEpochMs = null,
    )

    private fun episode(id: String, season: Int, episode: Int) = entity(id, OfflineDownloadState.Completed, 100f).copy(
        contentType = "Episode",
        showId = "show",
        showTitle = "Offline Show",
        seasonNumber = season,
        episodeNumber = episode,
        episodeTitle = "Episode $episode",
    )

    private class FakeDownloadsRepository(
        private val autoRemove: Boolean = false,
        private val removeFailure: RuntimeException? = null,
    ) : OfflineDownloadsRepository {
        val records = MutableStateFlow<List<OfflineDownloadEntity>>(emptyList())
        val actions = mutableListOf<String>()
        override val downloads = records
        override suspend fun enqueue(candidate: OfflineMovieDownloadCandidate) = EnqueueResult.Accepted("new")
        override suspend fun backfillMetadata(candidate: OfflineMovieDownloadCandidate) = Unit
        override fun pause(downloadId: String) { actions += "pause:$downloadId" }
        override fun resume(downloadId: String) { actions += "resume:$downloadId" }
        override fun retry(downloadId: String) { actions += "retry:$downloadId" }
        override fun cancel(downloadId: String) { actions += "cancel:$downloadId" }
        override fun remove(downloadId: String) {
            removeFailure?.let { throw it }
            actions += "remove:$downloadId"
            if (autoRemove) records.value = records.value.filterNot { it.downloadId == downloadId }
        }
        override suspend fun findById(downloadId: String) = records.value.firstOrNull { it.downloadId == downloadId }
        override suspend fun completedPlayback(downloadId: String, startPositionMs: Long): PlaybackMedia? = null
        override suspend fun reconcile() = Unit
    }
}
