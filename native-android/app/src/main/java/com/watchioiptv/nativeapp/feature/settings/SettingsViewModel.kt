package com.watchioiptv.nativeapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.datastore.WatchioSettingsRepository
import com.watchioiptv.nativeapp.data.epg.EpgRefreshCoordinator
import com.watchioiptv.nativeapp.data.epg.EpgRefreshInterval
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import com.watchioiptv.nativeapp.ui.theme.WatchioThemeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val inputMode: InputMode = InputMode.Auto,
    val streamFormat: StreamFormat = StreamFormat.Auto,
    val selectedTheme: WatchioThemeState = WatchioThemeState(),
    val themes: List<WatchioThemeState> = WatchioThemeState.Available,
    val themeLabel: String = "Watchio Default",
    val epgAutoRefreshEnabled: Boolean = true,
    val epgRefreshInterval: EpgRefreshInterval = EpgRefreshInterval.Default,
    val playerSettings: PlayerSettings = PlayerSettings(),
    val lastSuccessfulEpgRefreshEpochMs: Long? = null,
    val epgRefreshing: Boolean = false,
    val epgRefreshMessage: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: WatchioSettingsRepository,
    private val epgRefreshCoordinator: EpgRefreshCoordinator,
) : ViewModel() {
    private val epgRefreshing = MutableStateFlow(false)
    private val epgRefreshMessage = MutableStateFlow<String?>(null)
    private val lastSuccessfulRefresh = MutableStateFlow<Long?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.inputMode,
        settingsRepository.streamFormat,
        settingsRepository.theme,
        settingsRepository.epgAutoRefreshEnabled,
        settingsRepository.epgRefreshInterval,
        settingsRepository.playerSettings,
        lastSuccessfulRefresh,
        epgRefreshing,
        epgRefreshMessage,
    ) { values ->
        val inputMode = values[0] as InputMode
        val streamFormat = values[1] as StreamFormat
        val theme = values[2] as WatchioThemeState
        SettingsUiState(
            inputMode = inputMode,
            streamFormat = streamFormat,
            selectedTheme = theme,
            themeLabel = theme.id.label,
            epgAutoRefreshEnabled = values[3] as Boolean,
            epgRefreshInterval = values[4] as EpgRefreshInterval,
            playerSettings = values[5] as PlayerSettings,
            lastSuccessfulEpgRefreshEpochMs = values[6] as Long?,
            epgRefreshing = values[7] as Boolean,
            epgRefreshMessage = values[8] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshLastSuccess()
    }

    fun setInputMode(inputMode: InputMode) {
        viewModelScope.launch { settingsRepository.setInputMode(inputMode) }
    }

    fun setStreamFormat(streamFormat: StreamFormat) {
        viewModelScope.launch { settingsRepository.setStreamFormat(streamFormat) }
    }

    fun setTheme(theme: WatchioThemeState) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setEpgAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEpgAutoRefreshEnabled(enabled) }
    }

    fun setEpgRefreshInterval(interval: EpgRefreshInterval) {
        viewModelScope.launch { settingsRepository.setEpgRefreshInterval(interval) }
    }

    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoResume(enabled) }
    }

    fun setAutoPlayLiveChannel(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoPlayLiveChannel(enabled) }
    }

    fun setRememberLastLiveChannel(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRememberLastLiveChannel(enabled) }
    }

    fun setShowPlayerControls(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowPlayerControls(enabled) }
    }

    fun setControlAutoHideDelay(delay: ControlAutoHideDelay) {
        viewModelScope.launch { settingsRepository.setControlAutoHideDelay(delay) }
    }

    fun setAutoRetryStreams(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoRetryStreams(enabled) }
    }

    fun setRetryAttempts(attempts: Int) {
        viewModelScope.launch { settingsRepository.setRetryAttempts(attempts) }
    }

    fun setVideoScalingMode(mode: VideoScalingMode) {
        viewModelScope.launch { settingsRepository.setVideoScalingMode(mode) }
    }

    fun refreshEpgNow() {
        viewModelScope.launch {
            epgRefreshing.value = true
            epgRefreshMessage.value = null
            val summary = runCatching { epgRefreshCoordinator.refreshAllEnabledProviders() }
            epgRefreshing.value = false
            epgRefreshMessage.value = summary.fold(
                onSuccess = {
                    refreshLastSuccess()
                    if (it.failedProviders == 0) {
                        "Guide refreshed."
                    } else {
                        "Refresh finished with failures. Saved guide retained."
                    }
                },
                onFailure = {
                    refreshLastSuccess()
                    "Refresh failed. Saved guide retained."
                },
            )
        }
    }

    private fun refreshLastSuccess() {
        viewModelScope.launch {
            val providerId = settingsRepository.selectedProviderId.first()?.value
            lastSuccessfulRefresh.value = epgRefreshCoordinator.latestSuccessForProvider(providerId)
        }
    }
}
