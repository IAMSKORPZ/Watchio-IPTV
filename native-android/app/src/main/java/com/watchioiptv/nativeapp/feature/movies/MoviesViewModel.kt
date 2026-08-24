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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private var selectedMovie: WatchioMovieItem? = null
    private var progressJob: Job? = null

    val moviesState: StateFlow<MoviesUiState> = mutableMovies.asStateFlow()
    val detailsState: StateFlow<MovieDetailsUiState> = mutableDetails.asStateFlow()
    val playerState = playerManager.state

    init {
        loadMovies()
        viewModelScope.launch {
            settingsRepository.playerSettings.collect { settings ->
                mutableDetails.value = mutableDetails.value.copy(autoResumeEnabled = settings.autoResume)
            }
        }
    }

    fun loadMovies() {
        viewModelScope.launch {
            val providerId = moviesRepository.selectedProviderId()
            if (providerId == null) {
                mutableMovies.value = MoviesUiState(loading = false, errorMessage = "Add a provider first.")
                return@launch
            }
            val categories = moviesRepository.categories(providerId)
            val selected = categories.firstOrNull()
            mutableMovies.value = MoviesUiState(
                loading = false,
                categories = categories,
                selectedCategory = selected,
                movies = selected?.let { moviesRepository.movies(providerId, it) }.orEmpty(),
            )
        }
    }

    fun selectCategory(category: MovieCategory) {
        viewModelScope.launch {
            val providerId = moviesRepository.selectedProviderId() ?: return@launch
            mutableMovies.value = mutableMovies.value.copy(
                selectedCategory = category,
                movies = moviesRepository.movies(providerId, category, mutableMovies.value.searchQuery),
            )
        }
    }

    fun updateSearch(query: String) {
        viewModelScope.launch {
            val state = mutableMovies.value
            val providerId = moviesRepository.selectedProviderId() ?: return@launch
            val allCategory = state.categories.firstOrNull { it.id == "all" } ?: state.selectedCategory ?: return@launch
            val category = if (query.isBlank()) state.selectedCategory ?: allCategory else allCategory
            mutableMovies.value = state.copy(searchQuery = query, movies = moviesRepository.movies(providerId, category, query))
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
        viewModelScope.launch {
            val playback = moviesRepository.playback(movie, resume)
            playerManager.load(PlaybackMedia(playback.url, movie.name, playback.headers, playback.startPositionMs, isLive = false))
            startProgressSave(movie)
        }
    }

    fun seekBy(deltaMs: Long) {
        val snapshot = playerManager.snapshot()
        playerManager.seekTo(PlayerReliability.clampedSeekTarget(snapshot.positionMs, deltaMs, snapshot.durationMs, snapshot.currentMedia?.isLive == true))
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
                saveProgress()
            }
        }
    }

    private fun saveProgress() {
        val movie = selectedMovie ?: return
        val snapshot = playerManager.snapshot()
        viewModelScope.launch {
            historyRepository.upsert(
                HistoryItem(
                    providerId = movie.providerId,
                    contentType = ContentType.Movie,
                    contentId = movie.id,
                    title = movie.name,
                    imageUrl = movie.posterUrl,
                    positionMs = snapshot.positionMs,
                    durationMs = snapshot.durationMs,
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
