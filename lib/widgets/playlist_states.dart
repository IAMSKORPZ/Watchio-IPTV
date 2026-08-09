import 'package:watchio/l10n/localization_extension.dart';
import 'package:watchio/services/config_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class PlaylistLoadingState extends StatelessWidget {
  const PlaylistLoadingState({super.key});

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          image: DecorationImage(
            image: config.backgrounds.home.isNotEmpty
                ? NetworkImage(config.backgrounds.home)
                : const AssetImage('assets/images/background.png') as ImageProvider,
            fit: BoxFit.cover,
            colorFilter: ColorFilter.mode(
              Colors.black.withValues(alpha: 0.5),
              BlendMode.darken,
            ),
          ),
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Image.asset(
                'assets/images/App_Logo.png',
                height: 100,
                fit: BoxFit.contain,
                errorBuilder: (context, error, stackTrace) => 
                  const Icon(Icons.play_arrow_rounded, color: Color(0xFF00B7FF), size: 80),
              ),
              const SizedBox(height: 40),
              const CircularProgressIndicator(
                valueColor: AlwaysStoppedAnimation<Color>(Color(0xFFC12CFF)),
              ),
              const SizedBox(height: 24),
              Text(
                context.loc.loading_playlists.toUpperCase(),
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1.5,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class PlaylistErrorState extends StatelessWidget {
  final String error;
  final VoidCallback onRetry;

  const PlaylistErrorState({
    super.key,
    required this.error,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64, color: Colors.red),
            const SizedBox(height: 16),
            Text(
              context.loc.error_occurred,
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              error,
              style: const TextStyle(color: Colors.red),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: Text(context.loc.try_again),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class PlaylistEmptyState extends StatelessWidget {
  final VoidCallback onCreatePlaylist;

  const PlaylistEmptyState({super.key, required this.onCreatePlaylist});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.playlist_add, size: 80, color: Colors.grey[400]),
            const SizedBox(height: 24),
            Text(
              context.loc.empty_playlist_title,
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: Colors.grey[600],
              ),
            ),
            const SizedBox(height: 12),
            Text(
              context.loc.empty_playlist_message,
              style: TextStyle(
                fontSize: 16,
                color: Colors.grey[500],
                height: 1.4,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 32),
            ElevatedButton.icon(
              onPressed: onCreatePlaylist,
              icon: const Icon(Icons.add),
              label: Text(context.loc.empty_playlist_button),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 12,
                ),
                textStyle: const TextStyle(fontSize: 16),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
