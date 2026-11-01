package com.iamskorpz.watchioiptv.data.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.ResolvingDataSource
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadDao
import com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.core.model.ProviderId
import kotlinx.coroutines.runBlocking
import java.io.File

@UnstableApi
class OfflineMediaStore(
    context: Context,
    private val dao: OfflineDownloadDao,
    private val sourceResolver: OfflineDownloadSourceResolver,
) {
    val rootDirectory: File = File(context.noBackupFilesDir, "offline-media").apply { mkdirs() }
    val databaseProvider = StandaloneDatabaseProvider(context)
    val cache = SimpleCache(rootDirectory, NoOpCacheEvictor(), databaseProvider)

    private val remoteFactory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
        ResolvingDataSource.Resolver(::resolveRemoteRequest),
    )

    val playbackDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(null)

    val downloadDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(remoteFactory)

    private fun resolveRemoteRequest(dataSpec: DataSpec): DataSpec {
        val downloadId = downloadIdFromUri(dataSpec.uri)
            ?: throw IllegalArgumentException("Invalid offline download source.")
        val record = dao.findByIdBlocking(downloadId)
            ?: throw IllegalStateException("Offline download metadata unavailable.")
        val playback = sourceResolver.resolve(record)
        return dataSpec.buildUpon()
            .setUri(playback.url)
            .setHttpRequestHeaders(playback.headers)
            .setKey(cacheKey(downloadId))
            .build()
    }

    companion object {
        private const val SCHEME = "watchio-offline"
        private const val AUTHORITY = "movie"

        fun opaqueUri(downloadId: String): Uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath(downloadId)
            .build()

        fun cacheKey(downloadId: String): String = "watchio-offline:$downloadId"

        fun downloadIdFromUri(uri: Uri): String? = uri
            .takeIf { it.scheme == SCHEME && it.authority == AUTHORITY }
            ?.pathSegments
            ?.singleOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}

data class OfflineDownloadRemoteSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

fun interface OfflineDownloadSourceResolver {
    fun resolve(record: OfflineDownloadEntity): OfflineDownloadRemoteSource
}

class RepositoryOfflineDownloadSourceResolver(
    private val moviesRepository: MoviesRepository,
    private val seriesRepository: SeriesRepository,
) : OfflineDownloadSourceResolver {
    override fun resolve(record: OfflineDownloadEntity): OfflineDownloadRemoteSource {
        val providerId = record.providerId
            ?: throw IllegalStateException("Offline download source unavailable.")
        return runBlocking {
            if (record.contentType == "Episode") {
                val playback = seriesRepository.offlinePlayback(
                    providerId,
                    record.showId ?: throw IllegalStateException("Episode identity unavailable."),
                    record.contentId,
                )
                OfflineDownloadRemoteSource(playback.url, playback.headers)
            } else {
                val movie = moviesRepository.movie(ProviderId(providerId), record.contentId)
                    ?: throw IllegalStateException("Offline download source unavailable.")
                val playback = moviesRepository.playback(movie, resume = false)
                OfflineDownloadRemoteSource(playback.url, playback.headers)
            }
        }
    }
}
