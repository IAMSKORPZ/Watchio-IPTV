import 'dart:convert';
import 'dart:io';

import 'package:watchio/models/api_configuration_model.dart';
import 'package:watchio/models/provider_model.dart';
import 'package:watchio/models/stalker_provider_config.dart';
import 'package:watchio/repositories/iptv_repository.dart';
import 'package:watchio/repositories/user_preferences.dart';
import 'package:watchio/services/app_state.dart';
import 'package:watchio/services/playlist_service.dart';
import 'package:watchio/services/secure_storage_service.dart';
import 'package:watchio/services/stalker_auth_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

abstract class ProviderRepository {
  Future<IptvProvider> createProvider(IptvProvider provider);
  Future<IptvProvider> updateProvider(IptvProvider provider);
  Future<void> deleteProvider(String id);
  Future<IptvProvider?> getProvider(String id);
  Future<List<IptvProvider>> getAllProviders();
  Future<IptvProvider> setDefaultProvider(String id);
  Future<IptvProvider?> getDefaultProvider();
  Future<IptvProvider> switchProvider(String id);
  Future<IptvProvider> disableProvider(String id);
  Future<IptvProvider> enableProvider(String id);
  Future<IptvProvider> checkProviderStatus(String id);
}

class SharedPreferencesProviderRepository implements ProviderRepository {
  static const _providersKey = 'Watchio IPTV.providers.v1';

  @override
  Future<IptvProvider> createProvider(IptvProvider provider) async {
    _validate(provider);
    final providers = await getAllProviders();
    if (providers.any((item) => item.id == provider.id)) {
      throw ProviderValidationException('Provider already exists.');
    }

    final isFirst = providers.isEmpty;
    final saved = provider.copyWith(
      updatedAt: DateTime.now(),
      isDefault: provider.isDefault || isFirst,
    );
    await PlaylistService.savePlaylist(saved.toPlaylist());
    await _saveAll(_normalizeDefaults([...providers, saved]));
    return saved;
  }

  @override
  Future<IptvProvider> updateProvider(IptvProvider provider) async {
    _validate(provider);
    final providers = await getAllProviders();
    final index = providers.indexWhere((item) => item.id == provider.id);
    if (index == -1) throw ProviderValidationException('Provider not found.');

    final saved = provider.copyWith(updatedAt: DateTime.now());
    providers[index] = saved;
    await PlaylistService.updatePlaylist(saved.toPlaylist());
    await _saveAll(_normalizeDefaults(providers));
    return saved;
  }

  @override
  Future<void> deleteProvider(String id) async {
    final providers = await getAllProviders();
    providers.removeWhere((item) => item.id == id);
    await PlaylistService.deletePlaylist(id);
    await _saveAll(_normalizeDefaults(providers));
  }

  @override
  Future<IptvProvider?> getProvider(String id) async {
    final providers = await getAllProviders();
    for (final provider in providers) {
      if (provider.id == id) return provider;
    }
    return null;
  }

  @override
  Future<List<IptvProvider>> getAllProviders() async {
    final stored = await _readStoredProviders();
    final playlists = await PlaylistService.getPlaylists();
    final byId = {for (final provider in stored) provider.id: provider};
    final defaultId = await UserPreferences.getLastPlaylist();

    for (final playlist in playlists) {
      byId.putIfAbsent(
        playlist.id,
        () => IptvProvider.fromPlaylist(
          playlist,
          isDefault: defaultId == playlist.id,
        ),
      );
    }

    final providers = _normalizeDefaults(byId.values.toList());
    await _saveAll(providers);
    return providers;
  }

  @override
  Future<IptvProvider> setDefaultProvider(String id) async {
    final providers = await getAllProviders();
    final index = providers.indexWhere((item) => item.id == id);
    if (index == -1) throw ProviderValidationException('Provider not found.');

    final updated = [
      for (final provider in providers)
        provider.copyWith(
          isDefault: provider.id == id,
          updatedAt: provider.id == id ? DateTime.now() : provider.updatedAt,
        ),
    ];
    await _saveAll(updated);
    return updated.firstWhere((provider) => provider.id == id);
  }

  @override
  Future<IptvProvider?> getDefaultProvider() async {
    final providers = await getAllProviders();
    for (final provider in providers) {
      if (provider.isDefault) return provider;
    }
    return providers.isEmpty ? null : providers.first;
  }

  @override
  Future<IptvProvider> switchProvider(String id) async {
    final provider = await getProvider(id);
    if (provider == null) throw ProviderValidationException('Provider not found.');
    if (!provider.enabled) {
      throw ProviderValidationException('Provider is disabled.');
    }

    AppState.currentPlaylist = null;
    AppState.currentProvider = null;
    AppState.xtreamCodeRepository = null;
    AppState.m3uRepository = null;
    AppState.m3uItems = null;

    await UserPreferences.setLastPlaylist(id);
    final switched = provider.copyWith(lastUsed: DateTime.now());
    await updateProvider(switched);
    AppState.currentProvider = switched;
    return switched;
  }

  @override
  Future<IptvProvider> disableProvider(String id) async {
    final provider = await getProvider(id);
    if (provider == null) throw ProviderValidationException('Provider not found.');
    return updateProvider(provider.copyWith(enabled: false));
  }

  @override
  Future<IptvProvider> enableProvider(String id) async {
    final provider = await getProvider(id);
    if (provider == null) throw ProviderValidationException('Provider not found.');
    return updateProvider(provider.copyWith(enabled: true));
  }

