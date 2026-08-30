package com.watchioiptv.nativeapp.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.PlayerReliability
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.series.NextEpisodeState
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
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
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
    private val mutableNextEpisodeState = MutableStateFlow<NextEpisodeState>(NextEpisodeState.None)
    private val searchQueryFlow = MutableStateFlow("")
    private var categoryJob: Job? = null
    private var selectedEpisode: WatchioEpisodeItem? = null
    private var progressJob: Job? = null
    private var countdownJob: Job? = null
    private var initialResumePending = false
    private var initialResumeStartMs = 0L
    private var cancelledForThisEpisode = false
    private var autoPlayNextEpisodeEnabled = true
    private val isEpisodeTransitionInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastSavedCompletedEpisodeId: String? = null
    private var lastProgressSaveEpochMs = 0L

    val seriesState: StateFlow<SeriesUiState> = mutableSeries.asStateFlow()
    val detailsState: StateFlow<SeriesDetailsUiState> = mutableDetails.asStateFlow()
    val nextEpisodeState: StateFlow<NextEpisodeState> = mutableNextEpisodeState.asStateFlow()
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
                    val items = seriesRepository.seriesPage(providerId, category, 0, SeriesRepository.PAGE_SIZE, query)
                    mutableSeries.value = mutableSeries.value.copy(searchQuery = query, series = items, hasMore = query.isBlank() && items.size == SeriesRepository.PAGE_SIZE)
                }
        }
        viewModelScope.launch {
            settingsRepository.playerSettings.collect { settings ->
                mutableDetails.value = mutableDetails.value.copy(autoResumeEnabled = settings.autoResume)
                val prevAutoplay = autoPlayNextEpisodeEnabled
                autoPlayNextEpisodeEnabled = settings.autoPlayNextEpisode
                if (!settings.autoPlayNextEpisode && prevAutoplay) {
                    if (mutableNextEpisodeState.value is NextEpisodeState.Countdown) {
                        countdownJob?.cancel()
                        countdownJob = null
                        val nextEp = resolveNextEpisode()
                        val seriesTitle = mutableDetails.value.details?.title ?: "TV Show"
                        mutableNextEpisodeState.value = if (nextEp != null) {
                            NextEpisodeState.Ready(nextEp, seriesTitle)
                        } else NextEpisodeState.None
                    }
                }
            }
        }
        viewModelScope.launch {
            playerManager.state.collect { state ->
                when (state) {
                    is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Ended -> {
                        val snapshot = playerManager.snapshot()
                        val episode = selectedEpisode ?: seriesRepository.currentActiveEpisode()
                        if (episode != null) {
                            val finalDur = snapshot.durationMs ?: episode.resumeDurationMs ?: snapshot.positionMs
                            saveCompletedProgress(episode, finalDur)
                            if (!isEpisodeTransitionInProgress.get() && !cancelledForThisEpisode) {
                                val nextEp = resolveNextEpisode()
                                if (nextEp != null) {
                                    val seriesTitle = mutableDetails.value.details?.title ?: "TV Show"
                                    if (autoPlayNextEpisodeEnabled) {
                                        if (countdownJob?.isActive != true) {
                                            performEpisodeTransition(nextEp)
                                        }
                                    } else {
                                        mutableNextEpisodeState.value = NextEpisodeState.Ready(nextEp, seriesTitle)
                                    }
                                }
                            }
                        }
                        progressJob?.cancel()
                    }
                    is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Playing -> {
                        if (progressJob?.isActive != true) {
                            (selectedEpisode ?: seriesRepository.currentActiveEpisode())?.let { startPlaybackLoop(it) }
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
            val items = selected?.let { seriesRepository.seriesPage(providerId, it, 0, SeriesRepository.PAGE_SIZE) }.orEmpty()
            mutableSeries.value = SeriesUiState(
                loading = false,
                categories = categories,
                selectedCategory = selected,
                series = items,
                hasMore = items.size == SeriesRepository.PAGE_SIZE,
            )
        }
    }

    fun selectCategory(category: SeriesCategory) {
        categoryJob?.cancel()
        val currentQuery = mutableSeries.value.searchQuery
        mutableSeries.value = mutableSeries.value.copy(selectedCategory = category, series = emptyList(), loadingMore = false, hasMore = false)
        categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val providerId = seriesRepository.selectedProviderId() ?: return@launch
            val items = seriesRepository.seriesPage(providerId, category, 0, SeriesRepository.PAGE_SIZE, currentQuery)
            mutableSeries.value = mutableSeries.value.copy(series = items, hasMore = currentQuery.isBlank() && items.size == SeriesRepository.PAGE_SIZE)
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
                val items = seriesRepository.seriesPage(providerId, category, 0, SeriesRepository.PAGE_SIZE)
                mutableSeries.value = mutableSeries.value.copy(searchQuery = "", series = items, hasMore = items.size == SeriesRepository.PAGE_SIZE)
            }
        } else {
            searchQueryFlow.value = query
        }
    }

    fun loadMore() {
        val state = mutableSeries.value
        if (state.loadingMore || !state.hasMore || state.searchQuery.isNotBlank()) return
        val category = state.selectedCategory ?: return
        mutableSeries.value = state.copy(loadingMore = true)
        categoryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val providerId = seriesRepository.selectedProviderId() ?: run {
                mutableSeries.value = mutableSeries.value.copy(loadingMore = false, hasMore = false)
                return@launch
            }
            val page = seriesRepository.seriesPage(providerId, category, state.series.size, SeriesRepository.PAGE_SIZE)
            val current = mutableSeries.value
            if (current.selectedCategory == category) {
                mutableSeries.value = current.copy(
                    series = (current.series + page).distinctBy { it.series.id },
                    loadingMore = false,
                    hasMore = page.size == SeriesRepository.PAGE_SIZE,
                )
            }
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
        cancelledForThisEpisode = false
        mutableNextEpisodeState.value = NextEpisodeState.None
        countdownJob?.cancel()
        countdownJob = null
        isEpisodeTransitionInProgress.set(false)
        lastSavedCompletedEpisodeId = null
        seriesRepository.markActiveEpisode(episode)
        mutableDetails.value = mutableDetails.value.copy(targetEpisodeId = episode.episodeId, selectedSeasonNumber = episode.seasonNumber)
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
            startPlaybackLoop(episode)
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
        resetNextEpisodeCountdown()
        cancelledForThisEpisode = false
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
                selectedEpisode?.let { startPlaybackLoop(it) }
            }
        }
    }

    fun pauseForBackground() {
        resetNextEpisodeCountdown()
        saveProgress()
        progressJob?.cancel()
        playerManager.pause()
    }

    val currentEpisode: WatchioEpisodeItem?
        get() = selectedEpisode ?: seriesRepository.currentActiveEpisode()

    private fun canonicalEpisodes(): List<WatchioEpisodeItem> {
        val details = mutableDetails.value.details ?: return emptyList()
        return details.episodes
            .distinctBy { it.episodeId }
            .sortedWith(
                compareBy<WatchioEpisodeItem> { ep ->
                    if (ep.seasonNumber > 0) ep.seasonNumber else Int.MAX_VALUE - 1000 + ep.seasonNumber
                }.thenBy { ep ->
                    if (ep.episodeNumber > 0) ep.episodeNumber else Int.MAX_VALUE - 1000 + ep.episodeNumber
                }
            )
    }

    fun resolveNextEpisode(): WatchioEpisodeItem? {
        val current = currentEpisode ?: return null
        val episodes = canonicalEpisodes()
        val idx = episodes.indexOfFirst { it.episodeId == current.episodeId }
        if (idx < 0 || idx >= episodes.size - 1) return null
        val next = episodes[idx + 1]
        return if (next.episodeId != current.episodeId) next else null
    }

    fun resolvePreviousEpisode(): WatchioEpisodeItem? {
        val current = currentEpisode ?: return null
        val episodes = canonicalEpisodes()
        val idx = episodes.indexOfFirst { it.episodeId == current.episodeId }
        if (idx <= 0) return null
        val prev = episodes[idx - 1]
        return if (prev.episodeId != current.episodeId) prev else null
    }

    fun hasPreviousEpisode(): Boolean = resolvePreviousEpisode() != null

    fun hasNextEpisode(): Boolean = resolveNextEpisode() != null

    fun playPreviousEpisode() {
        val prev = resolvePreviousEpisode() ?: return
        resetNextEpisodeCountdown()
        cancelledForThisEpisode = false
        playEpisode(prev, resume = false)
    }

    fun playNextEpisode() {
        val next = resolveNextEpisode() ?: return
        resetNextEpisodeCountdown()
        performEpisodeTransition(next)
    }

    fun dismissNextEpisodeForCurrentEpisode() {
        countdownJob?.cancel()
        countdownJob = null
        cancelledForThisEpisode = true
        mutableNextEpisodeState.value = NextEpisodeState.None
    }

    fun resetNextEpisodeCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        mutableNextEpisodeState.value = NextEpisodeState.None
    }

    fun stopPlayback() {
        resetNextEpisodeCountdown()
        saveProgress()
        progressJob?.cancel()
        playerManager.stop()
        seriesRepository.clearActiveEpisode()
    }

    private fun startPlaybackLoop(episode: WatchioEpisodeItem) {
        progressJob?.cancel()
        selectedEpisode = episode
        progressJob = viewModelScope.launch {
            while (true) {
                delay(500L)
                if (currentEpisode?.episodeId != episode.episodeId) break
                val playerVal = playerManager.state.value
                val snapshot = playerManager.snapshot()
                if (playerVal is com.watchioiptv.nativeapp.core.player.WatchioPlayerState.Playing) {
                    val pos = snapshot.positionMs
                    val dur = snapshot.durationMs ?: episode.resumeDurationMs ?: 0L
                    checkCountdownTrigger(episode, pos, dur)

                    if (System.currentTimeMillis() - lastProgressSaveEpochMs >= 15_000L) {
                        lastProgressSaveEpochMs = System.currentTimeMillis()
                        saveProgress()
                    }
                }
            }
        }
    }

    private fun checkCountdownTrigger(episode: WatchioEpisodeItem, positionMs: Long, durationMs: Long) {
        if (isEpisodeTransitionInProgress.get() || cancelledForThisEpisode) return
        if (durationMs <= 0L || positionMs < 0L) return

        val remainingMs = durationMs - positionMs
        val nextEp = resolveNextEpisode() ?: run {
            resetNextEpisodeCountdown()
            return
        }
        val seriesTitle = mutableDetails.value.details?.title ?: "TV Show"

        if (remainingMs in 1..15_000L) {
            if (!autoPlayNextEpisodeEnabled) {
                if (mutableNextEpisodeState.value !is NextEpisodeState.Ready) {
                    mutableNextEpisodeState.value = NextEpisodeState.Ready(
                        nextEpisode = nextEp,
                        seriesTitle = seriesTitle,
                    )
                }
                return
            }

            if (countdownJob?.isActive != true && mutableNextEpisodeState.value !is NextEpisodeState.Countdown) {
                startCountdown(episode, nextEp, seriesTitle, remainingMs)
            }
        } else if (remainingMs > 15_000L) {
            if (mutableNextEpisodeState.value !is NextEpisodeState.None) {
                resetNextEpisodeCountdown()
            }
        }
    }

    private fun startCountdown(
        currentEp: WatchioEpisodeItem,
        nextEp: WatchioEpisodeItem,
        seriesTitle: String,
        initialRemainingMs: Long,
    ) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            val initialSecs = kotlin.math.min(10, kotlin.math.ceil(initialRemainingMs / 1000.0).toInt()).coerceAtLeast(1)
            var secondsLeft = initialSecs
            mutableNextEpisodeState.value = NextEpisodeState.Countdown(
                nextEpisode = nextEp,
                secondsRemaining = secondsLeft,
                seriesTitle = seriesTitle,
            )

            while (secondsLeft > 0) {
                delay(1_000L)
                if (isEpisodeTransitionInProgress.get() || cancelledForThisEpisode) return@launch
                if (currentEpisode?.episodeId != currentEp.episodeId) return@launch
                if (!autoPlayNextEpisodeEnabled) {
                    mutableNextEpisodeState.value = NextEpisodeState.Ready(nextEp, seriesTitle)
                    return@launch
                }
                secondsLeft -= 1
                if (secondsLeft > 0) {
                    mutableNextEpisodeState.value = NextEpisodeState.Countdown(
                        nextEpisode = nextEp,
                        secondsRemaining = secondsLeft,
                        seriesTitle = seriesTitle,
                    )
                }
            }

            if (autoPlayNextEpisodeEnabled && !cancelledForThisEpisode) {
                performEpisodeTransition(nextEp)
            }
        }
    }

    private fun performEpisodeTransition(nextEpisode: WatchioEpisodeItem) {
        if (isEpisodeTransitionInProgress.getAndSet(true)) return

        val current = currentEpisode
        countdownJob?.cancel()
        countdownJob = null

        viewModelScope.launch {
            try {
                if (current != null) {
                    val snap = playerManager.snapshot()
                    val finalDur = snap.durationMs ?: current.resumeDurationMs ?: snap.positionMs
                    saveCompletedProgress(current, finalDur)
                }

                cancelledForThisEpisode = false
                mutableNextEpisodeState.value = NextEpisodeState.None
                selectedEpisode = nextEpisode
                seriesRepository.markActiveEpisode(nextEpisode)
                mutableDetails.value = mutableDetails.value.copy(
                    targetEpisodeId = nextEpisode.episodeId,
                    selectedSeasonNumber = nextEpisode.seasonNumber,
                )

                val request = seriesRepository.playback(nextEpisode, resume = false)
                initialResumeStartMs = 0L
                initialResumePending = false

                playerManager.load(
                    PlaybackMedia(
                        url = request.url,
                        title = nextEpisode.title,
                        headers = request.headers,
                        startPositionMs = 0L,
                        isLive = false,
                    )
                )

                startPlaybackLoop(nextEpisode)
            } finally {
                delay(300L)
                isEpisodeTransitionInProgress.set(false)
            }
        }
    }

    private suspend fun saveCompletedProgress(episode: WatchioEpisodeItem, durationMs: Long) {
        if (lastSavedCompletedEpisodeId == episode.episodeId) return
        lastSavedCompletedEpisodeId = episode.episodeId

        val validDuration = if (durationMs > 0L) durationMs else episode.resumeDurationMs ?: 600_000L
        historyRepository.upsert(
            HistoryItem(
                providerId = episode.providerId,
                contentType = ContentType.Episode,
                contentId = episode.seriesId,
                subContentId = episode.episodeId,
                title = episode.title,
                imageUrl = episode.imageUrl,
                positionMs = validDuration,
                durationMs = validDuration,
                lastWatchedAtEpochMs = clock.nowEpochMs(),
            ),
        )
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
