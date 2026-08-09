import 'dart:convert';
import 'dart:io';

import 'package:watchio/models/update_info_model.dart';
import 'package:watchio/services/github_release_service.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class UpdateCheckResult {
  final String currentVersion;
  final UpdateInfoModel updateInfo;
  final GitHubRelease? release;
  final bool updateAvailable;
  final bool forceRequired;
  final DateTime checkedAt;
  final UpdateChannel channel;
  final bool fromCache;

  const UpdateCheckResult({
    required this.currentVersion,
    required this.updateInfo,
    required this.release,
    required this.updateAvailable,
    required this.forceRequired,
    required this.checkedAt,
    required this.channel,
    this.fromCache = false,
  });
}

class UpdateService {
  static const _lastCheckKey = 'Watchio IPTV.update.last_check.v1';
  static const _lastKnownVersionKey = 'watchio.update.last_known_version.v2';
  static const _channelKey = 'Watchio IPTV.update.channel.v1';
  static const _cachedReleaseKey = 'watchio.update.cached_release.v2';

  final GitHubReleaseService releaseService;

  UpdateService({GitHubReleaseService? releaseService})
    : releaseService = releaseService ?? GitHubReleaseService();

  Future<UpdateChannel> getChannel() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_channelKey, UpdateChannel.stable.name);
    return UpdateChannel.stable;
  }

  Future<void> setChannel(UpdateChannel channel) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_channelKey, UpdateChannel.stable.name);
  }

  Future<DateTime?> getLastCheckTime() async {
    final prefs = await SharedPreferences.getInstance();
    return DateTime.tryParse(prefs.getString(_lastCheckKey) ?? '');
  }

  Future<String?> getLastKnownVersion() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_lastKnownVersionKey);
  }

  Future<UpdateCheckResult> checkForUpdates({
    UpdateInfoModel? remoteUpdateInfo,
    bool allowCache = true,
  }) async {
    final currentVersion = await getCurrentVersion();
    final channel = await getChannel();

    try {
      final release = await releaseService.fetchLatestRelease(channel);
      final result = await _resultFromRelease(
        currentVersion: currentVersion,
        channel: channel,
        release: release,
        remoteUpdateInfo: remoteUpdateInfo,
        fromCache: false,
      );
      await _cacheResult(result);
      return result;
    } catch (_) {
      if (allowCache) {
        final cached = await _cachedResult(
          currentVersion,
          channel,
          remoteUpdateInfo,
        );
        if (cached != null) return cached;
      }

      final now = DateTime.now();
      return UpdateCheckResult(
        currentVersion: currentVersion,
        updateInfo: remoteUpdateInfo ?? UpdateInfoModel.defaults,
        release: null,
        updateAvailable: false,
        forceRequired: _isBelowMinimum(
          currentVersion,
          remoteUpdateInfo?.minimumVersion ??
              UpdateInfoModel.defaults.minimumVersion,
        ),
        checkedAt: now,
        channel: channel,
      );
    }
  }

  Future<bool> shouldRunScheduledCheck({
    Duration interval = const Duration(hours: 12),
  }) async {
    final lastCheck = await getLastCheckTime();
    if (lastCheck == null) return true;
    return DateTime.now().difference(lastCheck) >= interval;
  }

  Future<String> downloadInstaller(GitHubRelease release) async {
    final url = release.downloadUrl;
    if (url == null || url.isEmpty) {
      throw const UpdateException('No installer asset found.');
    }

    final uri = Uri.parse(url);
    final response = await http.get(uri).timeout(const Duration(minutes: 2));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw UpdateException('Download failed: HTTP ${response.statusCode}');
    }

    final dir = await getTemporaryDirectory();
    final name = p.basename(uri.path).isEmpty
        ? 'Watchio IPTV-${release.version}'
        : p.basename(uri.path);
    final file = File(p.join(dir.path, name));
    await file.writeAsBytes(response.bodyBytes);
    return file.path;
  }

  Future<String> getCurrentVersion() async {
    final info = await PackageInfo.fromPlatform();
    return info.version;
  }

  Future<UpdateCheckResult> _resultFromRelease({
    required String currentVersion,
    required UpdateChannel channel,
    required GitHubRelease? release,
    required UpdateInfoModel? remoteUpdateInfo,
    required bool fromCache,
  }) async {
    final latestVersion =
        release?.version ??
        remoteUpdateInfo?.latestVersion ??
        UpdateInfoModel.defaults.latestVersion;
    final updateInfo = UpdateInfoModel(
      latestVersion: latestVersion,
      minimumVersion:
          remoteUpdateInfo?.minimumVersion ??
          UpdateInfoModel.defaults.minimumVersion,
      forceUpdate: remoteUpdateInfo?.forceUpdate ?? false,
      updateUrl: release?.downloadUrl ?? remoteUpdateInfo?.updateUrl,
      releaseNotes: release?.releaseNotes ?? remoteUpdateInfo?.releaseNotes,
    );

    final updateAvailable = compareVersions(latestVersion, currentVersion) > 0;
    final forceRequired =
        updateInfo.forceUpdate ||
        _isBelowMinimum(currentVersion, updateInfo.minimumVersion);

    return UpdateCheckResult(
      currentVersion: currentVersion,
      updateInfo: updateInfo,
      release: release,
      updateAvailable: updateAvailable,
      forceRequired: forceRequired,
      checkedAt: DateTime.now(),
      channel: channel,
      fromCache: fromCache,
    );
  }

  Future<void> _cacheResult(UpdateCheckResult result) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_lastCheckKey, result.checkedAt.toIso8601String());
    await prefs.setString(
      _lastKnownVersionKey,
      result.updateInfo.latestVersion,
    );
    final release = result.release;
    if (release != null) {
      await prefs.setString(_cachedReleaseKey, jsonEncode(release.toJson()));
    }
  }

  Future<UpdateCheckResult?> _cachedResult(
    String currentVersion,
    UpdateChannel channel,
    UpdateInfoModel? remoteUpdateInfo,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final encoded = prefs.getString(_cachedReleaseKey);
    if (encoded == null) return null;
    final decoded = jsonDecode(encoded);
    if (decoded is! Map<String, dynamic>) return null;
    final release = GitHubRelease.fromJson(decoded);
    return _resultFromRelease(
      currentVersion: currentVersion,
      channel: channel,
      release: release,
      remoteUpdateInfo: remoteUpdateInfo,
      fromCache: true,
    );
  }

  bool _isBelowMinimum(String current, String minimum) {
    return compareVersions(current, minimum) < 0;
  }

  static int compareVersions(String left, String right) {
    final a = _parts(left);
    final b = _parts(right);
    final length = a.length > b.length ? a.length : b.length;
    for (var i = 0; i < length; i++) {
      final av = i < a.length ? a[i] : 0;
      final bv = i < b.length ? b[i] : 0;
      if (av != bv) return av.compareTo(bv);
    }
    return 0;
  }

  static List<int> _parts(String version) {
    return version
        .replaceFirst(RegExp(r'^[vV]'), '')
        .split(RegExp(r'[-+]'))
        .first
        .split('.')
        .map((part) => int.tryParse(part) ?? 0)
        .toList();
  }
}

class UpdateException implements Exception {
  final String message;

  const UpdateException(this.message);

  @override
  String toString() => message;
}
