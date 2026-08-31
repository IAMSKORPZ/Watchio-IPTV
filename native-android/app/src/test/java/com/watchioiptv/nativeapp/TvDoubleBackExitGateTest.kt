package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.ui.TvDoubleBackExitGate
import com.watchioiptv.nativeapp.ui.shouldRequireTvDoubleBackExit
import com.watchioiptv.nativeapp.domain.model.InputMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDoubleBackExitGateTest {
    @Test
    fun onlyTvRootWithoutBackStackRequiresDoubleBack() {
        assertTrue(shouldRequireTvDoubleBackExit(InputMode.TvRemote, "home", hasPreviousBackStackEntry = false))
        assertFalse(shouldRequireTvDoubleBackExit(InputMode.Touch, "home", hasPreviousBackStackEntry = false))
        assertFalse(shouldRequireTvDoubleBackExit(InputMode.Auto, "home", hasPreviousBackStackEntry = false))
        assertFalse(shouldRequireTvDoubleBackExit(InputMode.TvRemote, "series/details/1", hasPreviousBackStackEntry = true))
    }

    @Test
    fun firstBackArmsExit() {
        assertFalse(TvDoubleBackExitGate().onBack(nowMs = 1_000L))
    }

    @Test
    fun secondBackWithinTimeoutExits() {
        val gate = TvDoubleBackExitGate()

        gate.onBack(nowMs = 1_000L)

        assertTrue(gate.onBack(nowMs = 2_999L))
    }

    @Test
    fun secondBackAtTimeoutArmsAgain() {
        val gate = TvDoubleBackExitGate()

        gate.onBack(nowMs = 1_000L)

        assertFalse(gate.onBack(nowMs = 3_000L))
        assertTrue(gate.onBack(nowMs = 4_000L))
    }

    @Test
    fun resetPreventsStaleExit() {
        val gate = TvDoubleBackExitGate()

        gate.onBack(nowMs = 1_000L)
        gate.reset()

        assertFalse(gate.onBack(nowMs = 1_100L))
    }
}
