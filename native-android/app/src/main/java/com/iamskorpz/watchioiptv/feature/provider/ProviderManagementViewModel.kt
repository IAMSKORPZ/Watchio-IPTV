package com.iamskorpz.watchioiptv.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshCoordinator
import com.iamskorpz.watchioiptv.data.refresh.RefreshTrigger
import com.iamskorpz.watchioiptv.data.refresh.getOrThrow
import com.iamskorpz.watchioiptv.data.xtream.AccountBlockReason
import com.iamskorpz.watchioiptv.data.xtream.AccountValidationResult
import com.iamskorpz.watchioiptv.data.xtream.EndpointAttemptFailure
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointManager
import com.iamskorpz.watchioiptv.data.xtream.ProviderHealthState
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.model.WatchioProvider
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.feature.home.managedServerLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderManagementUiState(
    val providers: List<ProviderRowUiState> = emptyList(),
    val selectedProviderId: ProviderId? = null,
    val refreshingProviderId: ProviderId? = null,
    val serverChoices: List<ManagedServerChoice> = emptyList(),
    val providerHealth: ProviderHealthState = ProviderHealthState(),
    val message: String? = null,
)

data class ManagedServerChoice(val id: String, val name: String, val active: Boolean)

data class ProviderRowUiState(
    val provider: WatchioProvider,
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val refreshState: String,
    val accountState: ProviderAccountState = ProviderAccountState.NotChecked,
) {
    val typeLabel: String = when (provider.type) {
        ProviderType.Xtream -> "Xtream"
        ProviderType.M3uUrl -> "M3U URL"
        ProviderType.M3uFile -> "Local M3U"
    }
}

enum class ProviderAccountState { NotChecked, Verified, Expired, Disabled, Revoked, Rejected, Unverified }

sealed interface ProviderRemovalOutcome {
    data object InactiveProviderRemoved : ProviderRemovalOutcome
    data class ActiveProviderSelected(val providerId: ProviderId) : ProviderRemovalOutcome
    data object DownloadsOnly : ProviderRemovalOutcome
    data object LoginRequired : ProviderRemovalOutcome
}

internal suspend fun removeProviderAndResolve(
    providerId: ProviderId,
    providerRepository: ProviderRepository,
    settingsRepository: SettingsRepository,
    validateAccount: suspend (ProviderId) -> AccountValidationResult,
    hasPlayableDownloads: suspend () -> Boolean,
    onActiveProviderRemoval: suspend () -> Unit,
): ProviderRemovalOutcome {
    val activeProviderId = settingsRepository.selectedProviderId.first()
    val removingActiveProvider = activeProviderId == providerId
    if (removingActiveProvider) settingsRepository.setSelectedProviderId(null)
    try {
        if (removingActiveProvider) onActiveProviderRemoval()
        providerRepository.deleteProvider(providerId)
    } catch (failure: Throwable) {
        if (removingActiveProvider) settingsRepository.setSelectedProviderId(activeProviderId)
        throw failure
    }
    if (!removingActiveProvider) return ProviderRemovalOutcome.InactiveProviderRemoved

    val next = providerRepository.getProviders()
        .asSequence()
        .filter { it.enabled && it.type == ProviderType.Xtream }
        .map { it.id }
        .firstOrNull { runCatching { validateAccount(it) }.getOrNull() == AccountValidationResult.Active }
    return if (next != null) {
        settingsRepository.setSelectedProviderId(next)
        ProviderRemovalOutcome.ActiveProviderSelected(next)
    } else if (runCatching { hasPlayableDownloads() }.getOrDefault(false)) {
        ProviderRemovalOutcome.DownloadsOnly
    } else {
        ProviderRemovalOutcome.LoginRequired
    }
}

