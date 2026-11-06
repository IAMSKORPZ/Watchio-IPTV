package com.iamskorpz.watchioiptv.core.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

object PlaybackEndpointFailureClassifier {
    fun shouldAttemptRecovery(error: PlaybackFailure, attemptCount: Int): Boolean {
        val responseCode = error.responseCode
        if (responseCode != null) {
            if (responseCode in setOf(401, 403, 404, 410, 451)) return false
            return responseCode in 500..599 && attemptCount >= 2
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) return true
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) return true
        return error.cause?.causeChain().orEmpty().any {
            it is UnknownHostException || it is ConnectException || it is NoRouteToHostException ||
                it is SocketException || it is SocketTimeoutException || it is SSLException
        }
    }
}

private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

data class PlaybackFailure(val errorCode: Int, val cause: Throwable?, val responseCode: Int? = null)

fun PlaybackException.toPlaybackFailure(): PlaybackFailure = PlaybackFailure(
    errorCode = errorCode,
    cause = cause,
    responseCode = cause?.causeChain().orEmpty()
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode,
)

fun Throwable.toPlaybackFailure(): PlaybackFailure = PlaybackFailure(
    errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    cause = this,
    responseCode = causeChain()
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode,
)

sealed interface PlaybackEndpointRecoveryResult {
    data class Retry(val media: PlaybackMedia) : PlaybackEndpointRecoveryResult
    data class AccountBlocked(val message: String) : PlaybackEndpointRecoveryResult
    data object NotRecovered : PlaybackEndpointRecoveryResult
}

fun interface PlaybackEndpointRecovery {
    suspend fun recover(media: PlaybackMedia, error: PlaybackFailure, attemptCount: Int, positionMs: Long): PlaybackEndpointRecoveryResult
}

class PlaybackEndpointRecoveryGate {
    private val attempted = AtomicBoolean(false)

    fun tryStart(): Boolean = attempted.compareAndSet(false, true)

    fun hasStarted(): Boolean = attempted.get()

    fun reset() {
        attempted.set(false)
    }
}
