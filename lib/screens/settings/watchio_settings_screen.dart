import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import '../../controllers/xtream_code_home_controller.dart';
import '../../core/theme/theme_extensions.dart';
import '../../core/theme/theme_manager.dart';
import '../../shared/widgets/watchio_focus_action.dart';
import 'widgets/watchio_settings_scaffold.dart';
import 'sections/provider_management_page.dart';
import 'sections/account_info_page.dart';
import 'sections/player_settings_page.dart';
import 'sections/epg_settings_page.dart';
import 'sections/parental_controls_page.dart';
import 'sections/stream_format_page.dart';
import 'sections/input_mode_page.dart';
import 'sections/appearance_page.dart';
import 'sections/backup_restore_page.dart';
import '../update/update_screen.dart';
import 'dart:ui';
import '../../utils/firestick_performance.dart';

class WatchioSettingsScreen extends StatelessWidget {
  const WatchioSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final homeController = Provider.of<XtreamCodeHomeController>(
      context,
      listen: false,
    );

    return WatchioSettingsScaffold(
      title: 'SETTINGS',
      onBack: () => homeController.onNavigationTap(0),
      child: LayoutBuilder(
        builder: (context, constraints) => GridView.count(
          padding: const EdgeInsets.fromLTRB(40, 4, 40, 8),
          crossAxisCount: 3,
          mainAxisSpacing: 12,
          crossAxisSpacing: 20,
          mainAxisExtent: (((constraints.maxHeight - 24) / 2) + 18).clamp(
            110.0,
            165.0,
          ),
          children: [
            SettingsTile(
              title: 'Provider Management',
              subtitle: 'Manage IPTV providers',
              icon: Icons.dns_rounded,
              onTap: () => _navigate(context, const ProviderManagementPage()),
            ),
            SettingsTile(
              title: 'Account Information',
              subtitle: 'View your account details',
              icon: Icons.person_rounded,
              onTap: () => _navigate(context, const AccountInfoPage()),
            ),
            SettingsTile(
              title: 'Player Settings',
              subtitle: 'Playback and video settings',
              icon: Icons.play_circle_filled_rounded,
              onTap: () => _navigate(context, const PlayerSettingsPage()),
            ),
            SettingsTile(
              title: 'EPG Settings',
              subtitle: 'Guide and programme settings',
              icon: Icons.calendar_view_day_rounded,
              onTap: () => _navigate(context, const EpgSettingsPage()),
            ),
            SettingsTile(
              title: 'Parental Controls',
              subtitle: 'Restrict content and settings',
              icon: Icons.lock_rounded,
              onTap: () => _navigate(context, const ParentalControlsPage()),
            ),
            SettingsTile(
              title: 'Stream Format',
              subtitle: 'Choose your preferred format',
              icon: Icons.settings_input_component_rounded,
              onTap: () => _navigate(context, const StreamFormatPage()),
            ),
            SettingsTile(
              title: 'Input Mode',
              subtitle: 'Mobile touch or TV remote controls',
              icon: Icons.devices_other_rounded,
              onTap: () => _navigate(context, const InputModePage()),
            ),
            SettingsTile(
              title: 'Appearance',
              subtitle: 'Theme and visual customization',
              icon: Icons.palette_rounded,
              onTap: () => _navigate(context, const AppearancePage()),
            ),
            SettingsTile(
              title: 'Backup & Restore',
              subtitle: 'Export and restore application data',
              icon: Icons.backup_rounded,
              onTap: () => _navigate(context, const BackupRestorePage()),
            ),
            SettingsTile(
              title: 'Check for Updates',
              subtitle: 'Check for a newer Watchio version',
              icon: Icons.system_update_alt_rounded,
              onTap: () => _navigate(context, const UpdateScreen()),
            ),
          ],
        ),
      ),
    );
  }

  void _navigate(BuildContext context, Widget page) {
    Navigator.push(context, MaterialPageRoute(builder: (context) => page));
  }
}

class SettingsTile extends StatefulWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final VoidCallback onTap;

  const SettingsTile({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.onTap,
  });

  @override
  State<SettingsTile> createState() => _SettingsTileState();
}

class _SettingsTileState extends State<SettingsTile> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    final accentColor = Theme.of(context).colorScheme.primary;
    final themeManager = context.watch<ThemeManager>();
    final panelGradient = WatchioThemeExtension.of(context).panelGradient;

    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxHeight < 175;

        return FocusableActionDetector(
          onFocusChange: (value) => setState(() => _isFocused = value),
          shortcuts: WatchioFocusAction.activationShortcuts,
          actions: {
            ActivateIntent: CallbackAction<ActivateIntent>(
              onInvoke: (_) {
                widget.onTap();
                return null;
              },
            ),
          },
          child: AnimatedScale(
            scale: themeManager.animationsEnabled && _isFocused ? 1.05 : 1.0,
            duration: themeManager.animationsEnabled
                ? perfDuration(const Duration(milliseconds: 200))
                : Duration.zero,
            curve: Curves.easeOutCubic,
            child: InkWell(
              onTap: widget.onTap,
              borderRadius: BorderRadius.circular(30),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(30),
                  border: Border.all(
                    color: _isFocused
                        ? accentColor
                        : Colors.white.withValues(alpha: 0.1),
                    width: 2.5,
                  ),
                  boxShadow: firestickPerformanceMode
                      ? null
                      : [
                          if (_isFocused)
                            BoxShadow(
                              color: accentColor.withValues(alpha: 0.4),
                              blurRadius: 30,
                              spreadRadius: 2,
                            ),
                        ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(30),
                  child: BackdropFilter(
                    filter: ImageFilter.blur(
                      sigmaX: perfBlur(20),
                      sigmaY: perfBlur(20),
                    ),
                    child: Container(
                      decoration: BoxDecoration(gradient: panelGradient),
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: compact ? 4 : 8,
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            Icon(
                              widget.icon,
                              color: _isFocused ? Colors.white : Colors.white70,
                              size: compact ? 40 : 56,
                            ),
                            SizedBox(height: compact ? 2 : 8),
                            Text(
                              widget.title,
                              textAlign: TextAlign.center,
                              maxLines: 2,
                              style: GoogleFonts.outfit(
                                color: Colors.white,
                                fontSize: compact ? 16 : 18,
                                fontWeight: FontWeight.w900,
                                letterSpacing: 1.1,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              widget.subtitle,
                              textAlign: TextAlign.center,
                              maxLines: 2,
                              style: GoogleFonts.outfit(
                                color: _isFocused
                                    ? Colors.white70
                                    : Colors.white38,
                                fontSize: compact ? 11 : 12,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
