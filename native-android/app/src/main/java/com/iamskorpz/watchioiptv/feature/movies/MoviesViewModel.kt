package com.iamskorpz.watchioiptv.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.PlayerReliability
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.util.WatchioClock
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.movies.MovieCategory
import com.iamskorpz.watchioiptv.data.movies.MovieDetails
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.movies.WatchioMovieItem
import com.iamskorpz.watchioiptv.data.library.SearchRepository
import com.iamskorpz.watchioiptv.data.library.SearchScope
import com.iamskorpz.watchioiptv.data.library.WatchioSearchResult
import com.iamskorpz.watchioiptv.data.xtream.CatalogSyncKey
import com.iamskorpz.watchioiptv.data.xtream.CatalogSyncState
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.repository.FavoriteItem
import com.iamskorpz.watchioiptv.domain.repository.FavoritesRepository
import com.iamskorpz.watchioiptv.domain.repository.HistoryItem
import com.iamskorpz.watchioiptv.domain.repository.HistoryRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshCoordinator
import com.iamskorpz.watchioiptv.data.refresh.RefreshStatus
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity
import com.iamskorpz.watchioiptv.data.downloads.DownloadEligibility
import com.iamskorpz.watchioiptv.data.downloads.EnqueueResult
import com.iamskorpz.watchioiptv.data.downloads.MovieDownloadAuthorizationResolver
import com.iamskorpz.watchioiptv.data.downloads.PlaylistMovieDownloadAuthorizationResolver
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadPolicy
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadState
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadsRepository
import com.iamskorpz.watchioiptv.data.downloads.OfflineMovieDownloadCandidate
import com.iamskorpz.watchioiptv.data.downloads.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.FlowPreview::class)
internal fun kotlinx.coroutines.flow.Flow<String>.debouncedMovieSearchQueries() =
    debounce(50L).distinctUntilChanged()

data class MoviesUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val categories: List<MovieCategory> = emptyList(),
    val selectedCategory: MovieCategory? = null,
    val movies: List<WatchioMovieItem> = emptyList(),
    val searchResults: List<WatchioSearchResult> = emptyList(),
    val searchQuery: String = "",
    val searchLoading: Boolean = false,
    val searchStartedAtElapsedMs: Long? = null,
    val categorySearchQuery: String = "",
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val catalogSyncState: CatalogSyncState = CatalogSyncState.Idle,
)

data class MovieDetailsUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val details: MovieDetails? = null,
    val autoResumeEnabled: Boolean = true,
    val download: MovieDownloadUiState = MovieDownloadUiState(),
)

