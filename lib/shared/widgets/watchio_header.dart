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
                style: const TextStyle(
                  color: Color(0xFFC12CFF),
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
                onTap: widget.onSearch,
              ),
              if (widget.onProfile != null) ...[
                const SizedBox(width: 12),
                _HeaderIconButton(
                  icon: Icons.person_outline_rounded,
                  onTap: widget.onProfile!,
                ),
              ],
              const SizedBox(width: 12),
              _buildMenu(context),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMenu(BuildContext context) {
    return Theme(
      data: Theme.of(
        context,
      ).copyWith(hoverColor: Colors.white.withValues(alpha: 0.1)),
      child: PopupMenuButton<String>(
        icon: const _HeaderIconContainer(icon: Icons.more_vert_rounded),
        offset: const Offset(0, 50),
        color: const Color(0xFF1A1D29),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: Colors.white10),
        ),
        onSelected: (value) {
          switch (value) {
            case 'setup':
              widget.onSetup?.call();
              break;
            case 'refresh':
              widget.onRefresh?.call();
              break;
            case 'refresh_epg':
              if (Navigator.of(context).canPop()) {
                // Not the best way but if we are in live screen it works
              }
              // We'll pass this via a new callback
              if (widget.onRefreshEpg != null) {
                widget.onRefreshEpg!();
              }
              break;
            case 'settings':
              (widget.onSettings ?? widget.onMenu)?.call();
              break;
            case 'clear_history':
              widget.onClearHistory?.call();
              break;
          }
        },
        itemBuilder: (context) => [
          const PopupMenuItem(
            value: 'setup',
            child: Row(
              children: [
                Icon(Icons.tune_rounded, color: Colors.white70, size: 20),
                SizedBox(width: 12),
                Text('Setup', style: TextStyle(color: Colors.white)),
              ],
            ),
          ),
          if (widget.onClearHistory != null)
            const PopupMenuItem(
              value: 'clear_history',
              child: Row(
                children: [
                  Icon(
                    Icons.delete_sweep_rounded,
                    color: Colors.redAccent,
                    size: 20,
                  ),
                  SizedBox(width: 12),
                  Text('Clear History', style: TextStyle(color: Colors.white)),
                ],
              ),
            ),
          const PopupMenuItem(
            value: 'settings',
            child: Row(
              children: [
                Icon(Icons.settings_rounded, color: Colors.white70, size: 20),
                SizedBox(width: 12),
                Text('Settings', style: TextStyle(color: Colors.white)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HeaderIconContainer extends StatelessWidget {
  final IconData icon;

  const _HeaderIconContainer({required this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Icon(icon, color: Colors.white70, size: 22),
    );
  }
}

class _HeaderIconButton extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _HeaderIconButton({required this.icon, required this.onTap});

  @override
  State<_HeaderIconButton> createState() => _HeaderIconButtonState();
}

class _HeaderIconButtonState extends State<_HeaderIconButton> {
  bool _isFocused = false;
  @override
  Widget build(BuildContext context) {
    return WatchioFocusAction(
      onFocusChange: (v) => setState(() => _isFocused = v),
      onActivate: widget.onTap,
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: perfDuration(const Duration(milliseconds: 200)),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: _isFocused
                ? Colors.white.withValues(alpha: 0.2)
                : Colors.white.withValues(alpha: 0.05),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: _isFocused ? const Color(0xFFC12CFF) : Colors.white10,
            ),
          ),
          child: Icon(
            widget.icon,
            color: _isFocused ? Colors.white : Colors.white70,
            size: 22,
          ),
        ),
      ),
    );
  }
}
