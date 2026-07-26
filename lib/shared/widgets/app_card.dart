import 'package:flutter/material.dart';
import 'focus_wrapper.dart';
import 'glass_panel.dart';

class AppCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final double borderRadius;
  final bool isGlass;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double? width;
  final double? height;
  final Color? color;

  const AppCard({
    super.key,
    required this.child,
    this.onTap,
    this.borderRadius = 16.0,
    this.isGlass = true,
    this.padding,
    this.margin,
    this.width,
    this.height,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    Widget content = Container(
      width: width,
      height: height,
      margin: margin,
      padding: padding ?? const EdgeInsets.all(16),
      decoration: color != null
          ? BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(borderRadius),
            )
          : null,
      child: child,
    );

    if (isGlass) {
      content = GlassPanel(borderRadius: borderRadius, child: content);
    } else {
      content = Card(
        color: color,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(borderRadius),
        ),
        child: content,
      );
    }

    if (onTap != null) {
      return FocusWrapper(
        onPressed: onTap,
        borderRadius: BorderRadius.circular(borderRadius),
        child: content,
      );
    }

    return content;
  }
}
