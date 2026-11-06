package com.iamskorpz.watchioiptv.core.di

import android.content.Context
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.announcements.StaticAnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadSourceResolver
import com.iamskorpz.watchioiptv.data.downloads.RepositoryOfflineDownloadSourceResolver
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.data.updates.UpdateRepository
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointConfigSource
import com.iamskorpz.watchioiptv.syntheticfailovertest.SyntheticFailoverEndpointConfigSource
import com.iamskorpz.watchioiptv.core.player.PlaybackFailoverDiagnostics
import com.iamskorpz.watchioiptv.syntheticfailovertest.SyntheticPlaybackFailoverDiagnostics
import okhttp3.OkHttpClient

internal object AppVariantBindings {
    fun createPlaybackFailoverDiagnostics(): PlaybackFailoverDiagnostics = SyntheticPlaybackFailoverDiagnostics

    fun createEndpointConfigSource(context: Context, client: OkHttpClient): WatchioEndpointConfigSource =
        SyntheticFailoverEndpointConfigSource(context)

    fun createOfflineDownloadSourceResolver(
        moviesRepository: MoviesRepository,
        seriesRepository: SeriesRepository,
    ): OfflineDownloadSourceResolver = RepositoryOfflineDownloadSourceResolver(moviesRepository, seriesRepository)

    fun createAnnouncementRemoteDataSource(context: Context, client: OkHttpClient): AnnouncementRemoteDataSource =
        StaticAnnouncementRemoteDataSource("""{"schemaVersion":1,"announcements":[]}""")

    fun createUpdateRepository(context: Context, client: OkHttpClient): UpdateRepository =
        UpdateRepository(
            context = context,
            okHttpClient = client,
            manifestUrl = "",
            expectedChannel = "syntheticfailovertest",
            artifactDownloadsEnabled = false,
        )
}
