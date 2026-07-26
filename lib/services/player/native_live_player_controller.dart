import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../models/playback_item.dart';
import 'app_player_controller.dart';

class NativeLivePlayerController extends AppPlayerController {
  static const MethodChannel _channel = MethodChannel(
    'watchio/native_live_player',
  );
  static const EventChannel _events = EventChannel(
    'watchio/native_live_player/events',
  );
  static int _nextId = 0;

  final String _playerId =
      'live_${DateTime.now().microsecondsSinceEpoch}_${_nextId++}';
  StreamSubscription<dynamic>? _eventSub;

  bool _disposed = false;
  bool _isInitialized = false;
  bool _isPlaying = false;
  bool _isBuffering = false;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;
  String? _error;
  double? _aspectRatio;
  bool _hasAudioTrack = false;
  bool _hasVideoTrack = false;
  bool _hasRenderedFirstFrame = false;
  PlaybackItem? _currentItem;

  @override
  bool get isInitialized => _isInitialized;

  @override
  bool get isPlaying => _isPlaying;

  @override
  bool get isBuffering => _isBuffering;

  @override
  Duration get position => _position;

  @override
  Duration get duration => _duration;

  @override
  String? get error => _error;

  @override
  double? get aspectRatio => _aspectRatio;

  @override
  bool get hasAudioTrack => _hasAudioTrack;

  @override
  bool get hasVideoTrack => _hasVideoTrack;

  @override
  bool get hasRenderedFirstFrame => _hasRenderedFirstFrame;

  @override
  PlaybackItem? get currentItem => _currentItem;

  @override
  Future<void> initialize() async {
    if (_disposed || _isInitialized) return;
    _eventSub = _events.receiveBroadcastStream().listen(_handleEvent);
    await _invoke('initialize');
    _isInitialized = true;
    notifyListeners();
  }

  @override
  Future<void> setDataSource(PlaybackItem item) async {
    if (_disposed) return;
    _currentItem = item;
    _error = null;
    _isPlaying = false;
    _isBuffering = true;
    _hasAudioTrack = false;
    _hasVideoTrack = false;
    _hasRenderedFirstFrame = false;
    _position = Duration.zero;
    _duration = Duration.zero;
    notifyListeners();

    final uri = Uri.tryParse(item.url);
    if (uri == null || !uri.hasScheme || !uri.scheme.startsWith('http')) {
      _error = 'Invalid stream URL';
      notifyListeners();
      return;
    }

    await _invoke('setDataSource', {
      'url': item.url,
      'headers': item.headers,
      'startPositionMs': item.startPosition.inMilliseconds,
    });
  }

  @override
  Future<void> play() => _invoke('play');

  @override
  Future<void> pause() => _invoke('pause');

  @override
  Future<void> stop() async {
    if (_disposed) return;
    await _invoke('stop');
    _currentItem = null;
    _isPlaying = false;
    _isBuffering = false;
    _position = Duration.zero;
    _duration = Duration.zero;
    _error = null;
    _hasAudioTrack = false;
    _hasVideoTrack = false;
    _hasRenderedFirstFrame = false;
    notifyListeners();
  }

  @override
  Future<void> seek(Duration position) {
    return _invoke('seek', {'positionMs': position.inMilliseconds});
  }

  @override
  Future<void> setVolume(double volume) {
    return _invoke('setVolume', {'volume': (volume / 100).clamp(0.0, 1.0)});
  }

  @override
  Future<void> setAspectRatio(double? ratio) async {
    _aspectRatio = ratio;
    await _invoke('setAspectRatio', {
      'fit': ratio == null ? 'contain' : 'fill',
    });
    notifyListeners();
  }

  @override
  Future<List<String>> getAudioTracks() async => const ['Default'];

  @override
  Future<void> setAudioTrack(int index) async {}

  @override
  Future<List<String>> getSubtitleTracks() async => const ['None'];

  @override
  Future<void> setSubtitleTrack(int index) async {}

  @override
  Widget buildPlayerView(BuildContext context, {BoxFit? fit}) {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      return const ColoredBox(color: Colors.black);
    }

    final fitName = switch (fit) {
      BoxFit.cover => 'cover',
      BoxFit.fill => 'fill',
      _ => 'contain',
    };

    return RepaintBoundary(
      child: AndroidView(
        key: ValueKey('native-live-player-$_playerId-$fitName'),
        viewType: 'watchio/native_live_player_view',
        creationParams: {'playerId': _playerId, 'fit': fitName},
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }

  Future<void> _invoke(String method, [Map<String, Object?> args = const {}]) {
    if (_disposed && method != 'dispose') return Future.value();
    return _channel.invokeMethod(method, {'playerId': _playerId, ...args});
  }

  void _handleEvent(dynamic event) {
    if (_disposed || event is! Map) return;
    if (event['playerId'] != _playerId) return;

    final wasPlaying = _isPlaying;
    final wasBuffering = _isBuffering;
    final oldError = _error;
    final oldFirstFrame = _hasRenderedFirstFrame;
    final oldPosition = _position;

    _isPlaying = event['isPlaying'] == true;
    _isBuffering = event['isBuffering'] == true;
    _position = Duration(
      milliseconds: (event['positionMs'] as num? ?? 0).toInt(),
    );
    _duration = Duration(
      milliseconds: (event['durationMs'] as num? ?? 0).toInt(),
    );
    _hasAudioTrack = event['hasAudio'] == true;
    _hasVideoTrack = event['hasVideo'] == true;
    _hasRenderedFirstFrame = event['firstFrame'] == true;
    _error = event['error'] as String?;

    final positionMovedEnough =
        (_position - oldPosition).abs() >= const Duration(seconds: 2);
    if (wasPlaying != _isPlaying ||
        wasBuffering != _isBuffering ||
        oldError != _error ||
        oldFirstFrame != _hasRenderedFirstFrame ||
        positionMovedEnough) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _eventSub?.cancel();
    _eventSub = null;
    _channel.invokeMethod('dispose', {'playerId': _playerId});
    super.dispose();
  }
}
