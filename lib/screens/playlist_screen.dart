import 'package:another_iptv_player/l10n/localization_extension.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../controllers/playlist_controller.dart';
import '../../models/playlist_model.dart';
import 'xtream-codes/new_xtream_code_playlist_screen.dart';
import '../../widgets/playlist_card.dart';
import '../../widgets/playlist_states.dart';

class PlaylistScreen extends StatefulWidget {
  const PlaylistScreen({super.key});

  @override
  PlaylistScreenState createState() => PlaylistScreenState();
}

class PlaylistScreenState extends State<PlaylistScreen> {
  bool _hasAttemptedLoad = false;

  @override
  void initState() {
    super.initState();
    // Use the global provider and load once
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final controller = context.read<PlaylistController>();
      if (!controller.isLoading) {
        controller.loadPlaylists(context).then((_) {
          if (mounted) {
            setState(() {
              _hasAttemptedLoad = true;
            });
          }
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Consumer<PlaylistController>(
        builder: (context, controller, child) {
          // 1. Initial Loading (Only when first opening the app/screen)
          if (controller.isLoading && !_hasAttemptedLoad) {
            return const PlaylistLoadingState();
          }

          // 2. Error State
          if (controller.error != null) {
            return PlaylistErrorState(
              error: controller.error!,
              onRetry: () => controller.loadPlaylists(context),
            );
          }

          // 3. No Playlists Found (Redesign UI)
          if (controller.playlists.isEmpty) {
            return const NewXtreamCodePlaylistScreen();
          }

          // 4. List of Playlists (Standard UI)
          return _buildPlaylistList(context, controller);
        },
      ),
      floatingActionButton: _buildFloatingActionButton(context),
    );
  }

  Widget _buildPlaylistList(
    BuildContext context,
    PlaylistController controller,
  ) {
    return RefreshIndicator(
      onRefresh: () => controller.loadPlaylists(context),
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: controller.playlists.length,
        itemBuilder: (context, index) {
          return PlaylistCard(
            playlist: controller.playlists[index],
            onTap: () =>
                controller.openPlaylist(context, controller.playlists[index]),
            onDelete: () => _showDeleteDialog(
              context,
              controller,
              controller.playlists[index],
            ),
          );
        },
      ),
    );
  }

  Widget _buildFloatingActionButton(BuildContext context) {
    final controller = context.watch<PlaylistController>();
    if (controller.playlists.isEmpty) return const SizedBox.shrink();

    return FloatingActionButton(
      onPressed: () => _navigateToCreatePlaylist(context),
      tooltip: context.loc.create_new_playlist,
      child: const Icon(Icons.add, color: Colors.white),
    );
  }

  void _navigateToCreatePlaylist(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const NewXtreamCodePlaylistScreen(),
      ),
    );
  }

  void _showDeleteDialog(
    BuildContext context,
    PlaylistController controller,
    Playlist playlist,
  ) {
    showDialog(
      context: context,
      builder: (context) => _DeletePlaylistDialog(
        playlist: playlist,
        onDelete: () async {
          final success = await controller.deletePlaylist(playlist.id);
          if (success && context.mounted) {
            _showSuccessSnackBar(context, playlist.name);
          }
        },
      ),
    );
  }

  void _showSuccessSnackBar(BuildContext context, String playlistName) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(context.loc.playlist_deleted(playlistName)),
        backgroundColor: Colors.green,
      ),
    );
  }
}

class _DeletePlaylistDialog extends StatelessWidget {
  final Playlist playlist;
  final VoidCallback onDelete;

  const _DeletePlaylistDialog({required this.playlist, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(context.loc.playlist_delete_confirmation_title),
      content: RichText(
        text: TextSpan(
          children: [
            TextSpan(
              style: Theme.of(context).textTheme.bodyMedium,
              text: context.loc.playlist_delete_confirmation_message(
                playlist.name,
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(context.loc.cancel),
        ),
        ElevatedButton(
          onPressed: () {
            Navigator.pop(context);
            onDelete();
          },
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.red,
            foregroundColor: Colors.white,
          ),
          child: Text(context.loc.delete),
        ),
      ],
    );
  }
}
