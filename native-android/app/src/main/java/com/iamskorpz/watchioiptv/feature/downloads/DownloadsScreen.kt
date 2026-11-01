package com.iamskorpz.watchioiptv.feature.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadState
import com.iamskorpz.watchioiptv.ui.components.WatchioButton
import com.iamskorpz.watchioiptv.ui.components.WatchioButtonVariant
import com.iamskorpz.watchioiptv.ui.components.WatchioCard
import com.iamskorpz.watchioiptv.ui.components.WatchioEmptyState
import com.iamskorpz.watchioiptv.ui.components.WatchioLoading
import com.iamskorpz.watchioiptv.ui.components.WatchioPageHeader
import com.iamskorpz.watchioiptv.ui.components.WatchioSurfaceRole
import com.iamskorpz.watchioiptv.ui.icons.WatchioIcon
import com.iamskorpz.watchioiptv.ui.icons.WatchioIconColors
import com.iamskorpz.watchioiptv.ui.icons.WatchioIconKind
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioPosterTokens
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioSpacing
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioTypography
import com.iamskorpz.watchioiptv.ui.theme.watchioScreenBackgroundColor
import java.io.File
import java.util.Locale

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onBack: () -> Unit,
    onCategory: (DownloadsCategory) -> Unit,
    onMovie: (String) -> Unit,
    onShow: (String) -> Unit,
    onSeason: (String, Int) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (DownloadItemUi) -> Unit,
    onPlay: (String) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onPauseSeason: (SeasonDownloadUi) -> Unit = {},
    onResumeSeason: (SeasonDownloadUi) -> Unit = {},
    onCancelSeason: (SeasonDownloadUi) -> Unit = {},
    onDeleteSeason: (SeasonDownloadUi) -> Unit = {},
    onDismissSeasonDelete: () -> Unit = {},
    onConfirmSeasonDelete: () -> Unit = {},
    onDeleteShow: (DownloadedShowUi) -> Unit = {},
    onDismissShowDelete: () -> Unit = {},
    onConfirmShowDelete: () -> Unit = {},
    onDismissDeletionError: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    when (val destination = state.destination) {
        DownloadsDestination.Library -> DownloadsLibrary(
            state = state,
            onBack = onBack,
            onCategory = onCategory,
            onMovie = onMovie,
            onShow = onShow,
        )
        is DownloadsDestination.Movie -> state.movies.firstOrNull { it.downloadId == destination.downloadId }?.let {
            DownloadedMovieDetails(it, onBack, onPause, onResume, onRetry, onCancel, onDelete, onPlay)
        } ?: WatchioEmptyState("This download is no longer available.")
        is DownloadsDestination.Show -> state.shows.firstOrNull { it.showKey == destination.showKey }?.let {
            DownloadedShowDetails(it, state.focusRestoreKey, onBack, onSeason, onDeleteShow)
        } ?: WatchioEmptyState("This TV show is no longer available.")
        is DownloadsDestination.Season -> {
            val show = state.shows.firstOrNull { it.showKey == destination.showKey }
            val season = show?.seasons?.firstOrNull { it.seasonNumber == destination.seasonNumber }
            if (show != null && season != null) {
                DownloadedSeasonDetails(show, season, state.focusRestoreKey, onBack, onPause, onResume, onRetry, onCancel, onDelete, onPlay, onDeleteSeason)
            } else WatchioEmptyState("This season is no longer available.")
        }
    }
    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete download?") },
            text = { Text("Remove ${item.episodeTitle ?: item.title} and its offline media from this device?") },
            confirmButton = { TextButton(onClick = onConfirmDelete, modifier = Modifier.testTag("downloads-delete-confirm")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("Keep") } },
        )
    }
    state.pendingSeasonDelete?.let { season ->
        AlertDialog(
            onDismissRequest = onDismissSeasonDelete,
            title = { Text("Delete downloaded season?") },
            text = { Text("Remove ${season.showTitle} Season ${season.seasonNumber} and all its downloaded episodes?") },
            confirmButton = { TextButton(onClick = onConfirmSeasonDelete, modifier = Modifier.testTag("downloads-season-delete-confirm")) { Text("Delete season") } },
            dismissButton = { TextButton(onClick = onDismissSeasonDelete) { Text("Keep") } },
        )
    }
    state.pendingShowDelete?.let { show ->
        AlertDialog(
            onDismissRequest = onDismissShowDelete,
            title = { Text("Delete downloaded TV show?") },
            text = { Text("Remove ${show.title} and all its downloaded episodes from this device?") },
            confirmButton = { TextButton(onClick = onConfirmShowDelete, modifier = Modifier.testTag("downloads-show-delete-confirm")) { Text("Delete TV show") } },
            dismissButton = { TextButton(onClick = onDismissShowDelete) { Text("Keep") } },
        )
    }
    state.deletionErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissDeletionError,
            title = { Text("Download not deleted") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissDeletionError, modifier = Modifier.testTag("downloads-delete-error-dismiss")) { Text("OK") } },
        )
    }
}

