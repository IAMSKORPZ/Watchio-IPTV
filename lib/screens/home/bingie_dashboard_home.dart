import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../services/config_service.dart';
import 'widgets/home_tile.dart';
import 'widgets/home_header.dart';
import 'widgets/home_footer.dart';
import 'widgets/home_bottom_button.dart';
import '../../../utils/responsive_helper.dart';
import '../../../utils/firestick_performance.dart';
import '../../widgets/announcement_popup_gate.dart';

class BingieDashboardHome extends StatefulWidget {
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
  final VoidCallback? onTrakt;
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

  const BingieDashboardHome({
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
    this.onTrakt,
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
  State<BingieDashboardHome> createState() => _BingieDashboardHomeState();
}

class _BingieDashboardHomeState extends State<BingieDashboardHome>
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
                    final deviceType = ResponsiveHelper.getDeviceType(context);
                    final isDesktop = deviceType == DeviceType.desktop;
                    final isTablet = deviceType == DeviceType.tablet;

                    final double width = constraints.maxWidth;
                    final double height = constraints.maxHeight;
                    final useWideLayout = width > height && width >= 600;

                    final double horizontalPadding = isDesktop
                        ? 80
                        : width * 0.05;
                    final double verticalPadding = isDesktop
                        ? 40
                        : height * 0.04;
                    final double gap = isDesktop
                        ? 80
                        : (isTablet ? 24 : width * 0.015);

                    return Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 1600),
                        child: Padding(
                          padding: EdgeInsets.symmetric(
                            horizontal: horizontalPadding,
                            vertical: verticalPadding,
                          ),
                          child: Column(
                            children: [
                              // TOP HEADER
                              HomeHeader(
                                onSearch: widget.onSearch,
                                onProfile: widget.onProfile,
                                onSports: widget.onSports,
                                onSwitchPlaylist: widget.onSwitchPlaylist,
                                onAnnouncements: widget.onAnnouncements,
                              ),

                              useWideLayout
                                  ? isDesktop
                                        ? const SizedBox(height: 150)
                                        : const Spacer(flex: 2)
                                  : const Spacer(flex: 2),

                              // MAIN CONTENT - 3 CARDS
                              useWideLayout
                                  ? Expanded(
                                      flex: 18,
                                      child: _buildWideContent(
                                        gap,
                                        verticalGap: isDesktop ? 48 : 8,
                                      ),
                                    )
                                  : Expanded(
                                      flex: 14,
                                      child: Row(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.stretch,
                                        children: [
                                          Expanded(
                                            child: HomeTile(
                                              title: 'LIVE TV',
                                              subtitle:
                                                  'Watch Live TV Channels',
                                              icon: Icons.live_tv_rounded,
                                              accentColor: const Color(
                                                0xFFFF3D9A,
                                              ),
                                              onTap: widget.onLiveTv,
                                              onRefresh: widget.onRefreshLiveTv,
                                              isUpdating:
                                                  widget.isLiveTvUpdating,
                                              updateProgress:
                                                  widget.liveTvUpdateProgress,
                                              lastUpdatedLabel:
                                                  widget.liveTvLastUpdatedLabel,
                                              autofocus: true,
                                            ),
                                          ),
                                          SizedBox(width: gap),
                                          Expanded(
                                            child: HomeTile(
                                              title: 'MOVIES',
                                              subtitle:
                                                  'Browse a wide selection',
                                              icon: Icons.play_arrow_rounded,
                                              accentColor: const Color(
                                                0xFFA855F7,
                                              ),
                                              onTap: widget.onMovies,
                                              onRefresh: widget.onRefreshMovies,
                                              isUpdating:
                                                  widget.isMoviesUpdating,
                                              updateProgress:
                                                  widget.moviesUpdateProgress,
                                              lastUpdatedLabel:
                                                  widget.moviesLastUpdatedLabel,
                                            ),
                                          ),
                                          SizedBox(width: gap),
                                          Expanded(
                                            child: HomeTile(
                                              title: 'SERIES',
                                              subtitle:
                                                  'Discover and binge-watch',
                                              icon: Icons.movie_rounded,
                                              accentColor: const Color(
                                                0xFF20D9D2,
                                              ),
                                              onTap: widget.onSeries,
                                              onRefresh: widget.onRefreshSeries,
                                              isUpdating:
                                                  widget.isSeriesUpdating,
                                              updateProgress:
                                                  widget.seriesUpdateProgress,
                                              lastUpdatedLabel:
                                                  widget.seriesLastUpdatedLabel,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),

                              SizedBox(height: useWideLayout ? 0 : 8),

                              // SECONDARY ACTION ROW
                              useWideLayout
                                  ? const SizedBox.shrink()
                                  : Expanded(
                                      flex: 4,
                                      child: Row(
                                        children: [
                                          Expanded(
                                            child: HomeBottomButton(
                                              label: 'TV GUIDE',
                                              icon: Icons.live_tv_rounded,
                                              onTap: widget.onUpdate,
                                              accentColor: const Color(
                                                0xFFA855F7,
                                              ),
                                            ),
                                          ),
                                          SizedBox(width: gap),
                                          Expanded(
                                            child: HomeBottomButton(
                                              label: 'SETTINGS',
                                              icon: Icons.settings_rounded,
                                              onTap: widget.onSettings,
                                              accentColor: const Color(
                                                0xFF20D9D2,
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),

                              const Spacer(flex: 2),

                              // BOTTOM STATUS BAR
                              HomeFooter(
                                username: widget.username,
                                expiryDate: widget.expiryDate,
                                version: widget.version,
                              ),
                              if (isDesktop) const SizedBox(height: 24),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildWideContent(double gap, {required double verticalGap}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: HomeTile(
            title: 'LIVE TV',
            subtitle: 'Watch Live TV Channels',
            icon: Icons.live_tv_rounded,
            accentColor: const Color(0xFFFF3D9A),
            onTap: widget.onLiveTv,
            onRefresh: widget.onRefreshLiveTv,
            isUpdating: widget.isLiveTvUpdating,
            updateProgress: widget.liveTvUpdateProgress,
            lastUpdatedLabel: widget.liveTvLastUpdatedLabel,
            autofocus: true,
          ),
        ),
        SizedBox(width: gap),
        Expanded(
          child: Column(
            children: [
              Expanded(
                flex: 14,
                child: HomeTile(
                  title: 'MOVIES',
                  subtitle: 'Browse a wide selection',
                  icon: Icons.play_arrow_rounded,
                  accentColor: const Color(0xFFA855F7),
                  onTap: widget.onMovies,
                  onRefresh: widget.onRefreshMovies,
                  isUpdating: widget.isMoviesUpdating,
                  updateProgress: widget.moviesUpdateProgress,
                  lastUpdatedLabel: widget.moviesLastUpdatedLabel,
                ),
              ),
              SizedBox(height: verticalGap),
              Expanded(
                flex: 4,
                child: HomeBottomButton(
                  label: 'TV GUIDE',
                  icon: Icons.live_tv_rounded,
                  onTap: widget.onUpdate,
                  accentColor: const Color(0xFFA855F7),
                ),
              ),
            ],
          ),
        ),
        SizedBox(width: gap),
        Expanded(
          child: Column(
            children: [
              Expanded(
                flex: 14,
                child: HomeTile(
                  title: 'SERIES',
                  subtitle: 'Discover and binge-watch',
                  icon: Icons.movie_rounded,
                  accentColor: const Color(0xFF20D9D2),
                  onTap: widget.onSeries,
                  onRefresh: widget.onRefreshSeries,
                  isUpdating: widget.isSeriesUpdating,
                  updateProgress: widget.seriesUpdateProgress,
                  lastUpdatedLabel: widget.seriesLastUpdatedLabel,
                ),
              ),
              SizedBox(height: verticalGap),
              Expanded(
                flex: 4,
                child: HomeBottomButton(
                  label: 'SETTINGS',
                  icon: Icons.settings_rounded,
                  onTap: widget.onSettings,
                  accentColor: const Color(0xFF20D9D2),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
