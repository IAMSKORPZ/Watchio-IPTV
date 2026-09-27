package com.iamskorpz.watchioiptv.feature.tvguide

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.data.live.LiveTvCategory
import com.iamskorpz.watchioiptv.ui.components.WatchioCard
import com.iamskorpz.watchioiptv.ui.components.WatchioFocusableCard
import com.iamskorpz.watchioiptv.ui.components.WatchioPageHeader
import com.iamskorpz.watchioiptv.ui.components.WatchioPlayerSurface
import com.iamskorpz.watchioiptv.ui.components.WatchioSurfaceRole
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioAppearance
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioSpacing
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioTypography
import com.iamskorpz.watchioiptv.ui.theme.toComposeColor
import com.iamskorpz.watchioiptv.ui.theme.watchioScreenBackgroundColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class TimelineScrollRequest { Start, Now }

@Composable
fun TvGuideScreen(
    state: TvGuideUiState,
    playerManager: WatchioPlayerManager? = null,
    playerState: WatchioPlayerState? = null,
    onJumpToNow: () -> Unit,
    onDay: (java.time.LocalDate) -> Unit,
    onCategory: (LiveTvCategory) -> Unit,
    onRefresh: () -> Unit,
    onPreviewChannel: (WatchioGuideChannel?) -> Unit = {},
    onChannel: (WatchioGuideChannel) -> Unit,
    onProgrammeFocused: (WatchioGuideChannel, WatchioGuideProgramme) -> Unit = { _, _ -> },
    onProgramme: (WatchioGuideChannel, WatchioGuideProgramme) -> Unit,
    onWatchLive: (WatchioGuideChannel, WatchioGuideProgramme) -> Unit = { _, _ -> },
    onPlayLive: () -> Unit,
    onCloseDetails: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val horizontal = rememberScrollState()
    val zone = ZoneId.systemDefault()
    val density = LocalDensity.current
    val timelineWidth = TvGuideTimeline.widthDp(state.window.startUtcMs, state.window.endUtcMs, state.window.startUtcMs, state.window.endUtcMs).dp
    val categoryFocus = remember { FocusRequester() }
    val detailsReturnFocus = remember { FocusRequester() }
    var categoryPickerOpen by remember { mutableStateOf(false) }
    var restoreCategoryFocus by remember { mutableStateOf(false) }
    var restoreDetailsFocus by remember { mutableStateOf(false) }
    var timelineScrollRequest by remember { mutableStateOf<TimelineScrollRequest?>(null) }
    var initialNowPositionPending by remember { mutableStateOf(true) }
    val selectedChannel = state.channels.firstOrNull { it.channelId == state.selectedChannelId }
        ?: state.channels.firstOrNull()
    val selectedProgramme = selectedChannel?.let { channel ->
        state.programmes[channel.channelId].orEmpty().firstOrNull { it.programmeId == state.selectedProgrammeId }
            ?: state.programmes[channel.channelId].orEmpty().firstOrNull { it.isLiveNow }
            ?: state.programmes[channel.channelId].orEmpty().firstOrNull()
    }
    val previewChannel = state.channels.firstOrNull { it.channelId == state.previewChannelId } ?: selectedChannel

    fun handleActivation(channel: WatchioGuideChannel, programme: WatchioGuideProgramme?) {
        val isCurrentlyPreviewing = state.previewChannelId != null &&
            state.previewChannelId == channel.channelId &&
            playerState != null &&
            playerState !is WatchioPlayerState.Failed &&
            playerState !is WatchioPlayerState.Idle

        if (isCurrentlyPreviewing) {
            val targetProgramme = programme
                ?: state.programmes[channel.channelId]?.firstOrNull { it.isLiveNow }
                ?: state.programmes[channel.channelId]?.firstOrNull()
                ?: WatchioGuideProgramme(
                    programmeId = "no-info-${channel.channelId}",
                    channelId = channel.channelId,
                    epgChannelId = channel.epgChannelId.orEmpty(),
                    title = channel.displayName,
                    startUtcMs = state.window.startUtcMs,
                    endUtcMs = state.window.endUtcMs,
                    progress = 0f,
                    isLiveNow = true,
                )
            onWatchLive(channel, targetProgramme)
        } else {
            onPreviewChannel(channel)
            val targetProgramme = programme
                ?: state.programmes[channel.channelId]?.firstOrNull { it.isLiveNow }
                ?: state.programmes[channel.channelId]?.firstOrNull()
            if (targetProgramme != null) {
                onProgrammeFocused(channel, targetProgramme)
            } else {
                onChannel(channel)
            }
        }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { categoryFocus.requestFocus() }
    LaunchedEffect(categoryPickerOpen, restoreCategoryFocus) {
        if (!categoryPickerOpen && restoreCategoryFocus) {
            categoryFocus.requestFocus()
            restoreCategoryFocus = false
        }
    }
    LaunchedEffect(state.details, restoreDetailsFocus) {
        if (state.details == null && restoreDetailsFocus) {
            detailsReturnFocus.requestFocus()
            restoreDetailsFocus = false
        }
    }
    LaunchedEffect(state.loading, state.hasProvider, state.window.startUtcMs, state.window.endUtcMs, timelineScrollRequest) {
        val request = timelineScrollRequest
            ?: TimelineScrollRequest.Now.takeIf { initialNowPositionPending && !state.loading && state.hasProvider }
        when (request) {
            TimelineScrollRequest.Start -> horizontal.scrollTo(0)
            TimelineScrollRequest.Now -> {
                val targetDp = (TvGuideTimeline.offsetDp(state.nowEpochMs, state.window.startUtcMs) - 120f).coerceAtLeast(0f)
                horizontal.scrollTo(with(density) { targetDp.dp.toPx() }.roundToInt())
            }
            null -> Unit
        }
        if (request != null) initialNowPositionPending = false
        timelineScrollRequest = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(watchioScreenBackgroundColor())
            .testTag("tv-guide-screen"),
    ) {
        val layoutDim = TvGuideLayoutDimensions.calculate(maxWidth.value)
        val channelWidth = layoutDim.channelWidthDp.dp
        val rowHeight = layoutDim.rowHeightDp.dp
        val heroHeight = layoutDim.heroHeightDp.dp
        val isCompact = layoutDim.isCompact
        val scrollDp = with(density) { horizontal.value.toDp().value }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp,
                    vertical = if (layoutDim.isMedium) 6.dp else if (layoutDim.isCompact) 10.dp else 12.dp,
                ),
        ) {
            WatchioPageHeader(
                title = "TV GUIDE",
                onBack = onBack,
                testTagPrefix = "tv-guide",
            )
            Spacer(Modifier.height(if (layoutDim.isMedium) 4.dp else 6.dp))
            ProgrammeHero(
                channel = selectedChannel,
                previewChannel = previewChannel,
                programme = selectedProgramme,
                playerManager = playerManager,
                playerState = playerState,
                nowEpochMs = state.nowEpochMs,
                isCompact = isCompact,
                heroHeight = heroHeight,
                onDetails = null,
                onWatchLive = previewChannel?.let { channel ->
                    {
                        val targetProgramme = selectedProgramme
                            ?: state.programmes[channel.channelId]?.firstOrNull { it.isLiveNow }
                            ?: state.programmes[channel.channelId]?.firstOrNull()
                            ?: WatchioGuideProgramme(
                                programmeId = "no-info-${channel.channelId}",
                                channelId = channel.channelId,
                                epgChannelId = channel.epgChannelId.orEmpty(),
                                title = channel.displayName,
                                startUtcMs = state.window.startUtcMs,
                                endUtcMs = state.window.endUtcMs,
                                progress = 0f,
                                isLiveNow = true,
                            )
                        onWatchLive(channel, targetProgramme)
                    }
                },
            )

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = colors.liveTvAccent,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                )
            }
            state.message?.let {
                Text(
                    text = it,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                )
            }

            when {
                state.loading -> LoadingGuide()
                !state.hasProvider -> EmptyGuide("Add a provider first.")
                else -> {
                    // Category navigation remains available even when its channel filter is empty.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(channelWidth)
                                .fillMaxHeight()
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            TvGuideCategorySelector(
                                categories = state.categories,
                                selectedCategory = state.selectedCategory,
                                categoryFocus = categoryFocus,
                                onCategory = onCategory,
                                onOpenPicker = { categoryPickerOpen = true },
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontal),
                        ) {
                            TimeHeader(
                                start = state.window.startUtcMs,
                                end = state.window.endUtcMs,
                                nowEpochMs = state.nowEpochMs,
                                zone = zone,
                                width = timelineWidth,
                            )
                        }
                    }

                    if (state.channels.isEmpty()) {
                        EmptyGuide(
                            if (state.selectedCategory == null || state.selectedCategory.id == "all") "No Live TV channels."
                            else "No channels in this category.",
                        )
                    } else Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tv-guide-grid"),
                        ) {
                            items(state.channels, key = { it.channelId }) { channel ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight),
                                ) {
                                    WatchioGuideChannelCard(
                                        channel = channel,
                                        channelWidth = channelWidth,
                                        rowHeight = rowHeight,
                                        isCompact = isCompact,
                                        isSelected = channel.channelId == state.selectedChannelId,
                                        onClick = { handleActivation(channel, null) },
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(horizontal),
                                    ) {
                                        ProgrammeRow(
                                            channel = channel,
                                            programmes = state.programmes[channel.channelId].orEmpty(),
                                            window = state.window,
                                            nowEpochMs = state.nowEpochMs,
                                            rowHeight = rowHeight,
                                            timelineWidth = timelineWidth,
                                            selectedProgrammeId = state.selectedProgrammeId,
                                            detailsReturnFocus = detailsReturnFocus,
                                            onProgrammeFocused = { onProgrammeFocused(channel, it) },
                                            onProgramme = { handleActivation(channel, it) },
                                        )
                                    }
                                }
                            }
                        }

                        NowLine(
                            nowEpochMs = state.nowEpochMs,
                            window = state.window,
                            channelWidthDp = layoutDim.channelWidthDp,
                            scrollDp = scrollDp,
                        )
                    }
                }
            }
        }
    }

    state.details?.let {
        ProgrammeDetailsDialog(
            details = it,
            onPlayLive = onPlayLive,
            onClose = {
                restoreDetailsFocus = true
                onCloseDetails()
            },
        )
    }

    if (categoryPickerOpen) {
        CategoryPickerDialog(
            categories = state.categories,
            selectedCategoryId = state.selectedCategory?.id,
            onCategory = {
                restoreCategoryFocus = true
                categoryPickerOpen = false
                onCategory(it)
            },
            onClose = {
                restoreCategoryFocus = true
                categoryPickerOpen = false
            },
        )
    }
}

