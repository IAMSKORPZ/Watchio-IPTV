package com.watchioiptv.nativeapp.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.PlayerReliability
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.series.SeriesCardUiModel
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class SeriesUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val categories: List<SeriesCategory> = emptyList(),
    val selectedCategory: SeriesCategory? = null,
    val series: List<SeriesCardUiModel> = emptyList(),
    val searchQuery: String = "",
)

data class SeriesDetailsUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val details: SeriesDetails? = null,
    val selectedSeasonNumber: Int? = null,
    val targetEpisodeId: String? = null,
    val activeTab: String = "episodes",
    val autoResumeEnabled: Boolean = true,
) {
    val selectedEpisodes: List<WatchioEpisodeItem>
        get() = details?.episodes?.filter { it.seasonNumber == selectedSeasonNumber }.orEmpty()
    val resumeEpisode: WatchioEpisodeItem?
        get() = (targetEpisodeId?.let { id -> details?.episodes?.firstOrNull { it.episodeId == id } })
            ?: details?.episodes?.firstOrNull { SeriesRepository.shouldResumePosition(it.resumePositionMs, it.resumeDurationMs) }
}

@OptIn(FlowPreview::class)
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
    private val searchQueryFlow = MutableStateFlow("")
    private var categoryJob: Job? = null
    private var selectedEpisode: WatchioEpisodeItem? = null
    private var progressJob: Job? = null
    private var initialResumePending = false
    private var initialResumeStartMs = 0L

    val seriesState: StateFlow<SeriesUiState> = mutableSeries.asStateFlow()
    val detailsState: StateFlow<SeriesDetailsUiState> = mutableDetails.asStateFlow()
    val playerState = playerManager.state

    init {
        loadSeries()
        viewModelScope.launch {
            searchQueryFlow
                .debounce(250)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val providerId = seriesRepository.selectedProviderId() ?: return@collectLatest
                    val allCategory = mutableSeries.value.categories.firstOrNull { it.id == "all" }
                        ?: mutableSeries.value.selectedCategory
                        ?: return@collectLatest
                    val category = if (query.isBlank()) mutableSeries.value.selectedCategory ?: allCategory else allCategory
                    val items = seriesRepository.seriesCards(providerId, category, query)
                    mutableSeries.value = mutableSeries.value.copy(searchQuery = query, series = items)
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
                        val episode = selectedEpisode ?: seriesRepository.currentActiveEpisode()
                        val finalDur = snapshot.durationMs ?: episode?.resumeDurationMs ?: snapshot.positionMs
                        saveProgress(forcedPosition = finalDur, forcedDuration = finalDur)
                        progressJob?.cancel()
                    }
                    is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Playing -> {
                        if (progressJob?.isActive != true) {
                            (selectedEpisode ?: seriesRepository.currentActiveEpisode())?.let { startProgressSave(it) }
                        }
                    }
                    else -> {
                        progressJob?.cancel()
                    }
                }
            }
        }
    }

    fun loadSeries() {
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val providerId = seriesRepository.selectedProviderId()
            if (providerId == null) {
                mutableSeries.value = SeriesUiState(loading = false, errorMessage = "Add a provider first.")
                return@launch
            }
            val categories = seriesRepository.categories(providerId)
            val selected = categories.firstOrNull()
            val items = selected?.let { seriesRepository.seriesCards(providerId, it) }.orEmpty()
            mutableSeries.value = SeriesUiState(
                loading = false,
                categories = categories,
                selectedCategory = selected,
                series = items,
            )
        }
    }

    fun selectCategory(category: SeriesCategory) {
        categoryJob?.cancel()
        val currentQuery = mutableSeries.value.searchQuery
        mutableSeries.value = mutableSeries.value.copy(selectedCategory = category)
        categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val providerId = seriesRepository.selectedProviderId() ?: return@launch
            val items = seriesRepository.seriesCards(providerId, category, currentQuery)
            mutableSeries.value = mutableSeries.value.copy(series = items)
        }
    }

    fun updateSearch(query: String) {
        if (query.isBlank()) {
            searchQueryFlow.value = ""
            categoryJob?.cancel()
            categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                val providerId = seriesRepository.selectedProviderId() ?: return@launch
                val category = mutableSeries.value.selectedCategory
                    ?: mutableSeries.value.categories.firstOrNull { it.id == "all" }
                    ?: return@launch
                val items = seriesRepository.seriesCards(providerId, category, "")
                mutableSeries.value = mutableSeries.value.copy(searchQuery = "", series = items)
            }
        } else {
            searchQueryFlow.value = query
        }
    }

    fun loadDetails(series: WatchioSeriesItem, targetEpisodeId: String? = null) {
        viewModelScope.launch {
            val currentAutoResume = mutableDetails.value.autoResumeEnabled
            mutableDetails.value = SeriesDetailsUiState(loading = true, autoResumeEnabled = currentAutoResume, targetEpisodeId = targetEpisodeId)
            val details = seriesRepository.details(series)
            val targetEpisode = targetEpisodeId?.let { id -> details.episodes.firstOrNull { it.episodeId == id } }
            val selected = targetEpisode?.seasonNumber
                ?: details.seasons.firstOrNull { it.seasonNumber == 1 }?.seasonNumber
                ?: details.seasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                ?: details.seasons.firstOrNull()?.seasonNumber
            selectedEpisode = targetEpisode
            mutableDetails.value = SeriesDetailsUiState(
                loading = false,
                details = details,
                selectedSeasonNumber = selected,
                targetEpisodeId = targetEpisodeId,
                autoResumeEnabled = currentAutoResume,
            )
        }
    }

    fun loadDetails(seriesId: String, targetEpisodeId: String? = null) {
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
            loadDetails(item, targetEpisodeId)
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
        mutableDetails.value = mutableDetails.value.copy(targetEpisodeId = episode.episodeId)
        viewModelScope.launch {
            val request = seriesRepository.playback(episode, resume)
            if (request.startPositionMs > 0L) {
                initialResumeStartMs = request.startPositionMs
                initialResumePending = true
            } else {
                initialResumeStartMs = 0L
                initialResumePending = false
            }
            playerManager.load(PlaybackMedia(request.url, episode.title, request.headers, request.startPositionMs, isLive = false))
            startProgressSave(episode)
        }
    }

    fun playTopLevel(resume: Boolean = true) {
        val details = mutableDetails.value.details ?: return
        val targetEpId = mutableDetails.value.targetEpisodeId
        val targetEpisode = targetEpId?.let { id -> details.episodes.firstOrNull { it.episodeId == id } }
        val resumeEpisode = targetEpisode ?: details.episodes.firstOrNull { seriesRepository.shouldResume(it.resumePositionMs, it.resumeDurationMs) }
        val target = if (resume) {
            if (mutableDetails.value.autoResumeEnabled) resumeEpisode ?: details.episodes.firstOrNull() else details.episodes.firstOrNull()
        } else {
            resumeEpisode ?: details.series.lastEpisodeId?.let { id -> details.episodes.firstOrNull { it.episodeId == id } } ?: details.episodes.firstOrNull()
        } ?: return
        playEpisode(target, resume)
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
                selectedEpisode?.let { startProgressSave(it) }
            }
        }
    }

    fun pauseForBackground() {
        saveProgress()
        progressJob?.cancel()
        playerManager.pause()
    }

    val currentEpisode: WatchioEpisodeItem?
        get() = selectedEpisode ?: seriesRepository.currentActiveEpisode()

    private fun currentSeasonEpisodes(): List<WatchioEpisodeItem> {
        val details = mutableDetails.value.details ?: return emptyList()
        val seasonNum = mutableDetails.value.selectedSeasonNumber
        val seasonEpisodes = if (seasonNum != null) {
            details.episodes.filter { it.seasonNumber == seasonNum }
        } else details.episodes
        return seasonEpisodes.ifEmpty { details.episodes }
    }

    fun hasPreviousEpisode(): Boolean {
        val episodes = currentSeasonEpisodes()
        val current = currentEpisode ?: return false
        val idx = episodes.indexOfFirst { it.episodeId == current.episodeId }
        return idx > 0
    }

    fun hasNextEpisode(): Boolean {
        val episodes = currentSeasonEpisodes()
        val current = currentEpisode ?: return false
        val idx = episodes.indexOfFirst { it.episodeId == current.episodeId }
        return idx >= 0 && idx < episodes.size - 1
    }

    fun playPreviousEpisode() {
        val episodes = currentSeasonEpisodes()
        val current = currentEpisode ?: return
        val idx = episodes.indexOfFirst { it.episodeId == current.episodeId }
        if (idx > 0) {
            playEpisode(episodes[idx - 1], resume = false)
        }
    }

    fun playNextEpisode() {
        val episodes = currentSeasonEpisodes()
        val current = currentEpisode ?: return
        val idx = episodes.indexOfFirst { it.episodeId == current.episodeId }
        if (idx >= 0 && idx < episodes.size - 1) {
            playEpisode(episodes[idx + 1], resume = false)
        }
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
                if (playerManager.state.value is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Playing) {
                    saveProgress()
                }
            }
        }
    }

    private fun saveProgress(forcedPosition: Long? = null, forcedDuration: Long? = null) {
        val episode = selectedEpisode ?: seriesRepository.currentActiveEpisode() ?: return
        val snapshot = playerManager.snapshot()
        val rawPosition = forcedPosition ?: snapshot.positionMs
        val rawDuration = forcedDuration ?: snapshot.durationMs ?: episode.resumeDurationMs

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
                    providerId = episode.providerId,
                    contentType = ContentType.Episode,
                    contentId = episode.seriesId,
                    subContentId = episode.episodeId,
                    title = episode.title,
                    imageUrl = episode.imageUrl,
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
