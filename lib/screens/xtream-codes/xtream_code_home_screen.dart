import 'dart:async';

import 'package:watchio/models/api_configuration_model.dart';
import 'package:watchio/models/category_type.dart';
import 'package:watchio/repositories/iptv_repository.dart';
import 'package:watchio/services/app_state.dart';
import 'package:watchio/services/cache_metadata_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../controllers/xtream_code_home_controller.dart';
import '../../models/playlist_model.dart';
import '../../models/content_type.dart';
import '../../shared/widgets/app_shell.dart';
import '../home/watchio_dashboard_home.dart';
import '../watch_history_screen.dart';
import '../announcements/announcements_screen.dart';
import '../../l10n/localization_extension.dart';
import '../search_screen.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../movies/xtream_movies_screen.dart';
import '../series/xtream_series_screen.dart';
import '../live_stream/xtream_live_screen.dart';
import '../sports/sports_hub_screen.dart';
import '../settings/watchio_settings_screen.dart';
import '../playlist_switch_screen.dart';

class XtreamCodeHomeScreen extends StatefulWidget {
  final Playlist playlist;
  final bool refreshAfterFirstHomePaint;

  const XtreamCodeHomeScreen({
    super.key,
    required this.playlist,
    this.refreshAfterFirstHomePaint = false,
  });

  @override
  State<XtreamCodeHomeScreen> createState() => _XtreamCodeHomeScreenState();
}

class _XtreamCodeHomeScreenState extends State<XtreamCodeHomeScreen> {
  late XtreamCodeHomeController _controller;
  String _version = '0.0.1';
  bool _didRunPostLoginRefresh = false;

  @override
  void initState() {
    super.initState();
    _initializeController();
    _loadVersion();
    _schedulePostLoginRefresh();
  }

  void _initializeController() {
    final repository = IptvRepository(
      ApiConfig(
        baseUrl: widget.playlist.url ?? '',
        username: widget.playlist.username ?? '',
        password: widget.playlist.password ?? '',
      ),
      widget.playlist.id,
    );
    AppState.xtreamCodeRepository = repository;
    AppState.currentPlaylist = widget.playlist;
    _controller = XtreamCodeHomeController(false);
  }

  Future<void> _loadVersion() async {
    final info = await PackageInfo.fromPlatform();
    if (mounted) setState(() => _version = info.version);
  }

  void _schedulePostLoginRefresh() {
    if (!widget.refreshAfterFirstHomePaint || _didRunPostLoginRefresh) return;
    unawaited(_schedulePostLoginRefreshIfNeeded());
  }

