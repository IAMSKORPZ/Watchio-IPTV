package com.watchioiptv.nativeapp.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.PlayerReliability
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.series.SeriesCategory
import com.watchioiptv.nativeapp.data.series.SeriesDetails
import com.watchioiptv.nativeapp.data.series.SeriesRepository
import com.watchioiptv.nativeapp.data.series.WatchioEpisodeItem
import com.watchioiptv.nativeapp.data.series.WatchioSeriesItem
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

data class SeriesUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val categories: List<SeriesCategory> = emptyList(),
    val selectedCategory: SeriesCategory? = null,
    val series: List<WatchioSeriesItem> = emptyList(),
    val searchQuery: String = "",
)

data class SeriesDetailsUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val details: SeriesDetails? = null,
    val selectedSeasonNumber: Int? = null,
    val activeTab: String = "episodes",
    val autoResumeEnabled: Boolean = true,
) {
    val selectedEpisodes: List<WatchioEpisodeItem>
        get() = details?.episodes?.filter { it.seasonNumber == selectedSeasonNumber }.orEmpty()
    val resumeEpisode: WatchioEpisodeItem?
        get() = details?.episodes?.firstOrNull { SeriesRepository.shouldResumePosition(it.resumePositionMs, it.resumeDurationMs) }
}

class SeriesViewModel(
    private val seriesRepository: SeriesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val playerManager: WatchioPlayerManager,
    private val clock: WatchioClock,
) : ViewModel() {
    private val mutableSeries = MutableStateFlow(SeriesUiState())
    private val mutableDetails = MutableStateFlow(SeriesDetailsUiState())
    private var selectedEpisode: WatchioEpisodeItem? = null
    private var progressJob: Job? = null

    val seriesState: StateFlow<SeriesUiState> = mutableSeries.asStateFlow()
    val detailsState: StateFlow<SeriesDetailsUiState> = mutableDetails.asStateFlow()
    val playerState = playerManager.state

    init {
        loadSeries()
        viewModelScope.launch {
            settingsRepository.playerSettings.collect { settings ->
                mutableDetails.value = mutableDetails.value.copy(autoResumeEnabled = settings.autoResume)
            }
        }
    }

    fun loadSeries() {
        viewModelScope.launch {
            val providerId = seriesRepository.selectedProviderId()
            if (providerId == null) {
                mutableSeries.value = SeriesUiState(loading = false, errorMessage = "Add a provider first.")
                return@launch
            }
            val categories = seriesRepository.categories(providerId)
            val selected = categories.firstOrNull()
            mutableSeries.value = SeriesUiState(
                loading = false,
                categories = categories,
                selectedCategory = selected,
                series = selected?.let { seriesRepository.series(providerId, it) }.orEmpty(),
            )
        }
    }

    fun selectCategory(category: SeriesCategory) {
        viewModelScope.launch {
            val providerId = seriesRepository.selectedProviderId() ?: return@launch
            mutableSeries.value = mutableSeries.value.copy(
                selectedCategory = category,
                series = seriesRepository.series(providerId, category, mutableSeries.value.searchQuery),
            )
        }
    }

    fun updateSearch(query: String) {
        viewModelScope.launch {
            val providerId = seriesRepository.selectedProviderId() ?: return@launch
            val allCategory = mutableSeries.value.categories.firstOrNull { it.id == "all" } ?: mutableSeries.value.selectedCategory ?: return@launch
            val category = if (query.isBlank()) mutableSeries.value.selectedCategory ?: allCategory else allCategory
            mutableSeries.value = mutableSeries.value.copy(searchQuery = query, series = seriesRepository.series(providerId, category, query))
        }
    }

    fun loadDetails(seriesId: String) {
        viewModelScope.launch {
            val providerId = seriesRepository.selectedProviderId()
            val item = providerId?.let { seriesRepository.item(it, seriesId) }
            if (item == null) {
                mutableDetails.value = SeriesDetailsUiState(
                    loading = false,
                    errorMessage = "Series not found.",
                    autoResumeEnabled = mutableDetails.value.autoResumeEnabled,
                )
                return@launch
            }
            val currentAutoResume = mutableDetails.value.autoResumeEnabled
            mutableDetails.value = SeriesDetailsUiState(loading = true, autoResumeEnabled = currentAutoResume)
            val details = seriesRepository.details(item)
            val selected = details.seasons.firstOrNull { it.seasonNumber == 1 }?.seasonNumber
                ?: details.seasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                ?: details.seasons.firstOrNull()?.seasonNumber
            mutableDetails.value = SeriesDetailsUiState(
                loading = false,
                details = details,
                selectedSeasonNumber = selected,
                autoResumeEnabled = currentAutoResume,
            )
        }
    }

    fun refreshDetails() {
        val current = mutableDetails.value
        val seriesId = current.details?.series?.id ?: return
        viewModelScope.launch {
            val providerId = seriesRepository.selectedProviderId()
            val item = providerId?.let { seriesRepository.item(it, seriesId) } ?: return@launch
            val refreshed = seriesRepository.details(item)
            val selected = current.selectedSeasonNumber ?: refreshed.seasons.firstOrNull { it.seasonNumber == 1 }?.seasonNumber
            mutableDetails.value = current.copy(
                loading = false,
                details = refreshed,
                selectedSeasonNumber = selected,
                activeTab = current.activeTab,
                autoResumeEnabled = current.autoResumeEnabled,
            )
        }
    }

    fun selectSeason(seasonNumber: Int) {
        mutableDetails.value = mutableDetails.value.copy(selectedSeasonNumber = seasonNumber)
    }

    fun selectTab(tab: String) {
        mutableDetails.value = mutableDetails.value.copy(activeTab = tab)
    }

    fun toggleFavorite() {
        val details = mutableDetails.value.details ?: return
        viewModelScope.launch {
            val nowFavorite = favoritesRepository.toggle(
                FavoriteItem(
                    providerId = details.series.providerId,
                    contentType = ContentType.Series,
                    contentId = details.series.id,
                    title = details.series.name,
                    imageUrl = details.series.coverUrl,
                    createdAtEpochMs = clock.nowEpochMs(),
                ),
            )
            mutableDetails.value = mutableDetails.value.copy(details = details.copy(series = details.series.copy(isFavorite = nowFavorite)))
        }
    }

    fun playEpisode(episode: WatchioEpisodeItem, resume: Boolean = true) {
        selectedEpisode = episode
        seriesRepository.markActiveEpisode(episode)
        viewModelScope.launch {
            val request = seriesRepository.playback(episode, resume)
            playerManager.load(PlaybackMedia(request.url, episode.title, request.headers, request.startPositionMs, isLive = false))
            startProgressSave(episode)
        }
    }

    fun playTopLevel(resume: Boolean = true) {
        val details = mutableDetails.value.details ?: return
        val resumeEpisode = details.episodes.firstOrNull { seriesRepository.shouldResume(it.resumePositionMs, it.resumeDurationMs) }
        val target = if (resume) {
            if (mutableDetails.value.autoResumeEnabled) resumeEpisode ?: details.episodes.firstOrNull() else details.episodes.firstOrNull()
        } else {
            resumeEpisode ?: details.series.lastEpisodeId?.let { id -> details.episodes.firstOrNull { it.episodeId == id } } ?: details.episodes.firstOrNull()
        } ?: return
        playEpisode(target, resume)
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
                selectedEpisode?.let { startProgressSave(it) }
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
        seriesRepository.clearActiveEpisode()
    }

    private fun startProgressSave(episode: WatchioEpisodeItem) {
        progressJob?.cancel()
        selectedEpisode = episode
        progressJob = viewModelScope.launch {
            while (true) {
                delay(15_000L)
                saveProgress()
            }
        }
    }

    private fun saveProgress() {
        val episode = selectedEpisode ?: seriesRepository.currentActiveEpisode() ?: return
        val snapshot = playerManager.snapshot()
        viewModelScope.launch {
            historyRepository.upsert(
                HistoryItem(
                    providerId = episode.providerId,
                    contentType = ContentType.Episode,
                    contentId = episode.seriesId,
                    subContentId = episode.episodeId,
                    title = episode.title,
                    imageUrl = episode.imageUrl,
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
