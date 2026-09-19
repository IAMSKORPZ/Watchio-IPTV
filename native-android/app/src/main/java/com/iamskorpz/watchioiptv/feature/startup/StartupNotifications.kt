package com.iamskorpz.watchioiptv.feature.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iamskorpz.watchioiptv.data.updates.UpdateManifest
import com.iamskorpz.watchioiptv.feature.settings.UpdateStatus
import com.iamskorpz.watchioiptv.domain.model.Announcement
import com.iamskorpz.watchioiptv.domain.model.AnnouncementSnapshot
import com.iamskorpz.watchioiptv.domain.model.AnnouncementType
import com.iamskorpz.watchioiptv.ui.components.WatchioButton
import com.iamskorpz.watchioiptv.ui.components.WatchioButtonVariant
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioRadii
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioSpacing
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioTypography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text

internal fun selectStartupAnnouncement(
    snapshot: AnnouncementSnapshot,
    shownIds: Set<String>,
): Announcement? = snapshot.items
    .asSequence()
    .filterNot { it.isDismissed }
    .map { it.announcement }
    .filterNot { it.type == AnnouncementType.UPDATE }
    .filter { it.dismissible }
    .firstOrNull { it.id !in shownIds }

internal sealed interface StartupNotification {
    data class Update(val manifest: UpdateManifest) : StartupNotification
    data class RemoteAnnouncement(val announcement: Announcement) : StartupNotification
}

internal fun resolveStartupNotification(
    updateStatus: UpdateStatus,
    manifest: UpdateManifest?,
    deferredUpdateCode: Int?,
    snapshot: AnnouncementSnapshot,
    shownAnnouncementId: String?,
    updaterScreenOpen: Boolean,
): StartupNotification? {
    val availableUpdate = manifest?.takeIf {
        updateStatus == UpdateStatus.UpdateAvailable &&
            deferredUpdateCode != it.versionCode &&
            !updaterScreenOpen
    }
    if (availableUpdate != null) return StartupNotification.Update(availableUpdate)
    if (updateStatus == UpdateStatus.Idle || updateStatus == UpdateStatus.Checking || shownAnnouncementId != null) return null
    return selectStartupAnnouncement(snapshot, emptySet())?.let(StartupNotification::RemoteAnnouncement)
}

@Composable
internal fun StartupUpdateModal(
    manifest: UpdateManifest,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    val updateFocus = remember { FocusRequester() }
    val laterFocus = remember { FocusRequester() }
    val mandatory = manifest.mandatory
    StartupDialog(
        testTag = "startup-update-modal",
        onDismissRequest = { if (!mandatory) onLater() },
        dismissOnBackPress = !mandatory,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .testTag("startup-update-content"),
            verticalArrangement = Arrangement.spacedBy(LocalWatchioSpacing.current.md),
        ) {
        Text(
            if (mandatory) "Update required" else "Update available",
            color = LocalWatchioColors.current.textPrimary,
            style = LocalWatchioTypography.current.screenTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "A new version of Watchio is available.",
            color = LocalWatchioColors.current.textSecondary,
            style = LocalWatchioTypography.current.body,
        )
        Text(
            "Version ${manifest.versionName}",
            color = LocalWatchioColors.current.moviesAccent,
            style = LocalWatchioTypography.current.cardTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("startup-update-version"),
        )
        if (manifest.publishedAt.isNotBlank()) {
            Text(manifest.publishedAt, color = LocalWatchioColors.current.textMuted, style = LocalWatchioTypography.current.label)
        }
        if (manifest.releaseNotes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("startup-update-notes"),
                verticalArrangement = Arrangement.spacedBy(LocalWatchioSpacing.current.xs),
            ) {
                Text("What's new", color = LocalWatchioColors.current.textPrimary, fontWeight = FontWeight.Bold)
                manifest.releaseNotes.forEach { note ->
                    Text("• $note", color = LocalWatchioColors.current.textSecondary, style = LocalWatchioTypography.current.body)
                }
            }
        }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LocalWatchioSpacing.current.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!mandatory) {
                WatchioButton(
                    text = "LATER",
                    onClick = onLater,
                    variant = WatchioButtonVariant.Secondary,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(laterFocus)
                        .focusProperties { right = updateFocus; up = laterFocus; down = laterFocus }
                        .testTag("startup-update-later"),
                )
            }
            WatchioButton(
                text = "UPDATE",
                onClick = onUpdate,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(updateFocus)
                    .focusProperties {
                        if (!mandatory) left = laterFocus
                        right = updateFocus
                        up = updateFocus
                        down = updateFocus
                    }
                    .testTag("startup-update-action"),
            )
        }
    }
    LaunchedEffect(manifest.versionCode) { updateFocus.requestFocus() }
}

@Composable
internal fun StartupAnnouncementModal(
    announcement: Announcement,
    onDismiss: () -> Unit,
) {
    val dismissFocus = remember { FocusRequester() }
    StartupDialog(
        testTag = "startup-announcement-modal",
        onDismissRequest = { if (announcement.dismissible) onDismiss() },
        dismissOnBackPress = announcement.dismissible,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .testTag("startup-announcement-message"),
            verticalArrangement = Arrangement.spacedBy(LocalWatchioSpacing.current.md),
        ) {
            Text(
                announcement.title,
                color = LocalWatchioColors.current.textPrimary,
                style = LocalWatchioTypography.current.screenTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("startup-announcement-title"),
            )
            Text(announcement.body, color = LocalWatchioColors.current.textSecondary, style = LocalWatchioTypography.current.body)
            if (announcement.publishedAt.isNotBlank()) {
                Text(announcement.publishedAt, color = LocalWatchioColors.current.textMuted, style = LocalWatchioTypography.current.label)
            }
        }
        if (announcement.dismissible) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WatchioButton(
                    text = "DISMISS",
                    onClick = onDismiss,
                    modifier = Modifier
                        .widthIn(min = 150.dp)
                        .focusRequester(dismissFocus)
                        .focusProperties {
                            left = dismissFocus
                            right = dismissFocus
                            up = dismissFocus
                            down = dismissFocus
                        }
                        .testTag("startup-announcement-dismiss"),
                )
            }
            LaunchedEffect(announcement.id) { dismissFocus.requestFocus() }
        }
    }
}

@Composable
private fun StartupDialog(
    testTag: String,
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(24.dp)
                .testTag("$testTag-backdrop"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(LocalWatchioColors.current.surfaceElevated, RoundedCornerShape(LocalWatchioRadii.current.lg))
                    .padding(LocalWatchioSpacing.current.xl)
                    .testTag(testTag),
                verticalArrangement = Arrangement.spacedBy(LocalWatchioSpacing.current.md),
                content = content,
            )
        }
    }
}
