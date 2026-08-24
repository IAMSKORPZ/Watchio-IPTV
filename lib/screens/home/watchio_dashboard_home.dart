import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../services/config_service.dart';
import 'widgets/home_tile.dart';
import 'widgets/home_header.dart';
import 'widgets/home_footer.dart';
import 'widgets/home_bottom_button.dart';
import '../../../utils/firestick_performance.dart';
import '../../widgets/announcement_popup_gate.dart';

class WatchioDashboardHome extends StatefulWidget {
  final VoidCallback onLiveTv;
  final VoidCallback onMovies;
  final VoidCallback onSeries;
  final VoidCallback? onRefreshLiveTv;
  final VoidCallback? onRefreshMovies;
  final VoidCallback? onRefreshSeries;
  final VoidCallback onAnnouncements;
  final VoidCallback onUpdate;
  final VoidCallback onSettings;
  final VoidCallback onSearch;
  final VoidCallback onSports;
  final VoidCallback? onSwitchPlaylist;
  final VoidCallback onProfile;
  final String username;
  final String expiryDate;
  final String version;
  final bool isLiveTvUpdating;
  final bool isMoviesUpdating;
  final bool isSeriesUpdating;
  final double liveTvUpdateProgress;
  final double moviesUpdateProgress;
  final double seriesUpdateProgress;
  final String liveTvLastUpdatedLabel;
  final String moviesLastUpdatedLabel;
  final String seriesLastUpdatedLabel;

  const WatchioDashboardHome({
    super.key,
    required this.onLiveTv,
    required this.onMovies,
    required this.onSeries,
    this.onRefreshLiveTv,
    this.onRefreshMovies,
    this.onRefreshSeries,
    required this.onAnnouncements,
    required this.onUpdate,
    required this.onSettings,
    required this.onSearch,
    required this.onSports,
    this.onSwitchPlaylist,
    required this.onProfile,
    required this.username,
    required this.expiryDate,
    required this.version,
    this.isLiveTvUpdating = false,
    this.isMoviesUpdating = false,
    this.isSeriesUpdating = false,
    this.liveTvUpdateProgress = 0,
    this.moviesUpdateProgress = 0,
    this.seriesUpdateProgress = 0,
    this.liveTvLastUpdatedLabel = 'Last updated: never',
    this.moviesLastUpdatedLabel = 'Last updated: never',
    this.seriesLastUpdatedLabel = 'Last updated: never',
  });

  @override
  State<WatchioDashboardHome> createState() => _WatchioDashboardHomeState();
}

