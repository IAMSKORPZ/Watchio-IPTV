package com.iamskorpz.watchioiptv.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.diagnostics.QuickLoginBootstrapTrace
import com.iamskorpz.watchioiptv.core.pairing.QuickLoginCredentials
import com.iamskorpz.watchioiptv.core.pairing.QuickLoginReceiver
import com.iamskorpz.watchioiptv.core.pairing.QuickLoginScanParser
import com.iamskorpz.watchioiptv.core.pairing.QuickLoginScanResult
import com.iamskorpz.watchioiptv.core.pairing.QuickLoginSender
import com.iamskorpz.watchioiptv.core.security.ProviderCredentialStore
import com.iamskorpz.watchioiptv.data.xtream.XtreamCredentialsInput
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QuickLoginUiState(
    val inputMode: InputMode = InputMode.TvRemote,
    val invitation: String? = null,
    val expiresAtEpochMs: Long? = null,
    val isBusy: Boolean = false,
    val received: Boolean = false,
    val status: String = "",
    val errorMessage: String? = null,
)

class QuickLoginViewModel(
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val credentialStore: ProviderCredentialStore,
    private val xtreamRepository: XtreamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickLoginUiState())
    val state: StateFlow<QuickLoginUiState> = _state.asStateFlow()
    private val receiver = QuickLoginReceiver(
        scope = viewModelScope,
        onCredentials = ::receiveCredentials,
        onError = { message -> _state.value = _state.value.copy(invitation = null, expiresAtEpochMs = null, errorMessage = message, isBusy = false) },
    )

    init {
        viewModelScope.launch {
            settingsRepository.inputMode.collect { inputMode ->
                _state.value = _state.value.copy(inputMode = inputMode)
            }
        }
    }

    fun startTvPairing() {
        if (_state.value.invitation != null || _state.value.isBusy) return
        runCatching { receiver.start() }
            .onSuccess { invitation ->
                _state.value = _state.value.copy(
                    invitation = invitation.encode(),
                    expiresAtEpochMs = invitation.expiresAtEpochMs,
                    status = "Scan this code using Watchio on your phone.",
                    errorMessage = null,
                )
            }
            .onFailure { error ->
                _state.value = _state.value.copy(errorMessage = error.message ?: "Unable to start Quick Login.")
            }
    }

    fun sendScannedCode(rawValue: String?) {
        val invitation = when (val result = QuickLoginScanParser.parse(rawValue, System.currentTimeMillis())) {
            is QuickLoginScanResult.Valid -> result.invitation
            QuickLoginScanResult.Empty -> {
                _state.value = _state.value.copy(errorMessage = "Couldn't scan the QR code. Try again.")
                return
            }
            QuickLoginScanResult.Invalid -> {
                _state.value = _state.value.copy(errorMessage = "That isn't a Watchio Quick Login code.")
                return
            }
            QuickLoginScanResult.Expired -> {
                _state.value = _state.value.copy(errorMessage = "This Quick Login code has expired.")
                return
            }
            QuickLoginScanResult.Cancelled,
            QuickLoginScanResult.ScannerUnavailable -> error("Unexpected scanner result")
        }
        viewModelScope.launch {
            stage("qr_parsed")
            _state.value = _state.value.copy(isBusy = true, errorMessage = null, status = "Sending login to TV…")
            runCatching {
                stage("provider_lookup_started")
                val providerId = settingsRepository.selectedProviderId.first()
                    ?: throw IllegalArgumentException("Sign in to an Xtream provider first.")
                stage("provider_selected")
                val provider = providerRepository.getProvider(providerId)
                    ?: throw IllegalArgumentException("Active provider is unavailable.")
                require(provider.type == ProviderType.Xtream) { "Quick Login currently supports Xtream Codes providers." }
                stage("credentials_lookup_started")
                val credentials = credentialStore.getXtreamCredentials(providerId.value)
                    ?: throw IllegalArgumentException("Active provider credentials are unavailable.")
                stage("credentials_loaded")
                stage("transport_started")
                QuickLoginSender.send(
                    invitation = invitation,
                    credentials = QuickLoginCredentials(
                        providerName = provider.displayName,
                        serverUrl = provider.serverUrl.takeUnless { provider.id.value.startsWith(MANAGED_PROVIDER_PREFIX) },
                        username = credentials.username,
                        password = credentials.password,
                        mode = if (provider.id.value.startsWith(MANAGED_PROVIDER_PREFIX)) "managed" else "legacy",
                    ),
                )
            }.onSuccess {
                _state.value = _state.value.copy(isBusy = false, status = "Login sent. Finish setup on TV.")
            }.onFailure { error ->
                _state.value = _state.value.copy(isBusy = false, errorMessage = error.message ?: "Unable to send Quick Login.")
            }
        }
    }

    fun onScannerCancelled() {
        when (QuickLoginScanParser.cancelled()) {
            QuickLoginScanResult.Cancelled -> _state.value = _state.value.copy(errorMessage = "Scan cancelled. Try again.")
            else -> error("Unexpected scanner state")
        }
    }

    fun onScannerFailed() {
        when (QuickLoginScanParser.scannerUnavailable()) {
            QuickLoginScanResult.ScannerUnavailable -> _state.value = _state.value.copy(errorMessage = "Couldn't scan the QR code. Try again.")
            else -> error("Unexpected scanner state")
        }
    }

    private fun receiveCredentials(credentials: QuickLoginCredentials) {
        QuickLoginBootstrapTrace.start()
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, invitation = null, expiresAtEpochMs = null, status = "Connecting provider…", errorMessage = null)
            runCatching {
                QuickLoginBootstrapTrace.mark("quicklogin_sync_started")
                xtreamRepository.addProviderTwoPhase(
                    XtreamCredentialsInput(
                        displayName = credentials.providerName,
                        serverUrl = credentials.serverUrl,
                        username = credentials.username,
                        password = credentials.password,
                        managed = credentials.mode == "managed",
                    ),
                )
            }.onSuccess {
                QuickLoginBootstrapTrace.mark("quicklogin_initial_sync_completed", metadata = "epg_triggered=false series_episode_startup_count=0")
                _state.value = _state.value.copy(isBusy = false, received = true, status = "Quick Login complete.")
            }.onFailure { error ->
                _state.value = _state.value.copy(isBusy = false, errorMessage = error.message ?: "Unable to import provider.")
            }
        }
    }

    override fun onCleared() {
        receiver.close()
    }

    private fun stage(name: String) {
        println("QuickLogin:$name")
    }

    private companion object {
        const val MANAGED_PROVIDER_PREFIX = "xtream-managed-"
    }
}
