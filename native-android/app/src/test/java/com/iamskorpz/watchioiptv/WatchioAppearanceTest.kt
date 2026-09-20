package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.ui.theme.APPEARANCE_SCHEMA_VERSION
import com.iamskorpz.watchioiptv.ui.theme.WATCHIO_DEFAULT_THEME_ID
import com.iamskorpz.watchioiptv.ui.theme.WatchioAppearanceCodec
import com.iamskorpz.watchioiptv.ui.theme.WatchioAppearanceLibrary
import com.iamskorpz.watchioiptv.ui.theme.WatchioBuiltInThemes
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeDefinition
import com.iamskorpz.watchioiptv.feature.settings.AppearancePreviewScene
import com.iamskorpz.watchioiptv.feature.settings.AppearancePreviewTarget
import com.iamskorpz.watchioiptv.feature.settings.AppearanceSection
import com.iamskorpz.watchioiptv.feature.settings.previewScene
import com.iamskorpz.watchioiptv.feature.settings.previewTargetForSetting
import com.iamskorpz.watchioiptv.ui.theme.contrastRatio
import com.iamskorpz.watchioiptv.ui.theme.hasLowContrast
import com.iamskorpz.watchioiptv.ui.theme.editorHeaderFocusPalette
import com.iamskorpz.watchioiptv.ui.theme.parseHexColor
import com.iamskorpz.watchioiptv.ui.theme.toHexColor
import com.iamskorpz.watchioiptv.ui.theme.toComposeColor
import com.iamskorpz.watchioiptv.ui.theme.toAppearanceLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchioAppearanceTest {
    @Test fun defaultThemeIsImmutableIdentity() {
        assertEquals(WATCHIO_DEFAULT_THEME_ID, WatchioThemeDefinition.WatchioDefault.id)
        assertEquals("Watchio Default", WatchioThemeDefinition.WatchioDefault.name)
    }

    @Test fun persistenceRoundTripKeepsTheme() {
        val custom = WatchioThemeDefinition.WatchioDefault.duplicate("Living Room")
        val source = WatchioAppearanceLibrary(activeThemeId = custom.id, themes = listOf(custom))
        assertEquals(source.normalized(), WatchioAppearanceCodec.decode(WatchioAppearanceCodec.encode(source)))
    }

    @Test fun corruptDataFallsBackToDefault() {
        assertEquals(WatchioAppearanceLibrary.Default, WatchioAppearanceCodec.decode("not-json"))
    }

    @Test fun unknownSchemaFallsBackToDefault() {
        val raw = """{"schemaVersion":999,"activeThemeId":"bad","themes":[]}"""
        assertEquals(WatchioAppearanceLibrary.Default, WatchioAppearanceCodec.decode(raw))
    }

    @Test fun colorAndAlphaSerializeExactly() {
        val color = parseHexColor("#8044AAFF")
        assertEquals("#8044AAFF", color?.toHexColor())
        assertNull(parseHexColor("#XYZ"))
    }

    @Test fun argbConvertsToComposeAndBackWithoutPackedColorSpaceBits() {
        val argb = 0x8044AAFFL
        assertEquals(argb, argb.toComposeColor().toAppearanceLong())
    }

    @Test fun typographyBoundsNormalize() {
        val value = WatchioThemeDefinition.WatchioDefault.copy(
            typography = WatchioThemeDefinition.WatchioDefault.typography.copy(bodySp = 200f, metadataSp = 1f),
        ).normalized()
        assertEquals(22f, value.typography.bodySp)
        assertEquals(10f, value.typography.metadataSp)
    }

    @Test fun outlineAndRadiusBoundsNormalize() {
        val value = WatchioThemeDefinition.WatchioDefault.copy(
            cards = WatchioThemeDefinition.WatchioDefault.cards.copy(outlineWidthDp = 99f, cornerRadiusDp = -4f),
        ).normalized()
        assertEquals(6f, value.cards.outlineWidthDp)
        assertEquals(0f, value.cards.cornerRadiusDp)
    }

    @Test fun focusScaleBoundsNormalize() {
        assertEquals(1.10f, WatchioThemeDefinition.WatchioDefault.copy(focus = WatchioThemeDefinition.WatchioDefault.focus.copy(scale = 2f)).normalized().focus.scale)
    }

    @Test fun savedThemeIdsAreStableAndDuplicatesAreUnique() {
        val first = WatchioThemeDefinition.WatchioDefault.duplicate("One")
        val renamed = first.copy(name = "Renamed").normalized()
        val duplicate = renamed.duplicate()
        assertEquals(first.id, renamed.id)
        assertNotEquals(first.id, duplicate.id)
    }

    @Test fun invalidActiveThemeFallsBackToDefault() {
        assertEquals(WATCHIO_DEFAULT_THEME_ID, WatchioAppearanceLibrary(activeThemeId = "missing").normalized().activeThemeId)
    }

    @Test fun customThemeCanBeRemovedWithoutDeletingDefault() {
        val custom = WatchioThemeDefinition.WatchioDefault.duplicate()
        val library = WatchioAppearanceLibrary(activeThemeId = custom.id, themes = listOf(custom))
        val removed = library.copy(activeThemeId = WATCHIO_DEFAULT_THEME_ID, themes = library.themes.filterNot { it.id == custom.id }).normalized()
        assertTrue(removed.themes.isEmpty())
        assertEquals(WATCHIO_DEFAULT_THEME_ID, removed.activeThemeId)
    }

    @Test fun nameIsBoundedAndSanitized() {
        val theme = WatchioThemeDefinition.WatchioDefault.duplicate("  A   very   spaced ${"x".repeat(80)} ")
        assertTrue(theme.name.length <= 40)
        assertFalse(theme.name.contains("  "))
    }

    @Test fun lowContrastIsDetected() {
        val dark = 0xFF111111L
        val theme = WatchioThemeDefinition.WatchioDefault.copy(colors = WatchioThemeDefinition.WatchioDefault.colors.copy(primaryText = dark, appBackground = dark))
        assertTrue(theme.hasLowContrast())
        assertEquals(1f, contrastRatio(dark, dark), 0.001f)
    }

    @Test fun defaultSchemaIsCurrent() {
        assertEquals(APPEARANCE_SCHEMA_VERSION, WatchioAppearanceLibrary.Default.schemaVersion)
    }

    @Test fun defaultThemeHasNoLowContrastWarning() {
        assertFalse(WatchioThemeDefinition.WatchioDefault.hasLowContrast())
    }

    @Test fun builtInPresetIdsAreUniqueAndDeterministic() {
        val ids = WatchioBuiltInThemes.All.map { it.id }
        assertEquals(9, ids.size)
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(WatchioBuiltInThemes.All, WatchioBuiltInThemes.All)
    }

    @Test fun everyBuiltInRoundTripsWithoutBecomingStoredCustomData() {
        WatchioBuiltInThemes.All.forEach { preset ->
            val library = WatchioAppearanceLibrary(activeThemeId = preset.id)
            val decoded = WatchioAppearanceCodec.decode(WatchioAppearanceCodec.encode(library))
            assertEquals(preset, decoded.activeTheme())
            assertTrue(decoded.themes.isEmpty())
        }
    }

    @Test fun builtInsAreTemplatesAndDuplicateGetsCustomIdentity() {
        WatchioBuiltInThemes.All.forEach { preset ->
            assertTrue(preset.isBuiltIn)
            val custom = preset.duplicate()
            assertFalse(custom.isBuiltIn)
            assertNotEquals(preset.id, custom.id)
            assertEquals(preset.colors, custom.colors)
        }
    }

    @Test fun allBuiltInsPassCoreContrastChecks() {
        WatchioBuiltInThemes.All.forEach { preset -> assertFalse(preset.name, preset.hasLowContrast()) }
    }

    @Test fun previewSceneAndTargetMappingsAreSemantic() {
        assertEquals(AppearancePreviewScene.Panels, AppearanceSection.Panels.previewScene())
        assertEquals(AppearancePreviewScene.TvFocus, AppearanceSection.TvFocus.previewScene())
        assertEquals(AppearancePreviewTarget.PrimaryPanel, previewTargetForSetting("Panel opacity"))
        assertEquals(AppearancePreviewTarget.Header, previewTargetForSetting("Header"))
        assertEquals(AppearancePreviewTarget.CardOutline, previewTargetForSetting("Card outline"))
        assertEquals(AppearancePreviewTarget.PrimaryText, previewTargetForSetting("Primary text"))
        assertEquals(AppearancePreviewTarget.Badge, previewTargetForSetting("Badge"))
        assertEquals(AppearancePreviewTarget.FocusOutline, previewTargetForSetting("Focus outline"))
    }

    @Test fun editorHeaderFocusPaletteIsVisibleForDarkAndLightPresets() {
        val palette = editorHeaderFocusPalette()
        listOf(
            WatchioThemeDefinition.WatchioDefault,
            WatchioBuiltInThemes.AmoledBlack,
            WatchioBuiltInThemes.Light,
        ).forEach { preset ->
            assertTrue(preset.name, contrastRatio(palette.content, palette.background) >= 4.5f)
            assertTrue(preset.name, contrastRatio(palette.outline, palette.background) >= 3f)
        }
    }

    @Test fun editorSafeFocusFallbackDoesNotMutateLowContrastTheme() {
        val unsafe = WatchioThemeDefinition.WatchioDefault.copy(
            colors = WatchioThemeDefinition.WatchioDefault.colors.copy(
                focusOutline = 0xFF111111,
                focusedBackground = 0xFF111111,
                focusedText = 0xFF111111,
            ),
        )
        val before = unsafe.copy()
        val palette = editorHeaderFocusPalette()
        assertTrue(contrastRatio(palette.content, palette.background) >= 4.5f)
        assertTrue(contrastRatio(palette.outline, palette.background) >= 3f)
        assertEquals(before, unsafe)
    }
}
