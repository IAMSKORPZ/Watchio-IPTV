package com.watchioiptv.nativeapp.feature.tvguide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watchioiptv.nativeapp.data.live.LiveTvCategory
import com.watchioiptv.nativeapp.ui.components.WatchioFocusableCard
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
fun TvGuideScreen(
    state: TvGuideUiState,
    onJumpToNow: () -> Unit,
    onDay: (java.time.LocalDate) -> Unit,
    onCategory: (LiveTvCategory) -> Unit,
    onRefresh: () -> Unit,
    onChannel: (WatchioGuideChannel) -> Unit,
    onProgramme: (WatchioGuideChannel, WatchioGuideProgramme) -> Unit,
    onPlayLive: () -> Unit,
    onCloseDetails: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val horizontal = rememberScrollState()
    val zone = ZoneId.systemDefault()
    val channelWidth = 180.dp
    val rowHeight = 78.dp
    val timelineWidth = TvGuideTimeline.widthDp(state.window.startUtcMs, state.window.endUtcMs, state.window.startUtcMs, state.window.endUtcMs).dp
    val firstFocus = remember { FocusRequester() }
    var categoryPickerOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(24.dp)
            .testTag("tv-guide-screen"),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val controls: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    WatchioFocusableCard("NOW", accent = colors.focusGlow, onClick = onJumpToNow, modifier = Modifier.focusRequester(firstFocus))
                    WatchioFocusableCard(
                        title = "Category: ${state.selectedCategory?.name ?: "All Channels"}",
                        accent = colors.liveTvAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        onClick = { categoryPickerOpen = true },
                        modifier = Modifier.testTag("tv-guide-category-selector"),
                    )
                    state.window.days.forEach { day ->
                        WatchioFocusableCard(day.label, accent = if (day.date == state.window.day) colors.focusGlow else colors.seriesAccent, onClick = { onDay(day.date) })
                    }
                    WatchioFocusableCard(if (state.refreshing) "Refreshing..." else "Refresh EPG", accent = colors.liveTvAccent, onClick = onRefresh)
                    WatchioFocusableCard("Back", accent = colors.moviesAccent, onClick = onBack)
                }
            }
            if (maxWidth < 900.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideTitle(state)
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { controls() }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GuideTitle(state, Modifier.weight(1f))
                    controls()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        state.errorMessage?.let { Text(it, color = colors.liveTvAccent) }
        state.message?.let { Text(it, color = colors.textSecondary) }
        when {
            state.loading -> LoadingGuide()
            !state.hasProvider -> EmptyGuide("Add a provider first.")
            state.channels.isEmpty() -> EmptyGuide(if (state.selectedCategory == null || state.selectedCategory.id == "all") "No Live TV channels." else "No channels in this category.")
            else -> {
                Row(Modifier.fillMaxWidth().height(38.dp)) {
                    Box(Modifier.width(channelWidth).fillMaxHeight().background(colors.surfaceElevated).padding(10.dp)) {
                        Text("Channels", color = colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.weight(1f).horizontalScroll(horizontal)) {
                        TimeHeader(state.window.startUtcMs, state.window.endUtcMs, zone, timelineWidth)
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag("tv-guide-grid")) {
                        items(state.channels, key = { it.channelId }) { channel ->
                            Row(Modifier.fillMaxWidth().height(rowHeight)) {
                                WatchioFocusableCard(
                                    title = buildString {
                                        channel.channelNumber?.let { append(it).append("  ") }
                                        append(channel.displayName)
                                        if (channel.isFavourite) append("\nFavourite")
                                    },
                                    accent = if (channel.channelId == state.selectedChannelId) colors.focusGlow else colors.liveTvAccent,
                                    modifier = Modifier.width(channelWidth).height(rowHeight).semantics {
                                        contentDescription = "Channel ${channel.displayName}"
                                    },
                                    onClick = { onChannel(channel) },
                                )
                                Box(Modifier.weight(1f).horizontalScroll(horizontal)) {
                                    ProgrammeRow(
                                        channel = channel,
                                        programmes = state.programmes[channel.channelId].orEmpty(),
                                        window = state.window,
                                        nowEpochMs = state.nowEpochMs,
                                        timelineWidth = timelineWidth,
                                        selectedProgrammeId = state.selectedProgrammeId,
                                        onProgramme = { onProgramme(channel, it) },
                                    )
                                }
                            }
                        }
                    }
                    NowLine(
                        nowEpochMs = state.nowEpochMs,
                        window = state.window,
                        channelWidthDp = channelWidth.value,
                        scrollPx = horizontal.value,
                    )
                }
            }
        }
    }
    state.details?.let {
        ProgrammeDetailsDialog(it, onPlayLive, onCloseDetails)
    }
    if (categoryPickerOpen) {
        CategoryPickerDialog(
            categories = state.categories,
            selectedCategoryId = state.selectedCategory?.id,
            onCategory = {
                categoryPickerOpen = false
                onCategory(it)
            },
            onClose = { categoryPickerOpen = false },
        )
    }
}

