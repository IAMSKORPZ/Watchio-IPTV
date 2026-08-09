import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:another_iptv_player/l10n/localization_extension.dart';
import 'package:another_iptv_player/models/playlist_model.dart';
import 'package:another_iptv_player/controllers/xtream_code_home_controller.dart';
import 'package:another_iptv_player/screens/live_stream/xtream_live_screen.dart';
import 'package:another_iptv_player/screens/settings/announcement_center_screen.dart';
import 'package:another_iptv_player/widgets/tv_focusable.dart';
import 'package:provider/provider.dart';

class XtreamCodeDashboard extends StatefulWidget {
  final Playlist playlist;
  final XtreamCodeHomeController controller;
  final VoidCallback? onSearchTap;

  const XtreamCodeDashboard({
    super.key,
    required this.playlist,
    required this.controller,
    this.onSearchTap,
  });

  @override
  State<XtreamCodeDashboard> createState() => _XtreamCodeDashboardState();
}

class _XtreamCodeDashboardState extends State<XtreamCodeDashboard> {
  late Timer _timer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
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
    return Scaffold(
      backgroundColor: Colors.black,
      body: Container(
        decoration: BoxDecoration(
          color: const Color(0xFF050812),
          image: DecorationImage(
            image: const AssetImage('assets/images/App_Background.png'),
            fit: BoxFit.cover,
            colorFilter: ColorFilter.mode(
              Colors.black.withValues(alpha: 0.6),
              BlendMode.darken,
            ),
          ),
        ),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final bool isSmallHeight = constraints.maxHeight < 450;

            return SafeArea(
              child: Column(
                children: [
                  _buildHeader(context, isSmallHeight),
                  Expanded(
                    child: SingleChildScrollView(
                      physics: const NeverScrollableScrollPhysics(),
                      child: Container(
                        height:
                            constraints.maxHeight - (isSmallHeight ? 120 : 180),
                        padding: EdgeInsets.symmetric(
                          horizontal: 40.0,
                          vertical: isSmallHeight ? 5 : 20,
                        ),
                        child: _buildMainGrid(context, isSmallHeight),
                      ),
                    ),
                  ),
                  _buildFooter(context, isSmallHeight),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, bool isSmallHeight) {
    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: 40.0,
        vertical: isSmallHeight ? 10.0 : 30.0,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // Logo Section
          Expanded(
            flex: 2,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Image.asset(
                  'assets/images/App_Logo.png',
                  height: isSmallHeight ? 35 : 55,
                  errorBuilder: (context, error, stackTrace) => Icon(
                    Icons.tv,
                    color: const Color(0xFF20D9D2),
                    size: isSmallHeight ? 30 : 40,
                  ),
                ),
              ],
            ),
          ),

          // Center Section: Time/Date
          Expanded(
            flex: 3,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  DateFormat('hh:mm a').format(_now),
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: isSmallHeight ? 18 : 28,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1.0,
                  ),
                ),
                Text(
                  DateFormat('MMM d, yyyy').format(_now),
                  style: TextStyle(
                    color: const Color(0xFFC45CFF),
                    fontSize: isSmallHeight ? 11 : 14,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
              ],
            ),
          ),

          // Actions Section
          Expanded(
            flex: 4,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                _buildHeaderAction(
                  Icons.search,
                  context.loc.search,
                  () => widget.onSearchTap?.call(),
                ),
                const SizedBox(width: 15),
                _buildHeaderAction(
                  Icons.sports_soccer_outlined,
                  '',
                  () => widget.controller.onNavigationTap(6),
                ), // Assuming 6 is Sports
                const SizedBox(width: 15),
                _buildHeaderAction(Icons.notifications_none_rounded, '', () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const AnnouncementCenterScreen(),
                    ),
                  );
                }),
                const SizedBox(width: 15),
                _buildHeaderAction(Icons.info_outline_rounded, '', () {}),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeaderAction(IconData icon, String label, VoidCallback onTap) {
    return TvFocusable(
      onPressed: onTap,
      child: Padding(
        padding: const EdgeInsets.all(8.0),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: Colors.white, size: 24),
            if (label.isNotEmpty) ...[
              const SizedBox(width: 10),
              Text(
                label.toUpperCase(),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1.2,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildMainGrid(BuildContext context, bool isSmallHeight) {
    return Column(
      children: [
        // Main Tiles Row
        Expanded(
          flex: 4,
          child: Row(
            children: [
              Expanded(
                child: _buildMainTile(
                  title: 'LIVE TV',
                  subtitle: 'Watch Live TV Channels',
                  icon: Icons.play_arrow_rounded,
                  color: const Color(0xFFFF3D9A),
                  isSmallHeight: isSmallHeight,
                  onTap: () => widget.controller.onNavigationTap(2),
                ),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: _buildMainTile(
                  title: 'MOVIES',
                  subtitle: 'Browse a wide selection',
                  icon: Icons.play_arrow_rounded,
                  color: const Color(0xFFA855F7),
                  isSmallHeight: isSmallHeight,
                  onTap: () => widget.controller.onNavigationTap(3),
                ),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: _buildMainTile(
                  title: 'SERIES',
                  subtitle: 'Discover and binge-watch',
                  icon: Icons.movie_filter_rounded,
                  color: const Color(0xFF20D9D2),
                  isSmallHeight: isSmallHeight,
                  onTap: () => widget.controller.onNavigationTap(4),
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 20),

        // Bottom Actions Row
        Expanded(
          flex: 1,
          child: Row(
            children: [
              Expanded(
                child: _buildBottomAction(
                  title: 'LIVE + EPG',
                  icon: Icons.list_alt_rounded,
                  onTap: () => widget.controller.onNavigationTap(2),
                ),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: _buildBottomAction(
                  title: 'TV GUIDE',
                  icon: Icons.live_tv_rounded,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => ChangeNotifierProvider.value(
                        value: widget.controller,
                        child: XtreamLiveScreen(
                          playlist: widget.playlist,
                          openAsGuide: true,
                          homeController: widget.controller,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: _buildBottomAction(
                  title: 'SETTINGS',
                  icon: Icons.settings_rounded,
                  onTap: () => widget.controller.onNavigationTap(5),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildMainTile({
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required bool isSmallHeight,
    required VoidCallback onTap,
  }) {
    return TvFocusable(
      onPressed: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.05),
          borderRadius: BorderRadius.circular(30),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.3),
              blurRadius: 20,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(30),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
            child: Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    const Color(0xAA4A3D6A), // Brighter purple-grey glass
                    const Color(0xAA30274F), // Brighter purple-grey glass
                  ],
                ),
                border: Border.all(
                  color: color.withValues(alpha: 0.5), // Colored accent border
                  width: 1.5,
                ),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(icon, color: color, size: isSmallHeight ? 40 : 60),
                  const SizedBox(height: 20),
                  Text(
                    title,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: isSmallHeight ? 18 : 26,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    subtitle,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white60,
                      fontSize: isSmallHeight ? 10 : 12,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBottomAction({
    required String title,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return TvFocusable(
      onPressed: onTap,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              const Color(0xAA4A3D6A), // Brighter purple-grey glass
              const Color(0xAA30274F), // Brighter purple-grey glass
            ],
          ),
          borderRadius: BorderRadius.circular(30),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(30),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              decoration: const BoxDecoration(),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(icon, color: Colors.white, size: 24),
                  const SizedBox(width: 15),
                  Text(
                    title,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.0,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildFooter(BuildContext context, bool isSmallHeight) {
    final userInfo = widget.controller.userInfo;
    String expiration = 'Lifetime';
    if (userInfo?.userInfo.expDate != null) {
      try {
        final date = DateTime.fromMillisecondsSinceEpoch(
          int.parse(userInfo!.userInfo.expDate) * 1000,
        );
        expiration = DateFormat('d MMM yyyy').format(date);
      } catch (_) {}
    }

    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: 40.0,
        vertical: isSmallHeight ? 10 : 30,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Expiration
          Row(
            children: [
              const Icon(
                Icons.verified_rounded,
                color: Color(0xFF20D9D2),
                size: 18,
              ),
              const SizedBox(width: 10),
              Text(
                'Expiration: $expiration',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),

          // Version
          const Text(
            'v0.0.1',
            style: TextStyle(
              color: Colors.white38,
              fontSize: 12,
              fontWeight: FontWeight.bold,
            ),
          ),

          // User
          Row(
            children: [
              const Icon(
                Icons.person_rounded,
                color: Color(0xFFFF3D9A),
                size: 18,
              ),
              const SizedBox(width: 10),
              Text(
                'Logged In: ${userInfo?.userInfo.username ?? "Guest"}',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
