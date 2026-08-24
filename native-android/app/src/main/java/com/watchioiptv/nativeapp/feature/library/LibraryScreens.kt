package com.watchioiptv.nativeapp.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watchioiptv.nativeapp.data.library.ContinueWatchingItem
import com.watchioiptv.nativeapp.data.library.LibraryFavoriteItem
import com.watchioiptv.nativeapp.data.library.LibraryHistoryItem
import com.watchioiptv.nativeapp.data.library.SearchScope
import com.watchioiptv.nativeapp.data.library.WatchioSearchResult
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.ui.components.WatchioFocusableCard
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography
import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.watchioiptv.nativeapp.ui.components.WatchioCard

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

@Composable
fun GlobalSearchScreen(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onScope: (SearchScope) -> Unit,
    onResult: (WatchioSearchResult) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val firstFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
            .imePadding()
            .testTag("global-search-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("global-search-panel"),
        ) {
            val panelWidth = maxWidth
            val columnsLive = when {
                panelWidth < 520.dp -> 1
                panelWidth < 840.dp -> 2
                else -> 3
            }
            val columnsMedia = when {
                panelWidth < 460.dp -> 2
                panelWidth < 740.dp -> 4
                panelWidth < 1080.dp -> 5
                else -> 7
            }
            WatchioCard(
                modifier = Modifier.fillMaxSize(),
                accent = colors.seriesAccent,
                minWidth = 0.dp,
                minHeight = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("global-search"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 1. Compact Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth().height(22.dp).testTag("global-search-header-row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Search Watchio",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.testTag("global-search-title"),
                        )
                        Text(
                            text = "Close",
                            color = colors.liveTvAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable(onClick = onBack)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("global-search-close"),
                        )
                    }

                    // 2. Compact Search Field
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = colors.textPrimary,
                        ),
                        cursorBrush = SolidColor(colors.seriesAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .focusRequester(firstFocus)
                            .testTag("global-search-field"),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colors.surfaceElevated, RoundedCornerShape(6.dp))
                                    .border(1.dp, colors.focusGlow, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.query.isEmpty()) {
                                    Text(
                                        text = "Search Live TV, Movies, and Series...",
                                        color = colors.textMuted,
                                        fontSize = 12.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    // 3. Compact Single Horizontal Filter Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("global-search-filter-row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WatchioFocusableCard(
                            title = "Clear",
                            accent = colors.focusGlow,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            minWidth = 0.dp,
                            minHeight = 24.dp,
                            textStyle = LocalWatchioTypography.current.label.copy(fontSize = 10.sp, color = colors.textSecondary),
                            onClick = { onQuery("") },
                            modifier = Modifier.testTag("global-search-clear"),
                        )
                        SearchScope.entries.forEach { scope ->
                            val label = when (scope) {
                                SearchScope.Global -> "ALL"
                                SearchScope.Live -> "LIVE TV"
                                SearchScope.Movies -> "MOVIES"
                                SearchScope.Series -> "SERIES"
                            }
                            WatchioFocusableCard(
                                title = label,
                                accent = if (state.scope == scope) colors.seriesAccent else colors.focusGlow,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                minWidth = 0.dp,
                                minHeight = 24.dp,
                                textStyle = LocalWatchioTypography.current.label.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (state.scope == scope) FontWeight.Bold else FontWeight.Normal,
                                ),
                                onClick = { onScope(scope) },
                                modifier = Modifier.testTag("global-search-scope-${scope.name.lowercase()}"),
                            )
                        }
                    }

                    // 4. Expanded Results Area (~75-80% height)
                    if (state.loading) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.seriesAccent)
                        }
                    } else if (state.query.isBlank()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Type to search Live TV, Movies, and Series.", color = colors.textMuted, fontSize = 13.sp)
                        }
                    } else if (state.results.isEmpty) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No results", color = colors.textMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("global-search-results"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            when (state.scope) {
                                SearchScope.Global -> {
                                    if (state.results.live.isNotEmpty()) {
                                        liveSection(
                                            title = "LIVE TV",
                                            groupTag = "global-search-group-live",
                                            results = state.results.live,
                                            columns = columnsLive,
                                            onResult = onResult,
                                        )
                                    }
                                    if (state.results.movies.isNotEmpty()) {
                                        mediaSection(
                                            title = "MOVIES",
                                            groupTag = "global-search-group-movies",
                                            resultTypeTag = "global-search-result-movie",
                                            results = state.results.movies,
                                            columns = columnsMedia,
                                            onResult = onResult,
                                        )
                                    }
                                    if (state.results.series.isNotEmpty()) {
                                        mediaSection(
                                            title = "SERIES",
                                            groupTag = "global-search-group-series",
                                            resultTypeTag = "global-search-result-series",
                                            results = state.results.series,
                                            columns = columnsMedia,
                                            onResult = onResult,
                                        )
                                    }
                                }
                                SearchScope.Live -> {
                                    if (state.results.live.isNotEmpty()) {
                                        liveSection(
                                            title = "LIVE TV",
                                            groupTag = "global-search-group-live",
                                            results = state.results.live,
                                            columns = columnsLive,
                                            onResult = onResult,
                                        )
                                    }
                                }
                                SearchScope.Movies -> {
                                    if (state.results.movies.isNotEmpty()) {
                                        mediaSection(
                                            title = "MOVIES",
                                            groupTag = "global-search-group-movies",
                                            resultTypeTag = "global-search-result-movie",
                                            results = state.results.movies,
                                            columns = columnsMedia,
                                            onResult = onResult,
                                        )
                                    }
                                }
                                SearchScope.Series -> {
                                    if (state.results.series.isNotEmpty()) {
                                        mediaSection(
                                            title = "SERIES",
                                            groupTag = "global-search-group-series",
                                            resultTypeTag = "global-search-result-series",
                                            results = state.results.series,
                                            columns = columnsMedia,
                                            onResult = onResult,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultArtwork(
    contentType: ContentType,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    when (contentType) {
        ContentType.Live -> {
            Box(
                modifier = modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceElevated)
                    .testTag("search-artwork-logo"),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl.isNullOrBlank()) {
                    Text("TV", color = colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.testTag("search-artwork-fallback"))
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                    )
                }
            }
        }
        ContentType.Movie, ContentType.Series, ContentType.Episode -> {
            Box(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceElevated)
                    .testTag("search-artwork-poster"),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl.isNullOrBlank()) {
                    Text("No Image", color = colors.textSecondary, fontSize = 9.sp, modifier = Modifier.testTag("search-artwork-fallback"))
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun formatRating(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val value = raw.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return null
    return "★ ${"%.1f".format(value)}"
}

@Composable
private fun SearchResultLiveCard(
    item: WatchioSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = modifier.height(44.dp).testTag("global-search-result-live"),
        accent = colors.focusGlow,
        minWidth = 0.dp,
        minHeight = 44.dp,
        contentDescription = item.title,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchResultArtwork(contentType = item.contentType, imageUrl = item.imageUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultMediaCard(
    item: WatchioSearchResult,
    resultTypeTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val formattedRating = formatRating(item.rating)
    WatchioCard(
        modifier = modifier.testTag(resultTypeTag),
        accent = colors.focusGlow,
        minWidth = 0.dp,
        minHeight = 0.dp,
        contentDescription = "${item.title}${item.year?.let { ", $it" } ?: ""}",
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            ) {
                SearchResultArtwork(
                    contentType = item.contentType,
                    imageUrl = item.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(30.dp),
            )
            if (!item.year.isNullOrBlank() || formattedRating != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.year?.takeIf { it.isNotBlank() }?.let { year ->
                        Text(
                            text = year,
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                    formattedRating?.let { rating ->
                        Text(
                            text = rating,
                            color = colors.liveTvAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            } else if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text = item.subtitle,
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.liveSection(
    title: String,
    groupTag: String,
    results: List<WatchioSearchResult>,
    columns: Int,
    onResult: (WatchioSearchResult) -> Unit,
) {
    item {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp).testTag(groupTag),
        )
    }
    val chunked = results.chunked(columns)
    items(chunked, key = { row -> row.joinToString("-") { "${it.contentType.persisted}_${it.contentId}" } }) { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in rowItems) {
                SearchResultLiveCard(
                    item = item,
                    onClick = { onResult(item) },
                    modifier = Modifier.weight(1f),
                )
            }
            val remaining = columns - rowItems.size
            for (i in 0 until remaining) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mediaSection(
    title: String,
    groupTag: String,
    resultTypeTag: String,
    results: List<WatchioSearchResult>,
    columns: Int,
    onResult: (WatchioSearchResult) -> Unit,
) {
    item {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp).testTag(groupTag),
        )
    }
    val chunked = results.chunked(columns)
    items(chunked, key = { row -> row.joinToString("-") { "${it.contentType.persisted}_${it.contentId}" } }) { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in rowItems) {
                SearchResultMediaCard(
                    item = item,
                    resultTypeTag = resultTypeTag,
                    onClick = { onResult(item) },
                    modifier = Modifier.weight(1f),
                )
            }
            val remaining = columns - rowItems.size
            for (i in 0 until remaining) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MyListScreen(
    state: MyListUiState,
    onContinue: (ContinueWatchingItem) -> Unit,
    onFavorite: (LibraryFavoriteItem) -> Unit,
    onRemoveFavorite: (LibraryFavoriteItem) -> Unit,
    onHistory: (LibraryHistoryItem) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = onBack)
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(colors.surfaceBase), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.seriesAccent)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(colors.surfaceBase).padding(24.dp).testTag("my-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("MY LIST", color = colors.textPrimary, fontWeight = FontWeight.Bold) }
        continueSection(state.data.continueWatching, onContinue)
        favoriteSection("FAVOURITE LIVE TV", state.data.liveFavorites, onFavorite, onRemoveFavorite)
        favoriteSection("FAVOURITE MOVIES", state.data.movieFavorites, onFavorite, onRemoveFavorite)
        favoriteSection("FAVOURITE SERIES", state.data.seriesFavorites, onFavorite, onRemoveFavorite)
        historySection(state.data.history, onHistory)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.continueSection(items: List<ContinueWatchingItem>, onClick: (ContinueWatchingItem) -> Unit) {
    item { Text("CONTINUE WATCHING", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
    if (items.isEmpty()) item { Text("Nothing to continue watching", color = androidx.compose.ui.graphics.Color.Gray) }
    items(items, key = { "${it.contentType.persisted}-${it.contentId}-${it.subContentId}" }) {
        ResultRow(it.title, it.subtitle ?: progressText(it.positionMs, it.durationMs), onClick = { onClick(it) })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.favoriteSection(
    title: String,
    items: List<LibraryFavoriteItem>,
    onClick: (LibraryFavoriteItem) -> Unit,
    onRemove: (LibraryFavoriteItem) -> Unit,
) {
    item { Text(title, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
    if (items.isEmpty()) item { Text("No favourites yet", color = androidx.compose.ui.graphics.Color.Gray) }
    items(items, key = { "${it.contentType.persisted}-${it.contentId}" }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { ResultRow(it.title, it.contentType.persisted, onClick = { onClick(it) }) }
            WatchioFocusableCard("Remove", accent = LocalWatchioColors.current.liveTvAccent, onClick = { onRemove(it) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historySection(items: List<LibraryHistoryItem>, onClick: (LibraryHistoryItem) -> Unit) {
    item { Text("HISTORY", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
    if (items.isEmpty()) item { Text("No watch history yet", color = androidx.compose.ui.graphics.Color.Gray) }
    items(items, key = { "${it.contentType.persisted}-${it.contentId}-${it.subContentId}-${it.lastWatchedAtEpochMs}" }) {
        ResultRow(it.title, it.subtitle ?: it.contentType.persisted, onClick = { onClick(it) })
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalWatchioColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (focused) 0.12f else 0.05f))
            .border(if (focused) 3.dp else 1.dp, if (focused) colors.focusBorder else colors.surfaceElevated)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(14.dp),
    ) {
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun contentRoute(type: ContentType, id: String): String = when (type) {
    ContentType.Live -> if (id.isNotBlank()) "live/$id" else "live"
    ContentType.Movie -> "movies/$id"
    ContentType.Series -> "series/$id"
    ContentType.Episode -> "series/$id"
}

private fun progressText(positionMs: Long?, durationMs: Long?): String {
    val position = (positionMs ?: 0L) / 60_000L
    val duration = durationMs?.let { " / ${it / 60_000L}m" }.orEmpty()
    return "${position}m$duration"
}
