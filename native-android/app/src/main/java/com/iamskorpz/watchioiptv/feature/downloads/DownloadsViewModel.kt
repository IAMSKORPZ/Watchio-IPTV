package com.iamskorpz.watchioiptv.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadFailure
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadState
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class DownloadItemUi(
    val downloadId: String,
    val title: String,
    val posterPath: String?,
    val state: OfflineDownloadState,
    val bytesDownloaded: Long,
    val contentLength: Long?,
    val progressPercent: Int,
    val failureMessage: String?,
    val contentType: String = "Movie",
    val showTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val synopsis: String? = null,
    val releaseYear: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val runtime: String? = null,
    val episodeTitle: String? = null,
    val backdropPath: String? = null,
    val thumbnailPath: String? = null,
) {
    val playable: Boolean get() = state == OfflineDownloadState.Completed
}

enum class DownloadsCategory { Movies, TvShows }

sealed interface DownloadsDestination {
    data object Library : DownloadsDestination
    data class Movie(val downloadId: String) : DownloadsDestination
    data class Show(val showKey: String) : DownloadsDestination
    data class Season(val showKey: String, val seasonNumber: Int) : DownloadsDestination
}

data class DownloadedShowUi(
    val showKey: String,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val synopsis: String?,
    val releaseYear: String?,
    val genre: String?,
    val rating: String?,
    val runtime: String?,
    val seasons: List<SeasonDownloadUi>,
) {
    val progressPercent: Int get() = seasons.flatMap { it.episodes }.let { episodes ->
        if (episodes.isEmpty()) 0 else episodes.sumOf { it.progressPercent } / episodes.size
    }
    val completed: Boolean get() = seasons.flatMap { it.episodes }.all { it.playable }
}

data class DownloadsUiState(
    val loading: Boolean = true,
    val active: List<DownloadItemUi> = emptyList(),
    val completed: List<DownloadItemUi> = emptyList(),
    val errorMessage: String? = null,
    val pendingDelete: DownloadItemUi? = null,
    val seasons: List<SeasonDownloadUi> = emptyList(),
    val pendingSeasonDelete: SeasonDownloadUi? = null,
    val pendingShowDelete: DownloadedShowUi? = null,
    val deletionErrorMessage: String? = null,
    val selectedCategory: DownloadsCategory = DownloadsCategory.Movies,
    val destination: DownloadsDestination = DownloadsDestination.Library,
    val movies: List<DownloadItemUi> = emptyList(),
    val shows: List<DownloadedShowUi> = emptyList(),
    val focusRestoreKey: String? = null,
)

data class SeasonDownloadUi(
    val providerId: String,
    val showId: String,
    val showTitle: String,
    val seasonNumber: Int,
    val episodes: List<DownloadItemUi>,
) {
    val completed: Int get() = episodes.count { it.state == OfflineDownloadState.Completed }
    val downloading: Int get() = episodes.count { it.state == OfflineDownloadState.Downloading || it.state == OfflineDownloadState.Queued }
    val failed: Int get() = episodes.count { it.state == OfflineDownloadState.Failed }
    val progress: Int get() = if (episodes.isEmpty()) 0 else episodes.sumOf { it.progressPercent } / episodes.size
}

