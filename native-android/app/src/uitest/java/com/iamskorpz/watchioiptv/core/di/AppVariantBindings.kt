package com.iamskorpz.watchioiptv.core.di

import android.content.Context
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.updates.UpdateRepository
import com.iamskorpz.watchioiptv.uitest.StartupNotificationFixture
import com.iamskorpz.watchioiptv.uitest.fixtureArtifactDownloadsEnabled
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

internal object AppVariantBindings {
    fun createAnnouncementRemoteDataSource(context: Context, client: OkHttpClient): AnnouncementRemoteDataSource =
        AnnouncementRemoteDataSource { StartupNotificationFixture(context).announcementFeed() }

    fun createUpdateRepository(context: Context, client: OkHttpClient): UpdateRepository = UpdateRepository(
        context = context,
        okHttpClient = client,
        localManifest = { installed -> Json.encodeToString(StartupNotificationFixture(context).updateManifest(installed)) },
        artifactDownloadsEnabled = fixtureArtifactDownloadsEnabled(),
    )
}
