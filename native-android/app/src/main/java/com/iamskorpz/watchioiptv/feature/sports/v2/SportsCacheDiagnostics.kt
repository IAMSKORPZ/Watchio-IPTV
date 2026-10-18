package com.iamskorpz.watchioiptv.feature.sports.v2

import java.util.concurrent.atomic.AtomicLong

object SportsCacheDiagnostics {
    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val apiCalls = AtomicLong()
    private val deduplicated = AtomicLong()
    private val lookupMs = AtomicLong()
    private val epgIndexMs = AtomicLong()

    fun cacheHit() { hits.incrementAndGet() }
    fun cacheMiss() { misses.incrementAndGet() }
    fun apiCall() { apiCalls.incrementAndGet() }
    fun duplicatePrevented() { deduplicated.incrementAndGet() }
    fun broadcasterLookup(durationMs: Long) { lookupMs.set(durationMs.coerceAtLeast(0)) }
    fun epgIndexBuild(durationMs: Long) { epgIndexMs.set(durationMs.coerceAtLeast(0)) }

    fun summary(): String = "Cache hits ${hits.get()} | misses ${misses.get()} | API calls ${apiCalls.get()} | duplicate requests prevented ${deduplicated.get()} | last broadcaster lookup ${lookupMs.get()} ms | last EPG index ${epgIndexMs.get()} ms"
}
