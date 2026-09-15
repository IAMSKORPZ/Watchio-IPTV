package com.iamskorpz.watchioiptv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointManager
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val providersLoaded: Boolean = false,
    val providerId: ProviderId? = null,
    val providerName: String? = null,
    val providerType: ProviderType? = null,
    val providerCount: Int = 0,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val lastCatalogRefreshAtEpochMs: Long? = null,
    val liveRefreshAtEpochMs: Long? = null,
    val moviesRefreshAtEpochMs: Long? = null,
    val seriesRefreshAtEpochMs: Long? = null,
    val providerExpiryEpochMs: Long? = null,
    val liveRefreshing: Boolean = false,
    val moviesRefreshing: Boolean = false,
    val seriesRefreshing: Boolean = false,
    val liveRefreshProgress: Float? = null,
    val moviesRefreshProgress: Float? = null,
    val seriesRefreshProgress: Float? = null,
    val refreshMessage: String? = null,
    val activeServerLabel: String? = null,
    val activeServerOnline: Boolean = true,
) {
    val providerSummary: String =
        providerName ?: "No providers configured"
}

class HomeViewModel(
    private val providerRepository: ProviderRepository,
    settingsRepository: SettingsRepository,
    private val xtreamRepository: XtreamRepository,
    private val m3uRepository: M3uRepository,
    private val endpointManager: WatchioEndpointManager,
) : ViewModel() {
    private val refreshStatus = MutableStateFlow(HomeRefreshStatus())
    private val refreshJobs = mutableMapOf<ContentType, Job>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = combine(
        providerRepository.observeProviders(),
        settingsRepository.selectedProviderId,
        refreshStatus,
        endpointManager.activeEndpoint,
        endpointManager.activeEndpointOnline,
    ) { providers, selectedProviderId, refresh, activeEndpoint, activeEndpointOnline ->
        val selected = selectedProviderId?.let { id -> providers.firstOrNull { it.id == id && it.enabled && it.type == ProviderType.Xtream } }
            ?: providers.firstOrNull { it.enabled && it.type == ProviderType.Xtream }
        val xtreamCounts = if (selected?.type == ProviderType.Xtream) xtreamRepository.counts(selected.id) else null
        val m3uCounts = if (selected?.type == ProviderType.M3uUrl || selected?.type == ProviderType.M3uFile) {
            m3uRepository.counts(selected.id.value)
        } else {
            null
        }
        HomeUiState(
            providersLoaded = true,
            providerId = selected?.id,
            providerName = selected?.displayName,
            providerType = selected?.type,
            providerCount = providers.size,
            liveCount = xtreamCounts?.liveCount ?: m3uCounts?.liveCount ?: 0,
            movieCount = xtreamCounts?.movieCount ?: m3uCounts?.movieCount ?: 0,
            seriesCount = xtreamCounts?.seriesCount ?: m3uCounts?.seriesCount ?: 0,
            lastCatalogRefreshAtEpochMs = selected?.lastRefreshAtEpochMs,
            liveRefreshing = refresh.isRefreshing(selected?.id, ContentType.Live),
            moviesRefreshing = refresh.isRefreshing(selected?.id, ContentType.Movie),
            seriesRefreshing = refresh.isRefreshing(selected?.id, ContentType.Series),
            liveRefreshProgress = refresh.progress(selected?.id, ContentType.Live),
            moviesRefreshProgress = refresh.progress(selected?.id, ContentType.Movie),
            seriesRefreshProgress = refresh.progress(selected?.id, ContentType.Series),
            refreshMessage = refresh.message,
            activeServerLabel = activeEndpoint?.takeIf {
                selected?.id?.value?.startsWith(MANAGED_PROVIDER_PREFIX) == true && it.url == selected.serverUrl
            }?.let { managedServerLabel(it.id) },
            activeServerOnline = activeEndpointOnline != false,
        )
    }.flatMapLatest { home ->
        val providerId = home.providerId ?: return@flatMapLatest flowOf(home)
        combine(
            settingsRepository.observeProviderExpiryEpochMs(providerId),
            settingsRepository.observeSectionRefreshEpochMs(providerId, ContentType.Live),
            settingsRepository.observeSectionRefreshEpochMs(providerId, ContentType.Movie),
            settingsRepository.observeSectionRefreshEpochMs(providerId, ContentType.Series),
        ) { expiry, liveAt, moviesAt, seriesAt ->
            home.copy(
                providerExpiryEpochMs = expiry,
                liveRefreshAtEpochMs = liveAt ?: home.lastCatalogRefreshAtEpochMs,
                moviesRefreshAtEpochMs = moviesAt ?: home.lastCatalogRefreshAtEpochMs,
                seriesRefreshAtEpochMs = seriesAt ?: home.lastCatalogRefreshAtEpochMs,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            combine(providerRepository.observeProviders(), settingsRepository.selectedProviderId) { providers, selectedProviderId ->
                selectedProviderId?.let { id -> providers.firstOrNull { it.id == id && it.enabled } }
                    ?: providers.firstOrNull { it.enabled }
            }.distinctUntilChanged { old, new -> old?.id == new?.id && old?.serverUrl == new?.serverUrl }
                .collectLatest { provider ->
                    if (provider?.id?.value?.startsWith(MANAGED_PROVIDER_PREFIX) == true) endpointManager.restoreActive(provider.serverUrl)
                    else endpointManager.clearActive()
                }
        }
    }

    fun refreshSelectedProvider() {
        refreshSection(ContentType.Live)
        refreshSection(ContentType.Movie)
        refreshSection(ContentType.Series)
    }

    fun refreshLive() = refreshSection(ContentType.Live)
    fun refreshMovies() = refreshSection(ContentType.Movie)
    fun refreshSeries() = refreshSection(ContentType.Series)

    private fun refreshSection(contentType: ContentType) {
        val selected = state.value.providerId ?: return
        if (refreshJobs[contentType]?.isActive == true) return
        refreshJobs[contentType] = viewModelScope.launch {
            refreshStatus.value = refreshStatus.value.start(selected, contentType)
            val result = runCatching {
                val provider = providerRepository.getProvider(selected) ?: error("Provider not found.")
                when (provider.type) {
                    ProviderType.Xtream -> when (contentType) {
                        ContentType.Live -> xtreamRepository.refreshLive(selected)
                        ContentType.Movie -> xtreamRepository.refreshMovies(selected)
                        ContentType.Series -> xtreamRepository.refreshSeries(selected)
                        ContentType.Episode -> error("Episode is not a Home refresh section.")
                    }
                    ProviderType.M3uUrl, ProviderType.M3uFile -> m3uRepository.refreshSection(selected.value, contentType)
                }
            }
            refreshStatus.value = refreshStatus.value.finish(selected, contentType, result.isSuccess)
        }
    }

    internal data class HomeRefreshStatus(
        val refreshing: Set<Pair<ProviderId, ContentType>> = emptySet(),
        val progress: Map<Pair<ProviderId, ContentType>, Float> = emptyMap(),
        val message: String? = null,
    ) {
        fun isRefreshing(providerId: ProviderId?, contentType: ContentType): Boolean =
            providerId != null && Pair(providerId, contentType) in refreshing

        fun progress(providerId: ProviderId?, contentType: ContentType): Float? =
            providerId?.let { progress[Pair(it, contentType)] }

        fun start(providerId: ProviderId, contentType: ContentType): HomeRefreshStatus =
            copy(
                refreshing = refreshing + Pair(providerId, contentType),
                progress = progress + (Pair(providerId, contentType) to 0.08f),
                message = "Refreshing ${sectionLabel(contentType)}...",
            )

        fun finish(providerId: ProviderId, contentType: ContentType, success: Boolean): HomeRefreshStatus =
            copy(
                refreshing = refreshing - Pair(providerId, contentType),
                progress = progress + (Pair(providerId, contentType) to 1f),
                message = if (success) "${sectionLabel(contentType)} updated" else "${sectionLabel(contentType)} refresh failed. Cached library preserved.",
            )
    }

    private companion object {
        const val MANAGED_PROVIDER_PREFIX = "xtream-managed-"
        fun sectionLabel(contentType: ContentType): String = when (contentType) {
            ContentType.Live -> "Live TV"
            ContentType.Movie -> "Movies"
            ContentType.Series -> "Series"
            ContentType.Episode -> "Episodes"
        }
    }
}

internal fun managedServerLabel(endpointId: String): String = when (endpointId.trim().lowercase()) {
    "xolo" -> "Xolo"
    "mediatitans" -> "MediaTitans"
    "as8880" -> "AS8880"
    "aw999" -> "AW999"
    else -> "Server"
}
