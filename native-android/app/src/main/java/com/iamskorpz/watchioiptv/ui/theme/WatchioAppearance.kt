package com.iamskorpz.watchioiptv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

const val WATCHIO_DEFAULT_THEME_ID = "watchio-default"
const val APPEARANCE_SCHEMA_VERSION = 1

@Serializable
data class WatchioAppearanceLibrary(
    val schemaVersion: Int = APPEARANCE_SCHEMA_VERSION,
    val activeThemeId: String = WATCHIO_DEFAULT_THEME_ID,
    val themes: List<WatchioThemeDefinition> = emptyList(),
) {
    fun normalized(): WatchioAppearanceLibrary {
        if (schemaVersion != APPEARANCE_SCHEMA_VERSION) return Default
        val valid = themes.map { it.normalized() }
            .filterNot { it.id == WATCHIO_DEFAULT_THEME_ID }
            .distinctBy { it.id }
        val active = activeThemeId.takeIf { id -> WatchioBuiltInThemes.byId(id) != null || valid.any { it.id == id } }
            ?: WATCHIO_DEFAULT_THEME_ID
        return copy(activeThemeId = active, themes = valid)
    }

    fun activeTheme(): WatchioThemeDefinition =
        WatchioBuiltInThemes.byId(activeThemeId) ?: themes.firstOrNull { it.id == activeThemeId } ?: WatchioThemeDefinition.WatchioDefault

    companion object {
        val Default = WatchioAppearanceLibrary()
    }
}

@Serializable
data class WatchioThemeDefinition(
    val schemaVersion: Int = APPEARANCE_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val colors: WatchioSemanticColors = WatchioSemanticColors(),
    val typography: WatchioTypographyStyle = WatchioTypographyStyle(),
    val surfaces: WatchioSurfaceStyle = WatchioSurfaceStyle(),
    val cards: WatchioCardStyle = WatchioCardStyle(),
    val controls: WatchioControlStyle = WatchioControlStyle(),
    val navigation: WatchioNavigationStyle = WatchioNavigationStyle(),
    val focus: WatchioFocusStyle = WatchioFocusStyle(),
    val layout: WatchioLayoutStyle = WatchioLayoutStyle(),
    val effects: WatchioEffectsStyle = WatchioEffectsStyle(),
    val background: WatchioBackgroundStyle = WatchioBackgroundStyle(),
) {
    val isDefault: Boolean get() = id == WATCHIO_DEFAULT_THEME_ID
    val isBuiltIn: Boolean get() = WatchioBuiltInThemes.byId(id) != null

    fun normalized(): WatchioThemeDefinition = copy(
        schemaVersion = APPEARANCE_SCHEMA_VERSION,
        id = id.trim().take(80).ifBlank { UUID.randomUUID().toString() },
        name = name.trim().replace(Regex("\\s+"), " ").take(40).ifBlank { "Custom Theme" },
        typography = typography.normalized(),
        surfaces = surfaces.normalized(),
        cards = cards.normalized(),
        controls = controls.normalized(),
        focus = focus.normalized(),
        layout = layout.normalized(),
        effects = effects.normalized(),
        background = background.normalized(),
    )

    fun duplicate(newName: String = "$name Copy") = copy(
        id = UUID.randomUUID().toString(),
        name = newName,
    ).normalized()

    companion object {
        val WatchioDefault = WatchioThemeDefinition(
            id = WATCHIO_DEFAULT_THEME_ID,
            name = "Watchio Default",
        )
    }
}

object WatchioBuiltInThemes {
    private fun preset(
        id: String,
        name: String,
        background: Long,
        panel: Long,
        raised: Long,
        accent: Long,
        primaryText: Long,
        secondaryText: Long,
        outline: Long,
        selected: Long,
        selectedText: Long,
        focus: Long,
    ) = WatchioThemeDefinition(
        id = id,
        name = name,
        colors = WatchioSemanticColors(
            appBackground = background, accent = accent, primaryText = primaryText,
            secondaryText = secondaryText, mutedText = secondaryText,
            primaryPanel = panel, secondaryPanel = raised, header = raised, sidebar = panel,
            dialog = raised, popup = raised, cardBackground = panel, cardOutline = outline,
            selectedCardBackground = selected, selectedCardOutline = focus,
            buttonBackground = raised, buttonText = primaryText, buttonOutline = outline,
            selectedButton = accent, selectedButtonText = selectedText,
            navigationBackground = panel, navigationIcon = secondaryText,
            navigationSelectedIcon = primaryText, navigationSelectedBackground = selected,
            badgeBackground = accent, badgeText = selectedText, focusOutline = focus,
            focusGlow = focus, focusedBackground = selected, focusedText = primaryText,
            playerAccent = accent, playerControl = primaryText, playerOverlay = background,
        ),
        surfaces = WatchioSurfaceStyle(outlineColor = outline, outlineWidthDp = 0f, cornerRadiusDp = 10f),
        cards = WatchioCardStyle(cornerRadiusDp = 10f, outlineWidthDp = 0f, posterCornerRadiusDp = 10f),
        controls = WatchioControlStyle(cornerRadiusDp = 10f, outlineWidthDp = 0f),
        focus = WatchioFocusStyle(outlineWidthDp = 3f, glowIntensity = 0.7f, scale = 1.04f),
        effects = WatchioEffectsStyle(elevationDp = 5f),
        background = WatchioBackgroundStyle(overlayColor = background),
    )

