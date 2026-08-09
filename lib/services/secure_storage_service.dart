import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SecureStorageService {
  SecureStorageService._();

  static final SecureStorageService instance = SecureStorageService._();

  static const FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  // Legacy SharedPreferences keys used this prefix. Keep it for migration.
  static const String _prefix = 'secure_v1_';

  String _providerPasswordKey(String providerId) =>
      '${_prefix}provider_$providerId.password';

  Future<void> saveProviderPassword(String providerId, String? password) async {
    final value = password?.trim();
    final key = _providerPasswordKey(providerId);

    if (value == null || value.isEmpty) {
      await _storage.delete(key: key);
      await _deleteLegacy(key);
      return;
    }
    await _storage.write(key: key, value: value);
    await _deleteLegacy(key);
  }

  Future<String?> readProviderPassword(String providerId) async {
    return _readWithLegacyMigration(_providerPasswordKey(providerId));
  }

  Future<void> deleteProviderPassword(String providerId) async {
    final key = _providerPasswordKey(providerId);
    await _storage.delete(key: key);
    await _deleteLegacy(key);
  }

  Future<void> saveProviderSecret(
    String providerId,
    String name,
    String? value,
  ) async {
    final key = '${_prefix}provider_$providerId.$name';

    if (value == null || value.trim().isEmpty) {
      await _storage.delete(key: key);
      await _deleteLegacy(key);
      return;
    }
    await _storage.write(key: key, value: value.trim());
    await _deleteLegacy(key);
  }

  Future<String?> readProviderSecret(String providerId, String name) async {
    return _readWithLegacyMigration('${_prefix}provider_$providerId.$name');
  }

  Future<void> deleteProviderSecret(String providerId, String name) async {
    final key = '${_prefix}provider_$providerId.$name';
    await _storage.delete(key: key);
    await _deleteLegacy(key);
  }

  Future<String?> _readWithLegacyMigration(String key) async {
    final stored = await _storage.read(key: key);
    if (stored != null) return stored;

    final prefs = await SharedPreferences.getInstance();
    final legacy = prefs.getString(key);
    if (legacy == null || legacy.isEmpty) return null;

    await _storage.write(key: key, value: legacy);
    await prefs.remove(key);
    return legacy;
  }

  Future<void> _deleteLegacy(String key) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(key);
  }
}
