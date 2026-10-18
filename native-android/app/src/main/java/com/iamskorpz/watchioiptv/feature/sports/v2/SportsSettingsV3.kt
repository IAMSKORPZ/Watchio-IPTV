package com.iamskorpz.watchioiptv.feature.sports.v2

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SportsCapability { FIXTURES, LIVE_SCORES, MATCH_STATUS, TEAMS_LEAGUES, STANDINGS, BROADCASTS }

data class SportsProviderInfo(
    val purpose: String,
    val features: String,
    val accessNote: String,
    val registrationUrl: String,
)

fun sportsProviderInfo(source: SportsDataSource): SportsProviderInfo = when (source) {
    SportsDataSource.FootballData -> SportsProviderInfo(
        purpose = "Football fixtures, teams, competitions and match status.",
        features = "Fixtures, match status, teams and leagues",
        accessNote = "Free access covers selected competitions and is rate limited.",
        registrationUrl = "https://www.football-data.org/client/register",
    )
    SportsDataSource.ApiFootball -> SportsProviderInfo(
        purpose = "Broad football fixture and live-score coverage.",
        features = "Fixtures, live scores, match status, teams and leagues",
        accessNote = "A free plan is available; quotas and historical coverage depend on plan.",
        registrationUrl = "https://dashboard.api-football.com/register",
    )
    SportsDataSource.SoccersApi -> SportsProviderInfo(
        purpose = "Official broadcaster data used for channel matching.",
        features = "Football broadcast listings",
        accessNote = "Requires one API key. Broadcast access requires a supported paid plan.",
        registrationUrl = "https://admin.soccersapi.com/register",
    )
    SportsDataSource.TheSportsDb -> SportsProviderInfo(
        purpose = "Supplementary football event and TV broadcast data.",
        features = "Event lookup and broadcast listings",
        accessNote = "Free API access is limited. User-supplied supporter or premium keys unlock production access; Watchio never enables a paid plan.",
        registrationUrl = "https://www.thesportsdb.com/api.php",
    )
    else -> SportsProviderInfo("Unsupported provider.", "None", "No configuration available.", "")
}

@Serializable
data class SportsProviderPreference(
    val source: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
)

@Serializable
data class SportsCapabilityPreference(
    val capability: String,
    val source: String,
)

@Serializable
data class ManualChannelMapping(
    val providerId: String,
    val broadcaster: String,
    val channelId: String,
    val channelName: String,
    val preferredQuality: String? = null,
)

@Serializable
data class SportsSettingsSnapshot(
    val providers: List<SportsProviderPreference> = defaultProviderPreferences(),
    val capabilities: List<SportsCapabilityPreference> = defaultCapabilityPreferences(),
    val manualMappings: List<ManualChannelMapping> = emptyList(),
    val refreshMinutes: Int = 30,
)

interface SportsSettingsStore {
    fun read(): SportsSettingsSnapshot
    fun write(value: SportsSettingsSnapshot)
}

class AndroidSportsSettingsStore(context: Context) : SportsSettingsStore {
    private val preferences = context.getSharedPreferences("watchio_sports_v3", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun read(): SportsSettingsSnapshot = preferences.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString<SportsSettingsSnapshot>(it) }.getOrNull() }
        ?.sanitized()
        ?: SportsSettingsSnapshot()

    override fun write(value: SportsSettingsSnapshot) {
        preferences.edit { putString(KEY, json.encodeToString(SportsSettingsSnapshot.serializer(), value.sanitized())) }
    }

    private companion object { const val KEY = "settings" }
}

fun SportsSettingsSnapshot.enabledSources(capability: SportsCapability): List<SportsDataSource> {
    val preferred = capabilities.firstOrNull { it.capability == capability.name }?.source
    return providers.asSequence()
        .filter(SportsProviderPreference::enabled)
        .filter { providerCapabilities(SportsDataSource(it.source)).contains(capability) }
        .sortedWith(compareBy<SportsProviderPreference> { if (it.source == preferred) -1 else it.priority }.thenBy { it.priority })
        .map { SportsDataSource(it.source) }
        .toList()
}

fun SportsSettingsSnapshot.manualMappings(providerId: String): List<ManualChannelMapping> =
    manualMappings.filter { it.providerId == providerId }

fun providerCapabilities(source: SportsDataSource): Set<SportsCapability> = when (source) {
    SportsDataSource.FootballData -> setOf(SportsCapability.FIXTURES, SportsCapability.MATCH_STATUS, SportsCapability.TEAMS_LEAGUES)
    SportsDataSource.ApiFootball -> setOf(SportsCapability.FIXTURES, SportsCapability.LIVE_SCORES, SportsCapability.MATCH_STATUS, SportsCapability.TEAMS_LEAGUES)
    SportsDataSource.SoccersApi, SportsDataSource.TheSportsDb -> setOf(SportsCapability.BROADCASTS)
    else -> emptySet()
}

fun sportsProviderDisplayName(source: SportsDataSource): String = when (source) {
    SportsDataSource.FootballData -> "football-data.org"
    SportsDataSource.ApiFootball -> "API-Football"
    SportsDataSource.SoccersApi -> "SoccersAPI"
    SportsDataSource.TheSportsDb -> "TheSportsDB"
    else -> source.value
}

private fun SportsSettingsSnapshot.sanitized(): SportsSettingsSnapshot {
    val supported = defaultProviderPreferences().map { it.source }.toSet()
    val merged = defaultProviderPreferences().associateBy { it.source }.toMutableMap()
    providers.filter { it.source in supported }.forEach { merged[it.source] = it.copy(priority = it.priority.coerceIn(0, 99)) }
    val validMappings = manualMappings.filter {
        it.providerId.isNotBlank() && it.broadcaster.isNotBlank() && it.channelId.isNotBlank() && it.channelName.isNotBlank()
    }.distinctBy { Triple(it.providerId, normalizePhrase(it.broadcaster), it.channelId) }
    return copy(
        providers = merged.values.sortedBy(SportsProviderPreference::priority),
        capabilities = capabilities.filter { row ->
            runCatching { SportsCapability.valueOf(row.capability) }.getOrNull()?.let { capability ->
                row.source in supported && capability in providerCapabilities(SportsDataSource(row.source))
            } == true
        }.distinctBy(SportsCapabilityPreference::capability),
        manualMappings = validMappings,
        refreshMinutes = refreshMinutes.coerceIn(5, 240),
    )
}

private fun defaultProviderPreferences() = listOf(
    SportsProviderPreference(SportsDataSource.FootballData.value, enabled = true, priority = 0),
    SportsProviderPreference(SportsDataSource.ApiFootball.value, enabled = true, priority = 1),
    SportsProviderPreference(SportsDataSource.SoccersApi.value, enabled = true, priority = 0),
    SportsProviderPreference(SportsDataSource.TheSportsDb.value, enabled = true, priority = 1),
)

private fun defaultCapabilityPreferences() = listOf(
    SportsCapabilityPreference(SportsCapability.FIXTURES.name, SportsDataSource.ApiFootball.value),
    SportsCapabilityPreference(SportsCapability.LIVE_SCORES.name, SportsDataSource.ApiFootball.value),
    SportsCapabilityPreference(SportsCapability.MATCH_STATUS.name, SportsDataSource.ApiFootball.value),
    SportsCapabilityPreference(SportsCapability.TEAMS_LEAGUES.name, SportsDataSource.ApiFootball.value),
    SportsCapabilityPreference(SportsCapability.BROADCASTS.name, SportsDataSource.SoccersApi.value),
)
