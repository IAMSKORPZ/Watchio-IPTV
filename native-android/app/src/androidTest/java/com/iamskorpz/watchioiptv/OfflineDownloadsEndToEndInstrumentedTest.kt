package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iamskorpz.watchioiptv.data.downloads.DownloadAuthorization
import com.iamskorpz.watchioiptv.data.downloads.EnqueueResult
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadState
import com.iamskorpz.watchioiptv.data.downloads.OfflineMovieDownloadCandidate
import com.iamskorpz.watchioiptv.data.downloads.UitestMovieDownloadAuthorizationResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class OfflineDownloadsEndToEndInstrumentedTest {
    private val application = ApplicationProvider.getApplicationContext<Context>() as WatchioNativeApplication
    private val repository get() = application.container.offlineMovieDownloadRepository
    private val dao get() = application.container.database.offlineDownloadDao()

    @Test
    fun step1_startDownloadAndPauseAfterProgress() = runBlocking {
        removeFixtureIfPresent()
        val result = repository.enqueue(candidate())
        assertTrue(result is EnqueueResult.Accepted)
        val partial = awaitRecord {
            it.state == OfflineDownloadState.Downloading.name && it.bytesDownloaded >= 512 * 1024L
        }
        repository.pause(partial.downloadId)
        val paused = awaitRecord { it.state == OfflineDownloadState.Paused.name }
        assertTrue(paused.bytesDownloaded > 0L)
        assertTrue(paused.bytesDownloaded < FIXTURE_BYTES)
        val request = repository.downloadManager.downloadIndex.getDownload(paused.downloadId)!!.request
        assertEquals("watchio-offline", request.uri.scheme)
        assertTrue(request.data.isEmpty())
    }

    @Test
    fun step2_resumeWithoutFixtureServerRecordsFailure() = runBlocking {
        val paused = requireFixture()
        repository.resume(paused.downloadId)
        val failed = awaitRecord(timeoutMs = 45_000L) { it.state == OfflineDownloadState.Failed.name }
        assertTrue(failed.bytesDownloaded > 0L)
    }

    @Test
    fun step3_retryUsesRangeAndCompletesWithoutDuplicate() = runBlocking {
        val failed = requireFixture()
        val bytesBeforeRetry = failed.bytesDownloaded
        repository.retry(failed.downloadId)
        val completed = awaitRecord(timeoutMs = 120_000L) { it.state == OfflineDownloadState.Completed.name }
        assertTrue(completed.bytesDownloaded >= FIXTURE_BYTES)
        assertTrue(completed.bytesDownloaded >= bytesBeforeRetry)
        val duplicate = repository.enqueue(candidate())
        assertEquals(EnqueueResult.AlreadyExists(completed.downloadId), duplicate)
        assertTrue(repository.mediaStore.cache.cacheSpace >= FIXTURE_BYTES)
    }

    @Test
    fun step4_completedDownloadPlaysAndSeeksWithNoServer() = runBlocking {
        val completed = requireFixture()
        assertEquals(OfflineDownloadState.Completed.name, completed.state)
        repository.reconcile()
        val playback = repository.completedPlayback(completed.downloadId)
        assertNotNull(playback)
        playback!!
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(application)
                .setMediaSourceFactory(DefaultMediaSourceFactory(repository.mediaStore.playbackDataSourceFactory))
                .build()
            player.volume = 0f
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(playback.url)
                    .setCustomCacheKey(playback.cacheKey)
                    .setMimeType(playback.mimeType)
                    .build(),
            )
            player.prepare()
            player.playWhenReady = true
        }
        try {
            withTimeout(30_000L) {
                while (onMain(instrumentation) { player.playbackState } != Player.STATE_READY) delay(100L)
            }
            val target = (onMain(instrumentation) { player.duration } / 2L).coerceAtLeast(1_000L)
            instrumentation.runOnMainSync { player.seekTo(target) }
            withTimeout(15_000L) {
                while (onMain(instrumentation) { player.currentPosition } < target) delay(100L)
            }
            val before = onMain(instrumentation) { player.currentPosition }
            delay(1_000L)
            assertTrue(onMain(instrumentation) { player.currentPosition } > before)
            assertNull(onMain(instrumentation) { player.playerError })
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }

    @Test
    fun step5_deleteRemovesMetadataAndReclaimsCache() = runBlocking {
        val completed = requireFixture()
        val cacheBefore = repository.mediaStore.cache.cacheSpace
        repository.remove(completed.downloadId)
        withTimeout(30_000L) {
            while (dao.findById(completed.downloadId) != null) delay(100L)
        }
        assertNull(repository.downloadManager.downloadIndex.getDownload(completed.downloadId))
        assertTrue(repository.mediaStore.cache.cacheSpace < cacheBefore)
    }

    @Test
    fun step6_cancelActiveDownloadRemovesPartialMedia() = runBlocking {
        removeFixtureIfPresent()
        assertTrue(repository.enqueue(candidate()) is EnqueueResult.Accepted)
        val active = awaitRecord { it.state == OfflineDownloadState.Downloading.name && it.bytesDownloaded > 0L }
        repository.cancel(active.downloadId)
        withTimeout(30_000L) {
            while (dao.findById(active.downloadId) != null) delay(100L)
        }
        assertNull(repository.downloadManager.downloadIndex.getDownload(active.downloadId))
    }

    private suspend fun removeFixtureIfPresent() {
        val existing = dao.findByContent(PROVIDER_ID, CONTENT_ID) ?: return
        repository.remove(existing.downloadId)
        withTimeout(30_000L) {
            while (dao.findById(existing.downloadId) != null) delay(100L)
        }
    }

    private suspend fun requireFixture() = dao.findByContent(PROVIDER_ID, CONTENT_ID)
        ?: error("Offline UITEST fixture is missing.")

    private suspend fun awaitRecord(
        timeoutMs: Long = 30_000L,
        predicate: (com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity) -> Boolean,
    ): com.iamskorpz.watchioiptv.core.database.OfflineDownloadEntity {
        val matched = withTimeoutOrNull(timeoutMs) {
        while (true) {
            val record = dao.findByContent(PROVIDER_ID, CONTENT_ID)
            if (record != null && predicate(record)) return@withTimeoutOrNull record
            delay(100L)
        }
            error("unreachable")
        }
        if (matched != null) return matched
        val latest = dao.findByContent(PROVIDER_ID, CONTENT_ID)
        val indexed = latest?.let { repository.downloadManager.downloadIndex.getDownload(it.downloadId) }
        error("Timed out. Room=$latest Media3State=${indexed?.state} StopReason=${indexed?.stopReason} Failure=${indexed?.failureReason}")
    }

    private fun candidate() = OfflineMovieDownloadCandidate(
        providerId = PROVIDER_ID,
        contentId = CONTENT_ID,
        title = "Watchio Authorised Offline Test",
        containerExtension = "mp4",
        mimeType = "video/mp4",
        expectedContentLength = FIXTURE_BYTES,
        authorization = DownloadAuthorization.ProviderApproved,
    )

    private fun <T> onMain(
        instrumentation: android.app.Instrumentation,
        block: () -> T,
    ): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val PROVIDER_ID = UitestMovieDownloadAuthorizationResolver.PROVIDER_ID
        const val CONTENT_ID = UitestMovieDownloadAuthorizationResolver.CONTENT_ID
        const val FIXTURE_BYTES = 23_367_080L
    }
}