@Composable
private fun DownloadsLibrary(
    state: DownloadsUiState,
    onBack: () -> Unit,
    onCategory: (DownloadsCategory) -> Unit,
    onMovie: (String) -> Unit,
    onShow: (String) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val posterTokens = LocalWatchioPosterTokens.current
    val firstFocus = remember { FocusRequester() }
    val focusKey = state.focusRestoreKey ?: if (state.selectedCategory == DownloadsCategory.Movies) state.movies.firstOrNull()?.downloadId else state.shows.firstOrNull()?.showKey
    LaunchedEffect(state.selectedCategory, focusKey) { if (focusKey != null) runCatching { firstFocus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(watchioScreenBackgroundColor()).padding(horizontal = 18.dp, vertical = 14.dp).testTag("downloads-screen"),
    ) {
        WatchioPageHeader(title = "DOWNLOADS", onBack = onBack, testTagPrefix = "downloads")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("downloads-categories")) {
            DownloadCategoryButton("Movies", state.selectedCategory == DownloadsCategory.Movies, colors.moviesAccent) { onCategory(DownloadsCategory.Movies) }
            DownloadCategoryButton("TV Shows", state.selectedCategory == DownloadsCategory.TvShows, colors.seriesAccent) { onCategory(DownloadsCategory.TvShows) }
        }
        Spacer(Modifier.height(12.dp))
        WatchioCard(
            modifier = Modifier.fillMaxSize().testTag("downloads-grid-panel"),
            surfaceRole = WatchioSurfaceRole.Panel,
            accent = if (state.selectedCategory == DownloadsCategory.Movies) colors.moviesAccent else colors.seriesAccent,
            minWidth = 0.dp,
            minHeight = 0.dp,
        ) {
            when {
                state.loading -> WatchioLoading("Loading downloads…", Modifier.fillMaxSize())
                state.errorMessage != null -> WatchioEmptyState(state.errorMessage, Modifier.testTag("downloads-error"))
                state.selectedCategory == DownloadsCategory.Movies && state.movies.isEmpty() -> DownloadsEmptyState(
                    title = "No downloaded movies yet",
                    description = "Movies you download will appear here and remain ready to watch offline.",
                    tag = "downloads-empty-movies",
                )
                state.selectedCategory == DownloadsCategory.TvShows && state.shows.isEmpty() -> DownloadsEmptyState(
                    title = "No downloaded TV shows yet",
                    description = "Downloaded episodes and seasons will appear here for offline viewing.",
                    tag = "downloads-empty-shows",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(posterTokens.gridMinWidth),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 24.dp),
                    modifier = Modifier.fillMaxSize().testTag("downloads-grid"),
                ) {
                    if (state.selectedCategory == DownloadsCategory.Movies) {
                        items(state.movies, key = { it.downloadId }) { item ->
                            OfflinePosterCard(
                                title = item.title,
                                posterPath = item.posterPath,
                                progress = item.progressPercent,
                                completed = item.playable,
                                status = item.statusText(),
                                modifier = if (item.downloadId == focusKey) Modifier.focusRequester(firstFocus) else Modifier,
                                tag = "download-movie-${item.downloadId}",
                            ) { onMovie(item.downloadId) }
                        }
                    } else {
                        items(state.shows, key = { it.showKey }) { show ->
                            OfflinePosterCard(
                                title = show.title,
                                posterPath = show.posterPath,
                                progress = show.progressPercent,
                                completed = show.completed,
                                status = "${show.seasons.size} season${if (show.seasons.size == 1) "" else "s"}",
                                modifier = if (show.showKey == focusKey) Modifier.focusRequester(firstFocus) else Modifier,
                                tag = "download-show-${show.showKey}",
                            ) { onShow(show.showKey) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsEmptyState(title: String, description: String, tag: String) {
    val colors = LocalWatchioColors.current
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WatchioIcon(WatchioIconKind.Downloads, WatchioIconColors.ComingSoon, Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = colors.textPrimary, style = LocalWatchioTypography.current.cardTitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(description, color = colors.textSecondary, maxLines = 3)
    }
}

@Composable
private fun DownloadCategoryButton(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    WatchioCard(
        onClick = onClick,
        modifier = Modifier.testTag("downloads-category-${text.lowercase().replace(' ', '-') }"),
        surfaceRole = WatchioSurfaceRole.Control,
        accent = accent,
        selected = selected,
        minWidth = 124.dp,
        minHeight = 44.dp,
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = LocalWatchioColors.current.textPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun OfflinePosterCard(
    title: String,
    posterPath: String?,
    progress: Int,
    completed: Boolean,
    status: String,
    modifier: Modifier,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        onClick = onClick,
        modifier = modifier.testTag(tag),
        surfaceRole = WatchioSurfaceRole.Card,
        accent = WatchioIconColors.ComingSoon,
        minWidth = 0.dp,
        minHeight = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(colors.surfaceElevated)) {
                LocalArtwork(posterPath, title, Modifier.fillMaxSize())
                if (!completed) {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.72f)).padding(8.dp)) {
                        Text(status, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun DownloadedMovieDetails(
    item: DownloadItemUi,
    onBack: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (DownloadItemUi) -> Unit,
    onPlay: (String) -> Unit,
) = OfflineDetailsScaffold("MOVIES", item.backdropPath, onBack, "download-movie-details") {
    OfflineDetailsHero(
        title = item.title,
        posterPath = item.posterPath,
        synopsis = item.synopsis,
        metadata = item.metadataLine(),
        accent = LocalWatchioColors.current.moviesAccent,
        actions = {
            DownloadItemActions(item, onPause, onResume, onRetry, onCancel, onDelete, onPlay)
        },
    )
}

@Composable
private fun DownloadedShowDetails(
    show: DownloadedShowUi,
    focusRestoreKey: String?,
    onBack: () -> Unit,
    onSeason: (String, Int) -> Unit,
    onDeleteShow: (DownloadedShowUi) -> Unit,
) =
    OfflineDetailsScaffold("TV SHOWS", show.backdropPath, onBack, "download-show-details") {
        val seasonFocus = remember { FocusRequester() }
        val seasonKey = focusRestoreKey ?: show.seasons.firstOrNull()?.seasonNumber?.let { "season:$it" }
        LaunchedEffect(seasonKey) { if (seasonKey != null) runCatching { seasonFocus.requestFocus() } }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OfflineDetailsHero(
                    title = show.title,
                    posterPath = show.posterPath,
                    synopsis = show.synopsis,
                    metadata = listOfNotNull(show.releaseYear, show.genre, show.rating, show.runtime).joinToString(" · "),
                    accent = LocalWatchioColors.current.seriesAccent,
                    actions = { WatchioButton("Delete TV Show", { onDeleteShow(show) }, variant = WatchioButtonVariant.CompactAction, modifier = Modifier.testTag("download-delete-show")) },
                )
            }
            item { Text("Downloaded seasons", color = LocalWatchioColors.current.textPrimary, style = LocalWatchioTypography.current.cardTitle, fontWeight = FontWeight.Bold) }
            items(show.seasons, key = { it.seasonNumber }) { season ->
                WatchioCard(
                    onClick = { onSeason(show.showKey, season.seasonNumber) },
                    modifier = Modifier.fillMaxWidth().testTag("download-season-${season.seasonNumber}").let { if (seasonKey == "season:${season.seasonNumber}") it.focusRequester(seasonFocus) else it },
                    surfaceRole = WatchioSurfaceRole.Card,
                    accent = LocalWatchioColors.current.seriesAccent,
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Season ${season.seasonNumber}", color = LocalWatchioColors.current.textPrimary, fontWeight = FontWeight.Bold)
                        Text("${season.completed}/${season.episodes.size} episodes downloaded", color = LocalWatchioColors.current.textSecondary)
                        if (season.progress < 100) LinearProgressIndicator(progress = { season.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

@Composable
private fun DownloadedSeasonDetails(
    show: DownloadedShowUi,
    season: SeasonDownloadUi,
    focusRestoreKey: String?,
    onBack: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (DownloadItemUi) -> Unit,
    onPlay: (String) -> Unit,
    onDeleteSeason: (SeasonDownloadUi) -> Unit,
) = OfflineDetailsScaffold("${show.title.uppercase()} · SEASON ${season.seasonNumber}", show.backdropPath, onBack, "download-season-details") {
    val episodeFocus = remember { FocusRequester() }
    val episodeKey = focusRestoreKey ?: season.episodes.firstOrNull()?.downloadId
    LaunchedEffect(episodeKey) { if (episodeKey != null) runCatching { episodeFocus.requestFocus() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${season.completed}/${season.episodes.size} episodes downloaded", color = LocalWatchioColors.current.textSecondary)
            WatchioButton("Delete Season", { onDeleteSeason(season) }, variant = WatchioButtonVariant.CompactAction, modifier = Modifier.testTag("download-delete-season"))
        }
        LazyColumn(Modifier.fillMaxSize().testTag("download-episode-list"), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(season.episodes.sortedBy { it.episodeNumber }, key = { it.downloadId }) { episode ->
                DownloadedEpisodeRow(episode, if (episode.downloadId == episodeKey) Modifier.focusRequester(episodeFocus) else Modifier, onPause, onResume, onRetry, onCancel, onDelete, onPlay)
            }
        }
    }
}

@Composable
private fun DownloadedEpisodeRow(
    item: DownloadItemUi,
    modifier: Modifier,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (DownloadItemUi) -> Unit,
    onPlay: (String) -> Unit,
) {
    val colors = LocalWatchioColors.current
    WatchioCard(modifier = modifier.fillMaxWidth().testTag("download-episode-${item.downloadId}"), surfaceRole = WatchioSurfaceRole.Card, accent = colors.seriesAccent, minWidth = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(colors.surfaceElevated)) {
                LocalArtwork(item.thumbnailPath ?: item.posterPath, item.episodeTitle ?: item.title, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Episode ${item.episodeNumber}: ${item.episodeTitle ?: item.title}", color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.synopsis?.takeIf(String::isNotBlank)?.let { Text(it, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(item.statusText(), color = colors.textSecondary, fontSize = 12.sp)
                if (!item.playable) LinearProgressIndicator(progress = { item.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(0.8f)) {
                DownloadItemActions(item, onPause, onResume, onRetry, onCancel, onDelete, onPlay)
            }
        }
    }
}

@Composable
private fun OfflineDetailsScaffold(title: String, backdropPath: String?, onBack: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).testTag(tag)) {
        LocalArtwork(backdropPath, null, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.9f), 1f to Color.Black.copy(alpha = 0.45f))))
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
            WatchioPageHeader(title = title, onBack = onBack, testTagPrefix = tag, artworkReadability = true)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun OfflineDetailsHero(title: String, posterPath: String?, synopsis: String?, metadata: String, accent: Color, actions: @Composable () -> Unit) {
    val colors = LocalWatchioColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceElevated)) {
            LocalArtwork(posterPath, title, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = colors.artworkTextPrimary, style = LocalWatchioTypography.current.screenTitle, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (metadata.isNotBlank()) Text(metadata, color = colors.artworkTextSecondary, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            synopsis?.takeIf(String::isNotBlank)?.let { Text(it, color = colors.artworkTextSecondary, lineHeight = 20.sp, maxLines = 7, overflow = TextOverflow.Ellipsis) }
            if (posterPath == null) Text("Artwork unavailable", color = accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DownloadItemActions(
    item: DownloadItemUi,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (DownloadItemUi) -> Unit,
    onPlay: (String) -> Unit,
) {
    when (item.state) {
        OfflineDownloadState.Downloading, OfflineDownloadState.Queued -> {
            DownloadAction("Pause", "pause", item) { onPause(item.downloadId) }
            DownloadAction("Cancel", "cancel", item) { onCancel(item.downloadId) }
        }
        OfflineDownloadState.Paused -> {
            DownloadAction("Resume", "resume", item) { onResume(item.downloadId) }
            DownloadAction("Cancel", "cancel", item) { onCancel(item.downloadId) }
        }
        OfflineDownloadState.Failed -> {
            DownloadAction("Retry", "retry", item) { onRetry(item.downloadId) }
            DownloadAction("Delete", "delete", item) { onDelete(item) }
        }
        OfflineDownloadState.Completed -> {
            DownloadAction("Play / Resume", "play", item) { onPlay(item.downloadId) }
            DownloadAction("Delete Download", "delete", item) { onDelete(item) }
        }
        OfflineDownloadState.Removing -> CircularProgressIndicator(Modifier.size(24.dp))
    }
}

@Composable
private fun DownloadAction(label: String, action: String, item: DownloadItemUi, onClick: () -> Unit) {
    WatchioButton(label, onClick, variant = WatchioButtonVariant.CompactAction, modifier = Modifier.testTag("download-$action-${item.downloadId}"))
}

@Composable
private fun LocalArtwork(path: String?, description: String?, modifier: Modifier) {
    val file = path?.let(::File)?.takeIf(File::isFile)
    if (file != null) {
        AsyncImage(file, description, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(LocalWatchioColors.current.surfaceElevated), contentAlignment = Alignment.Center) {
            WatchioIcon(WatchioIconKind.Downloads, WatchioIconColors.ComingSoon, Modifier.size(42.dp))
        }
    }
}

private fun DownloadItemUi.metadataLine(): String = listOfNotNull(releaseYear, genre, rating, runtime).filter(String::isNotBlank).joinToString(" · ")

private fun DownloadItemUi.statusText(): String {
    val size = contentLength?.let(::formatBytes) ?: formatBytes(bytesDownloaded)
    return when (state) {
        OfflineDownloadState.Queued -> "Queued · $size"
        OfflineDownloadState.Downloading -> "$progressPercent% · ${formatBytes(bytesDownloaded)} of $size"
        OfflineDownloadState.Paused -> "Paused at $progressPercent% · $size"
        OfflineDownloadState.Completed -> "Downloaded · $size"
        OfflineDownloadState.Failed -> "Download failed · $size"
        OfflineDownloadState.Removing -> "Removing…"
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    do {
        value /= 1024.0
        unitIndex++
    } while (value >= 1024.0 && unitIndex < units.lastIndex)
    return String.format(Locale.US, if (value >= 10) "%.0f %s" else "%.1f %s", value, units[unitIndex])
}
