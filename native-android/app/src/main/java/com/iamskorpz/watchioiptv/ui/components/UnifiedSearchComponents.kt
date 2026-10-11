package com.iamskorpz.watchioiptv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iamskorpz.watchioiptv.data.library.WatchioSearchResult
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioRadii

enum class UnifiedSearchLayout { Posters, Channels }

@Composable
fun UnifiedSearchOverlay(
    title: String,
    query: String,
    results: List<WatchioSearchResult>,
    loading: Boolean,
    layout: UnifiedSearchLayout,
    testTagPrefix: String,
    onQuery: (String) -> Unit,
    onResult: (WatchioSearchResult) -> Unit,
    onDismiss: () -> Unit,
    metadata: (WatchioSearchResult) -> List<String> = { result -> listOfNotNull(result.year, result.rating, result.subtitle) },
    onArtworkLoaded: (() -> Unit)? = null,
) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.54f)).imePadding().testTag("$testTagPrefix-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.76f).fillMaxHeight(0.98f).testTag("$testTagPrefix-panel"),
            color = colors.dialogSurface,
            shape = RoundedCornerShape(LocalWatchioRadii.current.lg),
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss, modifier = Modifier.height(36.dp).testTag("$testTagPrefix-close")) { Text("Close") }
                }
                WatchioSearchTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = title,
                    testTag = "$testTagPrefix-field",
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
                Row(
                    Modifier.height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onQuery("") }, modifier = Modifier.height(36.dp).testTag("$testTagPrefix-clear")) { Text("Clear") }
                    if (loading && results.isEmpty()) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.focusBorder)
                    Text(
                        when {
                            query.isBlank() -> "Type to search"
                            loading && results.isEmpty() -> "Searching..."
                            loading -> "${results.size} strong results - refining..."
                            results.isEmpty() -> "No results"
                            else -> "${results.size} results"
                        },
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (layout == UnifiedSearchLayout.Posters) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier.fillMaxSize().testTag("$testTagPrefix-results"),
                    ) {
                        items(results, key = { "${it.providerId.value}:${it.contentType}:${it.contentId}" }) { result ->
                            UnifiedPosterSearchCard(result, testTagPrefix, onResult, onArtworkLoaded)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier.fillMaxSize().testTag("$testTagPrefix-results"),
                    ) {
                        items(results, key = { "${it.providerId.value}:${it.contentId}" }) { result ->
                            UnifiedChannelSearchCard(result, metadata(result), testTagPrefix, onResult, onArtworkLoaded)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedPosterSearchCard(
    result: WatchioSearchResult,
    testTagPrefix: String,
    onResult: (WatchioSearchResult) -> Unit,
    onArtworkLoaded: (() -> Unit)? = null,
) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-result"),
        minWidth = 0.dp,
        minHeight = 0.dp,
        onClick = { onResult(result) },
        contentDescription = result.title,
    ) { _ ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(colors.cardSurface)) {
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { onArtworkLoaded?.invoke() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(result.title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun UnifiedChannelSearchCard(
    result: WatchioSearchResult,
    metadata: List<String>,
    testTagPrefix: String,
    onResult: (WatchioSearchResult) -> Unit,
    onArtworkLoaded: (() -> Unit)? = null,
) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-result"),
        minWidth = 0.dp,
        minHeight = 72.dp,
        onClick = { onResult(result) },
        contentDescription = result.title,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = result.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { onArtworkLoaded?.invoke() },
                modifier = Modifier.size(52.dp),
            )
            Column(Modifier.fillMaxWidth()) {
                Text(result.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                metadata.take(2).forEach { line ->
                    Text(line, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