class DownloadsViewModel(
    private val repository: OfflineDownloadsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = mutableState.asStateFlow()
    private var pendingRemoval: PendingRemoval? = null

    init {
        viewModelScope.launch {
            runCatching { repository.reconcile() }
            repository.downloads
                .map { records ->
                    val items = records.map(OfflineDownloadEntity::toUi)
                    val grouped = items.grouped()
                    val seasons = records
                        .filter { it.contentType == "Episode" && it.providerId != null && it.showId != null && it.seasonNumber != null }
                        .groupBy { listOf(it.providerId!!, it.showId!!, it.seasonNumber.toString()) }
                        .map { (_, group) -> SeasonDownloadUi(group.first().providerId!!, group.first().showId!!, group.first().showTitle ?: "TV Show", group.first().seasonNumber!!, group.map(OfflineDownloadEntity::toUi).sortedBy { it.episodeNumber }) }
                        .sortedWith(compareBy({ it.showTitle }, { it.seasonNumber }))
                    val shows = seasons.groupBy { "${it.providerId}:${it.showId}" }.map { (showKey, showSeasons) ->
                        val first = showSeasons.first().episodes.first()
                        DownloadedShowUi(
                            showKey = showKey,
                            title = first.showTitle ?: "TV Show",
                            posterPath = first.posterPath,
                            backdropPath = first.backdropPath,
                            synopsis = first.synopsis,
                            releaseYear = first.releaseYear,
                            genre = first.genre,
                            rating = first.rating,
                            runtime = first.runtime,
                            seasons = showSeasons.sortedBy { it.seasonNumber },
                        )
                    }.sortedBy { it.title.lowercase() }
                    DownloadCollections(grouped.first, grouped.second, seasons, items.filter { it.contentType == "Movie" }, shows)
                }
                .distinctUntilChanged()
                .catch { error ->
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        errorMessage = error.message ?: "Downloads could not be loaded.",
                    )
                }
                .collect { grouped ->
                    val refreshed = mutableState.value.copy(
                        loading = false,
                        active = grouped.active,
                        completed = grouped.completed,
                        errorMessage = null,
                        seasons = grouped.seasons,
                        movies = grouped.movies,
                        shows = grouped.shows,
                    )
                    mutableState.value = reconcileDestination(refreshed)
                }
        }
    }

    fun pause(downloadId: String) = repository.pause(downloadId)
    fun resume(downloadId: String) = repository.resume(downloadId)
    fun retry(downloadId: String) = repository.retry(downloadId)
    fun cancel(downloadId: String) = repository.cancel(downloadId)

    fun requestDelete(item: DownloadItemUi) {
        mutableState.value = mutableState.value.copy(pendingDelete = item)
    }

    fun dismissDelete() {
        mutableState.value = mutableState.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val item = mutableState.value.pendingDelete ?: return
        val current = mutableState.value
        pendingRemoval = when (val destination = current.destination) {
            is DownloadsDestination.Movie -> PendingRemoval.Movie(item.downloadId, current.movies.indexOfFirst { it.downloadId == item.downloadId }.coerceAtLeast(0))
            is DownloadsDestination.Season -> {
                val season = current.shows.firstOrNull { it.showKey == destination.showKey }?.seasons?.firstOrNull { it.seasonNumber == destination.seasonNumber }
                PendingRemoval.Episode(destination.showKey, destination.seasonNumber, item.downloadId, season?.episodes?.indexOfFirst { it.downloadId == item.downloadId }?.coerceAtLeast(0) ?: 0)
            }
            else -> PendingRemoval.Movie(item.downloadId, 0)
        }
        mutableState.value = current.copy(pendingDelete = null, deletionErrorMessage = null)
        requestRemoval(listOf(item.downloadId))
    }

    fun pauseSeason(season: SeasonDownloadUi) = season.episodes.filterNot { it.playable }.forEach { repository.pause(it.downloadId) }
    fun resumeSeason(season: SeasonDownloadUi) = season.episodes.filter { it.state == OfflineDownloadState.Paused }.forEach { repository.resume(it.downloadId) }
    fun cancelSeason(season: SeasonDownloadUi) = season.episodes.filterNot { it.playable }.forEach { repository.cancel(it.downloadId) }
    fun requestDeleteSeason(season: SeasonDownloadUi) { mutableState.value = mutableState.value.copy(pendingSeasonDelete = season) }
    fun dismissDeleteSeason() { mutableState.value = mutableState.value.copy(pendingSeasonDelete = null) }
    fun confirmDeleteSeason() {
        val season = mutableState.value.pendingSeasonDelete ?: return
        val current = mutableState.value
        val showKey = "${season.providerId}:${season.showId}"
        val show = current.shows.firstOrNull { it.showKey == showKey }
        pendingRemoval = PendingRemoval.Season(showKey, season.episodes.map { it.downloadId }.toSet(), show?.seasons?.indexOfFirst { it.seasonNumber == season.seasonNumber }?.coerceAtLeast(0) ?: 0)
        mutableState.value = current.copy(pendingSeasonDelete = null, deletionErrorMessage = null)
        requestRemoval(season.episodes.map { it.downloadId })
    }

    fun requestDeleteShow(show: DownloadedShowUi) { mutableState.value = mutableState.value.copy(pendingShowDelete = show) }
    fun dismissDeleteShow() { mutableState.value = mutableState.value.copy(pendingShowDelete = null) }
    fun confirmDeleteShow() {
        val show = mutableState.value.pendingShowDelete ?: return
        val ids = show.seasons.flatMap { it.episodes }.map { it.downloadId }
        pendingRemoval = PendingRemoval.Show(show.showKey, ids.toSet(), mutableState.value.shows.indexOfFirst { it.showKey == show.showKey }.coerceAtLeast(0))
        mutableState.value = mutableState.value.copy(pendingShowDelete = null, deletionErrorMessage = null)
        requestRemoval(ids)
    }

    fun dismissDeletionError() { mutableState.value = mutableState.value.copy(deletionErrorMessage = null) }

    fun selectCategory(category: DownloadsCategory) {
        mutableState.value = mutableState.value.copy(selectedCategory = category, destination = DownloadsDestination.Library, focusRestoreKey = null)
    }

    fun openMovie(downloadId: String) {
        mutableState.value = mutableState.value.copy(destination = DownloadsDestination.Movie(downloadId))
    }

    fun openShow(showKey: String) {
        mutableState.value = mutableState.value.copy(destination = DownloadsDestination.Show(showKey))
    }

    fun openSeason(showKey: String, seasonNumber: Int) {
        mutableState.value = mutableState.value.copy(destination = DownloadsDestination.Season(showKey, seasonNumber))
    }

    fun navigateBack(): Boolean {
        val current = mutableState.value.destination
        if (current == DownloadsDestination.Library) return false
        mutableState.value = mutableState.value.copy(
            destination = when (current) {
                is DownloadsDestination.Season -> DownloadsDestination.Show(current.showKey)
                else -> DownloadsDestination.Library
            },
        )
        return true
    }

    private fun requestRemoval(downloadIds: List<String>) {
        runCatching { downloadIds.forEach(repository::remove) }.onFailure { error ->
            pendingRemoval = null
            mutableState.value = mutableState.value.copy(
                deletionErrorMessage = error.message?.takeIf(String::isNotBlank) ?: "The download could not be deleted. Please try again.",
            )
        }
    }

    private fun reconcileDestination(state: DownloadsUiState): DownloadsUiState {
        val removal = pendingRemoval
        if (removal != null && removal.downloadIds.none { id -> state.movies.any { it.downloadId == id } || state.seasons.any { season -> season.episodes.any { it.downloadId == id } } }) {
            pendingRemoval = null
            return removal.destinationAfter(state)
        }
        if (removal != null) return state
        return when (val destination = state.destination) {
            is DownloadsDestination.Movie -> if (state.movies.none { it.downloadId == destination.downloadId }) state.copy(selectedCategory = DownloadsCategory.Movies, destination = DownloadsDestination.Library) else state
            is DownloadsDestination.Show -> if (state.shows.none { it.showKey == destination.showKey }) state.copy(selectedCategory = DownloadsCategory.TvShows, destination = DownloadsDestination.Library) else state
            is DownloadsDestination.Season -> {
                val show = state.shows.firstOrNull { it.showKey == destination.showKey }
                when {
                    show == null -> state.copy(selectedCategory = DownloadsCategory.TvShows, destination = DownloadsDestination.Library)
                    show.seasons.none { it.seasonNumber == destination.seasonNumber } -> state.copy(selectedCategory = DownloadsCategory.TvShows, destination = DownloadsDestination.Show(destination.showKey))
                    else -> state
                }
            }
            DownloadsDestination.Library -> state
        }
    }
}

