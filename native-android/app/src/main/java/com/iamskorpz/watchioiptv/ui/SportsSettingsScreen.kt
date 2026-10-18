package com.iamskorpz.watchioiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.feature.settings.FootballDataConnectionStatus
import com.iamskorpz.watchioiptv.feature.settings.FootballDataSettingsUiState
import com.iamskorpz.watchioiptv.feature.sports.v2.ManualChannelMapping
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsBroadcastSettingsState
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsCapability
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsConnectionState
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsDataSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsProviderUi
import com.iamskorpz.watchioiptv.feature.sports.v2.sportsProviderInfo
import com.iamskorpz.watchioiptv.ui.components.WatchioButton
import com.iamskorpz.watchioiptv.ui.components.WatchioButtonVariant
import com.iamskorpz.watchioiptv.ui.components.WatchioCard
import com.iamskorpz.watchioiptv.ui.components.WatchioPageHeader
import com.iamskorpz.watchioiptv.ui.icons.WatchioIcon
import com.iamskorpz.watchioiptv.ui.icons.WatchioIconColors
import com.iamskorpz.watchioiptv.ui.icons.WatchioIconKind
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioSpacing
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioTypography

internal data class SportsSettingsActions(
    val onFootballInputChanged: (String) -> Unit,
    val onSaveFootball: () -> Unit,
    val onRemoveFootball: () -> Unit,
    val onCancelRemoveFootball: () -> Unit,
    val onConfirmRemoveFootball: () -> Unit,
    val onGetProviderKey: (SportsDataSource) -> Unit,
    val onSoccersApiKeyChanged: (String) -> Unit,
    val onSaveSoccers: () -> Unit,
    val onApiFootballChanged: (String) -> Unit,
    val onSaveApiFootball: () -> Unit,
    val onSportsDbChanged: (String) -> Unit,
    val onSaveSportsDb: () -> Unit,
    val onRemoveProvider: (SportsDataSource) -> Unit,
    val onToggleProvider: (SportsDataSource, Boolean) -> Unit,
    val onMoveProvider: (SportsDataSource, Int) -> Unit,
    val onTestProvider: (SportsDataSource) -> Unit,
    val onChooseCapability: (SportsCapability, SportsDataSource) -> Unit,
    val onRefreshMinutes: (Int) -> Unit,
    val onMappingBroadcaster: (String) -> Unit,
    val onChannelQuery: (String) -> Unit,
    val onAddMapping: (LiveTvChannel, String?) -> Unit,
    val onRemoveMapping: (ManualChannelMapping) -> Unit,
)

private enum class SportsSettingsPage {
    Landing, FootballData, FootballDataOrg, ApiFootball, TvBroadcasts, SoccersApi, TheSportsDb,
    Advanced, Priority, Routing, Mappings, RefreshDiagnostics,
}

