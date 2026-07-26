import 'package:flutter/material.dart';

import '../models/playlist_model.dart';
import '../repositories/user_preferences.dart';
import '../services/app_state.dart';
import '../services/playlist_service.dart';
import '../shared/widgets/watchio_focus_action.dart';
import '../utils/firestick_performance.dart';
import 'xtream-codes/new_xtream_code_playlist_screen.dart';
import 'xtream-codes/xtream_code_home_screen.dart';

class PlaylistSwitchScreen extends StatefulWidget {
  const PlaylistSwitchScreen({super.key});

  @override
  State<PlaylistSwitchScreen> createState() => _PlaylistSwitchScreenState();
}

class _PlaylistSwitchScreenState extends State<PlaylistSwitchScreen> {
  late Future<List<Playlist>> _playlistsFuture;

  @override
  void initState() {
    super.initState();
    _playlistsFuture = PlaylistService.getXStreamPlaylists();
  }

  Future<void> _switchTo(Playlist playlist) async {
    await UserPreferences.setLastPlaylist(playlist.id);
    AppState.currentPlaylist = playlist;
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(
        builder: (_) => XtreamCodeHomeScreen(playlist: playlist),
      ),
      (_) => false,
    );
  }

  void _addPlaylist() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const NewXtreamCodePlaylistScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF050816),
      body: Container(
        decoration: const BoxDecoration(
          image: DecorationImage(
            image: AssetImage('assets/images/background.png'),
            fit: BoxFit.cover,
          ),
        ),
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                const Color(0xFF050812).withValues(alpha: 0.25),
                const Color(0xFF050812).withValues(alpha: 0.88),
              ],
            ),
          ),
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(64, 34, 64, 40),
              child: FutureBuilder<List<Playlist>>(
                future: _playlistsFuture,
                builder: (context, snapshot) {
                  final playlists = snapshot.data ?? const <Playlist>[];

                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          _HeaderIconButton(
                            icon: Icons.arrow_back_rounded,
                            onTap: () => Navigator.of(context).maybePop(),
                          ),
                          const SizedBox(width: 18),
                          Image.asset(
                            'assets/images/App_Logo.png',
                            height: 78,
                            fit: BoxFit.contain,
                          ),
                          const Spacer(),
                          const Text(
                            'SWITCH PLAYLIST',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 28,
                              fontWeight: FontWeight.w900,
                              letterSpacing: 1.8,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 24),
                      if (snapshot.connectionState == ConnectionState.waiting)
                        const Expanded(
                          child: Center(child: CircularProgressIndicator()),
                        )
                      else if (playlists.isEmpty)
                        Expanded(child: _EmptyState(onAdd: _addPlaylist))
                      else
                        Expanded(
                          child: FocusTraversalGroup(
                            child: ListView.separated(
                              itemCount: playlists.length,
                              separatorBuilder: (_, _) =>
                                  const SizedBox(height: 14),
                              itemBuilder: (context, index) {
                                final playlist = playlists[index];
                                return _PlaylistSwitchTile(
                                  playlist: playlist,
                                  isCurrent:
                                      playlist.id ==
                                      AppState.currentPlaylist?.id,
                                  autofocus: index == 0,
                                  onTap: () => _switchTo(playlist),
                                );
                              },
                            ),
                          ),
                        ),
                      const SizedBox(height: 18),
                      Align(
                        alignment: Alignment.centerRight,
                        child: _AddPlaylistButton(onTap: _addPlaylist),
                      ),
                    ],
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PlaylistSwitchTile extends StatefulWidget {
  const _PlaylistSwitchTile({
    required this.playlist,
    required this.isCurrent,
    required this.onTap,
    this.autofocus = false,
  });

  final Playlist playlist;
  final bool isCurrent;
  final VoidCallback onTap;
  final bool autofocus;

  @override
  State<_PlaylistSwitchTile> createState() => _PlaylistSwitchTileState();
}

class _PlaylistSwitchTileState extends State<_PlaylistSwitchTile> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final borderColor = widget.isCurrent
        ? const Color(0xFFC12CFF)
        : const Color(0xFF00B7FF);

    return WatchioFocusAction(
      autofocus: widget.autofocus,
      onActivate: widget.onTap,
      onFocusChange: (focused) => setState(() => _focused = focused),
      mouseCursor: SystemMouseCursors.click,
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: perfDuration(const Duration(milliseconds: 150)),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            gradient: LinearGradient(
              colors: [
                const Color(0xFF21144A).withValues(alpha: 0.94),
                const Color(0xFF16285C).withValues(alpha: 0.94),
              ],
            ),
            border: Border.all(
              color: _focused || widget.isCurrent
                  ? borderColor
                  : Colors.white.withValues(alpha: 0.12),
              width: _focused ? 2.8 : 1.4,
            ),
          ),
          child: Row(
            children: [
              Icon(
                widget.isCurrent
                    ? Icons.check_circle_rounded
                    : Icons.switch_account_rounded,
                color: widget.isCurrent
                    ? const Color(0xFFC12CFF)
                    : const Color(0xFF00B7FF),
                size: 34,
              ),
              const SizedBox(width: 18),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            widget.playlist.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 20,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ),
                        if (widget.isCurrent)
                          const Text(
                            'CURRENT',
                            style: TextStyle(
                              color: Color(0xFFC12CFF),
                              fontSize: 12,
                              fontWeight: FontWeight.w900,
                              letterSpacing: 1,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      widget.playlist.url ?? '',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.58),
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              const Icon(
                Icons.chevron_right_rounded,
                color: Colors.white70,
                size: 34,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HeaderIconButton extends StatefulWidget {
  const _HeaderIconButton({required this.icon, required this.onTap});

  final IconData icon;
  final VoidCallback onTap;

  @override
  State<_HeaderIconButton> createState() => _HeaderIconButtonState();
}

class _HeaderIconButtonState extends State<_HeaderIconButton> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    return WatchioFocusAction(
      onActivate: widget.onTap,
      onFocusChange: (focused) => setState(() => _focused = focused),
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(14),
        child: AnimatedContainer(
          duration: perfDuration(const Duration(milliseconds: 150)),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: _focused
                ? Colors.white.withValues(alpha: 0.16)
                : Colors.white.withValues(alpha: 0.05),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: _focused ? const Color(0xFFC12CFF) : Colors.white12,
            ),
          ),
          child: Icon(widget.icon, color: Colors.white, size: 26),
        ),
      ),
    );
  }
}

