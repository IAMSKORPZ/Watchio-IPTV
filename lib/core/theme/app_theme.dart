import 'package:flutter/material.dart';
import 'theme_extensions.dart';

enum AppThemeType {
  bingieNeon,
  emerald,
  crimson,
  ocean,
  gold,
  midnight,
  amoled,
  custom,
}

class AppTheme {
  static const Color defaultBackground = Color(0xFF050712);
  static const Color defaultSurface = Color(0xFF0B1020);
  static const Color defaultElevatedSurface = Color(0xFF101426);
  static const Color defaultStatusSurface = Color(0xFF111327);
  static const Color defaultBorder = Color(0xFF30354D);
  static const Color defaultTextPrimary = Color(0xFFF8F8FC);
  static const Color defaultTextSecondary = Color(0xFFB7BAC8);
  static const Color defaultTextMuted = Color(0xFF8E92A8);
  static const Color primaryPink = Color(0xFFFF3D9A);
  static const Color brightPink = Color(0xFFFF58B0);
  static const Color deepPink = Color(0xFFB51F70);
  static const Color primaryPurple = Color(0xFFA855F7);
  static const Color brightPurple = Color(0xFFC45CFF);
  static const Color deepPurple = Color(0xFF7437D8);
  static const Color primaryTurquoise = Color(0xFF20D9D2);
  static const Color brightTurquoise = Color(0xFF39EEE5);
  static const Color deepTurquoise = Color(0xFF129C9A);
  static const Color defaultFocus = Colors.white;
  static const Color defaultFocusGlow = Color(0xFFD95CFF);

  static ThemeData getTheme(
    AppThemeType type, {
    Color? customHighlight,
    Color? customSecondary,
    Color? customBackground,
    Color? customSurface,
  }) {
    final backgroundOverride = customBackground;
    final surfaceOverride = customSurface;
    switch (type) {
      case AppThemeType.custom:
        return _buildTheme(
          primary: customHighlight ?? primaryPink,
          secondary: customSecondary ?? primaryTurquoise,
          background: backgroundOverride ?? defaultBackground,
          surface: surfaceOverride ?? defaultSurface,
        );
      case AppThemeType.emerald:
        return _buildTheme(
          primary: const Color(0xFF0BA360),
          secondary: const Color(0xFF3CBA92),
          background: backgroundOverride ?? const Color(0xFF0A0E21),
          surface: surfaceOverride ?? const Color(0xFF1D1E33),
        );
      case AppThemeType.crimson:
        return _buildTheme(
          primary: const Color(0xFFFF0844),
          secondary: const Color(0xFFFFB199),
          background: backgroundOverride ?? const Color(0xFF0A0E21),
          surface: surfaceOverride ?? const Color(0xFF1D1E33),
        );
      case AppThemeType.ocean:
        return _buildTheme(
          primary: const Color(0xFF2575FC),
          secondary: const Color(0xFF6A11CB),
          background: backgroundOverride ?? const Color(0xFF0A0E21),
          surface: surfaceOverride ?? const Color(0xFF1D1E33),
        );
      case AppThemeType.gold:
        return _buildTheme(
          primary: const Color(0xFFF6D365),
          secondary: const Color(0xFFFDA085),
          background: backgroundOverride ?? const Color(0xFF0A0E21),
          surface: surfaceOverride ?? const Color(0xFF1D1E33),
        );
      case AppThemeType.midnight:
        return _buildTheme(
          primary: const Color(0xFF243B55),
          secondary: const Color(0xFF141E30),
          background: backgroundOverride ?? const Color(0xFF0A0E21),
          surface: surfaceOverride ?? const Color(0xFF1D1E33),
        );
      case AppThemeType.amoled:
        return _buildTheme(
          primary: const Color(0xFF6A11CB),
          secondary: const Color(0xFF2575FC),
          background: backgroundOverride ?? Colors.black,
          surface: surfaceOverride ?? const Color(0xFF121212),
        );
      case AppThemeType.bingieNeon:
        return _buildTheme(
          primary: primaryPink,
          secondary: primaryTurquoise,
          background: backgroundOverride ?? defaultBackground,
          surface: surfaceOverride ?? defaultSurface,
        );
    }
  }

  static ThemeData _buildTheme({
    required Color primary,
    required Color secondary,
    Color background = defaultBackground,
    Color surface = defaultSurface,
  }) {
    final primaryGradient = LinearGradient(
      colors: [primary, secondary],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    );
    final onPrimary =
        ThemeData.estimateBrightnessForColor(primary) == Brightness.dark
        ? Colors.white
        : Colors.black;
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      primaryColor: primary,
      colorScheme: ColorScheme.dark(
        primary: primary,
        secondary: secondary,
        surface: surface,
        onSurface: defaultTextPrimary,
        outline: defaultBorder,
        primaryContainer: primary.withValues(alpha: 0.22),
        secondaryContainer: secondary.withValues(alpha: 0.2),
        onPrimary: onPrimary,
        onSecondary: Colors.white,
      ),
      scaffoldBackgroundColor: background,
      cardTheme: CardThemeData(
        color: surface,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      extensions: [
        WatchioThemeExtension(
          primaryGradient: primaryGradient,
          secondaryGradient: LinearGradient(
            colors: [secondary, primary],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          panelGradient: LinearGradient(
            colors: [
              Color.alphaBlend(primary.withValues(alpha: 0.22), surface),
              Color.alphaBlend(
                secondary.withValues(alpha: 0.14),
                defaultElevatedSurface,
              ),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          glassColor: defaultElevatedSurface.withValues(alpha: 0.72),
          glassBorder: defaultBorder.withValues(alpha: 0.62),
          highlightColor: primary,
          glowColor: secondary,
          panelColor: surface,
        ),
      ],
      fontFamily: 'Roboto',
    );
  }
}
