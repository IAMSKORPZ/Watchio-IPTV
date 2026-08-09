import 'dart:convert';

import 'package:watchio/models/announcement_model.dart';
import 'package:watchio/models/branding_model.dart';
import 'package:watchio/models/maintenance_model.dart';
import 'package:watchio/models/theme_model.dart';
import 'package:watchio/models/update_info_model.dart';
import 'package:watchio/providers/github_remote_config_provider.dart';
import 'package:watchio/providers/remote_config_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class RemoteConfigSnapshot {
  final BrandingModel branding;
  final RemoteThemeModel theme;
  final List<AnnouncementModel> announcements;
  final MaintenanceModel maintenance;
  final UpdateInfoModel updateInfo;
  final String sourceName;
  final DateTime? lastSyncTime;
  final bool usingCache;

  const RemoteConfigSnapshot({
    required this.branding,
    required this.theme,
    required this.announcements,
    required this.maintenance,
    required this.updateInfo,
    required this.sourceName,
    this.lastSyncTime,
    this.usingCache = false,
  });

  static const defaults = RemoteConfigSnapshot(
    branding: BrandingModel.defaults,
    theme: RemoteThemeModel.defaults,
    announcements: [],
    maintenance: MaintenanceModel.defaults,
    updateInfo: UpdateInfoModel.defaults,
    sourceName: 'Built-in defaults',
  );
}

class RemoteConfigService {
  static const _cacheKey = 'Watchio IPTV.remote_config.v1';
  static const _lastSyncKey = 'Watchio IPTV.remote_config.last_sync.v1';

  final RemoteConfigProvider provider;

  RemoteConfigService({RemoteConfigProvider? provider})
      : provider = provider ?? GitHubRemoteConfigProvider();

  Future<RemoteConfigSnapshot> load({bool forceRefresh = false}) async {
    final cached = await _tryCache();
    
    if (!forceRefresh && cached != null && cached.lastSyncTime != null) {
      final age = DateTime.now().difference(cached.lastSyncTime!);
      if (age < const Duration(hours: 6)) {
        return cached;
      }
    }

    final remote = await _tryRemote();
    if (remote != null) return remote;
    if (cached != null) return cached;

    return RemoteConfigSnapshot.defaults;
  }

  Future<RemoteConfigSnapshot?> refresh() => _tryRemote();

  Future<bool> hasCache() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_cacheKey) != null;
  }

  Future<RemoteConfigSnapshot?> _tryRemote() async {
    try {
      if (provider is GitHubRemoteConfigProvider) {
        (provider as GitHubRemoteConfigProvider).clearCache();
      }
      final results = await Future.wait<dynamic>([
        provider.fetchBranding(),
        provider.fetchTheme(),
        provider.fetchAnnouncements(),
        provider.fetchMaintenance(),
        provider.fetchUpdateInfo(),
      ]);

      final snapshot = RemoteConfigSnapshot(
        branding: results[0] as BrandingModel? ?? BrandingModel.defaults,
        theme: results[1] as RemoteThemeModel? ?? RemoteThemeModel.defaults,
        announcements:
            results[2] as List<AnnouncementModel>? ?? const <AnnouncementModel>[],
        maintenance: results[3] as MaintenanceModel? ?? MaintenanceModel.defaults,
        updateInfo: results[4] as UpdateInfoModel? ?? UpdateInfoModel.defaults,
        sourceName: provider.sourceName,
        lastSyncTime: DateTime.now(),
      );
      await _cache(snapshot);
      return snapshot;
    } catch (_) {
      return null;
    }
  }

  Future<RemoteConfigSnapshot?> _tryCache() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final encoded = prefs.getString(_cacheKey);
      if (encoded == null) return null;
      final decoded = jsonDecode(encoded);
      if (decoded is! Map<String, dynamic>) return null;
      final lastSync = DateTime.tryParse(prefs.getString(_lastSyncKey) ?? '');

      return RemoteConfigSnapshot(
        branding: decoded['branding'] is Map<String, dynamic>
            ? BrandingModel.fromJson(decoded['branding'] as Map<String, dynamic>)
            : BrandingModel.defaults,
        theme: decoded['theme'] is Map<String, dynamic>
            ? RemoteThemeModel.fromJson(decoded['theme'] as Map<String, dynamic>)
            : RemoteThemeModel.defaults,
        announcements: decoded['announcements'] is List
            ? (decoded['announcements'] as List)
                .whereType<Map<String, dynamic>>()
                .map(AnnouncementModel.fromJson)
                .where((item) => !item.isExpired)
                .toList()
            : const <AnnouncementModel>[],
        maintenance: decoded['maintenance'] is Map<String, dynamic>
            ? MaintenanceModel.fromJson(
                decoded['maintenance'] as Map<String, dynamic>,
              )
            : MaintenanceModel.defaults,
        updateInfo: decoded['updateInfo'] is Map<String, dynamic>
            ? UpdateInfoModel.fromJson(
                decoded['updateInfo'] as Map<String, dynamic>,
              )
            : UpdateInfoModel.defaults,
        sourceName: decoded['sourceName'] as String? ?? 'Cached config',
        lastSyncTime: lastSync,
        usingCache: true,
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> _cache(RemoteConfigSnapshot snapshot) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _cacheKey,
      jsonEncode({
        'branding': snapshot.branding.toJson(),
        'theme': snapshot.theme.toJson(),
        'announcements':
            snapshot.announcements.map((item) => item.toJson()).toList(),
        'maintenance': snapshot.maintenance.toJson(),
        'updateInfo': snapshot.updateInfo.toJson(),
        'sourceName': snapshot.sourceName,
      }),
    );
    await prefs.setString(_lastSyncKey, DateTime.now().toIso8601String());
  }
}
