package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.ui.theme.WatchioThemeId
import com.watchioiptv.nativeapp.ui.theme.WatchioThemeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WatchioThemeStateTest {
    @Test
    fun availableThemesUseDistinctPalettes() {
        assertEquals(
            listOf(
                WatchioThemeId.WatchioDefault,
                WatchioThemeId.Dark,
                WatchioThemeId.Purple,
                WatchioThemeId.Blue,
            ),
            WatchioThemeState.Available.map { it.id },
        )

        assertNotEquals(
            WatchioThemeState.fromId(WatchioThemeId.WatchioDefault).surfaceBase,
            WatchioThemeState.fromId(WatchioThemeId.Blue).surfaceBase,
        )
    }

    @Test
    fun persistedThemeIdsFallbackSafely() {
        assertEquals(WatchioThemeId.Purple, WatchioThemeId.fromPersisted("purple"))
        assertEquals(WatchioThemeId.WatchioDefault, WatchioThemeId.fromPersisted("old-theme"))
        assertEquals(WatchioThemeId.WatchioDefault, WatchioThemeId.fromPersisted(null))
    }
}
