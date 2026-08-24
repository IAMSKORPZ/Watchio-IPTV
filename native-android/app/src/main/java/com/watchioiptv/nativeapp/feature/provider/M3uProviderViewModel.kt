package com.watchioiptv.nativeapp.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.data.m3u.M3uFileInput
import com.watchioiptv.nativeapp.data.m3u.M3uImportState
import com.watchioiptv.nativeapp.data.m3u.M3uRepository
import com.watchioiptv.nativeapp.data.m3u.M3uUrlInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class M3uProviderFormState(
    val providerName: String = "",
    val playlistUrl: String = "",
    val userAgent: String = "",
    val fileUri: String = "",
    val importState: M3uImportState = M3uImportState.Idle,
    val errorMessage: String? = null,
) {
    val canSubmitUrl: Boolean =
        providerName.isNotBlank() && playlistUrl.isNotBlank() && importState !is M3uImportState.Importing
    val canSubmitFile: Boolean =
        providerName.isNotBlank() && fileUri.isNotBlank() && importState !is M3uImportState.Importing
}

class M3uProviderViewModel(
    private val repository: M3uRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(M3uProviderFormState())
    val state: StateFlow<M3uProviderFormState> = _state.asStateFlow()
    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            repository.state.collect { _state.value = _state.value.copy(importState = it) }
        }
    }

    fun updateProviderName(value: String) {
        _state.value = _state.value.copy(providerName = value, errorMessage = null)
    }

    fun updatePlaylistUrl(value: String) {
        _state.value = _state.value.copy(playlistUrl = value, errorMessage = null)
    }

    fun updateUserAgent(value: String) {
        _state.value = _state.value.copy(userAgent = value, errorMessage = null)
    }

    fun updateFileUri(value: String) {
        _state.value = _state.value.copy(fileUri = value, errorMessage = null)
    }

    fun connectUrl(onSuccess: () -> Unit) {
        val snapshot = _state.value
        if (!snapshot.canSubmitUrl) {
            _state.value = snapshot.copy(errorMessage = "Provider name and playlist URL are required.")
            return
        }
        importJob?.cancel()
        importJob = viewModelScope.launch {
            runCatching {
                repository.addUrlProvider(
                    M3uUrlInput(
                        displayName = snapshot.providerName,
                        playlistUrl = snapshot.playlistUrl,
                        userAgent = snapshot.userAgent.takeIf { it.isNotBlank() },
                    ),
                )
            }.onSuccess { onSuccess() }
                .onFailure { _state.value = _state.value.copy(errorMessage = it.message ?: "Unable to download playlist.") }
        }
    }

    fun connectFile(onSuccess: () -> Unit) {
        val snapshot = _state.value
        if (!snapshot.canSubmitFile) {
            _state.value = snapshot.copy(errorMessage = "Provider name and playlist file are required.")
            return
        }
        importJob?.cancel()
        importJob = viewModelScope.launch {
            runCatching {
                repository.addFileProvider(M3uFileInput(snapshot.providerName, snapshot.fileUri))
            }.onSuccess { onSuccess() }
                .onFailure { _state.value = _state.value.copy(errorMessage = it.message ?: "Playlist file could not be opened.") }
        }
    }
}
