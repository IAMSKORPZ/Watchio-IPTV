import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../models/content_type.dart';
import '../../models/playlist_content_model.dart';
import '../../repositories/search_repository.dart';
import '../../services/app_state.dart';
import '../../services/tmdb_service.dart';
import '../../services/trakt_service.dart';
import '../movies/movie_details_screen.dart';
import '../series/series_details_screen.dart';
import '../settings/widgets/watchio_settings_scaffold.dart';

class TraktScreen extends StatefulWidget {
  const TraktScreen({super.key});

  @override
  State<TraktScreen> createState() => _TraktScreenState();
}

class _TraktScreenState extends State<TraktScreen> {
  final _service = TraktService();
  bool _loading = true;
  bool _loggedIn = false;
  String? _error;
  Map<String, dynamic>? _settings;
  List<Map<String, dynamic>> _watchlist = [];
  List<Map<String, dynamic>> _history = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final loggedIn = await _service.isLoggedIn;
      if (!loggedIn) {
        if (mounted) setState(() => _loggedIn = false);
        return;
      }
      final data = await Future.wait([
        _service.getSettings(),
        _service.getWatchlist(),
        _service.getHistory(),
      ]);
      if (!mounted) return;
      final watchlist = await _enrich(data[1] as List<Map<String, dynamic>>);
      final history = await _enrich(data[2] as List<Map<String, dynamic>>);
      if (!mounted) return;
      setState(() {
        _loggedIn = true;
        _settings = data[0] as Map<String, dynamic>;
        _watchlist = watchlist;
        _history = history;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<List<Map<String, dynamic>>> _enrich(
    List<Map<String, dynamic>> source,
  ) async {
    final playlistId = AppState.currentPlaylist?.id;
    final search = SearchRepository();
    final tmdb = TmdbService();
    final unique = <String, Map<String, dynamic>>{};
    for (final item in source) {
      final media = (item['movie'] ?? item['show']) as Map<String, dynamic>?;
      if (media == null) continue;
      final ids = media['ids'] as Map<String, dynamic>?;
      unique['${ids?['trakt'] ?? media['title']}'] = item;
    }

    final enriched = <Map<String, dynamic>>[];
    for (final item in unique.values) {
      final media = (item['movie'] ?? item['show']) as Map<String, dynamic>;
      final isSeries = item['show'] != null;
      final title = media['title']?.toString() ?? '';
      ContentItem? local;
      if (playlistId != null && title.isNotEmpty) {
        final result = await search.search(
          playlistId,
          title,
          contentType: isSeries ? ContentType.series : ContentType.vod,
          limit: 10,
        );
        for (final candidate in result.items) {
          if (_normalise(candidate.name) == _normalise(title)) {
            final repository = AppState.xtreamCodeRepository;
            if (repository != null &&
                candidate.contentType == ContentType.vod) {
              final vod = await repository.findMovieById(candidate.id);
              if (vod != null) {
                local = ContentItem(
                  vod.streamId,
                  vod.name,
                  vod.streamIcon,
                  ContentType.vod,
                  containerExtension: vod.containerExtension,
                  vodStream: vod,
                );
              }
            } else if (repository != null &&
                candidate.contentType == ContentType.series) {
              final series = await repository.findSeriesStreamById(
                candidate.id,
              );
              if (series != null) {
                local = ContentItem(
                  series.seriesId,
                  series.name,
                  series.cover ?? '',
                  ContentType.series,
                  description: series.plot,
                  seriesStream: series,
                );
              }
            }
            local ??= candidate;
            break;
          }
        }
      }
      final ids = media['ids'] as Map<String, dynamic>?;
      final tmdbId = int.tryParse(ids?['tmdb']?.toString() ?? '');
      final details = tmdbId == null
          ? null
          : await tmdb.getMediaDetails(tmdbId, isSeries: isSeries);
      final poster = local?.imagePath.isNotEmpty == true
          ? local!.imagePath
          : details?['poster_url']?.toString();
      enriched.add({
        ...item,
        '_local': local,
        '_poster': poster,
        '_details': details,
      });
    }
    return enriched;
  }

  String _normalise(String value) => value
      .toLowerCase()
      .replaceAll(RegExp(r'\([^)]*\)|\[[^]]*\]'), '')
      .replaceAll(RegExp(r'\b(4k|uhd|fhd|hd|sd|vm)\b'), '')
      .replaceAll(RegExp(r'[^a-z0-9]+'), ' ')
      .trim();

  Future<void> _login() async {
    try {
      final code = await _service.requestDeviceCode();
      if (!mounted) return;
      await Clipboard.setData(ClipboardData(text: code.userCode));
      await launchUrl(Uri.parse(code.verificationUrl));
      if (!mounted) return;
      final authorized = await showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (_) => _TraktActivationDialog(service: _service, code: code),
      );
      if (authorized == true && mounted) await _load();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return WatchioSettingsScaffold(
      title: 'MY LIST',
      onBack: () => Navigator.pop(context),
      child: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _Message(message: _error!, action: _load, actionLabel: 'RETRY')
          : !_loggedIn
          ? _Message(
              message:
                  'Connect your Trakt account to see your watchlist and history.',
              action: _login,
              actionLabel: 'CONNECT TRAKT',
            )
          : _library(),
    );
  }

  Widget _library() {
    final user = _settings?['user'] as Map<String, dynamic>?;
    final name = user?['name'] ?? user?['username'] ?? 'Trakt User';
    return DefaultTabController(
      length: 2,
      child: Column(
        children: [
          SizedBox(
            height: 46,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 28),
              child: Row(
                children: [
                  const Icon(Icons.account_circle, size: 28),
                  const SizedBox(width: 10),
                  Text(
                    name.toString(),
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const Spacer(),
                  FilledButton.icon(
                    onPressed: () async {
                      await _service.logout();
                      await _load();
                    },
                    icon: const Icon(Icons.logout),
                    label: const Text('LOG OUT'),
                    style: FilledButton.styleFrom(
                      backgroundColor: Colors.white.withValues(alpha: 0.07),
                      foregroundColor: Colors.white,
                      side: BorderSide(
                        color: Colors.white.withValues(alpha: 0.2),
                      ),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 10,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(
            height: 40,
            child: TabBar(
              tabs: [
                Tab(text: 'WATCHLIST'),
                Tab(text: 'HISTORY'),
              ],
            ),
          ),
          Expanded(
            child: TabBarView(children: [_items(_watchlist), _items(_history)]),
          ),
        ],
      ),
    );
  }

  Widget _items(List<Map<String, dynamic>> items) {
    if (items.isEmpty) return const Center(child: Text('Nothing here yet.'));
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 1200
            ? 7
            : constraints.maxWidth >= 900
            ? 6
            : constraints.maxWidth >= 600
            ? 5
            : 3;
        return GridView.builder(
          padding: const EdgeInsets.all(14),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            childAspectRatio: 0.84,
            crossAxisSpacing: 10,
            mainAxisSpacing: 10,
          ),
          itemCount: items.length,
          itemBuilder: (_, index) {
            final item = items[index];
            final media =
                (item['movie'] ?? item['show']) as Map<String, dynamic>? ??
                const {};
            final local = item['_local'] as ContentItem?;
            final poster = item['_poster']?.toString();
            return Card(
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => _openItem(item),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Expanded(
                      child: poster == null || poster.isEmpty
                          ? const ColoredBox(
                              color: Color(0xFF161A2B),
                              child: Icon(Icons.movie_outlined, size: 48),
                            )
                          : CachedNetworkImage(
                              imageUrl: poster,
                              fit: BoxFit.contain,
                              errorWidget: (_, _, _) => const Icon(
                                Icons.broken_image_outlined,
                                size: 48,
                              ),
                            ),
                    ),
                    SizedBox(
                      height: 56,
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(8, 6, 8, 2),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              media['title']?.toString() ?? 'Unknown',
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 12,
                              ),
                            ),
                            const Spacer(),
                            Text(
                              media['year']?.toString() ?? '',
                              style: const TextStyle(fontSize: 11),
                            ),
                          ],
                        ),
                      ),
                    ),
                    SizedBox(
                      height: 42,
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(6, 3, 6, 6),
                        child: local == null
                            ? const Center(
                                child: Text(
                                  'COMING SOON',
                                  style: TextStyle(
                                    color: Colors.white54,
                                    fontSize: 10,
                                  ),
                                ),
                              )
                            : FilledButton.icon(
                                onPressed: () => _openItem(item),
                                icon: const Icon(Icons.play_arrow_rounded),
                                label: const Text('PLAY'),
                                style: FilledButton.styleFrom(
                                  minimumSize: const Size.fromHeight(30),
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                  ),
                                  textStyle: const TextStyle(fontSize: 11),
                                ),
                              ),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  void _openLocal(ContentItem item) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => item.contentType == ContentType.series
            ? SeriesDetailsScreen(contentItem: item)
            : MovieDetailsScreen(contentItem: item),
      ),
    );
  }

  void _openItem(Map<String, dynamic> item) {
    final local = item['_local'] as ContentItem?;
    final media = (item['movie'] ?? item['show']) as Map<String, dynamic>;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => _TraktComingSoonScreen(
          media: media,
          details: item['_details'] as Map<String, dynamic>?,
          poster: item['_poster']?.toString(),
          local: local,
          onPlay: local == null ? null : () => _openLocal(local),
        ),
      ),
    );
  }
}

