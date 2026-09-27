package com.iamskorpz.watchioiptv

import androidx.compose.ui.graphics.Color
import com.iamskorpz.watchioiptv.ui.icons.WatchioIconColors
import com.iamskorpz.watchioiptv.ui.icons.WatchioIconKind
import com.iamskorpz.watchioiptv.ui.icons.identityColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WatchioIconsTest {
    @Test
    fun requiredIconIdentitiesUseApprovedColours() {
        assertEquals(Color(0xFF1976D2), WatchioIconColors.LiveTv)
        assertEquals(Color(0xFF9146D8), WatchioIconColors.Movies)
        assertEquals(Color(0xFFF59E0B), WatchioIconColors.TvShows)
        assertEquals(Color(0xFF16A34A), WatchioIconColors.Football)
        assertEquals(Color(0xFF64748B), WatchioIconColors.Settings)
        assertEquals(Color(0xFFEF4444), WatchioIconColors.Favourite)
        assertEquals(Color(0xFF0891B2), WatchioIconColors.Search)
        assertEquals(Color(0xFFDB2777), WatchioIconColors.ComingSoon)
        assertEquals(Color(0xFF0D9488), WatchioIconColors.History)
    }

    @Test
    fun primarySectionsHaveDistinctShapesAndColours() {
        val primaryKinds = setOf(WatchioIconKind.LiveTv, WatchioIconKind.Movies, WatchioIconKind.TvShows)
        assertEquals(3, primaryKinds.size)
        assertNotEquals(WatchioIconKind.LiveTv.identityColor(), WatchioIconKind.Movies.identityColor())
        assertNotEquals(WatchioIconKind.Movies.identityColor(), WatchioIconKind.TvShows.identityColor())
        assertNotEquals(WatchioIconKind.LiveTv.identityColor(), WatchioIconKind.TvShows.identityColor())
    }

    @Test
    fun settingsIconUsesSettingsIdentityColor() {
        assertEquals(WatchioIconColors.Settings, WatchioIconKind.Settings.identityColor())
    }
}