@Composable
internal fun SportsSettingsScreen(
    footballState: FootballDataSettingsUiState,
    sportsState: SportsBroadcastSettingsState,
    actions: SportsSettingsActions,
    onBack: () -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(SportsSettingsPage.Landing.name) }
    val page = SportsSettingsPage.valueOf(pageName)
    fun navigate(target: SportsSettingsPage) { pageName = target.name }
    fun navigateBack() {
        val parent = when (page) {
            SportsSettingsPage.Landing -> null
            SportsSettingsPage.FootballData, SportsSettingsPage.TvBroadcasts, SportsSettingsPage.Advanced -> SportsSettingsPage.Landing
            SportsSettingsPage.FootballDataOrg, SportsSettingsPage.ApiFootball -> SportsSettingsPage.FootballData
            SportsSettingsPage.SoccersApi, SportsSettingsPage.TheSportsDb -> SportsSettingsPage.TvBroadcasts
            SportsSettingsPage.Priority, SportsSettingsPage.Routing, SportsSettingsPage.Mappings, SportsSettingsPage.RefreshDiagnostics -> SportsSettingsPage.Advanced
        }
        if (parent == null) onBack() else navigate(parent)
    }
    BackHandler(onBack = ::navigateBack)
    SportsSettingsScaffold(title = sportsPageTitle(page), onBack = ::navigateBack) {
        when (page) {
            SportsSettingsPage.Landing -> LandingPage(footballState, sportsState, ::navigate)
            SportsSettingsPage.FootballData -> ProviderListPage(
                intro = "Choose a service for fixtures, scores and match information.",
                providers = sportsState.providers.filter { it.source == SportsDataSource.FootballData || it.source == SportsDataSource.ApiFootball },
                onOpen = { navigate(if (it == SportsDataSource.FootballData) SportsSettingsPage.FootballDataOrg else SportsSettingsPage.ApiFootball) },
            )
            SportsSettingsPage.FootballDataOrg -> FootballDataOrgPage(footballState, sportsState, actions)
            SportsSettingsPage.ApiFootball -> ProviderDetailsPage(SportsDataSource.ApiFootball, sportsState, actions)
            SportsSettingsPage.TvBroadcasts -> ProviderListPage(
                intro = "Connect a broadcaster data service to help Watchio find channels showing football matches.",
                providers = sportsState.providers.filter { it.source == SportsDataSource.SoccersApi || it.source == SportsDataSource.TheSportsDb },
                onOpen = { navigate(if (it == SportsDataSource.SoccersApi) SportsSettingsPage.SoccersApi else SportsSettingsPage.TheSportsDb) },
            )
            SportsSettingsPage.SoccersApi -> ProviderDetailsPage(SportsDataSource.SoccersApi, sportsState, actions)
            SportsSettingsPage.TheSportsDb -> ProviderDetailsPage(SportsDataSource.TheSportsDb, sportsState, actions)
            SportsSettingsPage.Advanced -> AdvancedLanding(::navigate)
            SportsSettingsPage.Priority -> PriorityPage(sportsState, actions)
            SportsSettingsPage.Routing -> RoutingPage(sportsState, actions)
            SportsSettingsPage.Mappings -> MappingsPage(sportsState, actions)
            SportsSettingsPage.RefreshDiagnostics -> RefreshDiagnosticsPage(sportsState, actions)
        }
    }
    if (footballState.removeConfirmationVisible) {
        AlertDialog(
            onDismissRequest = actions.onCancelRemoveFootball,
            title = { Text("Remove API key?") },
            text = { Text("Sports fixture data will stop using football-data.org until another key is saved.") },
            confirmButton = { TextButton(onClick = actions.onConfirmRemoveFootball) { Text("Remove") } },
            dismissButton = { TextButton(onClick = actions.onCancelRemoveFootball) { Text("Cancel") } },
        )
    }
}

private fun sportsPageTitle(page: SportsSettingsPage): String = when (page) {
    SportsSettingsPage.Landing -> "SPORTS SETTINGS"
    SportsSettingsPage.FootballData -> "FOOTBALL DATA"
    SportsSettingsPage.FootballDataOrg -> "FOOTBALL-DATA.ORG"
    SportsSettingsPage.ApiFootball -> "API-FOOTBALL"
    SportsSettingsPage.TvBroadcasts -> "TV BROADCASTS"
    SportsSettingsPage.SoccersApi -> "SOCCERSAPI"
    SportsSettingsPage.TheSportsDb -> "THESPORTSDB"
    SportsSettingsPage.Advanced -> "ADVANCED SETTINGS"
    SportsSettingsPage.Priority -> "PROVIDER PRIORITY"
    SportsSettingsPage.Routing -> "CAPABILITY ROUTING"
    SportsSettingsPage.Mappings -> "MANUAL CHANNEL MAPPINGS"
    SportsSettingsPage.RefreshDiagnostics -> "REFRESH & DIAGNOSTICS"
}

@Composable
private fun SportsSettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("sports-settings-v4")) {
        val horizontal = if (maxWidth < 600.dp) 16.dp else 34.dp
        Column(Modifier.fillMaxSize().padding(horizontal = horizontal, vertical = 16.dp)) {
            WatchioPageHeader(title, onBack, testTagPrefix = "sports-settings")
            content()
        }
    }
}

