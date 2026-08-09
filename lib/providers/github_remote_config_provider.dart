import 'dart:convert';

import 'package:watchio/models/announcement_model.dart';
import 'package:watchio/models/branding_model.dart';
import 'package:watchio/models/maintenance_model.dart';
import 'package:watchio/models/theme_model.dart';
import 'package:watchio/models/update_info_model.dart';
import 'package:watchio/providers/remote_config_provider.dart';
import 'package:http/http.dart' as http;

class GitHubRemoteConfigProvider implements RemoteConfigProvider {
  final Uri? configUri;
  final http.Client _client;
  Map<String, dynamic>? _rootCache;

  GitHubRemoteConfigProvider({
    String configUrl = const String.fromEnvironment(
      'Watchio IPTV_REMOTE_CONFIG_URL',
      defaultValue: 'https://raw.githubusercontent.com/IAMSKORPZ/Watchio IPTV_App/main/remote_config.json',
    ),
    http.Client? client,
  })  : configUri = configUrl.trim().isEmpty ? null : Uri.tryParse(configUrl),
        _client = client ?? http.Client();

  @override
  String get sourceName => configUri == null ? 'Built-in defaults' : 'GitHub';

  void clearCache() {
    _rootCache = null;
  }

  @override
  Future<BrandingModel?> fetchBranding() async {
    final root = await _fetchRoot();
    final data = root['branding'];
    return data is Map<String, dynamic> ? BrandingModel.fromJson(data) : null;
  }

  @override
  Future<RemoteThemeModel?> fetchTheme() async {
    final root = await _fetchRoot();
    final data = root['theme'];
    return data is Map<String, dynamic> ? RemoteThemeModel.fromJson(data) : null;
  }

  @override
  Future<List<AnnouncementModel>?> fetchAnnouncements() async {
    final root = await _fetchRoot();
    final data = root['announcements'];
    if (data is! List) return null;
    return data
        .whereType<Map<String, dynamic>>()
        .map(AnnouncementModel.fromJson)
        .where((item) => !item.isExpired)
        .toList()
      ..sort((a, b) => b.priority.compareTo(a.priority));
  }

  @override
  Future<MaintenanceModel?> fetchMaintenance() async {
    final root = await _fetchRoot();
    final data = root['maintenance'];
    return data is Map<String, dynamic> ? MaintenanceModel.fromJson(data) : null;
  }

  @override
  Future<UpdateInfoModel?> fetchUpdateInfo() async {
    final root = await _fetchRoot();
    final data = root['updateInfo'];
    return data is Map<String, dynamic> ? UpdateInfoModel.fromJson(data) : null;
  }

  Future<Map<String, dynamic>> _fetchRoot() async {
    final cached = _rootCache;
    if (cached != null) return cached;

    final uri = configUri;
    if (uri == null || !uri.hasScheme || !uri.hasAuthority) {
      return const {};
    }

    final response = await _client.get(uri).timeout(const Duration(seconds: 8));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw RemoteConfigFetchException('HTTP ${response.statusCode}');
    }

    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Remote config root must be an object.');
    }
    _rootCache = decoded;
    return decoded;
  }
}

class RemoteConfigFetchException implements Exception {
  final String message;

  const RemoteConfigFetchException(this.message);

  @override
  String toString() => message;
}
