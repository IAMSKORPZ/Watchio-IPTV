import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../utils/firestick_performance.dart';
import 'watchio_focus_action.dart';

class WatchioHeader extends StatefulWidget {
  final VoidCallback onBack;
  final VoidCallback onSearch;
  final VoidCallback? onMenu;
  final VoidCallback? onRefresh;
  final VoidCallback? onRefreshEpg;
  final VoidCallback? onSettings;
  final bool isCompact;
  final double? customLogoHeight;
  final String? sectionTitle;
  final VoidCallback? onProfile;
  final VoidCallback? onSetup;
  final VoidCallback? onClearHistory;

  const WatchioHeader({
    super.key,
    required this.onBack,
    required this.onSearch,
    this.onMenu,
    this.onRefresh,
    this.onRefreshEpg,
    this.onSettings,
    this.isCompact = false,
    this.customLogoHeight,
    this.sectionTitle,
    this.onProfile,
    this.onSetup,
    this.onClearHistory,
  });

  @override
  State<WatchioHeader> createState() => _WatchioHeaderState();
}

class _WatchioHeaderState extends State<WatchioHeader> {
  late Timer _timer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (timer) {
      if (mounted) setState(() => _now = DateTime.now());
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final double verticalPadding = widget.isCompact ? 8 : 16;
    final double logoHeight = widget.customLogoHeight ?? 110;

    return Padding(
      padding: EdgeInsets.fromLTRB(
        24,
        verticalPadding,
        24,
        verticalPadding / 2,
      ),
      child: Row(
        children: [
          // LEFT: Back + Logo
          Row(
            children: [
              _HeaderIconButton(
                icon: Icons.arrow_back_rounded,
                tooltip: 'Back',
                onTap: widget.onBack,
              ),
              const SizedBox(width: 16),
              SizedBox(
                height: 60,
                width: logoHeight * 1.5,
                child: OverflowBox(
                  maxHeight: logoHeight,
                  child: Image.asset(
                    'assets/images/App_Logo.png',
                    height: logoHeight,
                    fit: BoxFit.contain,
                  ),
                ),
              ),
              if (widget.sectionTitle != null) ...[
                const SizedBox(width: 12),
                Text(
                  widget.sectionTitle!,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ],
          ),

          const Spacer(),

          // CENTER: Time & Date
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                DateFormat('hh:mm a').format(_now),
                style: TextStyle(
                  color: Colors.white,
                  fontSize: widget.isCompact ? 16 : 20,
                  fontWeight: FontWeight.w900,
                ),
              ),
              Text(
                DateFormat('MMM d, yyyy').format(_now),
                style: TextStyle(
                  color: Theme.of(context).colorScheme.primary,
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),

          const Spacer(),

          // RIGHT: Search + More
          Row(
            children: [
              _HeaderIconButton(
                icon: Icons.search_rounded,
                tooltip: 'Search',
                onTap: widget.onSearch,
              ),
              if (widget.onProfile != null) ...[
                const SizedBox(width: 12),
                _HeaderIconButton(
                  icon: Icons.person_outline_rounded,
                  tooltip: 'Profile',
                  onTap: widget.onProfile!,
                ),
              ],
              const SizedBox(width: 12),
              _HeaderMenuButton(
                onSelected: (value) {
                  switch (value) {
                    case 'setup':
                      widget.onSetup?.call();
                      break;
                    case 'refresh':
                      widget.onRefresh?.call();
                      break;
                    case 'refresh_epg':
                      widget.onRefreshEpg?.call();
                      break;
                    case 'settings':
                      (widget.onSettings ?? widget.onMenu)?.call();
                      break;
                    case 'clear_history':
                      widget.onClearHistory?.call();
                      break;
                  }
                },
                showClearHistory: widget.onClearHistory != null,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _HeaderMenuButton extends StatefulWidget {
  final ValueChanged<String> onSelected;
  final bool showClearHistory;

  const _HeaderMenuButton({
    required this.onSelected,
    required this.showClearHistory,
  });

  @override
  State<_HeaderMenuButton> createState() => _HeaderMenuButtonState();
}

class _HeaderMenuButtonState extends State<_HeaderMenuButton> {
  bool _isFocused = false;
  bool _isHovered = false;

  @override
  Widget build(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    final active = _isFocused || _isHovered;
    return Theme(
      data: Theme.of(context).copyWith(
        hoverColor: accent.withValues(alpha: 0.14),
        focusColor: accent.withValues(alpha: 0.18),
        splashColor: accent.withValues(alpha: 0.12),
      ),
      child: MouseRegion(
        onEnter: (_) => setState(() => _isHovered = true),
        onExit: (_) => setState(() => _isHovered = false),
        child: WatchioFocusAction(
          onFocusChange: (v) => setState(() => _isFocused = v),
          child: PopupMenuButton<String>(
            tooltip: 'Menu',
            offset: const Offset(0, 50),
            color: Theme.of(context).colorScheme.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(color: accent.withValues(alpha: 0.35)),
            ),
            onSelected: widget.onSelected,
            itemBuilder: (context) => [
              PopupMenuItem(
                value: 'setup',
                child: _ThemedMenuItem(
                  icon: Icons.tune_rounded,
                  label: 'Setup',
                ),
              ),
              if (widget.showClearHistory)
                const PopupMenuItem(
                  value: 'clear_history',
                  child: _ThemedMenuItem(
                    icon: Icons.delete_sweep_rounded,
                    label: 'Clear History',
                    danger: true,
                  ),
                ),
              const PopupMenuItem(
                value: 'settings',
                child: _ThemedMenuItem(
                  icon: Icons.settings_rounded,
                  label: 'Settings',
                ),
              ),
            ],
            icon: AnimatedContainer(
              duration: perfDuration(const Duration(milliseconds: 200)),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: active
                    ? accent.withValues(alpha: 0.18)
                    : Colors.white.withValues(alpha: 0.05),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: active ? accent : Colors.white10,
                  width: active ? 2 : 1,
                ),
                boxShadow: active
                    ? [
                        BoxShadow(
                          color: accent.withValues(alpha: 0.28),
                          blurRadius: 12,
                        ),
                      ]
                    : null,
              ),
              child: Icon(
                Icons.more_vert_rounded,
                color: active ? Colors.white : Colors.white70,
                size: 22,
              ),
            ),
            onOpened: () => setState(() => _isHovered = true),
            onCanceled: () => setState(() => _isHovered = false),
          ),
        ),
      ),
    );
  }
}

class _ThemedMenuItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool danger;

  const _ThemedMenuItem({
    required this.icon,
    required this.label,
    this.danger = false,
  });

  @override
  Widget build(BuildContext context) {
    final color = danger
        ? Colors.redAccent
        : Theme.of(context).colorScheme.primary;
    return Row(
      children: [
        Icon(icon, color: color, size: 20),
        const SizedBox(width: 12),
        Text(label, style: const TextStyle(color: Colors.white)),
      ],
    );
  }
}

class _HeaderIconButton extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;
  const _HeaderIconButton({
    required this.icon,
    required this.tooltip,
    required this.onTap,
  });

  @override
  State<_HeaderIconButton> createState() => _HeaderIconButtonState();
}

class _HeaderIconButtonState extends State<_HeaderIconButton> {
  bool _isFocused = false;
  bool _isHovered = false;

  @override
  Widget build(BuildContext context) {
    final active = _isFocused || _isHovered;
    final accent = Theme.of(context).colorScheme.primary;
    return Tooltip(
      message: widget.tooltip,
      child: WatchioFocusAction(
        onFocusChange: (v) => setState(() => _isFocused = v),
        onActivate: widget.onTap,
        child: InkWell(
          onTap: widget.onTap,
          onHover: (v) => setState(() => _isHovered = v),
          borderRadius: BorderRadius.circular(12),
          child: AnimatedContainer(
            duration: perfDuration(const Duration(milliseconds: 200)),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: active
                  ? accent.withValues(alpha: 0.18)
                  : Colors.white.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: active ? accent : Colors.white10,
                width: active ? 2 : 1,
              ),
              boxShadow: active
                  ? [
                      BoxShadow(
                        color: accent.withValues(alpha: 0.28),
                        blurRadius: 12,
                      ),
                    ]
                  : null,
            ),
            child: Icon(
              widget.icon,
              color: active ? Colors.white : Colors.white70,
              size: 22,
            ),
          ),
        ),
      ),
    );
  }
}