class _AddPlaylistButton extends StatefulWidget {
  const _AddPlaylistButton({required this.onTap});

  final VoidCallback onTap;

  @override
  State<_AddPlaylistButton> createState() => _AddPlaylistButtonState();
}

class _AddPlaylistButtonState extends State<_AddPlaylistButton> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    return WatchioFocusAction(
      onActivate: widget.onTap,
      onFocusChange: (focused) => setState(() => _focused = focused),
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(18),
        child: AnimatedContainer(
          duration: perfDuration(const Duration(milliseconds: 150)),
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 13),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            gradient: const LinearGradient(
              colors: [Color(0xFFC12CFF), Color(0xFF00B7FF)],
            ),
            border: _focused ? Border.all(color: Colors.white, width: 2) : null,
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.add_rounded, color: Colors.white, size: 22),
              SizedBox(width: 8),
              Text(
                'ADD PLAYLIST',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.onAdd});

  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.switch_account_rounded,
            color: Color(0xFF00B7FF),
            size: 70,
          ),
          const SizedBox(height: 18),
          const Text(
            'No playlists found',
            style: TextStyle(
              color: Colors.white,
              fontSize: 24,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'You need to add one first.',
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.62),
              fontSize: 15,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 20),
          _AddPlaylistButton(onTap: onAdd),
        ],
      ),
    );
  }
}
