package com.watchioiptv.nativeapp.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.watchioiptv.nativeapp.data.series.SeriesRepository
import com.watchioiptv.nativeapp.data.series.WatchioEpisodeItem
import com.watchioiptv.nativeapp.data.series.WatchioSeason
import com.watchioiptv.nativeapp.data.series.WatchioSeriesItem
import com.watchioiptv.nativeapp.feature.movies.HeartIcon
import com.watchioiptv.nativeapp.feature.movies.extractReleaseYear
import com.watchioiptv.nativeapp.feature.movies.formatRating
import com.watchioiptv.nativeapp.feature.movies.formatRuntime
import com.watchioiptv.nativeapp.ui.components.ResumePlaybackDialog
import com.watchioiptv.nativeapp.ui.components.ResumePlaybackRequest
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioFocusableCard
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioBorders
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioRadii
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
    val type = LocalWatchioTypography.current
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
    var showSeasonDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var hasAutoScrolledTarget by remember(details.series.id, state.targetEpisodeId) { mutableStateOf(false) }

    LaunchedEffect(details.series.id) { firstFocus.requestFocus() }

    // Auto-scroll target episode into view once when entering via Continue Watching
    LaunchedEffect(state.targetEpisodeId, state.selectedEpisodes) {
        if (!hasAutoScrolledTarget && state.targetEpisodeId != null && state.selectedEpisodes.isNotEmpty()) {
            val targetIdx = state.selectedEpisodes.indexOfFirst { it.episodeId == state.targetEpisodeId }
            if (targetIdx >= 0) {
                hasAutoScrolledTarget = true
                // In LazyColumn: index 0 is hero, index 1 is episodes section header, so target is targetIdx + 2
                listState.animateScrollToItem(targetIdx + 2)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("series-details")) {
        details.backdropUrl?.let { backdrop ->
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.88f),
                        0.45f to Color.Black.copy(alpha = 0.72f),
                        0.75f to Color.Black.copy(alpha = 0.45f),
                        1.0f to Color.Black.copy(alpha = 0.35f),
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            WatchioPageHeader(
                title = "SERIES",
                onBack = onBack,
                testTagPrefix = "series-details",
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("series-details-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Item 0: Hero / Details panel
                item(key = "series_hero") {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compactLandscape = maxWidth < 980.dp
                        val posterWidth = if (compactLandscape) 140.dp else 165.dp
                        val gap = if (compactLandscape) 20.dp else 28.dp

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            // Left column: Poster, Rating, Tabs
                            Column(
                                modifier = Modifier.width(posterWidth),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SeriesDetailsPoster(
                                    url = details.posterUrl,
                                    modifier = Modifier
                                        .width(posterWidth)
                                        .aspectRatio(2f / 3f)
                                        .testTag("series-poster"),
                                )
                                val ratingDisplay = formatRating(details.rating)
                                if (ratingDisplay != null) {
                                    Text(
                                        text = ratingDisplay,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("series-details-rating"),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SeriesDetailsTabButton(
                                        title = "Episodes",
                                        count = details.episodes.size,
                                        selected = state.activeTab == "episodes",
                                        onClick = { onTab("episodes") },
                                        modifier = Modifier.weight(1f).testTag("series-tab-episodes"),
                                    )
                                    SeriesDetailsTabButton(
                                        title = "Cast",
                                        count = null,
                                        selected = state.activeTab == "cast",
                                        onClick = { onTab("cast") },
                                        modifier = Modifier.weight(1f).testTag("series-tab-cast"),
                                    )
                                }
                            }

                            // Right column: Title, Meta, Actions, Details, Plot, Season Selector
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = details.title,
                                    color = colors.textPrimary,
                                    style = type.screenTitle,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("series-title"),
                                )
                                val metaSummary = formatSeriesMetaLine(details)
                                if (metaSummary.isNotBlank()) {
                                    Text(
                                        text = metaSummary,
                                        color = colors.textSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("series-details-meta"),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SeriesDetailsActionButton(
                                        title = "Play",
                                        accent = colors.seriesAccent,
                                        isPrimary = true,
                                        onClick = {
                                            if (resumeEpisode != null) {
                                                resumeDialogRequest = ResumePlaybackRequest(
                                                    title = details.title,
                                                    subtitle = "${SeriesRepository.formatEpisodeLabel(resumeEpisode.seasonNumber, resumeEpisode.episodeNumber, null) ?: "Episode"} - ${resumeEpisode.title}",
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
                                        modifier = Modifier
                                            .focusRequester(firstFocus)
                                            .testTag("series-play-button"),
                                    )
                                    details.trailerKey?.takeIf { it.isNotBlank() }?.let { key ->
                                        SeriesDetailsActionButton(
                                            title = "Trailer",
                                            accent = colors.liveTvAccent,
                                            onClick = { onTrailer(key) },
                                            modifier = Modifier.testTag("series-trailer-button"),
                                        )
                                    }
                                    SeriesDetailsActionButton(
                                        title = if (details.series.isFavorite) "Favourited" else "Favourite",
                                        accent = colors.focusGlow,
                                        icon = {
                                            HeartIcon(
                                                filled = details.series.isFavorite,
                                                color = if (details.series.isFavorite) colors.seriesAccent else Color.White,
                                            )
                                        },
                                        onClick = onFavorite,
                                        modifier = Modifier.testTag("series-favorite-button"),
                                    )
                                }
                                SeriesDetailRow("Director", details.director)
                                SeriesDetailRow("Release Date", details.releaseDate)
                                SeriesDetailRow("Genre", details.genre)
                                if (state.activeTab != "cast") {
                                    SeriesDetailRow("Cast", details.cast)
                                }
                                if (!details.plot.isNullOrBlank()) {
                                    Text(
                                        text = details.plot,
                                        color = colors.textSecondary,
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.5.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth(0.95f)
                                            .testTag("series-plot-text"),
                                    )
                                }
                                if (state.activeTab == "episodes" && details.seasons.isNotEmpty()) {
                                    val currentSeason = details.seasons.firstOrNull { it.seasonNumber == state.selectedSeasonNumber }
                                        ?: details.seasons.firstOrNull()
                                    val seasonBtnText = currentSeason?.name ?: "Season ${state.selectedSeasonNumber ?: 1}"
                                    SeasonSelectorButton(
                                        title = "$seasonBtnText ▼",
                                        accent = colors.seriesAccent,
                                        onClick = { showSeasonDialog = true },
                                        modifier = Modifier.testTag("series-season-selector-button"),
                                    )
                                }
                            }
                        }
                    }
                }

                // Lower section: Cast tab or Episodes list
                if (state.activeTab == "cast") {
                    item(key = "series_cast_tab") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = details.cast?.takeIf { it.isNotBlank() } ?: "No cast information available",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(vertical = 8.dp).testTag(if (details.cast.isNullOrBlank()) "series-cast-empty" else "series-cast-content"),
                        )
                    }
                } else {
                    val currentSeason = details.seasons.firstOrNull { it.seasonNumber == state.selectedSeasonNumber }
                    val seasonName = currentSeason?.name ?: "Season ${state.selectedSeasonNumber ?: 1}"
                    val epCount = state.selectedEpisodes.size
                    val epCountText = if (epCount == 1) "1 Episode" else "$epCount Episodes"

                    item(key = "series_episodes_section_header") {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "$seasonName • $epCountText",
                            color = colors.textPrimary,
                            style = type.cardTitle,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("series-episodes-header"),
                        )
                    }

                    if (state.selectedEpisodes.isEmpty()) {
                        item(key = "series_no_episodes") {
                            Text("No episodes available", color = colors.textSecondary, modifier = Modifier.padding(24.dp).testTag("series-no-episodes"))
                        }
                    } else {
                        items(state.selectedEpisodes, key = { it.episodeId }) { episode ->
                            val isResumable = SeriesRepository.shouldResumePosition(episode.resumePositionMs, episode.resumeDurationMs)
                            val isCompleted = SeriesRepository.isCompletedPosition(episode.resumePositionMs, episode.resumeDurationMs)
                            val isTarget = episode.episodeId == state.targetEpisodeId

                            EpisodeCard(
                                episode = episode,
                                backdropFallback = details.backdropUrl ?: details.posterUrl,
                                isResumable = isResumable,
                                isCompleted = isCompleted,
                                isTarget = isTarget,
                                onClick = {
                                    if (isResumable) {
                                        resumeDialogRequest = ResumePlaybackRequest(
                                            title = details.title,
                                            subtitle = "${SeriesRepository.formatEpisodeLabel(episode.seasonNumber, episode.episodeNumber, null) ?: "Episode"} - ${episode.title}",
                                            resumePositionMs = episode.resumePositionMs ?: 0L,
                                            durationMs = episode.resumeDurationMs,
                                            onResume = {
                                                resumeDialogRequest = null
                                                onEpisode(episode)
                                            },
                                            onRestart = {
                                                resumeDialogRequest = null
                                                onEpisode(episode.copy(resumePositionMs = 0L))
                                            },
                                            onDismiss = {
                                                resumeDialogRequest = null
                                            },
                                        )
                                    } else {
                                        onEpisode(episode)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Season selection dialog
        if (showSeasonDialog) {
            SeasonSelectionDialog(
                seasons = details.seasons,
                selectedSeasonNumber = state.selectedSeasonNumber ?: details.seasons.firstOrNull()?.seasonNumber ?: 1,
                onSelectSeason = { seasonNum ->
                    showSeasonDialog = false
                    onSeason(seasonNum)
                },
                onDismiss = { showSeasonDialog = false },
            )
        }

        resumeDialogRequest?.let { request ->
            ResumePlaybackDialog(request = request)
        }
    }
}

// --------------------------------------------------------------------------
// Episode card
// --------------------------------------------------------------------------

@Composable
private fun EpisodeCard(
    episode: WatchioEpisodeItem,
    backdropFallback: String?,
    isResumable: Boolean,
    isCompleted: Boolean,
    isTarget: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val radii = LocalWatchioRadii.current
    val borders = LocalWatchioBorders.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radii.md)

    val progress = if (isResumable && episode.resumeDurationMs != null && episode.resumeDurationMs > 0L) {
        ((episode.resumePositionMs ?: 0L).toFloat() / episode.resumeDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = shape,
                ambientColor = colors.focusGlow,
                spotColor = colors.focusGlow,
            )
            .border(
                BorderStroke(
                    width = if (focused) borders.focused else if (isTarget) 1.5.dp else borders.normal,
                    color = if (focused) colors.focusBorder else if (isTarget) colors.seriesAccent else Color.White.copy(alpha = 0.08f),
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .testTag("series-episode-card"),
        color = if (focused) colors.seriesAccent.copy(alpha = 0.16f)
        else if (isTarget) colors.seriesAccent.copy(alpha = 0.08f)
        else colors.surfaceCard,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 16:9 Thumbnail
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .background(Color.DarkGray, RoundedCornerShape(radii.sm))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(radii.sm)),
            ) {
                AsyncImage(
                    model = episode.imageUrl ?: backdropFallback,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("series-episode-thumbnail"),
                )
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("series-episode-watched-badge"),
                    ) {
                        Text("Watched", color = colors.seriesAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isResumable && progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("series-episode-progress-track"),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(colors.seriesAccent)
                                .testTag("series-episode-progress-bar"),
                        )
                    }
                }
            }

            // Episode info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val epLabel = SeriesRepository.formatEpisodeLabel(episode.seasonNumber, episode.episodeNumber, null)
                    if (!epLabel.isNullOrBlank()) {
                        Text(
                            text = epLabel,
                            color = colors.seriesAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("series-episode-number"),
                        )
                    }
                    Text(
                        text = episode.title,
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("series-episode-title"),
                    )
                }
                val runtimeFormatted = formatRuntime(episode.duration)
                if (!runtimeFormatted.isNullOrBlank()) {
                    Text(
                        text = runtimeFormatted,
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        modifier = Modifier.testTag("series-episode-runtime"),
                    )
                }
                if (!episode.plot.isNullOrBlank()) {
                    Text(
                        text = episode.plot,
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 16.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("series-episode-plot"),
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Season selection dialog
// --------------------------------------------------------------------------

@Composable
private fun SeasonSelectionDialog(
    seasons: List<WatchioSeason>,
    selectedSeasonNumber: Int,
    onSelectSeason: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val radii = LocalWatchioRadii.current
    val borders = LocalWatchioBorders.current
    val selectedFocus = remember { FocusRequester() }

    LaunchedEffect(selectedSeasonNumber) {
        selectedFocus.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 320.dp, max = 460.dp)
            .testTag("series-season-dialog"),
        shape = RoundedCornerShape(radii.lg),
        containerColor = colors.surfaceCard,
        title = {
            Text(
                text = "Select Season",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(seasons, key = { it.seasonNumber }) { season ->
                    val isSelected = season.seasonNumber == selectedSeasonNumber
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()
                    val shape = RoundedCornerShape(radii.md)

                    val epCountText = if (season.episodeCount == 1) "1 Episode" else "${season.episodeCount} Episodes"

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier)
                            .border(
                                BorderStroke(
                                    width = if (focused) borders.focused else borders.normal,
                                    color = if (focused) colors.focusBorder else if (isSelected) colors.seriesAccent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                                ),
                                shape = shape,
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = { onSelectSeason(season.seasonNumber) },
                            )
                            .focusable(interactionSource = interactionSource)
                            .testTag("series-season-option-${season.seasonNumber}"),
                        color = if (focused) colors.seriesAccent.copy(alpha = 0.28f)
                        else if (isSelected) colors.seriesAccent.copy(alpha = 0.16f)
                        else colors.surfaceElevated,
                        shape = shape,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = season.name,
                                color = colors.textPrimary,
                                fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                            )
                            if (season.episodeCount > 0) {
                                Text(
                                    text = epCountText,
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("series-season-dialog-close")) {
                Text("Close", color = colors.seriesAccent)
            }
        },
    )
}

// --------------------------------------------------------------------------
// UI Components & Buttons
// --------------------------------------------------------------------------

@Composable
private fun Poster(url: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.DarkGray,
    ) {
        if (url.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Image", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SeriesDetailsPoster(url: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
        shape = shape,
        color = Color.DarkGray,
    ) {
        if (url.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Image", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SeriesDetailsTabButton(
    title: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val radii = LocalWatchioRadii.current
    val borders = LocalWatchioBorders.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radii.sm)

    val label = if (count != null) "$title ($count)" else title

    Surface(
        modifier = modifier
            .height(34.dp)
            .border(
                BorderStroke(
                    width = if (focused) borders.focused else borders.normal,
                    color = if (focused) colors.focusBorder else if (selected) colors.seriesAccent else Color.White.copy(alpha = 0.08f),
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource),
        color = if (focused) colors.seriesAccent.copy(alpha = 0.28f)
        else if (selected) colors.seriesAccent.copy(alpha = 0.20f)
        else colors.surfaceElevated,
        shape = shape,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected || focused) colors.textPrimary else colors.textSecondary,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SeasonSelectorButton(
    title: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val radii = LocalWatchioRadii.current
    val borders = LocalWatchioBorders.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radii.md)

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 140.dp, minHeight = 38.dp)
            .shadow(
                elevation = if (focused) 8.dp else 0.dp,
                shape = shape,
                ambientColor = colors.focusGlow,
                spotColor = colors.focusGlow,
            )
            .border(
                BorderStroke(
                    width = if (focused) borders.focused else borders.normal,
                    color = if (focused) colors.focusBorder else accent.copy(alpha = 0.5f),
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource),
        color = if (focused) accent.copy(alpha = 0.28f) else colors.surfaceElevated,
        shape = shape,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.5.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SeriesDetailsActionButton(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val radii = LocalWatchioRadii.current
    val borders = LocalWatchioBorders.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radii.md)

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = if (isPrimary) 120.dp else 100.dp, minHeight = 44.dp)
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = shape,
                ambientColor = colors.focusGlow,
                spotColor = colors.focusGlow,
            )
            .border(
                BorderStroke(
                    width = if (focused) borders.focused else borders.normal,
                    color = if (focused) colors.focusBorder else Color.White.copy(alpha = 0.08f),
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource),
        color = if (isPrimary && !focused) accent.copy(alpha = 0.22f) else colors.surfaceCard,
        shape = shape,
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (focused) accent.copy(alpha = if (isPrimary) 0.32f else 0.20f)
                    else if (isPrimary) accent.copy(alpha = 0.18f)
                    else colors.surfaceCard
                )
                .padding(horizontal = 20.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.invoke()
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = if (isPrimary || focused) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SeriesDetailRow(label: String, value: String?) {
    value?.takeIf { it.isNotBlank() } ?: return
    val colors = LocalWatchioColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            color = colors.textSecondary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
        )
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 13.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// --------------------------------------------------------------------------
// Formatting helpers
// --------------------------------------------------------------------------

internal fun formatSeriesMetaLine(details: SeriesDetails): String {
    val year = extractReleaseYear(details.releaseDate)
    val genre = details.genre?.takeIf { it.isNotBlank() }
    val rating = formatRating(details.rating)

    return listOfNotNull(year, genre, rating)
        .joinToString(" • ")
}

