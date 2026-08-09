import 'package:watchio/models/stalker_provider_config.dart';
import 'package:watchio/services/secure_storage_service.dart';
import 'package:watchio/services/stalker_api_service.dart';
import 'package:watchio/services/stalker_auth_service.dart';

class StalkerRepository {
  final String providerId;
  final StalkerProviderConfig config;
  final StalkerAuthService auth;
  final StalkerApiService api;

  StalkerRepository({
    required this.providerId,
    required this.config,
    StalkerAuthService? auth,
    StalkerApiService? api,
  })  : auth = auth ?? StalkerAuthService(),
        api = api ?? StalkerApiService();

  Future<StalkerSession> authenticate(String macAddress) {
    return auth.authenticate(
      providerId: providerId,
      config: config,
      macAddress: macAddress,
    );
  }

  Future<String?> readToken() {
    return SecureStorageService.instance.readProviderSecret(
      providerId,
      'stalker_token',
    );
  }

  Future<List<Map<String, dynamic>>> fetchLivePage({
    required int page,
    String? categoryId,
  }) async {
    final token = await readToken();
    if (token == null) throw const StalkerRepositoryException('Missing token.');
    return api.fetchPage(
      config: config,
      token: token,
      type: 'live',
      page: page,
      categoryId: categoryId,
    );
  }
}

class StalkerRepositoryException implements Exception {
  final String message;

  const StalkerRepositoryException(this.message);

  @override
  String toString() => message;
}
