package com.iamskorpz.watchioiptv

import androidx.media3.common.PlaybackException
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.player.PlaybackEndpointFailureClassifier
import com.iamskorpz.watchioiptv.core.player.PlaybackEndpointRecoveryResult
import com.iamskorpz.watchioiptv.core.player.PlaybackEndpointRecoveryGate
import com.iamskorpz.watchioiptv.core.player.withRecoveryPosition
import com.iamskorpz.watchioiptv.core.player.PlaybackFailure
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.PlaybackSourceType
import com.iamskorpz.watchioiptv.data.refresh.NetworkConnectivityChecker
import com.iamskorpz.watchioiptv.data.xtream.AccountValidationResult
import com.iamskorpz.watchioiptv.data.xtream.PlaybackEndpointRecoveryCoordinator
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import java.util.concurrent.atomic.AtomicInteger
import java.net.SocketException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEndpointRecoveryTest {
    @Test fun retryMediaPreservesLatestVodPositionButLiveReturnsToEdge() {
        val movie = remoteMovie().copy(startPositionMs = 37_000L)
        assertEquals(82_000L, movie.withRecoveryPosition(82_000L).startPositionMs)
        assertEquals(37_000L, movie.withRecoveryPosition(0L).startPositionMs)
        assertEquals(0L, movie.copy(isLive = true).withRecoveryPosition(82_000L).startPositionMs)
    }

    @Test fun networkAndTimeoutFailuresAreEligible() {
        assertTrue(PlaybackEndpointFailureClassifier.shouldAttemptRecovery(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED), 1))
        assertTrue(PlaybackEndpointFailureClassifier.shouldAttemptRecovery(error(PlaybackException.ERROR_CODE_TIMEOUT), 1))
    }

    @Test fun connectionFailureCanProbeBeforeAnySameUrlRetry() {
        assertTrue(
            PlaybackEndpointFailureClassifier.shouldAttemptRecovery(
                error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
                attemptCount = 1,
            ),
        )
    }

    @Test fun abruptLoadSocketFailureIsEligibleForEarlyProbe() {
        assertTrue(
            PlaybackEndpointFailureClassifier.shouldAttemptRecovery(
                PlaybackFailure(PlaybackException.ERROR_CODE_UNSPECIFIED, SocketException("connection reset")),
                attemptCount = 1,
            ),
        )
    }

    @Test fun decoderAndDrmFailuresAreNeverEligible() {
        assertFalse(PlaybackEndpointFailureClassifier.shouldAttemptRecovery(error(PlaybackException.ERROR_CODE_DECODING_FAILED), 3))
        assertFalse(PlaybackEndpointFailureClassifier.shouldAttemptRecovery(error(PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR), 3))
    }

    @Test fun contentAuthAndLegalHttpFailuresNeverTriggerSwitch() {
        listOf(401, 403, 404, 410, 451).forEach { responseCode ->
            assertFalse(
                PlaybackEndpointFailureClassifier.shouldAttemptRecovery(
                    PlaybackFailure(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null, responseCode),
                    3,
                ),
            )
        }
    }

    @Test fun serverHttpFailureRequiresRepeatedAttempts() {
        val failure = PlaybackFailure(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null, 503)
        assertFalse(PlaybackEndpointFailureClassifier.shouldAttemptRecovery(failure, 1))
        assertTrue(PlaybackEndpointFailureClassifier.shouldAttemptRecovery(failure, 2))
    }

    @Test fun repeatedServerFailureStillRetainsHealthyPrimary() = runBlocking {
        var resolved = false
        val coordinator = coordinator(
            urls = ArrayDeque(listOf("https://one.test", "https://one.test")),
            validation = AccountValidationResult.Active,
            resolve = { resolved = true; "https://two.test/movie" },
        )
        val failure = PlaybackFailure(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null, 503)
        assertEquals(PlaybackEndpointRecoveryResult.NotRecovered, coordinator.recover(remoteMovie(), failure, 2, 7_000L))
        assertFalse(resolved)
    }

    @Test fun healthyCurrentEndpointDoesNotSwitchOrRetry() = runBlocking {
        var resolved = false
        val coordinator = coordinator(
            urls = ArrayDeque(listOf("https://one.test", "https://one.test")),
            validation = AccountValidationResult.Active,
            resolve = { resolved = true; "https://two.test/movie" },
        )
        assertEquals(PlaybackEndpointRecoveryResult.NotRecovered, coordinator.recover(remoteMovie(), timeout(), 1, 7_000L))
        assertFalse(resolved)
    }

    @Test fun authenticatedEndpointSwitchRebuildsSameVodAtCurrentPosition() = runBlocking {
        val coordinator = coordinator(
            urls = ArrayDeque(listOf("https://one.test", "https://two.test")),
            validation = AccountValidationResult.Active,
        )
        val result = coordinator.recover(remoteMovie(), timeout(), 1, 7_000L) as PlaybackEndpointRecoveryResult.Retry
        assertEquals("https://two.test/rebuilt", result.media.url)
        assertEquals(7_000L, result.media.startPositionMs)
        assertEquals("movie-7", result.media.recoveryRequest?.contentId)
    }

    @Test fun liveEndpointSwitchRestartsAtLiveEdge() = runBlocking {
        val coordinator = coordinator(
            urls = ArrayDeque(listOf("https://one.test", "https://two.test")),
            validation = AccountValidationResult.Active,
        )
        val live = remoteMovie().copy(isLive = true, recoveryRequest = PlaybackUrlRequest(ProviderId("provider"), ContentType.Live, "42"))
        val result = coordinator.recover(live, timeout(), 1, 99_000L) as PlaybackEndpointRecoveryResult.Retry
        assertEquals(0L, result.media.startPositionMs)
    }

    @Test fun accountRejectionIsReportedWithoutEndpointRetry() = runBlocking {
        var blockedMessage: String? = null
        val coordinator = PlaybackEndpointRecoveryCoordinator(
            providerUrl = { "https://one.test" },
            validateAccount = { AccountValidationResult.Blocked("Sign in again.") },
            resolveUrl = { "unused" },
            connectivityChecker = NetworkConnectivityChecker { true },
            onAccountBlocked = { blockedMessage = it },
        )
        assertEquals(
            PlaybackEndpointRecoveryResult.AccountBlocked("Sign in again."),
            coordinator.recover(remoteMovie(), timeout(), 1, 0L),
        )
        assertEquals("Sign in again.", blockedMessage)
    }

    @Test fun offlineAndCachePlaybackNeverTriggerRecovery() = runBlocking {
        var validations = 0
        val coordinator = PlaybackEndpointRecoveryCoordinator(
            providerUrl = { "https://one.test" },
            validateAccount = { validations++; AccountValidationResult.Active },
            resolveUrl = { "https://two.test/rebuilt" },
            connectivityChecker = NetworkConnectivityChecker { false },
        )
        assertEquals(PlaybackEndpointRecoveryResult.NotRecovered, coordinator.recover(remoteMovie(), timeout(), 1, 0L))
        assertEquals(
            PlaybackEndpointRecoveryResult.NotRecovered,
            coordinator.recover(remoteMovie().copy(sourceType = PlaybackSourceType.OfflineCache), timeout(), 1, 0L),
        )
        assertEquals(0, validations)
    }

    @Test fun cooldownPreventsRepeatedRecoveryLoops() = runBlocking {
        var validations = 0
        val coordinator = PlaybackEndpointRecoveryCoordinator(
            providerUrl = { "https://one.test" },
            validateAccount = { validations++; AccountValidationResult.Retryable("Unavailable") },
            resolveUrl = { "unused" },
            connectivityChecker = NetworkConnectivityChecker { true },
            nowMs = { 1_000L },
        )
        coordinator.recover(remoteMovie(), timeout(), 1, 0L)
        coordinator.recover(remoteMovie(), timeout(), 1, 0L)
        assertEquals(1, validations)
    }

    @Test fun onePlaybackSequenceAllowsOnlyOneEndpointRecovery() {
        val gate = PlaybackEndpointRecoveryGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.reset()
        assertTrue(gate.tryStart())
    }

    @Test fun earlyAndTerminalErrorsRaceIntoOneRecoveryAttempt() = runBlocking {
        val gate = PlaybackEndpointRecoveryGate()
        val starts = AtomicInteger(0)
        List(20) {
            async {
                if (gate.tryStart()) starts.incrementAndGet()
            }
        }.awaitAll()
        assertEquals(1, starts.get())
    }

    @Test fun newPlaybackSequenceInvalidatesOldGateAndGetsIndependentAttempt() {
        val gate = PlaybackEndpointRecoveryGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.reset()
        assertTrue(gate.tryStart())
    }

    @Test fun concurrentFailuresProduceOneHealthCheck() = runBlocking {
        var validations = 0
        val coordinator = PlaybackEndpointRecoveryCoordinator(
            providerUrl = { "https://one.test" },
            validateAccount = { validations++; delay(50); AccountValidationResult.Retryable("Unavailable") },
            resolveUrl = { "unused" },
            connectivityChecker = NetworkConnectivityChecker { true },
            nowMs = { 1_000L },
        )
        listOf(
            async { coordinator.recover(remoteMovie(), timeout(), 1, 0L) },
            async { coordinator.recover(remoteMovie(), timeout(), 1, 0L) },
        ).awaitAll()
        assertEquals(1, validations)
    }

    @Test fun switchedPlaybackPreservesLiveMovieAndEpisodeIdentity() = runBlocking {
        val requests = listOf(
            PlaybackUrlRequest(ProviderId("provider-live"), ContentType.Live, "live-1"),
            PlaybackUrlRequest(ProviderId("provider-movie"), ContentType.Movie, "movie-2", ".mkv"),
            PlaybackUrlRequest(ProviderId("provider-series"), ContentType.Episode, "series-3", "episode-4.mp4"),
        )
        requests.forEach { request ->
            val urls = ArrayDeque(listOf("https://one.test", "https://two.test"))
            val coordinator = PlaybackEndpointRecoveryCoordinator(
                providerUrl = { urls.removeFirst() },
                validateAccount = { AccountValidationResult.Active },
                resolveUrl = { "https://two.test/${it.contentId}" },
                connectivityChecker = NetworkConnectivityChecker { true },
            )
            val result = coordinator.recover(
                remoteMovie().copy(isLive = request.contentType == ContentType.Live, recoveryRequest = request),
                timeout(),
                1,
                4_000L,
            ) as PlaybackEndpointRecoveryResult.Retry
            assertEquals(request, result.media.recoveryRequest)
        }
    }

    @Test fun episodeEndpointSwitchPreservesLatestPosition() = runBlocking {
        val coordinator = coordinator(
            urls = ArrayDeque(listOf("https://one.test", "https://two.test")),
            validation = AccountValidationResult.Active,
        )
        val episode = remoteMovie().copy(
            recoveryRequest = PlaybackUrlRequest(ProviderId("provider"), ContentType.Episode, "episode-9", ".mkv"),
        )
        val result = coordinator.recover(episode, timeout(), 1, 54_321L) as PlaybackEndpointRecoveryResult.Retry
        assertEquals(54_321L, result.media.startPositionMs)
    }

    @Test fun allEndpointsUnavailableNeverBecomesAccountBlocked() = runBlocking {
        var blocked = false
        val coordinator = PlaybackEndpointRecoveryCoordinator(
            providerUrl = { "https://one.test" },
            validateAccount = { AccountValidationResult.Retryable("All unavailable") },
            resolveUrl = { "unused" },
            connectivityChecker = NetworkConnectivityChecker { true },
            onAccountBlocked = { blocked = true },
        )
        assertEquals(PlaybackEndpointRecoveryResult.NotRecovered, coordinator.recover(remoteMovie(), timeout(), 1, 0L))
        assertFalse(blocked)
    }

    private fun coordinator(
        urls: ArrayDeque<String>,
        validation: AccountValidationResult,
        resolve: suspend (PlaybackUrlRequest) -> String = { "https://two.test/rebuilt" },
    ) = PlaybackEndpointRecoveryCoordinator(
        providerUrl = { urls.removeFirstOrNull() ?: urls.lastOrNull() },
        validateAccount = { validation },
        resolveUrl = resolve,
        connectivityChecker = NetworkConnectivityChecker { true },
        nowMs = { 1_000L },
    )

    private fun remoteMovie() = PlaybackMedia(
        url = "https://one.test/movie",
        isLive = false,
        recoveryRequest = PlaybackUrlRequest(ProviderId("provider"), ContentType.Movie, "movie-7"),
    )

    private fun timeout() = error(PlaybackException.ERROR_CODE_TIMEOUT)
    private fun error(code: Int) = PlaybackFailure(code, null)
}
