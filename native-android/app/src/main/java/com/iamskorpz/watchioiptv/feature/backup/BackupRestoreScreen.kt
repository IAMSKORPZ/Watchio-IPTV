package com.iamskorpz.watchioiptv.feature.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupRestoreUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val includeCredentials: Boolean = false,
    val busy: Boolean = false,
    val pendingExport: ByteArray? = null,
    val exportRequestId: Int = 0,
    val restorePayload: WatchioBackupPayload? = null,
    val preview: BackupPreview? = null,
    val message: String? = null,
    val error: String? = null,
)

class BackupRestoreViewModel(private val repository: WatchioBackupRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupRestoreUiState())
    val state = mutableState.asStateFlow()

    fun setPassword(value: String) { mutableState.value = mutableState.value.copy(password = value, error = null) }
    fun setConfirmPassword(value: String) { mutableState.value = mutableState.value.copy(confirmPassword = value, error = null) }
    fun setIncludeCredentials(value: Boolean) { mutableState.value = mutableState.value.copy(includeCredentials = value) }

    fun createBackup() {
        val current = mutableState.value
        if (current.password.length < 10) return fail("Use a backup password with at least 10 characters.")
        if (current.password != current.confirmPassword) return fail("Backup passwords do not match.")
        viewModelScope.launch {
            mutableState.value = current.copy(busy = true, error = null, message = null)
            runCatching { repository.create(current.password.toCharArray(), current.includeCredentials) }
                .onSuccess { bytes -> mutableState.value = mutableState.value.copy(busy = false, pendingExport = bytes, exportRequestId = current.exportRequestId + 1) }
                .onFailure { fail(it.safeMessage("Backup creation failed.")) }
        }
    }

    fun writeBackup(resolver: ContentResolver, uri: Uri?) {
        val bytes = mutableState.value.pendingExport ?: return
        if (uri == null) {
            bytes.fill(0)
            mutableState.value = mutableState.value.copy(
                pendingExport = null, password = "", confirmPassword = "", error = "Backup export cancelled.",
            )
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "w")?.use { it.write(bytes); it.flush() }
                        ?: error("Selected destination is unavailable.")
                }
            }.onSuccess {
                bytes.fill(0)
                mutableState.value = mutableState.value.copy(busy = false, pendingExport = null, password = "", confirmPassword = "", message = "Backup created successfully.", error = null)
            }.onFailure {
                bytes.fill(0)
                mutableState.value = mutableState.value.copy(
                    busy = false, pendingExport = null, password = "", confirmPassword = "", error = it.safeMessage("Unable to write backup."),
                )
            }
        }
    }

    fun readBackup(resolver: ContentResolver, uri: Uri?) {
        val current = mutableState.value
        if (uri == null) return
        if (current.password.length < 10) return fail("Enter the backup password before selecting a file.")
        viewModelScope.launch {
            mutableState.value = current.copy(busy = true, error = null, message = null, preview = null, restorePayload = null)
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        val result = input.readBytes()
                        require(result.size <= WatchioBackupCrypto.MAX_BACKUP_BYTES) { "Backup file is too large." }
                        result
                    } ?: error("Selected backup is unavailable.")
                }
                try {
                    repository.preview(bytes, current.password.toCharArray())
                } finally {
                    bytes.fill(0)
                }
            }.onSuccess { (payload, preview) ->
                mutableState.value = mutableState.value.copy(busy = false, restorePayload = payload, preview = preview, error = null)
            }.onFailure { fail(it.safeMessage("Unable to read backup.")) }
        }
    }

    fun confirmRestore() {
        val payload = mutableState.value.restorePayload ?: return fail("Select and validate a backup first.")
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null, message = null)
            runCatching { repository.restore(payload) }
                .onSuccess { mutableState.value = BackupRestoreUiState(message = "Backup restored. Restart Watchio before using restored accounts.") }
                .onFailure { fail(it.safeMessage("Restore failed. Existing data was not intentionally deleted.")) }
        }
    }

    private fun fail(message: String) { mutableState.value = mutableState.value.copy(busy = false, error = message) }
    private fun Throwable.safeMessage(fallback: String) = message?.takeIf { it.length <= 160 && !it.contains("http", true) } ?: fallback
}

@Composable
fun BackupRestoreScreen(viewModel: BackupRestoreViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { viewModel.writeBackup(resolver, it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.readBackup(resolver, it) }
    var handledExportId by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.exportRequestId) {
        if (state.pendingExport != null && state.exportRequestId != handledExportId) {
            handledExportId = state.exportRequestId
            export.launch("Watchio-${System.currentTimeMillis()}.watchio")
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Encrypted, user-controlled backup")
        Text("Includes providers, favourites, history, settings and EPG sources. Offline videos and caches are excluded.")
        OutlinedTextField(
            value = state.password, onValueChange = viewModel::setPassword, label = { Text("Backup password") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.confirmPassword, onValueChange = viewModel::setConfirmPassword, label = { Text("Confirm password for export") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Include credentials")
                Text("Credentials are protected by backup password encryption. Disable to require sign-in after restore.")
            }
            Switch(checked = state.includeCredentials, onCheckedChange = viewModel::setIncludeCredentials, enabled = !state.busy)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::createBackup, enabled = !state.busy) { Text("Create Backup") }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }, enabled = !state.busy) { Text("Restore Backup") }
        }
        if (state.busy) CircularProgressIndicator()
        state.error?.let { Text(it) }
        state.message?.let { Text(it) }
        state.preview?.let { preview ->
            HorizontalDivider()
            Text("Restore preview")
            Text("Created ${DateFormat.getDateTimeInstance().format(Date(preview.createdAtEpochMs))} with Watchio ${preview.applicationVersion}")
            Text("${preview.providers} providers, ${preview.favorites} favourites, ${preview.history} history items, ${preview.epgSources} EPG sources")
            Text(if (preview.includesCredentials) "Encrypted credentials included" else "Credentials excluded; sign-in will be required")
            Text("Existing records are merged. Nothing is silently deleted.")
            Button(onClick = viewModel::confirmRestore, enabled = !state.busy) { Text("Confirm Restore") }
        }
        HorizontalDivider()
        Text("Keep your password safe. Watchio cannot recover it. TV file pickers vary by device and may require an installed file manager.")
    }
}

fun backupRestoreFactory(repository: WatchioBackupRepository) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupRestoreViewModel(repository) as T
}
