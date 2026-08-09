import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:provider/provider.dart';
import '../../../services/config_service.dart';
import '../../../core/theme/theme_manager.dart';
import '../../../shared/widgets/watchio_focus_action.dart';
import '../../../utils/firestick_performance.dart';

class WatchioSettingsScaffold extends StatefulWidget {
  final String title;
  final Widget child;
  final VoidCallback onBack;

  const WatchioSettingsScaffold({
    super.key,
    required this.title,
    required this.child,
    required this.onBack,
  });

  @override
  State<WatchioSettingsScaffold> createState() =>
      _WatchioSettingsScaffoldState();
}

class _WatchioSettingsScaffoldState extends State<WatchioSettingsScaffold> {
  late Timer _timer;
  DateTime _now = DateTime.now();
  String _version = 'v0.0.1';

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (timer) {
      if (mounted) setState(() => _now = DateTime.now());
    });
    _loadVersion();
  }

  Future<void> _loadVersion() async {
    final info = await PackageInfo.fromPlatform();
    if (mounted) setState(() => _version = 'v${info.version}');
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    final themeManager = context.watch<ThemeManager>();
    final homeBg = config.backgrounds.home;

    final background = Theme.of(context).scaffoldBackgroundColor;

    return Scaffold(
      backgroundColor: background,
      body: Stack(
        children: [
          // Background Layer
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(
                color: background,
                image: DecorationImage(
                  image: (themeManager.showBackgroundImage && homeBg.isNotEmpty)
                      ? perfNetworkImage(homeBg)
                      : const AssetImage('assets/images/background.png')
                            as ImageProvider,
                  fit: BoxFit.cover,
                ),
              ),
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      background.withValues(alpha: 0.2),
                      background.withValues(alpha: 0.6),
                      background.withValues(alpha: 0.9),
                    ],
                  ),
                ),
              ),
            ),
          ),
          // Content Layer
          Column(
            children: [
              // REFINED COMPACT HEADER - MATCHES APPROVED DESIGN
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
                child: SizedBox(
                  height: 58,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      // LEFT: Back + Logo
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            _HeaderIconButton(
                              icon: Icons.arrow_back_rounded,
                              onTap: widget.onBack,
                            ),
                            const SizedBox(width: 10),
                            SizedBox(
                              height: 52,
                              width: 135,
                              child: OverflowBox(
                                maxHeight: 90,
                                child: Image.asset(
                                  'assets/images/App_Logo.png',
                                  height: 90,
                                  fit: BoxFit.contain,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      // CENTER: Page Title (Perfectly centered on the line)
                      Text(
                        widget.title.toUpperCase(),
                        style: GoogleFonts.outfit(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 2.0,
                        ),
                      ),
                      // RIGHT: Time & Date (Aligned right on the same line)
                      Align(
                        alignment: Alignment.centerRight,
                        child: Text(
                          '${DateFormat('hh:mm A').format(_now)} | ${DateFormat('MMM d, yyyy').format(_now)}',
                          style: GoogleFonts.outfit(
                            color: Colors.white,
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              // MAIN CONTENT
              Expanded(child: widget.child),

              // MINIMAL FOOTER
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Text(
                  _version,
                  style: GoogleFonts.outfit(
                    color: Colors.white24,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _HeaderIconButton extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _HeaderIconButton({required this.icon, required this.onTap});

  @override
  State<_HeaderIconButton> createState() => _HeaderIconButtonState();
}

class _HeaderIconButtonState extends State<_HeaderIconButton> {
  bool _isFocused = false;
  @override
  Widget build(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    return WatchioFocusAction(
      onFocusChange: (v) => setState(() => _isFocused = v),
      onActivate: widget.onTap,
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: _isFocused
                ? Colors.white.withValues(alpha: 0.2)
                : Colors.white.withValues(alpha: 0.05),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: _isFocused ? accent : Colors.white10),
          ),
          child: Icon(
            widget.icon,
            color: _isFocused ? Colors.white : Colors.white70,
            size: 22,
          ),
        ),
      ),
    );
  }
}
