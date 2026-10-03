package com.iamskorpz.watchioiptv.feature.announcements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iamskorpz.watchioiptv.domain.model.Announcement
import com.iamskorpz.watchioiptv.domain.model.AnnouncementAction
import com.iamskorpz.watchioiptv.domain.model.AnnouncementItem
import com.iamskorpz.watchioiptv.domain.model.AnnouncementPriority
import com.iamskorpz.watchioiptv.domain.model.AnnouncementType
import com.iamskorpz.watchioiptv.ui.components.WatchioButton
import com.iamskorpz.watchioiptv.ui.components.WatchioButtonVariant
import com.iamskorpz.watchioiptv.ui.components.WatchioCard
import com.iamskorpz.watchioiptv.ui.components.WatchioPageHeader
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioSpacing
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioTypography
import com.iamskorpz.watchioiptv.ui.theme.watchioScreenBackgroundColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnnouncementsScreen(
    state: AnnouncementsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onCloseDetails: () -> Unit,
    onMarkAllRead: () -> Unit,
    onMailboxChange: (AnnouncementMailbox) -> Unit = {},
    onArchive: (String) -> Unit = {},
    onRestore: (String) -> Unit = {},
    onAction: (AnnouncementAction) -> Unit,
) {
    val selected = state.selected
    if (selected != null) {
        AnnouncementDetails(
            item = selected,
            onBack = onCloseDetails,
            onArchive = { onArchive(selected.announcement.id) },
            onRestore = { onRestore(selected.announcement.id) },
            onAction = onAction,
        )
        return
    }

    val colors = LocalWatchioColors.current
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(watchioScreenBackgroundColor())
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("announcements-screen"),
    ) {
        WatchioPageHeader(
            title = "ANNOUNCEMENTS",
            onBack = onBack,
            testTagPrefix = "announcements",
        )
        Spacer(Modifier.height(12.dp))
        AnnouncementMailboxControls(state, onMailboxChange, onMarkAllRead)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && state.visibleItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.liveTvAccent, modifier = Modifier.testTag("announcements-loading"))
                }
                state.error && state.visibleItems.isEmpty() -> AnnouncementMessage(
                    title = "Announcements unavailable",
                    body = "Check your connection and try again.",
                    button = "RETRY",
                    onClick = onRefresh,
                    testTag = "announcements-error",
                )
                state.visibleItems.isEmpty() -> AnnouncementMessage(
                    title = if (state.mailbox == AnnouncementMailbox.INBOX) "No announcements" else "No archived announcements",
                    body = if (state.mailbox == AnnouncementMailbox.INBOX) "Check back later for Watchio news and alerts." else "Announcements you archive will appear here.",
                    testTag = if (state.mailbox == AnnouncementMailbox.INBOX) "announcements-empty" else "announcements-archived-empty",
                )
                else -> AnnouncementList(state.visibleItems, state.mailbox, state.focusId, onOpen, onArchive, onRestore)
            }
        }
    }
}

