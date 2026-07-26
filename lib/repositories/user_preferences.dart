import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class UserPreferences {
  static const String _keyLastPlaylist = 'last_playlist';
  static const String _keyVolume = 'volume';
  static const String _keyAudioTrack = 'audio_track';
  static const String _keySubtitleTrack = 'subtitle_track';
  static const String _keyVideoQuality = 'video_quality';
  static const String _keyBackgroundPlay = 'background_play';
  static const String _keySubtitleFontSize = 'subtitle_font_size';
  static const String _keySubtitleHeight = 'subtitle_height';
  static const String _keySubtitleLetterSpacing = 'subtitle_letter_spacing';
  static const String _keySubtitleWordSpacing = 'subtitle_word_spacing';
  static const String _keySubtitleTextColor = 'subtitle_text_color';
  static const String _keySubtitleBackgroundColor = 'subtitle_background_color';
  static const String _keySubtitleFontWeight = 'subtitle_font_weight';
  static const String _keySubtitleTextAlign = 'subtitle_text_align';
  static const String _keySubtitlePadding = 'subtitle_padding';
  static const String _keyLocale = 'locale';
  static const String _hiddenCategoriesKey = 'hidden_categories';
  static const String _keyThemeMode = 'theme_mode';
  static const String _keyBrightnessGesture = 'brightness_gesture';
  static const String _keyVolumeGesture = 'volume_gesture';
  static const String _keySeekGesture = 'seek_gesture';
  static const String _keySpeedUpOnLongPress = 'speed_up_on_long_press';
  static const String _keySeekOnDoubleTap = 'seek_on_double_tap';
  static const String _keyPlayerEngine = 'player_engine';
  static const String _keyHardwareDecoding = 'hardware_decoding';
  static const String _keyAspectRatio = 'player_aspect_ratio';
  static const String _keyDeviceInputMode = 'device_input_mode';
  static const String _keyLiveShowChannelNumbers = 'live_show_channel_numbers';
  static const String _keyLiveShowChannelIcons = 'live_show_channel_icons';
  static const String _keyLiveShowChannelNames = 'live_show_channel_names';
  static const String _keyLiveShowCurrentProgram = 'live_show_current_program';
  static const String _keyLiveRowSize = 'live_row_size';
  static const String _keyLiveSortOrder = 'live_sort_order';

  static String _liveHiddenCategoriesKey(String playlistId) =>
      'live_hidden_categories_$playlistId';

  static Future<void> setLiveHiddenCategoryIds(
    String playlistId,
    List<String> categoryIds,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      _liveHiddenCategoriesKey(playlistId),
      categoryIds,
    );
  }

  static Future<List<String>> getLiveHiddenCategoryIds(
    String playlistId,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getStringList(_liveHiddenCategoriesKey(playlistId)) ?? [];
  }

  static Future<void> setLiveShowChannelNumbers(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyLiveShowChannelNumbers, value);
  }

  static Future<bool> getLiveShowChannelNumbers() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyLiveShowChannelNumbers) ?? true;
  }

  static Future<void> setLiveShowChannelIcons(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyLiveShowChannelIcons, value);
  }

  static Future<bool> getLiveShowChannelIcons() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyLiveShowChannelIcons) ?? true;
  }

  static Future<void> setLiveShowChannelNames(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyLiveShowChannelNames, value);
  }

  static Future<bool> getLiveShowChannelNames() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyLiveShowChannelNames) ?? true;
  }

  static Future<void> setLiveShowCurrentProgram(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyLiveShowCurrentProgram, value);
  }

  static Future<bool> getLiveShowCurrentProgram() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyLiveShowCurrentProgram) ?? true;
  }

  static Future<void> setLiveRowSize(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyLiveRowSize, value);
  }

  static Future<String> getLiveRowSize() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyLiveRowSize) ?? 'normal';
  }

  static Future<void> setLiveSortOrder(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyLiveSortOrder, value);
  }

  static Future<String?> getLiveSortOrder() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyLiveSortOrder);
  }

  static String _catalogKey(String kind, String setting) =>
      'catalog_${kind}_$setting';

  static String _catalogHiddenCategoriesKey(String kind, String playlistId) =>
      'catalog_${kind}_hidden_categories_$playlistId';

  static Future<void> setCatalogHiddenCategoryIds(
    String kind,
    String playlistId,
    List<String> categoryIds,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      _catalogHiddenCategoriesKey(kind, playlistId),
      categoryIds,
    );
  }

  static Future<List<String>> getCatalogHiddenCategoryIds(
    String kind,
    String playlistId,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getStringList(_catalogHiddenCategoriesKey(kind, playlistId)) ??
        [];
  }

  static Future<void> setCatalogShowPoster(String kind, bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_catalogKey(kind, 'show_poster'), value);
  }

  static Future<bool> getCatalogShowPoster(String kind) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_catalogKey(kind, 'show_poster')) ?? true;
  }

  static Future<void> setCatalogShowTitle(String kind, bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_catalogKey(kind, 'show_title'), value);
  }

  static Future<bool> getCatalogShowTitle(String kind) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_catalogKey(kind, 'show_title')) ?? true;
  }

  static Future<void> setCatalogShowRating(String kind, bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_catalogKey(kind, 'show_rating'), value);
  }

  static Future<bool> getCatalogShowRating(String kind) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_catalogKey(kind, 'show_rating')) ?? true;
  }

  static Future<void> setCatalogPosterSize(String kind, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_catalogKey(kind, 'poster_size'), value);
  }

  static Future<String> getCatalogPosterSize(String kind) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_catalogKey(kind, 'poster_size')) ?? 'normal';
  }

  static Future<void> setCatalogSortOrder(String kind, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_catalogKey(kind, 'sort_order'), value);
  }

  static Future<String?> getCatalogSortOrder(String kind) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_catalogKey(kind, 'sort_order'));
  }

  static Future<void> setDeviceInputMode(String mode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyDeviceInputMode, mode);
  }

  static Future<String?> getDeviceInputMode() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyDeviceInputMode);
  }

  static Future<void> removeDeviceInputMode() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyDeviceInputMode);
  }

  static Future<void> setPlayerEngine(String engine) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyPlayerEngine, engine);
  }

  static Future<String> getPlayerEngine() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyPlayerEngine) ?? 'auto';
  }

  static Future<void> setHardwareDecoding(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyHardwareDecoding, value);
  }

  static Future<bool> getHardwareDecoding() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyHardwareDecoding) ?? true;
  }

  static Future<void> setPlayerAspectRatio(String ratio) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyAspectRatio, ratio);
  }

  static Future<String> getPlayerAspectRatio() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyAspectRatio) ?? 'fit';
  }

  static Future<void> setLastPlaylist(String playlistId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyLastPlaylist, playlistId);
  }

  static Future<String?> getLastPlaylist() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyLastPlaylist);
  }

  static Future<void> removeLastPlaylist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyLastPlaylist);
  }

  static Future<void> setVolume(double volume) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keyVolume, volume);
  }

  static Future<double> getVolume() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_keyVolume) ?? 100;
  }

  static Future<void> setAudioTrack(String language) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyAudioTrack, language);
  }

  static Future<String> getAudioTrack() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyAudioTrack) ?? 'auto';
  }

  static Future<void> setSubtitleTrack(String language) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keySubtitleTrack, language);
  }

  static Future<String> getSubtitleTrack() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keySubtitleTrack) ?? 'auto';
  }

  static Future<void> setVideoTrack(String id) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyVideoQuality, id);
  }

  static Future<String> getVideoTrack() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyVideoQuality) ?? 'auto';
  }

  static Future<void> setBackgroundPlay(bool backgroundPlay) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyBackgroundPlay, backgroundPlay);
  }

  static Future<bool> getBackgroundPlay() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyBackgroundPlay) ?? true;
  }

  static Future<double> getSubtitleFontSize() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_keySubtitleFontSize) ?? 32.0;
  }

  static Future<void> setSubtitleFontSize(double fontSize) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keySubtitleFontSize, fontSize);
  }

  static Future<double> getSubtitleHeight() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_keySubtitleHeight) ?? 1.4;
  }

  static Future<void> setSubtitleHeight(double height) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keySubtitleHeight, height);
  }

  static Future<double> getSubtitleLetterSpacing() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_keySubtitleLetterSpacing) ?? 0.0;
  }

  static Future<void> setSubtitleLetterSpacing(double letterSpacing) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keySubtitleLetterSpacing, letterSpacing);
  }

  static Future<double> getSubtitleWordSpacing() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_keySubtitleWordSpacing) ?? 0.0;
  }

  static Future<void> setSubtitleWordSpacing(double wordSpacing) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keySubtitleWordSpacing, wordSpacing);
  }

  static Future<Color> getSubtitleTextColor() async {
    final prefs = await SharedPreferences.getInstance();
    final colorValue = prefs.getInt(_keySubtitleTextColor) ?? 0xffffffff;
    return Color(colorValue);
  }

  static Future<void> setSubtitleTextColor(Color textColor) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keySubtitleTextColor, textColor.toARGB32());
  }

  static Future<Color> getSubtitleBackgroundColor() async {
    final prefs = await SharedPreferences.getInstance();
    final colorValue = prefs.getInt(_keySubtitleBackgroundColor) ?? 0xaa000000;
    return Color(colorValue);
  }

  static Future<void> setSubtitleBackgroundColor(Color backgroundColor) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keySubtitleBackgroundColor, backgroundColor.toARGB32());
  }

  static Future<FontWeight> getSubtitleFontWeight() async {
    final prefs = await SharedPreferences.getInstance();
    final weightIndex =
        prefs.getInt(_keySubtitleFontWeight) ?? FontWeight.normal.value;
    return FontWeight.values[weightIndex];
  }

  static Future<void> setSubtitleFontWeight(FontWeight fontWeight) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keySubtitleFontWeight, fontWeight.value);
  }

  static Future<TextAlign> getSubtitleTextAlign() async {
    final prefs = await SharedPreferences.getInstance();
    final alignIndex =
        prefs.getInt(_keySubtitleTextAlign) ?? TextAlign.center.index;
    return TextAlign.values[alignIndex];
  }

  static Future<void> setSubtitleTextAlign(TextAlign textAlign) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keySubtitleTextAlign, textAlign.index);
  }

  static Future<double> getSubtitlePadding() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_keySubtitlePadding) ?? 24.0;
  }

  static Future<void> setSubtitlePadding(double padding) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keySubtitlePadding, padding);
  }

  static Future<String?> getLocale() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyLocale);
  }

  static Future<void> setLocale(String locale) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyLocale, locale);
  }

  static Future<void> removeLocale() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyLocale);
  }

  static Future<void> setHiddenCategories(List<String> categoryIds) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_hiddenCategoriesKey, categoryIds);
  }

  static Future<bool> getHiddenCategory(String categoryId) async {
    final hidden = await getHiddenCategories();
    return hidden.contains(categoryId);
  }

  static Future<List<String>> getHiddenCategories() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getStringList(_hiddenCategoriesKey) ?? [];
  }

  static Future<void> setThemeMode(ThemeMode mode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyThemeMode, mode.toString().split('.').last);
  }

  static Future<ThemeMode> getThemeMode() async {
    final prefs = await SharedPreferences.getInstance();
    final mode = prefs.getString(_keyThemeMode) ?? 'system';
    switch (mode) {
      case 'light':
        return ThemeMode.light;
      case 'dark':
        return ThemeMode.dark;
      default:
        return ThemeMode.system;
    }
  }

  // Player gesture settings
  static Future<bool> getBrightnessGesture() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyBrightnessGesture) ?? false;
  }

  static Future<void> setBrightnessGesture(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyBrightnessGesture, value);
  }

  static Future<bool> getVolumeGesture() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyVolumeGesture) ?? false;
  }

  static Future<void> setVolumeGesture(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyVolumeGesture, value);
  }

  static Future<bool> getSeekGesture() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keySeekGesture) ?? false;
  }

  static Future<void> setSeekGesture(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keySeekGesture, value);
  }

  static Future<bool> getSpeedUpOnLongPress() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keySpeedUpOnLongPress) ?? true;
  }

  static Future<void> setSpeedUpOnLongPress(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keySpeedUpOnLongPress, value);
  }

  static Future<bool> getSeekOnDoubleTap() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keySeekOnDoubleTap) ?? true;
  }

  static Future<void> setSeekOnDoubleTap(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keySeekOnDoubleTap, value);
  }
}