    val Midnight = preset("builtin-midnight", "Midnight", 0xFF050914, 0xFF0B1424, 0xFF13213A, 0xFF60A5FA, 0xFFF8FAFC, 0xFFB6C2D2, 0xFF29415F, 0xFF163A5F, 0xFF06111F, 0xFF93C5FD)
    val AmoledBlack = preset("builtin-amoled-black", "AMOLED Black", 0xFF000000, 0xFF070707, 0xFF111111, 0xFFFF3D9A, 0xFFFFFFFF, 0xFFBDBDBD, 0xFF333333, 0xFF29101D, 0xFF000000, 0xFFFFFFFF)
    val Ocean = preset("builtin-ocean", "Ocean", 0xFF031317, 0xFF082329, 0xFF0D343B, 0xFF26A69A, 0xFFF3FBFB, 0xFFA9C9CB, 0xFF24606A, 0xFF12484F, 0xFF031313, 0xFF80CBC4)
    val Crimson = preset("builtin-crimson", "Crimson", 0xFF0D080A, 0xFF1B1115, 0xFF28171D, 0xFFE54862, 0xFFFFF7F8, 0xFFD1B6BC, 0xFF63303C, 0xFF451C27, 0xFF160307, 0xFFFF8A9B)
    val PurpleNeon = preset("builtin-purple-neon", "Purple Neon", 0xFF090611, 0xFF160D24, 0xFF24133B, 0xFFB85CFF, 0xFFFFF8FF, 0xFFCDB9DD, 0xFF62318A, 0xFF351750, 0xFF12051D, 0xFFD89BFF)
    val Emerald = preset("builtin-emerald", "Emerald", 0xFF04100D, 0xFF0A211A, 0xFF113329, 0xFF34D399, 0xFFF5FFF9, 0xFFB0CFC1, 0xFF28604C, 0xFF174938, 0xFF03130C, 0xFF6EE7B7)
    val Light = preset("builtin-light", "Light", 0xFFF3F5F8, 0xFFFFFFFF, 0xFFE7EBF0, 0xFFB51662, 0xFF171923, 0xFF4E5565, 0xFF9CA3AF, 0xFFF5D8E5, 0xFFFFFFFF, 0xFF6D1FB3)
        .copy(effects = WatchioEffectsStyle(elevationDp = 2f, transitionIntensity = 0.8f))
    val Slate = preset("builtin-slate", "Slate", 0xFF111318, 0xFF1A1E26, 0xFF252B35, 0xFF94A3B8, 0xFFF8FAFC, 0xFFB6BEC9, 0xFF475569, 0xFF334155, 0xFF111318, 0xFFE2E8F0)

    val All: List<WatchioThemeDefinition> = listOf(
        WatchioThemeDefinition.WatchioDefault, Midnight, AmoledBlack, Ocean, Crimson, PurpleNeon, Emerald, Light, Slate,
    )
    fun byId(id: String): WatchioThemeDefinition? = All.firstOrNull { it.id == id }
}

