package com.watchioiptv.nativeapp.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.data.xtream.XtreamCredentialsInput
import com.watchioiptv.nativeapp.data.xtream.XtreamImportState
import com.watchioiptv.nativeapp.data.xtream.XtreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class XtreamProviderFormState(
    val providerName: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val importState: XtreamImportState = XtreamImportState.Idle,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean =
        providerName.isNotBlank() && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
            importState !is XtreamImportState.Importing
}

class XtreamProviderViewModel(
    private val xtreamRepository: XtreamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(XtreamProviderFormState())
    val state: StateFlow<XtreamProviderFormState> = _state.asStateFlow()
    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            xtreamRepository.state.collect { importState ->
                _state.value = _state.value.copy(importState = importState)
            }
        }
    }

    fun updateProviderName(value: String) {
        _state.value = _state.value.copy(providerName = value, errorMessage = null)
    }

    fun updateServerUrl(value: String) {
        _state.value = _state.value.copy(serverUrl = value, errorMessage = null)
    }

    fun updateUsername(value: String) {
        _state.value = _state.value.copy(username = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        _state.value = _state.value.copy(password = value, errorMessage = null)
    }

    fun connect(onSuccess: () -> Unit) {
        val snapshot = _state.value
        if (!snapshot.canSubmit) {
            _state.value = snapshot.copy(errorMessage = "All fields are required.")
            return
        }
        importJob?.cancel()
        importJob = viewModelScope.launch {
            runCatching {
                xtreamRepository.addProvider(
                    XtreamCredentialsInput(
                        displayName = snapshot.providerName,
                        serverUrl = snapshot.serverUrl,
                        username = snapshot.username,
                        password = snapshot.password,
                    ),
                )
            }.onSuccess {
                onSuccess()
            }.onFailure { throwable ->
                _state.value = _state.value.copy(errorMessage = throwable.message ?: "Unable to connect to provider.")
            }
        }
    }
}
