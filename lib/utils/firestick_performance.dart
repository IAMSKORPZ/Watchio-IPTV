import 'package:flutter/widgets.dart';

const bool _firestickPerformanceOverride = bool.fromEnvironment(
  'WATCHIO_FIRESTICK_MODE',
);

bool _inputTvPerformanceMode = false;

void setFirestickPerformanceMode(bool enabled) {
  _inputTvPerformanceMode = enabled;
}

bool get firestickPerformanceMode =>
    _firestickPerformanceOverride || _inputTvPerformanceMode;

Duration perfDuration(Duration normal) =>
    firestickPerformanceMode ? Duration.zero : normal;

double perfScale(double focusedScale) =>
    firestickPerformanceMode ? 1.0 : focusedScale;

double perfBlur(double normal) => firestickPerformanceMode ? 0.0 : normal;

ImageProvider perfNetworkImage(
  String url, {
  int cacheWidth = 1920,
  int cacheHeight = 1080,
}) {
  final provider = NetworkImage(url);
  if (!firestickPerformanceMode) return provider;
  return ResizeImage.resizeIfNeeded(cacheWidth, cacheHeight, provider);
}
