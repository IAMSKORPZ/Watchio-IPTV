package com.watchioiptv.nativeapp.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.watchioiptv.nativeapp.BuildConfig
import com.watchioiptv.nativeapp.data.updates.UpdateManifest
import com.watchioiptv.nativeapp.ui.components.WatchioButton
import com.watchioiptv.nativeapp.ui.components.WatchioButtonVariant
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioSpacing
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography
import java.io.File

// ---------------------------------------------------------------------------
// Screen entry point
// ---------------------------------------------------------------------------

@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onPermissionRequired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("updates-screen"),
    ) {
        WatchioPageHeader(
            title = "UPDATES",
            onBack = onBack,
            testTagPrefix = "updates",
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isTv = maxWidth >= 700.dp
            if (isTv) {
                UpdatesTvLayout(state, onCheck, onDownload, onPermissionRequired)
            } else {
                UpdatesMobileLayout(state, onCheck, onDownload, onPermissionRequired)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TV two-column layout
// ---------------------------------------------------------------------------

@Composable
private fun UpdatesTvLayout(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onPermissionRequired: () -> Unit,
) {
    val spacing = LocalWatchioSpacing.current
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            UpdateStatusCard(state)
            state.manifest?.let { manifest ->
                if (showReleaseNotes(state)) {
                    ReleaseNotesCard(manifest)
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            UpdateActionPanel(state, onCheck, onDownload, onPermissionRequired)
        }
    }
}

// ---------------------------------------------------------------------------
// Mobile stacked layout
// ---------------------------------------------------------------------------

@Composable
private fun UpdatesMobileLayout(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onPermissionRequired: () -> Unit,
) {
    val spacing = LocalWatchioSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        UpdateStatusCard(state)
        UpdateActionPanel(state, onCheck, onDownload, onPermissionRequired)
        state.manifest?.let { manifest ->
            if (showReleaseNotes(state)) {
                ReleaseNotesCard(manifest)
            }
        }
        Spacer(Modifier.height(spacing.sm))
    }
}

// ---------------------------------------------------------------------------
// Status card
// ---------------------------------------------------------------------------

@Composable
private fun UpdateStatusCard(state: UpdatesUiState) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val accentColor = when (state.status) {
        UpdateStatus.UpdateAvailable -> colors.moviesAccent
        UpdateStatus.ReadyToInstall, UpdateStatus.InstallPermissionRequired -> colors.seriesAccent
        UpdateStatus.Error -> colors.liveTvAccent
        else -> colors.liveTvAccent
    }
    val isMandatory = state.manifest?.mandatory == true && state.status == UpdateStatus.UpdateAvailable
    WatchioCard(
        accent = accentColor,
        minHeight = 0.dp,
        contentDescription = "Update status",
        modifier = Modifier.testTag("updates-status-card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceCard.copy(alpha = 0.78f))
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (isMandatory) {
                Text(
                    "UPDATE REQUIRED",
                    color = colors.liveTvAccent,
                    style = type.label,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("updates-mandatory-badge"),
                )
            }
            Text(
                statusHeading(state),
                color = colors.textPrimary,
                style = type.cardTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("updates-status-heading"),
            )
            Text(
                statusSubtitle(state),
                color = colors.textSecondary,
                style = type.body,
                modifier = Modifier.testTag("updates-status-subtitle"),
            )
            Spacer(Modifier.height(spacing.xs))
            UpdateInfoRow("Current Version", state.installed.versionName, testTag = "updates-installed-version")
            UpdateInfoRow("Build", state.installed.versionCode.toString())
            if (state.manifest != null && state.status == UpdateStatus.UpdateAvailable) {
                UpdateInfoRow("New Version", state.manifest.versionName, accentColor, testTag = "updates-new-version")
                UpdateInfoRow("Published", state.manifest.publishedAt)
            }
            UpdateInfoRow("Channel", "Development")
        }
    }
}

// ---------------------------------------------------------------------------
// Release notes card
// ---------------------------------------------------------------------------

@Composable
private fun ReleaseNotesCard(manifest: UpdateManifest) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    WatchioCard(
        accent = colors.moviesAccent,
        minHeight = 0.dp,
        contentDescription = "Release notes",
        modifier = Modifier.testTag("updates-release-notes-card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceCard.copy(alpha = 0.78f))
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                "What's New in ${manifest.versionName}",
                color = colors.textMuted,
                style = type.label,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(spacing.xs))
            manifest.releaseNotes.take(10).forEachIndexed { i, note ->
                Text(
                    "- ${note.take(200)}",
                    color = colors.textSecondary,
                    style = type.body,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("updates-release-note-$i"),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Action panel
// ---------------------------------------------------------------------------

@Composable
private fun UpdateActionPanel(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onPermissionRequired: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val isMandatory = state.manifest?.mandatory == true
    val primaryFocus = remember { FocusRequester() }

    when (state.status) {
        UpdateStatus.Checking -> {
            UpdateProgressCard(
                title = "CHECKING FOR UPDATES",
                detail = "Looking for the latest Watchio build.",
                indeterminate = true,
                testTag = "updates-checking-panel",
            )
        }

        UpdateStatus.Downloading -> {
            UpdateProgressCard(
                title = "DOWNLOADING UPDATE",
                detail = downloadDetail(state) ?: "Preparing download…",
                percent = state.progressPercent,
                indeterminate = state.progressPercent == null,
                testTag = "updates-downloading-panel",
            )
        }

        UpdateStatus.Verifying -> {
            UpdateProgressCard(
                title = "VERIFYING UPDATE",
                detail = "Checking file integrity…",
                indeterminate = true,
                testTag = "updates-verifying-panel",
            )
        }

        UpdateStatus.UpdateAvailable -> {
            LaunchedEffect(Unit) { primaryFocus.requestFocus() }
            WatchioCard(
                accent = colors.moviesAccent,
                minHeight = 0.dp,
                contentDescription = "Update action",
                modifier = Modifier.testTag("updates-available-panel"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceCard.copy(alpha = 0.78f))
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text("UPDATE AVAILABLE", color = colors.moviesAccent, style = type.label, fontWeight = FontWeight.Bold)
                    state.manifest?.let { m ->
                        Text(
                            "Watchio ${m.versionName}",
                            color = colors.textPrimary,
                            style = type.screenTitle,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("updates-available-version"),
                        )
                    }
                    WatchioButton(
                        text = "UPDATE NOW",
                        onClick = onDownload,
                        enabled = !state.busy,
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .focusRequester(primaryFocus)
                            .testTag("updates-download"),
                    )
                    if (!isMandatory) {
                        WatchioButton(
                            text = "LATER",
                            onClick = {},
                            variant = WatchioButtonVariant.Secondary,
                            modifier = Modifier
                                .widthIn(min = 180.dp)
                                .testTag("updates-later"),
                        )
                    }
                }
            }
        }

        UpdateStatus.ReadyToInstall, UpdateStatus.InstallPermissionRequired -> {
            LaunchedEffect(Unit) { primaryFocus.requestFocus() }
            val needsPermission = !context.canInstallUnknownApps()
            WatchioCard(
                accent = colors.seriesAccent,
                minHeight = 0.dp,
                contentDescription = "Install action",
                modifier = Modifier.testTag("updates-ready-panel"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceCard.copy(alpha = 0.78f))
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    val panelTitle = if (state.status == UpdateStatus.InstallPermissionRequired)
                        "INSTALLATION PERMISSION REQUIRED" else "READY TO INSTALL"
                    Text(panelTitle, color = colors.seriesAccent, style = type.label, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.status == UpdateStatus.InstallPermissionRequired)
                            "Allow Watchio to install this update through Android's installer."
                        else
                            "The update has been downloaded and verified. Ready to install.",
                        color = colors.textSecondary,
                        style = type.body,
                    )
                    WatchioButton(
                        text = if (state.status == UpdateStatus.InstallPermissionRequired && needsPermission)
                            "ALLOW INSTALLATION" else "INSTALL UPDATE",
                        onClick = {
                            if (needsPermission) {
                                onPermissionRequired()
                                context.openUnknownAppSources()
                            } else {
                                state.verifiedFile?.filePath?.let { context.openPackageInstaller(it) }
                            }
                        },
                        enabled = state.verifiedFile != null || needsPermission,
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .focusRequester(primaryFocus)
                            .testTag("updates-install"),
                    )
                    Text(
                        "Complete the installation in the Android installer when prompted.",
                        color = colors.textMuted,
                        style = type.label,
                    )
                }
            }
        }

        UpdateStatus.UpToDate, UpdateStatus.DevelopmentBuildNewer -> {
            LaunchedEffect(Unit) { primaryFocus.requestFocus() }
            WatchioCard(
                accent = colors.seriesAccent,
                minHeight = 0.dp,
                contentDescription = "Up to date",
                modifier = Modifier.testTag("updates-up-to-date"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceCard.copy(alpha = 0.78f))
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        UpdatesCheckmark(colors.seriesAccent)
                        Text(
                            "WATCHIO IS UP TO DATE",
                            color = colors.seriesAccent,
                            style = type.label,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (state.status == UpdateStatus.DevelopmentBuildNewer)
                            "This build is newer than the published release manifest."
                        else
                            "You're running the latest development build.",
                        color = colors.textSecondary,
                        style = type.body,
                    )
                    WatchioButton(
                        text = "CHECK AGAIN",
                        onClick = onCheck,
                        variant = WatchioButtonVariant.Secondary,
                        enabled = !state.busy,
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .focusRequester(primaryFocus)
                            .testTag("updates-check"),
                    )
                }
            }
        }

        UpdateStatus.Error -> {
            LaunchedEffect(Unit) { primaryFocus.requestFocus() }
            WatchioCard(
                accent = colors.liveTvAccent,
                minHeight = 0.dp,
                contentDescription = "Update error",
                modifier = Modifier.testTag("updates-error-panel"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceCard.copy(alpha = 0.78f))
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text("UPDATE CHECK FAILED", color = colors.liveTvAccent, style = type.label, fontWeight = FontWeight.Bold)
                    Text(
                        state.errorMessage ?: "Unable to check for updates. Check your internet connection and try again.",
                        color = colors.textSecondary,
                        style = type.body,
                        modifier = Modifier.testTag("updates-error"),
                    )
                    WatchioButton(
                        text = "RETRY",
                        onClick = onCheck,
                        enabled = !state.busy,
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .focusRequester(primaryFocus)
                            .testTag("updates-check"),
                    )
                }
            }
        }

        UpdateStatus.Idle -> {
            LaunchedEffect(Unit) { primaryFocus.requestFocus() }
            WatchioCard(
                accent = colors.liveTvAccent,
                minHeight = 0.dp,
                contentDescription = "Check for updates",
                modifier = Modifier.testTag("updates-idle-panel"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceCard.copy(alpha = 0.78f))
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text("CHECK FOR UPDATES", color = colors.textMuted, style = type.label, fontWeight = FontWeight.Bold)
                    Text(
                        "Tap below to check the development channel for the latest Watchio build.",
                        color = colors.textSecondary,
                        style = type.body,
                    )
                    WatchioButton(
                        text = "CHECK FOR UPDATES",
                        onClick = onCheck,
                        variant = WatchioButtonVariant.Secondary,
                        enabled = !state.busy,
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .focusRequester(primaryFocus)
                            .testTag("updates-check"),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Progress card
// ---------------------------------------------------------------------------

@Composable
private fun UpdateProgressCard(
    title: String,
    detail: String,
    percent: Int? = null,
    indeterminate: Boolean,
    testTag: String,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    WatchioCard(accent = colors.seriesAccent, minHeight = 0.dp, contentDescription = title, modifier = Modifier.testTag(testTag)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceCard.copy(alpha = 0.78f))
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(title, color = colors.textPrimary, style = type.body, fontWeight = FontWeight.Bold)
            Text(detail, color = colors.textSecondary, style = type.label)
            if (indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.seriesAccent)
            } else {
                LinearProgressIndicator(
                    progress = { (percent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.seriesAccent,
                )
                Text(
                    "${percent ?: 0}%",
                    color = colors.seriesAccent,
                    style = type.label,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("updates-progress-percent"),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Info row
// ---------------------------------------------------------------------------

@Composable
private fun UpdateInfoRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    testTag: String? = null,
) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.textMuted, style = type.body)
        Text(
            value,
            color = valueColor ?: colors.textPrimary,
            style = type.body,
            fontWeight = FontWeight.Bold,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        )
    }
}

// ---------------------------------------------------------------------------
// Checkmark
// ---------------------------------------------------------------------------

@Composable
private fun UpdatesCheckmark(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val strokeWidth = 2.5.dp.toPx()
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.52f), Offset(size.width * 0.42f, size.height * 0.78f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.42f, size.height * 0.78f), Offset(size.width * 0.85f, size.height * 0.24f), strokeWidth, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// State helpers
// ---------------------------------------------------------------------------

private fun showReleaseNotes(state: UpdatesUiState): Boolean =
    state.status in listOf(
        UpdateStatus.UpdateAvailable,
        UpdateStatus.Downloading,
        UpdateStatus.Verifying,
        UpdateStatus.ReadyToInstall,
        UpdateStatus.InstallPermissionRequired,
    )

private fun statusHeading(state: UpdatesUiState): String = when (state.status) {
    UpdateStatus.Idle -> "Watchio Updates"
    UpdateStatus.Checking -> "Checking for Updates"
    UpdateStatus.UpToDate, UpdateStatus.DevelopmentBuildNewer -> "Watchio is Up to Date"
    UpdateStatus.UpdateAvailable -> "Update Available"
    UpdateStatus.Downloading -> "Downloading Update"
    UpdateStatus.Verifying -> "Verifying Update"
    UpdateStatus.ReadyToInstall -> "Update Ready"
    UpdateStatus.InstallPermissionRequired -> "Permission Required"
    UpdateStatus.Error -> "Unable to Update"
}

private fun statusSubtitle(state: UpdatesUiState): String = when (state.status) {
    UpdateStatus.Idle -> "Check the development channel when ready."
    UpdateStatus.Checking -> "Looking for the latest development build…"
    UpdateStatus.UpToDate -> "You're running the latest development build."
    UpdateStatus.DevelopmentBuildNewer -> "This build is newer than the published release manifest."
    UpdateStatus.UpdateAvailable -> "A newer development build is available."
    UpdateStatus.Downloading -> downloadDetail(state) ?: "Downloading the update package."
    UpdateStatus.Verifying -> "Checking file integrity…"
    UpdateStatus.ReadyToInstall -> "The update has been downloaded and verified."
    UpdateStatus.InstallPermissionRequired -> "Allow Watchio to hand this update to Android's installer."
    UpdateStatus.Error -> state.errorMessage ?: "Unable to check for updates."
}

private fun downloadDetail(state: UpdatesUiState): String? {
    val downloaded = state.downloadedBytes ?: return null
    val total = state.totalBytes
    return if (total != null) {
        "${formatBytes(downloaded)} / ${formatBytes(total)}"
    } else {
        "${formatBytes(downloaded)} downloaded"
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}

// ---------------------------------------------------------------------------
// Context helpers — install flow (preserved exactly from original)
// ---------------------------------------------------------------------------

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
