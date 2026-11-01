package com.iamskorpz.watchioiptv.uitest

import com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadRemoteSource
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadSourceResolver
import com.iamskorpz.watchioiptv.data.downloads.UitestMovieDownloadAuthorizationResolver

object UitestOfflineDownloadSourceResolver : OfflineDownloadSourceResolver {
    const val FIXTURE_URL = "http://127.0.0.1:18765/watchio-offline-fixture.mp4"

    override fun resolve(record: OfflineDownloadEntity): OfflineDownloadRemoteSource {
        check(record.providerId == UitestMovieDownloadAuthorizationResolver.PROVIDER_ID)
        check(record.contentId == UitestMovieDownloadAuthorizationResolver.CONTENT_ID)
        return OfflineDownloadRemoteSource(FIXTURE_URL)
    }
}
