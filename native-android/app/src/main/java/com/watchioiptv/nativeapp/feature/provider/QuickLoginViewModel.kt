package com.watchioiptv.nativeapp.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.core.pairing.QuickLoginCredentials
import com.watchioiptv.nativeapp.core.pairing.QuickLoginInvitation
import com.watchioiptv.nativeapp.core.pairing.QuickLoginReceiver
import com.watchioiptv.nativeapp.core.pairing.QuickLoginSender
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.data.xtream.XtreamCredentialsInput
import com.watchioiptv.nativeapp.data.xtream.XtreamRepository
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
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

    fun sendScannedCode(rawValue: String) {
        val invitation = QuickLoginInvitation.parse(rawValue)
        if (invitation == null) {
            _state.value = _state.value.copy(errorMessage = "This is not a Watchio Quick Login QR code.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, errorMessage = null, status = "Sending login to TV…")
            runCatching {
                val providerId = settingsRepository.selectedProviderId.first()
                    ?: throw IllegalArgumentException("Sign in to an Xtream provider first.")
                val provider = providerRepository.getProvider(providerId)
                    ?: throw IllegalArgumentException("Active provider is unavailable.")
                require(provider.type == ProviderType.Xtream) { "Quick Login currently supports Xtream Codes providers." }
                val credentials = credentialStore.getXtreamCredentials(providerId.value)
                    ?: throw IllegalArgumentException("Active provider credentials are unavailable.")
                QuickLoginSender.send(
                    invitation = invitation,
                    credentials = QuickLoginCredentials(
                        providerName = provider.displayName,
                        serverUrl = provider.serverUrl ?: throw IllegalArgumentException("Active provider server is unavailable."),
                        username = credentials.username,
                        password = credentials.password,
                    ),
                )
            }.onSuccess {
                _state.value = _state.value.copy(isBusy = false, status = "Login sent. Finish setup on TV.")
            }.onFailure { error ->
                _state.value = _state.value.copy(isBusy = false, errorMessage = error.message ?: "Unable to send Quick Login.")
            }
        }
    }

    private fun receiveCredentials(credentials: QuickLoginCredentials) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, invitation = null, expiresAtEpochMs = null, status = "Connecting provider…", errorMessage = null)
            runCatching {
                xtreamRepository.addProvider(
                    XtreamCredentialsInput(
                        displayName = credentials.providerName,
                        serverUrl = credentials.serverUrl,
                        username = credentials.username,
                        password = credentials.password,
                    ),
                )
            }.onSuccess {
                _state.value = _state.value.copy(isBusy = false, received = true, status = "Quick Login complete.")
            }.onFailure { error ->
                _state.value = _state.value.copy(isBusy = false, errorMessage = error.message ?: "Unable to import provider.")
            }
        }
    }

    override fun onCleared() {
        receiver.close()
    }
}
