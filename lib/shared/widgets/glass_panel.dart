import 'dart:ui';
import 'package:flutter/material.dart';
import '../../core/theme/theme_extensions.dart';
import '../../core/theme/theme_manager.dart';
import '../../utils/firestick_performance.dart';
import 'package:provider/provider.dart';

LinearGradient contentPanelGradientOf(BuildContext context) =>
    BingieThemeExtension.of(context).panelGradient;

class GlassPanel extends StatelessWidget {
  final Widget child;
  final double borderRadius;
  final double blur;
  final double opacity;
  final EdgeInsetsGeometry? padding;
  final BoxBorder? border;
  final Gradient? gradient;

  const GlassPanel({
    super.key,
    required this.child,
    this.borderRadius = 16.0,
    this.blur = 10.0,
    this.opacity = 0.1,
    this.padding,
    this.border,
    this.gradient,
  });

  @override
  Widget build(BuildContext context) {
    final theme = BingieThemeExtension.of(context);
    final manager = context.watch<ThemeManager>();
    final effectiveRadius = borderRadius == 16
        ? manager.tileRadius
        : borderRadius;
    final effectiveBlur = perfBlur(blur);
    final content = Container(
      padding: padding,
      decoration: BoxDecoration(
        color: gradient == null
            ? Color.alphaBlend(
                theme.panelColor.withValues(alpha: 0.38),
                theme.glassColor.withValues(alpha: opacity),
              )
            : null,
        gradient: gradient,
        borderRadius: BorderRadius.circular(effectiveRadius),
        border: border ?? Border.all(color: theme.glassBorder),
      ),
      child: Material(type: MaterialType.transparency, child: child),
    );

    return ClipRRect(
      borderRadius: BorderRadius.circular(effectiveRadius),
      child: effectiveBlur <= 0
          ? content
          : BackdropFilter(
              filter: ImageFilter.blur(
                sigmaX: effectiveBlur,
                sigmaY: effectiveBlur,
              ),
              child: content,
            ),
    );
  }
}
