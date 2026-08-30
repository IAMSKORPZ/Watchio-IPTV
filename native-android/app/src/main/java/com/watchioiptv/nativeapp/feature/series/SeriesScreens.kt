package com.watchioiptv.nativeapp.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.watchioiptv.nativeapp.data.series.SeriesCardUiModel
import com.watchioiptv.nativeapp.data.series.SeriesCategory
import com.watchioiptv.nativeapp.data.series.SeriesCategoryKind
import com.watchioiptv.nativeapp.data.series.SeriesDetails
import com.watchioiptv.nativeapp.data.series.WatchioEpisodeItem
import com.watchioiptv.nativeapp.data.series.WatchioSeriesItem
import com.watchioiptv.nativeapp.feature.movies.formatRating
import com.watchioiptv.nativeapp.ui.components.ResumePlaybackDialog
import com.watchioiptv.nativeapp.ui.components.ResumePlaybackRequest
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioFocusableCard
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography

@Composable
fun SeriesScreen(
    state: SeriesUiState,
    onCategory: (SeriesCategory) -> Unit,
    onCategorySearch: (String) -> Unit = {},
    onSearch: (String) -> Unit,
    onSeries: (SeriesCardUiModel) -> Unit,
    onBack: () -> Unit,
    initialSearchVisible: Boolean = false,
) {
    val colors = LocalWatchioColors.current
    val firstCategoryFocus = remember { FocusRequester() }
    var searchVisible by remember { mutableStateOf(initialSearchVisible) }
    var optionsSeries by remember { mutableStateOf<SeriesCardUiModel?>(null) }
    BackHandler {
        if (searchVisible) {
            searchVisible = false
            onSearch("")
        } else {
            onBack()
        }
    }
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(colors.surfaceBase), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.seriesAccent)
        }
        return
    }
    LaunchedEffect(state.categories.firstOrNull()?.id) {
        if (state.categories.isNotEmpty()) firstCategoryFocus.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(colors.surfaceBase).testTag("series-screen")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            WatchioPageHeader(title = "SERIES", onBack = onBack, testTagPrefix = "series") {
                SeriesSearchIconButton(
                    accent = colors.moviesAccent,
                    onClick = { searchVisible = true },
                    modifier = Modifier.testTag("series-search"),
                )
                SeriesMoreButton(
                    accent = colors.seriesAccent,
                    onClick = {},
                    modifier = Modifier.testTag("series-more"),
                )
            }
            optionsSeries?.let { series ->
                SeriesOptionsDialog(
                    item = series,
                    onDetails = {
                        optionsSeries = null
                        onSeries(series)
                    },
                    onDismiss = { optionsSeries = null },
                )
            }
            state.errorMessage?.let { Text(it, color = colors.liveTvAccent) }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compactLandscape = maxWidth < 980.dp
                val categoryWidth = if (compactLandscape) 154.dp else 220.dp
                val gap = if (compactLandscape) 12.dp else 16.dp
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    SeriesCategoryRail(
                        state = state,
                        firstFocus = firstCategoryFocus,
                        onCategory = onCategory,
                        modifier = Modifier.width(categoryWidth).fillMaxHeight(),
                    )
                    WatchioCard(
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("series-grid-panel"),
                        accent = colors.seriesAccent,
                        minWidth = 0.dp,
                        minHeight = 0.dp,
                    ) {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            if (state.series.isEmpty()) {
                                Box(Modifier.fillMaxSize().testTag("series-empty"), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (state.selectedCategory?.kind == SeriesCategoryKind.ContinueWatching || state.selectedCategory?.id == "continue_watching") "Nothing to continue watching"
                                        else if (state.selectedCategory?.kind == SeriesCategoryKind.Favorites || state.selectedCategory?.id == "favorites") "No favourite series yet."
                                        else if (state.selectedCategory?.kind == SeriesCategoryKind.History || state.selectedCategory?.id == "history") "No series history yet."
                                        else "No series in this category.",
                                        color = colors.textSecondary,
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = if (compactLandscape) 92.dp else 132.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    modifier = Modifier.fillMaxSize().testTag("series-grid"),
                                ) {
                                    items(state.series, key = { "${it.series.providerId.value}:${it.series.id}" }) { item ->
                                        SeriesCard(
                                            item = item,
                                            onSeries = onSeries,
                                            onSeriesOptions = { optionsSeries = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (searchVisible) {
            SeriesSearchOverlay(
                query = state.searchQuery,
                results = if (state.searchQuery.isBlank()) emptyList() else state.series,
                onSearch = onSearch,
                onSelect = { item ->
                    searchVisible = false
                    onSearch("")
                    onSeries(item)
                },
                onDismiss = {
                    searchVisible = false
                    onSearch("")
                },
            )
        }
    }
}

// --------------------------------------------------------------------------
// Header action composables
// --------------------------------------------------------------------------

/**
 * Compact Search icon button for the Series header (Phase 14.2J).
 * Renders a magnifying glass icon matching the compact 44x44dp [SeriesMoreButton].
 */
@Composable
private fun SeriesSearchIconButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier
            .size(44.dp)
            .semantics { onClick(label = "Search Series") { onClick(); true } },
        accent = accent,
        minWidth = 44.dp,
        minHeight = 44.dp,
        contentDescription = "Search Series",
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
 * Compact three-dot More button for the Series header.
 * Renders a vertical ⋮ ellipsis (Unicode 22EE) — matches Movies header.
 */
@Composable
private fun SeriesMoreButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier
            .size(44.dp)
            .semantics { onClick(label = "More options") { onClick(); true } },
        accent = accent,
        minWidth = 44.dp,
        minHeight = 44.dp,
        contentDescription = "More options",
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

// --------------------------------------------------------------------------
// Category rail
// --------------------------------------------------------------------------

/**
 * Series category rail (Phase 14.2J).
 *
 * Sizing & Alignment:
 *  - Compact category cards (48dp height) with 6dp spacing.
 *  - Vertically centered text via [Alignment.CenterStart].
 *  - Preserves standard [WatchioCard] with WHITE focus border.
 *  - Active overflowing category smoothly marquees left; short/inactive categories remain static/ellipsized.
 */
@Composable
private fun SeriesCategoryRail(
    state: SeriesUiState,
    firstFocus: FocusRequester,
    onCategory: (SeriesCategory) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalWatchioColors.current
    val visible = state.categories
    WatchioCard(modifier = modifier.testTag("series-category-panel"), accent = colors.seriesAccent, minWidth = 0.dp, minHeight = 0.dp) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp).testTag("series-categories")) {
            itemsIndexed(visible, key = { _, item -> item.id }) { index, category ->
                SeriesCategoryRow(
                    category = category,
                    selected = category.id == state.selectedCategory?.id,
                    onClick = { onCategory(category) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .testTag("series-category-${category.id}"),
                )
            }
        }
    }
}

@Composable
private fun SeriesCategoryRow(
    category: SeriesCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val typography = LocalWatchioTypography.current
    var isOverflowing by remember(category.name) { mutableStateOf(false) }

    WatchioCard(
        modifier = modifier.height(48.dp),
        accent = if (selected) colors.seriesAccent else colors.focusGlow,
        selected = selected,
        minWidth = 0.dp,
        minHeight = 48.dp,
        contentDescription = category.name,
        onClick = onClick,
    ) { focused ->
        val shouldMarquee = (focused || selected) && isOverflowing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = category.name,
                color = colors.textPrimary,
                style = typography.cardTitle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (!isOverflowing && (textLayoutResult.hasVisualOverflow || textLayoutResult.didOverflowWidth)) {
                        isOverflowing = true
                    }
                },
                modifier = (if (shouldMarquee) {
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 600,
                        repeatDelayMillis = 1200,
                    )
                } else {
                    Modifier
                }).testTag("series-category-text-${category.id}"),
            )
        }
    }
}

