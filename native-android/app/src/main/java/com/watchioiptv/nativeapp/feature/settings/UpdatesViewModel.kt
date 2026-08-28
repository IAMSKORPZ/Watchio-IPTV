package com.watchioiptv.nativeapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.data.updates.InstalledVersion
import com.watchioiptv.nativeapp.data.updates.UpdateAvailability
import com.watchioiptv.nativeapp.data.updates.UpdateManifest
import com.watchioiptv.nativeapp.data.updates.UpdateRepository
import com.watchioiptv.nativeapp.data.updates.VerifiedUpdateFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdatesUiState(
    val installed: InstalledVersion,
    val status: UpdateStatus = UpdateStatus.Idle,
    val manifest: UpdateManifest? = null,
    val verifiedFile: VerifiedUpdateFile? = null,
    val progressPercent: Int? = null,
    val errorMessage: String? = null,
) {
    val busy: Boolean = status == UpdateStatus.Checking || status == UpdateStatus.Downloading || status == UpdateStatus.Verifying
}

enum class UpdateStatus {
    Idle,
    Checking,
    UpToDate,
    DevelopmentBuildNewer,
    UpdateAvailable,
    Downloading,
    Verifying,
    ReadyToInstall,
    InstallPermissionRequired,
    Error,
}

class UpdatesViewModel(
    private val repository: UpdateRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UpdatesUiState(installed = repository.installedVersion()))
    val state: StateFlow<UpdatesUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (mutableState.value.busy) return
        job = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(status = UpdateStatus.Checking, errorMessage = null, progressPercent = null)
            runCatching { repository.checkForUpdates() }
                .onSuccess { result ->
                    val status = when (result.status) {
                        UpdateAvailability.UpdateAvailable -> UpdateStatus.UpdateAvailable
                        UpdateAvailability.DevelopmentBuildNewer -> UpdateStatus.DevelopmentBuildNewer
                        UpdateAvailability.UpToDate -> UpdateStatus.UpToDate
                    }
                    mutableState.value = mutableState.value.copy(
                        installed = result.installed,
                        status = status,
                        manifest = result.manifest,
                        verifiedFile = null,
                        errorMessage = null,
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        status = UpdateStatus.Error,
                        errorMessage = userMessage(error),
                    )
                }
        }
    }

    fun downloadUpdate() {
        val manifest = mutableState.value.manifest ?: return
        if (mutableState.value.busy) return
        job = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(status = UpdateStatus.Downloading, progressPercent = null, errorMessage = null)
            runCatching {
                repository.downloadAndVerify(manifest) { downloaded, total ->
                    mutableState.value = mutableState.value.copy(
                        status = if (total == null) UpdateStatus.Downloading else UpdateStatus.Downloading,
                        progressPercent = total?.let { ((downloaded * 100) / it).toInt().coerceIn(0, 100) },
                    )
                }.also {
                    mutableState.value = mutableState.value.copy(status = UpdateStatus.Verifying, progressPercent = 100)
                }
            }.onSuccess { file ->
                mutableState.value = mutableState.value.copy(status = UpdateStatus.ReadyToInstall, verifiedFile = file, errorMessage = null)
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(status = UpdateStatus.Error, verifiedFile = null, errorMessage = userMessage(error))
            }
        }
    }

    fun installPermissionRequired() {
        mutableState.value = mutableState.value.copy(status = UpdateStatus.InstallPermissionRequired)
    }

    private fun userMessage(error: Throwable): String {
        return when (error.message) {
            "Update verification failed." -> "Update verification failed. The downloaded file did not match the expected checksum."
            "Update channel mismatch." -> "This update is for a different channel."
            "Update checksum is invalid." -> "Update information is invalid."
            "Update download URL is invalid." -> "Update information is invalid."
            "Update package does not match Watchio." -> "Update package does not match Watchio."
            else -> "Unable to check for updates. Check your internet connection and try again."
        }
    }
}
