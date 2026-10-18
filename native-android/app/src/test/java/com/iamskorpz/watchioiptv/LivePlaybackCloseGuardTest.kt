package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.ui.LivePlaybackCloseGuard
import com.iamskorpz.watchioiptv.ui.LivePlaybackOrigin
import com.iamskorpz.watchioiptv.ui.LivePlaybackReturnBackGuard
import com.iamskorpz.watchioiptv.ui.livePlaybackOrigin
import com.iamskorpz.watchioiptv.ui.isLivePlaybackReturnTransition
import org.junit.Assert.assertEquals
import org.junit.Test

class LivePlaybackCloseGuardTest {
    @Test
    fun repeatedCloseInvokesNavigationOnce() {
        val guard = LivePlaybackCloseGuard()
        var closeCount = 0

        repeat(5) { guard.close { closeCount++ } }

        assertEquals(1, closeCount)
    }

    @Test
    fun rapidBackOnRestoredOriginIsConsumedOnce() {
        var now = 1_000L
        val guard = LivePlaybackReturnBackGuard(elapsedRealtime = { now })

        guard.arm(LivePlaybackOrigin.Sports)
        now += 80L

        assertEquals(true, guard.consumeIfProtected(LivePlaybackOrigin.Sports))
        assertEquals(false, guard.consumeIfProtected(LivePlaybackOrigin.Sports))
    }

    @Test
    fun sportsHandoffCanArmGuardBeforeFullscreenAttaches() {
        var now = 1_000L
        val guard = LivePlaybackReturnBackGuard(elapsedRealtime = { now })

        guard.arm(LivePlaybackOrigin.Sports)
        now = 1_200L

        assertEquals(true, guard.consumeIfProtected(LivePlaybackOrigin.Sports))
        assertEquals(false, guard.consumeIfProtected(LivePlaybackOrigin.Sports))
    }

    @Test
    fun normalBackAfterProtectionWindowIsNotConsumed() {
        var now = 1_000L
        val guard = LivePlaybackReturnBackGuard(elapsedRealtime = { now })

        guard.arm(LivePlaybackOrigin.Sports)
        now += 251L

        assertEquals(false, guard.consumeIfProtected(LivePlaybackOrigin.Sports))
    }

    @Test
    fun rapidBackDuringDestinationTransitionIsConsumedWithoutOriginHandler() {
        var now = 1_000L
        val guard = LivePlaybackReturnBackGuard(elapsedRealtime = { now })

        guard.arm(LivePlaybackOrigin.Sports)
        now += 120L

        assertEquals(true, guard.consumeIfTransitionProtected())
        assertEquals(false, guard.consumeIfTransitionProtected())
    }

    @Test
    fun transitionProtectionExpiresSoDeliberateBackDelegatesNormally() {
        var now = 1_000L
        val guard = LivePlaybackReturnBackGuard(elapsedRealtime = { now })

        guard.arm(LivePlaybackOrigin.Sports)
        now += 251L

        assertEquals(false, guard.consumeIfTransitionProtected())
    }

    @Test
    fun guardDoesNotConsumeBackForDifferentOrigin() {
        val guard = LivePlaybackReturnBackGuard(elapsedRealtime = { 1_000L })

        guard.arm(LivePlaybackOrigin.Sports)

        assertEquals(false, guard.consumeIfProtected(LivePlaybackOrigin.Live))
    }

    @Test
    fun routeArgumentRestoresEverySupportedOrigin() {
        assertEquals(LivePlaybackOrigin.Sports, livePlaybackOrigin("sports"))
        assertEquals(LivePlaybackOrigin.TvGuide, livePlaybackOrigin("tv-guide"))
        assertEquals(LivePlaybackOrigin.Live, livePlaybackOrigin("live"))
    }

    @Test
    fun missingOrUnknownOriginSafelyUsesLive() {
        assertEquals(LivePlaybackOrigin.Live, livePlaybackOrigin(null))
        assertEquals(LivePlaybackOrigin.Live, livePlaybackOrigin("unknown"))
    }

    @Test
    fun sportsBackIsConsumedWhileFullscreenRemainsVisibleDuringReturnTransition() {
        assertEquals(
            true,
            isLivePlaybackReturnTransition(
                origin = LivePlaybackOrigin.Sports,
                currentRoute = "sports",
                visibleRoutes = listOf("live/fullscreen?origin={origin}", "live", "sports"),
            ),
        )
    }

    @Test
    fun sportsBackWorksNormallyAfterFullscreenLeavesVisibleEntries() {
        assertEquals(
            false,
            isLivePlaybackReturnTransition(
                origin = LivePlaybackOrigin.Sports,
                currentRoute = "sports",
                visibleRoutes = listOf("sports"),
            ),
        )
    }
}
