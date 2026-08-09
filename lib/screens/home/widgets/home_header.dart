import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../shared/widgets/watchio_focus_action.dart';
import '../../../utils/responsive_helper.dart';

class HomeHeader extends StatelessWidget {
  final VoidCallback onSearch;
  final VoidCallback onProfile;
  final VoidCallback onSports;
  final VoidCallback? onSwitchPlaylist;
  final VoidCallback? onAnnouncements;

  const HomeHeader({
    super.key,
    required this.onSearch,
    required this.onProfile,
    required this.onSports,
    this.onSwitchPlaylist,
    this.onAnnouncements,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final deviceType = ResponsiveHelper.getDeviceType(context);
    final isDesktop = deviceType == DeviceType.desktop;
    final isTablet = deviceType == DeviceType.tablet;

    const double logoHeight = 110;
    double timeFontSize = isDesktop ? 44 : (isTablet ? 28 : 22);
    double dateFontSize = isDesktop ? 18 : (isTablet ? 14 : 12);
    double iconSize = isDesktop ? 36 : (isTablet ? 28 : 24);

    return Row(
      children: [
        // LEFT: Logo
        SizedBox(
          height: 60,
          width: logoHeight * 1.5,
          child: OverflowBox(
            maxHeight: logoHeight,
            child: Image.asset(
              'assets/images/App_Logo.png',
              height: logoHeight,
              fit: BoxFit.contain,
              errorBuilder: (context, error, stackTrace) => Icon(
                Icons.play_arrow_rounded,
                color: const Color(0xFF20D9D2),
                size: logoHeight * 0.7,
              ),
            ),
          ),
        ),

        const Spacer(),

        // CENTER: Time & Date
        Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Text(
              DateFormat('hh:mm a').format(now),
              style: TextStyle(
                color: Colors.white,
                fontSize: timeFontSize,
                fontWeight: FontWeight.w900,
                letterSpacing: 0.5,
              ),
            ),
            Text(
              DateFormat('MMM d, yyyy').format(now),
              style: TextStyle(
                color: const Color(0xFFC45CFF),
                fontSize: dateFontSize,
                fontWeight: FontWeight.bold,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),

        const Spacer(),

        // RIGHT: Floating Navigation Icons
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Search Bar Area
            _ToolbarItem(
              onTap: onSearch,
              child: Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: isDesktop ? 20 : 12,
                  vertical: isDesktop ? 12 : 8,
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.travel_explore_rounded,
                      color: Colors.white,
                      size: iconSize,
                    ),
                    SizedBox(width: isDesktop ? 14 : 10),
                    Text(
                      'SEARCH',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: isDesktop ? 20 : 16,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.0,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(width: isDesktop ? 12 : 8),
            _ToolbarItem(
              label: 'SPORTS',
              icon: Icons.sports_soccer_rounded,
              iconSize: iconSize,
              onTap: onSports,
            ),
            _ToolbarItem(
              label: 'ANNOUNCEMENTS',
              icon: Icons.notifications_rounded,
              iconSize: iconSize,
              onTap: onAnnouncements ?? () {},
            ),
            if (onSwitchPlaylist != null)
              _ToolbarItem(
                label: 'SWITCH PLAYLIST',
                icon: Icons.switch_account_rounded,
                iconSize: iconSize,
                onTap: onSwitchPlaylist!,
              ),
          ],
        ),
      ],
    );
  }
}

class HeaderButton extends StatefulWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool hideLabel;

  const HeaderButton({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
    this.hideLabel = false,
  });

  @override
  State<HeaderButton> createState() => _HeaderButtonState();
}

