import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'app_theme.dart';

class ThemeStorage {
  static const String _themeKey = 'selected_theme';
  static const String _highlightKey = 'theme_highlight_color';
  static const String _secondaryKey = 'theme_secondary_color';
  static const String _backgroundKey = 'theme_background_color';
  static const String _surfaceKey = 'theme_surface_color';

  static Future<void> saveTheme(AppThemeType type) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_themeKey, type.name);
  }

  static Future<AppThemeType> loadTheme() async {
    final prefs = await SharedPreferences.getInstance();
    final themeName = prefs.getString(_themeKey);
    if (themeName == null) return AppThemeType.bingieNeon;
    return AppThemeType.values.firstWhere(
      (e) => e.name == themeName,
      orElse: () => AppThemeType.bingieNeon,
    );
  }

  static Future<void> saveColor(String key, Color color) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(key, color.toARGB32());
  }

  static Future<Color?> loadColor(String key) async {
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getInt(key);
    return value == null ? null : Color(value);
  }

  static Future<void> saveHighlightColor(Color color) =>
      saveColor(_highlightKey, color);
  static Future<void> saveSecondaryColor(Color color) =>
      saveColor(_secondaryKey, color);
  static Future<void> saveBackgroundColor(Color color) =>
      saveColor(_backgroundKey, color);
  static Future<void> saveSurfaceColor(Color color) =>
      saveColor(_surfaceKey, color);

  static Future<Color?> loadHighlightColor() => loadColor(_highlightKey);
  static Future<Color?> loadSecondaryColor() => loadColor(_secondaryKey);
  static Future<Color?> loadBackgroundColor() => loadColor(_backgroundKey);
  static Future<Color?> loadSurfaceColor() => loadColor(_surfaceKey);
}
