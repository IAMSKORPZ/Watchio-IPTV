import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import '../../controllers/favorites_controller.dart';
import '../../models/content_type.dart';
import '../../models/playback_item.dart';
import '../../models/player_engine.dart';
import '../../models/playlist_content_model.dart';
import '../../repositories/user_preferences.dart';
import '../../services/player/app_player_controller.dart';
import '../../services/player/player_factory.dart';
import '../../services/watch_history_service.dart';
import '../../models/watch_history.dart';
import '../../services/app_state.dart';
import '../../services/epg_storage_service.dart';
import '../../services/playback_url_resolver.dart';
import '../../shared/widgets/glass_panel.dart';
import '../../shared/widgets/app_card.dart';
import 'package:window_manager/window_manager.dart';

class UnifiedPlayerScreen extends StatefulWidget {
  final ContentItem contentItem;
  final List<ContentItem>? queue;
  final AppPlayerController? externalController;

  const UnifiedPlayerScreen({
    super.key,
    required this.contentItem,
    this.queue,
    this.externalController,
  });

  @override
  State<UnifiedPlayerScreen> createState() => _UnifiedPlayerScreenState();
}

class _UnifiedPlayerScreenState extends State<UnifiedPlayerScreen> {
  late AppPlayerController _playerController;
  late PlaybackItem _currentPlaybackItem;
  bool _showControls = true;
  bool _showChannelList = false;
  Timer? _controlsTimer;
  final WatchHistoryService _historyService = WatchHistoryService();
  final FavoritesController _favoritesController = FavoritesController();
  final EpgStorageService _epgService = EpgStorageService();

  bool _isFavorite = false;
  List<EpgProgramWindow> _epgPrograms = [];
  double _volume = 0.5;
  double _brightness = 0.5;
  bool _showSideSliders = false;
  Timer? _sideSlidersTimer;
  int _lastHistorySaveSecond = -1;
  String? _lastOverlayState;
  final FocusNode _playerFocusNode = FocusNode(debugLabel: 'fullscreenPlayer');
  final FocusNode _playPauseFocusNode = FocusNode(
    debugLabel: 'fullscreenPlayPause',
  );
  final FocusNode _backFocusNode = FocusNode(debugLabel: 'fullscreenBack');
  final FocusNode _topFavoriteFocusNode = FocusNode(
    debugLabel: 'fullscreenTopFavorite',
  );
  final FocusNode _moreFocusNode = FocusNode(debugLabel: 'fullscreenMore');
  final FocusNode _previousFocusNode = FocusNode(
    debugLabel: 'fullscreenPrevious',
  );
  final FocusNode _nextFocusNode = FocusNode(debugLabel: 'fullscreenNext');
  final FocusNode _channelsFocusNode = FocusNode(
    debugLabel: 'fullscreenChannels',
  );
  final FocusNode _epgFocusNode = FocusNode(debugLabel: 'fullscreenEpg');
  final FocusNode _subtitlesFocusNode = FocusNode(
    debugLabel: 'fullscreenSubtitles',
  );
  final FocusNode _audioFocusNode = FocusNode(debugLabel: 'fullscreenAudio');
  final FocusNode _ratioFocusNode = FocusNode(debugLabel: 'fullscreenRatio');
  final FocusNode _favoriteFocusNode = FocusNode(
    debugLabel: 'fullscreenFavorite',
  );
  final FocusNode _bottomMoreFocusNode = FocusNode(
    debugLabel: 'fullscreenBottomMore',
  );
  final FocusScopeNode _controlsFocusScope = FocusScopeNode(
    debugLabel: 'fullscreenControls',
  );
  double _lastNonZeroVolume = 0.5;

