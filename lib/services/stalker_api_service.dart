import 'dart:convert';

import 'package:watchio/models/stalker_provider_config.dart';
import 'package:http/http.dart' as http;

class StalkerApiService {
  final http.Client client;

  StalkerApiService({http.Client? client}) : client = client ?? http.Client();

  Future<List<Map<String, dynamic>>> fetchCategories({
    required StalkerProviderConfig config,
    required String token,
    required String type,
  }) async {
    final action = switch (type) {
      'live' => 'get_genres',
      'vod' => 'get_categories',
      'series' => 'get_categories',
      _ => 'get_genres',
    };
    return _fetchList(config: config, token: token, params: {
      'type': type == 'live' ? 'itv' : 'vod',
      'action': action,
      'JsHttpRequest': '1-xml',
    });
  }

  Future<List<Map<String, dynamic>>> fetchPage({
    required StalkerProviderConfig config,
    required String token,
    required String type,
    required int page,
    String? categoryId,
  }) {
    return _fetchList(config: config, token: token, params: {
      'type': type == 'live' ? 'itv' : 'vod',
      'action': type == 'live' ? 'get_ordered_list' : 'get_ordered_list',
      'p': page.toString(),
      'category': ?categoryId,
      'JsHttpRequest': '1-xml',
    });
  }

  Future<List<Map<String, dynamic>>> _fetchList({
    required StalkerProviderConfig config,
    required String token,
    required Map<String, String> params,
  }) async {
    final response = await client.get(
      _portalUri(config.portalUrl, params),
      headers: {'Authorization': 'Bearer $token'},
    );
    if (response.statusCode >= 400) {
      throw StalkerApiException('Stalker API failed: HTTP ${response.statusCode}');
    }
    final decoded = jsonDecode(response.body) as Map<String, dynamic>;
    final js = decoded['js'];
    final data = js is Map ? js['data'] : js;
    if (data is List) {
      return data.cast<Map>().map((item) => Map<String, dynamic>.from(item)).toList();
    }
    return const [];
  }

  Uri _portalUri(String portalUrl, Map<String, String> query) {
    final base = Uri.parse(portalUrl);
    final path = base.path.endsWith('/server/load.php')
        ? base.path
        : '${base.path.replaceAll(RegExp(r'/$'), '')}/server/load.php';
    return base.replace(path: path, queryParameters: query);
  }
}

class StalkerApiException implements Exception {
  final String message;

  const StalkerApiException(this.message);

  @override
  String toString() => message;
}
