package com.watchioiptv.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color

enum class WatchioThemeId(val persisted: String, val label: String) {
    WatchioDefault("watchio-default", "Watchio Default"),
    Dark("dark", "Dark"),
    Purple("purple", "Purple"),
    Blue("blue", "Blue");

    companion object {
        fun fromPersisted(value: String?): WatchioThemeId =
            entries.firstOrNull { it.persisted == value } ?: WatchioDefault
    }
}

data class WatchioThemeState(
    val id: WatchioThemeId = WatchioThemeId.WatchioDefault,
    val surfaceBase: Color = Color(0xFF050712),
    val surfaceCard: Color = Color(0xFF0B1020),
    val surfaceElevated: Color = Color(0xFF101426),
    val surfaceStatus: Color = Color(0xFF111327),
    val textPrimary: Color = Color(0xFFF8F8FC),
    val textSecondary: Color = Color(0xFFB7BAC8),
    val textMuted: Color = Color(0xFF8E92A8),
    val liveTvAccent: Color = Color(0xFFFF3D9A),
    val liveTvAccentBright: Color = Color(0xFFFF58B0),
    val liveTvAccentDim: Color = Color(0xFFB51F70),
    val moviesAccent: Color = Color(0xFFA855F7),
    val moviesAccentBright: Color = Color(0xFFC45CFF),
    val moviesAccentDim: Color = Color(0xFF7437D8),
    val seriesAccent: Color = Color(0xFF20D9D2),
    val seriesAccentBright: Color = Color(0xFF39EEE5),
    val seriesAccentDim: Color = Color(0xFF129C9A),
    val focusBorder: Color = Color.White,
    val focusGlow: Color = Color(0xFFD95CFF),
) {
    companion object {
        val Available: List<WatchioThemeState> = listOf(
            WatchioThemeState(),
            WatchioThemeState(
                id = WatchioThemeId.Dark,
                surfaceBase = Color(0xFF050506),
                surfaceCard = Color(0xFF111114),
                surfaceElevated = Color(0xFF1A1B20),
                surfaceStatus = Color(0xFF202127),
                liveTvAccent = Color(0xFFE84F7D),
                liveTvAccentBright = Color(0xFFFF6C98),
                liveTvAccentDim = Color(0xFF9E2E50),
                moviesAccent = Color(0xFFB5BCCB),
                moviesAccentBright = Color(0xFFD7DCE7),
                moviesAccentDim = Color(0xFF727987),
                seriesAccent = Color(0xFF4FD1A5),
                seriesAccentBright = Color(0xFF76E5BE),
                seriesAccentDim = Color(0xFF247D63),
                focusGlow = Color(0xFFE6E8EF),
            ),
            WatchioThemeState(
                id = WatchioThemeId.Purple,
                surfaceBase = Color(0xFF090511),
                surfaceCard = Color(0xFF160D24),
                surfaceElevated = Color(0xFF211133),
                surfaceStatus = Color(0xFF2B1640),
                liveTvAccent = Color(0xFFFF4F93),
                liveTvAccentBright = Color(0xFFFF78B0),
                liveTvAccentDim = Color(0xFFAA285E),
                moviesAccent = Color(0xFF8B5CF6),
                moviesAccentBright = Color(0xFFA78BFA),
                moviesAccentDim = Color(0xFF5B35B4),
                seriesAccent = Color(0xFF2DD4BF),
                seriesAccentBright = Color(0xFF5EEAD4),
                seriesAccentDim = Color(0xFF14897E),
                focusGlow = Color(0xFFC084FC),
            ),
            WatchioThemeState(
                id = WatchioThemeId.Blue,
                surfaceBase = Color(0xFF041018),
                surfaceCard = Color(0xFF0A1B28),
                surfaceElevated = Color(0xFF102A3A),
                surfaceStatus = Color(0xFF123345),
                liveTvAccent = Color(0xFF38BDF8),
                liveTvAccentBright = Color(0xFF7DD3FC),
                liveTvAccentDim = Color(0xFF0E7490),
                moviesAccent = Color(0xFF60A5FA),
                moviesAccentBright = Color(0xFF93C5FD),
                moviesAccentDim = Color(0xFF2563EB),
                seriesAccent = Color(0xFF22D3EE),
                seriesAccentBright = Color(0xFF67E8F9),
                seriesAccentDim = Color(0xFF0891B2),
                focusGlow = Color(0xFFBAE6FD),
            ),
        )

        fun fromId(id: WatchioThemeId): WatchioThemeState =
            Available.firstOrNull { it.id == id } ?: Available.first()
    }
}
