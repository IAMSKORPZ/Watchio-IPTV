package com.iamskorpz.watchioiptv.core.diagnostics

import android.os.SystemClock

object QuickLoginBootstrapTrace {
    private var applyStartedAtMs: Long? = null

    @Synchronized
    fun start() {
        applyStartedAtMs = SystemClock.elapsedRealtime()
        mark("quicklogin_apply_started")
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    @Synchronized
    fun mark(name: String, stageStartedAtMs: Long? = null, metadata: String = "") {
        val start = applyStartedAtMs ?: return
        val now = SystemClock.elapsedRealtime()
        val duration = stageStartedAtMs?.let { " stage_duration_ms=${now - it}" }.orEmpty()
        val safeMetadata = metadata.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        println("QuickLoginTiming:$name elapsed_ms_since_apply_start=${now - start}$duration$safeMetadata")
    }

    @Synchronized
    fun finishUiReady() {
        mark("quicklogin_ui_ready")
    }

    @Synchronized
    fun finishDeferredSync() {
        mark("quicklogin_deferred_sync_completed")
        applyStartedAtMs = null
    }
}