private sealed class PendingRemoval(open val downloadIds: Set<String>) {
    data class Movie(val id: String, val previousIndex: Int) : PendingRemoval(setOf(id))
    data class Episode(val showKey: String, val seasonNumber: Int, val id: String, val previousIndex: Int) : PendingRemoval(setOf(id))
    data class Season(val showKey: String, override val downloadIds: Set<String>, val previousIndex: Int) : PendingRemoval(downloadIds)
    data class Show(val showKey: String, override val downloadIds: Set<String>, val previousIndex: Int) : PendingRemoval(downloadIds)

    fun destinationAfter(state: DownloadsUiState): DownloadsUiState = when (this) {
        is Movie -> state.copy(
            selectedCategory = DownloadsCategory.Movies,
            destination = DownloadsDestination.Library,
            focusRestoreKey = state.movies.getOrNull(previousIndex.coerceAtMost((state.movies.size - 1).coerceAtLeast(0)))?.downloadId,
        )
        is Episode -> {
            val show = state.shows.firstOrNull { it.showKey == showKey }
            val season = show?.seasons?.firstOrNull { it.seasonNumber == seasonNumber }
            when {
                show == null -> state.copy(selectedCategory = DownloadsCategory.TvShows, destination = DownloadsDestination.Library)
                season == null -> state.copy(selectedCategory = DownloadsCategory.TvShows, destination = DownloadsDestination.Show(showKey), focusRestoreKey = show.seasons.firstOrNull()?.seasonNumber?.let { "season:$it" })
                else -> state.copy(destination = DownloadsDestination.Season(showKey, seasonNumber), focusRestoreKey = season.episodes.getOrNull(previousIndex.coerceAtMost((season.episodes.size - 1).coerceAtLeast(0)))?.downloadId)
            }
        }
        is Season -> {
            val show = state.shows.firstOrNull { it.showKey == showKey }
            if (show == null) state.copy(selectedCategory = DownloadsCategory.TvShows, destination = DownloadsDestination.Library)
            else state.copy(destination = DownloadsDestination.Show(showKey), focusRestoreKey = show.seasons.getOrNull(previousIndex.coerceAtMost((show.seasons.size - 1).coerceAtLeast(0)))?.seasonNumber?.let { "season:$it" })
        }
        is Show -> state.copy(
            selectedCategory = DownloadsCategory.TvShows,
            destination = DownloadsDestination.Library,
            focusRestoreKey = state.shows.getOrNull(previousIndex.coerceAtMost((state.shows.size - 1).coerceAtLeast(0)))?.showKey,
        )
    }
}

