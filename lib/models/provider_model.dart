import 'package:watchio/models/playlist_model.dart';

enum IptvProviderType { xtreamCodes, m3uUrl, m3uFile, stalker }

enum ProviderStatus { online, offline, authFailed, unknown }

class IptvProvider {
  final String id;
  final IptvProviderType type;
  final String name;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? lastUsed;
  final DateTime? lastConnected;
  final bool enabled;
  final bool isDefault;
  final ProviderStatus status;
  final String? lastFailureReason;
  final String? serverUrl;
  final String? username;
  final String? password;
  final String? playlistUrl;
  final String? localFilePath;
  final String? epgUrl;
  final Map<String, dynamic> providerConfig;

  const IptvProvider({
    required this.id,
    required this.type,
    required this.name,
    required this.createdAt,
    required this.updatedAt,
    this.lastUsed,
    this.lastConnected,
    this.enabled = true,
    this.isDefault = false,
    this.status = ProviderStatus.unknown,
    this.lastFailureReason,
    this.serverUrl,
    this.username,
    this.password,
    this.playlistUrl,
    this.localFilePath,
    this.epgUrl,
    this.providerConfig = const {},
  });

  IptvProvider copyWith({
    IptvProviderType? type,
    String? name,
    DateTime? updatedAt,
    DateTime? lastUsed,
    DateTime? lastConnected,
    bool? enabled,
    bool? isDefault,
    ProviderStatus? status,
    String? lastFailureReason,
    String? serverUrl,
    String? username,
    String? password,
    String? playlistUrl,
    String? localFilePath,
    String? epgUrl,
    Map<String, dynamic>? providerConfig,
    bool clearLastFailureReason = false,
  }) {
    return IptvProvider(
      id: id,
      type: type ?? this.type,
      name: name ?? this.name,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      lastUsed: lastUsed ?? this.lastUsed,
      lastConnected: lastConnected ?? this.lastConnected,
      enabled: enabled ?? this.enabled,
      isDefault: isDefault ?? this.isDefault,
      status: status ?? this.status,
      lastFailureReason:
          clearLastFailureReason ? null : (lastFailureReason ?? this.lastFailureReason),
      serverUrl: serverUrl ?? this.serverUrl,
      username: username ?? this.username,
      password: password ?? this.password,
      playlistUrl: playlistUrl ?? this.playlistUrl,
      localFilePath: localFilePath ?? this.localFilePath,
      epgUrl: epgUrl ?? this.epgUrl,
      providerConfig: providerConfig ?? this.providerConfig,
    );
  }

  Playlist toPlaylist() {
    return Playlist(
      id: id,
      name: name,
      type: type == IptvProviderType.xtreamCodes
          ? PlaylistType.xtream
          : (type == IptvProviderType.stalker
              ? PlaylistType.stalker
              : PlaylistType.m3u),
      url: type == IptvProviderType.xtreamCodes
          ? serverUrl
          : (type == IptvProviderType.stalker
              ? providerConfig['portalUrl'] as String?
              : (playlistUrl ?? localFilePath)),
      username: username,
      password: password,
      createdAt: createdAt,
    );
  }

  factory IptvProvider.fromPlaylist(Playlist playlist, {bool isDefault = false}) {
    final isXtream = playlist.type == PlaylistType.xtream;
    final url = playlist.url ?? '';
    final isM3uUrl = url.startsWith('http://') || url.startsWith('https://');

    return IptvProvider(
      id: playlist.id,
      type: isXtream
          ? IptvProviderType.xtreamCodes
          : (isM3uUrl ? IptvProviderType.m3uUrl : IptvProviderType.m3uFile),
      name: playlist.name,
      createdAt: playlist.createdAt,
      updatedAt: playlist.createdAt,
      enabled: true,
      isDefault: isDefault,
      status: ProviderStatus.unknown,
      serverUrl: isXtream ? playlist.url : null,
      username: playlist.username,
      password: playlist.password,
      playlistUrl: !isXtream && isM3uUrl ? playlist.url : null,
      localFilePath: !isXtream && !isM3uUrl ? playlist.url : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'type': type.name,
      'name': name,
      'createdAt': createdAt.toIso8601String(),
      'updatedAt': updatedAt.toIso8601String(),
      'lastUsed': lastUsed?.toIso8601String(),
      'lastConnected': lastConnected?.toIso8601String(),
      'enabled': enabled,
      'isDefault': isDefault,
      'status': status.name,
      'lastFailureReason': lastFailureReason,
      'serverUrl': serverUrl,
      'username': username,
      'playlistUrl': playlistUrl,
      'localFilePath': localFilePath,
      'epgUrl': epgUrl,
      'providerConfig': providerConfig,
    };
  }

  factory IptvProvider.fromJson(Map<String, dynamic> json) {
    return IptvProvider(
      id: json['id'] as String,
      type: IptvProviderType.values.firstWhere(
        (type) => type.name == json['type'],
        orElse: () => IptvProviderType.m3uUrl,
      ),
      name: json['name'] as String,
      createdAt: DateTime.tryParse(json['createdAt'] as String? ?? '') ??
          DateTime.now(),
      updatedAt: DateTime.tryParse(json['updatedAt'] as String? ?? '') ??
          DateTime.now(),
      lastUsed: DateTime.tryParse(json['lastUsed'] as String? ?? ''),
      lastConnected: DateTime.tryParse(json['lastConnected'] as String? ?? ''),
      enabled: json['enabled'] as bool? ?? true,
      isDefault: json['isDefault'] as bool? ?? false,
      status: ProviderStatus.values.firstWhere(
        (status) => status.name == json['status'],
        orElse: () => ProviderStatus.unknown,
      ),
      lastFailureReason: json['lastFailureReason'] as String?,
      serverUrl: json['serverUrl'] as String?,
      username: json['username'] as String?,
      password: json['password'] as String?,
      playlistUrl: json['playlistUrl'] as String?,
      localFilePath: json['localFilePath'] as String?,
      epgUrl: json['epgUrl'] as String?,
      providerConfig: json['providerConfig'] is Map<String, dynamic>
          ? Map<String, dynamic>.from(json['providerConfig'] as Map)
          : const {},
    );
  }
}

extension IptvProviderLabels on IptvProviderType {
  String get label {
    switch (this) {
      case IptvProviderType.xtreamCodes:
        return 'Xtream Codes';
      case IptvProviderType.m3uUrl:
        return 'M3U URL';
      case IptvProviderType.m3uFile:
        return 'M3U File';
      case IptvProviderType.stalker:
        return 'Stalker Portal';
    }
  }
}

extension ProviderStatusLabels on ProviderStatus {
  String get label {
    switch (this) {
      case ProviderStatus.online:
        return 'Online';
      case ProviderStatus.offline:
        return 'Offline';
      case ProviderStatus.authFailed:
        return 'Auth failed';
      case ProviderStatus.unknown:
        return 'Unknown';
    }
  }
}