data class MovieDownloadUiState(
    val enabled: Boolean = false,
    val label: String = "Download",
    val message: String = "Offline download permission has not been established for this movie.",
    val downloadId: String? = null,
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class MoviesViewModel(
    private val moviesRepository: MoviesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val playerManager: WatchioPlayerManager,
    private val clock: WatchioClock,
    private val xtreamRepository: XtreamRepository,
    private val searchRepository: SearchRepository? = null,
    private val catalogRefreshCoordinator: CatalogRefreshCoordinator? = null,
    private val offlineDownloadsRepository: OfflineDownloadsRepository? = null,
    private val downloadAuthorizationResolver: MovieDownloadAuthorizationResolver = PlaylistMovieDownloadAuthorizationResolver,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
) : ViewModel() {
    private val mutableMovies = MutableStateFlow(MoviesUiState())
    private val mutableDetails = MutableStateFlow(MovieDetailsUiState())
    private val searchQueryFlow = MutableStateFlow("")
    private var selectedMovie: WatchioMovieItem? = null
    private var progressJob: Job? = null
    private var categoryJob: Job? = null
    private var searchCacheWarmupJob: Job? = null
    private var categoryProviderId: ProviderId? = null
    private var manualCategorySelected = false
    private var manualCategoryId: String? = null
    private var initialCategoryResolved = false
    private var initialResumePending = false
    private var initialResumeStartMs = 0L
    private var downloadRecords: List<OfflineDownloadEntity> = emptyList()

    val moviesState: StateFlow<MoviesUiState> = mutableMovies.asStateFlow()
    val detailsState: StateFlow<MovieDetailsUiState> = mutableDetails.asStateFlow()
    val playerState = playerManager.state

    init {
        catalogRefreshCoordinator?.let { coordinator ->
            viewModelScope.launch {
                coordinator.updates.collect { event ->
                    val activeProviderId = moviesRepository.selectedProviderId()
                    if (event.status == RefreshStatus.Updated &&
                        event.providerId == activeProviderId &&
                        ContentType.Movie in event.changedSections
                    ) {
                        refreshSilently()
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(settingsRepository.selectedProviderId, xtreamRepository.catalogSyncStates) { providerId, states ->
                providerId to providerId?.let { states[CatalogSyncKey(it, ContentType.Movie)] }.orIdle()
            }.distinctUntilChanged().collectLatest { (_, syncState) ->
                mutableMovies.value = mutableMovies.value.copy(catalogSyncState = syncState)
                loadMovies()
            }
        }
        viewModelScope.launch {
            searchQueryFlow
                .debouncedMovieSearchQueries()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        executeSearch(query)
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.playerSettings.collect { settings ->
                mutableDetails.value = mutableDetails.value.copy(autoResumeEnabled = settings.autoResume)
            }
        }
        offlineDownloadsRepository?.let { repository ->
            viewModelScope.launch {
                repository.downloads.collect { records ->
                    downloadRecords = records
                    refreshMovieDownloadState()
                }
            }
        }
        viewModelScope.launch {
            playerManager.state.collect { state ->
                when (state) {
                    is com.iamskorpz.watchioiptv.core.player.WatchioPlayerState.Ended -> {
                        val snapshot = playerManager.snapshot()
                        val finalDur = snapshot.durationMs ?: selectedMovie?.resumeDurationMs ?: snapshot.positionMs
                        saveProgress(forcedPosition = finalDur, forcedDuration = finalDur)
                        progressJob?.cancel()
                    }
                    is com.iamskorpz.watchioiptv.core.player.WatchioPlayerState.Playing -> {
                        if (progressJob?.isActive != true) {
                            selectedMovie?.let { startProgressSave(it) }
                        }
                    }
                    else -> {
                        progressJob?.cancel()
                    }
                }
            }
        }
    }

    fun loadMovies() {
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch(defaultDispatcher) {
            val providerId = moviesRepository.selectedProviderId()
            if (providerId == null) {
                mutableMovies.value = MoviesUiState(loading = false, errorMessage = "Add a provider first.")
                return@launch
            }
            val categories = moviesRepository.categories(providerId)
            if (categoryProviderId != providerId) {
                categoryProviderId = providerId
                manualCategorySelected = false
                manualCategoryId = null
                initialCategoryResolved = false
            }
            val current = mutableMovies.value
            val restored = current.selectedCategory?.let { selected ->
                categories.firstOrNull { it.id == selected.id }
            }
            val preferred = preferredInitialMovieCategory(categories)
            val load = resolveMovieCategoryLoad(categories, restored, manualCategoryId, manualCategorySelected) { category ->
                moviesRepository.moviePage(providerId, category, 0, MoviesRepository.PAGE_SIZE)
            }
            val selected = load.selectedCategory.also {
                if (preferred != null || manualCategorySelected) initialCategoryResolved = true
                else if (!initialCategoryResolved) {
                    if (current.catalogSyncState != CatalogSyncState.Syncing) initialCategoryResolved = true
                }
            }
            val activeQuery = mutableMovies.value.searchQuery
            val items = load.items
            if (mutableMovies.value.searchQuery != activeQuery) return@launch
            mutableMovies.value = MoviesUiState(
                loading = false,
                errorMessage = if (mutableMovies.value.catalogSyncState == CatalogSyncState.Failed) "Movies couldn't finish syncing." else null,
                categories = categories,
                selectedCategory = selected,
                movies = items,
                searchResults = current.searchResults,
                searchQuery = activeQuery,
                searchLoading = false,
                categorySearchQuery = current.categorySearchQuery,
                hasMore = activeQuery.isBlank() && items.size == MoviesRepository.PAGE_SIZE,
                catalogSyncState = mutableMovies.value.catalogSyncState,
            )
            searchCacheWarmupJob?.cancel()
            searchCacheWarmupJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                searchRepository?.warm(providerId) ?: moviesRepository.getOrLoadCatalog(providerId)
            }
        }
    }

    fun refreshSilently() {
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch(defaultDispatcher) {
            val providerId = moviesRepository.selectedProviderId() ?: return@launch
            val categories = moviesRepository.categories(providerId)
            if (categories.isEmpty()) return@launch

            val current = mutableMovies.value
            val restored = current.selectedCategory?.let { selected ->
                categories.firstOrNull { it.id == selected.id }
            }
            val targetCategory = restored ?: categories.firstOrNull() ?: return@launch

            val currentItemCount = current.movies.size.coerceAtLeast(MoviesRepository.PAGE_SIZE)
            val updatedMovies = moviesRepository.moviePage(providerId, targetCategory, 0, currentItemCount)

            val activeQuery = current.searchQuery
            mutableMovies.value = current.copy(
                categories = categories,
                selectedCategory = targetCategory,
                movies = updatedMovies,
                hasMore = activeQuery.isBlank() && updatedMovies.size == currentItemCount,
            )

            // If user has a movie details screen open, refresh details without dismissing
            selectedMovie?.let { currentMovie ->
                if (mutableDetails.value.details != null) {
                    val freshMovie = updatedMovies.firstOrNull { it.id == currentMovie.id }
                        ?: moviesRepository.movie(providerId, currentMovie.id)
                        ?: currentMovie
                    selectedMovie = freshMovie
                    val updatedDetails = runCatching { moviesRepository.details(freshMovie) }.getOrNull()
                    if (updatedDetails != null) {
                        mutableDetails.value = mutableDetails.value.copy(
                            details = updatedDetails,
                        )
                    }
                }
            }

            searchCacheWarmupJob?.cancel()
            searchCacheWarmupJob = viewModelScope.launch(Dispatchers.IO) {
                searchRepository?.warm(providerId) ?: moviesRepository.getOrLoadCatalog(providerId)
            }
        }
    }

    fun selectCategory(category: MovieCategory) {
        categoryJob?.cancel()
        manualCategorySelected = true
        manualCategoryId = category.id
        initialCategoryResolved = true
        mutableMovies.value = mutableMovies.value.copy(selectedCategory = category, movies = emptyList(), loadingMore = false, hasMore = false)
        categoryJob = viewModelScope.launch(defaultDispatcher) {
            val providerId = moviesRepository.selectedProviderId() ?: return@launch
            val items = moviesRepository.moviePage(providerId, category, 0, MoviesRepository.PAGE_SIZE)
            mutableMovies.value = mutableMovies.value.copy(movies = items, hasMore = items.size == MoviesRepository.PAGE_SIZE)
        }
    }

    fun updateSearch(query: String) {
        val state = mutableMovies.value
        mutableMovies.value = state.copy(
            searchQuery = query,
            searchResults = emptyList(),
            searchLoading = query.isNotBlank(),
            searchStartedAtElapsedMs = if (query.isBlank()) null else android.os.SystemClock.elapsedRealtime(),
            hasMore = false,
        )
        if (query.isBlank()) {
            searchQueryFlow.value = ""
            categoryJob?.cancel()
            categoryJob = viewModelScope.launch(defaultDispatcher) {
                val providerId = moviesRepository.selectedProviderId() ?: return@launch
                val currentCategory = mutableMovies.value.selectedCategory
                    ?: mutableMovies.value.categories.firstOrNull()
                    ?: return@launch
                if (mutableMovies.value.movies.isEmpty()) {
                    val items = moviesRepository.moviePage(providerId, currentCategory, 0, MoviesRepository.PAGE_SIZE)
                    mutableMovies.value = mutableMovies.value.copy(movies = items, searchLoading = false, hasMore = items.size == MoviesRepository.PAGE_SIZE)
                } else mutableMovies.value = mutableMovies.value.copy(searchLoading = false)
            }
        } else {
            categoryJob?.cancel()
            searchQueryFlow.value = query
        }
    }

    private suspend fun executeSearch(query: String) = kotlinx.coroutines.withContext(defaultDispatcher) {
        val providerId = moviesRepository.selectedProviderId() ?: return@withContext
        val shared = searchRepository
        if (shared != null) {
            val final = shared.searchProgressively(providerId, query, SearchScope.Movies) { strong ->
                val current = mutableMovies.value
                if (current.searchQuery == query && shared.selectedProviderId() == providerId) {
                    mutableMovies.value = current.copy(searchResults = strong.results.movies, searchLoading = true)
                }
            }
            if (shared.selectedProviderId() == providerId && mutableMovies.value.searchQuery == query) {
                val current = mutableMovies.value
                mutableMovies.value = current.copy(searchResults = final.results.movies, searchLoading = false)
                logMovieSearchComplete(current.searchStartedAtElapsedMs, final.results.movies.size)
            }
            return@withContext
        }
        if (moviesRepository.requiresFuzzySearch(query)) {
            val strongResults = moviesRepository.searchMovies(providerId, query, includeFuzzy = false)
            val current = mutableMovies.value
            if (current.searchQuery == query && moviesRepository.selectedProviderId() == providerId) {
                mutableMovies.value = current.copy(
                    searchResults = strongResults.map { it.toSearchResult() },
                    searchLoading = true,
                    hasMore = false,
                )
            }
        }
        val results = moviesRepository.searchMovies(providerId, query, includeFuzzy = true)
        val providerStillActive = moviesRepository.selectedProviderId() == providerId
        if (providerStillActive && mutableMovies.value.searchQuery == query) {
            val current = mutableMovies.value
            mutableMovies.value = current.copy(searchResults = results.map { it.toSearchResult() }, searchLoading = false, hasMore = false)
            logMovieSearchComplete(current.searchStartedAtElapsedMs, results.size)
        }
    }

    private fun WatchioMovieItem.toSearchResult() = WatchioSearchResult(
        providerId = providerId,
        contentType = ContentType.Movie,
        contentId = id,
        title = name,
        subtitle = genre,
        imageUrl = posterUrl,
        rating = rating,
    )

    private fun logMovieSearchComplete(startedAt: Long?, resultCount: Int) {
        if (!com.iamskorpz.watchioiptv.BuildConfig.DEBUG || startedAt == null) return
        android.util.Log.d(
            "WatchioMovieSearchUi",
            "event=complete elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt} resultCount=$resultCount",
        )
    }

    fun loadMore() {
        val state = mutableMovies.value
        if (state.loadingMore || !state.hasMore || state.searchQuery.isNotBlank()) return
        val category = state.selectedCategory ?: return
        mutableMovies.value = state.copy(loadingMore = true)
        categoryJob = viewModelScope.launch(defaultDispatcher) {
            val providerId = moviesRepository.selectedProviderId() ?: run {
                mutableMovies.value = mutableMovies.value.copy(loadingMore = false, hasMore = false)
                return@launch
            }
            val page = moviesRepository.moviePage(providerId, category, state.movies.size, MoviesRepository.PAGE_SIZE)
            val current = mutableMovies.value
            if (current.selectedCategory == category) {
                mutableMovies.value = current.copy(
                    movies = (current.movies + page).distinctBy { it.id },
                    loadingMore = false,
                    hasMore = page.size == MoviesRepository.PAGE_SIZE,
                )
            }
        }
    }

    fun updateCategorySearch(query: String) {
        mutableMovies.value = mutableMovies.value.copy(categorySearchQuery = query)
    }

    fun loadDetails(movie: WatchioMovieItem) {
        selectedMovie = movie
        viewModelScope.launch {
            val currentAutoResume = mutableDetails.value.autoResumeEnabled
            mutableDetails.value = MovieDetailsUiState(loading = true, autoResumeEnabled = currentAutoResume)
            val details = moviesRepository.details(movie)
            mutableDetails.value = MovieDetailsUiState(
                loading = false,
                details = details,
                autoResumeEnabled = currentAutoResume,
                download = movieDownloadState(movie),
            )
            offlineDownloadsRepository?.backfillMetadata(details.toOfflineDownloadCandidate())
        }
    }

    fun loadDetails(movieId: String) {
        viewModelScope.launch {
            val providerId = moviesRepository.selectedProviderId()
            val movie = providerId?.let { moviesRepository.movie(it, movieId) }
            if (movie == null) {
                mutableDetails.value = MovieDetailsUiState(loading = false, errorMessage = "Movie not found.", autoResumeEnabled = mutableDetails.value.autoResumeEnabled)
                return@launch
            }
            loadDetails(movie)
        }
    }

    fun toggleFavorite() {
        val details = mutableDetails.value.details ?: return
        viewModelScope.launch {
            val nowFavorite = favoritesRepository.toggle(
                FavoriteItem(
                    providerId = details.movie.providerId,
                    contentType = ContentType.Movie,
                    contentId = details.movie.id,
                    title = details.movie.name,
                    imageUrl = details.movie.posterUrl,
                    createdAtEpochMs = clock.nowEpochMs(),
                ),
            )
            mutableDetails.value = mutableDetails.value.copy(details = details.copy(movie = details.movie.copy(isFavorite = nowFavorite)))
        }
    }

    fun downloadMovie() {
        val details = mutableDetails.value.details ?: return
        val movie = details.movie
        val repository = offlineDownloadsRepository ?: return
        val candidate = details.toOfflineDownloadCandidate()
        if (OfflineDownloadPolicy.evaluate(candidate) !is DownloadEligibility.Allowed) return
        mutableDetails.value = mutableDetails.value.copy(
            download = mutableDetails.value.download.copy(enabled = false, label = "Queued", message = "Adding movie to Downloads…"),
        )
        viewModelScope.launch {
            val result = repository.enqueue(candidate)
            mutableDetails.value = mutableDetails.value.copy(
                download = when (result) {
                    is EnqueueResult.Accepted -> MovieDownloadUiState(false, "Queued", "Download queued.", result.downloadId)
                    is EnqueueResult.AlreadyExists -> movieDownloadState(movie).copy(downloadId = result.downloadId)
                    is EnqueueResult.Denied -> MovieDownloadUiState(false, "Unavailable", result.reason.userMessage())
                },
            )
        }
    }

    private fun refreshMovieDownloadState() {
        val movie = mutableDetails.value.details?.movie ?: selectedMovie ?: return
        mutableDetails.value = mutableDetails.value.copy(download = movieDownloadState(movie))
    }

    private fun movieDownloadState(movie: WatchioMovieItem): MovieDownloadUiState {
        val existing = downloadRecords.firstOrNull {
            it.providerId == movie.providerId.value && it.contentId == movie.id
        }
        if (existing != null) {
            val state = runCatching { OfflineDownloadState.valueOf(existing.state) }.getOrDefault(OfflineDownloadState.Failed)
            return when (state) {
                OfflineDownloadState.Queued -> MovieDownloadUiState(false, "Queued", "Waiting to download.", existing.downloadId)
                OfflineDownloadState.Downloading -> MovieDownloadUiState(false, "Downloading", "${existing.percentDownloaded.toInt().coerceIn(0, 100)}% downloaded.", existing.downloadId)
                OfflineDownloadState.Paused -> MovieDownloadUiState(false, "Paused", "Resume this movie from Downloads.", existing.downloadId)
                OfflineDownloadState.Completed -> MovieDownloadUiState(false, "Downloaded", "Available offline in Downloads.", existing.downloadId)
                OfflineDownloadState.Failed -> MovieDownloadUiState(false, "Download failed", "Retry this movie from Downloads.", existing.downloadId)
                OfflineDownloadState.Removing -> MovieDownloadUiState(false, "Removing", "Removing offline media…", existing.downloadId)
            }
        }
        val candidate = mutableDetails.value.details
            ?.takeIf { it.movie.id == movie.id }
            ?.toOfflineDownloadCandidate()
            ?: movie.toOfflinePolicyCandidate()
        return when (val eligibility = OfflineDownloadPolicy.evaluate(candidate)) {
            is DownloadEligibility.Allowed -> MovieDownloadUiState(true, "Download", "Save this authorised movie for offline viewing.")
            is DownloadEligibility.Denied -> MovieDownloadUiState(false, "Unavailable", eligibility.reason.userMessage())
        }
    }

    private fun MovieDetails.toOfflineDownloadCandidate() = OfflineMovieDownloadCandidate(
        providerId = movie.providerId.value,
        contentId = movie.id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        containerExtension = movie.containerExtension,
        authorization = downloadAuthorizationResolver.authorizationFor(movie.providerId.value, movie.id),
        synopsis = plot,
        releaseYear = releaseDate?.take(4),
        genre = genre,
        rating = rating,
        runtime = runtime,
    )

    private fun WatchioMovieItem.toOfflinePolicyCandidate() = OfflineMovieDownloadCandidate(
        providerId = providerId.value,
        contentId = id,
        title = name,
        posterUrl = posterUrl,
        containerExtension = containerExtension,
        authorization = downloadAuthorizationResolver.authorizationFor(providerId.value, id),
    )

    fun play(resume: Boolean = true) {
        val movie = mutableDetails.value.details?.movie ?: selectedMovie ?: return
        selectedMovie = movie
        viewModelScope.launch {
            val playback = moviesRepository.playback(movie, resume)
            if (playback.startPositionMs > 0L) {
                initialResumeStartMs = playback.startPositionMs
                initialResumePending = true
            } else {
                initialResumeStartMs = 0L
                initialResumePending = false
            }
            playerManager.load(PlaybackMedia(playback.url, movie.name, playback.headers, playback.startPositionMs, isLive = false))
            startProgressSave(movie)
        }
    }

    fun restartPlayback() {
        initialResumePending = false
        initialResumeStartMs = 0L
        playerManager.restart()
        saveProgress(forcedPosition = 0L)
    }

    fun seekBy(deltaMs: Long) {
        val snapshot = playerManager.snapshot()
        val target = PlayerReliability.clampedSeekTarget(snapshot.positionMs, deltaMs, snapshot.durationMs, snapshot.currentMedia?.isLive == true)
        playerManager.seekTo(target)
        saveProgress(forcedPosition = target)
    }

    fun playPause() {
        when (playerManager.state.value) {
            is com.iamskorpz.watchioiptv.core.player.WatchioPlayerState.Playing -> {
                saveProgress()
                progressJob?.cancel()
                playerManager.pause()
            }
            else -> {
                playerManager.play()
                selectedMovie?.let { startProgressSave(it) }
            }
        }
    }

    fun pauseForBackground() {
        saveProgress()
        progressJob?.cancel()
        playerManager.pause()
    }

    fun stopPlayback() {
        saveProgress()
        progressJob?.cancel()
        playerManager.stop()
    }

    private fun startProgressSave(movie: WatchioMovieItem) {
        progressJob?.cancel()
        selectedMovie = movie
        progressJob = viewModelScope.launch {
            while (true) {
                delay(15_000L)
                if (playerManager.state.value is com.iamskorpz.watchioiptv.core.player.WatchioPlayerState.Playing) {
                    saveProgress()
                }
            }
        }
    }

    private fun saveProgress(forcedPosition: Long? = null, forcedDuration: Long? = null) {
        val movie = selectedMovie ?: return
        val snapshot = playerManager.snapshot()
        val rawPosition = forcedPosition ?: snapshot.positionMs
        val rawDuration = forcedDuration ?: snapshot.durationMs ?: movie.resumeDurationMs

        if (initialResumePending && forcedPosition == null) {
            if (rawPosition < 5_000L && initialResumeStartMs > 5_000L) {
                return
            } else {
                initialResumePending = false
            }
        }

        viewModelScope.launch {
            historyRepository.upsert(
                HistoryItem(
                    providerId = movie.providerId,
                    contentType = ContentType.Movie,
                    contentId = movie.id,
                    title = movie.name,
                    imageUrl = movie.posterUrl,
                    positionMs = rawPosition,
                    durationMs = rawDuration,
                    lastWatchedAtEpochMs = clock.nowEpochMs(),
                ),
            )
        }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}

internal fun preferredInitialMovieCategory(categories: List<MovieCategory>): MovieCategory? {
    val normalizedCategories = categories.associateBy { normalizeMovieCategoryName(it.name) }
    return normalizedCategories["latest releases hollywood"]
        ?: normalizedCategories["latest releases"]
        ?: normalizedCategories["just released hollywood"]
}

internal fun resolveMovieCategory(
    categories: List<MovieCategory>,
    restored: MovieCategory?,
    manualCategoryId: String?,
    manualCategorySelected: Boolean,
): MovieCategory? {
    val manual = manualCategoryId?.let { id -> categories.firstOrNull { it.id == id } }
    return when {
        manualCategorySelected && manual != null -> manual
        manualCategorySelected -> categories.firstOrNull()
        preferredInitialMovieCategory(categories) != null -> preferredInitialMovieCategory(categories)
        restored != null -> restored
        else -> categories.firstOrNull()
    }
}

internal data class MovieCategoryLoad<T>(
    val selectedCategory: MovieCategory?,
    val items: List<T>,
)

internal suspend fun <T> resolveMovieCategoryLoad(
    categories: List<MovieCategory>,
    restored: MovieCategory?,
    manualCategoryId: String?,
    manualCategorySelected: Boolean,
    loadPage: suspend (MovieCategory) -> List<T>,
): MovieCategoryLoad<T> {
    val selected = resolveMovieCategory(categories, restored, manualCategoryId, manualCategorySelected)
    return MovieCategoryLoad(selected, selected?.let { loadPage(it) }.orEmpty())
}

private fun normalizeMovieCategoryName(value: String): String =
    value.trim().lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

private fun CatalogSyncState?.orIdle(): CatalogSyncState = this ?: CatalogSyncState.Idle