private data class DownloadCollections(
    val active: List<DownloadItemUi>,
    val completed: List<DownloadItemUi>,
    val seasons: List<SeasonDownloadUi>,
    val movies: List<DownloadItemUi>,
    val shows: List<DownloadedShowUi>,
)

data class OfflinePlaybackUiState(
    val loading: Boolean = true,
    val title: String = "Downloaded movie",
    val errorMessage: String? = null,
)

class OfflinePlaybackViewModel(
    private val repository: OfflineDownloadsRepository,
    private val playerManager: WatchioPlayerManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OfflinePlaybackUiState())
    val state: StateFlow<OfflinePlaybackUiState> = mutableState.asStateFlow()
    val playerState = playerManager.state

    fun load(downloadId: String) {
        viewModelScope.launch {
            val record = repository.findById(downloadId)
            val media = repository.completedPlayback(downloadId)
            if (record == null || media == null) {
                mutableState.value = OfflinePlaybackUiState(
                    loading = false,
                    title = record?.title ?: "Downloaded movie",
                    errorMessage = "This download is incomplete or its media is missing.",
                )
                return@launch
            }
            mutableState.value = OfflinePlaybackUiState(loading = false, title = record.title)
            playerManager.load(media)
        }
    }

    fun playPause() {
        if (playerManager.state.value is WatchioPlayerState.Playing) playerManager.pause() else playerManager.play()
    }

    fun close() = playerManager.stop()
    fun pauseForBackground() = playerManager.pause()
}

private fun OfflineDownloadEntity.toUi(): DownloadItemUi {
    val parsedState = runCatching { OfflineDownloadState.valueOf(state) }.getOrDefault(OfflineDownloadState.Failed)
    val roundedProgress = when {
        parsedState == OfflineDownloadState.Completed -> 100
        percentDownloaded.isFinite() -> percentDownloaded.toInt().coerceIn(0, 100)
        else -> 0
    }
    return DownloadItemUi(
        downloadId = downloadId,
        title = title,
        posterPath = localPosterPath,
        state = parsedState,
        bytesDownloaded = bytesDownloaded.coerceAtLeast(0L).let { bytes ->
            if (parsedState == OfflineDownloadState.Completed) bytes else (bytes / PROGRESS_BYTE_STEP) * PROGRESS_BYTE_STEP
        },
        contentLength = contentLength.takeIf { it > 0L },
        progressPercent = roundedProgress,
        failureMessage = failureReason.toFailureMessage(),
        contentType = contentType,
        showTitle = showTitle,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        synopsis = synopsis,
        releaseYear = releaseYear,
        genre = genre,
        rating = rating,
        runtime = runtime,
        episodeTitle = episodeTitle,
        backdropPath = localBackdropPath,
        thumbnailPath = localThumbnailPath,
    )
}

private const val PROGRESS_BYTE_STEP = 5L * 1024L * 1024L

private fun List<DownloadItemUi>.grouped(): Pair<List<DownloadItemUi>, List<DownloadItemUi>> =
    filterNot { it.playable } to filter { it.playable }

private fun String?.toFailureMessage(): String? = when (this) {
    OfflineDownloadFailure.Network.name -> "Network connection interrupted."
    OfflineDownloadFailure.StorageExhausted.name -> "Storage became full during download."
    OfflineDownloadFailure.SourceUnavailable.name -> "The download source is no longer available."
    OfflineDownloadFailure.Unknown.name -> "The download failed."
    else -> null
}
