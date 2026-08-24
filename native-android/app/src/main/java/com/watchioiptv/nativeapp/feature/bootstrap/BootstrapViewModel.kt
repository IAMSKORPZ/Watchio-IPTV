package com.watchioiptv.nativeapp.feature.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface BootstrapDestination {
    data object Loading : BootstrapDestination
    data object NeedsDeviceMode : BootstrapDestination
    data object NeedsXtreamLogin : BootstrapDestination
    data object Ready : BootstrapDestination
}

class BootstrapViewModel(
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
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
                    if (!validSelected) settingsRepository.setSelectedProviderId(enabledProviders.first().id)
                    BootstrapDestination.Ready
                }
                onboardingComplete -> BootstrapDestination.NeedsXtreamLogin
                else -> BootstrapDestination.NeedsDeviceMode
            }
        }
    }
}
