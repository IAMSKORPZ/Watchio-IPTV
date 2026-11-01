package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.data.downloads.DownloadAuthorization
import com.iamskorpz.watchioiptv.data.downloads.DownloadDenialReason
import com.iamskorpz.watchioiptv.data.downloads.DownloadEligibility
import com.iamskorpz.watchioiptv.data.downloads.OfflineDownloadPolicy
import com.iamskorpz.watchioiptv.data.downloads.OfflineMovieDownloadRepository
import com.iamskorpz.watchioiptv.data.downloads.OfflineMovieDownloadCandidate
import com.iamskorpz.watchioiptv.data.downloads.OfflineStoragePolicy
import com.iamskorpz.watchioiptv.data.downloads.PlaylistMovieDownloadAuthorizationResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadPolicyTest {
    @Test
    fun globalDownloadQueueAllowsOnlyOneActiveTransfer() {
        assertEquals(1, OfflineMovieDownloadRepository.MAX_PARALLEL_DOWNLOADS)
    }
    @Test fun playlistMoviesAreApprovedAfterLogin() {
        assertEquals(
            DownloadAuthorization.ProviderApproved,
            PlaylistMovieDownloadAuthorizationResolver.authorizationFor("provider", "movie"),
        )
        assertEquals(
            DownloadAuthorization.NotEstablished,
            PlaylistMovieDownloadAuthorizationResolver.authorizationFor("", "movie"),
        )
    }

    @Test fun downloadsAreDeniedWithoutExplicitAuthorization() {
        val result = OfflineDownloadPolicy.evaluate(candidate(extension = "mp4"))
        assertEquals(DownloadEligibility.Denied(DownloadDenialReason.AuthorizationRequired), result)
    }

    @Test fun drmIsRejectedEvenWhenProviderApproved() {
        val result = OfflineDownloadPolicy.evaluate(
            candidate(extension = "mp4", authorization = DownloadAuthorization.ProviderApproved, drm = true)
        )
        assertEquals(DownloadEligibility.Denied(DownloadDenialReason.DrmProtected), result)
    }

    @Test fun onlyProgressiveMp4AndMkvAreAllowed() {
        listOf("mp4", "m4v", "mkv").forEach { extension ->
            assertTrue(
                OfflineDownloadPolicy.evaluate(
                    candidate(extension, DownloadAuthorization.ProviderApproved)
                ) is DownloadEligibility.Allowed
            )
        }
        listOf("m3u8", "mpd", "ts", "avi").forEach { extension ->
            assertEquals(
                DownloadEligibility.Denied(DownloadDenialReason.UnsupportedFormat),
                OfflineDownloadPolicy.evaluate(candidate(extension, DownloadAuthorization.ProviderApproved)),
            )
        }
    }

    @Test fun storageReserveProtectsAgainstExhaustion() {
        val total = 10L * 1024L * 1024L * 1024L
        val reserve = OfflineStoragePolicy.reserveBytes(total)
        assertFalse(OfflineStoragePolicy.hasCapacity(total, reserve + 99L, 100L))
        assertTrue(OfflineStoragePolicy.hasCapacity(total, reserve + 100L, 100L))
    }

    private fun candidate(
        extension: String,
        authorization: DownloadAuthorization = DownloadAuthorization.NotEstablished,
        drm: Boolean = false,
    ) = OfflineMovieDownloadCandidate(
        providerId = "provider",
        contentId = "movie",
        title = "Movie",
        containerExtension = extension,
        isDrmProtected = drm,
        authorization = authorization,
    )
}