// --------------------------------------------------------------------------
// Series search overlay
// --------------------------------------------------------------------------

@Composable
private fun SeriesSearchOverlay(
    query: String,
    results: List<SeriesCardUiModel>,
    onSearch: (String) -> Unit,
    onSelect: (SeriesCardUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
            .imePadding()
            .testTag("series-search-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        WatchioCard(
            modifier = Modifier.fillMaxWidth(0.76f).fillMaxHeight(0.84f).testTag("series-search-panel"),
            accent = colors.seriesAccent,
            minWidth = 0.dp,
            minHeight = 0.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Search series", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("series-search-close")) { Text("Close") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onSearch,
                    singleLine = true,
                    label = { Text("Search series") },
                    modifier = Modifier.fillMaxWidth().testTag("series-search-field"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSearch("") }, modifier = Modifier.testTag("series-search-clear")) { Text("Clear") }
                    Text(
                        text = if (query.isBlank()) "Type to search series" else "${results.size} results",
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 118.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize().testTag("series-search-results"),
                ) {
                    items(results.take(120), key = { "${it.series.providerId.value}:${it.series.id}" }) { item ->
                        Box(Modifier.testTag("series-search-result")) {
                            SeriesCard(item = item, onSeries = onSelect, onSeriesOptions = { onSelect(it) })
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Series options dialog
// --------------------------------------------------------------------------

@Composable
private fun SeriesOptionsDialog(item: SeriesCardUiModel, onDetails: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Series Options") },
        text = { Text(item.series.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = onDetails) { Text("View Details") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

// --------------------------------------------------------------------------
// Series card
// --------------------------------------------------------------------------

/**
 * Poster card for the Series grid and search overlay (Phase 14.2J).
 *
 * Layout:
 *  - 2:3 aspect-ratio poster (dominant) with compact rating badge overlay at top-right
 *  - Fixed-height title region (52dp — reserves space for up to 2 lines at normal text sizes)
 *
 * Rating is formatted as "★ 8.2" in a dark translucent badge on the poster.
 * Zero, null, blank, or unparseable values are hidden (no badge).
 * There is no separate rating line below the title.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesCard(
    item: SeriesCardUiModel,
    onSeries: (SeriesCardUiModel) -> Unit,
    onSeriesOptions: (SeriesCardUiModel) -> Unit = {},
) {
    val colors = LocalWatchioColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val formattedRating = item.series.formattedRating ?: formatRating(item.series.rating)
    val showProgress = item.isContinueWatching && item.progress != null && item.progress > 0f
    Column(
        Modifier
            .border(if (focused) 3.dp else 1.dp, if (focused) colors.focusBorder else Color.Transparent)
            .background(if (focused) colors.seriesAccent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSeries(item) },
                onLongClick = { onSeriesOptions(item) },
                onLongClickLabel = "Series Options",
            )
            .focusable(interactionSource = interactionSource)
            .testTag("series-card")
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Poster(item.series.coverUrl, Modifier.fillMaxSize().testTag("series-poster"))
            if (formattedRating != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .testTag("series-rating-badge"),
                ) {
                    Text(
                        text = formattedRating,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.testTag("series-rating"),
                    )
                }
            }
            if (showProgress) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("series-progress-bar-track"),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress ?: 0f)
                            .background(colors.moviesAccent)
                            .testTag("series-progress-bar"),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Fixed-height title region — all cards reserve identical vertical space.
        Box(Modifier.fillMaxWidth().height(52.dp).testTag("series-title-region")) {
            Column {
                Text(
                    text = item.series.name,
                    color = colors.textPrimary,
                    maxLines = if (item.isContinueWatching && item.episodeLabel != null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isContinueWatching && item.episodeLabel != null) {
                    Text(
                        text = item.episodeLabel,
                        color = colors.seriesAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("series-episode-label"),
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Series details screen
// --------------------------------------------------------------------------

@Composable
fun SeriesDetailsScreen(
    state: SeriesDetailsUiState,
    onPlay: (Boolean) -> Unit,
    onTrailer: (String) -> Unit,
    onFavorite: () -> Unit,
    onSeason: (Int) -> Unit,
    onTab: (String) -> Unit,
    onEpisode: (WatchioEpisodeItem) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = onBack)
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.seriesAccent)
        }
        return
    }
    val details = state.details ?: return
    val resumeEpisode = state.resumeEpisode
    val firstFocus = remember { FocusRequester() }
    var resumeDialogRequest by remember { mutableStateOf<ResumePlaybackRequest?>(null) }
    LaunchedEffect(details.series.id) { firstFocus.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().background(Color.Black).padding(24.dp).testTag("series-details")) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Poster(details.posterUrl, Modifier.width(150.dp).height(225.dp))
                    Column(Modifier.weight(1f)) {
                        WatchioFocusableCard("Back", accent = colors.focusGlow, onClick = onBack)
                        Spacer(Modifier.height(10.dp))
                        Text(details.title, color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(metaLine(details), color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            WatchioFocusableCard(
                                title = if (resumeEpisode != null) "Resume" else "Play",
                                accent = colors.seriesAccent,
                                onClick = {
                                    if (resumeEpisode != null) {
                                        resumeDialogRequest = ResumePlaybackRequest(
                                            title = details.title,
                                            subtitle = "S${resumeEpisode.seasonNumber} • E${resumeEpisode.episodeNumber} - ${resumeEpisode.title}",
                                            resumePositionMs = resumeEpisode.resumePositionMs ?: 0L,
                                            durationMs = resumeEpisode.resumeDurationMs,
                                            onResume = {
                                                resumeDialogRequest = null
                                                onPlay(true)
                                            },
                                            onRestart = {
                                                resumeDialogRequest = null
                                                onPlay(false)
                                            },
                                            onDismiss = {
                                                resumeDialogRequest = null
                                            },
                                        )
                                    } else {
                                        onPlay(false)
                                    }
                                },
                                modifier = Modifier.focusRequester(firstFocus).testTag("series-play-button"),
                            )
                            if (resumeEpisode != null) {
                                WatchioFocusableCard(
                                    title = "Start Over",
                                    accent = colors.moviesAccent,
                                    onClick = { onPlay(false) },
                                    modifier = Modifier.testTag("series-start-over-button"),
                                )
                            }
                            details.trailerKey?.takeIf { it.isNotBlank() }?.let { key ->
                                WatchioFocusableCard("Trailer", accent = colors.liveTvAccent, onClick = { onTrailer(key) })
                            }
                            WatchioFocusableCard(if (details.series.isFavorite) "Unfavorite" else "Favorite", accent = colors.focusGlow, onClick = onFavorite)
                        }
                        Spacer(Modifier.height(12.dp))
                        detailRow("Director", details.director)
                        detailRow("Release Date", details.releaseDate)
                        detailRow("Genre", details.genre)
                        Text(details.plot ?: "No description available", color = colors.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WatchioFocusableCard("Episodes(${details.episodes.size})", accent = if (state.activeTab == "episodes") colors.seriesAccent else colors.focusGlow, onClick = { onTab("episodes") })
                    WatchioFocusableCard("Cast", accent = if (state.activeTab == "cast") colors.seriesAccent else colors.focusGlow, onClick = { onTab("cast") })
                }
                Spacer(Modifier.height(12.dp))
            }
            if (state.activeTab == "cast") {
                item {
                    Text(details.cast?.takeIf { it.isNotBlank() } ?: "No cast information available", color = colors.textSecondary)
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        details.seasons.forEach { season ->
                            WatchioFocusableCard(
                                title = season.name,
                                accent = if (season.seasonNumber == state.selectedSeasonNumber) colors.seriesAccent else colors.focusGlow,
                                onClick = { onSeason(season.seasonNumber) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (state.selectedEpisodes.isEmpty()) {
                    item { Text("No episodes available", color = colors.textMuted, modifier = Modifier.padding(32.dp)) }
                } else {
                    items(state.selectedEpisodes, key = { it.episodeId }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            onEpisode = { ep ->
                                val epResumable = com.watchioiptv.nativeapp.data.series.SeriesRepository.shouldResumePosition(
                                    ep.resumePositionMs,
                                    ep.resumeDurationMs,
                                )
                                if (epResumable) {
                                    resumeDialogRequest = ResumePlaybackRequest(
                                        title = details.title,
                                        subtitle = "S${ep.seasonNumber} • E${ep.episodeNumber} - ${ep.title}",
                                        resumePositionMs = ep.resumePositionMs ?: 0L,
                                        durationMs = ep.resumeDurationMs,
                                        onResume = {
                                            resumeDialogRequest = null
                                            onEpisode(ep)
                                        },
                                        onRestart = {
                                            resumeDialogRequest = null
                                            onEpisode(ep.copy(resumePositionMs = 0L))
                                        },
                                        onDismiss = {
                                            resumeDialogRequest = null
                                        },
                                    )
                                } else {
                                    onEpisode(ep)
                                }
                            },
                        )
                    }
                }
            }
        }
        resumeDialogRequest?.let { request ->
            ResumePlaybackDialog(request = request)
        }
    }
}

// --------------------------------------------------------------------------
// Episode row
// --------------------------------------------------------------------------

@Composable
private fun EpisodeRow(episode: WatchioEpisodeItem, onEpisode: (WatchioEpisodeItem) -> Unit) {
    val colors = LocalWatchioColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(if (focused) 3.dp else 1.dp, if (focused) colors.focusBorder else colors.surfaceElevated)
            .clickable(interactionSource = interactionSource, indication = null) { onEpisode(episode) }
            .focusable(interactionSource = interactionSource)
            .background(Color.White.copy(alpha = if (focused) 0.12f else 0.05f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(episode.imageUrl, Modifier.width(130.dp).height(74.dp))
        Column(Modifier.weight(1f)) {
            Text("S${episode.seasonNumber}E${episode.episodeNumber}  ${episode.title}", color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.duration?.let { Text(it, color = colors.textMuted) }
            if (com.watchioiptv.nativeapp.data.series.SeriesRepository.shouldResumePosition(episode.resumePositionMs, episode.resumeDurationMs)) {
                Text("Resume available", color = colors.seriesAccent)
            }
        }
    }
}

// --------------------------------------------------------------------------
// Poster
// --------------------------------------------------------------------------

@Composable
private fun Poster(url: String?, modifier: Modifier) {
    Box(modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) Text("No Image", color = Color.White) else AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------------------------------------------------------
// Detail helpers
// --------------------------------------------------------------------------

@Composable
private fun detailRow(label: String, value: String?) {
    value?.takeIf { it.isNotBlank() } ?: return
    Text("$label: $value", color = LocalWatchioColors.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun metaLine(details: SeriesDetails): String = listOfNotNull(details.releaseDate, details.runtime, details.genre, details.rating)
    .filter { it.isNotBlank() }
    .joinToString("  |  ")
