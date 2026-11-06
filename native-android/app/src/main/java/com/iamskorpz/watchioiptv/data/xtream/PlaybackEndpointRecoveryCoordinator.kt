package com.iamskorpz.watchioiptv.data.xtream

import com.iamskorpz.watchioiptv.core.player.PlaybackEndpointFailureClassifier
import com.iamskorpz.watchioiptv.core.player.PlaybackEndpointRecovery
import com.iamskorpz.watchioiptv.core.player.PlaybackEndpointRecoveryResult
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.PlaybackFailure
import com.iamskorpz.watchioiptv.core.player.PlaybackSourceType
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlRequest
import com.iamskorpz.watchioiptv.data.refresh.NetworkConnectivityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaybackEndpointRecoveryCoordinator(
    private val providerUrl: suspend (ProviderId) -> String?,
    private val validateAccount: suspend (ProviderId) -> AccountValidationResult,
    private val resolveUrl: suspend (PlaybackUrlRequest) -> String,
    private val connectivityChecker: NetworkConnectivityChecker,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val cooldownMs: Long = 30_000L,
    private val onAccountBlocked: (String) -> Unit = {},
) : PlaybackEndpointRecovery {
    private val mutex = Mutex()
    private val lastAttemptByProvider = mutableMapOf<String, Long>()

    override suspend fun recover(
        media: PlaybackMedia,
        error: PlaybackFailure,
        attemptCount: Int,
        positionMs: Long,
    ): PlaybackEndpointRecoveryResult = withContext(Dispatchers.IO) {
        val request = media.recoveryRequest ?: return@withContext PlaybackEndpointRecoveryResult.NotRecovered
        if (media.sourceType != PlaybackSourceType.Remote || !connectivityChecker.isOnline()) {
            return@withContext PlaybackEndpointRecoveryResult.NotRecovered
        }
        if (!PlaybackEndpointFailureClassifier.shouldAttemptRecovery(error, attemptCount)) {
            return@withContext PlaybackEndpointRecoveryResult.NotRecovered
        }
        mutex.withLock {
            val key = request.providerId.value
            val now = nowMs()
            val previousAttempt = lastAttemptByProvider[key]
            if (previousAttempt != null && now - previousAttempt < cooldownMs) {
                acceptanceTrace("cooldown-block elapsedMs=${now - previousAttempt} cooldownMs=$cooldownMs")
                return@withLock PlaybackEndpointRecoveryResult.NotRecovered
            }
            lastAttemptByProvider[key] = now
            acceptanceTrace("cooldown-start cooldownMs=$cooldownMs")
            val before = providerUrl(request.providerId)
                ?: return@withLock PlaybackEndpointRecoveryResult.NotRecovered
            when (val validation = validateAccount(request.providerId)) {
                AccountValidationResult.Active -> {
                    val after = providerUrl(request.providerId)
                    if (after == null || after == before) return@withLock PlaybackEndpointRecoveryResult.NotRecovered
                    val recoveredUrl = resolveUrl(request)
                    PlaybackEndpointRecoveryResult.Retry(
                        media.copy(
                            url = recoveredUrl,
                            startPositionMs = if (media.isLive) 0L else positionMs.coerceAtLeast(0L),
                        ),
                    )
                }
                is AccountValidationResult.Blocked -> {
                    onAccountBlocked(validation.message)
                    PlaybackEndpointRecoveryResult.AccountBlocked(validation.message)
                }
                is AccountValidationResult.Retryable -> PlaybackEndpointRecoveryResult.NotRecovered
            }
        }
    }

    private fun acceptanceTrace(message: String) {
        if (com.iamskorpz.watchioiptv.BuildConfig.UPDATE_CHANNEL == "syntheticfailovertest") {
            println("WatchioFailoverTrace t=${System.nanoTime() / 1_000_000L} $message")
        }
    }
}
