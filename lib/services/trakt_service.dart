import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import 'secure_storage_service.dart';

class TraktDeviceCode {
  const TraktDeviceCode({
    required this.deviceCode,
    required this.userCode,
    required this.verificationUrl,
    required this.expiresIn,
    required this.interval,
  });

  final String deviceCode;
  final String userCode;
  final String verificationUrl;
  final int expiresIn;
  final int interval;
}

class TraktService {
  static const _clientId =
      '3ebbf0951b36ec8f38c24a45a03e977d3c394a72a9cbc6c8b00bbc5dbb355bc9';
  static const _clientSecret =
      'ceb24b2aef64d07aae918399b3ef09ad713a759970091bd873721c69668fedbb';
  static const _baseUrl = 'https://api.trakt.tv';
  static const _tokenKey = 'secure_v1_trakt.access_token';
  static const _refreshKey = 'secure_v1_trakt.refresh_token';

  Future<bool> get isLoggedIn async => (await accessToken) != null;

  Future<String?> get accessToken async {
    final stored = await SecureStorageService.instance.readProviderSecret(
      'trakt',
      'access_token',
    );
    if (stored != null) return stored;

    final prefs = await SharedPreferences.getInstance();
    final legacyToken = prefs.getString(_tokenKey);
    final legacyRefresh = prefs.getString(_refreshKey);
    if (legacyToken == null || legacyToken.isEmpty) return null;

    await SecureStorageService.instance.saveProviderSecret(
      'trakt',
      'access_token',
      legacyToken,
    );
    await SecureStorageService.instance.saveProviderSecret(
      'trakt',
      'refresh_token',
      legacyRefresh,
    );
    await prefs.remove(_tokenKey);
    await prefs.remove(_refreshKey);
    return legacyToken;
  }

  Future<TraktDeviceCode> requestDeviceCode() async {
    final response = await http.post(
      Uri.parse('$_baseUrl/oauth/device/code'),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode({'client_id': _clientId}),
    );
    final json = _decode(response);
    return TraktDeviceCode(
      deviceCode: json['device_code'] as String,
      userCode: json['user_code'] as String,
      verificationUrl: json['verification_url'] as String,
      expiresIn: json['expires_in'] as int,
      interval: json['interval'] as int,
    );
  }

  Future<bool> waitForAuthorization(TraktDeviceCode code) async {
    final deadline = DateTime.now().add(Duration(seconds: code.expiresIn));
    var interval = code.interval;
    while (DateTime.now().isBefore(deadline)) {
      await Future<void>.delayed(Duration(seconds: interval));
      late final http.Response response;
      try {
        response = await http
            .post(
              Uri.parse('$_baseUrl/oauth/device/token'),
              headers: const {'Content-Type': 'application/json'},
              body: jsonEncode({
                'code': code.deviceCode,
                'client_id': _clientId,
                'client_secret': _clientSecret,
              }),
            )
            .timeout(const Duration(seconds: 15));
      } on http.ClientException {
        // Android can briefly lose DNS while returning from the browser.
        // Keep polling until the activation code expires.
        continue;
      } on TimeoutException {
        continue;
      }
      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        await SecureStorageService.instance.saveProviderSecret(
          'trakt',
          'access_token',
          json['access_token'] as String,
        );
        await SecureStorageService.instance.saveProviderSecret(
          'trakt',
          'refresh_token',
          json['refresh_token'] as String,
        );
        return true;
      }
      if ([410, 418].contains(response.statusCode)) return false;
      if (response.statusCode == 429) interval += 5;
      if (![400, 404, 409, 429].contains(response.statusCode)) {
        throw Exception('Trakt login failed (${response.statusCode}).');
      }
    }
    return false;
  }

  Future<Map<String, dynamic>> getSettings() => _get('/users/settings');

  Future<List<Map<String, dynamic>>> getWatchlist() async {
    final results = await Future.wait([
      _getList('/sync/watchlist/movies?extended=full'),
      _getList('/sync/watchlist/shows?extended=full'),
    ]);
    return [...results[0], ...results[1]];
  }

  Future<List<Map<String, dynamic>>> getHistory() async {
    final results = await Future.wait([
      _getList('/sync/history/movies?limit=50&extended=full'),
      _getList('/sync/history/shows?limit=50&extended=full'),
    ]);
    return [...results[0], ...results[1]];
  }

  Future<void> logout() async {
    await SecureStorageService.instance.deleteProviderSecret(
      'trakt',
      'access_token',
    );
    await SecureStorageService.instance.deleteProviderSecret(
      'trakt',
      'refresh_token',
    );
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenKey);
    await prefs.remove(_refreshKey);
  }

  Future<Map<String, dynamic>> _get(String path) async {
    final response = await http.get(
      Uri.parse('$_baseUrl$path'),
      headers: await _headers(),
    );
    return _decode(response);
  }

  Future<List<Map<String, dynamic>>> _getList(String path) async {
    final response = await http.get(
      Uri.parse('$_baseUrl$path'),
      headers: await _headers(),
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      _decode(response);
    }
    return (jsonDecode(response.body) as List<dynamic>)
        .cast<Map<String, dynamic>>();
  }

  Future<Map<String, String>> _headers() async => {
    'Content-Type': 'application/json',
    'trakt-api-version': '2',
    'trakt-api-key': _clientId,
    if (await accessToken case final token?) 'Authorization': 'Bearer $token',
  };

  Map<String, dynamic> _decode(http.Response response) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Trakt request failed (${response.statusCode}).');
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }
}
