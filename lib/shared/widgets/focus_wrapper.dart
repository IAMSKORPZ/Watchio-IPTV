import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../core/theme/theme_extensions.dart';
import '../../services/input_mode_controller.dart';
import '../../utils/firestick_performance.dart';
import 'watchio_focus_action.dart';

class FocusWrapper extends StatefulWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final double scale;
  final bool showGlow;
  final bool showBorder;
  final BorderRadius borderRadius;
  final bool autofocus;
  final ValueChanged<bool>? onFocusChange;
  final Color? accentColor;

  const FocusWrapper({
    super.key,
    required this.child,
    this.onPressed,
    this.scale = 1.05,
    this.showGlow = true,
    this.showBorder = true,
    this.borderRadius = const BorderRadius.all(Radius.circular(12)),
    this.autofocus = false,
    this.onFocusChange,
    this.accentColor,
  });

  @override
  State<FocusWrapper> createState() => _FocusWrapperState();
}

class _FocusWrapperState extends State<FocusWrapper> {
  bool _isFocused = false;
  bool _isHovered = false;

  @override
  Widget build(BuildContext context) {
    final tokens = BingieThemeExtension.of(context);
    final accent = widget.accentColor ?? tokens.highlightColor;
    final glow = widget.accentColor ?? tokens.glowColor;
    final inputMode = context.watch<InputModeController>();

    final isActive = _isFocused || _isHovered;

    return Focus(
      autofocus: widget.autofocus,
      onFocusChange: (hasFocus) {
        widget.onFocusChange?.call(hasFocus);
        setState(() {
          _isFocused = hasFocus;
        });
      },
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            WatchioFocusAction.activationShortcuts.keys.any(
              (shortcut) =>
                  shortcut is SingleActivator &&
                  shortcut.trigger == event.logicalKey,
            )) {
          widget.onPressed?.call();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: MouseRegion(
        onEnter: (_) => setState(() => _isHovered = true),
        onExit: (_) => setState(() => _isHovered = false),
        child: GestureDetector(
          onTap: inputMode.allowPointerInput ? widget.onPressed : null,
          child: AnimatedScale(
            scale: isActive ? perfScale(widget.scale) : 1.0,
            duration: perfDuration(const Duration(milliseconds: 200)),
            curve: Curves.easeInOut,
            child: AnimatedContainer(
              duration: perfDuration(const Duration(milliseconds: 200)),
              decoration: BoxDecoration(
                borderRadius: widget.borderRadius,
                boxShadow: firestickPerformanceMode
                    ? null
                    : isActive && widget.showGlow
                    ? [
                        BoxShadow(
                          color: glow.withValues(alpha: 0.4),
                          blurRadius: 15,
                          spreadRadius: 2,
                        ),
                      ]
                    : [],
                border: Border.all(
                  color: widget.showBorder && isActive
                      ? accent
                      : Colors.transparent,
                  width: widget.showBorder ? 2 : 0,
                ),
              ),
              child: widget.child,
            ),
          ),
        ),
      ),
    );
  }
}
