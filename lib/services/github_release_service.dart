import 'dart:convert';

import 'package:http/http.dart' as http;

enum UpdateChannel { stable, beta, development }

class GitHubRelease {
  final String version;
  final String releaseNotes;
  final DateTime? publishedAt;
  final String? downloadUrl;
  final String htmlUrl;
  final UpdateChannel channel;

  const GitHubRelease({
    required this.version,
    required this.releaseNotes,
    required this.publishedAt,
    required this.downloadUrl,
    required this.htmlUrl,
    required this.channel,
  });

  Map<String, dynamic> toJson() {
    return {
      'version': version,
      'releaseNotes': releaseNotes,
      'publishedAt': publishedAt?.toIso8601String(),
      'downloadUrl': downloadUrl,
      'htmlUrl': htmlUrl,
      'channel': channel.name,
    };
  }

  factory GitHubRelease.fromJson(Map<String, dynamic> json) {
    return GitHubRelease(
      version: json['version'] as String? ?? '0.0.0',
      releaseNotes: json['releaseNotes'] as String? ?? '',
      publishedAt: DateTime.tryParse(json['publishedAt'] as String? ?? ''),
      downloadUrl: json['downloadUrl'] as String?,
      htmlUrl: json['htmlUrl'] as String? ?? '',
      channel: UpdateChannel.values.firstWhere(
        (item) => item.name == json['channel'],
        orElse: () => UpdateChannel.stable,
      ),
    );
  }
}

class GitHubReleaseService {
  static const String releaseTag = 'Latest';
  final String owner;
  final String repo;
  final http.Client _client;

  GitHubReleaseService({
    String owner = const String.fromEnvironment(
      'Watchio IPTV_GITHUB_OWNER',
      defaultValue: 'IAMSKORPZ',
    ),
    String repo = const String.fromEnvironment(
      'Watchio IPTV_GITHUB_REPO',
      defaultValue: 'Watchio',
    ),
    http.Client? client,
  }) : owner = owner.trim(),
       repo = repo.trim(),
       _client = client ?? http.Client();

  Future<GitHubRelease?> fetchLatestRelease(UpdateChannel channel) async {
    final uri = Uri.https(
      'api.github.com',
      '/repos/$owner/$repo/releases/tags/$releaseTag',
    );
    final response = await _client
        .get(uri, headers: const {'Accept': 'application/vnd.github+json'})
        .timeout(const Duration(seconds: 10));

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw GitHubReleaseException('GitHub HTTP ${response.statusCode}');
    }

    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('GitHub release response must be an object.');
    }
    if (decoded['draft'] == true) return null;
    return _fromGitHubJson(decoded);
  }

  GitHubRelease _fromGitHubJson(Map<String, dynamic> json) {
    final tag = (json['tag_name'] as String? ?? '').trim();
    final name = (json['name'] as String? ?? tag).trim();
    final version = _releaseVersion(json, tag.isEmpty ? name : tag);
    final prerelease = json['prerelease'] == true;
    final channel = _detectChannel(tag, name, prerelease);

    return GitHubRelease(
      version: version,
      releaseNotes: json['body'] as String? ?? '',
      publishedAt: DateTime.tryParse(json['published_at'] as String? ?? ''),
      downloadUrl: _pickAssetUrl(json['assets']),
      htmlUrl: json['html_url'] as String? ?? '',
      channel: channel,
    );
  }

  UpdateChannel _detectChannel(String tag, String name, bool prerelease) {
    final marker = '$tag $name'.toLowerCase();
    if (marker.contains('dev') ||
        marker.contains('alpha') ||
        marker.contains('nightly')) {
      return UpdateChannel.development;
    }
    if (marker.contains('beta') || marker.contains('rc')) {
      return UpdateChannel.beta;
    }
    return prerelease ? UpdateChannel.beta : UpdateChannel.stable;
  }

  String? _pickAssetUrl(dynamic assets) {
    if (assets is! List) return null;
    final typed = assets.whereType<Map<String, dynamic>>().toList();
    if (typed.isEmpty) return null;
    final preferred = typed.firstWhere((asset) {
      final name = (asset['name'] as String? ?? '').toLowerCase();
      return name.endsWith('.apk');
    }, orElse: () => <String, dynamic>{});
    return preferred['browser_download_url'] as String?;
  }

  String _normalizeVersion(String value) {
    return value.trim().replaceFirst(RegExp(r'^[vV]'), '').split('+').first;
  }

  String _releaseVersion(Map<String, dynamic> json, String fallback) {
    final assets = json['assets'];
    if (assets is List) {
      for (final asset in assets.whereType<Map<String, dynamic>>()) {
        final name = asset['name']?.toString() ?? '';
        final match = RegExp(
          r'v?(\d+\.\d+\.\d+)',
          caseSensitive: false,
        ).firstMatch(name);
        if (match != null) return match.group(1)!;
      }
    }
    return _normalizeVersion(fallback);
  }
}

class GitHubReleaseException implements Exception {
  final String message;

  const GitHubReleaseException(this.message);

  @override
  String toString() => message;
}
