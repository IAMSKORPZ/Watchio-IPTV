package com.iamskorpz.watchioiptv.feature.sports.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SportsBroadcastSettingsState(
    val usernameInput: String = "",
    val tokenInput: String = "",
    val configured: Boolean = false,
    val saved: Boolean = false,
)

class SportsBroadcastSettingsViewModel(
    private val credentialStore: SoccersApiCredentialStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SportsBroadcastSettingsState())
    val state: StateFlow<SportsBroadcastSettingsState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(configured = credentialStore.get() != null)
        }
    }

    fun updateUsername(value: String) { mutableState.value = mutableState.value.copy(usernameInput = value, saved = false) }
    fun updateToken(value: String) { mutableState.value = mutableState.value.copy(tokenInput = value, saved = false) }

    fun save() {
        val current = mutableState.value
        if (current.usernameInput.isBlank() || current.tokenInput.isBlank()) return
        viewModelScope.launch {
            credentialStore.save(current.usernameInput, current.tokenInput)
            mutableState.value = SportsBroadcastSettingsState(configured = true, saved = true)
        }
    }

    fun remove() {
        viewModelScope.launch {
            credentialStore.remove()
            mutableState.value = SportsBroadcastSettingsState()
        }
    }
}
