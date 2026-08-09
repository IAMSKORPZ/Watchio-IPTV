import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/theme/theme_manager.dart';
import '../../../shared/widgets/glass_panel.dart';
import '../widgets/watchio_settings_scaffold.dart';

class AppearancePage extends StatelessWidget {
  const AppearancePage({super.key});

  @override
  Widget build(BuildContext context) {
    final manager = context.watch<ThemeManager>();

    return WatchioSettingsScaffold(
      title: 'APPEARANCE',
      onBack: () => Navigator.pop(context),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(40, 8, 40, 8),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 680),
            child: GlassPanel(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Column(
                children: [
                  _ChoiceTile<AppThemeType>(
                    title: 'Colour Theme',
                    subtitle: 'Changes highlights, borders and glow',
                    value: manager.currentThemeType,
                    label: _themeName,
                    options: AppThemeType.values,
                    onChanged: manager.setThemeType,
                  ),
                  _ColorTile(
                    title: 'Highlight Colour',
                    subtitle: 'Focus rings, selected states and main buttons',
                    value: Theme.of(context).colorScheme.primary,
                    options: const [
                      Color(0xFFFF3D9A),
                      Color(0xFFFF58B0),
                      Color(0xFFFF4F64),
                      Color(0xFFFF7A1A),
                      Color(0xFFFACC15),
                      Color(0xFF22C55E),
                      Color(0xFFA855F7),
                      Color(0xFFC45CFF),
                      Color(0xFF20D9D2),
                      Color(0xFF38BDF8),
                      Color(0xFF2563EB),
                      Color(0xFFFFFFFF),
                    ],
                    onChanged: manager.setHighlightColor,
                  ),
                  _ColorTile(
                    title: 'Glow Colour',
                    subtitle: 'Gradients, progress and active accents',
                    value: Theme.of(context).colorScheme.secondary,
                    options: const [
                      Color(0xFF20D9D2),
                      Color(0xFF39EEE5),
                      Color(0xFF38BDF8),
                      Color(0xFF2563EB),
                      Color(0xFF22C55E),
                      Color(0xFFA855F7),
                      Color(0xFFC45CFF),
                      Color(0xFFFF3D9A),
                      Color(0xFFFF4F64),
                      Color(0xFFFACC15),
                      Color(0xFFD95CFF),
                    ],
                    onChanged: manager.setSecondaryColor,
                  ),
                  _ChoiceTile<String>(
                    title: 'Background Style',
                    subtitle: 'Changes backgrounds across the application',
                    value: manager.backgroundStyle,
                    label: _backgroundName,
                    options: const ['dynamic', 'dark', 'amoled', 'custom'],
                    onChanged: manager.setBackgroundStyle,
                  ),
                  _ColorTile(
                    title: 'Background Colour',
                    subtitle: 'Base app background when custom is active',
                    value: Theme.of(context).scaffoldBackgroundColor,
                    options: const [
                      Color(0xFF050712),
                      Color(0xFF0B1020),
                      Color(0xFF111827),
                      Color(0xFF000000),
                      Color(0xFF141E30),
                      Color(0xFF180B22),
                      Color(0xFF061C1B),
                      Color(0xFF101018),
                      Color(0xFF160B12),
                    ],
                    onChanged: manager.setBackgroundColor,
                  ),
                  _ColorTile(
                    title: 'Panel Colour',
                    subtitle: 'Cards, menus and settings panels',
                    value: Theme.of(context).colorScheme.surface,
                    options: const [
                      Color(0xFF0B1020),
                      Color(0xFF101426),
                      Color(0xFF111327),
                      Color(0xFF121212),
                      Color(0xFF1A1D29),
                      Color(0xFF172033),
                      Color(0xFF1B1230),
                      Color(0xFF102622),
                      Color(0xFF24131B),
                    ],
                    onChanged: manager.setSurfaceColor,
                  ),
                  _ChoiceTile<String>(
                    title: 'Tile Style',
                    subtitle: 'Changes card corners and glass intensity',
                    value: manager.tileStyle,
                    label: _tileName,
                    options: const ['rounded', 'compact'],
                    onChanged: manager.setTileStyle,
                  ),
                  SwitchListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 2,
                    ),
                    dense: true,
                    visualDensity: VisualDensity.compact,
                    title: Text('Animations', style: _titleStyle),
                    subtitle: Text(
                      'Enable smooth transitions and focus effects',
                      style: _subtitleStyle,
                    ),
                    value: manager.animationsEnabled,
                    onChanged: manager.setAnimationsEnabled,
                    activeThumbColor: Theme.of(context).colorScheme.primary,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  static String _themeName(AppThemeType type) => switch (type) {
    AppThemeType.bingieNeon => 'Default',
    AppThemeType.emerald => 'Emerald',
    AppThemeType.crimson => 'Crimson',
    AppThemeType.ocean => 'Ocean',
    AppThemeType.gold => 'Gold',
    AppThemeType.midnight => 'Midnight',
    AppThemeType.amoled => 'AMOLED',
    AppThemeType.custom => 'Custom',
  };

  static String _backgroundName(String value) => switch (value) {
    'dark' => 'Dark Gradient',
    'amoled' => 'AMOLED Black',
    'custom' => 'Custom Colour',
    _ => 'Dynamic Mesh',
  };

  static String _tileName(String value) =>
      value == 'compact' ? 'Compact Glass' : 'Rounded Glass';

  static final _titleStyle = GoogleFonts.outfit(
    color: Colors.white,
    fontWeight: FontWeight.bold,
    fontSize: 13,
  );
  static final _subtitleStyle = GoogleFonts.outfit(
    color: Colors.white38,
    fontSize: 10,
  );
}

class _ColorTile extends StatelessWidget {
  const _ColorTile({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.options,
    required this.onChanged,
  });

  final String title;
  final String subtitle;
  final Color value;
  final List<Color> options;
  final Future<void> Function(Color) onChanged;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: AppearancePage._titleStyle),
                    const SizedBox(height: 2),
                    Text(subtitle, style: AppearancePage._subtitleStyle),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Flexible(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  alignment: WrapAlignment.end,
                  children: [
                    for (final color in options)
                      _SwatchButton(
                        color: color,
                        selected: color.toARGB32() == value.toARGB32(),
                        onTap: () => onChanged(color),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const Divider(color: Colors.white10, indent: 16, endIndent: 16),
      ],
    );
  }
}

class _SwatchButton extends StatelessWidget {
  const _SwatchButton({
    required this.color,
    required this.selected,
    required this.onTap,
  });

  final Color color;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(999),
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        width: 24,
        height: 24,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: Border.all(
            color: selected ? Colors.white : Colors.white24,
            width: selected ? 3 : 1,
          ),
          boxShadow: selected
              ? [
                  BoxShadow(
                    color: color.withValues(alpha: 0.55),
                    blurRadius: 12,
                    spreadRadius: 1,
                  ),
                ]
              : null,
        ),
      ),
    );
  }
}

class _ChoiceTile<T> extends StatelessWidget {
  const _ChoiceTile({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.label,
    required this.options,
    required this.onChanged,
  });

  final String title;
  final String subtitle;
  final T value;
  final String Function(T) label;
  final List<T> options;
  final Future<void> Function(T) onChanged;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ListTile(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 0,
          ),
          dense: true,
          visualDensity: const VisualDensity(vertical: -3),
          title: Text(title, style: AppearancePage._titleStyle),
          subtitle: Text(subtitle, style: AppearancePage._subtitleStyle),
          trailing: DropdownButton<T>(
            value: value,
            dropdownColor: const Color(0xFF1A1D29),
            underline: const SizedBox.shrink(),
            style: GoogleFonts.outfit(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.bold,
            ),
            items: options
                .map(
                  (option) => DropdownMenuItem<T>(
                    value: option,
                    child: Text(label(option)),
                  ),
                )
                .toList(),
            onChanged: (option) {
              if (option != null) onChanged(option);
            },
          ),
        ),
        const Divider(color: Colors.white10, indent: 16, endIndent: 16),
      ],
    );
  }
}
