import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../../utils/responsive_helper.dart';
import '../../../utils/firestick_performance.dart';
import '../../../core/theme/theme_extensions.dart';

class HomeBottomButton extends StatefulWidget {
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final Color accentColor;

  const HomeBottomButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onTap,
    this.accentColor = const Color(0xFFFF3D9A),
  });

  @override
  State<HomeBottomButton> createState() => _HomeBottomButtonState();
}

class _HomeBottomButtonState extends State<HomeBottomButton> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    final panelGradient = WatchioThemeExtension.of(context).panelGradient;
    final deviceType = ResponsiveHelper.getDeviceType(context);
    final isDesktop = deviceType == DeviceType.desktop;

    return FocusableActionDetector(
      onFocusChange: (val) => setState(() => _isFocused = val),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: AnimatedScale(
        scale: _isFocused ? perfScale(1.05) : 1.0,
        duration: perfDuration(const Duration(milliseconds: 200)),
        curve: Curves.easeOutCubic,
        child: InkWell(
          onTap: widget.onTap,
          borderRadius: BorderRadius.circular(20),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final fontSize = isDesktop
                  ? 22.0
                  : (constraints.maxHeight * 0.22).clamp(16.0, 24.0);
              final iconSize = isDesktop
                  ? 28.0
                  : (constraints.maxHeight * 0.28).clamp(22.0, 30.0);

              return AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                width: double.infinity,
                height: double.infinity,
                decoration: BoxDecoration(
                  gradient: panelGradient,
                  borderRadius: BorderRadius.circular(isDesktop ? 24 : 30),
                  border: Border.all(
                    color: _isFocused
                        ? widget.accentColor
                        : Colors
                              .transparent, // No colored borders in normal state
                    width: 2.0,
                  ),
                  boxShadow: firestickPerformanceMode
                      ? null
                      : _isFocused
                      ? [
                          BoxShadow(
                            color: widget.accentColor.withValues(alpha: 0.4),
                            blurRadius: 15,
                            spreadRadius: 2,
                          ),
                        ]
                      : [],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(20),
                  child: BackdropFilter(
                    filter: ImageFilter.blur(
                      sigmaX: perfBlur(10),
                      sigmaY: perfBlur(10),
                    ),
                    child: Container(
                      // Ensure inner container also expands
                      width: double.infinity,
                      height: double.infinity,
                      alignment: Alignment.center,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        mainAxisSize: MainAxisSize.min, // Keep content together
                        children: [
                          Icon(
                            widget.icon,
                            color: _isFocused
                                ? widget.accentColor
                                : Colors.white,
                            size: iconSize,
                          ),
                          const SizedBox(width: 12),
                          Flexible(
                            child: Text(
                              widget.label,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              textAlign: TextAlign.center,
                              style: GoogleFonts.outfit(
                                color: Colors.white,
                                fontSize: fontSize,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 1.2,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}