class _WatchioDashboardHomeState extends State<WatchioDashboardHome>
    with SingleTickerProviderStateMixin {
  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeIn,
    );
    _fadeController.forward();
  }

  @override
  void dispose() {
    _fadeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Reinforce fullscreen mode
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);

    final config = context.watch<ConfigService>().config;
    final homeBg = config.backgrounds.home;
    final screenSize = MediaQuery.of(context).size;
    final isAnyTileUpdating =
        widget.isLiveTvUpdating ||
        widget.isMoviesUpdating ||
        widget.isSeriesUpdating;

    return AnnouncementPopupGate(
      child: FadeTransition(
        opacity: _fadeAnimation,
        child: Stack(
          children: [
            // BACKGROUND LAYER (Always full window size)
            Positioned.fill(
              child: Container(
                width: screenSize.width,
                height: screenSize.height,
                decoration: BoxDecoration(
                  color: const Color(0xFF050812),
                  image: DecorationImage(
                    image: (homeBg.isNotEmpty)
                        ? perfNetworkImage(homeBg)
                        : const AssetImage('assets/images/background.png')
                              as ImageProvider,
                    fit: BoxFit.cover,
                  ),
                ),
                child: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        const Color(0xFF050812).withValues(alpha: 0.2),
                        const Color(0xFF050812).withValues(alpha: 0.6),
                        const Color(0xFF050812).withValues(alpha: 0.9),
                      ],
                    ),
                  ),
                ),
              ),
            ),

            // CONTENT LAYER (Centered & Constrained to 1600px)
            FocusScope(
              canRequestFocus: !isAnyTileUpdating,
              descendantsAreFocusable: !isAnyTileUpdating,
              descendantsAreTraversable: !isAnyTileUpdating,
              child: AbsorbPointer(
                absorbing: isAnyTileUpdating,
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final double width = constraints.maxWidth;
                    final double height = constraints.maxHeight;
                    // Breakpoint rule: true phones keep the compact layout;
                    // tablets, desktop and TV share the restored dashboard:
                    // Live TV anchor left, Movies/Series right, two actions.
                    final isWide = width >= 600;
                    return isWide
                        ? _buildAdaptiveWideDashboard(width, height)
                        : _buildMobileDashboard(width, height);
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAdaptiveWideDashboard(double width, double height) {
    final safe = MediaQuery.paddingOf(context);
    final availableWidth = width - safe.left - safe.right;
    final availableHeight = height - safe.top - safe.bottom;
    final horizontalPadding = (availableWidth * 0.04).clamp(18.0, 64.0);
    final verticalPadding = (availableHeight * 0.045).clamp(16.0, 42.0);
    final gap = (availableWidth * 0.018).clamp(14.0, 30.0);
    final contentWidth = (availableWidth - horizontalPadding * 2).clamp(
      720.0,
      1760.0,
    );
    final headerHeight = (availableHeight * 0.12).clamp(64.0, 92.0);
    final gridMaxHeight = (availableHeight * 0.72).clamp(420.0, 760.0);

    return Center(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: contentWidth),
        child: Padding(
          padding: EdgeInsets.only(
            top: verticalPadding,
            bottom: verticalPadding,
          ),
          child: Column(
            children: [
              // Wide screens use real viewport width, not FittedBox/mobile
              // aspect scaling. Header/footer therefore spread to screen edges.
              SizedBox(height: headerHeight, child: _buildHeader()),
              SizedBox(height: gap),
              Expanded(
                child: Center(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(maxHeight: gridMaxHeight),
                    child: _buildWideGrid(gap),
                  ),
                ),
              ),
              SizedBox(height: gap),
              HomeFooter(
                username: widget.username,
                expiryDate: widget.expiryDate,
                version: widget.version,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMobileDashboard(double width, double height) {
    final gap = (width * 0.025).clamp(8.0, 14.0);
    return Center(
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: width * 0.05,
          vertical: height * 0.04,
        ),
        child: Column(
          children: [
            _buildHeader(),
            const Spacer(flex: 2),
            Expanded(flex: 14, child: _buildCompactContent(gap)),
            SizedBox(height: gap),
            Expanded(flex: 4, child: _buildSecondaryActions(gap)),
            const Spacer(flex: 2),
            HomeFooter(
              username: widget.username,
              expiryDate: widget.expiryDate,
              version: widget.version,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return HomeHeader(
      onSearch: widget.onSearch,
      onProfile: widget.onProfile,
      onSports: widget.onSports,
      onSwitchPlaylist: widget.onSwitchPlaylist,
      onAnnouncements: widget.onAnnouncements,
    );
  }

  Widget _buildWideGrid(double gap) {
    // D-pad focus stays inside HomeTile/HomeBottomButton. This grid only
    // controls responsive geometry, so focus bounds remain real widget bounds.
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          flex: 11,
          // Wide breakpoint: Live TV stays the tall anchor tile on the left.
          // Right side splits 3:1 so bottom actions stay chunky, not squashed.
          child: _buildLiveTile(autofocus: true),
        ),
        SizedBox(width: gap),
        Expanded(
          flex: 20,
          child: Column(
            children: [
              Expanded(
                flex: 3,
                child: Row(
                  children: [
                    Expanded(child: _buildMoviesTile()),
                    SizedBox(width: gap),
                    Expanded(child: _buildSeriesTile()),
                  ],
                ),
              ),
              SizedBox(height: gap),
              Expanded(flex: 1, child: _buildSecondaryActions(gap)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildCompactContent(double gap) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(child: _buildLiveTile(autofocus: true)),
        SizedBox(width: gap),
        Expanded(child: _buildMoviesTile()),
        SizedBox(width: gap),
        Expanded(child: _buildSeriesTile()),
      ],
    );
  }

  Widget _buildSecondaryActions(double gap) {
    return Row(
      children: [
        Expanded(
          child: HomeBottomButton(
            label: 'TV GUIDE',
            icon: Icons.live_tv_rounded,
            onTap: widget.onUpdate,
            accentColor: const Color(0xFFA855F7),
          ),
        ),
        SizedBox(width: gap),
        Expanded(
          child: HomeBottomButton(
            label: 'SETTINGS',
            icon: Icons.settings_rounded,
            onTap: widget.onSettings,
            accentColor: const Color(0xFF20D9D2),
          ),
        ),
      ],
    );
  }

  Widget _buildLiveTile({bool autofocus = false}) {
    return HomeTile(
      title: 'LIVE TV',
      subtitle: 'Watch Live TV Channels',
      icon: Icons.live_tv_rounded,
      accentColor: const Color(0xFFFF3D9A),
      onTap: widget.onLiveTv,
      onRefresh: widget.onRefreshLiveTv,
      isUpdating: widget.isLiveTvUpdating,
      updateProgress: widget.liveTvUpdateProgress,
      lastUpdatedLabel: widget.liveTvLastUpdatedLabel,
      autofocus: autofocus,
    );
  }

  Widget _buildMoviesTile() {
    return HomeTile(
      title: 'MOVIES',
      subtitle: 'Browse a wide selection',
      icon: Icons.play_arrow_rounded,
      accentColor: const Color(0xFFA855F7),
      onTap: widget.onMovies,
      onRefresh: widget.onRefreshMovies,
      isUpdating: widget.isMoviesUpdating,
      updateProgress: widget.moviesUpdateProgress,
      lastUpdatedLabel: widget.moviesLastUpdatedLabel,
    );
  }

  Widget _buildSeriesTile() {
    return HomeTile(
      title: 'SERIES',
      subtitle: 'Discover and binge-watch',
      icon: Icons.movie_rounded,
      accentColor: const Color(0xFF20D9D2),
      onTap: widget.onSeries,
      onRefresh: widget.onRefreshSeries,
      isUpdating: widget.isSeriesUpdating,
      updateProgress: widget.seriesUpdateProgress,
      lastUpdatedLabel: widget.seriesLastUpdatedLabel,
    );
  }
}