@Serializable
data class WatchioSemanticColors(
    val appBackground: Long = 0xFF050712,
    val accent: Long = 0xFFFF3D9A,
    val primaryText: Long = 0xFFF8F8FC,
    val secondaryText: Long = 0xFFB7BAC8,
    val mutedText: Long = 0xFF8E92A8,
    val primaryPanel: Long = 0xFF0B1020,
    val secondaryPanel: Long = 0xFF101426,
    val header: Long = 0xFF101426,
    val sidebar: Long = 0xFF0B1020,
    val dialog: Long = 0xFF111327,
    val popup: Long = 0xFF111327,
    val cardBackground: Long = 0xFF0B1020,
    val cardOutline: Long = 0xFF7437D8,
    val selectedCardBackground: Long = 0xFF211133,
    val selectedCardOutline: Long = 0xFFD95CFF,
    val buttonBackground: Long = 0xFF101426,
    val buttonText: Long = 0xFFF8F8FC,
    val buttonOutline: Long = 0xFF7437D8,
    val selectedButton: Long = 0xFFFF3D9A,
    val selectedButtonText: Long = 0xFF050712,
    val navigationBackground: Long = 0xFF0B1020,
    val navigationIcon: Long = 0xFFB7BAC8,
    val navigationSelectedIcon: Long = 0xFFF8F8FC,
    val navigationSelectedBackground: Long = 0xFF211133,
    val badgeBackground: Long = 0xFFFF3D9A,
    val badgeText: Long = 0xFFF8F8FC,
    val focusOutline: Long = 0xFFFFFFFF,
    val focusGlow: Long = 0xFFD95CFF,
    val focusedBackground: Long = 0xFF211133,
    val focusedText: Long = 0xFFF8F8FC,
    val playerAccent: Long = 0xFFFF3D9A,
    val playerControl: Long = 0xFFF8F8FC,
    val playerOverlay: Long = 0xCC050712,
)

@Serializable
data class WatchioTypographyStyle(
    val displaySp: Float = 28f,
    val sectionSp: Float = 22f,
    val bodySp: Float = 14f,
    val metadataSp: Float = 12f,
    val buttonSp: Float = 14f,
    val navigationSp: Float = 12f,
    val displayWeight: FontWeightChoice = FontWeightChoice.Bold,
    val sectionWeight: FontWeightChoice = FontWeightChoice.Bold,
    val bodyWeight: FontWeightChoice = FontWeightChoice.Normal,
    val buttonWeight: FontWeightChoice = FontWeightChoice.SemiBold,
) {
    fun normalized() = copy(
        displaySp = displaySp.coerceIn(22f, 42f),
        sectionSp = sectionSp.coerceIn(18f, 32f),
        bodySp = bodySp.coerceIn(12f, 22f),
        metadataSp = metadataSp.coerceIn(10f, 18f),
        buttonSp = buttonSp.coerceIn(12f, 22f),
        navigationSp = navigationSp.coerceIn(10f, 20f),
    )
}

@Serializable enum class FontWeightChoice { Normal, Medium, SemiBold, Bold }

@Serializable
data class WatchioSurfaceStyle(
    val primaryOpacity: Float = 1f,
    val secondaryOpacity: Float = 1f,
    val dialogOpacity: Float = 1f,
    val headerOpacity: Float = 1f,
    val navigationOpacity: Float = 1f,
    val outlineColor: Long = 0xFF7437D8,
    val outlineWidthDp: Float = 0f,
    val cornerRadiusDp: Float = 8f,
) {
    fun normalized() = copy(
        primaryOpacity = primaryOpacity.coerceIn(0.2f, 1f),
        secondaryOpacity = secondaryOpacity.coerceIn(0.2f, 1f),
        dialogOpacity = dialogOpacity.coerceIn(0.4f, 1f),
        headerOpacity = headerOpacity.coerceIn(0.2f, 1f),
        navigationOpacity = navigationOpacity.coerceIn(0.2f, 1f),
        outlineWidthDp = outlineWidthDp.coerceIn(0f, 6f),
        cornerRadiusDp = cornerRadiusDp.coerceIn(0f, 32f),
    )
}

@Serializable
data class WatchioCardStyle(
    val opacity: Float = 1f,
    val cornerRadiusDp: Float = 8f,
    val outlineWidthDp: Float = 0f,
    val posterCornerRadiusDp: Float = 8f,
    val posterOverlayOpacity: Float = 0.65f,
) {
    fun normalized() = copy(
        opacity = opacity.coerceIn(0.2f, 1f),
        cornerRadiusDp = cornerRadiusDp.coerceIn(0f, 32f),
        outlineWidthDp = outlineWidthDp.coerceIn(0f, 6f),
        posterCornerRadiusDp = posterCornerRadiusDp.coerceIn(0f, 32f),
        posterOverlayOpacity = posterOverlayOpacity.coerceIn(0f, 1f),
    )
}

@Serializable
data class WatchioControlStyle(
    val cornerRadiusDp: Float = 8f,
    val outlineWidthDp: Float = 0f,
    val disabledOpacity: Float = 0.45f,
) {
    fun normalized() = copy(
        cornerRadiusDp = cornerRadiusDp.coerceIn(0f, 32f),
        outlineWidthDp = outlineWidthDp.coerceIn(0f, 6f),
        disabledOpacity = disabledOpacity.coerceIn(0.2f, 0.8f),
    )
}

@Serializable
data class WatchioNavigationStyle(val showLabels: Boolean = true)