@Composable
private fun ProgrammeHero(
    channel: WatchioGuideChannel?,
    previewChannel: WatchioGuideChannel?,
    programme: WatchioGuideProgramme?,
    playerManager: WatchioPlayerManager?,
    playerState: WatchioPlayerState?,
    nowEpochMs: Long,
    isCompact: Boolean,
    heroHeight: Dp,
    onDetails: (() -> Unit)?,
    onWatchLive: (() -> Unit)?,
) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    val spacing = LocalWatchioSpacing.current
    val zone = ZoneId.systemDefault()
    val title = programme?.title ?: "TV Guide"
    val description = programme?.description?.takeIf { it.isNotBlank() }
        ?: if (channel != null) "Browse programmes on ${channel.displayName}." else "Choose a channel and programme to see details."
    val activeChannel = previewChannel ?: channel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(Brush.horizontalGradient(listOf(colors.surfaceBase, colors.surfaceElevated, colors.surfaceCard)))
            .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = 2.dp)
            .testTag("tv-guide-hero"),
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val playerHeight = (heroHeight - 4.dp).coerceAtLeast(36.dp)
        val playerWidth = (playerHeight.value * 16f / 9f).dp
        if (playerManager != null && playerState != null && activeChannel != null) {
            WatchioPlayerSurface(
                playerManager = playerManager,
                playerState = playerState,
                modifier = Modifier
                    .width(playerWidth)
                    .height(playerHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .testTag("tv-guide-mini-player"),
                onClick = onWatchLive ?: {},
                fallbackLogoUrl = activeChannel.logo,
                fallbackLabel = activeChannel.displayName,
                showLogoFallbackOnUnavailable = true,
                useFitScaling = true,
                contentDescription = "Live preview for ${activeChannel.displayName}",
            )
        } else {
            HeroChannelTile(
                channel = channel,
                isCompact = isCompact,
                usesArtworkFallback = programme?.icon.isNullOrBlank(),
                modifier = Modifier
                    .width(playerWidth)
                    .height(playerHeight),
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (programme?.isLiveNow == true || programme?.let { it.startUtcMs <= nowEpochMs && it.endUtcMs > nowEpochMs } == true) {
                    Text("LIVE NOW", color = colors.liveTvAccent, style = type.label, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = channel?.displayName ?: "Watchio",
                    color = colors.liveTvAccent,
                    style = type.label,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.testTag("tv-guide-hero-channel-name"),
                )
                Text("•", color = colors.textSecondary, style = type.label)
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = if (heroHeight < 80.dp) type.cardTitle else type.screenTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag("tv-guide-hero-title"),
                )
                programme?.let {
                    Text(
                        text = "${formatTime(it.startUtcMs, zone)} - ${formatTime(it.endUtcMs, zone)}" +
                            (it.category?.takeIf(String::isNotBlank)?.let { category -> "  •  $category" } ?: ""),
                        color = colors.textSecondary,
                        style = type.label,
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = description,
                    color = colors.textSecondary,
                    style = type.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("tv-guide-hero-description"),
                )
                if (onDetails != null) {
                    CompactGuideAction("DETAILS", colors.focusGlow, onDetails)
                }
            }
        }
    }
}

