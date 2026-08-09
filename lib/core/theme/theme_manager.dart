import 'package:flutter/material.dart';
import '../../repositories/user_preferences.dart';
import 'app_theme.dart';
import 'theme_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ThemeManager extends ChangeNotifier {
  AppThemeType _currentThemeType = AppThemeType.bingieNeon;
  ThemeMode _themeMode = ThemeMode.dark;
  String _backgroundStyle = 'dynamic';
  String _tileStyle = 'rounded';
  bool _animationsEnabled = true;
  Color _highlightColor = AppTheme.primaryPink;
  Color _secondaryColor = AppTheme.primaryTurquoise;
  Color _backgroundColor = AppTheme.defaultBackground;
  Color _surfaceColor = AppTheme.defaultSurface;

  AppThemeType get currentThemeType => _currentThemeType;
  ThemeMode get themeMode => _themeMode;
  String get backgroundStyle => _backgroundStyle;
  String get tileStyle => _tileStyle;
  bool get animationsEnabled => _animationsEnabled;
  Color get highlightColor => _highlightColor;
  Color get secondaryColor => _secondaryColor;
  Color get backgroundColor => _backgroundColor;
  Color get surfaceColor => _surfaceColor;
  bool get showBackgroundImage => _backgroundStyle == 'dynamic';
  double get tileRadius => _tileStyle == 'compact' ? 16 : 30;

  ThemeData get currentThemeData {
    final customBackground = switch (_backgroundStyle) {
      'amoled' => Colors.black,
      'dark' => AppTheme.defaultBackground,
      'custom' => _backgroundColor,
      _ => null,
    };
    return AppTheme.getTheme(
      _currentThemeType,
      customHighlight: _highlightColor,
      customSecondary: _secondaryColor,
      customBackground: customBackground,
      customSurface: _surfaceColor,
    );
  }

  ThemeManager() {
    _init();
  }

  Future<void> _init() async {
    _currentThemeType = await ThemeStorage.loadTheme();
    _highlightColor =
        await ThemeStorage.loadHighlightColor() ?? _highlightColor;
    _secondaryColor =
        await ThemeStorage.loadSecondaryColor() ?? _secondaryColor;
    _backgroundColor =
        await ThemeStorage.loadBackgroundColor() ?? _backgroundColor;
    _surfaceColor = await ThemeStorage.loadSurfaceColor() ?? _surfaceColor;
    _themeMode = await UserPreferences.getThemeMode();
    final prefs = await SharedPreferences.getInstance();
    _backgroundStyle = prefs.getString('appearance_background') ?? 'dynamic';
    _tileStyle = prefs.getString('appearance_tiles') ?? 'rounded';
    _animationsEnabled = prefs.getBool('appearance_animations') ?? true;
    notifyListeners();
  }

  Future<void> setThemeType(AppThemeType type) async {
    _currentThemeType = type;
    await ThemeStorage.saveTheme(type);
    notifyListeners();
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    _themeMode = mode;
    await UserPreferences.setThemeMode(mode);
    notifyListeners();
  }

  Future<void> setBackgroundStyle(String value) async {
    _backgroundStyle = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('appearance_background', value);
    notifyListeners();
  }

  Future<void> setTileStyle(String value) async {
    _tileStyle = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('appearance_tiles', value);
    notifyListeners();
  }

  Future<void> setAnimationsEnabled(bool value) async {
    _animationsEnabled = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('appearance_animations', value);
    notifyListeners();
  }

  Future<void> setHighlightColor(Color value) async {
    _highlightColor = value;
    _currentThemeType = AppThemeType.custom;
    await ThemeStorage.saveHighlightColor(value);
    await ThemeStorage.saveTheme(_currentThemeType);
    notifyListeners();
  }

  Future<void> setSecondaryColor(Color value) async {
    _secondaryColor = value;
    _currentThemeType = AppThemeType.custom;
    await ThemeStorage.saveSecondaryColor(value);
    await ThemeStorage.saveTheme(_currentThemeType);
    notifyListeners();
  }

  Future<void> setBackgroundColor(Color value) async {
    _backgroundColor = value;
    _currentThemeType = AppThemeType.custom;
    _backgroundStyle = 'custom';
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('appearance_background', _backgroundStyle);
    await ThemeStorage.saveBackgroundColor(value);
    await ThemeStorage.saveTheme(_currentThemeType);
    notifyListeners();
  }

  Future<void> setSurfaceColor(Color value) async {
    _surfaceColor = value;
    _currentThemeType = AppThemeType.custom;
    await ThemeStorage.saveSurfaceColor(value);
    await ThemeStorage.saveTheme(_currentThemeType);
    notifyListeners();
  }

  // Legacy support aliases
  AppThemeType get selectedThemeType => _currentThemeType;
  // This helps when existing code expects a field named 'currentTheme' that is an enum
  AppThemeType get currentTheme => _currentThemeType;
  Future<void> setAppTheme(AppThemeType type) => setThemeType(type);
  Future<void> setTheme(dynamic val) async {
    if (val is AppThemeType) {
      await setThemeType(val);
    } else if (val is ThemeMode) {
      await setThemeMode(val);
    }
  }
}
