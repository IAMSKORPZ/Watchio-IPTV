package com.watchioiptv.nativeapp.ui

import com.watchioiptv.nativeapp.domain.model.InputMode

internal fun shouldRequireTvDoubleBackExit(
    inputMode: InputMode,
    route: String?,
    hasPreviousBackStackEntry: Boolean,
): Boolean = inputMode == InputMode.TvRemote && route == "home" && !hasPreviousBackStackEntry

internal class TvDoubleBackExitGate(
    private val timeoutMs: Long = 2_000L,
) {
    private var firstBackAtMs: Long? = null

    fun onBack(nowMs: Long): Boolean {
        val previous = firstBackAtMs
        firstBackAtMs = null
        if (previous != null && nowMs - previous in 0 until timeoutMs) return true

        firstBackAtMs = nowMs
        return false
    }

    fun reset() {
        firstBackAtMs = null
    }
}