  Future<void> _schedulePostLoginRefreshIfNeeded() async {
    if (!widget.refreshAfterFirstHomePaint || _didRunPostLoginRefresh) return;
    final isDone = await CacheMetadataService().isFirstPostLoginRefreshDone(
      widget.playlist.id,
    );
    if (!mounted || isDone) return;
    _didRunPostLoginRefresh = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      unawaited(_runPostLoginRefresh());
    });
  }

  Future<void> _runPostLoginRefresh() async {
    await Future<void>.delayed(const Duration(milliseconds: 350));
    if (!mounted) return;

    var allSucceeded = true;
    for (final type in const [
      CategoryType.series,
      CategoryType.vod,
      CategoryType.live,
    ]) {
      if (!mounted) return;
      final success = await _controller.refreshSection(type);
      allSucceeded = allSucceeded && success;
    }

    if (allSucceeded) {
      await CacheMetadataService().setFirstPostLoginRefreshDone(
        widget.playlist.id,
        true,
      );
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _showAnnouncements() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const WatchioAnnouncementsScreen()),
    );
  }

  void _showSportsHub() {
    // We will create this screen shortly
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const SportsHubScreen()),
    );
  }

  void _showPlaylistSwitcher() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const PlaylistSwitchScreen()),
    );
  }

  void _openTvGuide() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChangeNotifierProvider.value(
          value: _controller,
          child: XtreamLiveScreen(
            playlist: widget.playlist,
            openAsGuide: true,
            homeController: _controller,
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: _controller,
      child: Consumer<XtreamCodeHomeController>(
        builder: (context, controller, child) {
          if (controller.isLoading) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }

          final userInfo = controller.userInfo?.userInfo;

          final navItems = [
            (icon: Icons.home_rounded, label: context.loc.home),
            (icon: Icons.history_rounded, label: context.loc.history),
            (icon: Icons.live_tv_rounded, label: context.loc.live_streams),
            (icon: Icons.movie_rounded, label: context.loc.movies),
            (icon: Icons.tv_rounded, label: context.loc.series_plural),
            (icon: Icons.settings_rounded, label: context.loc.settings),
          ];

          return AppShell(
            currentIndex: controller.currentIndex,
            onIndexChanged: controller.onNavigationTap,
            navItems: navItems,
            onSearchTap: _navigateToSearch,
            onRefreshTap: _openTvGuide,
            onSettingsTap: () => controller.onNavigationTap(5),
            pages: [
              WatchioDashboardHome(
                onLiveTv: () => unawaited(
                  controller.openContentSection(2, CategoryType.live),
                ),
                onMovies: () => unawaited(
                  controller.openContentSection(3, CategoryType.vod),
                ),
                onSeries: () => unawaited(
                  controller.openContentSection(4, CategoryType.series),
                ),
                onRefreshLiveTv: () => unawaited(
                  _refreshSection(context, controller, CategoryType.live),
                ),
                onRefreshMovies: () => unawaited(
                  _refreshSection(context, controller, CategoryType.vod),
                ),
                onRefreshSeries: () => unawaited(
                  _refreshSection(context, controller, CategoryType.series),
                ),
                onAnnouncements: _showAnnouncements,
                onUpdate: _openTvGuide,
                onSettings: () => controller.onNavigationTap(5),
                onSearch: _navigateToSearch,
                onSports: _showSportsHub,
                onSwitchPlaylist: _showPlaylistSwitcher,
                onProfile: () => controller.onNavigationTap(5),
                username: userInfo?.username ?? 'Guest',
                expiryDate: userInfo?.expDate ?? 'N/A',
                version: _version,
                isLiveTvUpdating: controller.isRefreshing(CategoryType.live),
                isMoviesUpdating: controller.isRefreshing(CategoryType.vod),
                isSeriesUpdating: controller.isRefreshing(CategoryType.series),
                liveTvUpdateProgress: controller.refreshProgress(
                  CategoryType.live,
                ),
                moviesUpdateProgress: controller.refreshProgress(
                  CategoryType.vod,
                ),
                seriesUpdateProgress: controller.refreshProgress(
                  CategoryType.series,
                ),
                liveTvLastUpdatedLabel: _lastUpdatedLabel(
                  controller,
                  CategoryType.live,
                ),
                moviesLastUpdatedLabel: _lastUpdatedLabel(
                  controller,
                  CategoryType.vod,
                ),
                seriesLastUpdatedLabel: _lastUpdatedLabel(
                  controller,
                  CategoryType.series,
                ),
              ),
              WatchHistoryScreen(playlistId: widget.playlist.id),
              XtreamLiveScreen(playlist: widget.playlist),
              XtreamMoviesScreen(),
              XtreamSeriesScreen(),
              const WatchioSettingsScreen(),
            ],
          );
        },
      ),
    );
  }

  void _navigateToSearch([ContentType? contentType]) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SearchScreen(contentType: contentType),
      ),
    );
  }

  Future<void> _refreshSection(
    BuildContext context,
    XtreamCodeHomeController controller,
    CategoryType type,
  ) async {
    final success = await controller.refreshSection(type);
    if (!context.mounted) return;

    if (!success) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${_sectionLabel(type)} refresh failed.')),
      );
    }
  }

  String _lastUpdatedLabel(
    XtreamCodeHomeController controller,
    CategoryType type,
  ) {
    final updatedAt = controller.lastUpdated(type);
    final count = controller.cachedItemCount(type);
    final error = controller.lastRefreshError(type);
    final countPrefix = count > 0 ? '$count cached • ' : '';

    if (updatedAt == null) {
      return error == null
          ? '${countPrefix}Last updated: never'
          : '${countPrefix}Last update failed';
    }

    final seconds = DateTime.now().difference(updatedAt).inSeconds;
    final age = () {
      if (seconds < 5) return 'just now';
      if (seconds < 60) return '$seconds sec ago';

      final minutes = seconds ~/ 60;
      if (minutes < 60) return '$minutes min ago';

      final hours = minutes ~/ 60;
      if (hours < 24) return '$hours hr ago';

      final days = hours ~/ 24;
      return '$days day${days == 1 ? '' : 's'} ago';
    }();

    if (error != null) return '${countPrefix}Last update failed';
    final staleSuffix = controller.isCacheStale(type)
        ? ' • Update available'
        : '';
    return '${countPrefix}Last updated: $age$staleSuffix';
  }

  String _sectionLabel(CategoryType type) {
    return switch (type) {
      CategoryType.live => 'Live TV',
      CategoryType.vod => 'Movies',
      CategoryType.series => 'Series',
    };
  }
}
