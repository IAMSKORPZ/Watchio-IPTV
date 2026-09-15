package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.core.player.shouldKeepScreenOn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackKeepScreenOnTest {
    @Test fun playingKeepsScreenOn() = assertTrue(shouldKeepScreenOn(WatchioPlayerState.Playing(WatchioPlayerState.Idle().metadata)))
    @Test fun nonPlayingStatesReleaseScreenOn() {
        assertFalse(shouldKeepScreenOn(WatchioPlayerState.Idle()))
        assertFalse(shouldKeepScreenOn(WatchioPlayerState.Buffering(WatchioPlayerState.Idle().metadata)))
        assertFalse(shouldKeepScreenOn(WatchioPlayerState.Ended(WatchioPlayerState.Idle().metadata)))
    }
}
