package com.watchioiptv.nativeapp.feature.tvguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.data.live.LiveTvCategory
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.util.WatchioClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TvGuideUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val nowEpochMs: Long = 0L,
    val window: WatchioGuideWindow = TvGuideTimeline.defaultWindow(0L),
    val categories: List<LiveTvCategory> = emptyList(),
    val selectedCategory: LiveTvCategory? = null,
    val channels: List<WatchioGuideChannel> = emptyList(),
    val programmes: Map<String, List<WatchioGuideProgramme>> = emptyMap(),
    val selectedChannelId: String? = null,
    val selectedProgrammeId: String? = null,
    val details: ProgrammeDetails? = null,
    val hasProvider: Boolean = true,
    val hasEpgSource: Boolean = false,
    val epgChannelCount: Int = 0,
    val epgProgrammeCount: Int = 0,
)

class TvGuideViewModel(
    private val repository: TvGuideRepository,
    private val playerManager: WatchioPlayerManager,
    private val clock: WatchioClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TvGuideUiState(nowEpochMs = clock.nowEpochMs()))
    private var ticker: Job? = null
    private var loadJob: Job? = null
    private var selectedCategoryId: String? = null
    private val sourceDiscoveryAttempts = mutableSetOf<String>()
    private var activeProviderId: ProviderId? = null

    val state: StateFlow<TvGuideUiState> = mutableState.asStateFlow()

    init {
        observeProvider()
        startTicker()
    }

    fun jumpToNow() {
        val now = clock.nowEpochMs()
        load(activeProviderId, TvGuideTimeline.defaultWindow(now), now)
    }

    fun selectDay(day: LocalDate) {
        val now = clock.nowEpochMs()
        load(activeProviderId, TvGuideTimeline.windowForDay(day, now), now)
    }

    fun refreshEpg() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(refreshing = true, errorMessage = null, message = null)
            repository.refresh()
                .onSuccess { message ->
                    mutableState.value = mutableState.value.copy(refreshing = false, message = message)
                    load(activeProviderId, mutableState.value.window, clock.nowEpochMs())
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        refreshing = false,
                        loading = false,
                        errorMessage = "Could not refresh TV Guide. Existing guide data is still available.",
                    )
                }
        }
    }

    fun selectCategory(category: LiveTvCategory) {
        selectedCategoryId = category.id
        val now = clock.nowEpochMs()
        load(activeProviderId, mutableState.value.window, now)
    }

    fun selectProgramme(channel: WatchioGuideChannel, programme: WatchioGuideProgramme) {
        mutableState.value = mutableState.value.copy(
            selectedChannelId = channel.channelId,
            selectedProgrammeId = programme.programmeId,
            details = ProgrammeDetails(programme, channel),
        )
    }

    fun selectChannel(channel: WatchioGuideChannel) {
        mutableState.value = mutableState.value.copy(
            selectedChannelId = channel.channelId,
            selectedProgrammeId = null,
            details = ProgrammeDetails(
                programme = WatchioGuideProgramme(
                    programmeId = "no-info-${channel.channelId}",
                    channelId = channel.channelId,
                    epgChannelId = channel.epgChannelId.orEmpty(),
                    title = "No programme information",
                    description = null,
                    startUtcMs = mutableState.value.window.startUtcMs,
                    endUtcMs = mutableState.value.window.endUtcMs,
                    progress = 0f,
                    isLiveNow = false,
                ),
                channel = channel,
            ),
        )
    }

    fun closeDetails() {
        mutableState.value = mutableState.value.copy(details = null)
    }

    fun playLive(onStarted: () -> Unit) {
        val details = mutableState.value.details ?: return
        val now = clock.nowEpochMs()
        if (details.programme.programmeId.startsWith("no-info-") || details.programme.isLiveNow || details.programme.startUtcMs <= now && details.programme.endUtcMs > now) {
            viewModelScope.launch {
                playerManager.load(repository.playback(details.channel))
                onStarted()
            }
        }
    }

    private fun load(providerId: ProviderId?, window: WatchioGuideWindow, nowEpochMs: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (providerId == null) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    refreshing = false,
                    nowEpochMs = nowEpochMs,
                    window = window,
                    hasProvider = false,
                    categories = emptyList(),
                    selectedCategory = null,
                    channels = emptyList(),
                    programmes = emptyMap(),
                    hasEpgSource = false,
                    epgChannelCount = 0,
                    epgProgrammeCount = 0,
                )
                return@launch
            }
            val previous = mutableState.value
            mutableState.value = previous.copy(
                loading = previous.channels.isEmpty(),
                nowEpochMs = nowEpochMs,
                window = window,
            )
            try {
                val categories = repository.categories(providerId)
                val fallbackCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull()
                mutableState.value = mutableState.value.copy(
                    hasProvider = true,
                    categories = categories,
                    selectedCategory = fallbackCategory,
                )
                selectedCategoryId = fallbackCategory?.id

                val data = repository.guideForProvider(providerId, window, nowEpochMs, selectedCategoryId)
                selectedCategoryId = data.selectedCategory?.id
                val currentChannel = mutableState.value.selectedChannelId?.takeIf { id -> data.channels.any { it.channelId == id } }
                val selectedChannelId = currentChannel ?: data.channels.firstOrNull()?.channelId
                val selectedProgrammeId = mutableState.value.selectedProgrammeId
                    ?: selectedChannelId?.let { channelId -> data.programmes[channelId]?.firstOrNull { it.isLiveNow }?.programmeId }
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    hasProvider = true,
                    categories = data.categories,
                    selectedCategory = data.selectedCategory,
                    channels = data.channels,
                    programmes = data.programmes,
                    selectedChannelId = selectedChannelId,
                    selectedProgrammeId = selectedProgrammeId,
                    hasEpgSource = data.hasEpgSource,
                    epgChannelCount = data.epgChannelCount,
                    epgProgrammeCount = data.epgProgrammeCount,
                    errorMessage = null,
                )
                val providerId = data.providerId?.value
                if (providerId != null && shouldAttemptSourceRefresh(data) && sourceDiscoveryAttempts.add(providerId)) {
                    refreshEpg()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    errorMessage = "Could not load saved TV Guide.",
                )
            }
        }
    }

    private fun shouldAttemptSourceRefresh(data: TvGuideData): Boolean =
        !data.hasEpgSource || (data.epgChannelCount == 0 && data.epgProgrammeCount == 0)

    private fun observeProvider() {
        viewModelScope.launch {
            repository.observeSelectedProviderId()
                .distinctUntilChanged()
                .collect { providerId ->
                    activeProviderId = providerId
                    val now = clock.nowEpochMs()
                    load(providerId, TvGuideTimeline.defaultWindow(now), now)
                }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val now = clock.nowEpochMs()
                val state = mutableState.value
                load(activeProviderId, state.window, now)
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        loadJob?.cancel()
        super.onCleared()
    }
}
