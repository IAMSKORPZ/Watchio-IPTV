package com.iamskorpz.watchioiptv.data.downloads

import android.content.Context
import android.os.StatFs
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadDao
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.PlaybackSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

interface OfflineDownloadsRepository {
    val downloads: Flow<List<OfflineDownloadEntity>>
    suspend fun enqueue(candidate: OfflineMovieDownloadCandidate): EnqueueResult
    suspend fun backfillMetadata(candidate: OfflineMovieDownloadCandidate)
    fun pause(downloadId: String)
    fun resume(downloadId: String)
    fun retry(downloadId: String)
    fun cancel(downloadId: String)
    fun remove(downloadId: String)
    suspend fun findById(downloadId: String): OfflineDownloadEntity?
    suspend fun completedPlayback(downloadId: String, startPositionMs: Long = 0L): PlaybackMedia?
    suspend fun reconcile()
}

@UnstableApi
class OfflineMovieDownloadRepository(
    private val context: Context,
    private val dao: OfflineDownloadDao,
    val mediaStore: OfflineMediaStore,
) : OfflineDownloadsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enqueueMutex = Mutex()
    override val downloads: Flow<List<OfflineDownloadEntity>> = dao.observeAll()

    val downloadManager = DownloadManager(
        context,
        mediaStore.databaseProvider,
        mediaStore.cache,
        mediaStore.downloadDataSourceFactory,
        Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS),
    ).apply {
        maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
        addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                scope.launch { persistProgress(download, finalException) }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                scope.launch {
                    dao.findById(download.request.id)?.let { record ->
                        listOf(record.localPosterPath, record.localBackdropPath, record.localThumbnailPath).forEach { path ->
                            path?.let { runCatching { File(it).takeIf(File::isFile)?.delete() } }
                        }
                    }
                    dao.delete(download.request.id)
                }
            }
        })
    }

    init {
        scope.launch {
            while (isActive) {
                downloadManager.currentDownloads.forEach { download ->
                    if (download.state == Download.STATE_DOWNLOADING) persistProgress(download, null)
                }
                delay(PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun enqueue(candidate: OfflineMovieDownloadCandidate): EnqueueResult = withContext(Dispatchers.IO) {
        enqueueMutex.withLock {
        val eligibility = OfflineDownloadPolicy.evaluate(candidate)
        if (eligibility is DownloadEligibility.Denied) return@withLock EnqueueResult.Denied(eligibility.reason)
        eligibility as DownloadEligibility.Allowed

        val stat = StatFs(mediaStore.rootDirectory.absolutePath)
        if (!OfflineStoragePolicy.hasCapacity(stat.totalBytes, stat.availableBytes, candidate.expectedContentLength)) {
            return@withLock EnqueueResult.Denied(DownloadDenialReason.InsufficientStorage)
        }
        if (candidate.providerId.isNullOrBlank() || candidate.contentId.isBlank()) {
            return@withLock EnqueueResult.Denied(DownloadDenialReason.SourceUnavailable)
        }
        dao.findByContent(candidate.providerId, candidate.contentId)?.let {
            return@withLock EnqueueResult.AlreadyExists(it.downloadId)
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsert(
            OfflineDownloadEntity(
                downloadId = id,
                providerId = candidate.providerId,
                contentId = candidate.contentId,
                title = candidate.title,
                localPosterPath = null,
                containerExtension = eligibility.extension,
                mimeType = eligibility.mimeType,
                state = OfflineDownloadState.Queued.name,
                bytesDownloaded = 0L,
                contentLength = candidate.expectedContentLength ?: -1L,
                percentDownloaded = 0f,
                failureReason = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                completedAtEpochMs = null,
                contentType = candidate.contentType,
                showId = candidate.showId,
                showTitle = candidate.showTitle,
                seasonNumber = candidate.seasonNumber,
                episodeNumber = candidate.episodeNumber,
                synopsis = candidate.synopsis,
                releaseYear = candidate.releaseYear,
                genre = candidate.genre,
                rating = candidate.rating,
                runtime = candidate.runtime,
                episodeTitle = candidate.episodeTitle,
            )
        )
        val request = request(id, eligibility.mimeType)
        WatchioDownloadService.add(context, request)
        candidate.posterUrl?.takeIf { it.isNotBlank() }?.let { posterUrl ->
            scope.launch { snapshotArtwork(id, "poster", posterUrl, dao::updateLocalPoster) }
        }
        candidate.backdropUrl?.takeIf { it.isNotBlank() }?.let { backdropUrl ->
            scope.launch { snapshotArtwork(id, "backdrop", backdropUrl, dao::updateLocalBackdrop) }
        }
        candidate.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { thumbnailUrl ->
            scope.launch { snapshotArtwork(id, "thumbnail", thumbnailUrl, dao::updateLocalThumbnail) }
        }
        EnqueueResult.Accepted(id)
        }
    }

    override suspend fun backfillMetadata(candidate: OfflineMovieDownloadCandidate) = withContext(Dispatchers.IO) {
        val providerId = candidate.providerId ?: return@withContext
        val record = dao.findByContent(providerId, candidate.contentId) ?: return@withContext
        dao.updateOfflineMetadata(
            downloadId = record.downloadId,
            title = candidate.title,
            synopsis = candidate.synopsis ?: record.synopsis,
            releaseYear = candidate.releaseYear ?: record.releaseYear,
            genre = candidate.genre ?: record.genre,
            rating = candidate.rating ?: record.rating,
            runtime = candidate.runtime ?: record.runtime,
            showTitle = candidate.showTitle ?: record.showTitle,
            episodeTitle = candidate.episodeTitle ?: record.episodeTitle,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        if (record.localPosterPath == null) candidate.posterUrl?.takeIf(String::isNotBlank)?.let {
            snapshotArtwork(record.downloadId, "poster", it, dao::updateLocalPoster)
        }
        if (record.localBackdropPath == null) candidate.backdropUrl?.takeIf(String::isNotBlank)?.let {
            snapshotArtwork(record.downloadId, "backdrop", it, dao::updateLocalBackdrop)
        }
        if (record.localThumbnailPath == null) candidate.thumbnailUrl?.takeIf(String::isNotBlank)?.let {
            snapshotArtwork(record.downloadId, "thumbnail", it, dao::updateLocalThumbnail)
        }
    }

    override fun pause(downloadId: String) {
        downloadManager.setStopReason(downloadId, STOP_REASON_USER_PAUSED)
    }

    override fun resume(downloadId: String) {
        downloadManager.setStopReason(downloadId, Download.STOP_REASON_NONE)
        downloadManager.resumeDownloads()
    }

    override fun retry(downloadId: String) {
        scope.launch {
            val failed = runCatching { downloadManager.downloadIndex.getDownload(downloadId) }.getOrNull()
                ?: return@launch
            downloadManager.setStopReason(downloadId, Download.STOP_REASON_NONE)
            downloadManager.addDownload(failed.request)
            downloadManager.resumeDownloads()
        }
    }

    override fun cancel(downloadId: String) = WatchioDownloadService.remove(context, downloadId)

    override fun remove(downloadId: String) = WatchioDownloadService.remove(context, downloadId)

    override suspend fun findById(downloadId: String): OfflineDownloadEntity? = withContext(Dispatchers.IO) {
        dao.findById(downloadId)
    }

    override suspend fun completedPlayback(downloadId: String, startPositionMs: Long): PlaybackMedia? =
        withContext(Dispatchers.IO) {
            val record = dao.findById(downloadId) ?: return@withContext null
            val download = runCatching { downloadManager.downloadIndex.getDownload(downloadId) }.getOrNull()
            if (download?.state != Download.STATE_COMPLETED || record.state != OfflineDownloadState.Completed.name) {
                return@withContext null
            }
            PlaybackMedia(
                url = OfflineMediaStore.opaqueUri(downloadId).toString(),
                title = record.title,
                startPositionMs = startPositionMs,
                isLive = false,
                sourceType = PlaybackSourceType.OfflineCache,
                cacheKey = OfflineMediaStore.cacheKey(downloadId),
                mimeType = record.mimeType,
            )
        }

    override suspend fun reconcile() = withContext(Dispatchers.IO) {
        val indexed = mutableMapOf<String, Download>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) indexed[cursor.download.request.id] = cursor.download
        }
        dao.getAll().forEach { record ->
            val download = indexed[record.downloadId]
            if (download == null) {
                dao.updateProgress(
                    downloadId = record.downloadId,
                    state = OfflineDownloadState.Failed.name,
                    bytesDownloaded = record.bytesDownloaded,
                    contentLength = record.contentLength,
                    percentDownloaded = record.percentDownloaded,
                    failureReason = OfflineDownloadFailure.SourceUnavailable.name,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    completedAtEpochMs = null,
                )
            } else {
                persistProgress(download, null)
            }
        }
    }

    private suspend fun persistProgress(download: Download, exception: Exception?) {
        val existing = dao.findById(download.request.id) ?: return
        val state = download.toOfflineState()
        val failure = exception?.toSafeFailure()
        dao.updateProgress(
            downloadId = download.request.id,
            state = state.name,
            bytesDownloaded = download.bytesDownloaded,
            contentLength = download.contentLength,
            percentDownloaded = download.percentDownloaded.takeIf { it >= 0f } ?: existing.percentDownloaded,
            failureReason = failure?.name,
            updatedAtEpochMs = System.currentTimeMillis(),
            completedAtEpochMs = if (state == OfflineDownloadState.Completed) {
                existing.completedAtEpochMs ?: System.currentTimeMillis()
            } else null,
        )
    }

    private suspend fun snapshotArtwork(
        downloadId: String,
        kind: String,
        artworkUrl: String,
        persistPath: suspend (String, String, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val uri = runCatching { android.net.Uri.parse(artworkUrl) }.getOrNull() ?: return@withContext
        if (uri.scheme !in setOf("http", "https")) return@withContext
        val directory = File(mediaStore.rootDirectory, "artwork").apply { mkdirs() }
        val destination = File(directory, "$downloadId-$kind.img")
        val temporary = File(directory, "$downloadId-$kind.tmp")
        runCatching {
            val connection = URL(artworkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ARTWORK_BYTES) { "Artwork is too large." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(temporary.renameTo(destination)) { "Artwork could not be stored." }
            persistPath(downloadId, destination.absolutePath, System.currentTimeMillis())
        }.onFailure {
            temporary.delete()
            destination.delete()
        }
    }

    private fun Download.toOfflineState(): OfflineDownloadState = when (state) {
        Download.STATE_QUEUED, Download.STATE_RESTARTING -> OfflineDownloadState.Queued
        Download.STATE_DOWNLOADING -> OfflineDownloadState.Downloading
        Download.STATE_STOPPED -> OfflineDownloadState.Paused
        Download.STATE_COMPLETED -> OfflineDownloadState.Completed
        Download.STATE_FAILED -> OfflineDownloadState.Failed
        Download.STATE_REMOVING -> OfflineDownloadState.Removing
        else -> OfflineDownloadState.Failed
    }

    private fun Throwable.toSafeFailure(): OfflineDownloadFailure {
        val chain = generateSequence(this as Throwable?) { it.cause }
            .mapNotNull { it.message?.lowercase() }
            .joinToString(" ")
        return when {
            "no space" in chain || "enospc" in chain -> OfflineDownloadFailure.StorageExhausted
            this is java.io.IOException -> OfflineDownloadFailure.Network
            else -> OfflineDownloadFailure.Unknown
        }
    }

    companion object {
        internal const val MAX_PARALLEL_DOWNLOADS = 1
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
        private const val STOP_REASON_USER_PAUSED = 1
        private const val MAX_ARTWORK_BYTES = 8L * 1024L * 1024L

        internal fun request(downloadId: String, mimeType: String): DownloadRequest = DownloadRequest.Builder(
            downloadId,
            OfflineMediaStore.opaqueUri(downloadId),
        )
            .setMimeType(mimeType)
            .setCustomCacheKey(OfflineMediaStore.cacheKey(downloadId))
            .build()
    }
}

sealed interface EnqueueResult {
    data class Accepted(val downloadId: String) : EnqueueResult
    data class AlreadyExists(val downloadId: String) : EnqueueResult
    data class Denied(val reason: DownloadDenialReason) : EnqueueResult
}