class _HeaderButtonState extends State<HeaderButton> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    return WatchioFocusAction(
      onFocusChange: (val) => setState(() => _isFocused = val),
      onActivate: widget.onTap,
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(16),
        child: AnimatedScale(
          scale: _isFocused ? 1.05 : 1.0,
          duration: const Duration(milliseconds: 200),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: EdgeInsets.symmetric(
              horizontal: widget.hideLabel ? 12 : 18,
              vertical: 10,
            ),
            decoration: BoxDecoration(
              color: _isFocused
                  ? Colors.white.withValues(alpha: 0.15)
                  : Colors.white.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: _isFocused
                    ? const Color(0xFFFFFFFF)
                    : Colors.white.withValues(alpha: 0.1),
                width: _isFocused ? 2.5 : 1.0,
              ),
              boxShadow: _isFocused
                  ? [
                      BoxShadow(
                        color: const Color(0xFFD95CFF).withValues(alpha: 0.3),
                        blurRadius: 15,
                      ),
                    ]
                  : [],
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  widget.icon,
                  color: _isFocused ? Colors.white : Colors.white70,
                  size: 20,
                ),
                if (!widget.hideLabel) ...[
                  const SizedBox(width: 10),
                  Text(
                    widget.label,
                    style: TextStyle(
                      color: _isFocused ? Colors.white : Colors.white70,
                      fontWeight: FontWeight.w900,
                      fontSize: 13,
                      letterSpacing: 0.5,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ToolbarItem extends StatefulWidget {
  final IconData? icon;
  final String? label;
  final Widget? child;
  final VoidCallback onTap;
  final double iconSize;

  const _ToolbarItem({
    this.icon,
    this.label,
    this.child,
    required this.onTap,
    this.iconSize = 22,
  });

  @override
  State<_ToolbarItem> createState() => _ToolbarItemState();
}

class _ToolbarItemState extends State<_ToolbarItem> {
  bool _isFocused = false;
  bool _isHovered = false;

  @override
  Widget build(BuildContext context) {
    final showLabel = widget.label != null && (_isFocused || _isHovered);

    return Padding(
      padding: EdgeInsets.symmetric(horizontal: widget.child != null ? 8 : 3),
      child: WatchioFocusAction(
        onFocusChange: (val) => setState(() => _isFocused = val),
        onActivate: widget.onTap,
        child: InkWell(
          onTap: widget.onTap,
          onHover: (val) => setState(() => _isHovered = val),
          borderRadius: BorderRadius.circular(30),
          child: widget.child != null
              ? AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  padding: EdgeInsets.zero,
                  decoration: BoxDecoration(
                    color: _isFocused
                        ? Colors.white.withValues(alpha: 0.15)
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(30),
                    boxShadow: _isFocused
                        ? [
                            BoxShadow(
                              color: const Color(
                                0xFFD95CFF,
                              ).withValues(alpha: 0.3),
                              blurRadius: 10,
                            ),
                          ]
                        : [],
                  ),
                  child: widget.child,
                )
              : SizedBox(
                  width: widget.iconSize + 20,
                  height: 58,
                  child: Stack(
                    clipBehavior: Clip.none,
                    alignment: Alignment.topCenter,
                    children: [
                      Positioned(
                        top: 0,
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 160),
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: showLabel
                                ? Colors.white.withValues(alpha: 0.15)
                                : Colors.transparent,
                            boxShadow: showLabel
                                ? [
                                    BoxShadow(
                                      color: const Color(
                                        0xFFD95CFF,
                                      ).withValues(alpha: 0.3),
                                      blurRadius: 10,
                                    ),
                                  ]
                                : [],
                          ),
                          child: Icon(
                            widget.icon,
                            color: showLabel ? Colors.white : Colors.white70,
                            size: widget.iconSize,
                          ),
                        ),
                      ),
                      Positioned(
                        top: widget.iconSize + 18,
                        child: IgnorePointer(
                          child: AnimatedOpacity(
                            opacity: showLabel ? 1 : 0,
                            duration: const Duration(milliseconds: 120),
                            child: SizedBox(
                              width: 112,
                              child: Text(
                                widget.label ?? '',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 8,
                                  fontWeight: FontWeight.w900,
                                  letterSpacing: 0.4,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
        ),
      ),
    );
  }
}
