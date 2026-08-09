import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/screens/m3u/m3u_data_loader_screen.dart';
import 'package:watchio/l10n/localization_extension.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../../../controllers/playlist_controller.dart';
import '../../../../services/config_service.dart';

class NewM3uPlaylistScreen extends StatefulWidget {
  const NewM3uPlaylistScreen({super.key});

  @override
  NewM3uPlaylistScreenState createState() => NewM3uPlaylistScreenState();
}

class NewM3uPlaylistScreenState extends State<NewM3uPlaylistScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController(text: 'M3U Playlist-1');
  final _urlController = TextEditingController();

  final FocusNode _nameFocus = FocusNode();
  final FocusNode _urlFocus = FocusNode();
  final FocusNode _submitFocus = FocusNode();

  bool _isUrlSource = true;
  bool _isFormValid = false;
  String? _selectedFilePath;
  String? _selectedFileName;

  @override
  void initState() {
    super.initState();
    _nameController.addListener(_validateForm);
    _urlController.addListener(_validateForm);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _nameFocus.requestFocus();
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    _urlController.dispose();
    _nameFocus.dispose();
    _urlFocus.dispose();
    _submitFocus.dispose();
    super.dispose();
  }

  void _validateForm() {
    setState(() {
      final String name = _nameController.text.trim();
      if (_isUrlSource) {
        _isFormValid = name.isNotEmpty && _urlController.text.trim().isNotEmpty;
      } else {
        _isFormValid = name.isNotEmpty && _selectedFilePath != null;
      }
    });
  }

  void _onSourceTypeChanged(bool isUrl) {
    setState(() {
      _isUrlSource = isUrl;
      if (isUrl) {
        _selectedFilePath = null;
        _selectedFileName = null;
      } else {
        _urlController.clear();
      }
    });
    _validateForm();
  }

  Future<void> _pickFile() async {
    try {
      FilePickerResult? result = await FilePicker.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['m3u', 'm3u8'],
        allowMultiple: false,
      );

      if (result != null) {
        setState(() {
          _selectedFilePath = result.files.single.path;
          _selectedFileName = result.files.single.name;
        });
        _validateForm();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(context.loc.file_selection_error)),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    final loginBg = config.backgrounds.login;

    return Scaffold(
      backgroundColor: const Color(0xFF050816),
      resizeToAvoidBottomInset: false,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final double width = constraints.maxWidth;
          final double height = constraints.maxHeight;
          final bool isMobile = width < 700;
          final bool isTV = width >= 1600;

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

          if (height < 450) {
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
                      ? NetworkImage(loginBg)
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
                    if (!isMobile)
                      Expanded(
                        flex: 4,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 20),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.center,
                            children: [
                              const SizedBox(height: 20), // Shift logo downward
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
                              const SizedBox(height: 25), // Gap logo -> VPN
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
                              ), // Gap VPN -> Playlists
                              SizedBox(
                                width: 220,
                                height: 60,
                                child: _SideButton(
                                  icon: Icons.view_list_rounded,
                                  label: 'LIST PLAYLISTS',
                                  height: 60,
                                  onTap: () => Navigator.pop(context),
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
                                        'ADD M3U PLAYLIST',
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
                                      _XTextField(
                                        controller: _nameController,
                                        focusNode: _nameFocus,
                                        label: context.loc.playlist_name,
                                        icon: Icons.list_rounded,
                                        height: fieldHeight,
                                        textInputAction: TextInputAction.next,
                                        onSubmitted: (_) {
                                          if (_isUrlSource) {
                                            FocusScope.of(
                                              context,
                                            ).requestFocus(_urlFocus);
                                          }
                                        },
                                      ),
                                      SizedBox(height: spacing),
                                      Row(
                                        children: [
                                          Expanded(
                                            child: _SourceTypeCard(
                                              label: 'M3U URL',
                                              icon: Icons.link_rounded,
                                              isSelected: _isUrlSource,
                                              onTap: () =>
                                                  _onSourceTypeChanged(true),
                                            ),
                                          ),
                                          const SizedBox(width: 16),
                                          Expanded(
                                            child: _SourceTypeCard(
                                              label: 'M3U FILE',
                                              icon: Icons.file_present_rounded,
                                              isSelected: !_isUrlSource,
                                              onTap: () =>
                                                  _onSourceTypeChanged(false),
                                            ),
                                          ),
                                        ],
                                      ),
                                      SizedBox(height: spacing),
                                      if (_isUrlSource)
                                        _XTextField(
                                          controller: _urlController,
                                          focusNode: _urlFocus,
                                          label: 'http://url_here.com/file.m3u',
                                          icon: Icons.link_rounded,
                                          height: fieldHeight,
                                          hint: context.loc.m3u_url_hint,
                                          textInputAction: TextInputAction.done,
                                          onSubmitted: (_) => _savePlaylist(),
                                        )
                                      else
                                        _FilePickerCard(
                                          fileName: _selectedFileName,
                                          onTap: _pickFile,
                                          height: fieldHeight,
                                        ),
                                      const SizedBox(height: 12),
                                      _AddPlaylistButton(
                                        label: context.loc.create_playlist
                                            .toUpperCase(),
                                        focusNode: _submitFocus,
                                        isLoading: controller.isLoading,
                                        height: buttonHeight,
                                        onTap: controller.isLoading
                                            ? null
                                            : (_isFormValid
                                                  ? _savePlaylist
                                                  : null),
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
                                              onPressed: () =>
                                                  Navigator.pop(context),
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
    final playlistController = Provider.of<PlaylistController>(
      context,
      listen: false,
    );

    playlistController.clearError();

    // 1. Validate Playlist Name
    if (_nameController.text.trim().isEmpty) {
      playlistController.setError("Please enter a playlist name");
      return;
    }

    // 2. Source-specific validation
    if (_isUrlSource) {
      final String url = _urlController.text.trim();
      if (url.isEmpty) {
        playlistController.setError("Please enter an M3U URL");
        return;
      }

      final uri = Uri.tryParse(url);
      if (uri == null || !uri.hasScheme || !uri.hasAuthority) {
        playlistController.setError("Please enter a valid URL");
        return;
      }

      if (!['http', 'https'].contains(uri.scheme)) {
        playlistController.setError("Please enter a valid HTTP/HTTPS URL");
        return;
      }
    } else {
      if (_selectedFilePath == null) {
        playlistController.setError("Please select an M3U file");
        return;
      }
    }

    // 3. Proceed with creation
    var playlist = await playlistController.createPlaylist(
      name: _nameController.text.trim(),
      type: PlaylistType.m3u,
      url: _isUrlSource ? _urlController.text.trim() : _selectedFileName,
    );

    if (playlist != null && mounted) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (context) => M3uDataLoaderScreen(
            playlist: playlist,
            m3uItems: const [],
            streamingUrl: _isUrlSource ? _urlController.text.trim() : null,
            streamingFilePath: _isUrlSource ? null : _selectedFilePath,
          ),
        ),
      );
    }
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

class _SourceTypeCard extends StatefulWidget {
  final String label;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _SourceTypeCard({
    required this.label,
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  State<_SourceTypeCard> createState() => _SourceTypeCardState();
}

class _SourceTypeCardState extends State<_SourceTypeCard> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    final active = widget.isSelected || _isFocused;
    return FocusableActionDetector(
      onFocusChange: (val) => setState(() => _isFocused = val),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) => widget.onTap(),
        ),
      },
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(vertical: 16),
          decoration: BoxDecoration(
            color: active
                ? const Color(0xFFC12CFF).withValues(alpha: 0.2)
                : const Color(0xFF0F1423).withValues(alpha: 0.75),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: active
                  ? const Color(0xFF00B7FF)
                  : Colors.white.withValues(alpha: 0.1),
              width: active ? 2.5 : 1,
            ),
          ),
          child: Column(
            children: [
              Icon(
                widget.icon,
                color: active ? const Color(0xFF00B7FF) : Colors.white54,
              ),
              const SizedBox(height: 8),
              Text(
                widget.label,
                style: TextStyle(
                  color: active ? Colors.white : Colors.white54,
                  fontWeight: FontWeight.w900,
                  fontSize: 13,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FilePickerCard extends StatefulWidget {
  final String? fileName;
  final VoidCallback onTap;
  final double height;

  const _FilePickerCard({
    required this.fileName,
    required this.onTap,
    required this.height,
  });

  @override
  State<_FilePickerCard> createState() => _FilePickerCardState();
}

class _FilePickerCardState extends State<_FilePickerCard> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      onFocusChange: (val) => setState(() => _isFocused = val),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) => widget.onTap(),
        ),
      },
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          height: widget.height,
          padding: const EdgeInsets.symmetric(horizontal: 20),
          decoration: BoxDecoration(
            color: const Color(0xFF0F1423).withValues(alpha: 0.75),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: _isFocused
                  ? const Color(0xFF00B7FF)
                  : Colors.white.withValues(alpha: 0.1),
              width: _isFocused ? 2.5 : 1,
            ),
          ),
          child: Row(
            children: [
              Icon(
                Icons.file_upload_rounded,
                color: const Color(0xFFC12CFF),
                size: 24,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Text(
                  widget.fileName ?? 'SELECT M3U FILE',
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w900,
                    fontSize: 15,
                  ),
                ),
              ),
              const Icon(
                Icons.arrow_forward_ios_rounded,
                color: Colors.white24,
                size: 16,
              ),
            ],
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
  final double height;
  final TextInputAction textInputAction;
  final Function(String)? onSubmitted;

  const _XTextField({
    required this.controller,
    required this.focusNode,
    required this.label,
    required this.icon,
    required this.height,
    this.hint,
    this.textInputAction = TextInputAction.next,
    this.onSubmitted,
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
        textInputAction: widget.textInputAction,
      ),
    );
    if (!mounted) return;
    widget.focusNode.requestFocus();
    if (result != null) {
      widget.onSubmitted?.call(result);
    }
  }

  String get _displayText {
    final text = widget.controller.text;
    if (text.isEmpty) return widget.hint ?? widget.label;
    return text;
  }

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      focusNode: widget.focusNode,
      onFocusChange: (value) => setState(() => _isFocused = value),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
      },
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
          scale: _isFocused ? 1.01 : 1.0,
          duration: const Duration(milliseconds: 200),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            height: widget.height,
            decoration: BoxDecoration(
              color: const Color(0xFF0F1423).withValues(alpha: 0.75),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: _isFocused
                    ? Colors.transparent
                    : const Color(0xFF00B7FF).withValues(alpha: 0.3),
                width: _isFocused ? 0 : 1,
              ),
              boxShadow: _isFocused
                  ? [
                      BoxShadow(
                        color: const Color(0xFFC12CFF).withValues(alpha: 0.4),
                        blurRadius: 15,
                        spreadRadius: 2,
                      ),
                      BoxShadow(
                        color: const Color(0xFF00B7FF).withValues(alpha: 0.2),
                        blurRadius: 25,
                        spreadRadius: 4,
                      ),
                    ]
                  : [],
            ),
            child: Container(
              decoration: _isFocused
                  ? BoxDecoration(
                      borderRadius: BorderRadius.circular(18),
                      border: const Border.fromBorderSide(
                        BorderSide(width: 3.0, color: Colors.transparent),
                      ),
                      gradient: const LinearGradient(
                        colors: [Color(0xFFC12CFF), Color(0xFF00B7FF)],
                      ),
                    )
                  : null,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                decoration: _isFocused
                    ? BoxDecoration(
                        color: const Color(0xFF0F1423),
                        borderRadius: BorderRadius.circular(15),
                      )
                    : null,
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
      ),
    );
  }
}

