package com.watchioiptv.nativeapp.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.live.LiveTvCategory
import com.watchioiptv.nativeapp.data.live.LiveTvCategoryKind
import com.watchioiptv.nativeapp.data.live.LiveTvChannel
import com.watchioiptv.nativeapp.data.live.LiveTvNowNext
import com.watchioiptv.nativeapp.data.live.LiveTvRepository
import com.watchioiptv.nativeapp.data.epg.EpgRefreshCoordinator
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.repository.FavoriteItem
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryItem
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.LiveTvBrowsingState
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveTvUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val categories: List<LiveTvCategory> = emptyList(),
    val selectedCategory: LiveTvCategory? = null,
    val channels: List<LiveTvChannel> = emptyList(),
    val selectedChannel: LiveTvChannel? = null,
    val initialScrollIndex: Int = 0,
    val nowNext: LiveTvNowNext = LiveTvNowNext(null, null, 0f),
    val categorySearchQuery: String = "",
    val liveSearchQuery: String = "",
    val epgRefreshing: Boolean = false,
    val epgRefreshMessage: String? = null,
)

class LiveTvViewModel(
    private val liveTvRepository: LiveTvRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val epgRefreshCoordinator: EpgRefreshCoordinator,
    private val playerManager: WatchioPlayerManager,
    private val clock: WatchioClock,
) : ViewModel() {
    private val mutableUi = MutableStateFlow(LiveTvUiState())
    private var playbackJob: Job? = null
    private var epgJob: Job? = null

    val uiState: StateFlow<LiveTvUiState> = mutableUi.asStateFlow()
    val playerState: StateFlow<WatchioPlayerState> = playerManager.state
    val combinedState: StateFlow<Pair<LiveTvUiState, WatchioPlayerState>> =
        uiState.combine(playerState) { ui, player -> ui to player }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutableUi.value to playerManager.state.value)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            mutableUi.value = LiveTvUiState(loading = true)
            val providerId = liveTvRepository.selectedProviderId()
            if (providerId == null) {
                mutableUi.value = LiveTvUiState(loading = false, errorMessage = "Add a provider first.")
                return@launch
            }
            val categories = liveTvRepository.categories(providerId)
            if (categories.isEmpty()) {
                mutableUi.value = LiveTvUiState(loading = false, categories = emptyList())
                return@launch
            }
            val settings = settingsRepository.playerSettings.first()
            val savedState = if (settings.rememberLastLiveChannel) {
                settingsRepository.observeLiveBrowsingState(providerId).first()
            } else {
                LiveTvBrowsingState()
            }

            // Category restoration priority:
            // 1. Stable ID match
            // 2. Name match (case-insensitive)
            // 3. Default first category (All Channels)
            val targetCategory = savedState.categoryId?.let { id ->
                categories.firstOrNull { it.id == id || it.sourceCategoryId == id }
            } ?: savedState.categoryName?.let { name ->
                categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
            } ?: categories.first()

            val channels = liveTvRepository.channels(providerId, targetCategory)

            // Channel restoration priority:
            // 1. Stable ID match
            // 2. Name match (case-insensitive)
            // 3. Saved list index fallback
            // 4. Default first channel in category
            val targetChannel = savedState.channelId?.let { id ->
                channels.firstOrNull { it.id == id }
            } ?: savedState.channelName?.let { name ->
                channels.firstOrNull { it.name.equals(name, ignoreCase = true) }
            } ?: savedState.channelIndex?.let { index ->
                if (index in channels.indices) channels[index] else null
            } ?: channels.firstOrNull()

            val targetIndex = if (targetChannel != null) {
                channels.indexOfFirst { it.id == targetChannel.id }.coerceAtLeast(0)
            } else {
                savedState.scrollIndex ?: 0
            }

            mutableUi.value = LiveTvUiState(
                loading = false,
                categories = categories,
                selectedCategory = targetCategory,
                channels = channels,
                selectedChannel = targetChannel,
                initialScrollIndex = targetIndex,
            )

            if (targetChannel != null) {
                updateNowNext(targetChannel)
                if (settings.autoPlayLiveChannel) {
                    selectChannel(targetChannel)
                }
            }
        }
    }

    fun selectCategory(category: LiveTvCategory) {
        viewModelScope.launch {
            val providerId = liveTvRepository.selectedProviderId() ?: return@launch
            val channels = liveTvRepository.channels(providerId, category)
            val currentSelected = mutableUi.value.selectedChannel
            val newSelected = currentSelected?.takeIf { sel -> channels.any { it.id == sel.id } }
                ?: channels.firstOrNull()
            val newIndex = if (newSelected != null) {
                channels.indexOfFirst { it.id == newSelected.id }.coerceAtLeast(0)
            } else 0

            mutableUi.value = mutableUi.value.copy(
                selectedCategory = category,
                channels = channels,
                selectedChannel = newSelected,
                initialScrollIndex = newIndex,
                nowNext = LiveTvNowNext(null, null, 0f),
            )
            newSelected?.let { updateNowNext(it) }

            persistBrowsingState(
                providerId = providerId,
                category = category,
                channel = newSelected,
                channelIndex = newIndex,
            )
        }
    }

    fun updateCategorySearch(query: String) {
        mutableUi.value = mutableUi.value.copy(categorySearchQuery = query)
    }

    fun updateLiveSearch(query: String) {
        viewModelScope.launch {
            val providerId = liveTvRepository.selectedProviderId() ?: return@launch
            val allCategory = mutableUi.value.categories.firstOrNull { it.id == "all" }
            val channels = if (query.isBlank()) {
                val category = mutableUi.value.selectedCategory ?: allCategory
                category?.let { liveTvRepository.channels(providerId, it) }.orEmpty()
            } else {
                allCategory?.let { liveTvRepository.channels(providerId, it) }
                    .orEmpty()
                    .filter { it.name.contains(query, ignoreCase = true) }
            }
            mutableUi.value = mutableUi.value.copy(
                liveSearchQuery = query,
                channels = channels,
                selectedChannel = channels.firstOrNull { it.id == mutableUi.value.selectedChannel?.id } ?: mutableUi.value.selectedChannel,
            )
        }
    }

    fun selectChannelById(channelId: String) {
        viewModelScope.launch {
            val providerId = liveTvRepository.selectedProviderId() ?: return@launch
            val allCategory = mutableUi.value.categories.firstOrNull { it.id == "all" }
                ?: LiveTvCategory("all", "ALL CHANNELS", LiveTvCategoryKind.All)
            val allChannels = liveTvRepository.channels(providerId, allCategory)
            val target = allChannels.firstOrNull { it.id == channelId } ?: return@launch
            selectChannel(target)
        }
    }

    fun selectChannel(channel: LiveTvChannel) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val currentChannels = mutableUi.value.channels
            val index = currentChannels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
            mutableUi.value = mutableUi.value.copy(
                selectedChannel = channel,
                initialScrollIndex = index,
                errorMessage = null,
            )
            updateNowNext(channel)
            val playback = liveTvRepository.playback(channel)
            playerManager.load(
                PlaybackMedia(
                    url = playback.url,
                    title = channel.name,
                    headers = playback.headers,
                    isLive = true,
                ),
            )
            historyRepository.upsert(
                HistoryItem(
                    providerId = channel.providerId,
                    contentType = ContentType.Live,
                    contentId = channel.id,
                    title = channel.name,
                    imageUrl = channel.logoUrl,
                    positionMs = null,
                    durationMs = null,
                    lastWatchedAtEpochMs = clock.nowEpochMs(),
                ),
            )
            mutableUi.value.selectedCategory?.let { category ->
                persistBrowsingState(
                    providerId = channel.providerId,
                    category = category,
                    channel = channel,
                    channelIndex = index,
                )
            }
            startEpgTicker(channel)
        }
    }

    private suspend fun persistBrowsingState(
        providerId: ProviderId,
        category: LiveTvCategory,
        channel: LiveTvChannel?,
        channelIndex: Int,
    ) {
        if (!settingsRepository.playerSettings.first().rememberLastLiveChannel) return
        settingsRepository.saveLiveBrowsingState(
            providerId = providerId,
            state = LiveTvBrowsingState(
                categoryId = category.id,
                categoryName = category.name,
                channelId = channel?.id,
                channelName = channel?.name,
                channelIndex = channelIndex,
                scrollIndex = channelIndex,
            ),
        )
    }

    fun toggleFavorite(channel: LiveTvChannel? = mutableUi.value.selectedChannel) {
        channel ?: return
        viewModelScope.launch {
            val isFavorite = favoritesRepository.toggle(
                FavoriteItem(
                    providerId = channel.providerId,
                    contentType = ContentType.Live,
                    contentId = channel.id,
                    title = channel.name,
                    imageUrl = channel.logoUrl,
                    createdAtEpochMs = clock.nowEpochMs(),
                ),
            )
            val updated = channel.copy(isFavorite = isFavorite)
            mutableUi.value = mutableUi.value.copy(
                selectedChannel = updated,
                channels = mutableUi.value.channels.map { if (it.id == updated.id) updated else it },
            )
        }
    }

    fun retry() = playerManager.retry()

    fun refreshEpg() {
        val providerId = mutableUi.value.selectedChannel?.providerId ?: return
        if (mutableUi.value.epgRefreshing) return
        viewModelScope.launch {
            mutableUi.value = mutableUi.value.copy(epgRefreshing = true, epgRefreshMessage = null)
            val result = runCatching { epgRefreshCoordinator.refreshProvider(providerId.value) }
            mutableUi.value = mutableUi.value.copy(
                epgRefreshing = false,
                epgRefreshMessage = if (result.isSuccess) "EPG refreshed." else "EPG refresh failed. Cached guide retained.",
            )
            mutableUi.value.selectedChannel?.let { updateNowNext(it) }
        }
    }
    fun playPause() {
        when (playerManager.state.value) {
            is WatchioPlayerState.Playing -> playerManager.pause()
            else -> playerManager.play()
        }
    }

    fun hasPreviousChannel(): Boolean = mutableUi.value.channels.size > 1

    fun hasNextChannel(): Boolean = mutableUi.value.channels.size > 1

    fun selectPreviousChannel() {
        val currentChannels = mutableUi.value.channels
        val currentSelected = mutableUi.value.selectedChannel ?: return
        val currentIndex = currentChannels.indexOfFirst { it.id == currentSelected.id }
        if (currentIndex > 0) {
            selectChannel(currentChannels[currentIndex - 1])
        } else if (currentChannels.isNotEmpty()) {
            selectChannel(currentChannels.last())
        }
    }

    fun selectNextChannel() {
        val currentChannels = mutableUi.value.channels
        val currentSelected = mutableUi.value.selectedChannel ?: return
        val currentIndex = currentChannels.indexOfFirst { it.id == currentSelected.id }
        if (currentIndex >= 0 && currentIndex < currentChannels.size - 1) {
            selectChannel(currentChannels[currentIndex + 1])
        } else if (currentChannels.isNotEmpty()) {
            selectChannel(currentChannels.first())
        }
    }

    fun leaveLiveTv() {
        playbackJob?.cancel()
        epgJob?.cancel()
        playerManager.stop()
    }

    fun pauseForBackground() {
        playbackJob?.cancel()
        playerManager.stop()
    }

    private fun startEpgTicker(channel: LiveTvChannel) {
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            while (true) {
                updateNowNext(channel)
                delay(60_000L)
            }
        }
    }

    private suspend fun updateNowNext(channel: LiveTvChannel) {
        mutableUi.value = mutableUi.value.copy(nowNext = liveTvRepository.nowNext(channel, clock.nowEpochMs()))
    }

    override fun onCleared() {
        leaveLiveTv()
        super.onCleared()
    }
}