@Serializable
data class WatchioFocusStyle(
    val outlineWidthDp: Float = 3f,
    val glowIntensity: Float = 0.7f,
    val scale: Float = 1.04f,
) {
    fun normalized() = copy(
        outlineWidthDp = outlineWidthDp.coerceIn(1f, 8f),
        glowIntensity = glowIntensity.coerceIn(0f, 1f),
        scale = scale.coerceIn(1f, 1.10f),
    )
}

@Serializable enum class DensityChoice { Compact, Standard, Spacious }
@Serializable enum class CardSizeChoice { Compact, Standard, Large }
@Serializable
data class WatchioLayoutStyle(
    val density: DensityChoice = DensityChoice.Standard,
    val cardSize: CardSizeChoice = CardSizeChoice.Standard,
    val spacingScale: Float = 1f,
    val uiScale: Float = 1f,
) {
    fun normalized() = copy(
        spacingScale = spacingScale.coerceIn(0.8f, 1.3f),
        uiScale = uiScale.coerceIn(0.9f, 1.15f),
    )
}

@Serializable enum class AnimationChoice { Full, Reduced, Off }
@Serializable
data class WatchioEffectsStyle(
    val animations: AnimationChoice = AnimationChoice.Full,
    val elevationDp: Float = 4f,
    val transitionIntensity: Float = 1f,
) {
    fun normalized() = copy(
        elevationDp = elevationDp.coerceIn(0f, 16f),
        transitionIntensity = transitionIntensity.coerceIn(0f, 1f),
    )
}

@Serializable enum class BackgroundScale { Fit, FillCrop, Stretch }
@Serializable enum class BackgroundAlignment { Center, Top, Bottom }
@Serializable
data class WatchioBackgroundStyle(
    val imageUri: String? = null,
    val imageOpacity: Float = 1f,
    val overlayColor: Long = 0xFF050712,
    val overlayOpacity: Float = 0f,
    val scale: BackgroundScale = BackgroundScale.FillCrop,
    val alignment: BackgroundAlignment = BackgroundAlignment.Center,
    val blurRadiusDp: Float = 0f,
) {
    fun normalized() = copy(
        imageUri = imageUri?.take(2_048),
        imageOpacity = imageOpacity.coerceIn(0f, 1f),
        overlayOpacity = overlayOpacity.coerceIn(0f, 1f),
        blurRadiusDp = blurRadiusDp.coerceIn(0f, 24f),
    )
}

object WatchioAppearanceCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(library: WatchioAppearanceLibrary): String = json.encodeToString(library.normalized())
    fun decode(value: String?): WatchioAppearanceLibrary = runCatching {
        require(!value.isNullOrBlank())
        json.decodeFromString<WatchioAppearanceLibrary>(value).normalized()
    }.getOrDefault(WatchioAppearanceLibrary.Default)
}

fun Long.toComposeColor(): Color = Color(toInt())
fun Color.toAppearanceLong(): Long = toArgb().toUInt().toLong()

fun parseHexColor(value: String): Long? {
    val raw = value.trim().removePrefix("#")
    if (raw.length !in setOf(6, 8) || raw.any { it.digitToIntOrNull(16) == null }) return null
    val argb = if (raw.length == 6) "FF$raw" else raw
    return argb.toULong(16).toLong()
}

fun Long.toHexColor(includeAlpha: Boolean = true): String {
    val value = toULong() and 0xFFFFFFFFu
    return if (includeAlpha) "#%08X".format(value.toLong()) else "#%06X".format((value and 0xFFFFFFu).toLong())
}

fun contrastRatio(foreground: Long, background: Long): Float {
    fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((color ushr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    val a = luminance(foreground)
    val b = luminance(background)
    return ((max(a, b) + 0.05) / (min(a, b) + 0.05)).toFloat()
}

fun WatchioThemeDefinition.hasLowContrast(): Boolean = with(colors) {
    contrastRatio(primaryText, appBackground) < 4.5f ||
        contrastRatio(primaryText, primaryPanel) < 4.5f ||
        contrastRatio(secondaryText, primaryPanel) < 4.5f ||
        contrastRatio(buttonText, buttonBackground) < 4.5f ||
        contrastRatio(badgeText, badgeBackground) < 3f ||
        contrastRatio(focusOutline, focusedBackground) < 3f ||
        contrastRatio(navigationSelectedIcon, navigationSelectedBackground) < 3f
}

data class EditorHeaderFocusPalette(
    val background: Long,
    val content: Long,
    val outline: Long,
)

/** Fixed safe treatment for critical editor actions; never rewrites the draft or saved theme. */
fun editorHeaderFocusPalette(): EditorHeaderFocusPalette = EditorHeaderFocusPalette(
    background = 0xFF171717,
    content = 0xFFFFFFFF,
    outline = 0xFFFFD740,
)