@Composable
private fun PageList(tag: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(tag),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun LandingPage(
    footballState: FootballDataSettingsUiState,
    sportsState: SportsBroadcastSettingsState,
    navigate: (SportsSettingsPage) -> Unit,
) {
    PageList("sports-settings-landing") {
        item {
            SettingsNavigationRow(
                "Football Data", "Fixtures, scores and match information", WatchioIconKind.Football,
                if (footballState.configured || sportsState.isConfigured(SportsDataSource.ApiFootball)) "Configured" else "Needs setup",
            ) { navigate(SportsSettingsPage.FootballData) }
        }
        item {
            SettingsNavigationRow(
                "TV Broadcasts", "Find channels showing each match", WatchioIconKind.LiveTv,
                if (sportsState.isConfigured(SportsDataSource.SoccersApi) || sportsState.isConfigured(SportsDataSource.TheSportsDb)) "Configured" else "Optional",
            ) { navigate(SportsSettingsPage.TvBroadcasts) }
        }
        item {
            SettingsNavigationRow("Advanced Settings", "Provider priorities, manual mappings, refresh and diagnostics", WatchioIconKind.Settings, null) {
                navigate(SportsSettingsPage.Advanced)
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(title: String, subtitle: String, icon: WatchioIconKind, status: String?, onClick: () -> Unit) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    WatchioCard(
        modifier = Modifier.fillMaxWidth().testTag("sports-settings-${title.lowercase().replace(' ', '-') }"),
        minWidth = 0.dp,
        minHeight = 72.dp,
        contentDescription = listOfNotNull(title, subtitle, status).joinToString(", "),
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            WatchioIcon(icon, if (icon == WatchioIconKind.Football) WatchioIconColors.Football else colors.liveTvAccent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold)
                Text(subtitle, color = colors.textSecondary, style = type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            status?.let { StatusText(it, positive = it == "Configured") }
            Text("›", color = colors.textSecondary, style = type.screenTitle)
        }
    }
}

@Composable
private fun ProviderListPage(intro: String, providers: List<SportsProviderUi>, onOpen: (SportsDataSource) -> Unit) {
    val colors = LocalWatchioColors.current
    PageList("sports-provider-list") {
        item { Text(intro, color = colors.textSecondary, style = LocalWatchioTypography.current.body) }
        items(providers, key = { it.source.value }) { provider ->
            val info = sportsProviderInfo(provider.source)
            SettingsNavigationRow(provider.name, info.purpose, if (provider.source == SportsDataSource.SoccersApi || provider.source == SportsDataSource.TheSportsDb) WatchioIconKind.LiveTv else WatchioIconKind.Football, providerStatus(provider)) { onOpen(provider.source) }
        }
    }
}

@Composable
private fun FootballDataOrgPage(state: FootballDataSettingsUiState, sportsState: SportsBroadcastSettingsState, actions: SportsSettingsActions) {
    val colors = LocalWatchioColors.current
    val provider = sportsState.providers.first { it.source == SportsDataSource.FootballData }
    PageList("sports-provider-detail-football-data") {
        item { ProviderIntro(SportsDataSource.FootballData, state.configured, footballStatus(state.status)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (provider.enabled) "Enabled" else "Disabled", color = colors.textPrimary)
                Switch(provider.enabled, { actions.onToggleProvider(SportsDataSource.FootballData, it) }, modifier = Modifier.testTag("sports-provider-enabled-football-data"))
            }
        }
        item {
            SecretField(state.input, actions.onFootballInputChanged, "football-data.org API key", "sports-football-data-key")
        }
        item {
            CompactActions {
                WatchioButton("Get API Key", { actions.onGetProviderKey(SportsDataSource.FootballData) }, variant = WatchioButtonVariant.Secondary, modifier = Modifier.widthIn(140.dp, 220.dp).testTag("sports-get-key-football-data"))
                WatchioButton("Save & Test", actions.onSaveFootball, enabled = state.input.isNotBlank() && state.status != FootballDataConnectionStatus.Checking, loading = state.status == FootballDataConnectionStatus.Checking, modifier = Modifier.widthIn(140.dp, 220.dp).testTag("sports-save-test-football-data"))
                if (state.configured) WatchioButton("Remove Key", actions.onRemoveFootball, variant = WatchioButtonVariant.Ghost, modifier = Modifier.widthIn(140.dp, 220.dp).testTag("sports-remove-football-data"))
            }
        }
        item { Text("Data provided by football-data.org", color = colors.textMuted, style = LocalWatchioTypography.current.label) }
    }
}

@Composable
private fun ProviderDetailsPage(source: SportsDataSource, state: SportsBroadcastSettingsState, actions: SportsSettingsActions) {
    val provider = state.providers.first { it.source == source }
    PageList("sports-provider-detail-${source.value}") {
        item { ProviderIntro(source, provider.configured, connectionText(provider.connection)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (provider.enabled) "Enabled" else "Disabled", color = LocalWatchioColors.current.textPrimary)
                Switch(provider.enabled, { actions.onToggleProvider(source, it) }, modifier = Modifier.testTag("sports-provider-enabled-${source.value}"))
            }
        }
        when (source) {
            SportsDataSource.ApiFootball -> item { SecretField(state.apiFootballInput, actions.onApiFootballChanged, "API-Football API key", "sports-api-football-key") }
            SportsDataSource.SoccersApi -> item { SecretField(state.soccersApiKeyInput, actions.onSoccersApiKeyChanged, "SoccersAPI API key", "sports-soccers-key") }
            SportsDataSource.TheSportsDb -> item { SecretField(state.theSportsDbInput, actions.onSportsDbChanged, "TheSportsDB API key", "sports-thesportsdb-key") }
            else -> Unit
        }
        item {
            CompactActions {
                WatchioButton("Get API Key", { actions.onGetProviderKey(source) }, variant = WatchioButtonVariant.Secondary, modifier = Modifier.widthIn(140.dp, 220.dp).testTag("sports-get-key-${source.value}"))
                WatchioButton("Save & Test", {
                    when (source) {
                        SportsDataSource.ApiFootball -> actions.onSaveApiFootball()
                        SportsDataSource.SoccersApi -> actions.onSaveSoccers()
                        SportsDataSource.TheSportsDb -> actions.onSaveSportsDb()
                        else -> Unit
                    }
                }, enabled = credentialInputReady(source, state), modifier = Modifier.widthIn(140.dp, 220.dp).testTag("sports-save-test-${source.value}"))
                if (provider.configured) {
                    WatchioButton("Test Connection", { actions.onTestProvider(source) }, loading = provider.connection == SportsConnectionState.TESTING, variant = WatchioButtonVariant.Secondary, modifier = Modifier.widthIn(150.dp, 230.dp).testTag("sports-test-${source.value}"))
                    WatchioButton("Remove Key", { actions.onRemoveProvider(source) }, variant = WatchioButtonVariant.Ghost, modifier = Modifier.widthIn(140.dp, 220.dp).testTag("sports-remove-${source.value}"))
                }
            }
        }
    }
}

@Composable
private fun ProviderIntro(source: SportsDataSource, configured: Boolean, connection: String) {
    val colors = LocalWatchioColors.current
    val info = sportsProviderInfo(source)
    WatchioCard(Modifier.fillMaxWidth(), minWidth = 0.dp, minHeight = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(info.purpose, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Text("Supports: ${info.features}", color = colors.textSecondary)
            Text(info.accessNote, color = colors.textSecondary)
            StatusText(if (configured) "Credentials saved" else "Not configured", configured)
            Text("Connection: $connection", color = colors.textSecondary, modifier = Modifier.testTag("sports-connection-status"))
        }
    }
}

@Composable
private fun AdvancedLanding(navigate: (SportsSettingsPage) -> Unit) {
    PageList("sports-advanced-landing") {
        item { SettingsNavigationRow("Provider Priority", "Choose fallback order", WatchioIconKind.More, null) { navigate(SportsSettingsPage.Priority) } }
        item { SettingsNavigationRow("Capability Routing", "Choose which provider supplies each feature", WatchioIconKind.More, null) { navigate(SportsSettingsPage.Routing) } }
        item { SettingsNavigationRow("Manual Channel Mappings", "Link a broadcaster to one provider channel", WatchioIconKind.LiveTv, null) { navigate(SportsSettingsPage.Mappings) } }
        item { SettingsNavigationRow("Refresh & Diagnostics", "Cache timing and connection status", WatchioIconKind.Settings, null) { navigate(SportsSettingsPage.RefreshDiagnostics) } }
    }
}

@Composable
private fun PriorityPage(state: SportsBroadcastSettingsState, actions: SportsSettingsActions) {
    val colors = LocalWatchioColors.current
    PageList("sports-priority") {
        item { Text("Higher providers are tried first when they support the same feature.", color = colors.textSecondary) }
        items(state.providers, key = { it.source.value }) { provider ->
            WatchioCard(Modifier.fillMaxWidth(), minWidth = 0.dp, minHeight = 64.dp) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${provider.priority + 1}. ${provider.name}", color = colors.textPrimary, modifier = Modifier.weight(1f))
                    WatchioButton("Up", { actions.onMoveProvider(provider.source, -1) }, modifier = Modifier.width(96.dp), enabled = provider.priority > 0, variant = WatchioButtonVariant.Secondary)
                    WatchioButton("Down", { actions.onMoveProvider(provider.source, 1) }, modifier = Modifier.width(96.dp), enabled = provider.priority < state.providers.lastIndex, variant = WatchioButtonVariant.Secondary)
                }
            }
        }
    }
}

