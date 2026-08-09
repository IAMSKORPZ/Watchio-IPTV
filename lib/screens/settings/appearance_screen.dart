import 'package:watchio/core/theme/theme_manager.dart';
import 'package:watchio/core/theme/app_theme.dart';
import 'package:watchio/shared/widgets/app_card.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class AppearanceScreen extends StatelessWidget {
  const AppearanceScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final themeManager = context.watch<ThemeManager>();

    return Scaffold(
      appBar: AppBar(title: const Text('Appearance')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Themes', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 180,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 1.15,
            ),
            itemCount: AppThemeType.values.length - 1, // Exclude custom for now
            itemBuilder: (context, index) {
              final type = AppThemeType.values[index];
              final selected = themeManager.currentThemeType == type;
              return _ThemeTile(
                type: type,
                selected: selected,
                onTap: () => themeManager.setThemeType(type),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ThemeTile extends StatelessWidget {
  final AppThemeType type;
  final bool selected;
  final VoidCallback onTap;

  const _ThemeTile({
    required this.type,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final themeData = AppTheme.getTheme(type);

    return AppCard(
      padding: EdgeInsets.zero,
      onTap: onTap,
      child: Stack(
        fit: StackFit.expand,
        children: [
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  themeData.colorScheme.primary,
                  themeData.colorScheme.secondary,
                  themeData.scaffoldBackgroundColor,
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Row(
                  children: [
                    _Swatch(color: themeData.colorScheme.primary),
                    _Swatch(color: themeData.colorScheme.secondary),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  _getThemeName(type),
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ],
            ),
          ),
          if (selected)
            const Positioned(
              right: 8,
              top: 8,
              child: Icon(Icons.check_circle, color: Colors.white),
            ),
        ],
      ),
    );
  }

  String _getThemeName(AppThemeType type) {
    switch (type) {
      case AppThemeType.bingieNeon:
        return 'Default';
      case AppThemeType.emerald:
        return 'Emerald';
      case AppThemeType.crimson:
        return 'Crimson';
      case AppThemeType.ocean:
        return 'Ocean';
      case AppThemeType.gold:
        return 'Gold';
      case AppThemeType.midnight:
        return 'Midnight';
      case AppThemeType.amoled:
        return 'AMOLED';
      default:
        return 'Custom';
    }
  }
}

class _Swatch extends StatelessWidget {
  final Color color;

  const _Swatch({required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 18,
      height: 18,
      margin: const EdgeInsets.only(right: 4),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: Colors.white24),
      ),
    );
  }
}
