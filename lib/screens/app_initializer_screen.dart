import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/repositories/user_preferences.dart';
import 'package:watchio/services/app_state.dart';
import 'package:watchio/services/config_service.dart';
import 'package:watchio/services/input_mode_controller.dart';
import 'package:watchio/services/playlist_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'onboarding/device_mode_selection_screen.dart';
import 'xtream-codes/new_xtream_code_playlist_screen.dart';
import 'xtream-codes/xtream_code_home_screen.dart';

class AppInitializerScreen extends StatefulWidget {
  const AppInitializerScreen({super.key});

  @override
  State<AppInitializerScreen> createState() => _AppInitializerScreenState();
}

class _AppInitializerScreenState extends State<AppInitializerScreen>
    with SingleTickerProviderStateMixin {
  bool _isLoading = true;
  Playlist? _lastPlaylist;
  late AnimationController _pulseController;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat(reverse: true);
    _loadLastPlaylist();
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  Future<void> _loadLastPlaylist() async {
    final lastPlaylistId = await UserPreferences.getLastPlaylist();

    if (lastPlaylistId != null) {
      final playlist = await PlaylistService.getPlaylistById(lastPlaylistId);
      if (playlist != null &&
          playlist.type == PlaylistType.xtream &&
          _hasXtreamCredentials(playlist)) {
        AppState.currentPlaylist = playlist;
        _lastPlaylist = playlist;
      }
    }

    // Add a minimum delay to show the splash effect
    await Future.delayed(const Duration(milliseconds: 1500));

    if (mounted) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  bool _hasXtreamCredentials(Playlist playlist) {
    return (playlist.url?.trim().isNotEmpty ?? false) &&
        (playlist.username?.trim().isNotEmpty ?? false) &&
        (playlist.password?.trim().isNotEmpty ?? false);
  }

  @override
  Widget build(BuildContext context) {
    final configService = context.watch<ConfigService>();
    final inputMode = context.watch<InputModeController>();

    if (_isLoading || configService.isLoading || !inputMode.isLoaded) {
      return Scaffold(
        backgroundColor: const Color(0xFF050816),
        body: Container(
          width: double.infinity,
          height: double.infinity,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [const Color(0xFF0F1423), const Color(0xFF050816)],
            ),
          ),
          child: SafeArea(
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Spacer(flex: 3),
                  // Logo with Ambient Glow
                  ScaleTransition(
                    scale: Tween<double>(begin: 0.95, end: 1.05).animate(
                      CurvedAnimation(
                        parent: _pulseController,
                        curve: Curves.easeInOut,
                      ),
                    ),
                    child: Container(
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: const Color(
                              0xFFC12CFF,
                            ).withValues(alpha: 0.15),
                            blurRadius: 40,
                            spreadRadius: 10,
                          ),
                          BoxShadow(
                            color: const Color(
                              0xFF00B7FF,
                            ).withValues(alpha: 0.1),
                            blurRadius: 30,
                            spreadRadius: 5,
                          ),
                        ],
                      ),
                      child: Image.asset(
                        'assets/images/App_Logo.png',
                        width: 180,
                        fit: BoxFit.contain,
                        errorBuilder: (context, error, stackTrace) =>
                            const Icon(
                              Icons.play_arrow_rounded,
                              color: Color(0xFF00B7FF),
                              size: 100,
                            ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  const Text(
                    'WATCHIO IPTV',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 32,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 2.0,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Loading your entertainment',
                    style: TextStyle(
                      color: Colors.white38,
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const Spacer(flex: 2),
                  // Modern Progress Bar
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 60),
                    child: Column(
                      children: [
                        Container(
                          width: 300,
                          height: 8,
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.05),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Stack(
                            children: [
                              AnimatedBuilder(
                                animation: _pulseController,
                                builder: (context, child) {
                                  return FractionallySizedBox(
                                    alignment: Alignment.centerLeft,
                                    widthFactor:
                                        0.3 +
                                        (0.4 *
                                            _pulseController
                                                .value), // Animated dummy progress
                                    child: Container(
                                      decoration: BoxDecoration(
                                        gradient: const LinearGradient(
                                          colors: [
                                            Color(0xFFC12CFF),
                                            Color(0xFF00B7FF),
                                          ],
                                        ),
                                        borderRadius: BorderRadius.circular(4),
                                        boxShadow: [
                                          BoxShadow(
                                            color: const Color(
                                              0xFF00B7FF,
                                            ).withValues(alpha: 0.4),
                                            blurRadius: 10,
                                            spreadRadius: 1,
                                          ),
                                        ],
                                      ),
                                    ),
                                  );
                                },
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),
                        const Text(
                          'INITIALISING',
                          style: TextStyle(
                            color: Color(0xFF00B7FF),
                            fontSize: 12,
                            fontWeight: FontWeight.w900,
                            letterSpacing: 1.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const Spacer(flex: 1),
                ],
              ),
            ),
          ),
        ),
      );
    }

    if (!inputMode.hasMode) {
      return const DeviceModeSelectionScreen();
    }

    if (_lastPlaylist == null) {
      return const NewXtreamCodePlaylistScreen();
    }

    return XtreamCodeHomeScreen(playlist: _lastPlaylist!);
  }
}
