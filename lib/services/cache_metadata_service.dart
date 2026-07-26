import 'dart:convert';

import 'package:another_iptv_player/models/category_type.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum CacheSection { live, vod, series, epg, account }

extension CacheSectionMapping on CacheSection {
  static CacheSection fromCategoryType(CategoryType type) {
    return switch (type) {
      CategoryType.live => CacheSection.live,
      CategoryType.vod => CacheSection.vod,
      CategoryType.series => CacheSection.series,
    };
  }
}

class CacheMetadata {
  final String playlistId;
  final CacheSection section;
  final DateTime? lastUpdated;
  final DateTime? lastSuccess;
  final int itemCount;
  final String? lastError;
  final bool isFirstPostLoginRefreshDone;

  const CacheMetadata({
    required this.playlistId,
    required this.section,
    this.lastUpdated,
    this.lastSuccess,
    this.itemCount = 0,
    this.lastError,
    this.isFirstPostLoginRefreshDone = false,
  });

  factory CacheMetadata.empty(String playlistId, CacheSection section) {
    return CacheMetadata(playlistId: playlistId, section: section);
  }

  factory CacheMetadata.fromJson(
    String playlistId,
    CacheSection section,
    Map<String, dynamic> json,
  ) {
    return CacheMetadata(
      playlistId: playlistId,
      section: section,
      lastUpdated: _readDate(json['lastUpdated']),
      lastSuccess: _readDate(json['lastSuccess']),
      itemCount: json['itemCount'] is int ? json['itemCount'] as int : 0,
      lastError: json['lastError'] as String?,
      isFirstPostLoginRefreshDone: json['isFirstPostLoginRefreshDone'] == true,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'lastUpdated': lastUpdated?.toIso8601String(),
      'lastSuccess': lastSuccess?.toIso8601String(),
      'itemCount': itemCount,
      'lastError': lastError,
      'isFirstPostLoginRefreshDone': isFirstPostLoginRefreshDone,
    };
  }

  CacheMetadata copyWith({
    DateTime? lastUpdated,
    DateTime? lastSuccess,
    int? itemCount,
    Object? lastError = _unset,
    bool? isFirstPostLoginRefreshDone,
  }) {
    return CacheMetadata(
      playlistId: playlistId,
      section: section,
      lastUpdated: lastUpdated ?? this.lastUpdated,
      lastSuccess: lastSuccess ?? this.lastSuccess,
      itemCount: itemCount ?? this.itemCount,
      lastError: identical(lastError, _unset)
          ? this.lastError
          : lastError as String?,
      isFirstPostLoginRefreshDone:
          isFirstPostLoginRefreshDone ?? this.isFirstPostLoginRefreshDone,
    );
  }

  static DateTime? _readDate(dynamic value) {
    if (value is! String || value.isEmpty) return null;
    return DateTime.tryParse(value);
  }

  static const Object _unset = Object();
}

class CacheMetadataService {
  static const Duration staleAfter = Duration(hours: 24);
  static const String _prefix = 'watchio.cache_metadata.v1';
  static const String _firstRefreshPrefix =
      'watchio.cache_first_login_refresh.v1';

  Future<CacheMetadata> getSection(
    String playlistId,
    CacheSection section,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_sectionKey(playlistId, section));
    if (raw == null || raw.isEmpty) {
      return CacheMetadata.empty(playlistId, section);
    }

    try {
      final json = jsonDecode(raw);
      if (json is Map<String, dynamic>) {
        return CacheMetadata.fromJson(playlistId, section, json);
      }
      if (json is Map) {
        return CacheMetadata.fromJson(
          playlistId,
          section,
          Map<String, dynamic>.from(json),
        );
      }
    } catch (_) {
      // Bad metadata should not block app startup.
    }

    return CacheMetadata.empty(playlistId, section);
  }

  Future<void> setSection(CacheMetadata metadata) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _sectionKey(metadata.playlistId, metadata.section),
      jsonEncode(metadata.toJson()),
    );
  }

  Future<void> markSuccess({
    required String playlistId,
    required CacheSection section,
    required int itemCount,
  }) async {
    final current = await getSection(playlistId, section);
    final now = DateTime.now();
    await setSection(
      current.copyWith(
        lastUpdated: now,
        lastSuccess: now,
        itemCount: itemCount,
        lastError: null,
      ),
    );
  }

  Future<void> markFailure({
    required String playlistId,
    required CacheSection section,
    required Object error,
  }) async {
    final current = await getSection(playlistId, section);
    await setSection(current.copyWith(lastError: _cleanError(error)));
  }

  Future<bool> isFirstPostLoginRefreshDone(String playlistId) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_firstRefreshKey(playlistId)) ?? false;
  }

  Future<void> setFirstPostLoginRefreshDone(
    String playlistId,
    bool value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_firstRefreshKey(playlistId), value);
  }

  bool isStale(CacheMetadata metadata) {
    final lastSuccess = metadata.lastSuccess ?? metadata.lastUpdated;
    if (lastSuccess == null) return true;
    return DateTime.now().difference(lastSuccess) > staleAfter;
  }

  String _sectionKey(String playlistId, CacheSection section) {
    return '$_prefix.$playlistId.${section.name}';
  }

  String _firstRefreshKey(String playlistId) {
    return '$_firstRefreshPrefix.$playlistId';
  }

  String _cleanError(Object error) {
    final value = error.toString().replaceAll(RegExp(r'\s+'), ' ').trim();
    if (value.length <= 180) return value;
    return '${value.substring(0, 180)}…';
  }
}
