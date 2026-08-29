package com.watchioiptv.nativeapp.feature.live

import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import com.watchioiptv.nativeapp.data.live.LiveTvCategory
import com.watchioiptv.nativeapp.data.live.LiveTvChannel
import com.watchioiptv.nativeapp.data.live.LiveTvNowNext
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.ui.components.WatchioButton
import com.watchioiptv.nativeapp.ui.components.WatchioButtonVariant
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioFocusableCard
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LiveTvScreen(
    uiState: LiveTvUiState,
    playerState: WatchioPlayerState,
    playerManager: WatchioPlayerManager,
    onCategory: (LiveTvCategory) -> Unit,
    onCategorySearch: (String) -> Unit,
    onLiveSearch: (String) -> Unit,
    onChannel: (LiveTvChannel) -> Unit,
    onFavorite: (LiveTvChannel) -> Unit,
    onRetry: () -> Unit,
    onRefreshEpg: () -> Unit,
    onFullscreen: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val firstCategoryFocus = remember { FocusRequester() }
    var liveSearchVisible by remember { mutableStateOf(false) }
    var optionsChannel by remember { mutableStateOf<LiveTvChannel?>(null) }
    var isLongBackHandled by remember { mutableStateOf(false) }

    if (uiState.loading) {
        Box(Modifier.fillMaxSize().background(colors.surfaceBase), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.liveTvAccent)
        }
        return
    }
    LaunchedEffect(uiState.categories.firstOrNull()?.id) {
        if (uiState.categories.isNotEmpty()) firstCategoryFocus.requestFocus()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(colors.surfaceBase, Color(0xFF12071F), Color(0xFF041B22))))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("live-tv-screen")
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    val nativeEvent = event.nativeKeyEvent
                    if (event.type == KeyEventType.KeyDown) {
                        val hasActivePreview = uiState.selectedChannel != null &&
                            (playerState is WatchioPlayerState.Playing ||
                             playerState is WatchioPlayerState.Buffering ||
                             playerState is WatchioPlayerState.Connecting ||
                             playerState is WatchioPlayerState.Recovering)
                        if (hasActivePreview && (nativeEvent.isLongPress || nativeEvent.repeatCount >= 1)) {
                            if (!isLongBackHandled) {
                                isLongBackHandled = true
                                onFullscreen()
                            }
                            return@onPreviewKeyEvent true
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        if (isLongBackHandled) {
                            isLongBackHandled = false
                            return@onPreviewKeyEvent true
                        }
                        if (liveSearchVisible) {
                            liveSearchVisible = false
                            onLiveSearch("")
                        } else {
                            onBack()
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                false
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            LiveHeader(
                onSearchVisible = {
                    onLiveSearch("")
                    liveSearchVisible = true
                },
                onRefreshEpg = onRefreshEpg,
                onBack = onBack,
            )
            if (liveSearchVisible) {
                LiveChannelSearchOverlay(
                    query = uiState.liveSearchQuery,
                    results = if (uiState.liveSearchQuery.isBlank()) emptyList() else uiState.channels,
                    selectedChannel = uiState.selectedChannel,
                    onSearch = onLiveSearch,
                    onSelect = { channel ->
                        liveSearchVisible = false
                        onLiveSearch("")
                        if (channel.id != uiState.selectedChannel?.id) onChannel(channel)
                    },
                    onDismiss = {
                        liveSearchVisible = false
                        onLiveSearch("")
                    },
                )
            }
            optionsChannel?.let { channel ->
                ChannelOptionsDialog(
                    channel = channel,
                    nowNext = if (channel.id == uiState.selectedChannel?.id) uiState.nowNext else LiveTvNowNext(null, null, 0f),
                    showRetry = playerState is WatchioPlayerState.Failed && channel.id == uiState.selectedChannel?.id,
                    onFavorite = { onFavorite(channel) },
                    onRetry = onRetry,
                    onDismiss = { optionsChannel = null },
                )
            }
            uiState.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = colors.liveTvAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 980.dp
                val gap = if (compact) 10.dp else 14.dp
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    CategoryPanel(
                        state = uiState,
                        firstFocus = firstCategoryFocus,
                        onCategorySearch = onCategorySearch,
                        onCategory = onCategory,
                        modifier = Modifier.weight(if (compact) 0.22f else 0.22f).fillMaxHeight(),
                    )
                    ChannelListPanel(
                        channels = uiState.channels,
                        selectedChannel = uiState.selectedChannel,
                        initialScrollIndex = uiState.initialScrollIndex,
                        onChannel = { channel ->
                            if (channel.id == uiState.selectedChannel?.id) onFullscreen() else onChannel(channel)
                        },
                        onChannelOptions = { optionsChannel = it },
                        modifier = Modifier.weight(if (compact) 0.28f else 0.28f).fillMaxHeight(),
                    )
                    RightLivePanel(
                        uiState = uiState,
                        playerState = playerState,
                        playerManager = playerManager,
                        compact = compact,
                        onFullscreen = onFullscreen,
                        onRetry = onRetry,
                        onRefreshEpg = onRefreshEpg,
                        modifier = Modifier.weight(if (compact) 0.50f else 0.50f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveHeader(
    onSearchVisible: () -> Unit,
    onRefreshEpg: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    WatchioPageHeader(title = "LIVE TV", onBack = onBack, testTagPrefix = "live") {
        LiveSearchIconButton(
            accent = colors.seriesAccent,
            onClick = onSearchVisible,
            modifier = Modifier.testTag("live-search"),
        )
        LiveMoreButton(
            accent = colors.liveTvAccent,
            onClick = onRefreshEpg,
            modifier = Modifier.testTag("live-more"),
        )
    }
}

/**
 * Compact Search icon button for the Live TV header (Phase 14.2H.6).
 * Matches the 44x44dp square icon button from Movies and Series.
 */
@Composable
private fun LiveSearchIconButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier
            .size(44.dp)
            .semantics { onClick(label = "Search Live TV") { onClick(); true } },
        accent = accent,
        minWidth = 44.dp,
        minHeight = 44.dp,
        contentDescription = "Search Live TV",
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                val strokeWidth = 2.dp.toPx()
                val radius = size.width * 0.30f
                val centerOffset = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.42f)
                drawCircle(
                    color = colors.textPrimary,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidth),
                )
                val handleStart = androidx.compose.ui.geometry.Offset(
                    centerOffset.x + (radius * 0.7071f),
                    centerOffset.y + (radius * 0.7071f),
                )
                val handleEnd = androidx.compose.ui.geometry.Offset(
                    size.width * 0.88f,
                    size.height * 0.88f,
                )
                drawLine(
                    color = colors.textPrimary,
                    start = handleStart,
                    end = handleEnd,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Compact three-dot More button for the Live TV header (Phase 14.2H.6).
 * Renders a vertical ⋮ ellipsis (Unicode 22EE) — matches Movies and Series header.
 */
@Composable
private fun LiveMoreButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier
            .size(44.dp)
            .semantics { onClick(label = "More Live TV Options") { onClick(); true } },
        accent = accent,
        minWidth = 44.dp,
        minHeight = 44.dp,
        contentDescription = "More Live TV Options",
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                val dotRadius = size.width * 0.12f
                val cx = size.width / 2f
                val topY = size.height * 0.18f
                val midY = size.height * 0.50f
                val botY = size.height * 0.82f
                drawCircle(colors.textPrimary, dotRadius, androidx.compose.ui.geometry.Offset(cx, topY))
                drawCircle(colors.textPrimary, dotRadius, androidx.compose.ui.geometry.Offset(cx, midY))
                drawCircle(colors.textPrimary, dotRadius, androidx.compose.ui.geometry.Offset(cx, botY))
            }
        }
    }
}

