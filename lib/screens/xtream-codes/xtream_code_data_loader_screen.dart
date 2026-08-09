import 'package:watchio/repositories/user_preferences.dart';
import 'package:watchio/services/app_state.dart';
import 'package:flutter/material.dart';
import 'package:watchio/controllers/iptv_controller.dart';
import 'package:watchio/models/api_configuration_model.dart';
import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/models/progress_step.dart';
import 'package:watchio/models/provider_model.dart';
import 'package:watchio/repositories/iptv_repository.dart';
import 'package:watchio/repositories/provider_repository.dart';
import 'package:watchio/services/playlist_service.dart';
import 'package:watchio/l10n/localization_extension.dart';
import 'package:provider/provider.dart';
import 'xtream_code_home_screen.dart';
import '../playlist_screen.dart';

class XtreamCodeDataLoaderScreen extends StatefulWidget {
  final Playlist playlist;
  final bool refreshAll;

  const XtreamCodeDataLoaderScreen({
    super.key,
    required this.playlist,
    this.refreshAll = false,
  });

  @override
  XtreamCodeDataLoaderScreenState createState() =>
      XtreamCodeDataLoaderScreenState();
}

class XtreamCodeDataLoaderScreenState extends State<XtreamCodeDataLoaderScreen>
    with TickerProviderStateMixin {
  late AnimationController _animationController;
  late AnimationController _pulseAnimationController;
  late Animation<double> _progressAnimation;
  late Animation<double> _pulseAnimation;
  IptvController? _controller;
  bool _isInitialized = false;
  bool _isRepairing = false;
  String? _initError;

  Map<ProgressStep, String> get stepDisplayNames => {
        ProgressStep.userInfo: 'INITIALISING',
        ProgressStep.categories: 'LOADING CATEGORIES',
        ProgressStep.liveChannels: 'LOADING CHANNELS',
        ProgressStep.movies: 'LOADING MOVIES',
        ProgressStep.series: 'LOADING SERIES',
      };

  @override
  void initState() {
    super.initState();

    _animationController = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    );

    _pulseAnimationController = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    )..repeat(reverse: true);

    _progressAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeOutCubic),
    );

    _pulseAnimation = Tween<double>(begin: 0.97, end: 1.03).animate(
      CurvedAnimation(
        parent: _pulseAnimationController,
        curve: Curves.easeInOut,
      ),
    );

    _initializeController();
  }

  void _initializeController() async {
    // Hydrate credentials from secure storage if needed
    final fullPlaylist =
        await PlaylistService.getPlaylistById(widget.playlist.id) ??
            widget.playlist;
    AppState.currentPlaylist = fullPlaylist;

    final url = fullPlaylist.url;
    final username = fullPlaylist.username;
    final password = fullPlaylist.password;

    if (url == null ||
        url.isEmpty ||
        username == null ||
        username.isEmpty ||
        password == null ||
        password.isEmpty) {
      if (mounted) {
        setState(() {
          _initError =
              'Provider credentials are missing or invalid. Please repair the login to continue.';
          _isInitialized = true;
        });
      }
      return;
    }

    final repository = IptvRepository(
      ApiConfig(
        baseUrl: url,
        username: username,
        password: password,
      ),
      fullPlaylist.id,
    );

    _controller?.dispose();
    _controller = IptvController(repository, widget.refreshAll);

    if (mounted) {
      setState(() {
        _isInitialized = true;
      });
    }

    _startLoading();
  }

  @override
  void dispose() {
    _animationController.dispose();
    _pulseAnimationController.dispose();
    _controller?.dispose();
    super.dispose();
  }

  Future<void> _startLoading() async {
    if (_controller == null) return;
    final success = await _controller!.loadAllData();

    if (success) {
      if (mounted) {
        _animationController.animateTo(1.0);
        await Future.delayed(const Duration(milliseconds: 1000));

        AppState.currentPlaylist = widget.playlist;
        await UserPreferences.setLastPlaylist(widget.playlist.id);

        if (mounted) {
          Navigator.pushAndRemoveUntil(
            context,
            MaterialPageRoute(
              builder: (context) => ChangeNotifierProvider.value(
                value: _controller!,
                child: XtreamCodeHomeScreen(playlist: widget.playlist),
              ),
            ),
            (route) => false,
          );
        }
      }
    }
  }

  double _getProgressValue(ProgressStep step) {
    switch (step) {
      case ProgressStep.userInfo:
        return 0.2;
      case ProgressStep.categories:
        return 0.4;
      case ProgressStep.liveChannels:
        return 0.6;
      case ProgressStep.movies:
        return 0.8;
      case ProgressStep.series:
        return 1.0;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_isInitialized) {
      return const Scaffold(
        backgroundColor: Color(0xFF050816),
        body: Center(child: CircularProgressIndicator(color: Color(0xFF00B7FF))),
      );
    }

    if (_initError != null || _controller == null) {
      return Scaffold(
        backgroundColor: const Color(0xFF050816),
        body: Center(
          child: _buildManualErrorCard(
              context, _initError ?? 'Initialization failed.'),
        ),
      );
    }

    return Scaffold(
      backgroundColor: const Color(0xFF050816),
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF0F1423), Color(0xFF050816)],
          ),
        ),
        child: ChangeNotifierProvider.value(
          value: _controller!,
          child: Consumer<IptvController>(
            builder: (context, controller, child) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (mounted) {
                  _animationController
                      .animateTo(_getProgressValue(controller.currentStep));
                }
              });

              return SafeArea(
                child: Center(
                  child: SingleChildScrollView(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 24),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          // Logo with Ambient Glow
                          ScaleTransition(
                            scale: _pulseAnimation,
                            child: Container(
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                boxShadow: [
                                  BoxShadow(
                                    color: const Color(0xFFC12CFF)
                                        .withValues(alpha: 0.15),
                                    blurRadius: 40,
                                    spreadRadius: 10,
                                  ),
                                  BoxShadow(
                                    color: const Color(0xFF00B7FF)
                                        .withValues(alpha: 0.1),
                                    blurRadius: 30,
                                    spreadRadius: 5,
                                  ),
                                ],
                              ),
                              child: Image.asset(
                                'assets/images/App_Logo.png',
                                width: 160,
                                fit: BoxFit.contain,
                                errorBuilder: (context, error, stackTrace) =>
                                    const Icon(Icons.play_arrow_rounded,
                                        color: Color(0xFF00B7FF), size: 100),
                              ),
                            ),
                          ),
                          const SizedBox(height: 32),
                          const Text(
                            'WATCHIO IPTV',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 28,
                              fontWeight: FontWeight.w900,
                              letterSpacing: 2.0,
                            ),
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'Loading your entertainment',
                            style: TextStyle(
                              color: Colors.white38,
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                              letterSpacing: 1.2,
                            ),
                          ),

                          const SizedBox(height: 40),

                          // Modern Progress Bar
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 40),
                            child: Column(
                              children: [
                                AnimatedBuilder(
                                  animation: _progressAnimation,
                                  builder: (context, child) {
                                    return Column(
                                      children: [
                                        Container(
                                          constraints: const BoxConstraints(
                                              maxWidth: 400),
                                          height: 8,
                                          decoration: BoxDecoration(
                                            color: Colors.white
                                                .withValues(alpha: 0.05),
                                            borderRadius:
                                                BorderRadius.circular(4),
                                          ),
                                          child: FractionallySizedBox(
                                            alignment: Alignment.centerLeft,
                                            widthFactor: _progressAnimation
                                                .value
                                                .clamp(0.0, 1.0),
                                            child: Container(
                                              decoration: BoxDecoration(
                                                gradient: const LinearGradient(
                                                  colors: [
                                                    Color(0xFFC12CFF),
                                                    Color(0xFF00B7FF)
                                                  ],
                                                ),
                                                borderRadius:
                                                    BorderRadius.circular(4),
                                                boxShadow: [
                                                  BoxShadow(
                                                    color:
                                                        const Color(0xFF00B7FF)
                                                            .withValues(
                                                                alpha: 0.4),
                                                    blurRadius: 10,
                                                    spreadRadius: 1,
                                                  ),
                                                ],
                                              ),
                                            ),
                                          ),
                                        ),
                                        const SizedBox(height: 20),
                                        Text(
                                          stepDisplayNames[
                                                  controller.currentStep] ??
                                              'FINALISING',
                                          style: const TextStyle(
                                            color: Color(0xFF00B7FF),
                                            fontSize: 13,
                                            fontWeight: FontWeight.w900,
                                            letterSpacing: 1.5,
                                          ),
                                        ),
                                        if (controller.importProgress !=
                                            null) ...[
                                          const SizedBox(height: 8),
                                          Text(
                                            '${controller.importProgress!.processedItems} items',
                                            style: const TextStyle(
                                              fontSize: 11,
                                              color: Colors.white38,
                                              fontWeight: FontWeight.bold,
                                            ),
                                          ),
                                        ],
                                      ],
                                    );
                                  },
                                ),
                                const SizedBox(height: 24),
                                // Cancel Button
                                TextButton.icon(
                                  onPressed: () {
                                    controller.cancelImport();
                                    Navigator.pushAndRemoveUntil(
                                      context,
                                      MaterialPageRoute(
                                          builder: (context) =>
                                              const PlaylistScreen()),
                                      (route) => false,
                                    );
                                  },
                                  icon: const Icon(Icons.close_rounded,
                                      color: Colors.white54, size: 18),
                                  label: const Text(
                                    'CANCEL',
                                    style: TextStyle(
                                        color: Colors.white54,
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold),
                                  ),
                                ),
                              ],
                            ),
                          ),

                          // Error handling
                          if (controller.errorMessage != null) ...[
                            const SizedBox(height: 32),
                            Padding(
                              padding:
                                  const EdgeInsets.symmetric(horizontal: 40),
                              child: Container(
                                padding: const EdgeInsets.all(20),
                                decoration: BoxDecoration(
                                  color: Colors.red.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(16),
                                  border: Border.all(
                                      color:
                                          Colors.red.withValues(alpha: 0.3)),
                                ),
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    const Icon(Icons.error_outline_rounded,
                                        color: Colors.redAccent, size: 32),
                                    const SizedBox(height: 12),
                                    Text(
                                      context.loc.error_occurred,
                                      style: const TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.w900,
                                          color: Colors.redAccent),
                                    ),
                                    const SizedBox(height: 8),
                                    Text(
                                      controller.errorMessage!,
                                      style: const TextStyle(
                                          fontSize: 13, color: Colors.white70),
                                      textAlign: TextAlign.center,
                                    ),
                                    const SizedBox(height: 16),
                                    SizedBox(
                                      width: double.infinity,
                                      child: ElevatedButton(
                                        onPressed: () {
                                          Navigator.pushAndRemoveUntil(
                                            context,
                                            MaterialPageRoute(
                                                builder: (context) =>
                                                    const PlaylistScreen()),
                                            (route) => false,
                                          );
                                        },
                                        style: ElevatedButton.styleFrom(
                                          backgroundColor:
                                              const Color(0xFF00B7FF),
                                          foregroundColor: Colors.white,
                                          shape: RoundedRectangleBorder(
                                              borderRadius:
                                                  BorderRadius.circular(12)),
                                        ),
                                        child: Text(
                                            context.loc.close.toUpperCase()),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildManualErrorCard(BuildContext context, String message) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 40),
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: Colors.red.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.red.withValues(alpha: 0.3)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline_rounded,
                color: Colors.redAccent, size: 48),
            const SizedBox(height: 16),
            const Text(
              'CONFIGURATION ERROR',
              style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                  color: Colors.redAccent),
            ),
            const SizedBox(height: 12),
            Text(
              'Provider credentials are missing or invalid. Please repair the login to continue.',
              style: const TextStyle(fontSize: 14, color: Colors.white70),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            Column(
              children: [
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: _showRepairLogin,
                    icon: const Icon(Icons.build_rounded),
                    label: const Text('REPAIR LOGIN'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFC12CFF),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pushAndRemoveUntil(
                        context,
                        MaterialPageRoute(
                            builder: (context) => const PlaylistScreen()),
                        (route) => false,
                      );
                    },
                    icon: const Icon(Icons.list_rounded),
                    label: const Text('BACK TO PLAYLISTS'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.white70,
                      side: const BorderSide(color: Colors.white24),
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _showRepairLogin() {
    final urlController =
        TextEditingController(text: AppState.currentPlaylist?.url);
    final usernameController =
        TextEditingController(text: AppState.currentPlaylist?.username);
    final passwordController =
        TextEditingController(text: AppState.currentPlaylist?.password);
    bool obscurePassword = true;
    String? dialogError;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: const Color(0xFF1A1D29),
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
              side: const BorderSide(color: Colors.white10)),
          title: Row(
            children: [
              const Icon(Icons.settings_backup_restore_rounded,
                  color: Color(0xFF00B7FF)),
              const SizedBox(width: 12),
              const Text('REPAIR CREDENTIALS',
                  style: TextStyle(
                      color: Colors.white, fontWeight: FontWeight.w900)),
            ],
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text(
                    'Your login details were not found or expired. Please update them below.',
                    style: TextStyle(color: Colors.white60, fontSize: 13)),
                const SizedBox(height: 20),
                _RepairTextField(
                  controller: urlController,
                  label: 'Server URL',
                  hint: 'http://example.com:8080',
                  icon: Icons.link_rounded,
                ),
                const SizedBox(height: 12),
                _RepairTextField(
                  controller: usernameController,
                  label: 'Username',
                  icon: Icons.person_outline_rounded,
                ),
                const SizedBox(height: 12),
                _RepairTextField(
                  controller: passwordController,
                  label: 'Password',
                  icon: Icons.lock_outline_rounded,
                  isPassword: true,
                  obscure: obscurePassword,
                  onToggleObscure: () =>
                      setDialogState(() => obscurePassword = !obscurePassword),
                ),
                if (dialogError != null) ...[
                  const SizedBox(height: 12),
                  Text(dialogError!,
                      style: const TextStyle(
                          color: Colors.redAccent,
                          fontSize: 12,
                          fontWeight: FontWeight.bold)),
                ]
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child:
                  const Text('CANCEL', style: TextStyle(color: Colors.white54)),
            ),
            ElevatedButton(
              onPressed: _isRepairing
                  ? null
                  : () async {
                      final url = urlController.text.trim();
                      final user = usernameController.text.trim();
                      final pass = passwordController.text.trim();

                      if (url.isEmpty || user.isEmpty || pass.isEmpty) {
                        setDialogState(
                            () => dialogError = 'All fields are required');
                        return;
                      }

                      setDialogState(() {
                        _isRepairing = true;
                        dialogError = null;
                      });

                      try {
                        // Validate before saving
                        final repository = IptvRepository(
                            ApiConfig(
                                baseUrl: url, username: user, password: pass),
                            AppState.currentPlaylist?.id ?? 'temp');

                        final info =
                            await repository.getPlayerInfo(forceRefresh: true);

                        if (info == null || info.userInfo.auth == 0) {
                          setDialogState(() {
                            _isRepairing = false;
                            dialogError =
                                'Login failed. Check URL, Username and Password.';
                          });
                          return;
                        }

                        // Valid! Save it.
                        final current = AppState.currentPlaylist!;
                        final updated = current.copyWith(
                          url: url,
                          username: user,
                          password: pass,
                        );

                        await PlaylistService.updatePlaylist(updated);

                        // Also update IptvProvider record if it exists
                        final providerRepo =
                            SharedPreferencesProviderRepository();
                        final provider =
                            await providerRepo.getProvider(current.id);
                        if (provider != null) {
                          await providerRepo.updateProvider(provider.copyWith(
                            serverUrl: url,
                            username: user,
                            password: pass,
                            status: ProviderStatus.online,
                            lastConnected: DateTime.now(),
                            clearLastFailureReason: true,
                          ));
                        }

                        if (!context.mounted) return;
                        Navigator.pop(context);
                        setState(() {
                          _initError = null;
                          _isRepairing = false;
                          _isInitialized =
                              false; // Reset to trigger loading again
                        });
                        _initializeController();
                      } catch (e) {
                        setDialogState(() {
                          _isRepairing = false;
                          dialogError = 'Connection error: $e';
                        });
                      }
                    },
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF00B7FF),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: _isRepairing
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Text('SAVE & RETRY'),
            ),
          ],
        ),
      ),
    );
  }
}

class _RepairTextField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final String? hint;
  final IconData icon;
  final bool isPassword;
  final bool obscure;
  final VoidCallback? onToggleObscure;

  const _RepairTextField({
    required this.controller,
    required this.label,
    required this.icon,
    this.hint,
    this.isPassword = false,
    this.obscure = false,
    this.onToggleObscure,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      obscureText: obscure,
      style: const TextStyle(color: Colors.white, fontSize: 14),
      decoration: InputDecoration(
        labelText: label,
        labelStyle: const TextStyle(color: Colors.white38),
        hintText: hint,
        hintStyle: const TextStyle(color: Colors.white12),
        prefixIcon: Icon(icon, color: const Color(0xFFC12CFF), size: 20),
        suffixIcon: isPassword
            ? IconButton(
                icon: Icon(obscure ? Icons.visibility_off : Icons.visibility,
                    color: Colors.white24, size: 18),
                onPressed: onToggleObscure,
              )
            : null,
        filled: true,
        fillColor: Colors.white.withValues(alpha: 0.05),
        border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none),
        contentPadding: const EdgeInsets.symmetric(vertical: 16),
      ),
    );
  }
}
