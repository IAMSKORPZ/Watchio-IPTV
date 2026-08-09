import 'package:watchio/l10n/localization_extension.dart';
import 'package:watchio/screens/m3u/m3u_items_screen.dart';
import 'package:watchio/screens/m3u/m3u_playlist_settings_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:watchio/controllers/m3u_home_controller.dart';
import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/repositories/m3u_repository.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../../services/app_state.dart';
import '../home/watchio_dashboard_home.dart';
import '../sports/sports_hub_screen.dart';

class M3UHomeScreen extends StatefulWidget {
  final Playlist playlist;

  const M3UHomeScreen({super.key, required this.playlist});

  @override
  State<M3UHomeScreen> createState() => _M3UHomeScreenState();
}

class _M3UHomeScreenState extends State<M3UHomeScreen> {
  late M3UHomeController _controller;
  String _version = '0.0.1';

  @override
  void initState() {
    super.initState();
    _initializeController();
    _loadVersion();
  }

  Future<void> _loadVersion() async {
    final info = await PackageInfo.fromPlatform();
    if (mounted) setState(() => _version = info.version);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _initializeController() {
    AppState.m3uRepository = M3uRepository();
    AppState.currentPlaylist = widget.playlist;
    _controller = M3UHomeController();
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: _controller,
      child: Consumer<M3UHomeController>(
        builder: (context, controller, child) {
          if (controller.isLoading) return _buildLoadingScreen(context);

          return Scaffold(
            backgroundColor: const Color(0xFF050812),
            body: SafeArea(
              child: IndexedStack(
                index: controller.currentIndex,
                children: [
                  WatchioDashboardHome(
                    onLiveTv: () => controller.onNavigationTap(1),
                    onMovies: () => controller.onNavigationTap(1),
                    onSeries: () => controller.onNavigationTap(1),
                    onAnnouncements: () {},
                    onUpdate: () => controller.onNavigationTap(0),
                    onSettings: () => controller.onNavigationTap(2),
                    onSearch: () {},
                    onSports: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const SportsHubScreen(),
                      ),
                    ),
                    onProfile: () => controller.onNavigationTap(2),
                    username: 'M3U User',
                    expiryDate: 'Lifetime',
                    version: _version,
                  ),
                  M3uItemsScreen(m3uItems: controller.m3uItems!),
                  M3uPlaylistSettingsScreen(playlist: widget.playlist),
                ],
              ),
            ),
            bottomNavigationBar: controller.currentIndex == 0
                ? null
                : _buildBottomNavigationBar(context, controller),
          );
        },
      ),
    );
  }

  Widget _buildLoadingScreen(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(context.loc.loading_lists),
          ],
        ),
      ),
    );
  }

  BottomNavigationBar _buildBottomNavigationBar(
    BuildContext context,
    M3UHomeController controller,
  ) {
    return BottomNavigationBar(
      currentIndex: controller.currentIndex,
      onTap: controller.onNavigationTap,
      type: BottomNavigationBarType.fixed,
      items: [
        BottomNavigationBarItem(
          icon: const Icon(Icons.home),
          label: context.loc.home,
        ),
        BottomNavigationBarItem(
          icon: const Icon(Icons.all_inbox),
          label: context.loc.all,
        ),
        BottomNavigationBarItem(
          icon: const Icon(Icons.settings),
          label: context.loc.settings,
        ),
      ],
    );
  }
}
