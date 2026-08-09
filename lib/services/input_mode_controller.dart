import 'package:flutter/foundation.dart';

import '../repositories/user_preferences.dart';
import '../utils/firestick_performance.dart';

enum DeviceInputMode { mobile, desktop, tv }

extension DeviceInputModeLabel on DeviceInputMode {
  String get storageValue {
    switch (this) {
      case DeviceInputMode.mobile:
        return 'mobile';
      case DeviceInputMode.desktop:
        return 'desktop';
      case DeviceInputMode.tv:
        return 'tv';
    }
  }

  String get title {
    switch (this) {
      case DeviceInputMode.mobile:
        return 'Mobile';
      case DeviceInputMode.desktop:
        return 'Desktop';
      case DeviceInputMode.tv:
        return 'TV';
    }
  }
}

class InputModeController extends ChangeNotifier {
  DeviceInputMode? _mode;
  bool _isLoaded = false;

  DeviceInputMode? get mode => _mode;
  bool get isLoaded => _isLoaded;
  bool get isTvMode => _mode == DeviceInputMode.tv;
  bool get isMobileMode => _mode == DeviceInputMode.mobile;
  bool get isDesktopMode => _mode == DeviceInputMode.desktop;
  bool get hasMode => _mode != null;

  bool get allowPointerInput => !isTvMode || isDesktopPlatform;
  bool get allowTouchGestures => isMobileMode;
  bool get showKeyboardOnFocus => isMobileMode;
  bool get useTvPerformanceProfile => isTvMode;
  bool get isDesktopPlatform =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.windows ||
          defaultTargetPlatform == TargetPlatform.macOS ||
          defaultTargetPlatform == TargetPlatform.linux);

  Future<void> load() async {
    final value = await UserPreferences.getDeviceInputMode();
    _mode = _parse(value);
    _isLoaded = true;
    setFirestickPerformanceMode(isTvMode);
    notifyListeners();
  }

  Future<void> setMode(DeviceInputMode mode) async {
    _mode = mode;
    _isLoaded = true;
    await UserPreferences.setDeviceInputMode(mode.storageValue);
    setFirestickPerformanceMode(isTvMode);
    notifyListeners();
  }

  Future<void> clearMode() async {
    _mode = null;
    _isLoaded = true;
    await UserPreferences.removeDeviceInputMode();
    setFirestickPerformanceMode(false);
    notifyListeners();
  }

  DeviceInputMode? _parse(String? value) {
    switch (value) {
      case 'mobile':
        return DeviceInputMode.mobile;
      case 'desktop':
        return DeviceInputMode.desktop;
      case 'tv':
        return DeviceInputMode.tv;
      default:
        return null;
    }
  }
}
