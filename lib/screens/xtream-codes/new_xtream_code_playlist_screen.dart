import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../../../services/config_service.dart';
import '../../../../controllers/playlist_controller.dart';
import '../../../../models/api_configuration_model.dart';
import '../../../../models/playlist_model.dart';
import '../../../../repositories/iptv_repository.dart';
import '../../../../shared/widgets/watchio_focus_action.dart';
import '../../../../utils/firestick_performance.dart';
import '../../../../services/playlist_service.dart';

class NewXtreamCodePlaylistScreen extends StatefulWidget {
  const NewXtreamCodePlaylistScreen({super.key});

  @override
  NewXtreamCodePlaylistScreenState createState() =>
      NewXtreamCodePlaylistScreenState();
}

class NewXtreamCodePlaylistScreenState
    extends State<NewXtreamCodePlaylistScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController(text: 'Playlist-1');
  final _urlController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();

  late FocusNode _playlistFocus;
  late FocusNode _usernameFocus;
  late FocusNode _passwordFocus;
  late FocusNode _urlFocus;
  late FocusNode _submitFocus;

  bool _obscurePassword = true;
  bool _isFormValid = false;

  @override
  void initState() {
    super.initState();
    _playlistFocus = FocusNode();
    _usernameFocus = FocusNode();
    _passwordFocus = FocusNode();
    _urlFocus = FocusNode();
    _submitFocus = FocusNode();

    _nameController.addListener(_validateForm);
    _urlController.addListener(_validateForm);
    _usernameController.addListener(_validateForm);
    _passwordController.addListener(_validateForm);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        FocusScope.of(context).requestFocus(_usernameFocus);
      }
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    _urlController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    _playlistFocus.dispose();
    _usernameFocus.dispose();
    _passwordFocus.dispose();
    _urlFocus.dispose();
    _submitFocus.dispose();
    super.dispose();
  }

  void _validateForm() {
    setState(() {
      _isFormValid =
          _nameController.text.trim().isNotEmpty &&
          _urlController.text.trim().isNotEmpty &&
          _usernameController.text.trim().isNotEmpty &&
          _passwordController.text.trim().isNotEmpty;
    });
  }

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    final loginBg = config.backgrounds.login;
    final keyboardOpen = MediaQuery.viewInsetsOf(context).bottom > 0;

    return Scaffold(
      backgroundColor: const Color(0xFF050816),
      resizeToAvoidBottomInset: true,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final double width = constraints.maxWidth;
          final double height = constraints.maxHeight;
          final bool isMobile = width < 700;
          final bool isTV = width >= 1600;

          // Logo Scaling (Width-based) + 10% Increase
          double logoWidth;
          if (isMobile) {
            logoWidth = 120;
          } else if (isTV) {
            logoWidth = 210;
          } else {
            logoWidth = 165;
          }

          double fieldHeight;
          double buttonHeight;
          if (isMobile) {
            fieldHeight = 52;
            buttonHeight = 50;
          } else if (isTV) {
            fieldHeight = 65;
            buttonHeight = 60;
          } else {
            fieldHeight = 60;
            buttonHeight = 55;
          }

          double titleFontSize = isMobile ? 20 : 24;
          double spacing = isMobile ? 8 : 12;

          if (height < 450 || keyboardOpen) {
            logoWidth *= 0.8;
            fieldHeight *= 0.8;
            buttonHeight *= 0.8;
            spacing = 6;
            titleFontSize = 18;
          }

          return TweenAnimationBuilder<double>(
            tween: Tween<double>(begin: 0.0, end: 1.0),
            duration: const Duration(milliseconds: 400),
            curve: Curves.easeOut,
            builder: (context, value, child) {
              return Opacity(
                opacity: value,
                child: Transform.scale(
                  scale: 0.97 + (0.03 * value),
                  child: child,
                ),
              );
            },
            child: Container(
              width: width,
              height: height,
              decoration: BoxDecoration(
                image: DecorationImage(
                  image: loginBg.isNotEmpty
                      ? perfNetworkImage(loginBg)
                      : const AssetImage('assets/images/background.png')
                            as ImageProvider,
                  fit: BoxFit.cover,
                  colorFilter: ColorFilter.mode(
                    Colors.black.withValues(alpha: 0.5),
                    BlendMode.darken,
                  ),
                ),
              ),
              child: SafeArea(
                child: Row(
                  children: [
                    if (!isMobile && !keyboardOpen)
                      Expanded(
                        flex: 4,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 20),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.center,
                            children: [
                              const SizedBox(height: 20), // Shift logo downward
                              // Watchio Logo with ambient TV glow
                              Container(
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  boxShadow: [
                                    BoxShadow(
                                      color: const Color(
                                        0xFFC12CFF,
                                      ).withValues(alpha: 0.1),
                                      blurRadius: 35,
                                      spreadRadius: 12,
                                    ),
                                    BoxShadow(
                                      color: const Color(
                                        0xFF00B7FF,
                                      ).withValues(alpha: 0.08),
                                      blurRadius: 30,
                                      spreadRadius: 6,
                                    ),
                                  ],
                                ),
                                child: Image.asset(
                                  'assets/images/logo.png',
                                  width: logoWidth,
                                  fit: BoxFit.contain,
                                  errorBuilder: (context, error, stackTrace) =>
                                      Icon(
                                        Icons.play_arrow_rounded,
                                        color: const Color(0xFF00B7FF),
                                        size: logoWidth * 0.4,
                                      ),
                                ),
                              ),
                              const SizedBox(
                                height: 25,
                              ), // Reduced gap to move logo closer to buttons
                              SizedBox(
                                width: 220,
                                height: 60,
                                child: _SideButton(
                                  icon: Icons.vpn_lock_rounded,
                                  label: 'CONNECT VPN',
                                  height: 60,
                                  onTap: () {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                        content: Text(
                                          'VPN Service coming soon',
                                        ),
                                      ),
                                    );
                                  },
                                ),
                              ),
                              const SizedBox(
                                height: 14,
                              ), // Target 12-16px spacing for grouping
                              SizedBox(
                                width: 220,
                                height: 60,
                                child: _SideButton(
                                  icon: Icons.view_list_rounded,
                                  label: 'LIST PLAYLISTS',
                                  height: 60,
                                  onTap: _openPlaylistList,
                                ),
                              ),
                              const SizedBox(height: 30), // Compensatory spacer
                            ],
                          ),
                        ),
                      ),
                    Expanded(
                      flex: 6,
                      child: Container(
                        padding: EdgeInsets.symmetric(
                          horizontal: isMobile ? 24 : 60,
                        ),
                        child: Center(
                          child: SingleChildScrollView(
                            physics: const BouncingScrollPhysics(),
                            child: Consumer<PlaylistController>(
                              builder: (context, controller, child) {
                                return Form(
                                  key: _formKey,
                                  child: Column(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    crossAxisAlignment: isMobile
                                        ? CrossAxisAlignment.center
                                        : CrossAxisAlignment.start,
                                    children: [
                                      if (isMobile) ...[
                                        Image.asset(
                                          'assets/images/logo.png',
                                          width: logoWidth,
                                          fit: BoxFit.contain,
                                        ),
                                        const SizedBox(height: 16),
                                      ],
                                      Text(
                                        'ENTER YOUR PLAYLIST DETAILS',
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        textAlign: isMobile
                                            ? TextAlign.center
                                            : TextAlign.left,
                                        style: TextStyle(
                                          color: Colors.white,
                                          fontSize: titleFontSize,
                                          fontWeight: FontWeight.w900,
                                          letterSpacing: 1.2,
                                        ),
                                      ),
                                      SizedBox(height: spacing),
                                      FocusTraversalOrder(
                                        order: const NumericFocusOrder(1),
                                        child: _XTextField(
                                          controller: _nameController,
                                          focusNode: _playlistFocus,
                                          label: 'Playlist Name',
                                          icon: Icons.list_rounded,
                                          height: fieldHeight,
                                          textInputAction: TextInputAction.next,
                                          onSubmitted: (_) {
                                            if (mounted) {
                                              FocusScope.of(
                                                context,
                                              ).requestFocus(_usernameFocus);
                                            }
                                          },
                                        ),
                                      ),
                                      SizedBox(height: spacing),
                                      FocusTraversalOrder(
                                        order: const NumericFocusOrder(2),
                                        child: _XTextField(
                                          controller: _usernameController,
                                          focusNode: _usernameFocus,
                                          label: 'Username',
                                          icon: Icons.person_outline_rounded,
                                          height: fieldHeight,
                                          textInputAction: TextInputAction.next,
                                          onSubmitted: (_) {
                                            if (mounted) {
                                              FocusScope.of(
                                                context,
                                              ).requestFocus(_passwordFocus);
                                            }
                                          },
                                        ),
                                      ),
                                      SizedBox(height: spacing),
                                      FocusTraversalOrder(
                                        order: const NumericFocusOrder(3),
                                        child: _XTextField(
                                          controller: _passwordController,
                                          focusNode: _passwordFocus,
                                          label: 'Password',
                                          icon: Icons.lock_outline_rounded,
                                          isPassword: true,
                                          obscure: _obscurePassword,
                                          height: fieldHeight,
                                          textInputAction: TextInputAction.next,
                                          onSubmitted: (_) {
                                            if (mounted) {
                                              FocusScope.of(
                                                context,
                                              ).requestFocus(_urlFocus);
                                            }
                                          },
                                          onToggleObscure: () => setState(
                                            () => _obscurePassword =
                                                !_obscurePassword,
                                          ),
                                        ),
                                      ),
                                      SizedBox(height: spacing),
                                      FocusTraversalOrder(
                                        order: const NumericFocusOrder(4),
                                        child: _XTextField(
                                          controller: _urlController,
                                          focusNode: _urlFocus,
                                          label: 'http://url_here.com:port',
                                          icon: Icons.link_rounded,
                                          height: fieldHeight,
                                          hint: 'http://example.com:8080',
                                          textInputAction: TextInputAction.done,
                                          onSubmitted: (_) {
                                            if (mounted) _savePlaylist();
                                          },
                                        ),
                                      ),
                                      const SizedBox(height: 12),
                                      FocusTraversalOrder(
                                        order: const NumericFocusOrder(5),
                                        child: _AddPlaylistButton(
                                          focusNode: _submitFocus,
                                          isLoading: controller.isLoading,
                                          height: buttonHeight,
                                          onTap: controller.isLoading
                                              ? null
                                              : (_isFormValid
                                                    ? _savePlaylist
                                                    : null),
                                        ),
                                      ),
                                      if (isMobile) ...[
                                        const SizedBox(height: 12),
                                        Row(
                                          mainAxisAlignment:
                                              MainAxisAlignment.center,
                                          children: [
                                            IconButton(
                                              icon: const Icon(
                                                Icons.vpn_lock_rounded,
                                                color: Colors.white70,
                                              ),
                                              onPressed: () {},
                                            ),
                                            const SizedBox(width: 20),
                                            IconButton(
                                              icon: const Icon(
                                                Icons.view_list_rounded,
                                                color: Colors.white70,
                                              ),
                                              onPressed: _openPlaylistList,
                                            ),
                                          ],
                                        ),
                                      ],
                                      if (controller.error != null) ...[
                                        const SizedBox(height: 8),
                                        Text(
                                          controller.error!,
                                          textAlign: TextAlign.center,
                                          style: const TextStyle(
                                            color: Colors.redAccent,
                                            fontWeight: FontWeight.bold,
                                            fontSize: 13,
                                          ),
                                        ),
                                      ],
                                    ],
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _savePlaylist() async {
    if (_formKey.currentState!.validate()) {
      final controller = Provider.of<PlaylistController>(
        context,
        listen: false,
      );

      controller.clearError();

      final repository = IptvRepository(
        ApiConfig(
          baseUrl: _urlController.text.trim(),
          username: _usernameController.text.trim(),
          password: _passwordController.text.trim(),
        ),
        _nameController.text.trim(),
      );

      var playerInfo = await repository.getPlayerInfo(forceRefresh: true);

      if (playerInfo == null || playerInfo.userInfo.auth == 0) {
        controller.setError('Invalid credentials or server unavailable');
        return;
      }

      final playlist = await controller.createPlaylist(
        name: _nameController.text.trim(),
        type: PlaylistType.xtream,
        url: _urlController.text.trim(),
        username: _usernameController.text.trim(),
        password: _passwordController.text.trim(),
      );

      if (playlist != null && mounted) {
        final savedRepository = IptvRepository(
          ApiConfig(
            baseUrl: playlist.url ?? '',
            username: playlist.username ?? '',
            password: playlist.password ?? '',
          ),
          playlist.id,
        );

        await savedRepository.getPlayerInfo(forceRefresh: true);

        if (!mounted) return;
        await controller.openPlaylist(
          context,
          playlist,
          refreshAfterFirstHomePaint: true,
        );
      }
    }
  }

  Future<void> _openPlaylistList() async {
    final playlists = await PlaylistService.getXStreamPlaylists();
    if (!mounted) return;

    if (playlists.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('No playlists found. You need to add one first.'),
          duration: Duration(seconds: 3),
        ),
      );
      FocusScope.of(context).requestFocus(_usernameFocus);
      return;
    }

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => _SavedXtreamPlaylistScreen(playlists: playlists),
      ),
    );
  }
}

class _SavedXtreamPlaylistScreen extends StatelessWidget {
  const _SavedXtreamPlaylistScreen({required this.playlists});

  final List<Playlist> playlists;

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    final bg = config.backgrounds.login;

    return Scaffold(
      backgroundColor: const Color(0xFF050816),
      body: Container(
        decoration: BoxDecoration(
          image: DecorationImage(
            image: bg.isNotEmpty
                ? perfNetworkImage(bg)
                : const AssetImage('assets/images/background.png')
                      as ImageProvider,
            fit: BoxFit.cover,
            colorFilter: ColorFilter.mode(
              Colors.black.withValues(alpha: 0.55),
              BlendMode.darken,
            ),
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 64, vertical: 34),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    WatchioFocusAction(
                      onActivate: () => Navigator.of(context).maybePop(),
                      child: IconButton(
                        onPressed: () => Navigator.of(context).maybePop(),
                        icon: const Icon(
                          Icons.arrow_back_rounded,
                          color: Colors.white,
                          size: 32,
                        ),
                      ),
                    ),
                    const SizedBox(width: 18),
                    Image.asset(
                      'assets/images/App_Logo.png',
                      height: 72,
                      fit: BoxFit.contain,
                    ),
                    const Spacer(),
                    const Text(
                      'SAVED PLAYLISTS',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 28,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.8,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                Expanded(
                  child: FocusTraversalGroup(
                    child: ListView.separated(
                      itemCount: playlists.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 14),
                      itemBuilder: (context, index) => _SavedPlaylistTile(
                        playlist: playlists[index],
                        autofocus: index == 0,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SavedPlaylistTile extends StatefulWidget {
  const _SavedPlaylistTile({required this.playlist, this.autofocus = false});

  final Playlist playlist;
  final bool autofocus;

  @override
  State<_SavedPlaylistTile> createState() => _SavedPlaylistTileState();
}

class _SavedPlaylistTileState extends State<_SavedPlaylistTile> {
  bool _focused = false;

  Future<void> _open() async {
    await context.read<PlaylistController>().openPlaylist(
      context,
      widget.playlist,
      refreshAfterFirstHomePaint: true,
    );
  }

  @override
  Widget build(BuildContext context) {
    return WatchioFocusAction(
      autofocus: widget.autofocus,
      onActivate: _open,
      onFocusChange: (focused) => setState(() => _focused = focused),
      mouseCursor: SystemMouseCursors.click,
      child: GestureDetector(
        onTap: _open,
        child: AnimatedContainer(
          duration: perfDuration(const Duration(milliseconds: 150)),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            gradient: LinearGradient(
              colors: [
                const Color(0xFF21144A).withValues(alpha: 0.92),
                const Color(0xFF16285C).withValues(alpha: 0.92),
              ],
            ),
            border: Border.all(
              color: _focused
                  ? const Color(0xFF00B7FF)
                  : Colors.white.withValues(alpha: 0.12),
              width: _focused ? 2.5 : 1.2,
            ),
          ),
          child: Row(
            children: [
              const Icon(
                Icons.vpn_key_rounded,
                color: Color(0xFF00B7FF),
                size: 34,
              ),
              const SizedBox(width: 18),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.playlist.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      widget.playlist.url ?? '',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.58),
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              const Icon(
                Icons.chevron_right_rounded,
                color: Colors.white70,
                size: 34,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SideButton extends StatefulWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final double height;

  const _SideButton({
    required this.icon,
    required this.label,
    required this.onTap,
    required this.height,
  });

  @override
  State<_SideButton> createState() => _SideButtonState();
}

class _SideButtonState extends State<_SideButton> {
  bool _isFocused = false;
  bool _isHovered = false;

  bool get _isActive => _isFocused || _isHovered;

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      onFocusChange: (val) => setState(() => _isFocused = val),
      onShowHoverHighlight: (val) => setState(() => _isHovered = val),
      shortcuts: WatchioFocusAction.activationShortcuts,
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) => widget.onTap(),
        ),
      },
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _isActive ? 1.03 : 1.0,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOutCubic,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
            width: double.infinity,
            height: widget.height,
            decoration: BoxDecoration(
              color: _isActive
                  ? Colors.white.withValues(alpha: 0.15)
                  : Colors.white.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: _isActive
                    ? const Color(0xFF00B7FF)
                    : Colors.white.withValues(alpha: 0.1),
                width: _isActive ? 3.0 : 1,
              ),
              boxShadow: _isActive
                  ? [
                      BoxShadow(
                        color: const Color(0xFFC12CFF).withValues(alpha: 0.4),
                        blurRadius: 24,
                        spreadRadius: 2,
                      ),
                      BoxShadow(
                        color: const Color(0xFF00B7FF).withValues(alpha: 0.3),
                        blurRadius: 18,
                        spreadRadius: 1,
                      ),
                    ]
                  : [],
            ),
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Icon(
                  widget.icon,
                  color: _isActive ? const Color(0xFF00B7FF) : Colors.white70,
                  size: 28,
                ),
                const SizedBox(width: 16),
                Text(
                  widget.label,
                  style: TextStyle(
                    color: _isActive ? Colors.white : Colors.white70,
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 0.8,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _XTextField extends StatefulWidget {
  final TextEditingController controller;
  final FocusNode focusNode;
  final String label;
  final IconData icon;
  final String? hint;
  final bool isPassword;
  final bool obscure;
  final double height;
  final TextInputAction textInputAction;
  final Function(String)? onSubmitted;
  final VoidCallback? onToggleObscure;

  const _XTextField({
    required this.controller,
    required this.focusNode,
    required this.label,
    required this.icon,
    required this.height,
    this.hint,
    this.isPassword = false,
    this.obscure = false,
    this.textInputAction = TextInputAction.next,
    this.onSubmitted,
    this.onToggleObscure,
  });

  @override
  State<_XTextField> createState() => _XTextFieldState();
}

class _XTextFieldState extends State<_XTextField> {
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    widget.focusNode.addListener(_handleFocusChange);
  }

  @override
  void dispose() {
    widget.focusNode.removeListener(_handleFocusChange);
    super.dispose();
  }

  void _handleFocusChange() {
    if (mounted) {
      setState(() {
        _isFocused = widget.focusNode.hasFocus;
      });
    }
  }

  Future<void> _openEditor() async {
    widget.focusNode.requestFocus();
    final result = await showDialog<String>(
      context: context,
      barrierDismissible: true,
      builder: (context) => _TextEntryDialog(
        title: widget.label,
        controller: widget.controller,
        obscure: widget.obscure,
        isPassword: widget.isPassword,
        textInputAction: widget.textInputAction,
      ),
    );
    if (!mounted) return;
    widget.focusNode.requestFocus();
    if (result != null && widget.textInputAction == TextInputAction.next) {
      widget.onSubmitted?.call(result);
    } else if (result != null &&
        widget.textInputAction == TextInputAction.done) {
      widget.onSubmitted?.call(result);
    }
  }

  String get _displayText {
    final text = widget.controller.text;
    if (text.isEmpty) return widget.hint ?? widget.label;
    if (widget.obscure) return '•' * text.length;
    return text;
  }

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      focusNode: widget.focusNode,
      onFocusChange: (value) => setState(() => _isFocused = value),
      shortcuts: WatchioFocusAction.activationShortcuts,
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            _openEditor();
            return null;
          },
        ),
      },
      child: GestureDetector(
        onTap: _openEditor,
        child: AnimatedScale(
          scale: _isFocused ? 1.01 : 1,
          duration: const Duration(milliseconds: 200),
          child: Container(
            height: widget.height,
            padding: EdgeInsets.all(_isFocused ? 3 : 1),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              gradient: _isFocused
                  ? const LinearGradient(
                      colors: [Color(0xFFC12CFF), Color(0xFF00B7FF)],
                    )
                  : null,
              border: _isFocused
                  ? null
                  : Border.all(
                      color: const Color(0xFF00B7FF).withValues(alpha: 0.3),
                    ),
              boxShadow: _isFocused
                  ? [
                      BoxShadow(
                        color: const Color(0xFFC12CFF).withValues(alpha: 0.4),
                        blurRadius: 15,
                        spreadRadius: 2,
                      ),
                    ]
                  : null,
            ),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              decoration: BoxDecoration(
                color: const Color(0xFF0F1423).withValues(alpha: 0.92),
                borderRadius: BorderRadius.circular(15),
              ),
              child: Row(
                children: [
                  Icon(widget.icon, color: const Color(0xFFC12CFF), size: 22),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Text(
                      _displayText,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: widget.controller.text.isEmpty
                            ? Colors.white.withValues(alpha: 0.45)
                            : Colors.white,
                        fontWeight: FontWeight.w600,
                        fontSize: 16,
                      ),
                    ),
                  ),
                  if (widget.isPassword)
                    IconButton(
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                      icon: Icon(
                        widget.obscure
                            ? Icons.visibility_off
                            : Icons.visibility,
                        color: Colors.white38,
                        size: 20,
                      ),
                      onPressed: widget.onToggleObscure,
                    )
                  else
                    const Icon(
                      Icons.edit_rounded,
                      color: Colors.white38,
                      size: 20,
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _TextEntryDialog extends StatefulWidget {
  final String title;
  final TextEditingController controller;
  final bool obscure;
  final bool isPassword;
  final TextInputAction textInputAction;

  const _TextEntryDialog({
    required this.title,
    required this.controller,
    required this.obscure,
    required this.isPassword,
    required this.textInputAction,
  });

  @override
  State<_TextEntryDialog> createState() => _TextEntryDialogState();
}

class _TextEntryDialogState extends State<_TextEntryDialog> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.controller.text);
    _focusNode = FocusNode();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _focusNode.requestFocus();
      _controller.selection = TextSelection(
        baseOffset: 0,
        extentOffset: _controller.text.length,
      );
      SystemChannels.textInput.invokeMethod<void>('TextInput.show');
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _save() {
    widget.controller.text = _controller.text;
    Navigator.of(context).pop(_controller.text);
  }

  @override
  Widget build(BuildContext context) {
    final screenHeight = MediaQuery.sizeOf(context).height;
    final keyboardHeight = MediaQuery.viewInsetsOf(context).bottom;
    final maxDialogHeight = (screenHeight - keyboardHeight - 48)
        .clamp(160.0, 360.0)
        .toDouble();
    final compact = maxDialogHeight < 260;

    return Dialog(
      backgroundColor: const Color(0xFF111827),
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: compact ? 520 : 620,
          maxHeight: maxDialogHeight,
        ),
        child: SingleChildScrollView(
          physics: const ClampingScrollPhysics(),
          padding: EdgeInsets.all(compact ? 16 : 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                widget.title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.w900,
                ),
              ),
              SizedBox(height: compact ? 10 : 18),
              SizedBox(
                height: compact ? 54 : 70,
                child: TextField(
                  controller: _controller,
                  focusNode: _focusNode,
                  autofocus: true,
                  obscureText: widget.obscure,
                  keyboardType: widget.isPassword
                      ? TextInputType.visiblePassword
                      : TextInputType.text,
                  textInputAction: widget.textInputAction,
                  onSubmitted: (_) => _save(),
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: compact ? 16 : 20,
                  ),
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: const Color(0xFF0F1423),
                    contentPadding: EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: compact ? 10 : 18,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: const BorderSide(color: Color(0xFFC12CFF)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: const BorderSide(
                        color: Color(0xFFC12CFF),
                        width: 2,
                      ),
                    ),
                  ),
                ),
              ),
              SizedBox(height: compact ? 12 : 22),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('CANCEL'),
                  ),
                  const SizedBox(width: 12),
                  ElevatedButton(onPressed: _save, child: const Text('DONE')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AddPlaylistButton extends StatefulWidget {
  final bool isLoading;
  final double height;
  final FocusNode focusNode;
  final VoidCallback? onTap;

  const _AddPlaylistButton({
    required this.isLoading,
    required this.height,
    required this.focusNode,
    this.onTap,
  });

  @override
  State<_AddPlaylistButton> createState() => _AddPlaylistButtonState();
}

class _AddPlaylistButtonState extends State<_AddPlaylistButton> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      focusNode: widget.focusNode,
      onFocusChange: (val) => setState(() => _isFocused = val),
      shortcuts: WatchioFocusAction.activationShortcuts,
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) => widget.onTap?.call(),
        ),
      },
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _isFocused ? 1.02 : 1.0,
          duration: const Duration(milliseconds: 200),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            width: double.infinity,
            height: widget.height,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: _isFocused
                    ? [const Color(0xFFD14CFF), const Color(0xFF20C7FF)]
                    : [const Color(0xFFC12CFF), const Color(0xFF00B7FF)],
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
              ),
              borderRadius: BorderRadius.circular(18),
              border: _isFocused
                  ? Border.all(color: Colors.white, width: 2.5)
                  : null,
              boxShadow: [
                BoxShadow(
                  color: const Color(
                    0xFF00B7FF,
                  ).withValues(alpha: _isFocused ? 0.6 : 0.4),
                  blurRadius: _isFocused ? 30 : 15,
                  offset: Offset(0, _isFocused ? 8 : 4),
                ),
              ],
            ),
            child: Center(
              child: widget.isLoading
                  ? const SizedBox(
                      height: 24,
                      width: 24,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2.5,
                      ),
                    )
                  : const Text(
                      'ADD PLAYLIST',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.5,
                      ),
                    ),
            ),
          ),
        ),
      ),
    );
  }
}
