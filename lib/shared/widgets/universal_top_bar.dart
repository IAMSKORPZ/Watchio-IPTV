import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'focus_wrapper.dart';

class UniversalTopBar extends StatefulWidget implements PreferredSizeWidget {
  final VoidCallback? onSearchTap;
  final VoidCallback? onProfileTap;
  final VoidCallback? onRefreshTap;
  final VoidCallback? onSettingsTap;
  final String? title;

  const UniversalTopBar({
    super.key,
    this.onSearchTap,
    this.onProfileTap,
    this.onRefreshTap,
    this.onSettingsTap,
    this.title,
  });

  @override
  State<UniversalTopBar> createState() => _UniversalTopBarState();

  @override
  Size get preferredSize => const Size.fromHeight(50.0);
}

class _UniversalTopBarState extends State<UniversalTopBar> {
  late Timer _timer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (timer) {
      if (mounted) {
        setState(() {
          _now = DateTime.now();
        });
      }
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final bool isSmallHeight = MediaQuery.of(context).size.height < 500;
        final double barHeight = isSmallHeight ? 45 : 55;

        return Container(
          height: barHeight,
          padding: EdgeInsets.symmetric(horizontal: isSmallHeight ? 16 : 24.0),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: Colors.white.withValues(alpha: 0.05),
                width: 1,
              ),
            ),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Left: Logo or Title
              Expanded(
                flex: 4,
                child: widget.title != null
                    ? Text(
                        widget.title!,
                        style: TextStyle(
                          fontSize: isSmallHeight ? 16 : 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      )
                    : _buildLogo(context, isSmallHeight),
              ),

              // Center: Time
              if (constraints.maxWidth > 700)
                Expanded(
                  flex: 3,
                  child: Center(
                    child: Text(
                      DateFormat('hh:mm a').format(_now),
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: isSmallHeight ? 14 : 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),

              // Right: Actions
              Expanded(
                flex: 5,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    if (widget.onSearchTap != null)
                      _buildAction(
                        Icons.search,
                        isSmallHeight,
                        widget.onSearchTap,
                      ),
                    if (widget.onSearchTap != null)
                      SizedBox(width: isSmallHeight ? 8 : 12),
                    if (widget.onProfileTap != null)
                      _buildAction(
                        Icons.person_outline,
                        isSmallHeight,
                        widget.onProfileTap,
                      ),
                    if (widget.onProfileTap != null)
                      SizedBox(width: isSmallHeight ? 8 : 12),
                    if (widget.onRefreshTap != null)
                      _buildAction(
                        Icons.refresh,
                        isSmallHeight,
                        widget.onRefreshTap,
                      ),
                    if (widget.onRefreshTap != null)
                      SizedBox(width: isSmallHeight ? 8 : 12),
                    if (widget.onSettingsTap != null)
                      _buildAction(
                        Icons.settings_outlined,
                        isSmallHeight,
                        widget.onSettingsTap,
                      ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildLogo(BuildContext context, bool isSmallHeight) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          Icons.play_arrow_rounded,
          color: Colors.blue,
          size: isSmallHeight ? 18 : 22,
        ),
        const SizedBox(width: 8),
        Flexible(
          child: FittedBox(
            fit: BoxFit.scaleDown,
            child: Row(
              children: [
                Text(
                  'BINGIE',
                  style: TextStyle(
                    fontSize: isSmallHeight ? 14 : 18,
                    fontWeight: FontWeight.w900,
                    color: Colors.white,
                    letterSpacing: 1.1,
                  ),
                ),
                Text(
                  'TV',
                  style: TextStyle(
                    fontSize: isSmallHeight ? 14 : 18,
                    fontWeight: FontWeight.w900,
                    color: Theme.of(context).primaryColor,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAction(IconData icon, bool isSmallHeight, VoidCallback? onTap) {
    return FocusWrapper(
      onPressed: onTap,
      borderRadius: BorderRadius.circular(6),
      scale: 1.1,
      child: Container(
        padding: EdgeInsets.all(isSmallHeight ? 6 : 8),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.05),
          borderRadius: BorderRadius.circular(isSmallHeight ? 6 : 8),
        ),
        child: Icon(icon, color: Colors.white70, size: isSmallHeight ? 16 : 18),
      ),
    );
  }
}
