import 'dart:convert';

import 'package:watchio/models/stalker_provider_config.dart';
import 'package:watchio/services/secure_storage_service.dart';
import 'package:http/http.dart' as http;

class StalkerAuthService {
  final http.Client client;

  StalkerAuthService({http.Client? client}) : client = client ?? http.Client();

  Future<StalkerSession> authenticate({
    required String providerId,
    required StalkerProviderConfig config,
    required String macAddress,
  }) async {
    await SecureStorageService.instance.saveProviderSecret(
      providerId,
      'stalker_mac',
      macAddress,
    );

    final token = await handshake(config: config, macAddress: macAddress);
    await SecureStorageService.instance.saveProviderSecret(
      providerId,
      'stalker_token',
      token,
    );
    final profile = await loadProfile(config: config, token: token);
    return StalkerSession(token: token, profile: profile);
  }

  Future<String> handshake({
    required StalkerProviderConfig config,
    required String macAddress,
  }) async {
    final uri = _portalUri(config.portalUrl, {
      'type': 'stb',
      'action': 'handshake',
      'JsHttpRequest': '1-xml',
    });
    final response = await client.get(uri, headers: _headers(config, macAddress));
    if (response.statusCode >= 400) {
      throw StalkerAuthException('Handshake failed: HTTP ${response.statusCode}');
    }
    final decoded = jsonDecode(response.body) as Map<String, dynamic>;
    final token = decoded['js'] is Map ? decoded['js']['token'] : null;
    if (token is! String || token.isEmpty) {
      throw const StalkerAuthException('Handshake did not return a token.');
    }
    return token;
  }

  Future<Map<String, dynamic>> loadProfile({
    required StalkerProviderConfig config,
    required String token,
  }) async {
    final uri = _portalUri(config.portalUrl, {
      'type': 'stb',
      'action': 'get_profile',
      'JsHttpRequest': '1-xml',
    });
    final response = await client.get(
      uri,
      headers: {'Authorization': 'Bearer $token'},
    );
    if (response.statusCode >= 400) {
      throw StalkerAuthException('Profile failed: HTTP ${response.statusCode}');
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Uri _portalUri(String portalUrl, Map<String, String> query) {
    final base = Uri.parse(portalUrl);
    final path = base.path.endsWith('/server/load.php')
        ? base.path
        : '${base.path.replaceAll(RegExp(r'/$'), '')}/server/load.php';
    return base.replace(path: path, queryParameters: query);
  }

  Map<String, String> _headers(
    StalkerProviderConfig config,
    String macAddress,
  ) {
    return {
      'User-Agent': config.userAgentOverride ?? 'Mozilla/5.0 (QtEmbedded; U; Linux; MAG)',
      'Cookie': 'mac=$macAddress; stb_lang=en; timezone=UTC',
    };
  }
}

class StalkerSession {
  final String token;
  final Map<String, dynamic> profile;

  const StalkerSession({required this.token, required this.profile});
}

class StalkerAuthException implements Exception {
  final String message;

  const StalkerAuthException(this.message);

  @override
  String toString() => message;
}
