package com.iamskorpz.watchioiptv.feature.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.data.xtream.AccountValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface BootstrapDestination {
    data object Loading : BootstrapDestination
    data object NeedsDeviceMode : BootstrapDestination
    data object NeedsXtreamLogin : BootstrapDestination
    data class AccountBlocked(val message: String) : BootstrapDestination
    data class ConnectionRequired(val message: String) : BootstrapDestination
    data object Ready : BootstrapDestination
}

class BootstrapViewModel(
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    val detectedInputMode: InputMode = InputMode.Touch,
    private val validateAccount: suspend (ProviderId) -> AccountValidationResult = { AccountValidationResult.Active },
) : ViewModel() {
    private val _destination = MutableStateFlow<BootstrapDestination>(BootstrapDestination.Loading)
    val destination: StateFlow<BootstrapDestination> = _destination

    init {
        resolve()
    }

    fun chooseMobile() {
        choose(InputMode.Touch)
    }

    fun chooseTv() {
        choose(InputMode.TvRemote)
    }

    fun chooseAutomatic() {
        choose(InputMode.Auto)
    }

    fun retry() {
        _destination.value = BootstrapDestination.Loading
        resolve()
    }

    private fun choose(inputMode: InputMode) {
        viewModelScope.launch {
            settingsRepository.setInputMode(inputMode)
            settingsRepository.setDeviceModeOnboardingCompleted(true)
            _destination.value = BootstrapDestination.NeedsXtreamLogin
        }
    }

    private fun resolve() {
        viewModelScope.launch {
            val providers = providerRepository.getProviders()
            val selectedProviderId = settingsRepository.selectedProviderId.first()
            val onboardingComplete = settingsRepository.deviceModeOnboardingCompleted.first()
            val enabledProviders = providers.filter { it.enabled && it.type == ProviderType.Xtream }
            val hasProvider = enabledProviders.isNotEmpty()
            val validSelected = selectedProviderId != null && enabledProviders.any { it.id == selectedProviderId }
            _destination.value = when {
                hasProvider -> {
                    if (!onboardingComplete) settingsRepository.setDeviceModeOnboardingCompleted(true)
                    val activeProviderId = selectedProviderId.takeIf { validSelected } ?: enabledProviders.first().id.also {
                        settingsRepository.setSelectedProviderId(it)
                    }
                    when (val result = validateAccount(activeProviderId)) {
                        AccountValidationResult.Active -> BootstrapDestination.Ready
                        is AccountValidationResult.Blocked -> BootstrapDestination.AccountBlocked(result.message)
                        is AccountValidationResult.Retryable -> BootstrapDestination.ConnectionRequired(result.message)
                    }
                }
                onboardingComplete -> BootstrapDestination.NeedsXtreamLogin
                else -> BootstrapDestination.NeedsDeviceMode
            }
        }
    }
}
