package com.watchioiptv.nativeapp.feature.announcements

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.watchioiptv.nativeapp.domain.model.Announcement
import com.watchioiptv.nativeapp.domain.model.AnnouncementAction
import com.watchioiptv.nativeapp.domain.model.AnnouncementItem
import com.watchioiptv.nativeapp.domain.model.AnnouncementPriority
import com.watchioiptv.nativeapp.domain.model.AnnouncementType
import com.watchioiptv.nativeapp.ui.components.WatchioButton
import com.watchioiptv.nativeapp.ui.components.WatchioButtonVariant
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioSpacing
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography
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
    onDismiss: (String) -> Unit,
    onToggleArchived: () -> Unit,
    onAction: (AnnouncementAction) -> Unit,
) {
    val selected = state.selected
    if (selected != null) {
        AnnouncementDetails(
            item = selected,
            onBack = onCloseDetails,
            onDismiss = { onDismiss(selected.announcement.id) },
            onAction = onAction,
        )
        return
    }

    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(colors.surfaceBase).padding(horizontal = 32.dp, vertical = 20.dp).testTag("announcements-screen"),
    ) {
        WatchioPageHeader(
            title = "ANNOUNCEMENTS",
            onBack = onBack,
            testTagPrefix = "announcements",
            actions = {
                WatchioButton(
                    text = if (state.showArchived) "INBOX" else "ARCHIVED",
                    onClick = onToggleArchived,
                    variant = WatchioButtonVariant.CompactAction,
                    modifier = Modifier.widthIn(min = 112.dp).testTag("announcements-archive-toggle"),
                )
            },
        )
        Spacer(Modifier.height(spacing.md))
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
                title = if (state.showArchived) "No archived announcements." else "No announcements right now.",
                body = if (state.showArchived) "Dismissed announcements will appear here." else "Check back later for Watchio news and alerts.",
                testTag = "announcements-empty",
            )
            else -> AnnouncementList(state.visibleItems, onOpen)
        }
    }
}

@Composable
private fun AnnouncementList(items: List<AnnouncementItem>, onOpen: (String) -> Unit) {
    val spacing = LocalWatchioSpacing.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items.firstOrNull()?.announcement?.id) {
        if (items.isNotEmpty()) firstFocus.requestFocus()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("announcements-list"),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(items, key = { it.announcement.id }) { item ->
            AnnouncementCard(
                item = item,
                onClick = { onOpen(item.announcement.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (item == items.first()) Modifier.focusRequester(firstFocus) else Modifier)
                    .testTag("announcement-${item.announcement.id}"),
            )
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
                if (!item.isRead && !item.isDismissed) Box(Modifier.align(Alignment.TopEnd).size(9.dp).background(colors.moviesAccent, androidx.compose.foundation.shape.CircleShape).testTag("announcement-unread-${item.announcement.id}"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.announcement.title, color = colors.textPrimary, style = type.cardTitle, fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatAnnouncementDate(item.announcement.publishedAt), color = colors.textMuted, style = type.label)
                }
                Text(item.announcement.type.name.replace('_', ' '), color = accent, style = type.label, fontWeight = FontWeight.Bold)
                Text(item.announcement.body, color = colors.textSecondary, style = type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AnnouncementDetails(
    item: AnnouncementItem,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (AnnouncementAction) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val announcement = item.announcement
    val accent = announcementAccent(announcement.priority, announcement.type)
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(colors.surfaceBase).padding(horizontal = 32.dp, vertical = 20.dp).testTag("announcement-details"),
    ) {
        WatchioPageHeader(title = "ANNOUNCEMENT", onBack = onBack, testTagPrefix = "announcement-detail")
        Spacer(Modifier.height(spacing.lg))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = if (maxWidth < 900.dp) maxWidth else 900.dp
            WatchioCard(
                modifier = Modifier.widthIn(max = contentWidth).align(Alignment.TopCenter).testTag("announcement-detail-card"),
                accent = accent,
                minWidth = 0.dp,
                minHeight = 0.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(announcement.type.name.replace('_', ' '), color = accent, style = type.label, fontWeight = FontWeight.Bold)
                        Text(formatAnnouncementDate(announcement.publishedAt), color = colors.textMuted, style = type.label)
                    }
                    Text(announcement.title, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold)
                    Text(announcement.body, color = colors.textSecondary, style = type.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        announcement.action?.let { action ->
                            WatchioButton(action.label, onClick = { onAction(action) }, modifier = Modifier.widthIn(min = 150.dp).testTag("announcement-action"))
                        }
                        if (announcement.dismissible) {
                            WatchioButton("ARCHIVE", onClick = onDismiss, variant = WatchioButtonVariant.Secondary, modifier = Modifier.widthIn(min = 140.dp).testTag("announcement-dismiss"))
                        }
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
