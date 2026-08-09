import 'package:flutter/material.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/focus_wrapper.dart';
import '../../../services/app_state.dart';

class XtreamCodeDashboardV2 extends StatefulWidget {
  final Function(int) onCategoryTap;

  const XtreamCodeDashboardV2({super.key, required this.onCategoryTap});

  @override
  State<XtreamCodeDashboardV2> createState() => _XtreamCodeDashboardV2State();
}

class _XtreamCodeDashboardV2State extends State<XtreamCodeDashboardV2> {
  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final bool isLandscape = constraints.maxWidth > constraints.maxHeight;
        final double spacing = 12.0;
        final double horizontalPadding = 16.0;
        final bool isSmallHeight = constraints.maxHeight < 400;

        return Column(
          children: [
            Expanded(
              child: Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: horizontalPadding,
                  vertical: isSmallHeight ? 4 : 8,
                ),
                child: isLandscape
                    ? _buildLandscapeLayout(
                        context,
                        constraints,
                        spacing,
                        isSmallHeight,
                      )
                    : _buildPortraitLayout(context, constraints, spacing),
              ),
            ),
            _buildFooter(context, isSmallHeight),
          ],
        );
      },
    );
  }

  Widget _buildLandscapeLayout(
    BuildContext context,
    BoxConstraints constraints,
    double spacing,
    bool isSmallHeight,
  ) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Left: LIVE TV
        Expanded(
          flex: 2,
          child: _buildCategoryCard(
            context,
            title: 'LIVE TV',
            subtitle: 'Watch Live',
            icon: Icons.live_tv_rounded,
            gradient: const [Color(0xFF6A11CB), Color(0xFF2575FC)],
            badge: 'LIVE',
            onTap: () => widget.onCategoryTap(2),
            isSmallHeight: isSmallHeight,
          ),
        ),
        SizedBox(width: spacing),
        // Right Column
        Expanded(
          flex: 5,
          child: Column(
            children: [
              // Top Row: MOVIES & SERIES
              Expanded(
                flex: 2,
                child: Row(
                  children: [
                    Expanded(
                      child: _buildCategoryCard(
                        context,
                        title: 'MOVIES',
                        subtitle: 'Thousands of movies',
                        icon: Icons.play_circle_fill_rounded,
                        gradient: const [Color(0xFFFF0844), Color(0xFFFFB199)],
                        onTap: () => widget.onCategoryTap(3),
                        isSmallHeight: isSmallHeight,
                      ),
                    ),
                    SizedBox(width: spacing),
                    Expanded(
                      child: _buildCategoryCard(
                        context,
                        title: 'SERIES',
                        subtitle: 'Binge-worthy shows',
                        icon: Icons.movie_filter_rounded,
                        gradient: const [Color(0xFF0BA360), Color(0xFF3CBA92)],
                        onTap: () => widget.onCategoryTap(4),
                        isSmallHeight: isSmallHeight,
                      ),
                    ),
                  ],
                ),
              ),
              SizedBox(height: spacing),
              // Bottom Row: Small Tiles
              Expanded(
                flex: 1,
                child: Row(
                  children: [
                    Expanded(
                      child: _buildSmallTile(
                        context,
                        title: 'HISTORY',
                        icon: Icons.history,
                        iconColor: Colors.purpleAccent,
                        onTap: () => widget.onCategoryTap(1),
                        isSmallHeight: isSmallHeight,
                      ),
                    ),
                    SizedBox(width: spacing),
                    Expanded(
                      child: _buildSmallTile(
                        context,
                        title: 'MULTI',
                        icon: Icons.grid_view_rounded,
                        iconColor: Colors.cyanAccent,
                        onTap: () {},
                        isSmallHeight: isSmallHeight,
                      ),
                    ),
                    SizedBox(width: spacing),
                    Expanded(
                      child: _buildSmallTile(
                        context,
                        title: 'SETTINGS',
                        icon: Icons.settings_rounded,
                        iconColor: const Color(0xFF20D9D2),
                        onTap: () => widget.onCategoryTap(5),
                        isSmallHeight: isSmallHeight,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildPortraitLayout(
    BuildContext context,
    BoxConstraints constraints,
    double spacing,
  ) {
    return SingleChildScrollView(
      child: Column(
        children: [
          _buildCategoryCard(
            context,
            title: 'LIVE TV',
            subtitle: 'Watch Live Channels',
            icon: Icons.live_tv_rounded,
            gradient: const [Color(0xFF6A11CB), Color(0xFF2575FC)],
            badge: 'LIVE',
            onTap: () => widget.onCategoryTap(2),
            height: 180,
          ),
          SizedBox(height: spacing),
          Row(
            children: [
              Expanded(
                child: _buildCategoryCard(
                  context,
                  title: 'MOVIES',
                  subtitle: 'Browse movies',
                  icon: Icons.play_circle_fill_rounded,
                  gradient: const [Color(0xFFFF0844), Color(0xFFFFB199)],
                  onTap: () => widget.onCategoryTap(3),
                  height: 140,
                ),
              ),
              SizedBox(width: spacing),
              Expanded(
                child: _buildCategoryCard(
                  context,
                  title: 'SERIES',
                  subtitle: 'Binge series',
                  icon: Icons.movie_filter_rounded,
                  gradient: const [Color(0xFF0BA360), Color(0xFF3CBA92)],
                  onTap: () => widget.onCategoryTap(4),
                  height: 140,
                ),
              ),
            ],
          ),
          SizedBox(height: spacing),
          _buildSmallTile(
            context,
            title: 'WATCH HISTORY',
            icon: Icons.history,
            iconColor: Colors.purpleAccent,
            onTap: () => widget.onCategoryTap(1),
            height: 60,
          ),
          SizedBox(height: spacing / 2),
          _buildSmallTile(
            context,
            title: 'MULTI SCREEN',
            icon: Icons.grid_view_rounded,
            iconColor: Colors.cyanAccent,
            onTap: () {},
            height: 60,
          ),
          SizedBox(height: spacing / 2),
          _buildSmallTile(
            context,
            title: 'SETTINGS',
            icon: Icons.settings_rounded,
            iconColor: const Color(0xFF20D9D2),
            onTap: () => widget.onCategoryTap(5),
            height: 60,
          ),
        ],
      ),
    );
  }

  Widget _buildCategoryCard(
    BuildContext context, {
    required String title,
    required String subtitle,
    required IconData icon,
    required List<Color> gradient,
    String? badge,
    required VoidCallback onTap,
    double? height,
    bool isSmallHeight = false,
  }) {
    return FocusWrapper(
      onPressed: onTap,
      child: AppCard(
        height: height ?? double.infinity,
        padding: EdgeInsets.zero,
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: gradient,
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
          child: Stack(
            children: [
              if (badge != null && !isSmallHeight)
                Positioned(
                  top: 10,
                  left: 10,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 2,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      badge,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 8,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      icon,
                      color: Colors.white,
                      size: isSmallHeight ? 32 : 50,
                    ),
                    const SizedBox(height: 8),
                    FittedBox(
                      child: Text(
                        title,
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: isSmallHeight ? 16 : 22,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              if (!isSmallHeight)
                const Positioned(
                  bottom: 8,
                  right: 8,
                  child: Icon(
                    Icons.arrow_forward_ios,
                    color: Colors.white24,
                    size: 12,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSmallTile(
    BuildContext context, {
    required String title,
    required IconData icon,
    required Color iconColor,
    required VoidCallback onTap,
    double? height,
    bool isSmallHeight = false,
  }) {
    return FocusWrapper(
      onPressed: onTap,
      child: AppCard(
        height: height ?? double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: iconColor, size: isSmallHeight ? 18 : 24),
            const SizedBox(width: 8),
            Flexible(
              child: FittedBox(
                child: Text(
                  title,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: isSmallHeight ? 10 : 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFooter(BuildContext context, bool isSmallHeight) {
    final playlist = AppState.currentPlaylist;
    if (isSmallHeight) return const SizedBox.shrink();

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              const Icon(
                Icons.workspace_premium,
                color: Colors.amber,
                size: 12,
              ),
              const SizedBox(width: 4),
              const Text(
                'Exp: Lifetime',
                style: TextStyle(color: Colors.white70, fontSize: 9),
              ),
            ],
          ),
          const Text(
            'By using this app, you agree to the Terms of Service.',
            style: TextStyle(color: Colors.grey, fontSize: 8),
          ),
          Text(
            'User: ${playlist?.username ?? "Guest"}',
            style: const TextStyle(color: Colors.white70, fontSize: 9),
          ),
        ],
      ),
    );
  }
}
