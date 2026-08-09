import 'package:flutter/material.dart';
import '../../core/theme/theme_extensions.dart';

class GradientButton extends StatelessWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final double borderRadius;
  final LinearGradient? gradient;
  final double? width;
  final double height;
  final EdgeInsetsGeometry? padding;
  final IconData? icon;
  final FocusNode? focusNode;
  final bool autofocus;

  const GradientButton({
    super.key,
    required this.child,
    this.onPressed,
    this.borderRadius = 12.0,
    this.gradient,
    this.width,
    this.height = 50.0,
    this.padding,
    this.icon,
    this.focusNode,
    this.autofocus = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = WatchioThemeExtension.of(context);

    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        gradient: gradient ?? theme.primaryGradient,
        borderRadius: BorderRadius.circular(borderRadius),
        boxShadow: [
          BoxShadow(
            color: (gradient ?? theme.primaryGradient).colors.first.withValues(alpha: 0.3),
            blurRadius: 8,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ElevatedButton(
        onPressed: onPressed,
        focusNode: focusNode,
        autofocus: autofocus,
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.transparent,
          shadowColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(borderRadius),
          ),
          padding: padding,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (icon != null) ...[
              Icon(icon, color: Colors.white),
              const SizedBox(width: 8),
            ],
            child,
          ],
        ),
      ),
    );
  }
}
