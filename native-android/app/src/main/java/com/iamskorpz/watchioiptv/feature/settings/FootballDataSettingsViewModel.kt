package com.iamskorpz.watchioiptv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialValidator
import com.iamskorpz.watchioiptv.feature.sports.FootballDataValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FootballDataConnectionStatus { NotConfigured, Checking, Connected, Invalid, RateLimited, UnableToVerify }

data class FootballDataSettingsUiState(
    val input: String = "",
    val configured: Boolean = false,
    val status: FootballDataConnectionStatus = FootballDataConnectionStatus.NotConfigured,
    val removeConfirmationVisible: Boolean = false,
)

class FootballDataSettingsViewModel(
    private val credentialStore: FootballDataCredentialStore,
    private val validator: FootballDataCredentialValidator,
    private val invalidateSportsCache: () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FootballDataSettingsUiState())
    val state: StateFlow<FootballDataSettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = credentialStore.get() != null
            mutableState.value = mutableState.value.copy(
                configured = configured,
                status = if (configured) FootballDataConnectionStatus.Connected else FootballDataConnectionStatus.NotConfigured,
            )
        }
    }

    fun updateInput(value: String) {
        mutableState.value = mutableState.value.copy(input = value)
    }

    fun validateAndSave() {
        val normalized = mutableState.value.input.trim()
        if (normalized.isBlank() || mutableState.value.status == FootballDataConnectionStatus.Checking) return
        mutableState.value = mutableState.value.copy(status = FootballDataConnectionStatus.Checking)
        viewModelScope.launch {
            when (validator.validate(normalized)) {
                FootballDataValidationResult.Valid -> {
                    credentialStore.save(normalized)
                    invalidateSportsCache()
                    mutableState.value = FootballDataSettingsUiState(
                        configured = true,
                        status = FootballDataConnectionStatus.Connected,
                    )
                }
                FootballDataValidationResult.Invalid -> mutableState.value = mutableState.value.copy(status = FootballDataConnectionStatus.Invalid)
                FootballDataValidationResult.RateLimited -> mutableState.value = mutableState.value.copy(status = FootballDataConnectionStatus.RateLimited)
                FootballDataValidationResult.NetworkError -> mutableState.value = mutableState.value.copy(status = FootballDataConnectionStatus.UnableToVerify)
            }
        }
    }

    fun requestRemove() {
        if (mutableState.value.configured) mutableState.value = mutableState.value.copy(removeConfirmationVisible = true)
    }

    fun cancelRemove() {
        mutableState.value = mutableState.value.copy(removeConfirmationVisible = false)
    }

    fun confirmRemove() {
        viewModelScope.launch {
            credentialStore.remove()
            invalidateSportsCache()
            mutableState.value = FootballDataSettingsUiState()
        }
    }
}
