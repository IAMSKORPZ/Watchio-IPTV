package com.watchioiptv.nativeapp.feature.movies

import android.content.Intent
import android.net.Uri
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import com.watchioiptv.nativeapp.core.player.shouldKeepScreenOn
import com.watchioiptv.nativeapp.data.movies.MovieCategory
import com.watchioiptv.nativeapp.data.movies.MovieCategoryKind
import com.watchioiptv.nativeapp.data.movies.MovieDetails
import com.watchioiptv.nativeapp.data.movies.WatchioMovieItem
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.ui.components.ResumePlaybackDialog
import com.watchioiptv.nativeapp.ui.components.ResumePlaybackRequest
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioFocusableCard
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.components.WatchioProgressBar
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioBorders
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioRadii
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MoviesScreen(
    state: MoviesUiState,
    onCategory: (MovieCategory) -> Unit,
    onCategorySearch: (String) -> Unit,
    onSearch: (String) -> Unit,
    onMovie: (WatchioMovieItem) -> Unit,
    onBack: () -> Unit,
    onLoadMore: () -> Unit = {},
    initialSearchVisible: Boolean = false,
) {
    val colors = LocalWatchioColors.current
    val firstCategoryFocus = remember { FocusRequester() }
    var searchVisible by remember { mutableStateOf(initialSearchVisible) }
    var optionsMovie by remember { mutableStateOf<WatchioMovieItem?>(null) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, state.movies.size, state.hasMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (state.hasMore && lastVisible >= state.movies.size - 20) onLoadMore()
            }
    }
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
            CircularProgressIndicator(color = colors.moviesAccent)
        }
        return
    }
    LaunchedEffect(state.categories.firstOrNull()?.id) {
        if (state.categories.isNotEmpty()) firstCategoryFocus.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(colors.surfaceBase).testTag("movies-screen")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            WatchioPageHeader(title = "MOVIES", onBack = onBack, testTagPrefix = "movies") {
                MoviesSearchIconButton(
                    accent = colors.seriesAccent,
                    onClick = { searchVisible = true },
                    modifier = Modifier.testTag("movies-search"),
                )
                MoviesMoreButton(
                    accent = colors.moviesAccent,
                    onClick = {},
                    modifier = Modifier.testTag("movies-more"),
                )
            }
            optionsMovie?.let { movie ->
                MovieOptionsDialog(
                    movie = movie,
                    onDetails = {
                        optionsMovie = null
                        onMovie(movie)
                    },
                    onDismiss = { optionsMovie = null },
                )
            }
            state.errorMessage?.let { Text(it, color = colors.liveTvAccent) }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compactLandscape = maxWidth < 980.dp
                val categoryWidth = if (compactLandscape) 154.dp else 220.dp
                val gap = if (compactLandscape) 12.dp else 16.dp
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MovieCategoryRail(
                        state = state,
                        firstFocus = firstCategoryFocus,
                        onCategory = onCategory,
                        modifier = Modifier.width(categoryWidth).fillMaxHeight(),
                    )
                    WatchioCard(modifier = Modifier.weight(1f).fillMaxHeight().testTag("movie-grid-panel"), accent = colors.moviesAccent, minWidth = 0.dp, minHeight = 0.dp) {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            if (state.movies.isEmpty()) {
                                Box(Modifier.fillMaxSize().testTag("movie-empty"), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (state.selectedCategory?.kind == MovieCategoryKind.ContinueWatching || state.selectedCategory?.id == "continue_watching") "Nothing to continue watching"
                                        else if (state.selectedCategory?.id == "favorites") "No favourite movies yet."
                                        else if (state.selectedCategory?.id == "history") "No movie history yet."
                                        else "No movies in this category.",
                                        color = colors.textSecondary,
                                    )
                                }
                            } else {
                                val isContinueWatching = state.selectedCategory?.kind == MovieCategoryKind.ContinueWatching || state.selectedCategory?.id == "continue_watching"
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Adaptive(minSize = if (compactLandscape) 92.dp else 132.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    modifier = Modifier.fillMaxSize().testTag("movie-grid"),
                                ) {
                                    items(
                                        items = state.movies,
                                        key = { "${it.providerId.value}:${it.id}" },
                                        contentType = { "movie_card" },
                                    ) { movie ->
                                        MovieCard(
                                            movie = movie,
                                            showProgress = isContinueWatching,
                                            onMovie = onMovie,
                                            onMovieOptions = { optionsMovie = it },
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
            MovieSearchOverlay(
                query = state.searchQuery,
                results = if (state.searchQuery.isBlank()) emptyList() else state.movies,
                onSearch = onSearch,
                onSelect = { movie ->
                    searchVisible = false
                    onSearch("")
                    onMovie(movie)
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
 * Compact Search icon button for the Movies header (Phase 14.2I.3).
 * Renders a magnifying glass icon matching the compact 44x44dp [MoviesMoreButton].
 */
@Composable
private fun MoviesSearchIconButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier
            .size(44.dp)
            .semantics { onClick(label = "Search Movies") { onClick(); true } },
        accent = accent,
        minWidth = 44.dp,
        minHeight = 44.dp,
        contentDescription = "Search Movies",
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
 * Compact three-dot More button for the Movies header.
 * Renders a vertical ⋮ ellipsis (Unicode 22EE) — no text, no large card.
 * Matches the compact style of the header Back button.
 */
@Composable
private fun MoviesMoreButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
// Category rail — no left category search field
// --------------------------------------------------------------------------

/**
 * Movies category rail.
 *
 * The left-panel "Search categories" field has been intentionally removed.
 * Category filtering by name is available internally through [state.categorySearchQuery]
 * driven by [onCategorySearch] but is NOT exposed as a visible UI control here.
 * Header Search owns the primary movie-search UX.
 *
 * The rail begins with system categories (ALL MOVIES / FAVOURITES / HISTORY) which are
 * prepended by [MoviesRepository.categories] and therefore appear first in [state.categories].
 */
@Composable
private fun MovieCategoryRail(
    state: MoviesUiState,
    firstFocus: FocusRequester,
    onCategory: (MovieCategory) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalWatchioColors.current
    // No category search field — show all categories as returned by the repository.
    // Repository always prepends: ALL MOVIES, FAVOURITES, HISTORY, then provider categories.
    val visible = state.categories
    WatchioCard(modifier = modifier.testTag("movie-category-panel"), accent = colors.moviesAccent, minWidth = 0.dp, minHeight = 0.dp) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp).testTag("movie-categories")) {
            itemsIndexed(visible, key = { _, item -> item.id }) { index, category ->
                MovieCategoryRow(
                    category = category,
                    selected = category.id == state.selectedCategory?.id,
                    onClick = { onCategory(category) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .testTag("movie-category-${category.id}"),
                )
            }
        }
    }
}

/**
 * Compact category row for Movies category rail (Phase 14.2I.4).
 *
 * Sizing & Alignment:
 *  - Reduced rendered height (48dp) allowing significantly more category rows to fit vertically.
 *  - Vertically centered text via [Alignment.CenterStart] inside a full-height container.
 *  - Left horizontal text alignment with comfortable [12.dp] horizontal padding.
 *  - Preserves existing background, corner radius, and WHITE focus border from [WatchioCard].
 *  - Preserves existing theme colours: [colors.moviesAccent] when selected, [colors.focusGlow] glow.
 *
 * Text & Overflow Behaviour:
 *  - Inactive / unfocused: standard 1-line text with [TextOverflow.Ellipsis].
 *  - Focused / selected:
 *      * If text fits available width: stays static (no animation).
 *      * If text overflows available width: smoothly marquees left with [Modifier.basicMarquee].
 *  - When focus moves away: marquee stops immediately, resets to start position with ellipsis.
 */
@Composable
private fun MovieCategoryRow(
    category: MovieCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val typography = LocalWatchioTypography.current
    var isOverflowing by remember(category.name) { mutableStateOf(false) }

    WatchioCard(
        modifier = modifier.height(48.dp),
        accent = if (selected) colors.moviesAccent else colors.focusGlow,
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
                }).testTag("movie-category-text-${category.id}"),
            )
        }
    }
}

// --------------------------------------------------------------------------
// Movie search overlay
// --------------------------------------------------------------------------

@Composable
private fun MovieSearchOverlay(
    query: String,
    results: List<WatchioMovieItem>,
    onSearch: (String) -> Unit,
    onSelect: (WatchioMovieItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
            .imePadding()
            .testTag("movie-search-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        WatchioCard(
            modifier = Modifier.fillMaxWidth(0.76f).fillMaxHeight(0.84f).testTag("movie-search-panel"),
            accent = colors.moviesAccent,
            minWidth = 0.dp,
            minHeight = 0.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Search movies", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("movie-search-close")) { Text("Close") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onSearch,
                    singleLine = true,
                    label = { Text("Search movies") },
                    modifier = Modifier.fillMaxWidth().testTag("movie-search-field"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSearch("") }, modifier = Modifier.testTag("movie-search-clear")) { Text("Clear") }
                    Text(
                        text = if (query.isBlank()) "Type to search movies" else "${results.size} results",
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
                    modifier = Modifier.fillMaxSize().testTag("movie-search-results"),
                ) {
                    items(
                        items = results.take(120),
                        key = { "${it.providerId.value}:${it.id}" },
                        contentType = { "movie_card" },
                    ) { movie ->
                        Box(Modifier.testTag("movie-search-result")) {
                            MovieCard(movie = movie, onMovie = onSelect, onMovieOptions = { onSelect(it) })
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Movie options dialog
// --------------------------------------------------------------------------

@Composable
private fun MovieOptionsDialog(movie: WatchioMovieItem, onDetails: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Movie Options") },
        text = { Text(movie.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = onDetails) { Text("View Details") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// --------------------------------------------------------------------------
// Movie details screen
// --------------------------------------------------------------------------

@Composable
fun MovieDetailsScreen(
    state: MovieDetailsUiState,
    onPlay: (Boolean) -> Unit,
    onTrailer: (String) -> Unit,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    BackHandler(onBack = onBack)
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.moviesAccent)
        }
        return
    }
    val details = state.details ?: return
    val firstFocus = remember { FocusRequester() }
    var resumeDialogRequest by remember { mutableStateOf<ResumePlaybackRequest?>(null) }
    val resumable = remember(details.movie.resumePositionMs, details.movie.resumeDurationMs) {
        com.watchioiptv.nativeapp.data.movies.MoviesRepository.shouldResumePosition(
            details.movie.resumePositionMs,
            details.movie.resumeDurationMs,
        )
    }

    LaunchedEffect(details.movie.id) { firstFocus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("movie-details")) {
        details.backdropUrl?.let {
            AsyncImage(
                model = it,
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
                title = "MOVIES",
                onBack = onBack,
                testTagPrefix = "movie-details",
            )
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compactLandscape = maxWidth < 980.dp
                val posterWidth = if (compactLandscape) 140.dp else 165.dp
                val gap = if (compactLandscape) 20.dp else 28.dp

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    MovieDetailsPoster(
                        url = details.posterUrl,
                        modifier = Modifier
                            .width(posterWidth)
                            .aspectRatio(2f / 3f)
                            .testTag("movie-poster"),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 6.dp else 10.dp),
                    ) {
                        Text(
                            text = details.title,
                            color = colors.textPrimary,
                            style = type.screenTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("movie-title"),
                        )
                        val metaSummary = formatMovieMetaLine(details)
                        if (metaSummary.isNotBlank()) {
                            Text(
                                text = metaSummary,
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("movie-details-meta"),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MovieDetailsActionButton(
                                title = "Play",
                                accent = colors.moviesAccent,
                                isPrimary = true,
                                onClick = {
                                    if (resumable) {
                                        resumeDialogRequest = ResumePlaybackRequest(
                                            title = details.title,
                                            subtitle = "Movie",
                                            resumePositionMs = details.movie.resumePositionMs ?: 0L,
                                            durationMs = details.movie.resumeDurationMs,
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
                                    .testTag("movie-play-button"),
                            )
                            details.trailerKey?.takeIf { it.isNotBlank() }?.let { key ->
                                MovieDetailsActionButton(
                                    title = "Trailer",
                                    accent = colors.liveTvAccent,
                                    onClick = { onTrailer(key) },
                                    modifier = Modifier.testTag("movie-trailer-button"),
                                )
                            }
                            MovieDetailsActionButton(
                                title = if (details.movie.isFavorite) "Favourited" else "Favourite",
                                accent = colors.focusGlow,
                                icon = {
                                    HeartIcon(
                                        filled = details.movie.isFavorite,
                                        color = if (details.movie.isFavorite) colors.moviesAccent else Color.White,
                                    )
                                },
                                onClick = onFavorite,
                                modifier = Modifier.testTag("movie-favorite-button"),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        MovieDetailRow("Director", details.director)
                        MovieDetailRow("Release Date", details.releaseDate)
                        MovieDetailRow("Genre", details.genre)
                        MovieDetailRow("Cast", details.cast)
                        if (!details.plot.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Plot",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.testTag("movie-plot-header"),
                            )
                            Text(
                                text = details.plot,
                                color = colors.textSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .testTag("movie-plot-text"),
                            )
                        }
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
// Movie player screen
// --------------------------------------------------------------------------

@Composable
fun MoviePlayerScreen(
    playerState: WatchioPlayerState,
    playerSettings: PlayerSettings,
    playerManager: WatchioPlayerManager,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
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
        Modifier.fillMaxSize().background(Color.Black).testTag("movie-player").focusRequester(surfaceFocus).focusable().clickable { controlsVisible = !controlsVisible }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(-10_000L); true }
                    Key.DirectionRight -> { onSeek(10_000L); true }
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> { controlsVisible = true; true }
                    Key.Escape, Key.Back -> { onClose(); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> FrameLayout(context).also { view ->
                view.keepScreenOn = shouldKeepScreenOn(playerState)
                playerManager.attachSurface(view)
            } },
            update = { view ->
                view.keepScreenOn = shouldKeepScreenOn(playerState)
                playerManager.attachSurface(view)
            },
            onRelease = { view ->
                view.keepScreenOn = false
                playerManager.detachSurface(view)
            },
        )
        val snapshot = playerManager.snapshot()
        if (controlsVisible && playerSettings.showPlayerControls) {
            Column(Modifier.align(Alignment.BottomCenter).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (snapshot.durationMs != null && snapshot.durationMs > 0L) {
                    LinearProgressIndicator(
                        progress = { (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) },
                        color = LocalWatchioColors.current.moviesAccent,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WatchioFocusableCard("<< 10s", accent = LocalWatchioColors.current.focusGlow, onClick = { onSeek(-10_000L) })
                    WatchioFocusableCard(if (playerState is WatchioPlayerState.Playing) "Pause" else "Play", accent = LocalWatchioColors.current.moviesAccent, onClick = onPlayPause, modifier = Modifier.focusRequester(firstFocus))
                    WatchioFocusableCard("10s >>", accent = LocalWatchioColors.current.focusGlow, onClick = { onSeek(10_000L) })
                    WatchioFocusableCard("Back", accent = LocalWatchioColors.current.seriesAccent, onClick = onClose)
                }
            }
        } else if (controlsVisible) {
            WatchioFocusableCard(
                if (playerState is WatchioPlayerState.Playing) "Pause" else "Play",
                accent = LocalWatchioColors.current.moviesAccent,
                onClick = onPlayPause,
                modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp).focusRequester(firstFocus),
            )
        }
        when (playerState) {
            is WatchioPlayerState.Connecting -> Text("Connecting...", color = Color.White, modifier = Modifier.align(Alignment.Center))
            is WatchioPlayerState.Buffering -> Text("Buffering...", color = Color.White, modifier = Modifier.align(Alignment.Center))
            is WatchioPlayerState.Failed -> Text(playerState.message, color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> Unit
        }
    }
}

// --------------------------------------------------------------------------
// Movie card
// --------------------------------------------------------------------------

/**
 * Poster card for the movie grid and search overlay (Phase 14.2I.3).
 *
 * Layout:
 *  - 2:3 aspect-ratio poster (dominant) with compact rating badge overlay at top-right
 *  - Fixed-height title region (52dp — reserves space for up to 2 lines at normal text sizes)
 *
 * Rating is formatted as "★ 6.5" in a dark translucent badge on the poster.
 * Zero, null, blank, or unparseable values are hidden (no badge).
 * There is no separate rating line below the title.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieCard(
    movie: WatchioMovieItem,
    showProgress: Boolean = false,
    onMovie: (WatchioMovieItem) -> Unit,
    onMovieOptions: (WatchioMovieItem) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val formattedRating = movie.formattedRating ?: formatRating(movie.rating)
    Column(
        Modifier
            .border(if (focused) 3.dp else 1.dp, if (focused) colors.focusBorder else Color.Transparent)
            .background(if (focused) colors.moviesAccent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onMovie(movie) },
                onLongClick = { onMovieOptions(movie) },
                onLongClickLabel = "Movie Options",
            )
            .focusable(interactionSource = interactionSource)
            .testTag("movie-card")
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Poster(movie.posterUrl, Modifier.fillMaxSize().testTag("movie-poster"))
            if (formattedRating != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .testTag("movie-rating-badge"),
                ) {
                    Text(
                        text = formattedRating,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.testTag("movie-rating"),
                    )
                }
            }
            if (showProgress) {
                val progress = remember(movie.resumePositionMs, movie.resumeDurationMs) {
                    val pos = movie.resumePositionMs
                    val dur = movie.resumeDurationMs
                    if (pos != null && dur != null && dur > 0L) {
                        (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    } else null
                }
                if (progress != null) {
                    WatchioProgressBar(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .testTag("movie-progress-bar"),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Fixed-height title region — all cards reserve identical vertical space.
        Box(Modifier.fillMaxWidth().height(52.dp).testTag("movie-title-region")) {
            Text(
                text = movie.name,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --------------------------------------------------------------------------
// Poster & Movie Details Components
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

@Composable
private fun MovieDetailsPoster(url: String?, modifier: Modifier = Modifier) {
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
fun HeartIcon(
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier.size(16.dp),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.82f)
            cubicTo(w * 0.18f, h * 0.54f, 0f, h * 0.34f, 0f, h * 0.20f)
            cubicTo(0f, h * 0.06f, w * 0.18f, 0f, w * 0.34f, 0f)
            cubicTo(w * 0.44f, 0f, w * 0.5f, h * 0.08f, w * 0.5f, h * 0.15f)
            cubicTo(w * 0.5f, h * 0.08f, w * 0.56f, 0f, w * 0.66f, 0f)
            cubicTo(w * 0.82f, 0f, w * 1.0f, h * 0.06f, w * 1.0f, h * 0.20f)
            cubicTo(w * 1.0f, h * 0.34f, w * 0.82f, h * 0.54f, w * 0.5f, h * 0.82f)
            close()
        }
        if (filled) {
            drawPath(path, color, style = Fill)
        } else {
            drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun MovieDetailsActionButton(
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

// --------------------------------------------------------------------------
// Detail helpers
// --------------------------------------------------------------------------

@Composable
private fun MovieDetailRow(label: String, value: String?) {
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

@Suppress("unused")
@Composable
private fun detailRow(label: String, value: String?) {
    value?.takeIf { it.isNotBlank() } ?: return
    Text("$label: $value", color = LocalWatchioColors.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Suppress("unused")
private fun metaLine(details: MovieDetails): String = listOfNotNull(details.releaseDate, details.runtime, details.genre, details.rating)
    .filter { it.isNotBlank() }
    .joinToString("  |  ")

// --------------------------------------------------------------------------
// Metadata & Rating formatting
// --------------------------------------------------------------------------

/**
 * Extracts a 4-digit release year from various date string formats.
 *
 * Supports: "2016-01-01", "2016/01/01", "2016", "Jan 2016".
 * Returns null if no valid 4-digit year is present.
 */
internal fun extractReleaseYear(dateStr: String?): String? {
    if (dateStr.isNullOrBlank()) return null
    val trimmed = dateStr.trim()
    val match = Regex("""\b(19\d{2}|20\d{2})\b""").find(trimmed)
    return match?.value
}

/**
 * Parses and formats movie runtime into a clean, human-readable display string.
 *
 * Examples:
 *  - "01:51:15" or "1:51:15" -> "1h 51m"
 *  - "00:45:00" -> "45m"
 *  - "111" or "111 min" or "111 mins" -> "1h 51m"
 *  - "45" -> "45m"
 *  - "1h 51m" -> "1h 51m"
 *  - null / blank / invalid -> null
 */
internal fun formatRuntime(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()

    // Case 1: HH:MM:SS or H:MM:SS
    val hmsMatch = Regex("""^(\d{1,2}):(\d{2}):(\d{2})$""").matchEntire(trimmed)
    if (hmsMatch != null) {
        val hours = hmsMatch.groupValues[1].toIntOrNull() ?: 0
        val mins = hmsMatch.groupValues[2].toIntOrNull() ?: 0
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            mins > 0 -> "${mins}m"
            else -> null
        }
    }

    // Case 2: MM:SS or M:SS
    val msMatch = Regex("""^(\d{1,3}):(\d{2})$""").matchEntire(trimmed)
    if (msMatch != null) {
        val totalMins = msMatch.groupValues[1].toIntOrNull() ?: 0
        val hours = totalMins / 60
        val mins = totalMins % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            mins > 0 -> "${mins}m"
            else -> null
        }
    }

    // Case 3: "111 min" or "111 mins" or "111m"
    val minWordMatch = Regex("""^(\d+)\s*(?:min|mins|minute|minutes|m)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
    if (minWordMatch != null) {
        val totalMins = minWordMatch.groupValues[1].toIntOrNull() ?: 0
        if (totalMins <= 0) return null
        val hours = totalMins / 60
        val mins = totalMins % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    // Case 4: Already formatted like "1h 51m" or "2h" or "45m"
    val formattedMatch = Regex("""^(\d+h(?:\s*\d+m)?|\d+m)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
    if (formattedMatch != null) {
        return trimmed
    }

    // Case 5: Raw numeric value (either minutes or seconds)
    val num = trimmed.toIntOrNull()
    if (num != null && num > 0) {
        // If > 300, treat as seconds (e.g. 6660s = 111m = 1h 51m)
        val totalMins = if (num > 300) num / 60 else num
        if (totalMins <= 0) return null
        val hours = totalMins / 60
        val mins = totalMins % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    return null
}

/**
 * Builds a clean, bullet-separated metadata summary line from valid movie fields.
 *
 * Example: "2016 • 1h 51m • Crime, Thriller • ★ 5.0"
 */
internal fun formatMovieMetaLine(details: MovieDetails): String {
    val year = extractReleaseYear(details.releaseDate)
    val runtime = formatRuntime(details.runtime)
    val genre = details.genre?.takeIf { it.isNotBlank() }
    val rating = formatRating(details.rating)

    return listOfNotNull(year, runtime, genre, rating)
        .joinToString(" • ")
}

/**
 * Formats a raw rating string from the provider into a display string.
 *
 * Rules:
 *  - null / blank → null (hidden)
 *  - parse failure → null (hidden)
 *  - value ≤ 0 or NaN → null (hidden)
 *  - value > 0 → "★ X.X" (one decimal place)
 *
 * Examples: "6.458" → "★ 6.5", "7" → "★ 7.0", "0" → null, "" → null.
 */
internal fun formatRating(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val value = raw.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return null
    return "★ ${"%.1f".format(value)}"
}

// --------------------------------------------------------------------------
// YouTube trailer
// --------------------------------------------------------------------------

fun openYoutubeTrailer(context: android.content.Context, key: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Suppress("unused")
private fun resumeTitle(movie: WatchioMovieItem): String =
    if ((movie.resumePositionMs ?: 0L) > 60_000L) "Resume" else "Play"

