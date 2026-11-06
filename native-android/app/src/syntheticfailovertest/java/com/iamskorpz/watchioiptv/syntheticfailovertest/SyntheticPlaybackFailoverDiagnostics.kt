package com.iamskorpz.watchioiptv.syntheticfailovertest

import com.iamskorpz.watchioiptv.core.player.PlaybackFailoverDiagnostics

internal object SyntheticPlaybackFailoverDiagnostics : PlaybackFailoverDiagnostics {
    override fun trace(message: String) {
        println("WatchioFailoverTrace t=${System.nanoTime() / 1_000_000L} $message")
    }
}