  @override
  void initState() {
    super.initState();
    _currentPlaybackItem = PlaybackItem.fromContentItem(widget.contentItem);

    if (widget.externalController != null) {
      _playerController = widget.externalController!;
      _playerController.addListener(_onPlayerStateChanged);
      if (_currentPlaybackItem.isLive) _fetchEpg();
    } else {
      _initPlayer();
    }

    _startControlsTimer();
    _checkFavorite();
    _loadInitialSettings();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _playerFocusNode.requestFocus();
    });
    HardwareKeyboard.instance.addHandler(_handleGlobalFullscreenKeyEvent);
  }

  Future<void> _loadInitialSettings() async {
    final vol = await UserPreferences.getVolume();
    if (mounted) {
      setState(() {
        _volume = (vol / 100.0).clamp(0.0, 1.0);
        if (_volume > 0) _lastNonZeroVolume = _volume;
      });
    }
  }

  Future<void> _checkFavorite() async {
    final fav = await _favoritesController.isFavorite(
      _currentPlaybackItem.id,
      _currentPlaybackItem.contentType,
    );
    if (mounted) {
      setState(() {
        _isFavorite = fav;
      });
    }
  }

  Future<void> _toggleFavorite() async {
    final res = await _favoritesController.toggleFavorite(
      _currentPlaybackItem.originalItem!,
    );
    if (mounted) {
      setState(() {
        _isFavorite = res;
      });
    }
  }

  Future<void> _initPlayer() async {
    if (AppState.currentPlaylist == null) {
      debugPrint('UnifiedPlayer Error: AppState.currentPlaylist is NULL');
      return;
    }

    final engineStr = await UserPreferences.getPlayerEngine();
    final engine = PlayerEngine.values.firstWhere(
      (e) => e.name == engineStr,
      orElse: () => PlayerEngine.auto,
    );

    _playerController = PlayerFactory.create(
      engine,
      contentType: _currentPlaybackItem.contentType,
    );
    await _playerController.initialize();

    // Apply default aspect ratio
    final ratioStr = await UserPreferences.getPlayerAspectRatio();
    _applyAspectRatio(ratioStr);

    Duration startPos = Duration.zero;
    if (_currentPlaybackItem.contentType != ContentType.liveStream) {
      final history = await _historyService.getWatchHistory(
        AppState.currentPlaylist!.id,
        _currentPlaybackItem.id,
      );
      if (history != null && history.watchDuration != null) {
        startPos = history.watchDuration!;
      }
    }

    final playItem = _currentPlaybackItem.copyWith(startPosition: startPos);

    // Resolve URL asynchronously if needed (for secure credentials)
    String? resolvedUrl;
    if (AppState.currentPlaylist != null) {
      resolvedUrl = await PlaybackUrlResolver.resolveUrl(
        item: widget.contentItem,
        playlist: AppState.currentPlaylist!,
      );
    }

    if (resolvedUrl == null || resolvedUrl.isEmpty) {
      debugPrint('UnifiedPlayer: CRITICAL - URL resolution failed');
      // The player will show error based on the original (broken) URL or we can set an error manually
    }

    final playItemWithUrl = playItem.copyWith(url: resolvedUrl ?? playItem.url);

    debugPrint('--- WATCHIO PLAYBACK PIPELINE AUDIT ---');
    debugPrint('Provider: ${AppState.currentPlaylist!.name}');
    debugPrint('Provider Type: ${AppState.currentPlaylist!.type}');
    debugPrint('Content Name: ${playItemWithUrl.title}');
    debugPrint('Content Type: ${playItemWithUrl.contentType}');
    debugPrint('Stream ID: ${playItemWithUrl.id}');
    debugPrint(
      'Episode ID: ${playItemWithUrl.originalItem?.season != null ? playItemWithUrl.id : "N/A"}',
    );
    debugPrint('Generated URL: [redacted]');
    debugPrint('User-Agent: ${playItemWithUrl.headers['User-Agent']}');
    debugPrint('Referer: ${playItemWithUrl.headers['Referer']}');
    debugPrint('Playback Engine: ${engine.name}');
    debugPrint('--------------------------------------');

    if (playItemWithUrl.url.isEmpty ||
        (!playItemWithUrl.url.startsWith('http') &&
            playItemWithUrl.url == playItemWithUrl.id)) {
      debugPrint('UnifiedPlayer: CRITICAL - Invalid URL generated');
    }

    await _playerController.setDataSource(playItemWithUrl);
    _playerController.addListener(_onPlayerStateChanged);

    if (_currentPlaybackItem.isLive) {
      _fetchEpg();
    }
  }

  Future<void> _fetchEpg() async {
    final ls = _currentPlaybackItem.originalItem?.liveStream;
    if (ls == null || ls.epgChannelId.isEmpty) return;

    try {
      final programs = await _epgService.getProgramsForWindow(
        playlistId: AppState.currentPlaylist!.id,
        epgChannelId: ls.epgChannelId,
        start: DateTime.now(),
        end: DateTime.now().add(const Duration(hours: 6)),
        limit: 2,
      );
      if (mounted) {
        setState(() {
          _epgPrograms = programs;
        });
      }
    } catch (e) {
      debugPrint('EPG Fetch Error: $e');
    }
  }

  void _applyAspectRatio(String ratioStr) {
    double? ratio;
    switch (ratioStr) {
      case '16:9':
        ratio = 16 / 9;
        break;
      case '4:3':
        ratio = 4 / 3;
        break;
      case 'fill':
        ratio = MediaQuery.of(context).size.aspectRatio;
        break;
      case 'stretch':
        ratio = MediaQuery.of(context).size.aspectRatio;
        break;
      case 'fit':
        ratio = null;
        break;
      default:
        ratio = null;
    }
    _playerController.setAspectRatio(ratio);
  }

  void _onPlayerStateChanged() {
    if (mounted) {
      setState(() {});
    }
    // Save history periodically for VOD, or once for Live to mark as "Recent"
    final currentSecond = _playerController.position.inSeconds;
    if (_playerController.isPlaying &&
        currentSecond % 10 == 0 &&
        currentSecond != _lastHistorySaveSecond) {
      _lastHistorySaveSecond = currentSecond;
      _saveHistory();
    }
  }

  Future<void> _saveHistory() async {
    if (AppState.currentPlaylist == null) return;
    await _historyService.saveWatchHistory(
      WatchHistory(
        playlistId: AppState.currentPlaylist!.id,
        contentType: _currentPlaybackItem.contentType,
        streamId: _currentPlaybackItem.id,
        lastWatched: DateTime.now(),
        title: _currentPlaybackItem.title,
        imagePath: _currentPlaybackItem.imagePath,
        totalDuration: _currentPlaybackItem.isLive
            ? Duration.zero
            : _playerController.duration,
        watchDuration: _currentPlaybackItem.isLive
            ? Duration.zero
            : _playerController.position,
      ),
    );
  }

  void _startControlsTimer() {
    _controlsTimer?.cancel();
    _controlsTimer = Timer(const Duration(seconds: 5), () {
      if (mounted &&
          _playerController.isPlaying &&
          _showControls &&
          !_controlsFocusScope.hasFocus) {
        setState(() {
          _showControls = false;
        });
        _controlsFocusScope.unfocus();
        _playerFocusNode.requestFocus();
      }
    });
  }

  void _handlePlayerSurfaceTap() {
    if (hasError) return;
    if (_showControls) {
      _hideControls();
    } else {
      _showControlsTemporarily();
    }
  }

  Future<void> _toggleDesktopFullscreen() async {
    if (kIsWeb ||
        !(defaultTargetPlatform == TargetPlatform.windows ||
            defaultTargetPlatform == TargetPlatform.linux ||
            defaultTargetPlatform == TargetPlatform.macOS)) {
      return;
    }
    await windowManager.setFullScreen(!await windowManager.isFullScreen());
  }

  void _showControlsTemporarily() {
    if (hasError) return;
    if (!_showControls) {
      setState(() {
        _showControls = true;
        _showChannelList = false;
      });
      _focusPlayPause();
    }
    _startControlsTimer();
  }

  void _hideControls() {
    if (_showControls) {
      setState(() {
        _showControls = false;
      });
      _controlsTimer?.cancel();
      _controlsFocusScope.unfocus();
      _playerFocusNode.requestFocus();
    }
  }

  void _focusPlayPause() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_showControls) return;
      _playPauseFocusNode.requestFocus();
    });
  }

  List<FocusNode> get _overlayFocusNodes {
    final nodes = <FocusNode>[
      _backFocusNode,
      if (_currentPlaybackItem.isLive) _topFavoriteFocusNode,
      _moreFocusNode,
      _previousFocusNode,
      _playPauseFocusNode,
      _nextFocusNode,
      _channelsFocusNode,
      if (_currentPlaybackItem.isLive) _epgFocusNode,
      _subtitlesFocusNode,
      _audioFocusNode,
      _ratioFocusNode,
      _favoriteFocusNode,
      _bottomMoreFocusNode,
    ];
    return nodes.where((node) => node.context != null).toList();
  }

  int _focusedOverlayIndex() {
    final nodes = _overlayFocusNodes;
    final index = nodes.indexWhere((node) => node.hasFocus);
    return index == -1 ? nodes.indexOf(_playPauseFocusNode) : index;
  }

  void _focusOverlayIndex(int index) {
    final nodes = _overlayFocusNodes;
    if (nodes.isEmpty) {
      _playerFocusNode.requestFocus();
      return;
    }
    nodes[index.clamp(0, nodes.length - 1)].requestFocus();
  }

  void _moveOverlayFocus(int delta) {
    _showControlsTemporarily();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final nodes = _overlayFocusNodes;
      if (nodes.isEmpty) return;
      final current = _focusedOverlayIndex();
      _focusOverlayIndex((current + delta) % nodes.length);
    });
  }

  void _focusUpperOverlay() {
    _showControlsTemporarily();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (_moreFocusNode.context != null) {
        _moreFocusNode.requestFocus();
      } else {
        _backFocusNode.requestFocus();
      }
    });
  }

  void _focusLowerOverlay() {
    _showControlsTemporarily();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (_channelsFocusNode.context != null) {
        _channelsFocusNode.requestFocus();
      } else {
        _playPauseFocusNode.requestFocus();
      }
    });
  }

  void _toggleMute() {
    final targetVolume = _volume > 0 ? 0.0 : _lastNonZeroVolume;
    if (_volume > 0) _lastNonZeroVolume = _volume;
    setState(() => _volume = targetVolume);
    _playerController.setVolume(targetVolume * 100);
    _showSliders();
    _showControlsTemporarily();
  }

  void _exitFullscreen() {
    if (!mounted) return;
    debugPrint('UnifiedPlayer: Exiting fullscreen');
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.pop();
    }
  }

  void _toggleChannelList() {
    setState(() {
      _showChannelList = !_showChannelList;
      if (_showChannelList) {
        _showControls = false;
        _controlsTimer?.cancel();
      }
    });
  }

  void _showSliders() {
    setState(() {
      _showSideSliders = true;
    });
    _sideSlidersTimer?.cancel();
    _sideSlidersTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) {
        setState(() {
          _showSideSliders = false;
        });
      }
    });
  }

  void _switchChannel(int delta) {
    if (widget.queue == null || widget.queue!.isEmpty) return;
    final idx = widget.queue!.indexWhere(
      (item) => item.id == _currentPlaybackItem.id,
    );
    if (idx == -1) return;
    final nextIdx = (idx + delta).clamp(0, widget.queue!.length - 1);
    if (nextIdx == idx) return;
    _playItem(widget.queue![nextIdx]);
  }

  void _playItem(ContentItem item) async {
    final playlist = AppState.currentPlaylist;
    if (playlist == null) return;

    final resolvedUrl = await PlaybackUrlResolver.resolveUrl(
      item: item,
      playlist: playlist,
    );

    if (mounted) {
      setState(() {
        _currentPlaybackItem = PlaybackItem.fromContentItem(
          item,
        ).copyWith(url: resolvedUrl);
        _checkFavorite();
        _epgPrograms = [];
      });
      _playerController.setDataSource(_currentPlaybackItem);
      if (_currentPlaybackItem.isLive) {
        _fetchEpg();
      }
      _startControlsTimer();
    }
  }

  void _seekRelative(int seconds) {
    if (_currentPlaybackItem.isLive) return;
    final target = _playerController.position + Duration(seconds: seconds);
    Duration clamped = target;
    if (target < Duration.zero) {
      clamped = Duration.zero;
    } else if (target > _playerController.duration) {
      clamped = _playerController.duration;
    }
    _playerController.seek(clamped);
    _showControlsTemporarily();
  }

  bool _handleGlobalFullscreenKeyEvent(KeyEvent event) {
    return _handleFullscreenKey(event) == KeyEventResult.handled;
  }

  KeyEventResult _handleFullscreenKeyEvent(FocusNode node, KeyEvent event) {
    return _handleFullscreenKey(event);
  }

  KeyEventResult _handleFullscreenKey(KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }

    final key = event.logicalKey;
    debugPrint('UnifiedPlayer Key: ${key.keyLabel} (${key.keyId})');

    final isBack =
        key == LogicalKeyboardKey.escape ||
        key == LogicalKeyboardKey.backspace ||
        key == LogicalKeyboardKey.goBack ||
        key == LogicalKeyboardKey.browserBack;
    final isSelect =
        key == LogicalKeyboardKey.select ||
        key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.space ||
        key == LogicalKeyboardKey.gameButtonA;
    final isShiftPressed = HardwareKeyboard.instance.logicalKeysPressed.any(
      (pressed) =>
          pressed == LogicalKeyboardKey.shiftLeft ||
          pressed == LogicalKeyboardKey.shiftRight,
    );
    final focusedChild =
        _showControls &&
        _controlsFocusScope.hasFocus &&
        !_playerFocusNode.hasPrimaryFocus;

    if (isBack) {
      if (_showChannelList) {
        setState(() => _showChannelList = false);
        _showControlsTemporarily();
        return KeyEventResult.handled;
      }
      _exitFullscreen();
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.keyF) {
      _exitFullscreen();
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.keyM) {
      _toggleMute();
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.keyK ||
        key == LogicalKeyboardKey.mediaPlayPause) {
      _togglePlayPause();
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.keyJ ||
        key == LogicalKeyboardKey.mediaRewind) {
      _seekRelative(-10);
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.keyL ||
        key == LogicalKeyboardKey.mediaFastForward) {
      _seekRelative(30);
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.tab) {
      if (!_showControls) {
        _showControlsTemporarily();
        return KeyEventResult.handled;
      }
      _moveOverlayFocus(isShiftPressed ? -1 : 1);
      return KeyEventResult.handled;
    }

    if (isSelect) {
      if (!_showControls) {
        _showControlsTemporarily();
        return KeyEventResult.handled;
      }
      if (focusedChild) return KeyEventResult.ignored;
      _togglePlayPause();
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.channelUp) {
      _switchChannel(1);
      _showControlsTemporarily();
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.channelDown) {
      _switchChannel(-1);
      _showControlsTemporarily();
      return KeyEventResult.handled;
    }

    if (!_showControls) {
      if (key == LogicalKeyboardKey.arrowLeft) {
        _seekRelative(-10);
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowRight) {
        _seekRelative(30);
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowUp ||
          key == LogicalKeyboardKey.arrowDown) {
        if (key == LogicalKeyboardKey.arrowUp) {
          _focusUpperOverlay();
        } else {
          _focusLowerOverlay();
        }
        return KeyEventResult.handled;
      }
    }

    if (_showControls && key == LogicalKeyboardKey.arrowLeft) {
      _moveOverlayFocus(-1);
      return KeyEventResult.handled;
    }

    if (_showControls && key == LogicalKeyboardKey.arrowRight) {
      _moveOverlayFocus(1);
      return KeyEventResult.handled;
    }

    if (_showControls && key == LogicalKeyboardKey.arrowUp) {
      _focusUpperOverlay();
      return KeyEventResult.handled;
    }

    if (_showControls && key == LogicalKeyboardKey.arrowDown) {
      _focusLowerOverlay();
      return KeyEventResult.handled;
    }

    return KeyEventResult.ignored;
  }

  void _togglePlayPause() {
    if (hasError) return;
    _showControlsTemporarily();
    if (_playerController.isPlaying) {
      _playerController.pause();
    } else {
      _playerController.play();
    }
    _startControlsTimer();
  }

  bool get hasError =>
      _playerController.error != null && !_playerController.isPlaying;
  bool get hasFatalPlaybackError => hasError;

  String get _overlayState {
    if (hasFatalPlaybackError) return 'failed';
    if (_playerController.isBuffering) return 'buffering';
    if (_playerController.isPlaying &&
        !_playerController.hasRenderedFirstFrame &&
        !_playerController.hasVideoTrack) {
      return 'buffering-video';
    }
    if (!_playerController.isPlaying &&
        _playerController.error == null &&
        !_playerController.hasRenderedFirstFrame) {
      return 'connecting';
    }
    return 'ready';
  }

  void _logOverlayState() {
    final state = _overlayState;
    if (_lastOverlayState == state) return;
    debugPrint(
      'UnifiedPlayer Overlay: ${_lastOverlayState ?? "none"} -> $state '
      '(playing=${_playerController.isPlaying}, buffering=${_playerController.isBuffering}, '
      'audio=${_playerController.hasAudioTrack}, video=${_playerController.hasVideoTrack}, '
      'firstFrame=${_playerController.hasRenderedFirstFrame}, error=${_playerController.error ?? "none"})',
    );
    _lastOverlayState = state;
  }

  @override
  void dispose() {
    HardwareKeyboard.instance.removeHandler(_handleGlobalFullscreenKeyEvent);
    _controlsTimer?.cancel();
    _sideSlidersTimer?.cancel();
    if (_currentPlaybackItem.contentType != ContentType.liveStream) {
      _saveHistory();
    }
    _playerController.removeListener(_onPlayerStateChanged);
    if (widget.externalController == null) {
      _playerController.dispose();
    }
    _playerFocusNode.dispose();
    _playPauseFocusNode.dispose();
    _backFocusNode.dispose();
    _topFavoriteFocusNode.dispose();
    _moreFocusNode.dispose();
    _previousFocusNode.dispose();
    _nextFocusNode.dispose();
    _channelsFocusNode.dispose();
    _epgFocusNode.dispose();
    _subtitlesFocusNode.dispose();
    _audioFocusNode.dispose();
    _ratioFocusNode.dispose();
    _favoriteFocusNode.dispose();
    _bottomMoreFocusNode.dispose();
    _controlsFocusScope.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _logOverlayState();
    return Scaffold(
      backgroundColor: Colors.black,
      body: PopScope(
        canPop: !_showChannelList,
        onPopInvokedWithResult: (didPop, result) {
          if (didPop) return;
          if (_showChannelList) {
            setState(() => _showChannelList = false);
            _showControlsTemporarily();
          }
        },
        child: Shortcuts(
          shortcuts: const {
            SingleActivator(LogicalKeyboardKey.space): _PlayPauseIntent(),
            SingleActivator(LogicalKeyboardKey.keyK): _PlayPauseIntent(),
            SingleActivator(LogicalKeyboardKey.keyJ): _SeekBackIntent(),
            SingleActivator(LogicalKeyboardKey.keyL): _SeekForwardIntent(),
            SingleActivator(LogicalKeyboardKey.mediaPlay): _PlayPauseIntent(),
            SingleActivator(LogicalKeyboardKey.mediaPause): _PlayPauseIntent(),
          },
          child: Actions(
            actions: {
              ActivateIntent: CallbackAction<ActivateIntent>(
                onInvoke: (_) {
                  if (hasError) return null;
                  if (!_showControls) {
                    _showControlsTemporarily();
                  } else {
                    _togglePlayPause();
                  }
                  return null;
                },
              ),
              _PlayPauseIntent: CallbackAction<_PlayPauseIntent>(
                onInvoke: (_) {
                  if (hasError) return null;
                  _togglePlayPause();
                  return null;
                },
              ),
              _ShowControlsIntent: CallbackAction<_ShowControlsIntent>(
                onInvoke: (_) {
                  _showControlsTemporarily();
                  return null;
                },
              ),
              _HideControlsIntent: CallbackAction<_HideControlsIntent>(
                onInvoke: (_) {
                  _hideControls();
                  return null;
                },
              ),
              _SeekBackIntent: CallbackAction<_SeekBackIntent>(
                onInvoke: (_) {
                  if (hasError) return null;
                  if (!_currentPlaybackItem.isLive) {
                    _seekRelative(-10);
                  }
                  return null;
                },
              ),
              _SeekForwardIntent: CallbackAction<_SeekForwardIntent>(
                onInvoke: (_) {
                  if (hasError) return null;
                  if (!_currentPlaybackItem.isLive) {
                    _seekRelative(30);
                  }
                  return null;
                },
              ),
              _ChannelUpIntent: CallbackAction<_ChannelUpIntent>(
                onInvoke: (_) {
                  _switchChannel(1);
                  return null;
                },
              ),
              _ChannelDownIntent: CallbackAction<_ChannelDownIntent>(
                onInvoke: (_) {
                  _switchChannel(-1);
                  return null;
                },
              ),
            },
            child: FocusTraversalGroup(
              policy: OrderedTraversalPolicy(),
              child: Focus(
                focusNode: _playerFocusNode,
                autofocus: true,
                onKeyEvent: _handleFullscreenKeyEvent,
                child: _buildPlayerStack(),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPlayerStack() {
    return Stack(
      children: [
        // VIDEO LAYER (Always visible)
        Positioned.fill(
          child: MouseRegion(
            onHover: (_) => _showControlsTemporarily(),
            child: GestureDetector(
              onTap: _handlePlayerSurfaceTap,
              onDoubleTap: _toggleDesktopFullscreen,
              behavior: HitTestBehavior.opaque,
              child: _playerController.buildPlayerView(context),
            ),
          ),
        ),

        if (_overlayState == 'connecting' ||
            _overlayState == 'buffering' ||
            _overlayState == 'buffering-video')
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(color: Color(0xFFC12CFF)),
                const SizedBox(height: 12),
                Text(
                  _overlayState == 'connecting'
                      ? 'Connecting...'
                      : 'Buffering Video...',
                  style: const TextStyle(
                    color: Colors.white70,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),

        if (hasFatalPlaybackError) _buildErrorOverlay(),

        // Sidebar Sliders - TiviMate style: only when adjusting
        if (_showSideSliders && !hasFatalPlaybackError) ...[
          _buildSideSlider(true), // Brightness
          _buildSideSlider(false), // Volume
        ],

        // CONTROLS LAYER (Overlay with fade)
        AnimatedOpacity(
          opacity: (_showControls && !hasFatalPlaybackError) ? 1.0 : 0.0,
          duration: const Duration(milliseconds: 300),
          child: IgnorePointer(
            ignoring: !_showControls || hasFatalPlaybackError,
            child: Stack(
              children: [
                // Background Dark Overlay
                Positioned.fill(
                  child: GestureDetector(
                    onTap: _hideControls,
                    behavior: HitTestBehavior.opaque,
                    child: Container(
                      color: Colors.black.withValues(alpha: 0.3),
                    ),
                  ),
                ),
                FocusTraversalGroup(
                  policy: OrderedTraversalPolicy(),
                  child: FocusScope(
                    node: _controlsFocusScope,
                    child: _buildPremiumUI(),
                  ),
                ),
              ],
            ),
          ),
        ),

        if (_showChannelList) _buildChannelListOverlay(),
      ],
    );
  }

  Widget _buildPremiumUI() {
    return Positioned.fill(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final isLargeScreen = constraints.maxWidth > 800;
          return Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.black.withValues(alpha: 0.85),
                  Colors.transparent,
                  Colors.transparent,
                  Colors.black.withValues(alpha: 0.85),
                ],
                stops: const [0.0, 0.25, 0.75, 1.0],
              ),
            ),
            child: SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Column(
                  children: [
                    _buildTopBar(isLargeScreen),
                    const Spacer(),
                    _buildCenterControls(isLargeScreen),
                    const Spacer(),
                    _buildBottomSection(isLargeScreen),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildTopBar(bool isLargeScreen) {
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          _CircleBtn(
            icon: Icons.arrow_back_rounded,
            size: isLargeScreen ? 44 : 36,
            focusNode: _backFocusNode,
            onTap: _exitFullscreen,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  _currentPlaybackItem.title,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: isLargeScreen ? 20 : 16,
                    fontWeight: FontWeight.w900,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                if (_currentPlaybackItem.subtitle != null)
                  Text(
                    _currentPlaybackItem.subtitle!,
                    style: const TextStyle(
                      color: Color(0xFF00B7FF),
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
              ],
            ),
          ),
          const _ClockWidget(),
          const SizedBox(width: 20),
          if (_currentPlaybackItem.isLive) ...[
            _CircleBtn(
              icon: _isFavorite
                  ? Icons.favorite_rounded
                  : Icons.favorite_border_rounded,
              iconColor: _isFavorite ? Colors.redAccent : null,
              focusNode: _topFavoriteFocusNode,
              onTap: _toggleFavorite,
            ),
            const SizedBox(width: 12),
          ],
          _CircleBtn(
            icon: Icons.more_vert_rounded,
            size: isLargeScreen ? 44 : 36,
            focusNode: _moreFocusNode,
            onTap: _showMoreMenu,
          ),
        ],
      ),
    );
  }

  Widget _buildCenterControls(bool isLargeScreen) {
    final btnSize = isLargeScreen ? 80.0 : 64.0;
    final isLive = _currentPlaybackItem.isLive;

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (isLive)
          _LargeControlBtn(
            icon: Icons.skip_previous_rounded,
            size: btnSize * 0.7,
            focusNode: _previousFocusNode,
            onTap: () => _switchChannel(-1),
          )
        else
          _LargeControlBtn(
            icon: Icons.replay_10_rounded,
            size: btnSize * 0.7,
            focusNode: _previousFocusNode,
            onTap: () => _seekRelative(-10),
          ),
        const SizedBox(width: 32),
        _LargeControlBtn(
          icon: _playerController.isPlaying
              ? Icons.pause_rounded
              : Icons.play_arrow_rounded,
          size: btnSize,
          isPrimary: true,
          focusNode: _playPauseFocusNode,
          onTap: () {
            _togglePlayPause();
          },
        ),
        const SizedBox(width: 32),
        if (isLive)
          _LargeControlBtn(
            icon: Icons.skip_next_rounded,
            size: btnSize * 0.7,
            focusNode: _nextFocusNode,
            onTap: () => _switchChannel(1),
          )
        else
          _LargeControlBtn(
            icon: Icons.forward_30_rounded,
            size: btnSize * 0.7,
            focusNode: _nextFocusNode,
            onTap: () => _seekRelative(30),
          ),
      ],
    );
  }

  Widget _buildBottomSection(bool isLargeScreen) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_currentPlaybackItem.isLive)
          _buildCompactEpgInfo(isLargeScreen)
        else
          _buildVodProgress(isLargeScreen),
        const SizedBox(height: 12),
        _buildActionRow(),
        const SizedBox(height: 8),
      ],
    );
  }

  Widget _buildCompactEpgInfo(bool isLargeScreen) {
    final current = _epgPrograms.isNotEmpty ? _epgPrograms[0] : null;
    final next = _epgPrograms.length > 1 ? _epgPrograms[1] : null;

    double progress = 0.0;
    if (current != null) {
      final total = current.end.difference(current.start).inSeconds;
      final elapsed = DateTime.now().difference(current.start).inSeconds;
      progress = (elapsed / total).clamp(0.0, 1.0);
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.07),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        children: [
          if (_currentPlaybackItem.imagePath.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(right: 16),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Image.network(
                  _currentPlaybackItem.imagePath,
                  width: 44,
                  height: 44,
                  fit: BoxFit.contain,
                  errorBuilder: (ctx, err, st) => const Icon(
                    Icons.live_tv,
                    color: Colors.white24,
                    size: 28,
                  ),
                ),
              ),
            ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  current?.title ?? 'No Programme Information',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: isLargeScreen ? 16 : 14,
                    fontWeight: FontWeight.bold,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(2),
                  child: LinearProgressIndicator(
                    value: progress,
                    minHeight: 3,
                    backgroundColor: Colors.white10,
                    valueColor: const AlwaysStoppedAnimation(Color(0xFFC12CFF)),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  next != null ? 'Next: ${next.title}' : 'Next: No info',
                  style: const TextStyle(color: Colors.white54, fontSize: 12),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          const Text(
            'LIVE',
            style: TextStyle(
              color: Colors.redAccent,
              fontWeight: FontWeight.w900,
              fontSize: 12,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildVodProgress(bool isLargeScreen) {
    final pos = _playerController.position;
    final dur = _playerController.duration;
    final progress = dur.inMilliseconds > 0
        ? pos.inMilliseconds / dur.inMilliseconds
        : 0.0;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            activeTrackColor: const Color(0xFFC12CFF),
            inactiveTrackColor: Colors.white24,
            thumbColor: Colors.white,
            trackHeight: 4,
            overlayShape: SliderComponentShape.noOverlay,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
          ),
          child: Slider(
            value: progress.clamp(0.0, 1.0),
            onChanged: (v) {
              _playerController.seek(
                Duration(milliseconds: (v * dur.inMilliseconds).toInt()),
              );
              _startControlsTimer();
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _formatDuration(pos),
                style: const TextStyle(color: Colors.white70, fontSize: 11),
              ),
              Text(
                _formatDuration(dur),
                style: const TextStyle(color: Colors.white70, fontSize: 11),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildActionRow() {
    final isLive = _currentPlaybackItem.isLive;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _ActionBtn(
            icon: Icons.list_rounded,
            label: 'CHANNELS',
            focusNode: _channelsFocusNode,
            onTap: _toggleChannelList,
          ),
          if (isLive)
            _ActionBtn(
              icon: Icons.calendar_view_day_rounded,
              label: 'EPG',
              focusNode: _epgFocusNode,
              onTap: _showEpgModal,
            ),
          _ActionBtn(
            icon: Icons.subtitles_rounded,
            label: 'SUBTITLES',
            focusNode: _subtitlesFocusNode,
            onTap: _showSubtitleMenu,
          ),
          _ActionBtn(
            icon: Icons.audiotrack_rounded,
            label: 'AUDIO',
            focusNode: _audioFocusNode,
            onTap: _showAudioMenu,
          ),
          _ActionBtn(
            icon: Icons.aspect_ratio_rounded,
            label: 'RATIO',
            focusNode: _ratioFocusNode,
            onTap: _showAspectRatioMenu,
          ),
          _ActionBtn(
            icon: _isFavorite
                ? Icons.favorite_rounded
                : Icons.favorite_border_rounded,
            label: 'FAVOURITE',
            iconColor: _isFavorite ? Colors.redAccent : null,
            focusNode: _favoriteFocusNode,
            onTap: _toggleFavorite,
          ),
          _ActionBtn(
            icon: Icons.settings_rounded,
            label: 'MORE',
            focusNode: _bottomMoreFocusNode,
            onTap: _showMoreMenu,
          ),
        ],
      ),
    );
  }

  void _showMoreMenu() {
    _showTrackMenu(
      'More Actions',
      [
        'Audio Tracks',
        'Subtitles',
        'Aspect Ratio',
        'Catchup',
        'Multi-View',
        'Player Settings',
      ],
      (idx) {
        switch (idx) {
          case 0:
            _showAudioMenu();
            break;
          case 1:
            _showSubtitleMenu();
            break;
          case 2:
            _showAspectRatioMenu();
            break;
          case 3:
            _showTrackMenu('Catchup', ['Last 24 Hours', 'Last 7 Days'], (i) {});
            break;
          case 4:
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Multi-View coming soon')),
            );
            break;
          case 5:
            _showTrackMenu('Player Settings', [
              'Hardware Decoding',
              'Buffer Size',
              'Network Timeout',
            ], (i) {});
            break;
        }
      },
    );
  }

  Widget _buildSideSlider(bool isBrightness) {
    return Positioned(
      left: isBrightness ? 24 : null,
      right: !isBrightness ? 24 : null,
      top: 0,
      bottom: 0,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              isBrightness
                  ? Icons.brightness_6_rounded
                  : Icons.volume_up_rounded,
              color: Colors.white70,
              size: 16,
            ),
            const SizedBox(height: 8),
            Container(
              height: 140,
              width: 32,
              decoration: BoxDecoration(
                color: const Color(0xAA30274F), // Standard glass bottom color
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
              ),
              child: RotatedBox(
                quarterTurns: 3,
                child: Slider(
                  value: isBrightness ? _brightness : _volume,
                  activeColor: const Color(0xFFC12CFF),
                  inactiveColor: Colors.white10,
                  onChanged: (v) {
                    setState(() {
                      if (isBrightness) {
                        _brightness = v;
                      } else {
                        _volume = v;
                      }
                    });
                    if (!isBrightness) {
                      _playerController.setVolume(v * 100);
                    }
                    _showSliders();
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorOverlay() {
    debugPrint('PLAYER ERROR: ${_playerController.error}');

    return Positioned.fill(
      child: Container(
        color: Colors.black,
        child: Stack(
          children: [
            Positioned(
              top: 24,
              left: 24,
              child: _CircleBtn(
                icon: Icons.arrow_back_rounded,
                onTap: () => Navigator.pop(context),
              ),
            ),
            Center(
              child: GlassPanel(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.error_outline,
                      color: Colors.redAccent,
                      size: 64,
                    ),
                    const SizedBox(height: 24),
                    const Text(
                      'Playback Failed',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Unable to connect to stream',
                      style: TextStyle(color: Colors.white70, fontSize: 15),
                    ),
                    const SizedBox(height: 32),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        ElevatedButton.icon(
                          icon: const Icon(Icons.refresh_rounded),
                          onPressed: () => _playerController.setDataSource(
                            _currentPlaybackItem,
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFFC12CFF),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 24,
                              vertical: 12,
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          label: const Text('RETRY'),
                        ),
                        const SizedBox(width: 16),
                        OutlinedButton.icon(
                          icon: const Icon(Icons.list_rounded),
                          onPressed: _toggleChannelList,
                          style: OutlinedButton.styleFrom(
                            foregroundColor: Colors.white,
                            side: const BorderSide(color: Colors.white24),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 24,
                              vertical: 12,
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          label: const Text('CHANNELS'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChannelListOverlay() {
    return Positioned(
      left: 0,
      top: 0,
      bottom: 0,
      child: SafeArea(
        child: AppCard(
          width: 300,
          margin: const EdgeInsets.all(16),
          padding: EdgeInsets.zero,
          borderRadius: 24,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 20, 12, 12),
                child: Row(
                  children: [
                    const Text(
                      'CHANNELS',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.0,
                      ),
                    ),
                    const Spacer(),
                    IconButton(
                      icon: const Icon(
                        Icons.close,
                        color: Colors.white70,
                        size: 20,
                      ),
                      onPressed: _toggleChannelList,
                    ),
                  ],
                ),
              ),
              Expanded(
                child: widget.queue == null || widget.queue!.isEmpty
                    ? const Center(
                        child: Text(
                          'No Channels',
                          style: TextStyle(color: Colors.white38),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        itemCount: widget.queue!.length,
                        itemBuilder: (context, index) {
                          final item = widget.queue![index];
                          final isPlaying = item.id == _currentPlaybackItem.id;
                          return _ChannelListTile(
                            item: item,
                            isPlaying: isPlaying,
                            onTap: () {
                              _playItem(item);
                              _toggleChannelList();
                            },
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDuration(Duration d) {
    final hh = d.inHours.toString().padLeft(2, '0');
    final mm = (d.inMinutes % 60).toString().padLeft(2, '0');
    final ss = (d.inSeconds % 60).toString().padLeft(2, '0');
    return d.inHours > 0 ? '$hh:$mm:$ss' : '$mm:$ss';
  }

  void _showSubtitleMenu() async {
    final tracks = await _playerController.getSubtitleTracks();
    if (!mounted) return;
    _showTrackMenu(
      'Subtitles',
      tracks,
      (idx) => _playerController.setSubtitleTrack(idx),
    );
  }

  void _showAudioMenu() async {
    final tracks = await _playerController.getAudioTracks();
    if (!mounted) return;
    _showTrackMenu(
      'Audio Tracks',
      tracks,
      (idx) => _playerController.setAudioTrack(idx),
    );
  }

  void _showAspectRatioMenu() {
    final ratios = ['fit', 'fill', 'stretch', '16:9', '4:3'];
    _showTrackMenu(
      'Aspect Ratio',
      ratios.map((e) => e.toUpperCase()).toList(),
      (idx) {
        final selected = ratios[idx];
        UserPreferences.setPlayerAspectRatio(selected);
        _applyAspectRatio(selected);
        if (mounted) {
          setState(() {});
        }
      },
    );
  }

  void _showTrackMenu(
    String title,
    List<String> items,
    Function(int) onSelected,
  ) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1D29),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) => SafeArea(
        child: Container(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: items.length,
                  itemBuilder: (context, index) => ListTile(
                    title: Text(
                      items[index],
                      style: const TextStyle(color: Colors.white),
                    ),
                    onTap: () {
                      onSelected(index);
                      Navigator.pop(context);
                    },
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showEpgModal() {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF050812),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: Container(
          height: MediaQuery.of(context).size.height * 0.5,
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              const Text(
                'PROGRAMME GUIDE',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 16),
              Expanded(
                child: _epgPrograms.isEmpty
                    ? const Center(
                        child: Text(
                          'No EPG Data',
                          style: TextStyle(color: Colors.white38),
                        ),
                      )
                    : ListView.builder(
                        itemCount: _epgPrograms.length,
                        itemBuilder: (context, i) {
                          final p = _epgPrograms[i];

                          return ListTile(
                            title: Text(
                              p.title,
                              style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            subtitle: Text(
                              '${DateFormat('HH:mm').format(p.start)} - ${DateFormat('HH:mm').format(p.end)}',
                              style: const TextStyle(color: Colors.white54),
                            ),
                            trailing: i == 0
                                ? const Badge(
                                    label: Text('LIVE'),
                                    backgroundColor: Colors.redAccent,
                                  )
                                : (p.start.isBefore(DateTime.now())
                                      ? IconButton(
                                          icon: const Icon(
                                            Icons.history_rounded,
                                            color: Color(0xFF00B7FF),
                                          ),
                                          onPressed: () {
                                            Navigator.pop(context);
                                            _playCatchup(p);
                                          },
                                        )
                                      : null),
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _playCatchup(EpgProgramWindow program) {
    final durationMinutes = program.end.difference(program.start).inMinutes;
    final newItem = widget.contentItem.copyWith(
      description: program.title,
      catchupStartTime: program.start,
      catchupDurationMinutes: durationMinutes,
    );

    _playItem(newItem);
  }
}

class _ChannelListTile extends StatefulWidget {
  final ContentItem item;
  final bool isPlaying;
  final VoidCallback onTap;
  const _ChannelListTile({
    required this.item,
    required this.isPlaying,
    required this.onTap,
  });
  @override
  State<_ChannelListTile> createState() => _ChannelListTileState();
}

class _ChannelListTileState extends State<_ChannelListTile> {
  bool _isFocused = false;
  EpgProgramWindow? _currentProgram;
  final _epgService = EpgStorageService();

  @override
  void initState() {
    super.initState();
    _fetchCurrentProgram();
  }

  @override
  void didUpdateWidget(_ChannelListTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.item.id != oldWidget.item.id) {
      _currentProgram = null;
      _fetchCurrentProgram();
    }
  }

  Future<void> _fetchCurrentProgram() async {
    final liveStream = widget.item.liveStream;
    final playlistId = liveStream?.playlistId;
    final epgId = liveStream?.epgChannelId ?? '';

    if (playlistId == null || epgId.isEmpty) return;

    try {
      final programs = await _epgService.getProgramsForWindow(
        playlistId: playlistId,
        epgChannelId: epgId,
        start: DateTime.now(),
        end: DateTime.now().add(const Duration(minutes: 5)),
        limit: 1,
      );
      if (mounted) {
        setState(() => _currentProgram = programs.firstOrNull);
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final program = _currentProgram;
    final programText = program == null
        ? null
        : '${DateFormat('HH:mm').format(program.start)} ${program.title}';

    return FocusableActionDetector(
      onFocusChange: (v) => setState(() => _isFocused = v),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: InkWell(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          margin: const EdgeInsets.only(bottom: 4),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: _isFocused
                ? const Color(0xFFC12CFF).withValues(alpha: 0.2)
                : (widget.isPlaying
                      ? Colors.white.withValues(alpha: 0.05)
                      : Colors.transparent),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: _isFocused
                  ? const Color(0xFFC12CFF).withValues(alpha: 0.5)
                  : (widget.isPlaying ? Colors.white10 : Colors.transparent),
            ),
          ),
          child: Row(
            children: [
              if (widget.item.imagePath.isNotEmpty)
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: Image.network(
                    widget.item.imagePath,
                    width: 32,
                    height: 32,
                    fit: BoxFit.contain,
                    errorBuilder: (context, error, stackTrace) => const Icon(
                      Icons.live_tv,
                      size: 16,
                      color: Colors.white24,
                    ),
                  ),
                )
              else
                const Icon(Icons.live_tv, size: 16, color: Colors.white24),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      widget.item.name,
                      style: TextStyle(
                        color: widget.isPlaying
                            ? const Color(0xFF00B7FF)
                            : Colors.white,
                        fontWeight: widget.isPlaying
                            ? FontWeight.bold
                            : FontWeight.normal,
                        fontSize: 13,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (programText != null) ...[
                      const SizedBox(height: 3),
                      Text(
                        programText,
                        style: const TextStyle(
                          color: Colors.white54,
                          fontSize: 11,
                          height: 1.1,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ],
                ),
              ),
              if (widget.isPlaying)
                const Icon(
                  Icons.play_arrow_rounded,
                  color: Color(0xFF00B7FF),
                  size: 14,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CircleBtn extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  final Color? iconColor;
  final double size;
  final FocusNode? focusNode;

  const _CircleBtn({
    required this.icon,
    required this.onTap,
    this.iconColor,
    this.size = 36,
    this.focusNode,
  });

  @override
  State<_CircleBtn> createState() => _CircleBtnState();
}

class _CircleBtnState extends State<_CircleBtn> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) => FocusableActionDetector(
    focusNode: widget.focusNode,
    onFocusChange: (v) => setState(() => _isFocused = v),
    shortcuts: const {
      SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
      SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
      SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
    },
    actions: {
      ActivateIntent: CallbackAction<ActivateIntent>(
        onInvoke: (_) {
          widget.onTap();
          return null;
        },
      ),
    },
    child: AnimatedScale(
      scale: _isFocused ? 1.1 : 1.0,
      duration: const Duration(milliseconds: 200),
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(widget.size),
        child: Container(
          width: widget.size,
          height: widget.size,
          decoration: BoxDecoration(
            color: _isFocused
                ? const Color(0xFFC12CFF)
                : Colors.white.withValues(alpha: 0.08),
            shape: BoxShape.circle,
            border: Border.all(
              color: _isFocused ? Colors.white : Colors.white10,
            ),
          ),
          child: Icon(
            widget.icon,
            color: widget.iconColor ?? Colors.white,
            size: widget.size * 0.6,
          ),
        ),
      ),
    ),
  );
}

class _LargeControlBtn extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  final double size;
  final bool isPrimary;
  final FocusNode? focusNode;
  const _LargeControlBtn({
    required this.icon,
    required this.onTap,
    this.size = 64,
    this.isPrimary = false,
    this.focusNode,
  });
  @override
  State<_LargeControlBtn> createState() => _LargeControlBtnState();
}

class _LargeControlBtnState extends State<_LargeControlBtn> {
  bool _isFocused = false;
  @override
  Widget build(BuildContext context) => FocusableActionDetector(
    focusNode: widget.focusNode,
    onFocusChange: (v) => setState(() => _isFocused = v),
    shortcuts: const {
      SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
      SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
      SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
    },
    actions: {
      ActivateIntent: CallbackAction<ActivateIntent>(
        onInvoke: (_) {
          widget.onTap();
          return null;
        },
      ),
    },
    child: InkWell(
      onTap: widget.onTap,
      borderRadius: BorderRadius.circular(widget.size),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: widget.size,
        height: widget.size,
        decoration: BoxDecoration(
          color: _isFocused
              ? const Color(0xFFC12CFF)
              : (widget.isPrimary
                    ? const Color(0xFFC12CFF).withValues(alpha: 0.2)
                    : Colors.white10),
          shape: BoxShape.circle,
          border: Border.all(
            color: _isFocused ? Colors.white : Colors.white10,
            width: 2,
          ),
        ),
        child: Icon(widget.icon, color: Colors.white, size: widget.size * 0.6),
      ),
    ),
  );
}

class _ActionBtn extends StatefulWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color? iconColor;
  final FocusNode? focusNode;

  const _ActionBtn({
    required this.icon,
    required this.label,
    required this.onTap,
    this.iconColor,
    this.focusNode,
  });

  @override
  State<_ActionBtn> createState() => _ActionBtnState();
}

class _ActionBtnState extends State<_ActionBtn> {
  bool _isFocused = false;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 12),
    child: FocusableActionDetector(
      focusNode: widget.focusNode,
      onFocusChange: (v) => setState(() => _isFocused = v),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(10),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            color: _isFocused
                ? Colors.white.withValues(alpha: 0.1)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                widget.icon,
                color: _isFocused
                    ? const Color(0xFFC12CFF)
                    : (widget.iconColor ?? Colors.white70),
                size: 24,
              ),
              const SizedBox(height: 6),
              Text(
                widget.label,
                style: TextStyle(
                  color: _isFocused ? Colors.white : Colors.white54,
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _ClockWidget extends StatefulWidget {
  const _ClockWidget();
  @override
  State<_ClockWidget> createState() => _ClockWidgetState();
}

class _ClockWidgetState extends State<_ClockWidget> {
  late Timer _timer;
  DateTime _now = DateTime.now();
  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(
      const Duration(seconds: 1),
      (t) => setState(() => _now = DateTime.now()),
    );
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Text(
    DateFormat('HH:mm').format(_now),
    style: const TextStyle(
      color: Colors.white,
      fontSize: 16,
      fontWeight: FontWeight.w900,
    ),
  );
}

class _ShowControlsIntent extends Intent {
  const _ShowControlsIntent();
}

class _HideControlsIntent extends Intent {
  const _HideControlsIntent();
}

class _PlayPauseIntent extends Intent {
  const _PlayPauseIntent();
}

class _SeekBackIntent extends Intent {
  const _SeekBackIntent();
}

class _SeekForwardIntent extends Intent {
  const _SeekForwardIntent();
}

class _ChannelUpIntent extends Intent {
  const _ChannelUpIntent();
}

class _ChannelDownIntent extends Intent {
  const _ChannelDownIntent();
}
