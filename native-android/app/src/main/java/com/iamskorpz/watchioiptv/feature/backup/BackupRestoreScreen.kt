package com.iamskorpz.watchioiptv.feature.backup

import android.content.ContentResolver
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
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

    fun exportPickerUnavailable(error: Throwable) {
        mutableState.value.pendingExport?.fill(0)
        mutableState.value = mutableState.value.copy(
            pendingExport = null,
            password = "",
            confirmPassword = "",
            busy = false,
            error = error.safeMessage("No compatible document picker is available to save this backup."),
        )
    }

    fun restorePickerUnavailable(error: Throwable) {
        fail(error.safeMessage("No compatible document picker is available to select a backup."))
    }

    fun readBackup(resolver: ContentResolver, uri: Uri?) {
        val current = mutableState.value
        if (uri == null) return fail("Backup selection cancelled.")
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
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    launchCreateDocument: ((String) -> Unit)? = null,
    launchOpenDocument: ((Array<String>) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }
    val credentialsFocusRequester = remember { FocusRequester() }
    val createFocusRequester = remember { FocusRequester() }
    val restoreFocusRequester = remember { FocusRequester() }
    val confirmRestoreFocusRequester = remember { FocusRequester() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        viewModel.writeBackup(resolver, it)
        createFocusRequester.requestFocus()
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.readBackup(resolver, it)
        restoreFocusRequester.requestFocus()
    }
    val inputModeManager = LocalInputModeManager.current
    var handledExportId by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.exportRequestId) {
        if (state.pendingExport != null && state.exportRequestId != handledExportId) {
            handledExportId = state.exportRequestId
            runCatching {
                (launchCreateDocument ?: export::launch)("Watchio-${System.currentTimeMillis()}.watchio")
            }.onFailure { error ->
                if (error is ActivityNotFoundException || error is SecurityException) {
                    viewModel.exportPickerUnavailable(error)
                    createFocusRequester.requestFocus()
                } else {
                    throw error
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (inputModeManager.inputMode == InputMode.Keyboard) passwordFocusRequester.requestFocus()
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Encrypted, user-controlled backup")
        Text("Includes providers, favourites, history, settings and EPG sources. Offline videos and caches are excluded.")
        OutlinedTextField(
            value = state.password, onValueChange = viewModel::setPassword, label = { Text("Backup password") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .backupFocusNavigation(down = confirmPasswordFocusRequester)
                .testTag("backup-password"),
        )
        OutlinedTextField(
            value = state.confirmPassword, onValueChange = viewModel::setConfirmPassword, label = { Text("Confirm password for export") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(confirmPasswordFocusRequester)
                .backupFocusNavigation(up = passwordFocusRequester, down = credentialsFocusRequester)
                .testTag("backup-password-confirm"),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Include credentials")
                Text("Credentials are protected by backup password encryption. Disable to require sign-in after restore.")
            }
            Switch(
                checked = state.includeCredentials,
                onCheckedChange = viewModel::setIncludeCredentials,
                enabled = !state.busy,
                modifier = Modifier.focusRequester(credentialsFocusRequester)
                    .backupFocusNavigation(up = confirmPasswordFocusRequester, down = createFocusRequester)
                    .testTag("backup-credentials"),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 420.dp
            val actionModifier = if (compact) Modifier.fillMaxWidth() else Modifier
            @Composable fun CreateAction() {
                Button(
                    onClick = viewModel::createBackup,
                    enabled = !state.busy,
                    modifier = actionModifier.focusRequester(createFocusRequester)
                        .backupFocusNavigation(
                            up = credentialsFocusRequester,
                            down = restoreFocusRequester.takeIf { compact },
                            right = restoreFocusRequester.takeIf { !compact },
                        )
                        .testTag("backup-create"),
                ) { Text("Create Backup") }
            }
            @Composable fun RestoreAction() {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            (launchOpenDocument ?: restore::launch)(arrayOf("application/octet-stream", "application/json", "*/*"))
                        }.onFailure { error ->
                            if (error is ActivityNotFoundException || error is SecurityException) {
                                viewModel.restorePickerUnavailable(error)
                                restoreFocusRequester.requestFocus()
                            } else {
                                throw error
                            }
                        }
                    },
                    enabled = !state.busy,
                    modifier = actionModifier.focusRequester(restoreFocusRequester)
                        .backupFocusNavigation(
                            up = if (compact) createFocusRequester else credentialsFocusRequester,
                            down = confirmRestoreFocusRequester.takeIf { state.preview != null },
                            left = createFocusRequester.takeIf { !compact },
                        )
                        .testTag("backup-restore"),
                ) { Text("Restore Backup") }
            }
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CreateAction()
                    RestoreAction()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CreateAction()
                    RestoreAction()
                }
            }
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
            Button(
                onClick = viewModel::confirmRestore,
                enabled = !state.busy,
                modifier = Modifier.focusRequester(confirmRestoreFocusRequester)
                    .backupFocusNavigation(up = restoreFocusRequester)
                    .testTag("backup-confirm-restore"),
            ) { Text("Confirm Restore") }
        }
        HorizontalDivider()
        Text("Keep your password safe. Watchio cannot recover it. TV file pickers vary by device and may require an installed file manager.")
    }
}

private fun Modifier.backupFocusNavigation(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val target = when (event.key) {
        Key.DirectionUp -> up
        Key.DirectionDown -> down
        Key.DirectionLeft -> left
        Key.DirectionRight -> right
        else -> null
    } ?: return@onPreviewKeyEvent false
    target.requestFocus()
    true
}

@Composable
fun BackupRecoveryGate(status: BackupRecoveryStatus) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (status == BackupRecoveryStatus.RECOVERING) CircularProgressIndicator()
        Text(if (status == BackupRecoveryStatus.RECOVERING) "Recovering interrupted restore" else "Backup restore recovery required")
        Text(
            if (status == BackupRecoveryStatus.RECOVERING) {
                "Watchio is restoring a consistent application state."
            } else {
                "Watchio preserved the recovery point but could not finish safely. Do not clear app data. Retry after restarting Watchio."
            },
        )
    }
}

fun backupRestoreFactory(repository: WatchioBackupRepository) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupRestoreViewModel(repository) as T
}
