package com.iamskorpz.watchioiptv.core.di

import android.content.Context
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.announcements.GitHubAnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.updates.UpdateRepository
import okhttp3.OkHttpClient

internal object AppVariantBindings {
    fun createAnnouncementRemoteDataSource(context: Context, client: OkHttpClient): AnnouncementRemoteDataSource =
        GitHubAnnouncementRemoteDataSource(client)

    fun createUpdateRepository(context: Context, client: OkHttpClient): UpdateRepository =
        UpdateRepository(context, client)
}
