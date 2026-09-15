package com.iamskorpz.watchioiptv.core.util

interface WatchioClock {
    fun nowEpochMs(): Long
}

object SystemWatchioClock : WatchioClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