@Composable
private fun CategoryPickerDialog(
    categories: List<LiveTvCategory>,
    selectedCategoryId: String?,
    onCategory: (LiveTvCategory) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Select TV Guide Category") },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp).testTag("tv-guide-category-list")) {
                items(categories, key = { it.id }) { category ->
                    WatchioFocusableCard(
                        title = category.name,
                        accent = if (category.id == selectedCategoryId) colors.focusGlow else colors.liveTvAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        onClick = { onCategory(category) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}

@Composable
private fun GuideTitle(state: TvGuideUiState, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    Column(modifier) {
        Text("TV Guide", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(statusText(state), color = colors.textMuted)
    }
}

@Composable
private fun TimeHeader(start: Long, end: Long, zone: ZoneId, width: androidx.compose.ui.unit.Dp) {
    val colors = LocalWatchioColors.current
    Row(Modifier.width(width).fillMaxHeight().background(colors.surfaceStatus)) {
        val slots = ceil((end - start) / (TvGuideTimeline.SlotMinutes * 60_000f)).toInt()
        repeat(slots) { index ->
            val time = start + index * TvGuideTimeline.SlotMinutes * 60_000L
            Box(Modifier.width((TvGuideTimeline.SlotMinutes * TvGuideTimeline.MinuteWidthDp).dp).padding(8.dp)) {
                Text(formatTime(time, zone), color = colors.textSecondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProgrammeRow(
    channel: WatchioGuideChannel,
    programmes: List<WatchioGuideProgramme>,
    window: WatchioGuideWindow,
    nowEpochMs: Long,
    timelineWidth: androidx.compose.ui.unit.Dp,
    selectedProgrammeId: String?,
    onProgramme: (WatchioGuideProgramme) -> Unit,
) {
    val colors = LocalWatchioColors.current
    Row(Modifier.width(timelineWidth).height(78.dp)) {
        var cursor = window.startUtcMs
        val visible = programmes
            .filter { it.endUtcMs > window.startUtcMs && it.startUtcMs < window.endUtcMs }
            .sortedBy { it.startUtcMs }
        if (visible.isEmpty()) {
            NoInfoCell(timelineWidth)
            return@Row
        }
        visible.forEach { programme ->
            val gap = (programme.startUtcMs.coerceAtLeast(window.startUtcMs) - cursor).coerceAtLeast(0L)
            if (gap > 0) Spacer(Modifier.width(TvGuideTimeline.widthForGapDp(gap).dp))
            val width = TvGuideTimeline.widthDp(programme.startUtcMs, programme.endUtcMs, window.startUtcMs, window.endUtcMs).dp
            val accent = when {
                programme.programmeId == selectedProgrammeId -> colors.focusGlow
                programme.isLiveNow -> colors.seriesAccent
                else -> colors.surfaceElevated
            }
            WatchioFocusableCard(
                title = buildString {
                    append(programme.title)
                    if (width.value > 220f) append("\n").append(formatTime(programme.startUtcMs, ZoneId.systemDefault())).append(" - ").append(formatTime(programme.endUtcMs, ZoneId.systemDefault()))
                },
                accent = accent,
                modifier = Modifier
                    .width(width)
                    .height(78.dp)
                    .semantics {
                        contentDescription = "${programme.title}, ${channel.displayName}, ${formatTime(programme.startUtcMs, ZoneId.systemDefault())} to ${formatTime(programme.endUtcMs, ZoneId.systemDefault())}"
                    },
                onClick = { onProgramme(programme) },
            )
            if (programme.isLiveNow) {
                LinearProgressIndicator(
                    progress = { TvGuideTimeline.progress(nowEpochMs, programme.startUtcMs, programme.endUtcMs) },
                    modifier = Modifier.width(0.dp),
                    color = colors.focusGlow,
                    trackColor = Color.Transparent,
                )
            }
            cursor = cursor.coerceAtLeast(programme.endUtcMs.coerceAtMost(window.endUtcMs))
        }
        val tail = (window.endUtcMs - cursor).coerceAtLeast(0L)
        if (tail > 0) Spacer(Modifier.width(TvGuideTimeline.widthForGapDp(tail).dp))
    }
}

@Composable
private fun NoInfoCell(width: androidx.compose.ui.unit.Dp) {
    val colors = LocalWatchioColors.current
    Box(Modifier.width(width).height(78.dp).background(colors.surfaceCard).padding(12.dp), contentAlignment = Alignment.CenterStart) {
        Text("No programme information", color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NowLine(nowEpochMs: Long, window: WatchioGuideWindow, channelWidthDp: Float, scrollPx: Int) {
    if (nowEpochMs !in window.startUtcMs..window.endUtcMs) return
    val colors = LocalWatchioColors.current
    val offset = TvGuideTimeline.nowLineOffsetDp(nowEpochMs, window.startUtcMs, channelWidthDp, scrollPx) ?: return
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = offset.dp).width(2.dp).fillMaxHeight().background(colors.focusGlow))
        Text("NOW", color = colors.focusGlow, modifier = Modifier.padding(start = (offset + 6f).dp, top = 4.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgrammeDetailsDialog(details: ProgrammeDetails, onPlayLive: () -> Unit, onClose: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val programme = details.programme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(programme.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(details.channel.displayName)
                Text("${formatDate(programme.startUtcMs, zone)}  ${formatTime(programme.startUtcMs, zone)} - ${formatTime(programme.endUtcMs, zone)}")
                val duration = ((programme.endUtcMs - programme.startUtcMs) / 60_000L).coerceAtLeast(0L)
                Text("$duration min")
                programme.description?.let { Text(it) }
                programme.category?.let { Text(it) }
                programme.rating?.let { Text(it) }
                programme.episodeInfo?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onPlayLive) { Text("Play Live") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}

@Composable
private fun LoadingGuide() {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Text("Loading TV Guide")
    }
}

@Composable
private fun EmptyGuide(message: String) {
    val colors = LocalWatchioColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = colors.textSecondary)
    }
}

private fun statusText(state: TvGuideUiState): String = when {
    state.loading -> "Loading TV Guide..."
    state.refreshing -> "Loading TV Guide..."
    !state.hasEpgSource -> "No EPG source available."
    state.epgProgrammeCount == 0 -> "Guide not downloaded yet."
    else -> "${state.epgChannelCount} EPG channels  ${state.epgProgrammeCount} programmes"
}

private fun formatTime(epochMs: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMs).atZone(zone))

private fun formatDate(epochMs: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(epochMs).atZone(zone))