@Composable
private fun RoutingPage(state: SportsBroadcastSettingsState, actions: SportsSettingsActions) {
    val colors = LocalWatchioColors.current
    PageList("sports-routing") {
        item { Text("Use recommended defaults unless a provider lacks coverage.", color = colors.textSecondary) }
        SportsCapability.entries.forEach { capability ->
            val providers = state.providers.filter { provider -> provider.enabled && capability in provider.capabilities }
            if (providers.isNotEmpty()) item(capability.name) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(capability.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    CompactActions {
                        providers.forEach { provider ->
                            val selected = state.settings.capabilities.firstOrNull { it.capability == capability.name }?.source == provider.source.value
                            WatchioButton(provider.name, { actions.onChooseCapability(capability, provider.source) }, modifier = Modifier.widthIn(150.dp, 240.dp), variant = if (selected) WatchioButtonVariant.Primary else WatchioButtonVariant.Secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MappingsPage(state: SportsBroadcastSettingsState, actions: SportsSettingsActions) {
    val colors = LocalWatchioColors.current
    val query = state.channelQuery.trim()
    val channels = if (query.length < 2) emptyList() else state.availableChannels.asSequence().filter { it.name.contains(query, ignoreCase = true) }.take(50).toList()
    PageList("sports-manual-mappings") {
        item { Text("Mappings apply only to the active IPTV provider. Enter a broadcaster, then search for a channel.", color = colors.textSecondary) }
        item { PlainField(state.mappingBroadcaster, actions.onMappingBroadcaster, "Broadcaster name", "sports-mapping-broadcaster") }
        item { PlainField(state.channelQuery, actions.onChannelQuery, "Search provider channels", "sports-mapping-channel-search") }
        if (query.length < 2) item { Text("Type at least 2 characters to search. Full channel catalogue stays hidden.", color = colors.textMuted) }
        items(channels, key = { it.id }) { channel ->
            WatchioCard(
                modifier = Modifier.fillMaxWidth().testTag("sports-mapping-channel-${channel.id}"), minWidth = 0.dp, minHeight = 54.dp,
                enabled = state.mappingBroadcaster.isNotBlank(), contentDescription = "Map ${channel.name}", onClick = { actions.onAddMapping(channel, null) },
            ) { Text(channel.name, color = if (state.mappingBroadcaster.isBlank()) colors.textMuted else colors.textPrimary, modifier = Modifier.padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        val mappings = state.settings.manualMappings.filter { it.providerId == state.activeProviderId }
        if (mappings.isNotEmpty()) item { Text("Saved mappings", color = colors.textPrimary, fontWeight = FontWeight.Bold) }
        items(mappings, key = { "${it.broadcaster}-${it.channelId}" }) { mapping ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${mapping.broadcaster} → ${mapping.channelName}", color = colors.textSecondary, modifier = Modifier.weight(1f), maxLines = 2)
                WatchioButton("Remove", { actions.onRemoveMapping(mapping) }, variant = WatchioButtonVariant.Ghost)
            }
        }
    }
}

@Composable
private fun RefreshDiagnosticsPage(state: SportsBroadcastSettingsState, actions: SportsSettingsActions) {
    val colors = LocalWatchioColors.current
    PageList("sports-refresh-diagnostics") {
        item { Text("Refresh interval", color = colors.textPrimary, fontWeight = FontWeight.Bold) }
        item {
            CompactActions { listOf(15, 30, 60).forEach { minutes -> WatchioButton("${minutes}m", { actions.onRefreshMinutes(minutes) }, modifier = Modifier.width(110.dp), variant = if (state.settings.refreshMinutes == minutes) WatchioButtonVariant.Primary else WatchioButtonVariant.Secondary) } }
        }
        item { Text("Watchio reuses cached data during outages and rate limits.", color = colors.textSecondary) }
        item { Text("Diagnostics", color = colors.textPrimary, fontWeight = FontWeight.Bold) }
        item { Text(state.diagnostic ?: "No recent connection or matching problem.", color = colors.textSecondary, modifier = Modifier.testTag("sports-diagnostics")) }
        item { Text("Diagnostics never include API keys, provider credentials, stream URLs or account details.", color = colors.textMuted) }
    }
}

@Composable
private fun CompactActions(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun StatusText(text: String, positive: Boolean) {
    val colors = LocalWatchioColors.current
    Text(text, color = if (positive) colors.seriesAccent else colors.textSecondary, style = LocalWatchioTypography.current.label, modifier = Modifier.testTag("sports-status"))
}

private fun SportsBroadcastSettingsState.isConfigured(source: SportsDataSource): Boolean = providers.firstOrNull { it.source == source }?.configured == true

private fun providerStatus(provider: SportsProviderUi): String = when {
    !provider.configured -> "Needs setup"
    provider.connection == SportsConnectionState.CONNECTED -> "Working"
    provider.connection == SportsConnectionState.READY -> "Saved, not tested"
    provider.connection == SportsConnectionState.TESTING -> "Testing"
    else -> "Problem"
}

private fun credentialInputReady(source: SportsDataSource, state: SportsBroadcastSettingsState): Boolean = when (source) {
    SportsDataSource.ApiFootball -> state.apiFootballInput.isNotBlank()
    SportsDataSource.SoccersApi -> state.soccersApiKeyInput.isNotBlank()
    SportsDataSource.TheSportsDb -> state.theSportsDbInput.isNotBlank()
    else -> false
}

private fun footballStatus(status: FootballDataConnectionStatus): String = when (status) {
    FootballDataConnectionStatus.NotConfigured -> "not configured"
    FootballDataConnectionStatus.Configured -> "saved, not tested"
    FootballDataConnectionStatus.Checking -> "testing"
    FootballDataConnectionStatus.Connected -> "verified"
    FootballDataConnectionStatus.Invalid -> "invalid credentials"
    FootballDataConnectionStatus.RateLimited -> "rate limited"
    FootballDataConnectionStatus.UnableToVerify -> "service unavailable"
}

private fun connectionText(status: SportsConnectionState): String = when (status) {
    SportsConnectionState.NOT_CONFIGURED -> "not configured"
    SportsConnectionState.READY -> "saved, not tested"
    SportsConnectionState.TESTING -> "testing"
    SportsConnectionState.CONNECTED -> "verified"
    SportsConnectionState.INVALID -> "invalid credentials"
    SportsConnectionState.RATE_LIMITED -> "quota exhausted or rate limited"
    SportsConnectionState.UNSUPPORTED_TIER -> "subscription tier does not support broadcasts"
    SportsConnectionState.UNAVAILABLE -> "service unavailable or network error"
}
