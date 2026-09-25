package com.iamskorpz.watchioiptv

import androidx.compose.ui.graphics.Color
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
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeState
import com.iamskorpz.watchioiptv.ui.components.headerBackIconColor
import com.iamskorpz.watchioiptv.ui.components.WatchioSurfaceRole
import com.iamskorpz.watchioiptv.ui.components.semanticOutlineWidthDp
import com.iamskorpz.watchioiptv.ui.components.semanticBorderWidthDp
import com.iamskorpz.watchioiptv.ui.theme.resolveWatchioThemeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchioAppearanceTest {
    @Test fun semanticOutlineWidthsRemainIndependent() {
        val base = WatchioThemeDefinition.WatchioDefault
        val cardOnly = base.copy(cards = base.cards.copy(outlineWidthDp = 2f))
        assertEquals(2f, semanticOutlineWidthDp(WatchioSurfaceRole.Card, cardOnly))
        assertEquals(0f, semanticOutlineWidthDp(WatchioSurfaceRole.Panel, cardOnly))
        assertEquals(0f, semanticOutlineWidthDp(WatchioSurfaceRole.Control, cardOnly))

        val panelOnly = base.copy(surfaces = base.surfaces.copy(outlineWidthDp = 2f))
        assertEquals(0f, semanticOutlineWidthDp(WatchioSurfaceRole.Card, panelOnly))
        assertEquals(2f, semanticOutlineWidthDp(WatchioSurfaceRole.Panel, panelOnly))
        assertEquals(0f, semanticOutlineWidthDp(WatchioSurfaceRole.Control, panelOnly))

        val controlOnly = base.copy(controls = base.controls.copy(outlineWidthDp = 2f))
        assertEquals(0f, semanticOutlineWidthDp(WatchioSurfaceRole.Card, controlOnly))
        assertEquals(0f, semanticOutlineWidthDp(WatchioSurfaceRole.Panel, controlOnly))
        assertEquals(2f, semanticOutlineWidthDp(WatchioSurfaceRole.Control, controlOnly))

        WatchioSurfaceRole.entries.forEach { assertEquals(0f, semanticOutlineWidthDp(it, base)) }
        assertTrue(base.focus.outlineWidthDp > 0f)
    }

    @Test fun customCardBorderPrecedenceKeepsNormalFocusAndSelectionIndependent() {
        val base = WatchioThemeDefinition.WatchioDefault
        val outlined = base.copy(cards = base.cards.copy(outlineWidthDp = 2f))

        assertEquals(0f, semanticBorderWidthDp(WatchioSurfaceRole.Card, base, focused = false))
        assertEquals(2f, semanticBorderWidthDp(WatchioSurfaceRole.Card, outlined, focused = false))
        assertEquals(base.focus.outlineWidthDp, semanticBorderWidthDp(WatchioSurfaceRole.Card, base, focused = true))
        assertEquals(
            1.5f,
            semanticBorderWidthDp(
                WatchioSurfaceRole.Card,
                base,
                focused = false,
                selected = true,
                selectedWidthDp = 1.5f,
            ),
        )
        assertEquals(0f, semanticBorderWidthDp(WatchioSurfaceRole.Card, base.copy(surfaces = base.surfaces.copy(outlineWidthDp = 2f)), focused = false))
        assertEquals(0f, semanticBorderWidthDp(WatchioSurfaceRole.Card, base.copy(controls = base.controls.copy(outlineWidthDp = 2f)), focused = false))
    }

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

    @Test fun nonDefaultAppearanceDrivesRealApplicationSemanticPalette() {
        listOf(WatchioBuiltInThemes.Light, WatchioBuiltInThemes.AmoledBlack, WatchioBuiltInThemes.Ocean).forEach { preset ->
            val resolved = resolveWatchioThemeState(WatchioThemeState(), preset)
            val colors = preset.colors
            assertEquals(colors.appBackground, resolved.surfaceBase.toAppearanceLong())
            assertEquals(colors.primaryPanel, resolved.surfaceCard.toAppearanceLong())
            assertEquals(colors.secondaryPanel, resolved.surfaceElevated.toAppearanceLong())
            assertEquals(colors.header, resolved.headerSurface.toAppearanceLong())
            assertEquals(colors.navigationBackground, resolved.navigationSurface.toAppearanceLong())
            assertEquals(colors.dialog, resolved.dialogSurface.toAppearanceLong())
            assertEquals(colors.cardBackground, resolved.cardSurface.toAppearanceLong())
            assertEquals(colors.cardOutline, resolved.cardOutline.toAppearanceLong())
            assertEquals(colors.selectedCardBackground, resolved.selectedCardSurface.toAppearanceLong())
            assertEquals(colors.buttonBackground, resolved.buttonSurface.toAppearanceLong())
            assertEquals(colors.buttonText, resolved.buttonText.toAppearanceLong())
            assertEquals(colors.badgeBackground, resolved.badgeSurface.toAppearanceLong())
            assertEquals(colors.badgeText, resolved.badgeText.toAppearanceLong())
            assertEquals(colors.focusOutline, resolved.focusBorder.toAppearanceLong())
            assertEquals(colors.focusedBackground, resolved.focusedSurface.toAppearanceLong())
            assertEquals(colors.focusedText, resolved.focusedContent.toAppearanceLong())
            assertEquals(colors.playerOverlay, resolved.playerOverlay.toAppearanceLong())
        }
    }

    @Test fun watchioDefaultPreservesLegacyVisualPalette() {
        val legacy = WatchioThemeState()
        assertEquals(legacy, resolveWatchioThemeState(legacy, WatchioThemeDefinition.WatchioDefault))
    }

    @Test fun lightThemeKeepsArtworkBackedDetailTextReadable() {
        val colors = WatchioThemeState.fromDefinition(WatchioBuiltInThemes.Light)
        assertEquals(Color.White, colors.artworkTextPrimary)
        assertEquals(Color(0xFFD1D5DB), colors.artworkTextSecondary)
    }

    @Test fun artworkHeaderBackControlUsesControlContentPalette() {
        listOf(
            WatchioThemeDefinition.WatchioDefault,
            WatchioBuiltInThemes.Light,
            WatchioBuiltInThemes.AmoledBlack,
            WatchioBuiltInThemes.Ocean,
        ).forEach { preset ->
            val colors = WatchioThemeState.fromDefinition(preset)
            assertEquals(colors.buttonText, headerBackIconColor(colors, focused = false))
            assertEquals(colors.focusedContent, headerBackIconColor(colors, focused = true))
            assertTrue(
                contrastRatio(colors.buttonText.toAppearanceLong(), colors.buttonSurface.toAppearanceLong()) >= 4.5f,
            )
        }
        val light = WatchioThemeState.fromDefinition(WatchioBuiltInThemes.Light)
        assertTrue(headerBackIconColor(light, focused = false) != light.artworkTextPrimary)
    }
}
