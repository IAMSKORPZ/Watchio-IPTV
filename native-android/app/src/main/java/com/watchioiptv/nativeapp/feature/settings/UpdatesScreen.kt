package com.watchioiptv.nativeapp.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.watchioiptv.nativeapp.BuildConfig
import com.watchioiptv.nativeapp.ui.components.WatchioButton
import com.watchioiptv.nativeapp.ui.components.WatchioButtonVariant
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioSpacing
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography
import java.io.File

@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onPermissionRequired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("updates-content"),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        WatchioCard(accent = colors.liveTvAccent, minHeight = 0.dp, contentDescription = "Update status") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceCard.copy(alpha = 0.78f))
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                SettingsInfoRow("Current Version", state.installed.versionName)
                SettingsInfoRow("Build", state.installed.versionCode.toString())
                SettingsInfoRow("Update Channel", "Development")
                SettingsInfoRow("Update Status", statusLabel(state))
            }
        }

        if (state.status == UpdateStatus.Checking) {
            ProgressPanel("Checking for updates...", indeterminate = true)
        }
        if (state.status == UpdateStatus.Downloading) {
            ProgressPanel(
                title = "Downloading Update",
                percent = state.progressPercent,
                indeterminate = state.progressPercent == null,
            )
        }
        if (state.status == UpdateStatus.Verifying) {
            ProgressPanel("Verifying update...", indeterminate = true)
        }

        state.manifest?.let { manifest ->
            if (state.status == UpdateStatus.UpdateAvailable || state.status == UpdateStatus.ReadyToInstall || state.status == UpdateStatus.Downloading || state.status == UpdateStatus.Verifying) {
                WatchioCard(accent = colors.moviesAccent, minHeight = 0.dp, contentDescription = "Release notes") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceCard.copy(alpha = 0.78f))
                            .padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Text("New Version", color = colors.textMuted, style = type.label)
                        Text(manifest.versionName, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold)
                        Text("Release Notes", color = colors.textMuted, style = type.label)
                        manifest.releaseNotes.take(8).forEach { note ->
                            Text("• ${note.take(180)}", color = colors.textSecondary, style = type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (state.status == UpdateStatus.UpToDate || state.status == UpdateStatus.DevelopmentBuildNewer) {
            Text("Watchio is up to date.", color = colors.textSecondary, style = type.body, modifier = Modifier.testTag("updates-up-to-date"))
        }
        state.errorMessage?.let {
            Text(it, color = colors.liveTvAccent, style = type.body, modifier = Modifier.testTag("updates-error"))
        }
        if (state.status == UpdateStatus.InstallPermissionRequired) {
            Text("Allow Watchio to install this update.", color = colors.textSecondary, style = type.body)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WatchioButton(
                text = if (state.status == UpdateStatus.Error) "Try Again" else "Check for Updates",
                onClick = onCheck,
                enabled = !state.busy,
                variant = WatchioButtonVariant.Secondary,
                modifier = Modifier.testTag("updates-check"),
            )
            if (state.status == UpdateStatus.UpdateAvailable) {
                WatchioButton(
                    text = "Download Update",
                    onClick = onDownload,
                    enabled = !state.busy,
                    modifier = Modifier.testTag("updates-download"),
                )
            }
            if (state.status == UpdateStatus.ReadyToInstall || state.status == UpdateStatus.InstallPermissionRequired) {
                WatchioButton(
                    text = "Install Update",
                    onClick = {
                        if (!context.canInstallUnknownApps()) {
                            onPermissionRequired()
                            context.openUnknownAppSources()
                        } else {
                            state.verifiedFile?.filePath?.let { context.openPackageInstaller(it) }
                        }
                    },
                    enabled = state.verifiedFile != null,
                    modifier = Modifier.testTag("updates-install"),
                )
            }
        }

        Spacer(Modifier.height(spacing.sm))
        Text(
            "Development build. Android installer asks before installing.",
            color = colors.textMuted,
            style = type.label,
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.textMuted, style = type.body)
        Text(value, color = colors.textPrimary, style = type.body, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressPanel(title: String, percent: Int? = null, indeterminate: Boolean) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    WatchioCard(accent = colors.seriesAccent, minHeight = 0.dp, contentDescription = title) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceCard.copy(alpha = 0.78f))
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                CircularProgressIndicator(color = colors.seriesAccent)
                Text(title, color = colors.textPrimary, style = type.body, fontWeight = FontWeight.Bold)
            }
            if (indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.seriesAccent)
            } else {
                LinearProgressIndicator(progress = { (percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = colors.seriesAccent)
                Text("${percent ?: 0}%", color = colors.textSecondary, style = type.label)
            }
        }
    }
}

private fun statusLabel(state: UpdatesUiState): String = when (state.status) {
    UpdateStatus.Idle -> "Ready"
    UpdateStatus.Checking -> "Checking"
    UpdateStatus.UpToDate -> "Up to date"
    UpdateStatus.DevelopmentBuildNewer -> "Up to date"
    UpdateStatus.UpdateAvailable -> "Update available"
    UpdateStatus.Downloading -> "Downloading"
    UpdateStatus.Verifying -> "Verifying"
    UpdateStatus.ReadyToInstall -> "Update ready"
    UpdateStatus.InstallPermissionRequired -> "Install permission required"
    UpdateStatus.Error -> "Error"
}

private fun Context.canInstallUnknownApps(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
}

private fun Context.openUnknownAppSources() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun Context.openPackageInstaller(path: String) {
    val file = File(path)
    val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}