@Composable
private fun LiveChannelSearchOverlay(
    query: String,
    results: List<LiveTvChannel>,
    selectedChannel: LiveTvChannel?,
    onSearch: (String) -> Unit,
    onSelect: (LiveTvChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val fieldFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .imePadding()
                .testTag("live-search-overlay"),
            contentAlignment = Alignment.Center,
        ) {
            WatchioCard(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.82f)
                    .testTag("live-search-panel"),
                accent = colors.liveTvAccent,
                minWidth = 0.dp,
                minHeight = 0.dp,
            ) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Search channels", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onDismiss, modifier = Modifier.testTag("live-search-close")) { Text("Close") }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onSearch,
                        singleLine = true,
                        label = { Text("Search channels") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus)
                            .testTag("live-search-field"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSearch("") }, modifier = Modifier.testTag("live-search-clear")) { Text("Clear") }
                        Text(
                            text = if (query.isBlank()) "Type to search channels" else "${results.size} results",
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LazyColumn(Modifier.fillMaxSize().testTag("live-search-results")) {
                        items(results.take(100), key = { it.id }) { channel ->
                            SearchResultRow(
                                channel = channel,
                                selected = channel.id == selectedChannel?.id,
                                onClick = { onSelect(channel) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(channel: LiveTvChannel, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("live-search-result"),
        accent = if (selected) colors.liveTvAccent else colors.focusGlow,
        selected = selected,
        minWidth = 0.dp,
        minHeight = 58.dp,
        contentDescription = channel.name,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel.logoUrl)
            Text(
                channel.name,
                color = colors.textPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryPanel(
    state: LiveTvUiState,
    firstFocus: FocusRequester,
    onCategorySearch: (String) -> Unit,
    onCategory: (LiveTvCategory) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalWatchioColors.current
    val visible = remember(state.categories, state.categorySearchQuery) {
        state.categories.filter { it.name.contains(state.categorySearchQuery, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedCategory?.id) {
        val index = visible.indexOfFirst { it.id == state.selectedCategory?.id }
        if (index > 0) {
            listState.scrollToItem((index - 1).coerceAtLeast(0))
        }
    }
    WatchioCard(modifier = modifier.testTag("live-category-panel"), accent = colors.liveTvAccent, minWidth = 0.dp, minHeight = 0.dp) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            OutlinedTextField(
                value = state.categorySearchQuery,
                onValueChange = onCategorySearch,
                singleLine = true,
                label = { Text("Search categories") },
                modifier = Modifier.fillMaxWidth().testTag("live-category-search"),
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("live-categories")) {
                itemsIndexed(visible, key = { _, item -> item.id }) { index, category ->
                    val isSelected = category.id == state.selectedCategory?.id
                    val isDefaultFocus = if (state.selectedCategory != null) isSelected else index == 0
                    CategoryRow(
                        category = category,
                        selected = isSelected,
                        onClick = { onCategory(category) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .then(if (isDefaultFocus) Modifier.focusRequester(firstFocus) else Modifier)
                            .testTag(categoryTag(category)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: LiveTvCategory, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier,
        accent = if (selected) colors.liveTvAccent else colors.focusGlow,
        selected = selected,
        minWidth = 0.dp,
        minHeight = 54.dp,
        contentDescription = category.name,
        onClick = onClick,
    ) {
        Text(
            category.name,
            color = colors.textPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ChannelListPanel(
    channels: List<LiveTvChannel>,
    selectedChannel: LiveTvChannel?,
    initialScrollIndex: Int,
    onChannel: (LiveTvChannel) -> Unit,
    onChannelOptions: (LiveTvChannel) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalWatchioColors.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialScrollIndex - 1).coerceAtLeast(0),
    )

    LaunchedEffect(selectedChannel?.id) {
        val targetIndex = channels.indexOfFirst { it.id == selectedChannel?.id }
        if (targetIndex >= 0) {
            val visibleStart = listState.firstVisibleItemIndex
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            if (targetIndex < visibleStart || targetIndex >= visibleStart + visibleCount) {
                listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
            }
        }
    }

    WatchioCard(modifier = modifier.testTag("live-channel-list"), accent = colors.seriesAccent, minWidth = 0.dp, minHeight = 0.dp) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(10.dp).testTag("live-channels"),
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelRow(channel, channel.id == selectedChannel?.id, onChannel, onChannelOptions)
            }
        }
    }
}

@Composable
private fun RightLivePanel(
    uiState: LiveTvUiState,
    playerState: WatchioPlayerState,
    playerManager: WatchioPlayerManager,
    compact: Boolean,
    onFullscreen: () -> Unit,
    onRetry: () -> Unit,
    onRefreshEpg: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val compactTopRowHeight = (maxWidth * 0.68f * 9f / 16f)
            .coerceAtLeast(112.dp)
            .coerceAtMost(maxHeight * 0.56f)
        val panelGap = if (compact) 8.dp else 12.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(panelGap)) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth().height(compactTopRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayerSurface(
                        playerManager = playerManager,
                        playerState = playerState,
                        modifier = Modifier.weight(0.68f).fillMaxHeight().testTag("live-preview"),
                        onClick = if (uiState.selectedChannel == null) ({}) else onFullscreen,
                    )
                    ChannelInfoCard(
                        uiState = uiState,
                        playerState = playerState,
                        onFullscreen = onFullscreen,
                        onRetry = onRetry,
                        showActions = false,
                        compact = true,
                        modifier = Modifier.weight(0.32f).fillMaxHeight().testTag("live-channel-info"),
                    )
                }
                EpgPanel(
                    uiState = uiState,
                    compact = true,
                    onRefreshEpg = onRefreshEpg,
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("live-epg-panel"),
                )
            } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerSurface(
                    playerManager = playerManager,
                    playerState = playerState,
                    modifier = Modifier.weight(1.35f).aspectRatio(16f / 9f).testTag("live-preview"),
                    onClick = onFullscreen,
                )
                ChannelInfoCard(
                    uiState = uiState,
                    playerState = playerState,
                    onFullscreen = onFullscreen,
                    onRetry = onRetry,
                    showActions = true,
                    compact = false,
                    modifier = Modifier.weight(0.95f).testTag("live-channel-info"),
                )
            }
            EpgPanel(
                uiState = uiState,
                compact = false,
                onRefreshEpg = onRefreshEpg,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("live-epg-panel"),
            )
            }
        }
    }
}

@Composable
private fun ChannelInfoCard(
    uiState: LiveTvUiState,
    playerState: WatchioPlayerState,
    onFullscreen: () -> Unit,
    onRetry: () -> Unit,
    showActions: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val colors = LocalWatchioColors.current
    val channel = uiState.selectedChannel
    val range = programmeTimeRange(uiState.nowNext.currentStartEpochMs, uiState.nowNext.currentEndEpochMs)
    WatchioCard(modifier = modifier, accent = colors.liveTvAccent, minWidth = 0.dp, minHeight = 0.dp) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 8.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 7.dp),
        ) {
            Text("LIVE TV", color = colors.liveTvAccent, fontWeight = FontWeight.Bold, maxLines = 1)
            if (channel == null) {
                Text("No channel selected", color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(channel.name, color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = if (compact) 3 else 2, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        Text(uiState.nowNext.currentTitle ?: "No programme information", color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (range.isNotBlank()) Text(range, color = colors.textSecondary, maxLines = 1)
                        if (range.isNotBlank()) LinearProgressIndicator(
                            progress = { uiState.nowNext.progress },
                            color = colors.liveTvAccent,
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                        uiState.nowNext.nextTitle?.let {
                            Text("Next: $it", color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgPanel(uiState: LiveTvUiState, compact: Boolean, onRefreshEpg: () -> Unit, modifier: Modifier) {
    val colors = LocalWatchioColors.current
    WatchioCard(modifier = modifier, accent = colors.seriesAccent, minWidth = 0.dp, minHeight = 0.dp) {
        Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            val channel = uiState.selectedChannel
            Text("CURRENT PROGRAMME", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            if (channel == null) {
                Text("No programme selected", color = colors.textSecondary)
            } else if (uiState.nowNext.currentTitle == null) {
                Text("No EPG Information Available", color = colors.textSecondary)
                uiState.epgRefreshMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (compact) {
                    TextButton(onClick = onRefreshEpg, modifier = Modifier.testTag("live-refresh-epg")) {
                        Text(if (uiState.epgRefreshing) "Refreshing..." else "Refresh EPG")
                    }
                } else {
                    WatchioButton(
                        text = if (uiState.epgRefreshing) "Refreshing..." else "Refresh EPG",
                        onClick = onRefreshEpg,
                        loading = uiState.epgRefreshing,
                        variant = WatchioButtonVariant.Secondary,
                        modifier = Modifier.testTag("live-refresh-epg"),
                    )
                }
            } else {
                Text(uiState.nowNext.currentTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val range = programmeTimeRange(uiState.nowNext.currentStartEpochMs, uiState.nowNext.currentEndEpochMs)
                if (range.isNotBlank()) Text(range, color = colors.textSecondary)
                if (range.isNotBlank()) LinearProgressIndicator(progress = { uiState.nowNext.progress }, color = colors.liveTvAccent, modifier = Modifier.fillMaxWidth().height(5.dp))
                uiState.nowNext.nextTitle?.let {
                    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                    Text("NEXT", color = colors.textMuted, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(it, color = colors.textSecondary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                uiState.nowNext.currentDescription?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.textSecondary, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ChannelOptionsDialog(
    channel: LiveTvChannel,
    nowNext: LiveTvNowNext,
    showRetry: Boolean,
    onFavorite: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Channel Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(channel.name, fontWeight = FontWeight.Bold)
                Text(nowNext.currentTitle ?: "No programme information available.")
                val range = programmeTimeRange(nowNext.currentStartEpochMs, nowNext.currentEndEpochMs)
                if (range.isNotBlank()) Text(range)
                nowNext.currentDescription?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                nowNext.nextTitle?.let { Text("Next: $it") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onFavorite()
                onDismiss()
            }) {
                Text(if (channel.isFavorite) "Remove from Favourites" else "Add to Favourites")
            }
        },
        dismissButton = {
            Row {
                if (showRetry) {
                    TextButton(onClick = {
                        onRetry()
                        onDismiss()
                    }) { Text("Retry Stream") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
fun FullscreenPlayerScreen(
    playerState: WatchioPlayerState,
    playerSettings: PlayerSettings,
    playerManager: WatchioPlayerManager,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    val firstFocus = remember { FocusRequester() }
    val surfaceFocus = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            firstFocus.requestFocus()
            if (playerSettings.controlAutoHideDelay != ControlAutoHideDelay.Never) {
                delay(playerSettings.controlAutoHideDelay.seconds * 1_000L)
                controlsVisible = false
                surfaceFocus.requestFocus()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("live-fullscreen")
            .focusRequester(surfaceFocus)
            .focusable()
            .clickable { controlsVisible = !controlsVisible }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        controlsVisible = true
                        true
                    }
                    Key.Escape, Key.Back -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(
            playerManager = playerManager,
            playerState = playerState,
            modifier = Modifier.fillMaxSize(),
            onClick = { controlsVisible = !controlsVisible },
        )
        if (controlsVisible && playerSettings.showPlayerControls) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WatchioFocusableCard(
                    title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                    accent = LocalWatchioColors.current.liveTvAccent,
                    onClick = onPlayPause,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                WatchioFocusableCard("Retry", accent = LocalWatchioColors.current.seriesAccent, onClick = onRetry)
                WatchioFocusableCard("Back", accent = LocalWatchioColors.current.focusGlow, onClick = onClose)
            }
        } else if (controlsVisible) {
            WatchioFocusableCard(
                title = if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                accent = LocalWatchioColors.current.liveTvAccent,
                onClick = onPlayPause,
                modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp).focusRequester(firstFocus),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(channel: LiveTvChannel, selected: Boolean, onChannel: (LiveTvChannel) -> Unit, onChannelOptions: (LiveTvChannel) -> Unit) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        accent = if (selected) colors.liveTvAccent else colors.focusGlow,
        selected = selected,
        minWidth = 0.dp,
        minHeight = 64.dp,
        contentDescription = channel.name,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .combinedClickable(
                onClick = { onChannel(channel) },
                onLongClick = { onChannelOptions(channel) },
                onLongClickLabel = "Channel Options",
            )
            .testTag("live-channel-card"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel.logoUrl)
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = colors.textPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.isFavorite) Text("Favourite", color = colors.liveTvAccent, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChannelLogo(url: String?) {
    val colors = LocalWatchioColors.current
    Box(
        Modifier
            .size(42.dp)
            .background(colors.surfaceElevated, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text("TV", color = colors.textSecondary, fontWeight = FontWeight.Bold)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

private fun categoryTag(category: LiveTvCategory): String = when (category.id) {
    "all" -> "live-category-all"
    "favorites" -> "live-category-favorites"
    "history" -> "live-category-history"
    else -> "live-category-${category.id}"
}

private fun programmeTimeRange(startEpochMs: Long?, endEpochMs: Long?): String {
    if (startEpochMs == null || endEpochMs == null) return ""
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    return "${formatter.format(Instant.ofEpochMilli(startEpochMs))} - ${formatter.format(Instant.ofEpochMilli(endEpochMs))}"
}

@Composable
private fun PlayerSurface(
    playerManager: WatchioPlayerManager,
    playerState: WatchioPlayerState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = LocalWatchioColors.current
    Box(
        modifier
            .background(Color.Black)
            .border(if (focused) 2.dp else 0.dp, colors.focusGlow, RoundedCornerShape(6.dp))
            .semantics { contentDescription = "Open fullscreen player" }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FrameLayout(context).also { playerManager.attachSurface(it) }
            },
            update = { playerManager.attachSurface(it) },
            onRelease = { playerManager.detachSurface(it) },
        )
        when (playerState) {
            is WatchioPlayerState.Connecting -> Text("Connecting...", color = Color.White)
            is WatchioPlayerState.Buffering -> Text("Buffering...", color = Color.White)
            is WatchioPlayerState.Failed -> Text(playerState.message, color = Color.White)
            is WatchioPlayerState.Idle -> Text("Select a channel", color = Color.White)
            else -> Unit
        }
    }
}
