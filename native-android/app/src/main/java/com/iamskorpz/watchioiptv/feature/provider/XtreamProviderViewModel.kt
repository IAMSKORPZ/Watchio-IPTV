package com.iamskorpz.watchioiptv.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.data.xtream.XtreamCredentialsInput
import com.iamskorpz.watchioiptv.data.xtream.XtreamImportState
import com.iamskorpz.watchioiptv.data.xtream.XtreamImportStage
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.data.xtream.WatchioDnsResolver
import com.iamskorpz.watchioiptv.data.xtream.WatchioDnsResult
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class XtreamProviderFormState(
    val providerName: String = "",
    val username: String = "",
    val password: String = "",
    val importState: XtreamImportState = XtreamImportState.Idle,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean =
        providerName.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
            importState !is XtreamImportState.Importing
}

class XtreamProviderViewModel(
    private val xtreamRepository: XtreamRepository,
    private val dnsResolver: WatchioDnsResolver? = null,
    private val endpointManager: WatchioEndpointManager? = null,
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

    fun updateUsername(value: String) {
        _state.value = _state.value.copy(username = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        _state.value = _state.value.copy(password = value, errorMessage = null)
    }

    fun connect(onSuccess: () -> Unit) {
        if (importJob?.isActive == true) return
        val snapshot = _state.value
        if (!snapshot.canSubmit) {
            _state.value = snapshot.copy(errorMessage = "All fields are required.")
            return
        }
        _state.value = snapshot.copy(
            importState = XtreamImportState.Importing(XtreamImportStage.Authenticating, snapshot.providerName),
            errorMessage = null,
        )
        importJob = viewModelScope.launch {
            runCatching {
                xtreamRepository.addProviderTwoPhase(
                    XtreamCredentialsInput(
                        displayName = snapshot.providerName,
                        username = snapshot.username,
                        password = snapshot.password,
                        managed = true,
                    ),
                )
            }.onSuccess {
                onSuccess()
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    importState = XtreamImportState.Idle,
                    errorMessage = throwable.message ?: "Unable to connect to provider.",
                )
            }
        }
    }

    fun connectDns(onSuccess: () -> Unit) {
        if (importJob?.isActive == true) return
        val snapshot = _state.value
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            _state.value = snapshot.copy(errorMessage = "Username and password are required.")
            return
        }
        val resolver = dnsResolver
        val manager = endpointManager
        if (resolver == null || manager == null) {
            _state.value = snapshot.copy(errorMessage = "DNS Login is not available yet. Please use Xtream login.")
            return
        }
        val displayName = snapshot.providerName.ifBlank { "Watchio" }
        _state.value = snapshot.copy(
            importState = XtreamImportState.Importing(XtreamImportStage.Authenticating, displayName),
            errorMessage = null,
        )
        importJob = viewModelScope.launch {
            runCatching {
                when (val result = resolver.resolve(snapshot.username.trim())) {
                    WatchioDnsResult.NotFound -> throw IllegalArgumentException("Username not recognised.")
                    is WatchioDnsResult.Found -> {
                        val endpoint = manager.dnsEndpoint(result.endpoint.id, result.endpoint.url)
                        xtreamRepository.addDnsProviderTwoPhase(
                            XtreamCredentialsInput(displayName, username = snapshot.username, password = snapshot.password, managed = true),
                            endpoint,
                        )
                    }
                }
            }.onSuccess { onSuccess() }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        importState = XtreamImportState.Idle,
                        errorMessage = throwable.message ?: "Unable to sign in.",
                    )
                }
        }
    }
}