class _TraktComingSoonScreen extends StatelessWidget {
  const _TraktComingSoonScreen({
    required this.media,
    required this.details,
    required this.poster,
    required this.local,
    required this.onPlay,
  });

  final Map<String, dynamic> media;
  final Map<String, dynamic>? details;
  final String? poster;
  final ContentItem? local;
  final VoidCallback? onPlay;

  @override
  Widget build(BuildContext context) {
    final title = media['title']?.toString() ?? 'Unknown';
    final tmdbOverview = details?['overview']?.toString();
    final overview = tmdbOverview?.isNotEmpty == true
        ? tmdbOverview
        : media['overview']?.toString();
    final tmdbGenres = (details?['genres'] as List<dynamic>?)
        ?.map((genre) => (genre as Map<String, dynamic>)['name'])
        .join(' • ');
    final traktGenres = (media['genres'] as List<dynamic>?)?.join(' • ');
    final genres = tmdbGenres?.isNotEmpty == true ? tmdbGenres : traktGenres;
    final rating = details?['vote_average'] ?? media['rating'];
    return WatchioSettingsScaffold(
      title: title.toUpperCase(),
      onBack: () => Navigator.pop(context),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(48, 12, 48, 24),
        child: Row(
          children: [
            AspectRatio(
              aspectRatio: 2 / 3,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(18),
                child: poster == null || poster!.isEmpty
                    ? const ColoredBox(
                        color: Color(0xFF161A2B),
                        child: Icon(Icons.movie_outlined, size: 64),
                      )
                    : CachedNetworkImage(imageUrl: poster!, fit: BoxFit.cover),
              ),
            ),
            const SizedBox(width: 36),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '${media['year'] ?? ''}'
                    '${genres == null || genres.isEmpty ? '' : '  •  $genres'}'
                    '${rating == null ? '' : '  •  ★ ${double.tryParse(rating.toString())?.toStringAsFixed(1) ?? rating}'}',
                  ),
                  const SizedBox(height: 20),
                  if (local == null)
                    const Chip(label: Text('COMING SOON'))
                  else
                    FilledButton.icon(
                      onPressed: onPlay,
                      icon: const Icon(Icons.play_arrow_rounded),
                      label: const Text('PLAY'),
                    ),
                  const SizedBox(height: 20),
                  Text(
                    overview?.isNotEmpty == true
                        ? overview!
                        : 'This title is not currently available from your provider.',
                    style: const TextStyle(color: Colors.white70, fontSize: 16),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TraktActivationDialog extends StatefulWidget {
  const _TraktActivationDialog({required this.service, required this.code});

  final TraktService service;
  final TraktDeviceCode code;

  @override
  State<_TraktActivationDialog> createState() => _TraktActivationDialogState();
}

class _TraktActivationDialogState extends State<_TraktActivationDialog> {
  String _status = 'Waiting for authorization...';

  @override
  void initState() {
    super.initState();
    _poll();
  }

  Future<void> _poll() async {
    try {
      final authorized = await widget.service.waitForAuthorization(widget.code);
      if (!mounted) return;
      if (authorized) {
        Navigator.pop(context, true);
      } else {
        setState(() => _status = 'Code expired. Please try again.');
      }
    } catch (error) {
      if (mounted) setState(() => _status = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF121629),
      title: const Text('Connect Trakt'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Code copied. Paste it on the Trakt activation page:'),
            const SizedBox(height: 18),
            SelectableText(
              widget.code.userCode,
              style: const TextStyle(
                fontSize: 32,
                fontWeight: FontWeight.bold,
                color: Color(0xFFC12CFF),
                letterSpacing: 4,
              ),
            ),
            const SizedBox(height: 12),
            Text(widget.code.verificationUrl),
            const SizedBox(height: 16),
            const CircularProgressIndicator(),
            const SizedBox(height: 12),
            Text(_status),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('CANCEL'),
        ),
      ],
    );
  }
}

class _Message extends StatelessWidget {
  const _Message({
    required this.message,
    required this.action,
    required this.actionLabel,
  });
  final String message;
  final VoidCallback action;
  final String actionLabel;
  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(
          Icons.playlist_add_check_rounded,
          size: 72,
          color: Color(0xFFC12CFF),
        ),
        const SizedBox(height: 18),
        Text(message, textAlign: TextAlign.center),
        const SizedBox(height: 20),
        FilledButton(onPressed: action, child: Text(actionLabel)),
      ],
    ),
  );
}
