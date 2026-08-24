package com.watchioiptv.nativeapp.core.player

import androidx.media3.common.C
import androidx.media3.common.PlaybackException

object PlayerReliability {
    const val MaxAutomaticRetries = 3

    fun clampedSeekTarget(positionMs: Long, deltaMs: Long, durationMs: Long?, isLive: Boolean): Long {
        if (isLive) return positionMs.coerceAtLeast(0L)
        val target = (positionMs + deltaMs).coerceAtLeast(0L)
        return durationMs?.takeIf { it != C.TIME_UNSET && it >= 0L }?.let { target.coerceAtMost(it) } ?: target
    }

    fun shouldAutoRetry(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true
        else -> false
    }

    fun userMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Unable to connect to stream."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Stream returned an HTTP error."
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "This stream format is not supported on this device."
        else -> "Playback failed."
    }
}
