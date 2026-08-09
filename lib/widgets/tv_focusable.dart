import 'package:flutter/material.dart';
import 'package:watchio/core/theme/theme_extensions.dart';
import 'package:watchio/shared/widgets/watchio_focus_action.dart';
import 'package:provider/provider.dart';
import '../services/input_mode_controller.dart';
import '../utils/firestick_performance.dart';

class TvFocusable extends StatefulWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final BorderRadius borderRadius;
  final EdgeInsets margin;
  final bool autofocus;

  const TvFocusable({
    super.key,
    required this.child,
    this.onPressed,
    this.borderRadius = const BorderRadius.all(Radius.circular(12)),
    this.margin = EdgeInsets.zero,
    this.autofocus = false,
  });

  @override
  State<TvFocusable> createState() => _TvFocusableState();
}

class _TvFocusableState extends State<TvFocusable> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final tokens = WatchioThemeExtension.of(context);
    final inputMode = context.watch<InputModeController>();

    return Padding(
      padding: widget.margin,
      child: GestureDetector(
        onTap: inputMode.allowPointerInput ? widget.onPressed : null,
        child: WatchioFocusAction(
          autofocus: widget.autofocus,
          mouseCursor: widget.onPressed == null
              ? MouseCursor.defer
              : SystemMouseCursors.click,
          onActivate: widget.onPressed,
          onFocusChange: (focused) => setState(() => _focused = focused),
          child: AnimatedScale(
            scale: _focused ? perfScale(1.045) : 1,
            duration: perfDuration(const Duration(milliseconds: 120)),
            curve: Curves.easeOut,
            child: AnimatedContainer(
              duration: perfDuration(const Duration(milliseconds: 120)),
              curve: Curves.easeOut,
              decoration: BoxDecoration(
                borderRadius: widget.borderRadius,
                border: Border.all(
                  color: _focused ? tokens.highlightColor : Colors.transparent,
                  width: 2,
                ),
                boxShadow: firestickPerformanceMode
                    ? null
                    : _focused
                    ? [
                        BoxShadow(
                          color: tokens.glowColor.withValues(alpha: 0.35),
                          blurRadius: 14,
                          spreadRadius: 1,
                        ),
                      ]
                    : null,
              ),
              child: widget.child,
            ),
          ),
        ),
      ),
    );
  }
}
