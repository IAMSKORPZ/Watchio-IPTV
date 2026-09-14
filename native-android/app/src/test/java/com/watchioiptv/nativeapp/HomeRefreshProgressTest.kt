package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.feature.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRefreshProgressTest {
    @Test
    fun progressStartsAtLeftAndOnlyCompletesWhenRefreshFinishes() {
        val provider = ProviderId("provider")
        val started = HomeViewModel.HomeRefreshStatus().start(provider, ContentType.Movie)
        assertTrue(started.isRefreshing(provider, ContentType.Movie))
        assertEquals(0.08f, started.progress(provider, ContentType.Movie))

        val finished = started.finish(provider, ContentType.Movie, success = true)
        assertTrue(!finished.isRefreshing(provider, ContentType.Movie))
        assertEquals(1f, finished.progress(provider, ContentType.Movie))
    }

    @Test
    fun duplicateStartDoesNotCreateDuplicateRefreshIdentity() {
        val provider = ProviderId("provider")
        val once = HomeViewModel.HomeRefreshStatus().start(provider, ContentType.Live)
        val twice = once.start(provider, ContentType.Live)
        assertEquals(1, twice.refreshing.size)
    }
}
