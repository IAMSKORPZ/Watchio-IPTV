import 'package:watchio/models/provider_model.dart';
import 'package:watchio/repositories/provider_repository.dart';
import 'package:watchio/services/performance_service.dart';
import 'package:flutter/material.dart';

class ProviderController extends ChangeNotifier {
  final ProviderRepository repository;

  ProviderController({ProviderRepository? repository})
      : repository = repository ?? SharedPreferencesProviderRepository();

  List<IptvProvider> _providers = [];
  bool _isLoading = false;
  String? _error;

  List<IptvProvider> get providers => List.unmodifiable(_providers);
  bool get isLoading => _isLoading;
  String? get error => _error;

  Future<void> loadProviders() async {
    _setLoading(true);
    _error = null;
    try {
      _providers = await repository.getAllProviders();
    } catch (e) {
      _error = e.toString();
    } finally {
      _setLoading(false);
    }
  }

  Future<bool> saveProvider(IptvProvider provider, {bool isEdit = false}) async {
    _setLoading(true);
    _error = null;
    try {
      if (isEdit) {
        await repository.updateProvider(provider);
      } else {
        await repository.createProvider(provider);
      }
      await loadProviders();
      return true;
    } catch (e) {
      _error = e.toString();
      _setLoading(false);
      return false;
    }
  }

  Future<bool> deleteProvider(String id) async {
    return _runAndReload(() => repository.deleteProvider(id));
  }

  Future<bool> setDefaultProvider(String id) async {
    return _runAndReload(() => repository.setDefaultProvider(id));
  }

  Future<bool> switchProvider(String id) async {
    return _runAndReload(
      () => PerformanceService.track(
        'provider_switch',
        () => repository.switchProvider(id),
        metadata: {'providerId': id},
      ),
    );
  }

  Future<bool> setEnabled(String id, bool enabled) async {
    return _runAndReload(
      () => enabled ? repository.enableProvider(id) : repository.disableProvider(id),
    );
  }

  Future<bool> checkStatus(String id) async {
    return _runAndReload(() => repository.checkProviderStatus(id));
  }

  Future<bool> _runAndReload(Future<dynamic> Function() action) async {
    _setLoading(true);
    _error = null;
    try {
      await action();
      await loadProviders();
      return true;
    } catch (e) {
      _error = e.toString();
      _setLoading(false);
      return false;
    }
  }

  void _setLoading(bool value) {
    _isLoading = value;
    notifyListeners();
  }
}
