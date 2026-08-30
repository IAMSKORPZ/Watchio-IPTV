package com.watchioiptv.nativeapp.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.PlayerReliability
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.movies.MovieCategory
import com.watchioiptv.nativeapp.data.movies.MovieDetails
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.movies.WatchioMovieItem
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.repository.FavoriteItem
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryItem
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MoviesUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val categories: List<MovieCategory> = emptyList(),
    val selectedCategory: MovieCategory? = null,
    val movies: List<WatchioMovieItem> = emptyList(),
    val searchQuery: String = "",
    val categorySearchQuery: String = "",
)

data class MovieDetailsUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val details: MovieDetails? = null,
    val autoResumeEnabled: Boolean = true,
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class MoviesViewModel(
    private val moviesRepository: MoviesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val playerManager: WatchioPlayerManager,
    private val clock: WatchioClock,
) : ViewModel() {
    private val mutableMovies = MutableStateFlow(MoviesUiState())
    private val mutableDetails = MutableStateFlow(MovieDetailsUiState())
    private val searchQueryFlow = MutableStateFlow("")
    private var selectedMovie: WatchioMovieItem? = null
    private var progressJob: Job? = null
    private var categoryJob: Job? = null
    private var initialResumePending = false
    private var initialResumeStartMs = 0L

    val moviesState: StateFlow<MoviesUiState> = mutableMovies.asStateFlow()
    val detailsState: StateFlow<MovieDetailsUiState> = mutableDetails.asStateFlow()
    val playerState = playerManager.state

    init {
        loadMovies()
        viewModelScope.launch {
            searchQueryFlow
                .debounce(250L)
                .distinctUntilChanged()
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
        viewModelScope.launch {
            playerManager.state.collect { state ->
                when (state) {
                    is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Ended -> {
                        val snapshot = playerManager.snapshot()
                        val finalDur = snapshot.durationMs ?: selectedMovie?.resumeDurationMs ?: snapshot.positionMs
                        saveProgress(forcedPosition = finalDur, forcedDuration = finalDur)
                        progressJob?.cancel()
                    }
                    is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Playing -> {
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
        categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val providerId = moviesRepository.selectedProviderId()
            if (providerId == null) {
                mutableMovies.value = MoviesUiState(loading = false, errorMessage = "Add a provider first.")
                return@launch
            }
            val categories = moviesRepository.categories(providerId)
            val selected = categories.firstOrNull()
            val items = selected?.let { moviesRepository.movies(providerId, it) }.orEmpty()
            mutableMovies.value = MoviesUiState(
                loading = false,
                categories = categories,
                selectedCategory = selected,
                movies = items,
            )
        }
    }

    fun selectCategory(category: MovieCategory) {
        categoryJob?.cancel()
        val currentQuery = mutableMovies.value.searchQuery
        mutableMovies.value = mutableMovies.value.copy(selectedCategory = category)
        categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val providerId = moviesRepository.selectedProviderId() ?: return@launch
            val items = moviesRepository.movies(providerId, category, currentQuery)
            mutableMovies.value = mutableMovies.value.copy(movies = items)
        }
    }

    fun updateSearch(query: String) {
        val state = mutableMovies.value
        mutableMovies.value = state.copy(searchQuery = query)
        if (query.isBlank()) {
            searchQueryFlow.value = ""
            categoryJob?.cancel()
            categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                val providerId = moviesRepository.selectedProviderId() ?: return@launch
                val currentCategory = mutableMovies.value.selectedCategory
                    ?: mutableMovies.value.categories.firstOrNull()
                    ?: return@launch
                val items = moviesRepository.movies(providerId, currentCategory, "")
                mutableMovies.value = mutableMovies.value.copy(movies = items)
            }
        } else {
            searchQueryFlow.value = query
        }
    }

    private suspend fun executeSearch(query: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val providerId = moviesRepository.selectedProviderId() ?: return@withContext
        val state = mutableMovies.value
        val allCategory = state.categories.firstOrNull { it.kind == com.watchioiptv.nativeapp.data.movies.MovieCategoryKind.All }
            ?: state.categories.firstOrNull()
            ?: return@withContext
        val results = moviesRepository.movies(providerId, allCategory, query)
        mutableMovies.value = mutableMovies.value.copy(movies = results)
    }

    fun updateCategorySearch(query: String) {
        mutableMovies.value = mutableMovies.value.copy(categorySearchQuery = query)
    }

    fun loadDetails(movie: WatchioMovieItem) {
        selectedMovie = movie
        viewModelScope.launch {
            val currentAutoResume = mutableDetails.value.autoResumeEnabled
            mutableDetails.value = MovieDetailsUiState(loading = true, autoResumeEnabled = currentAutoResume)
            mutableDetails.value = MovieDetailsUiState(loading = false, details = moviesRepository.details(movie), autoResumeEnabled = currentAutoResume)
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
            is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Playing -> {
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
                if (playerManager.state.value is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Playing) {
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