class _TextEntryDialog extends StatefulWidget {
  final String title;
  final TextEditingController controller;
  final TextInputAction textInputAction;

  const _TextEntryDialog({
    required this.title,
    required this.controller,
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
    return Dialog(
      backgroundColor: const Color(0xFF111827),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 620),
        child: Padding(
          padding: const EdgeInsets.all(28),
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
              const SizedBox(height: 18),
              TextField(
                controller: _controller,
                focusNode: _focusNode,
                autofocus: true,
                keyboardType: TextInputType.text,
                textInputAction: widget.textInputAction,
                onSubmitted: (_) => _save(),
                style: const TextStyle(color: Colors.white, fontSize: 20),
                decoration: InputDecoration(
                  filled: true,
                  fillColor: const Color(0xFF0F1423),
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
              const SizedBox(height: 22),
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
  final String label;
  final bool isLoading;
  final double height;
  final FocusNode focusNode;
  final VoidCallback? onTap;

  const _AddPlaylistButton({
    required this.label,
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
    final bool isEnabled = widget.onTap != null;

    return FocusableActionDetector(
      focusNode: widget.focusNode,
      enabled: isEnabled,
      onFocusChange: (val) => setState(() => _isFocused = val),
      shortcuts: {
        const SingleActivator(LogicalKeyboardKey.enter): const ActivateIntent(),
        const SingleActivator(LogicalKeyboardKey.select):
            const ActivateIntent(),
      },
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
          child: AnimatedOpacity(
            duration: const Duration(milliseconds: 200),
            opacity: isEnabled ? 1.0 : 0.4,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: double.infinity,
              height: widget.height,
              decoration: BoxDecoration(
                gradient: isEnabled
                    ? LinearGradient(
                        colors: _isFocused
                            ? [const Color(0xFFD14CFF), const Color(0xFF20C7FF)]
                            : [
                                const Color(0xFFC12CFF),
                                const Color(0xFF00B7FF),
                              ],
                        begin: Alignment.centerLeft,
                        end: Alignment.centerRight,
                      )
                    : null,
                color: isEnabled ? null : Colors.white.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(18),
                border: _isFocused
                    ? Border.all(color: Colors.white, width: 2.5)
                    : null,
                boxShadow: isEnabled
                    ? [
                        BoxShadow(
                          color: const Color(
                            0xFF00B7FF,
                          ).withValues(alpha: _isFocused ? 0.6 : 0.4),
                          blurRadius: _isFocused ? 30 : 15,
                          offset: Offset(0, _isFocused ? 8 : 4),
                        ),
                      ]
                    : null,
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
                    : Text(
                        widget.label,
                        style: const TextStyle(
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
      ),
    );
  }
}
