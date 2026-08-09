import 'package:watchio/repositories/user_preferences.dart';
import 'package:watchio/services/event_bus.dart';
import 'package:watchio/services/player_state.dart';
import 'package:watchio/widgets/player-buttons/back_button_widget.dart';
import 'package:watchio/widgets/player-buttons/video_channel_selector_widget.dart';
import 'package:watchio/widgets/player-buttons/video_favorite_widget.dart';
import 'package:watchio/widgets/player-buttons/video_info_widget.dart';
import 'package:watchio/widgets/player-buttons/video_settings_widget.dart';
import 'package:watchio/widgets/player-buttons/video_title_widget.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit_video/media_kit_video.dart';

class VideoWidget extends StatefulWidget {
  final VideoController controller;
  final SubtitleViewConfiguration subtitleViewConfiguration;

  const VideoWidget({
    super.key,
    required this.controller,
    required this.subtitleViewConfiguration,
  });

  @override
  State<VideoWidget> createState() => _VideoWidgetState();
}

class _VideoWidgetState extends State<VideoWidget> {
  bool _brightnessGesture = false;
  bool _volumeGesture = false;
  bool _seekGesture = false;
  bool _speedUpOnLongPress = true;
  bool _seekOnDoubleTap = true;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final brightnessGesture = await UserPreferences.getBrightnessGesture();
    final volumeGesture = await UserPreferences.getVolumeGesture();
    final seekGesture = await UserPreferences.getSeekGesture();
    final speedUpOnLongPress = await UserPreferences.getSpeedUpOnLongPress();
    final seekOnDoubleTap = await UserPreferences.getSeekOnDoubleTap();
    if (mounted) {
      setState(() {
        _brightnessGesture = brightnessGesture;
        _volumeGesture = volumeGesture;
        _seekGesture = seekGesture;
        _speedUpOnLongPress = speedUpOnLongPress;
        _seekOnDoubleTap = seekOnDoubleTap;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    final topButtons = [
      const BackButtonWidget(),
      const Expanded(child: VideoTitleWidget()),
      const VideoInfoWidget(),
      VideoChannelSelectorWidget(
        queue: PlayerState.queue,
        currentIndex: PlayerState.currentIndex,
      ),
      const VideoFavoriteWidget(),
      const VideoSettingsWidget(),
    ];

    switch (Theme.of(context).platform) {
      case TargetPlatform.android:
      case TargetPlatform.iOS:
        return _withTvRemoteShortcuts(
          MaterialVideoControlsTheme(
            normal: MaterialVideoControlsThemeData(
              brightnessGesture: _brightnessGesture,
              volumeGesture: _volumeGesture,
              seekGesture: _seekGesture,
              speedUpOnLongPress: _speedUpOnLongPress,
              seekOnDoubleTap: _seekOnDoubleTap,
              topButtonBar: topButtons,
              bottomButtonBar: const [],
            ),
            fullscreen: MaterialVideoControlsThemeData(
              brightnessGesture: _brightnessGesture,
              volumeGesture: _volumeGesture,
              seekGesture: _seekGesture,
              speedUpOnLongPress: _speedUpOnLongPress,
              seekOnDoubleTap: _seekOnDoubleTap,
              topButtonBar: topButtons,
              bottomButtonBar: const [],
              seekBarMargin: const EdgeInsets.fromLTRB(0, 0, 0, 10),
            ),
            child: Material(
              color: Colors.black,
              child: Video(controller: widget.controller),
            ),
          ),
        );
      case TargetPlatform.macOS:
      case TargetPlatform.windows:
      case TargetPlatform.linux:
        return _withTvRemoteShortcuts(
          MaterialDesktopVideoControlsTheme(
            normal: MaterialDesktopVideoControlsThemeData(
              modifyVolumeOnScroll: false,
              toggleFullscreenOnDoublePress: true,
              topButtonBar: topButtons,
            ),
            fullscreen: MaterialDesktopVideoControlsThemeData(
              modifyVolumeOnScroll: false,
              toggleFullscreenOnDoublePress: true,
              topButtonBar: topButtons,
            ),
            child: Material(
              color: Colors.black,
              child: Video(controller: widget.controller),
            ),
          ),
        );
      default:
        return _withTvRemoteShortcuts(
          Video(
            controller: widget.controller,
            controls: NoVideoControls,
            resumeUponEnteringForegroundMode: true,
            pauseUponEnteringBackgroundMode: !PlayerState.backgroundPlay,
            subtitleViewConfiguration: widget.subtitleViewConfiguration,
          ),
        );
    }
  }

  Widget _withTvRemoteShortcuts(Widget child) {
    return Focus(
      autofocus: true,
      child: Shortcuts(
        shortcuts: const {
          SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.keyK): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.mediaPlayPause): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.keyJ): _SeekBackIntent(),
          SingleActivator(LogicalKeyboardKey.keyL): _SeekForwardIntent(),
          SingleActivator(LogicalKeyboardKey.mediaRewind): _SeekBackIntent(),
          SingleActivator(LogicalKeyboardKey.mediaFastForward):
              _SeekForwardIntent(),
          SingleActivator(LogicalKeyboardKey.arrowLeft): _SeekBackIntent(),
          SingleActivator(LogicalKeyboardKey.arrowRight): _SeekForwardIntent(),
          SingleActivator(LogicalKeyboardKey.goBack): DismissIntent(),
          SingleActivator(LogicalKeyboardKey.escape): DismissIntent(),
          SingleActivator(LogicalKeyboardKey.channelUp): _ChannelUpIntent(),
          SingleActivator(LogicalKeyboardKey.channelDown): _ChannelDownIntent(),
        },
        child: Actions(
          actions: {
            ActivateIntent: CallbackAction<ActivateIntent>(
              onInvoke: (_) {
                widget.controller.player.playOrPause();
                return null;
              },
            ),
            _SeekBackIntent: CallbackAction<_SeekBackIntent>(
              onInvoke: (_) {
                final player = widget.controller.player;
                player.seek(
                  player.state.position - const Duration(seconds: 10),
                );
                return null;
              },
            ),
            _SeekForwardIntent: CallbackAction<_SeekForwardIntent>(
              onInvoke: (_) {
                final player = widget.controller.player;
                player.seek(
                  player.state.position + const Duration(seconds: 10),
                );
                return null;
              },
            ),
            _ChannelUpIntent: CallbackAction<_ChannelUpIntent>(
              onInvoke: (_) {
                _switchQueueItem(1);
                return null;
              },
            ),
            _ChannelDownIntent: CallbackAction<_ChannelDownIntent>(
              onInvoke: (_) {
                _switchQueueItem(-1);
                return null;
              },
            ),
            DismissIntent: CallbackAction<DismissIntent>(
              onInvoke: (_) {
                Navigator.maybePop(context);
                return null;
              },
            ),
          },
          child: child,
        ),
      ),
    );
  }

  void _switchQueueItem(int delta) {
    final queue = PlayerState.queue;
    if (queue == null || queue.isEmpty) return;
    final next = (PlayerState.currentIndex + delta).clamp(0, queue.length - 1);
    if (next == PlayerState.currentIndex) return;
    EventBus().emit('player_content_item_index_changed', next);
  }
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

// Backward compatibility wrapper
Widget getVideo(
  BuildContext context,
  VideoController controller,
  SubtitleViewConfiguration subtitleViewConfiguration,
) {
  return VideoWidget(
    controller: controller,
    subtitleViewConfiguration: subtitleViewConfiguration,
  );
}
