package com.iamskorpz.watchioiptv.core.player

interface PlaybackFailoverDiagnostics {
    fun trace(message: String)
}

object NoOpPlaybackFailoverDiagnostics : PlaybackFailoverDiagnostics {
    override fun trace(message: String) = Unit
}
