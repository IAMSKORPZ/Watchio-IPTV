package com.watchioiptv.nativeapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.domain.repository.XtreamAccountMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AccountInformationUiState(
    val providerName: String = "Not available",
    val username: String = "Not available",
    val accountStatus: String = "Unknown",
    val expirationEpochMs: Long? = null,
    val addedAtEpochMs: Long? = null,
    val providerType: String = "Not available",
    val maximumConnections: String = "Not available",
    val activeConnections: String = "Not available",
    val outputFormats: String = "Not available",
    val providerRefreshAtEpochMs: Long? = null,
    val liveRefreshAtEpochMs: Long? = null,
    val moviesRefreshAtEpochMs: Long? = null,
    val seriesRefreshAtEpochMs: Long? = null,
    val unavailableReason: String? = null,
)

class AccountInformationViewModel(
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val credentialStore: ProviderCredentialStore,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _state = MutableStateFlow(AccountInformationUiState())
    val state: StateFlow<AccountInformationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.selectedProviderId.collectLatest { providerId ->
                if (providerId == null) {
                    _state.value = AccountInformationUiState(unavailableReason = "No active Xtream provider.")
                    return@collectLatest
                }
                combine(
                    providerRepository.observeProviders(),
                    settingsRepository.observeProviderExpiryEpochMs(providerId),
                    settingsRepository.observeSectionRefreshEpochMs(providerId, ContentType.Live),
                    settingsRepository.observeSectionRefreshEpochMs(providerId, ContentType.Movie),
                    settingsRepository.observeSectionRefreshEpochMs(providerId, ContentType.Series),
                    settingsRepository.observeXtreamAccountMetadata(providerId),
                ) { values ->
                    @Suppress("UNCHECKED_CAST")
                    val providers = values[0] as List<WatchioProvider>
                    AccountSnapshot(
                        provider = providers.firstOrNull { it.id == providerId },
                        providerId = providerId,
                        expiry = values[1] as Long?,
                        liveRefresh = values[2] as Long?,
                        moviesRefresh = values[3] as Long?,
                        seriesRefresh = values[4] as Long?,
                        metadata = values[5] as XtreamAccountMetadata,
                    )
                }.collectLatest { snapshot ->
                    _state.value = snapshot.toUiState(credentialStore.safeMaskedUsername(snapshot.providerId), nowEpochMs())
                }
            }
        }
    }
}

private data class AccountSnapshot(
    val provider: WatchioProvider?,
    val providerId: ProviderId,
    val expiry: Long?,
    val liveRefresh: Long?,
    val moviesRefresh: Long?,
    val seriesRefresh: Long?,
    val metadata: XtreamAccountMetadata,
)

private suspend fun ProviderCredentialStore.safeMaskedUsername(providerId: ProviderId): String =
    getXtreamCredentials(providerId.value)?.username?.maskUsername() ?: "Not available"

private fun AccountSnapshot.toUiState(maskedUsername: String, nowEpochMs: Long): AccountInformationUiState {
    val provider = provider ?: return AccountInformationUiState(unavailableReason = "Active provider is not available.")
    if (provider.type != ProviderType.Xtream) {
        return AccountInformationUiState(
            providerName = provider.displayName,
            addedAtEpochMs = provider.createdAtEpochMs,
            providerRefreshAtEpochMs = provider.lastRefreshAtEpochMs,
            providerType = provider.type.displayLabel(),
            unavailableReason = "Account information is available for Xtream Codes providers.",
        )
    }
    return AccountInformationUiState(
        providerName = provider.displayName,
        username = maskedUsername,
        accountStatus = metadata.status.displayStatus(expiry, nowEpochMs),
        expirationEpochMs = expiry,
        addedAtEpochMs = provider.createdAtEpochMs,
        providerType = "Xtream Codes",
        maximumConnections = metadata.maxConnections.safeValue(),
        activeConnections = metadata.activeConnections.safeValue(),
        outputFormats = metadata.allowedOutputFormats.formatOutputFormats(),
        providerRefreshAtEpochMs = provider.lastRefreshAtEpochMs,
        liveRefreshAtEpochMs = liveRefresh ?: provider.lastRefreshAtEpochMs,
        moviesRefreshAtEpochMs = moviesRefresh ?: provider.lastRefreshAtEpochMs,
        seriesRefreshAtEpochMs = seriesRefresh ?: provider.lastRefreshAtEpochMs,
    )
}

private fun Long?.statusLabel(nowEpochMs: Long): String = when {
    this == null || this <= 0L -> "Unknown"
    this < nowEpochMs -> "Expired"
    else -> "Active"
}

private fun String?.displayStatus(expiry: Long?, nowEpochMs: Long): String {
    val cleaned = this?.trim()?.takeIf { it.isNotBlank() }
    if (cleaned != null) {
        return when {
            cleaned.equals("active", ignoreCase = true) && expiry != null && expiry > 0L && expiry < nowEpochMs -> "Expired"
            cleaned.equals("active", ignoreCase = true) -> "Active"
            cleaned.equals("disabled", ignoreCase = true) -> "Disabled"
            cleaned.equals("banned", ignoreCase = true) -> "Banned"
            cleaned.equals("expired", ignoreCase = true) -> "Expired"
            else -> cleaned.replaceFirstChar { it.uppercase() }
        }
    }
    return expiry.statusLabel(nowEpochMs)
}

private fun String?.safeValue(): String = this?.trim()?.takeIf { it.isNotBlank() } ?: "Not available"

private fun List<String>.formatOutputFormats(): String {
    val labels = mapNotNull { format ->
        when (format.trim().lowercase()) {
            "m3u8", "hls" -> "HLS"
            "ts", "mpegts", "mpeg-ts" -> "TS"
            else -> format.trim().uppercase().takeIf { it.isNotBlank() }
        }
    }.distinct()
    return labels.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Not available"
}

private fun ProviderType.displayLabel(): String = when (this) {
    ProviderType.Xtream -> "Xtream Codes"
    ProviderType.M3uUrl -> "M3U URL"
    ProviderType.M3uFile -> "Local M3U File"
}

private fun String.maskUsername(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return "Not available"
    if (trimmed.length <= 2) return "***"
    return "${trimmed.first()}***${trimmed.last()}"
}