@Composable
private fun HeroChannelTile(
    channel: WatchioGuideChannel?,
    isCompact: Boolean,
    usesArtworkFallback: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    Box(
        modifier = modifier
            .then(if (usesArtworkFallback) Modifier.testTag("tv-guide-hero-fallback") else Modifier)
            .background(colors.surfaceStatus, RoundedCornerShape(6.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!channel?.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel?.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(channel?.channelNumber ?: "TV", color = colors.liveTvAccent, fontWeight = FontWeight.Bold, style = LocalWatchioTypography.current.cardTitle)
                Text(
                    channel?.displayName ?: "Watchio",
                    color = colors.textSecondary,
                    style = LocalWatchioTypography.current.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvGuideHeader(
    state: TvGuideUiState,
    isCompact: Boolean,
    firstFocus: FocusRequester,
    onJumpToNow: () -> Unit,
    onDay: (java.time.LocalDate) -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(colors.surfaceElevated)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (isCompact) 4.dp else 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactGuideAction("NOW", colors.focusGlow, onJumpToNow, Modifier.focusRequester(firstFocus).testTag("tv-guide-now"))
        state.window.days.forEach { day ->
            CompactGuideAction(
                text = day.label.uppercase(),
                accent = if (day.date == state.window.day) colors.focusGlow else colors.seriesAccent,
                onClick = { onDay(day.date) },
                modifier = Modifier.testTag("tv-guide-day-${day.date}"),
            )
        }
        CompactGuideAction(if (state.refreshing) "REFRESHING" else "REFRESH", colors.liveTvAccent, onRefresh)
    }
}

@Composable
private fun CompactGuideAction(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val appearance = LocalWatchioAppearance.current
    WatchioCard(
        modifier = modifier.height(30.dp),
        surfaceRole = WatchioSurfaceRole.Control,
        accent = accent,
        minWidth = 0.dp,
        minHeight = 0.dp,
        contentDescription = text,
        onClick = onClick,
    ) { focused ->
        Box(Modifier.padding(horizontal = 9.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (focused) appearance.colors.focusedText.toComposeColor() else colors.textPrimary,
                style = LocalWatchioTypography.current.label,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TvGuideCategorySelector(
    categories: List<LiveTvCategory>,
    selectedCategory: LiveTvCategory?,
    categoryFocus: FocusRequester,
    onCategory: (LiveTvCategory) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val appearance = LocalWatchioAppearance.current
    val ids = categories.map { it.id }
    val previous = TvGuideCategoryNavigation.previousIndex(ids, selectedCategory?.id)?.let(categories::get)
    val next = TvGuideCategoryNavigation.nextIndex(ids, selectedCategory?.id)?.let(categories::get)

    Row(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategorySelectorPart(
            text = "<<",
            enabled = previous != null,
            contentDescription = "Previous TV Guide category",
            modifier = Modifier.width(26.dp).testTag("tv-guide-category-previous"),
            onClick = { previous?.let(onCategory) },
        )
        WatchioCard(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("tv-guide-category-selector"),
            focusRequester = categoryFocus,
            surfaceRole = WatchioSurfaceRole.Control,
            accent = colors.liveTvAccent,
            minWidth = 0.dp,
            minHeight = 0.dp,
            contentDescription = "TV Guide category ${selectedCategory?.name ?: "All Channels"}",
            onClick = onOpenPicker,
        ) { focused ->
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = (selectedCategory?.name ?: "All Channels").uppercase(),
                    color = if (focused) appearance.colors.focusedText.toComposeColor() else colors.textPrimary,
                    style = LocalWatchioTypography.current.label,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CategorySelectorPart(
            text = ">>",
            enabled = next != null,
            contentDescription = "Next TV Guide category",
            modifier = Modifier.width(26.dp).testTag("tv-guide-category-next"),
            onClick = { next?.let(onCategory) },
        )
    }
}

@Composable
private fun CategorySelectorPart(
    text: String,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val appearance = LocalWatchioAppearance.current
    WatchioCard(
        modifier = modifier.fillMaxHeight(),
        surfaceRole = WatchioSurfaceRole.Control,
        accent = colors.liveTvAccent,
        enabled = enabled,
        minWidth = 0.dp,
        minHeight = 0.dp,
        contentDescription = contentDescription,
        onClick = onClick,
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = when {
                    !enabled -> colors.textMuted.copy(alpha = 0.45f)
                    focused -> appearance.colors.focusedText.toComposeColor()
                    else -> colors.textPrimary
                },
                style = LocalWatchioTypography.current.label,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WatchioGuideChannelCard(
    channel: WatchioGuideChannel,
    channelWidth: Dp,
    rowHeight: Dp,
    isCompact: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    val spacing = LocalWatchioSpacing.current
    val appearance = LocalWatchioAppearance.current

    WatchioCard(
        modifier = Modifier
            .width(channelWidth)
            .height(rowHeight)
            .semantics { contentDescription = "Channel ${channel.displayName}" },
        surfaceRole = WatchioSurfaceRole.Card,
        accent = if (isSelected) colors.focusGlow else colors.liveTvAccent,
        selected = isSelected,
        onClick = onClick,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isCompact) spacing.xs else 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
        ) {
            // Channel logo with fallback
            Box(
                modifier = Modifier
                    .size(if (isCompact) 22.dp else 26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(1.dp),
                    )
                } else {
                    Text(
                        text = channel.channelNumber ?: "TV",
                        color = colors.textSecondary,
                        style = type.label,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            // Channel name + number
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (channel.channelNumber != null) {
                        Text(
                            text = channel.channelNumber,
                            color = colors.textMuted,
                            style = type.label,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = channel.displayName,
                        color = if (focused) appearance.colors.focusedText.toComposeColor() else colors.textPrimary,
                        style = type.label,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Favorite subtle indicator
            if (channel.isFavourite) {
                Text(
                    text = "★",
                    color = colors.focusGlow,
                    style = type.label,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun WatchioGuideProgrammeCard(
    programme: WatchioGuideProgramme,
    channel: WatchioGuideChannel,
    widthDp: Float,
    rowHeight: Dp,
    isSelected: Boolean,
    nowEpochMs: Long,
    zone: ZoneId,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    val spacing = LocalWatchioSpacing.current
    val appearance = LocalWatchioAppearance.current
    val isPast = programme.endUtcMs <= nowEpochMs
    val isCurrent = programme.isLiveNow

    val accent = when {
        isSelected -> colors.focusGlow
        isCurrent -> colors.liveTvAccent
        isPast -> Color.Transparent
        else -> colors.cardOutline
    }

    val contentDesc = "${programme.title}, ${channel.displayName}, ${formatTime(programme.startUtcMs, zone)} to ${formatTime(programme.endUtcMs, zone)}"

    WatchioCard(
        modifier = Modifier
            .width(widthDp.dp)
            .height(rowHeight)
            .testTag("tv-guide-programme-${programme.programmeId}")
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { contentDescription = contentDesc },
        surfaceRole = WatchioSurfaceRole.Card,
        focusRequester = focusRequester,
        accent = accent,
        selected = isSelected,
        backgroundColor = if (isPast) colors.surfaceElevated.copy(alpha = 0.55f) else null,
        onClick = onClick,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (widthDp < 60f) 4.dp else spacing.sm, vertical = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isCurrent) 3.dp else 0.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = programme.title,
                    color = when {
                        focused -> appearance.colors.focusedText.toComposeColor()
                        isPast -> colors.textMuted
                        else -> colors.textPrimary
                    },
                    style = if (widthDp < 80f) type.label else type.cardTitle,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (widthDp >= 100f) {
                    val timeText = if (widthDp >= 180f) {
                        "${formatTime(programme.startUtcMs, zone)} - ${formatTime(programme.endUtcMs, zone)}"
                    } else {
                        formatTime(programme.startUtcMs, zone)
                    }
                    Text(
                        text = timeText,
                        color = if (focused) appearance.colors.focusedText.toComposeColor().copy(alpha = 0.8f) else colors.textSecondary,
                        style = type.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (widthDp >= 260f && !programme.category.isNullOrBlank()) {
                    Text(
                        text = programme.category,
                        color = colors.textMuted,
                        style = type.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Slim live progress indicator along bottom edge
            if (isCurrent) {
                val progress = TvGuideTimeline.progress(nowEpochMs, programme.startUtcMs, programme.endUtcMs)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(colors.surfaceElevated.copy(alpha = 0.4f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                            .background(if (focused) appearance.colors.focusedText.toComposeColor() else colors.liveTvAccent)
                            .testTag("tv-guide-programme-progress"),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeHeader(
    start: Long,
    end: Long,
    nowEpochMs: Long,
    zone: ZoneId,
    width: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalWatchioColors.current
    val slotWidthDp = (TvGuideTimeline.SlotMinutes * TvGuideTimeline.MinuteWidthDp).dp
    val slots = ceil((end - start) / (TvGuideTimeline.SlotMinutes * 60_000f)).toInt()

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(colors.surfaceStatus),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(slots) { index ->
                val time = start + index * TvGuideTimeline.SlotMinutes * 60_000L
                val isHour = (index % 2) == 0
                Box(
                    modifier = Modifier
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    // Vertical tick
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(if (isHour) 12.dp else 7.dp)
                            .align(Alignment.BottomStart)
                            .background(if (isHour) colors.textSecondary.copy(alpha = 0.6f) else colors.textMuted.copy(alpha = 0.3f)),
                    )
                    Text(
                        text = formatTime(time, zone),
                        color = if (isHour) colors.textPrimary else colors.textSecondary,
                        fontWeight = if (isHour) FontWeight.Bold else FontWeight.Normal,
                        style = LocalWatchioTypography.current.label,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // Small indicator in time ruler if now is in current window
        if (nowEpochMs in start..end) {
            val nowOffsetDp = TvGuideTimeline.offsetDp(nowEpochMs, start)
            Box(
                modifier = Modifier
                    .padding(start = nowOffsetDp.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(colors.liveTvAccent),
            )
        }
    }
}

@Composable
private fun ProgrammeRow(
    channel: WatchioGuideChannel,
    programmes: List<WatchioGuideProgramme>,
    window: WatchioGuideWindow,
    nowEpochMs: Long,
    rowHeight: Dp,
    timelineWidth: androidx.compose.ui.unit.Dp,
    selectedProgrammeId: String?,
    detailsReturnFocus: FocusRequester,
    onProgrammeFocused: (WatchioGuideProgramme) -> Unit,
    onProgramme: (WatchioGuideProgramme) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val zone = ZoneId.systemDefault()

    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(rowHeight)
            .drawBehind {
                val slotWidthPx = (TvGuideTimeline.SlotMinutes * TvGuideTimeline.MinuteWidthDp).dp.toPx()
                val slots = ceil((window.endUtcMs - window.startUtcMs) / (TvGuideTimeline.SlotMinutes * 60_000f)).toInt()
                val hourColor = colors.surfaceElevated.copy(alpha = 0.35f)
                val halfHourColor = colors.surfaceElevated.copy(alpha = 0.15f)
                for (i in 0 until slots) {
                    val x = i * slotWidthPx
                    val isHour = (i % 2) == 0
                    drawLine(
                        color = if (isHour) hourColor else halfHourColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
    ) {
        Row(
            modifier = Modifier
                .width(timelineWidth)
                .height(rowHeight),
        ) {
            var cursor = window.startUtcMs
            val visible = programmes
                .filter { it.endUtcMs > window.startUtcMs && it.startUtcMs < window.endUtcMs }
                .sortedBy { it.startUtcMs }

            if (visible.isEmpty()) {
                NoInfoCell(timelineWidth, rowHeight)
                return@Row
            }

            visible.forEach { programme ->
                val gap = (programme.startUtcMs.coerceAtLeast(window.startUtcMs) - cursor).coerceAtLeast(0L)
                if (gap > 0) Spacer(Modifier.width(TvGuideTimeline.widthForGapDp(gap).dp))

                val width = TvGuideTimeline.widthDp(programme.startUtcMs, programme.endUtcMs, window.startUtcMs, window.endUtcMs)
                WatchioGuideProgrammeCard(
                    programme = programme,
                    channel = channel,
                    widthDp = width,
                    rowHeight = rowHeight,
                    isSelected = programme.programmeId == selectedProgrammeId,
                    nowEpochMs = nowEpochMs,
                    zone = zone,
                    focusRequester = detailsReturnFocus.takeIf { programme.programmeId == selectedProgrammeId },
                    onFocused = { onProgrammeFocused(programme) },
                    onClick = { onProgramme(programme) },
                )
                cursor = cursor.coerceAtLeast(programme.endUtcMs.coerceAtMost(window.endUtcMs))
            }

            val tail = (window.endUtcMs - cursor).coerceAtLeast(0L)
            if (tail > 0) Spacer(Modifier.width(TvGuideTimeline.widthForGapDp(tail).dp))
        }
    }
}

@Composable
private fun NoInfoCell(width: androidx.compose.ui.unit.Dp, rowHeight: Dp) {
    val colors = LocalWatchioColors.current
    Box(
        modifier = Modifier
            .width(width)
            .height(rowHeight)
            .background(colors.surfaceCard)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("No programme information", color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NowLine(
    nowEpochMs: Long,
    window: WatchioGuideWindow,
    channelWidthDp: Float,
    scrollDp: Float,
) {
    if (nowEpochMs !in window.startUtcMs..window.endUtcMs) return
    val colors = LocalWatchioColors.current
    val offset = TvGuideTimeline.nowLineOffsetDp(
        nowUtcMs = nowEpochMs,
        windowStartUtcMs = window.startUtcMs,
        windowEndUtcMs = window.endUtcMs,
        channelWidthDp = channelWidthDp,
        scrollDp = scrollDp,
    ) ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f),
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(colors.liveTvAccent)
                .testTag("tv-guide-now-line"),
        )
        Box(
            modifier = Modifier
                .padding(start = (offset - 12f).coerceAtLeast(channelWidthDp).dp, top = 2.dp)
                .background(colors.liveTvAccent, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = "NOW",
                color = colors.selectedButtonText,
                fontWeight = FontWeight.Bold,
                style = LocalWatchioTypography.current.label,
            )
        }
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
    val initialFocus = remember { FocusRequester() }
    val initialCategoryId = selectedCategoryId.takeIf { selected -> categories.any { it.id == selected } } ?: categories.firstOrNull()?.id
    LaunchedEffect(Unit) {
        // AlertDialog content is hosted in a separate window; wait for it to attach and settle.
        repeat(2) { withFrameNanos { } }
        initialFocus.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Select TV Guide Category", style = LocalWatchioTypography.current.cardTitle, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .testTag("tv-guide-category-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    val isSelected = category.id == selectedCategoryId
                    WatchioCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("tv-guide-category-option-${category.id}"),
                        surfaceRole = WatchioSurfaceRole.Control,
                        accent = if (isSelected) colors.focusGlow else colors.liveTvAccent,
                        minWidth = 0.dp,
                        minHeight = 42.dp,
                        focusRequester = initialFocus.takeIf { category.id == initialCategoryId },
                        onClick = { onCategory(category) },
                    ) { _ ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = category.name,
                                style = LocalWatchioTypography.current.body,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(colors.liveTvAccent, shape = RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                    }
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
private fun GuideTitle(state: TvGuideUiState, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    Column(modifier) {
        Text("TV Guide", color = colors.textPrimary, fontWeight = FontWeight.Bold, style = type.screenTitle)
        Text(statusText(state), color = colors.textMuted, style = type.label)
    }
}

@Composable
private fun LoadingGuide() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
