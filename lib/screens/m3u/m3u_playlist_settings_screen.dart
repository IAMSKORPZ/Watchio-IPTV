import 'package:flutter/material.dart';
import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/l10n/localization_extension.dart';
import '../../widgets/playlist_info_widget.dart';
import '../settings/general_settings_section.dart';

class M3uPlaylistSettingsScreen extends StatelessWidget {
  final Playlist playlist;

  const M3uPlaylistSettingsScreen({super.key, required this.playlist});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: SelectableText(
          context.loc.settings,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
        children: [
          const GeneralSettingsWidget(),
          const SizedBox(height: 16),
          PlaylistInfoWidget(playlist: playlist),
        ],
      ),
    );
  }
}
