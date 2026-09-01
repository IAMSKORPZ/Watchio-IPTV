package com.watchioiptv.nativeapp.core.player

/** Keeps Android's display awake only while Media3 reports active playback. */
fun shouldKeepScreenOn(playerState: WatchioPlayerState): Boolean =
    playerState is WatchioPlayerState.Playing
