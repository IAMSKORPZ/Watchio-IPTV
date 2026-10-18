package com.iamskorpz.watchioiptv.feature.sports.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialStore
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SportsConnectionState { NOT_CONFIGURED, READY, TESTING, CONNECTED, INVALID, RATE_LIMITED, UNSUPPORTED_TIER, UNAVAILABLE }

data class SportsProviderUi(
    val source: SportsDataSource,
    val name: String,
    val capabilities: Set<SportsCapability>,
    val enabled: Boolean,
    val priority: Int,
    val configured: Boolean,
    val connection: SportsConnectionState,
    val lastSuccess: Instant? = null,
)

data class SportsBroadcastSettingsState(
    val soccersApiKeyInput: String = "",
    val apiFootballInput: String = "",
    val theSportsDbInput: String = "",
    val configured: Boolean = false,
    val saved: Boolean = false,
    val providers: List<SportsProviderUi> = emptyList(),
    val settings: SportsSettingsSnapshot = SportsSettingsSnapshot(),
    val availableChannels: List<LiveTvChannel> = emptyList(),
    val activeProviderId: String? = null,
    val channelQuery: String = "",
    val mappingBroadcaster: String = "",
    val diagnostic: String? = null,
)

class SportsBroadcastSettingsViewModel(
    private val credentialStore: SoccersApiCredentialStore,
    private val footballDataStore: FootballDataCredentialStore? = null,
    private val apiFootballStore: ApiFootballCredentialStore? = null,
    private val theSportsDbStore: TheSportsDbCredentialStore? = null,
    private val settingsStore: SportsSettingsStore? = null,
    private val channelLoader: suspend () -> Pair<String?, List<LiveTvChannel>> = { null to emptyList() },
    private val connectionTester: suspend (SportsDataSource) -> SportsSourceResult<*> = { SportsSourceResult.Failure(SportsSourceError.Unsupported) },
    private val invalidateBroadcastCache: () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow(SportsBroadcastSettingsState())
    val state: StateFlow<SportsBroadcastSettingsState> = mutableState.asStateFlow()
    private var activeProviderId: String? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val preferences = settingsStore?.read() ?: SportsSettingsSnapshot()
            val configured = mapOf(
                SportsDataSource.SoccersApi to (credentialStore.get() != null),
                SportsDataSource.ApiFootball to !apiFootballStore?.get().isNullOrBlank(),
                SportsDataSource.TheSportsDb to !theSportsDbStore?.get().isNullOrBlank(),
                SportsDataSource.FootballData to !footballDataStore?.get().isNullOrBlank(),
            )
            val channels = channelLoader()
            activeProviderId = channels.first
            mutableState.value = mutableState.value.copy(
                configured = configured[SportsDataSource.SoccersApi] == true,
                settings = preferences,
                availableChannels = channels.second,
                activeProviderId = channels.first,
                providers = preferences.providers.map { row ->
                    val source = SportsDataSource(row.source)
                    SportsProviderUi(source, sportsProviderDisplayName(source), providerCapabilities(source), row.enabled, row.priority,
                        configured[source] == true, if (configured[source] == true) SportsConnectionState.READY else SportsConnectionState.NOT_CONFIGURED)
                },
                diagnostic = SportsCacheDiagnostics.summary(),
            )
        }
    }

    fun updateSoccersApiKey(value: String) { mutableState.value = mutableState.value.copy(soccersApiKeyInput = value, saved = false) }
    fun updateApiFootball(value: String) { mutableState.value = mutableState.value.copy(apiFootballInput = value, saved = false) }
    fun updateTheSportsDb(value: String) { mutableState.value = mutableState.value.copy(theSportsDbInput = value, saved = false) }
    fun updateChannelQuery(value: String) { mutableState.value = mutableState.value.copy(channelQuery = value) }
    fun updateMappingBroadcaster(value: String) { mutableState.value = mutableState.value.copy(mappingBroadcaster = value) }

    fun save() {
        val current = mutableState.value
        if (current.soccersApiKeyInput.isBlank()) return
        viewModelScope.launch {
            credentialStore.save(current.soccersApiKeyInput)
            invalidateBroadcastCache()
            mutableState.value = current.copy(
                soccersApiKeyInput = "", configured = true, saved = true,
                providers = current.providers.markConfigured(SportsDataSource.SoccersApi),
            )
            test(SportsDataSource.SoccersApi)
        }
    }

    fun saveApiFootball() {
        val value = mutableState.value.apiFootballInput.trim(); if (value.isBlank()) return
        viewModelScope.launch {
            apiFootballStore?.save(value)
            mutableState.value = mutableState.value.copy(
                apiFootballInput = "", saved = true,
                providers = mutableState.value.providers.markConfigured(SportsDataSource.ApiFootball),
            )
            test(SportsDataSource.ApiFootball)
        }
    }

    fun saveTheSportsDb() {
        val value = mutableState.value.theSportsDbInput.trim(); if (value.isBlank()) return
        viewModelScope.launch {
            theSportsDbStore?.save(value)
            invalidateBroadcastCache()
            mutableState.value = mutableState.value.copy(
                theSportsDbInput = "", saved = true,
                providers = mutableState.value.providers.markConfigured(SportsDataSource.TheSportsDb),
            )
            test(SportsDataSource.TheSportsDb)
        }
    }

    fun remove(source: SportsDataSource = SportsDataSource.SoccersApi) {
        viewModelScope.launch {
            when (source) {
                SportsDataSource.SoccersApi -> credentialStore.remove()
                SportsDataSource.ApiFootball -> apiFootballStore?.remove()
                SportsDataSource.TheSportsDb -> theSportsDbStore?.remove()
                else -> Unit
            }
            if (source == SportsDataSource.SoccersApi || source == SportsDataSource.TheSportsDb) invalidateBroadcastCache()
            mutableState.value = mutableState.value.copy(soccersApiKeyInput = "", apiFootballInput = "", theSportsDbInput = "", saved = false)
            refresh()
        }
    }

    fun setEnabled(source: SportsDataSource, enabled: Boolean) = updateSettings { snapshot ->
        snapshot.copy(providers = snapshot.providers.map { if (it.source == source.value) it.copy(enabled = enabled) else it })
    }

    fun movePriority(source: SportsDataSource, delta: Int) = updateSettings { snapshot ->
        val ordered = snapshot.providers.sortedBy(SportsProviderPreference::priority).toMutableList()
        val from = ordered.indexOfFirst { it.source == source.value }; if (from < 0) return@updateSettings snapshot
        val to = (from + delta).coerceIn(0, ordered.lastIndex); if (from == to) return@updateSettings snapshot
        val item = ordered.removeAt(from); ordered.add(to, item)
        snapshot.copy(providers = ordered.mapIndexed { index, row -> row.copy(priority = index) })
    }

    fun chooseCapability(capability: SportsCapability, source: SportsDataSource) = updateSettings { snapshot ->
        snapshot.copy(capabilities = snapshot.capabilities.filterNot { it.capability == capability.name } + SportsCapabilityPreference(capability.name, source.value))
    }

    fun setRefreshMinutes(minutes: Int) = updateSettings { it.copy(refreshMinutes = minutes) }

    fun addMapping(channel: LiveTvChannel, preferredQuality: String? = null) {
        val providerId = activeProviderId ?: return
        val broadcaster = mutableState.value.mappingBroadcaster.trim(); if (broadcaster.isBlank()) return
        updateSettings { snapshot ->
            snapshot.copy(manualMappings = snapshot.manualMappings.filterNot {
                it.providerId == providerId && normalizePhrase(it.broadcaster) == normalizePhrase(broadcaster) && it.channelId == channel.id
            } + ManualChannelMapping(providerId, broadcaster, channel.id, channel.name, preferredQuality))
        }
        mutableState.value = mutableState.value.copy(mappingBroadcaster = "", channelQuery = "")
    }

    fun removeMapping(mapping: ManualChannelMapping) = updateSettings { snapshot -> snapshot.copy(manualMappings = snapshot.manualMappings - mapping) }

    fun test(source: SportsDataSource) {
        mutableState.value = mutableState.value.copy(providers = mutableState.value.providers.map { if (it.source == source) it.copy(connection = SportsConnectionState.TESTING) else it })
        viewModelScope.launch {
            val result = connectionTester(source)
            val connection = when (result) {
                is SportsSourceResult.Success -> SportsConnectionState.CONNECTED
                SportsSourceResult.NoData -> if (source == SportsDataSource.SoccersApi || source == SportsDataSource.TheSportsDb) {
                    SportsConnectionState.UNAVAILABLE
                } else {
                    SportsConnectionState.CONNECTED
                }
                is SportsSourceResult.Failure -> when (result.error) {
                    SportsSourceError.Unauthorized -> SportsConnectionState.INVALID
                    is SportsSourceError.RateLimited -> SportsConnectionState.RATE_LIMITED
                    SportsSourceError.UnsupportedTier -> SportsConnectionState.UNSUPPORTED_TIER
                    else -> SportsConnectionState.UNAVAILABLE
                }
            }
            val now = if (connection == SportsConnectionState.CONNECTED) Instant.now() else null
            mutableState.value = mutableState.value.copy(
                providers = mutableState.value.providers.map { if (it.source == source) it.copy(connection = connection, lastSuccess = now ?: it.lastSuccess) else it },
                diagnostic = "${sportsProviderDisplayName(source)}: ${connection.name.lowercase().replace('_', ' ')}",
            )
        }
    }

    private fun updateSettings(change: (SportsSettingsSnapshot) -> SportsSettingsSnapshot) {
        val store = settingsStore ?: return
        val updated = change(mutableState.value.settings)
        store.write(updated)
        invalidateBroadcastCache()
        mutableState.value = mutableState.value.copy(settings = store.read())
        refresh()
    }

    private fun List<SportsProviderUi>.markConfigured(source: SportsDataSource): List<SportsProviderUi> =
        map { if (it.source == source) it.copy(configured = true, connection = SportsConnectionState.READY) else it }
}
