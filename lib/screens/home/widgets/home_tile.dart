import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/theme_extensions.dart';
import '../../../core/theme/theme_manager.dart';
import '../../../shared/widgets/watchio_focus_action.dart';
import '../../../utils/firestick_performance.dart';
import '../../../utils/responsive_helper.dart';

class HomeTile extends StatefulWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color accentColor;
  final VoidCallback onTap;
  final VoidCallback? onRefresh;
  final bool isUpdating;
  final double updateProgress;
  final String? lastUpdatedLabel;
  final bool autofocus;

  const HomeTile({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.accentColor,
    required this.onTap,
    this.onRefresh,
    this.isUpdating = false,
    this.updateProgress = 0,
    this.lastUpdatedLabel,
    this.autofocus = false,
  });

  @override
  State<HomeTile> createState() => _HomeTileState();
}

class _HomeTileState extends State<HomeTile> {
  final FocusNode _tileFocusNode = FocusNode();
  bool _isTileFocused = false;
  bool _isRefreshFocused = false;

  @override
  void didUpdateWidget(covariant HomeTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isUpdating && !widget.isUpdating) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _isRefreshFocused = false;
          _tileFocusNode.requestFocus();
        }
      });
    }
  }

  @override
  void dispose() {
    _tileFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final manager = context.watch<ThemeManager>();
    final panelGradient = WatchioThemeExtension.of(context).panelGradient;
    final isAnyFocused = _isTileFocused || _isRefreshFocused;

    return AnimatedScale(
      scale: manager.animationsEnabled && isAnyFocused ? perfScale(1.05) : 1.0,
      duration: manager.animationsEnabled
          ? perfDuration(const Duration(milliseconds: 200))
          : Duration.zero,
      curve: Curves.easeOutCubic,
      child: AnimatedContainer(
        duration: perfDuration(const Duration(milliseconds: 200)),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(30),
          border: Border.all(
            color: isAnyFocused
                ? widget.accentColor
                : widget.accentColor.withValues(alpha: 0.4),
            width: 2.0,
          ),
          boxShadow: firestickPerformanceMode
              ? null
              : [
                  BoxShadow(
                    color: widget.accentColor.withValues(
                      alpha: isAnyFocused ? 0.4 : 0.08,
                    ),
                    blurRadius: isAnyFocused ? 25 : 12,
                    spreadRadius: isAnyFocused ? 2 : 0,
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
              width: double.infinity,
              height: double.infinity,
              decoration: BoxDecoration(
                gradient: panelGradient,
                borderRadius: BorderRadius.circular(30),
              ),
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final deviceType = ResponsiveHelper.getDeviceType(context);
                  final isDesktop = deviceType == DeviceType.desktop;
                  final isTablet = deviceType == DeviceType.tablet;
                  final h = constraints.maxHeight;
                  final showRefreshFooter = widget.onRefresh != null;
                  final footerHeight = showRefreshFooter
                      ? (h * 0.16).clamp(38.0, 48.0)
                      : 0.0;
                  final contentHeight = (h - footerHeight).clamp(80.0, h);

                  double iconSize = (contentHeight * 0.28).clamp(30.0, 54.0);
                  double titleSize = (contentHeight * 0.11).clamp(16.0, 24.0);
                  double subtitleSize = (contentHeight * 0.052).clamp(
                    9.0,
                    12.0,
                  );
                  double spacing = (contentHeight * 0.035).clamp(3.0, 9.0);

                  if (isDesktop) {
                    iconSize = showRefreshFooter ? 54 : 72;
                    titleSize = showRefreshFooter ? 25 : 32;
                    subtitleSize = showRefreshFooter ? 12 : 16;
                    spacing = showRefreshFooter ? 8 : 16;
                  } else if (isTablet) {
                    iconSize = showRefreshFooter ? 48 : 60;
                    titleSize = showRefreshFooter ? 22 : 26;
                    subtitleSize = showRefreshFooter ? 11 : 13;
                    spacing = showRefreshFooter ? 7 : 10;
                  }

                  final contentPadding = isDesktop ? 18.0 : 10.0;

                  return Stack(
                    children: [
                      Positioned.fill(
                        bottom: footerHeight,
                        child: WatchioFocusAction(
                          focusNode: _tileFocusNode,
                          autofocus: widget.autofocus,
                          onFocusChange: (value) =>
                              setState(() => _isTileFocused = value),
                          onActivate: widget.isUpdating ? null : widget.onTap,
                          mouseCursor: widget.isUpdating
                              ? MouseCursor.defer
                              : SystemMouseCursors.click,
                          child: GestureDetector(
                            behavior: HitTestBehavior.opaque,
                            onTap: widget.isUpdating ? null : widget.onTap,
                            child: Container(
                              alignment: Alignment.center,
                              padding: EdgeInsets.all(contentPadding),
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                mainAxisAlignment: MainAxisAlignment.center,
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  Icon(
                                    widget.icon,
                                    color: widget.accentColor,
                                    size: iconSize,
                                  ),
                                  SizedBox(height: spacing),
                                  FittedBox(
                                    fit: BoxFit.scaleDown,
                                    child: Text(
                                      widget.title,
                                      textAlign: TextAlign.center,
                                      style: GoogleFonts.outfit(
                                        color: Colors.white,
                                        fontSize: titleSize,
                                        fontWeight: FontWeight.w900,
                                        letterSpacing: 1.2,
                                      ),
                                    ),
                                  ),
                                  SizedBox(height: isDesktop ? 8 : 4),
                                  Text(
                                    widget.subtitle,
                                    textAlign: TextAlign.center,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: GoogleFonts.outfit(
                                      color: Colors.white60,
                                      fontSize: subtitleSize,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                      if (showRefreshFooter)
                        Positioned(
                          left: 0,
                          right: 0,
                          bottom: 0,
                          height: footerHeight,
                          child: _TileRefreshFooter(
                            label:
                                widget.lastUpdatedLabel ??
                                'Last updated: never',
                            accentColor: widget.accentColor,
                            onRefresh: widget.isUpdating
                                ? null
                                : widget.onRefresh,
                            onFocusChange: (value) =>
                                setState(() => _isRefreshFocused = value),
                          ),
                        ),
                      if (widget.isUpdating)
                        Positioned.fill(
                          child: _TileUpdatingOverlay(
                            icon: widget.icon,
                            title: widget.title,
                            accentColor: widget.accentColor,
                            progress: widget.updateProgress,
                          ),
                        ),
                    ],
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _TileRefreshFooter extends StatefulWidget {
  final String label;
  final Color accentColor;
  final VoidCallback? onRefresh;
  final ValueChanged<bool>? onFocusChange;

  const _TileRefreshFooter({
    required this.label,
    required this.accentColor,
    required this.onRefresh,
    this.onFocusChange,
  });

  @override
  State<_TileRefreshFooter> createState() => _TileRefreshFooterState();
}

class _TileRefreshFooterState extends State<_TileRefreshFooter> {
  bool _isRefreshFocused = false;

  void _setRefreshFocused(bool value) {
    setState(() => _isRefreshFocused = value);
    widget.onFocusChange?.call(value);
  }

  @override
  Widget build(BuildContext context) {
    final refreshEndColor = _refreshEndColor(widget.accentColor);
    final button = SizedBox(
      width: 58,
      height: 34,
      child: AnimatedScale(
        scale: _isRefreshFocused ? perfScale(1.08) : 1,
        duration: perfDuration(const Duration(milliseconds: 120)),
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                widget.accentColor.withValues(
                  alpha: widget.onRefresh == null ? 0.18 : 0.78,
                ),
                refreshEndColor.withValues(
                  alpha: widget.onRefresh == null ? 0.12 : 0.62,
                ),
              ],
            ),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: _isRefreshFocused
                  ? Colors.white
                  : Colors.white.withValues(alpha: 0.22),
              width: _isRefreshFocused ? 2 : 1.2,
            ),
            boxShadow: firestickPerformanceMode || widget.onRefresh == null
                ? null
                : [
                    BoxShadow(
                      color: widget.accentColor.withValues(
                        alpha: _isRefreshFocused ? 0.48 : 0.35,
                      ),
                      blurRadius: _isRefreshFocused ? 14 : 10,
                      spreadRadius: _isRefreshFocused ? 1.2 : 0.5,
                    ),
                  ],
          ),
          child: Material(
            type: MaterialType.transparency,
            child: InkWell(
              onTap: widget.onRefresh,
              borderRadius: BorderRadius.circular(10),
              child: Icon(
                Icons.sync_rounded,
                size: 24,
                color: Colors.white.withValues(
                  alpha: widget.onRefresh == null ? 0.35 : 0.95,
                ),
              ),
            ),
          ),
        ),
      ),
    );

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 6, 8, 6),
      decoration: BoxDecoration(
        color: const Color(0xFF111327).withValues(alpha: 0.78),
        borderRadius: const BorderRadius.vertical(bottom: Radius.circular(28)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              widget.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: GoogleFonts.outfit(
                color: Colors.white,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          WatchioFocusAction(
            onActivate: widget.onRefresh,
            onFocusChange: _setRefreshFocused,
            mouseCursor: widget.onRefresh == null
                ? MouseCursor.defer
                : SystemMouseCursors.click,
            child: button,
          ),
        ],
      ),
    );
  }

  Color _refreshEndColor(Color accentColor) {
    return switch (accentColor.toARGB32()) {
      0xFFFF3D9A => const Color(0xFFA855F7),
      0xFFA855F7 => const Color(0xFF20D9D2),
      0xFF20D9D2 => const Color(0xFF315B86),
      _ => Color.lerp(accentColor, Colors.black, 0.28)!,
    };
  }
}

class _TileUpdatingOverlay extends StatelessWidget {
  final IconData icon;
  final String title;
  final Color accentColor;
  final double progress;

  const _TileUpdatingOverlay({
    required this.icon,
    required this.title,
    required this.accentColor,
    required this.progress,
  });

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: const Color(0xFF03030A),
        borderRadius: BorderRadius.circular(28),
      ),
      child: Stack(
        children: [
          Positioned.fill(
            child: _UpdatingProgressWash(
              accentColor: accentColor,
              progress: progress,
            ),
          ),
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(28),
                gradient: LinearGradient(
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                  colors: [
                    Colors.transparent,
                    Colors.black.withValues(alpha: 0.18),
                    Colors.black.withValues(alpha: 0.36),
                  ],
                ),
              ),
            ),
          ),
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  icon,
                  color: Colors.white.withValues(alpha: 0.18),
                  size: 46,
                ),
                const SizedBox(height: 6),
                Text(
                  title,
                  style: GoogleFonts.outfit(
                    color: Colors.white.withValues(alpha: 0.22),
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white.withValues(alpha: 0.75),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      'Updating',
                      style: GoogleFonts.outfit(
                        color: Colors.white.withValues(alpha: 0.72),
                        fontSize: 18,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _UpdatingProgressWash extends StatelessWidget {
  final Color accentColor;
  final double progress;

  const _UpdatingProgressWash({
    required this.accentColor,
    required this.progress,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(28),
      child: TweenAnimationBuilder<double>(
        tween: Tween<double>(begin: 0, end: progress.clamp(0.03, 1)),
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOutCubic,
        builder: (context, value, child) {
          return FractionallySizedBox(
            alignment: Alignment.centerLeft,
            widthFactor: value,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: accentColor.withValues(alpha: 0.62),
              ),
            ),
          );
        },
      ),
    );
  }
}
