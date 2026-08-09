import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/models/stalker_provider_config.dart';
import 'package:watchio/repositories/stalker_repository.dart';
import 'package:flutter/material.dart';

import 'package:watchio/screens/home/watchio_dashboard_home.dart';

class StalkerHomeScreen extends StatefulWidget {
  final Playlist playlist;
  final StalkerProviderConfig config;

  const StalkerHomeScreen({
    super.key,
    required this.playlist,
    required this.config,
  });

  @override
  State<StalkerHomeScreen> createState() => _StalkerHomeScreenState();
}

class _StalkerHomeScreenState extends State<StalkerHomeScreen> {
  late final StalkerRepository repository;
  bool isLoading = false;
  String? status;
  String? error;

  @override
  void initState() {
    super.initState();
    repository = StalkerRepository(
      providerId: widget.playlist.id,
      config: widget.config,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF050812),
      body: SafeArea(
        child: WatchioDashboardHome(
          onLiveTv: () {},
          onMovies: () {},
          onSeries: () {},
          onAnnouncements: () {},
          onUpdate: () {},
          onSettings: () {},
          onSearch: () {},
          onSports: () {},
          onProfile: () {},
          username: 'Stalker User',
          expiryDate: 'N/A',
          version: '0.0.1',
        ),
      ),
    );
  }
}