@Composable
private fun AnnouncementMailboxControls(
    state: AnnouncementsUiState,
    onMailboxChange: (AnnouncementMailbox) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("announcements-mailboxes")) {
        val tabs: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WatchioButton(
                    text = "INBOX (${state.snapshot.inboxItems.size})",
                    onClick = { onMailboxChange(AnnouncementMailbox.INBOX) },
                    variant = if (state.mailbox == AnnouncementMailbox.INBOX) WatchioButtonVariant.Primary else WatchioButtonVariant.Secondary,
                    modifier = Modifier.width(155.dp).testTag("announcements-inbox-tab"),
                )
                WatchioButton(
                    text = "ARCHIVED (${state.snapshot.archivedItems.size})",
                    onClick = { onMailboxChange(AnnouncementMailbox.ARCHIVED) },
                    variant = if (state.mailbox == AnnouncementMailbox.ARCHIVED) WatchioButtonVariant.Primary else WatchioButtonVariant.Secondary,
                    modifier = Modifier.width(155.dp).testTag("announcements-archived-tab"),
                )
            }
        }
        val showMarkAll = state.mailbox == AnnouncementMailbox.INBOX && state.snapshot.unreadCount > 0
        if (maxWidth < 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs()
                if (showMarkAll) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        WatchioButton(
                            text = "MARK ALL READ",
                            onClick = onMarkAllRead,
                            modifier = Modifier.width(170.dp).testTag("announcements-mark-all-read"),
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                tabs()
                Spacer(Modifier.weight(1f))
                if (showMarkAll) {
                    WatchioButton(
                        text = "MARK ALL READ",
                        onClick = onMarkAllRead,
                        modifier = Modifier.width(170.dp).testTag("announcements-mark-all-read"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementList(
    items: List<AnnouncementItem>,
    mailbox: AnnouncementMailbox,
    focusId: String?,
    onOpen: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    val spacing = LocalWatchioSpacing.current
    val firstFocus = remember { FocusRequester() }
    val focusTargetId = focusId?.takeIf { id -> items.any { it.announcement.id == id } }
        ?: items.firstOrNull()?.announcement?.id
    LaunchedEffect(focusTargetId) {
        if (items.isNotEmpty()) firstFocus.requestFocus()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("announcements-list"),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(items, key = { it.announcement.id }) { item ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnnouncementCard(
                    item = item,
                    onClick = { onOpen(item.announcement.id) },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (item.announcement.id == focusTargetId) Modifier.focusRequester(firstFocus) else Modifier)
                        .testTag("announcement-${item.announcement.id}"),
                )
                WatchioButton(
                    text = if (mailbox == AnnouncementMailbox.INBOX) "ARCHIVE" else "RESTORE",
                    onClick = {
                        if (mailbox == AnnouncementMailbox.INBOX) onArchive(item.announcement.id)
                        else onRestore(item.announcement.id)
                    },
                    variant = WatchioButtonVariant.Secondary,
                    modifier = Modifier.width(120.dp).testTag("announcement-${if (mailbox == AnnouncementMailbox.INBOX) "archive" else "restore"}-${item.announcement.id}"),
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCard(item: AnnouncementItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val accent = announcementAccent(item.announcement.priority, item.announcement.type)
    WatchioCard(
        modifier = modifier,
        accent = accent,
        minWidth = 0.dp,
        minHeight = 104.dp,
        contentDescription = "${if (item.isRead) "Read" else "Unread"} announcement: ${item.announcement.title}",
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                AnnouncementGlyph(accent)
                if (!item.isRead) Box(Modifier.align(Alignment.TopEnd).size(9.dp).background(colors.moviesAccent, androidx.compose.foundation.shape.CircleShape).testTag("announcement-unread-${item.announcement.id}"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.announcement.title, color = colors.textPrimary, style = type.cardTitle, fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatAnnouncementDate(item.announcement.publishedAt), color = colors.textMuted, style = type.label)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(item.announcement.type.name.replace('_', ' '), color = accent, style = type.label, fontWeight = FontWeight.Bold)
                    if (item.isDismissed) Text("DISMISSED", color = colors.textMuted, style = type.label, fontWeight = FontWeight.Bold)
                }
                Text(item.announcement.body, color = colors.textSecondary, style = type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AnnouncementDetails(
    item: AnnouncementItem,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onAction: (AnnouncementAction) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val announcement = item.announcement
    val accent = announcementAccent(announcement.priority, announcement.type)
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(watchioScreenBackgroundColor())
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("announcement-details"),
    ) {
        WatchioPageHeader(
            title = if (item.isArchived) "ARCHIVED ANNOUNCEMENT" else "ANNOUNCEMENT",
            onBack = onBack,
            testTagPrefix = "announcement-detail",
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = if (maxWidth < 900.dp) maxWidth else 900.dp
            WatchioCard(
                modifier = Modifier.widthIn(max = contentWidth).fillMaxHeight().align(Alignment.TopCenter).testTag("announcement-detail-card"),
                accent = accent,
                minWidth = 0.dp,
                minHeight = 0.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(spacing.xl).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${announcement.type.name.replace('_', ' ')}  ${announcement.priority.name}",
                            color = accent,
                            style = type.label,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(formatAnnouncementDate(announcement.publishedAt), color = colors.textMuted, style = type.label)
                    }
                    if (item.isDismissed) Text("Startup popup dismissed", color = colors.textMuted, style = type.label)
                    Text(announcement.title, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold)
                    Text(announcement.body, color = colors.textSecondary, style = type.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        announcement.action?.let { action ->
                            WatchioButton(action.label, onClick = { onAction(action) }, modifier = Modifier.width(150.dp).testTag("announcement-action"))
                        }
                        WatchioButton(
                            text = if (item.isArchived) "RESTORE" else "ARCHIVE",
                            onClick = if (item.isArchived) onRestore else onArchive,
                            variant = WatchioButtonVariant.Secondary,
                            modifier = Modifier.width(140.dp).testTag(if (item.isArchived) "announcement-detail-restore" else "announcement-detail-archive"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementMessage(title: String, body: String, testTag: String, button: String? = null, onClick: () -> Unit = {}) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    Box(Modifier.fillMaxSize().testTag(testTag), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(title, color = colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold)
            Text(body, color = colors.textSecondary, style = type.body)
            button?.let { WatchioButton(it, onClick = onClick, modifier = Modifier.widthIn(min = 140.dp)) }
        }
    }
}

@Composable
private fun announcementAccent(priority: AnnouncementPriority, type: AnnouncementType): Color {
    val colors = LocalWatchioColors.current
    return when {
        priority == AnnouncementPriority.CRITICAL -> colors.moviesAccent
        priority == AnnouncementPriority.IMPORTANT || type == AnnouncementType.IMPORTANT -> colors.liveTvAccent
        type == AnnouncementType.UPDATE -> colors.seriesAccent
        type == AnnouncementType.FEATURE -> colors.moviesAccent
        else -> colors.focusGlow
    }
}

@Composable
private fun AnnouncementGlyph(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension / 2)
        drawCircle(color, radius = size.minDimension * 0.22f, center = center)
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.08f), Offset(size.width * 0.5f, size.height * 0.28f), strokeWidth = 3.dp.toPx())
    }
}

internal fun formatAnnouncementDate(raw: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm").format(Instant.parse(raw).atZone(zoneId))
    }.getOrDefault("Date unavailable")
