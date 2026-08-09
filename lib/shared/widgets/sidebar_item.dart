import 'package:flutter/material.dart';
import 'focus_wrapper.dart';

class SidebarItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final ValueChanged<bool>? onFocusChange;
  final bool isCollapsed;
  final int? count;
  final Color? accentColor;

  const SidebarItem({
    super.key,
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
    this.onFocusChange,
    this.isCollapsed = false,
    this.count,
    this.accentColor,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final accent = accentColor ?? colorScheme.primary;

    return FocusWrapper(
      onPressed: onTap,
      onFocusChange: onFocusChange,
      scale: 1.02,
      showGlow: false,
      accentColor: accent,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: selected ? accent.withValues(alpha: 0.15) : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
          border: selected
              ? Border.all(color: accent.withValues(alpha: 0.5), width: 1)
              : null,
        ),
        child: Row(
          mainAxisSize: isCollapsed ? MainAxisSize.min : MainAxisSize.max,
          children: [
            Icon(
              icon,
              color: selected
                  ? accent
                  : colorScheme.onSurface.withValues(alpha: 0.6),
              size: 24,
            ),
            if (!isCollapsed) ...[
              const SizedBox(width: 16),
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(
                    color: selected
                        ? Colors.white
                        : colorScheme.onSurface.withValues(alpha: 0.6),
                    fontSize: 16,
                    fontWeight: selected ? FontWeight.bold : FontWeight.normal,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (count != null) ...[
                const SizedBox(width: 8),
                Text(
                  '$count',
                  style: TextStyle(
                    color: selected
                        ? Colors.white.withValues(alpha: 0.5)
                        : colorScheme.onSurface.withValues(alpha: 0.3),
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}
