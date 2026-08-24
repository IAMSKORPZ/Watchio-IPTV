package com.watchioiptv.nativeapp.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.m3u.M3uRepository
import com.watchioiptv.nativeapp.data.xtream.XtreamRepository
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderManagementUiState(
    val providers: List<ProviderRowUiState> = emptyList(),
    val selectedProviderId: ProviderId? = null,
    val refreshingProviderId: ProviderId? = null,
    val message: String? = null,
)

data class ProviderRowUiState(
    val provider: WatchioProvider,
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val refreshState: String,
) {
    val typeLabel: String = when (provider.type) {
        ProviderType.Xtream -> "Xtream"
        ProviderType.M3uUrl -> "M3U URL"
        ProviderType.M3uFile -> "Local M3U"
    }
}

class ProviderManagementViewModel(
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val xtreamRepository: XtreamRepository,
    private val m3uRepository: M3uRepository,
) : ViewModel() {
    private val status = MutableStateFlow(ProviderManagementUiState())
    private var refreshJob: Job? = null

    val state: StateFlow<ProviderManagementUiState> = combine(
        providerRepository.observeProviders(),
        settingsRepository.selectedProviderId,
        status,
    ) { providers, selectedProviderId, current ->
        val rows = providers.map { provider ->
            val counts = counts(provider)
            ProviderRowUiState(
                provider = provider,
                liveCount = counts.live,
                movieCount = counts.movies,
                seriesCount = counts.series,
                refreshState = if (current.refreshingProviderId == provider.id) "Refreshing..." else provider.lastRefreshAtEpochMs?.let { "Updated" } ?: "Idle",
            )
        }
        current.copy(providers = rows, selectedProviderId = selectedProviderId, message = current.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderManagementUiState())

    fun select(providerId: ProviderId) {
        viewModelScope.launch {
            settingsRepository.setSelectedProviderId(providerId)
            status.value = status.value.copy(message = "Provider selected")
        }
    }

    fun refresh(providerId: ProviderId) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            status.value = status.value.copy(refreshingProviderId = providerId, message = "Refreshing...")
            val result = runCatching {
                val provider = providerRepository.getProvider(providerId) ?: error("Provider not found.")
                when (provider.type) {
                    ProviderType.Xtream -> xtreamRepository.refreshProvider(providerId)
                    ProviderType.M3uUrl, ProviderType.M3uFile -> m3uRepository.refresh(providerId.value)
                }
            }
            status.value = status.value.copy(
                refreshingProviderId = null,
                message = if (result.isSuccess) "Updated" else "Refresh failed. Existing library preserved.",
            )
        }
    }

    fun delete(providerId: ProviderId) {
        viewModelScope.launch {
            providerRepository.deleteProvider(providerId)
            val remaining = providerRepository.getProviders()
            val next = remaining.firstOrNull { it.enabled && it.type == ProviderType.Xtream }?.id
            settingsRepository.setSelectedProviderId(next)
            status.value = status.value.copy(message = if (next == null) "Provider removed. Add a provider to continue." else "Provider removed")
        }
    }

    private suspend fun counts(provider: WatchioProvider): Counts = when (provider.type) {
        ProviderType.Xtream -> xtreamRepository.counts(provider.id).let { Counts(it.liveCount, it.movieCount, it.seriesCount) }
        ProviderType.M3uUrl, ProviderType.M3uFile -> m3uRepository.counts(provider.id.value).let { Counts(it.liveCount, it.movieCount, it.seriesCount) }
    }

    private data class Counts(val live: Int, val movies: Int, val series: Int)
}
