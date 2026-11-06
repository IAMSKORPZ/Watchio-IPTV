package com.iamskorpz.watchioiptv.core.di

import android.content.Context
import com.iamskorpz.watchioiptv.BuildConfig
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.announcements.GitHubAnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.updates.UpdateRepository
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadSourceResolver
import com.iamskorpz.watchioiptv.data.downloads.RepositoryOfflineDownloadSourceResolver
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.data.xtream.AndroidWatchioEndpointConfigSource
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointConfigSource
import com.iamskorpz.watchioiptv.core.player.NoOpPlaybackFailoverDiagnostics
import com.iamskorpz.watchioiptv.core.player.PlaybackFailoverDiagnostics
import okhttp3.OkHttpClient

internal object AppVariantBindings {
    fun createPlaybackFailoverDiagnostics(): PlaybackFailoverDiagnostics = NoOpPlaybackFailoverDiagnostics

    fun createEndpointConfigSource(context: Context, client: OkHttpClient): WatchioEndpointConfigSource =
        AndroidWatchioEndpointConfigSource(context, client)

    fun createOfflineDownloadSourceResolver(moviesRepository: MoviesRepository, seriesRepository: SeriesRepository): OfflineDownloadSourceResolver =
        RepositoryOfflineDownloadSourceResolver(moviesRepository, seriesRepository)

    fun createAnnouncementRemoteDataSource(context: Context, client: OkHttpClient): AnnouncementRemoteDataSource =
        GitHubAnnouncementRemoteDataSource(client)

    fun createUpdateRepository(context: Context, client: OkHttpClient): UpdateRepository =
        UpdateRepository(context, client, BuildConfig.UPDATE_MANIFEST_URL, BuildConfig.UPDATE_CHANNEL)
}