class ProviderManagementViewModel(
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val xtreamRepository: XtreamRepository,
    private val m3uRepository: M3uRepository,
    private val endpointManager: WatchioEndpointManager,
    private val catalogRefreshCoordinator: CatalogRefreshCoordinator? = null,
    private val validateAccount: suspend (ProviderId) -> AccountValidationResult = xtreamRepository::validateAccount,
    private val hasPlayableDownloads: suspend () -> Boolean = { false },
    private val stopPlayback: () -> Unit = {},
    private val invalidateProviderState: (ProviderId) -> Unit = {},
) : ViewModel() {
    private val status = MutableStateFlow(ProviderManagementUiState())
    private var refreshJob: Job? = null
    private val accountStates = MutableStateFlow<Map<ProviderId, ProviderAccountState>>(emptyMap())
    private val mutableRemovalOutcomes = MutableSharedFlow<ProviderRemovalOutcome>(extraBufferCapacity = 1)
    val removalOutcomes: SharedFlow<ProviderRemovalOutcome> = mutableRemovalOutcomes.asSharedFlow()

    private val endpointState = combine(
        endpointManager.configuredEndpoints,
        endpointManager.activeEndpoint,
        endpointManager.health,
    ) { endpoints, activeEndpoint, health -> EndpointState(endpoints, activeEndpoint?.url, health) }

    val state: StateFlow<ProviderManagementUiState> = combine(
        providerRepository.observeProviders(),
        settingsRepository.selectedProviderId,
        status,
        endpointState,
        accountStates,
    ) { providers, selectedProviderId, current, endpoints, accounts ->
        val rows = providers.map { provider ->
            val counts = counts(provider)
            ProviderRowUiState(
                provider = provider,
                liveCount = counts.live,
                movieCount = counts.movies,
                seriesCount = counts.series,
                refreshState = if (current.refreshingProviderId == provider.id) "Refreshing..." else provider.lastRefreshAtEpochMs?.let { "Updated" } ?: "Idle",
                accountState = accounts[provider.id] ?: ProviderAccountState.NotChecked,
            )
        }
        current.copy(
            providers = rows,
            selectedProviderId = selectedProviderId,
            serverChoices = endpoints.endpoints.map { ManagedServerChoice(it.id, managedServerLabel(it.id), it.url == endpoints.activeUrl) },
            providerHealth = endpoints.health,
            message = current.message,
        )
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
            val provider = providerRepository.getProvider(providerId)
            val result = runCatching {
                provider ?: error("Provider not found.")
                val coordinator = catalogRefreshCoordinator
                if (coordinator != null) {
                    coordinator.refreshProvider(providerId, RefreshTrigger.Manual).getOrThrow()
                } else {
                    when (provider.type) {
                        ProviderType.Xtream -> xtreamRepository.refreshProvider(providerId)
                        ProviderType.M3uUrl, ProviderType.M3uFile -> m3uRepository.refresh(providerId.value)
                    }
                }
            }
            if (provider?.type == ProviderType.Xtream) {
                accountStates.value = accountStates.value + (
                    providerId to when (val failure = result.exceptionOrNull()) {
                        null -> ProviderAccountState.Verified
                        is EndpointAttemptFailure.Account -> failure.toProviderAccountState()
                        else -> ProviderAccountState.Unverified
                    }
                )
            }
            status.value = status.value.copy(
                refreshingProviderId = null,
                message = if (result.isSuccess) "Updated" else "Refresh failed. Existing library preserved.",
            )
        }
    }

    fun delete(providerId: ProviderId) {
        viewModelScope.launch {
            val result = runCatching {
                removeProviderAndResolve(
                    providerId = providerId,
                    providerRepository = providerRepository,
                    settingsRepository = settingsRepository,
                    validateAccount = validateAccount,
                    hasPlayableDownloads = hasPlayableDownloads,
                    onActiveProviderRemoval = {
                        stopPlayback()
                        refreshJob?.cancelAndJoin()
                        catalogRefreshCoordinator?.cancelProviderRefreshes(providerId)
                        invalidateProviderState(providerId)
                    },
                )
            }
            result.onSuccess { outcome ->
                status.value = status.value.copy(
                    message = if (outcome == ProviderRemovalOutcome.InactiveProviderRemoved) "Provider removed" else null,
                )
                mutableRemovalOutcomes.emit(outcome)
            }.onFailure {
                status.value = status.value.copy(message = "Provider could not be removed.")
            }
        }
    }

    init {
        viewModelScope.launch { runCatching { endpointManager.refreshConfig() } }
    }

    private suspend fun counts(provider: WatchioProvider): Counts = when (provider.type) {
        ProviderType.Xtream -> xtreamRepository.counts(provider.id).let { Counts(it.liveCount, it.movieCount, it.seriesCount) }
        ProviderType.M3uUrl, ProviderType.M3uFile -> m3uRepository.counts(provider.id.value).let { Counts(it.liveCount, it.movieCount, it.seriesCount) }
    }

    private data class Counts(val live: Int, val movies: Int, val series: Int)
    private data class EndpointState(
        val endpoints: List<com.iamskorpz.watchioiptv.data.xtream.WatchioEndpoint>,
        val activeUrl: String?,
        val health: ProviderHealthState,
    )
}

internal fun AccountValidationResult.Blocked.toProviderAccountState(): ProviderAccountState = when (reason) {
    AccountBlockReason.Expired -> ProviderAccountState.Expired
    AccountBlockReason.Disabled -> ProviderAccountState.Disabled
    AccountBlockReason.Revoked -> ProviderAccountState.Revoked
    AccountBlockReason.InvalidCredentials -> ProviderAccountState.Rejected
}

internal fun EndpointAttemptFailure.Account.toProviderAccountState(): ProviderAccountState = when (reason) {
    AccountBlockReason.Expired -> ProviderAccountState.Expired
    AccountBlockReason.Disabled -> ProviderAccountState.Disabled
    AccountBlockReason.Revoked -> ProviderAccountState.Revoked
    AccountBlockReason.InvalidCredentials -> ProviderAccountState.Rejected
}