  @override
  Future<IptvProvider> checkProviderStatus(String id) async {
    final provider = await getProvider(id);
    if (provider == null) throw ProviderValidationException('Provider not found.');

    try {
      _validate(provider);
      if (provider.type == IptvProviderType.xtreamCodes) {
        final repo = IptvRepository(
          ApiConfig(
            baseUrl: provider.serverUrl!,
            username: provider.username!,
            password: provider.password!,
          ),
          provider.id,
        );
        final info = await repo.getPlayerInfo(forceRefresh: true);
        if (info == null || info.userInfo.auth == 0) {
          return updateProvider(
            provider.copyWith(
              status: ProviderStatus.authFailed,
              lastFailureReason: 'Invalid credentials.',
            ),
          );
        }
      } else if (provider.type == IptvProviderType.m3uUrl) {
        final client = HttpClient();
        final request = await client
            .getUrl(Uri.parse(provider.playlistUrl!))
            .timeout(const Duration(seconds: 8));
        final response = await request.close().timeout(const Duration(seconds: 8));
        client.close();
        if (response.statusCode >= 400) {
          return updateProvider(
            provider.copyWith(
              status: ProviderStatus.offline,
              lastFailureReason: 'HTTP ${response.statusCode}.',
            ),
          );
        }
      } else if (provider.type == IptvProviderType.stalker) {
        _validateUrl(
          provider.providerConfig['portalUrl'] as String?,
          'Portal URL',
        );
        final mac = await SecureStorageService.instance.readProviderSecret(
          provider.id,
          'stalker_mac',
        );
        if (mac == null || mac.trim().isEmpty) {
          return updateProvider(
            provider.copyWith(
              status: ProviderStatus.authFailed,
              lastFailureReason: 'MAC address is required.',
            ),
          );
        }
        await StalkerAuthService().authenticate(
          providerId: provider.id,
          config: StalkerProviderConfig.fromJson(provider.providerConfig),
          macAddress: mac,
        );
      }

      return updateProvider(
        provider.copyWith(
          status: ProviderStatus.online,
          lastConnected: DateTime.now(),
          clearLastFailureReason: true,
        ),
      );
    } catch (e) {
      return updateProvider(
        provider.copyWith(
          status: ProviderStatus.offline,
          lastFailureReason: e.toString(),
        ),
      );
    }
  }

  Future<List<IptvProvider>> _readStoredProviders() async {
    final prefs = await SharedPreferences.getInstance();
    final encoded = prefs.getString(_providersKey);
    if (encoded == null || encoded.isEmpty) return [];
    final decoded = jsonDecode(encoded) as List<dynamic>;
    final providers = decoded
        .map((item) => IptvProvider.fromJson(item as Map<String, dynamic>))
        .toList();
    return Future.wait(providers.map(_hydrateSecret));
  }

  Future<void> _saveAll(List<IptvProvider> providers) async {
    for (final provider in providers) {
      await SecureStorageService.instance.saveProviderPassword(
        provider.id,
        provider.password,
      );
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _providersKey,
      jsonEncode(providers.map((provider) => provider.toJson()).toList()),
    );
  }

  Future<IptvProvider> _hydrateSecret(IptvProvider provider) async {
    final password =
        await SecureStorageService.instance.readProviderPassword(provider.id);
    return password == null ? provider : provider.copyWith(password: password);
  }

  List<IptvProvider> _normalizeDefaults(List<IptvProvider> providers) {
    if (providers.isEmpty) return providers;
    final defaultId = providers.firstWhere(
      (provider) => provider.isDefault,
      orElse: () => providers.first,
    ).id;

    return [
      for (final provider in providers)
        provider.copyWith(isDefault: provider.id == defaultId),
    ];
  }

  void _validate(IptvProvider provider) {
    if (provider.name.trim().isEmpty) {
      throw ProviderValidationException('Provider name is required.');
    }

    switch (provider.type) {
      case IptvProviderType.xtreamCodes:
        _validateUrl(provider.serverUrl, 'Server URL');
        if (provider.username?.trim().isEmpty ?? true) {
          throw ProviderValidationException('Username is required.');
        }
        if (provider.password?.trim().isEmpty ?? true) {
          throw ProviderValidationException('Password is required.');
        }
        break;
      case IptvProviderType.m3uUrl:
        _validateUrl(provider.playlistUrl, 'Playlist URL');
        break;
      case IptvProviderType.m3uFile:
        final path = provider.localFilePath;
        if (path == null || path.trim().isEmpty) {
          throw ProviderValidationException('Local file path is required.');
        }
        final file = File(path);
        if (!file.existsSync()) {
          throw ProviderValidationException('M3U file does not exist.');
        }
        try {
          file.openSync(mode: FileMode.read).closeSync();
        } catch (_) {
          throw ProviderValidationException('M3U file is not readable.');
        }
        break;
      case IptvProviderType.stalker:
        _validateUrl(
          provider.providerConfig['portalUrl'] as String?,
          'Portal URL',
        );
        break;
    }
  }

  void _validateUrl(String? value, String label) {
    if (value?.trim().isEmpty ?? true) {
      throw ProviderValidationException('$label is required.');
    }
    final uri = Uri.tryParse(value!.trim());
    if (uri == null ||
        !uri.hasScheme ||
        !uri.hasAuthority ||
        !['http', 'https'].contains(uri.scheme)) {
      throw ProviderValidationException('$label must be a valid URL.');
    }
  }
}

class ProviderValidationException implements Exception {
  final String message;

  const ProviderValidationException(this.message);

  @override
  String toString() => message;
}
