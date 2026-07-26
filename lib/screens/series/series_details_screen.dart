import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:another_iptv_player/database/database.dart';
import 'package:another_iptv_player/models/api_configuration_model.dart';
import 'package:another_iptv_player/models/content_type.dart';
import 'package:another_iptv_player/models/playlist_content_model.dart';
import 'package:another_iptv_player/services/app_state.dart';
import 'package:another_iptv_player/repositories/iptv_repository.dart';
import 'package:another_iptv_player/l10n/localization_extension.dart';
import 'package:another_iptv_player/services/watch_history_service.dart';
import 'package:another_iptv_player/services/tmdb_service.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../controllers/favorites_controller.dart';
import 'episode_screen.dart';
import '../../../shared/widgets/focus_wrapper.dart';

class SeriesDetailsScreen extends StatefulWidget {
  final ContentItem contentItem;

  const SeriesDetailsScreen({super.key, required this.contentItem});

  @override
  State<SeriesDetailsScreen> createState() => _SeriesDetailsScreenState();
}

class _SeriesDetailsScreenState extends State<SeriesDetailsScreen> {
  late IptvRepository _repository;
  late FavoritesController _favoritesController;
  late WatchHistoryService _watchHistoryService;

  SeriesInfosData? seriesInfo;
  List<SeasonsData> seasons = [];
  List<EpisodesData> episodes = [];
  bool isLoading = true;
  String? error;
  bool _isFavorite = false;

  SeasonsData? _selectedSeason;
  EpisodesData? _lastOpenedEpisode;
  String? _tmdbTrailerKey;

  String _activeTab = 'episodes'; // 'episodes' or 'cast'

  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _favoritesController = FavoritesController();
    _watchHistoryService = WatchHistoryService();
    _initializeRepository();
    _loadSeriesDetails();
    _checkFavoriteStatus();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _initializeRepository() {
    _repository = IptvRepository(
      ApiConfig(
        baseUrl: AppState.currentPlaylist!.url!,
        username: AppState.currentPlaylist!.username!,
        password: AppState.currentPlaylist!.password!,
      ),
      AppState.currentPlaylist!.id,
    );
  }

  Future<void> _loadSeriesDetails() async {
    if (!mounted) return;
    setState(() {
      isLoading = true;
      error = null;
    });

    try {
      final seriesId = widget.contentItem.id;
      final seriesResponse = await _repository.getSeriesInfo(seriesId);

      if (seriesResponse != null && mounted) {
        final fetchedSeasons = seriesResponse.seasons;
        fetchedSeasons.sort((a, b) => a.seasonNumber.compareTo(b.seasonNumber));

        setState(() {
          seriesInfo = seriesResponse.seriesInfo;
          seasons = fetchedSeasons;
          episodes = seriesResponse.episodes;

          if (seasons.isNotEmpty) {
            _selectedSeason = seasons.firstWhere(
              (s) => s.seasonNumber == 1,
              orElse: () => seasons.firstWhere(
                (s) => s.seasonNumber > 0,
                orElse: () => seasons.first,
              ),
            );
          }
          isLoading = false;
        });

        final providerTrailer =
            seriesInfo?.youtubeTrailer ??
            widget.contentItem.seriesStream?.youtubeTrailer;
        if (providerTrailer == null || providerTrailer.isEmpty) {
          if (seriesInfo?.tmdbId != null) {
            _tmdbTrailerKey = await TmdbService().getTvShowTrailer(
              seriesInfo!.tmdbId!,
            );
          }
        }

        await _loadLastOpenedEpisodeFromHistory();
      } else if (mounted) {
        setState(() {
          error = context.loc.preparing_series_exception_1;
          isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          error = context.loc.preparing_series_exception_2(e.toString());
          isLoading = false;
        });
      }
    }
  }

  Future<void> _loadLastOpenedEpisodeFromHistory() async {
    if (episodes.isEmpty) return;
    final playlistId = AppState.currentPlaylist!.id;
    final allSeriesHistory = await _watchHistoryService
        .getWatchHistoryByContentType(ContentType.series, playlistId);

    if (!mounted || allSeriesHistory.isEmpty) return;

    final Map<String, EpisodesData> byId = {
      for (final ep in episodes) ep.episodeId.toString(): ep,
    };

    EpisodesData? matched;
    for (final history in allSeriesHistory) {
      final ep = byId[history.streamId];
      if (ep != null) {
        matched = ep;
        break;
      }
    }

    final ep = matched;
    if (ep != null && mounted) {
      setState(() {
        _lastOpenedEpisode = ep;
      });
    }
  }

  Future<void> _checkFavoriteStatus() async {
    final isFavorite = await _favoritesController.isFavorite(
      widget.contentItem.id,
      widget.contentItem.contentType,
    );
    if (mounted) {
      setState(() => _isFavorite = isFavorite);
    }
  }

  Future<void> _toggleFavorite() async {
    final result = await _favoritesController.toggleFavorite(
      widget.contentItem,
    );
    if (mounted) {
      setState(() => _isFavorite = result);
    }
  }

  void _openEpisode(EpisodesData episode) {
    if (mounted) setState(() => _lastOpenedEpisode = episode);

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => EpisodeScreen(
          seriesInfo: seriesInfo,
          seasons: seasons,
          episodes: episodes,
          contentItem: ContentItem(
            episode.episodeId,
            episode.title,
            episode.movieImage ?? "",
            ContentType.series,
            containerExtension: episode.containerExtension,
            season: episode.season,
          ),
        ),
      ),
    );
  }

  String? get _posterUrl {
    return seriesInfo?.cover ??
        widget.contentItem.seriesStream?.cover ??
        widget.contentItem.imagePath;
  }

  String? get _backdropUrl {
    if (seriesInfo?.backdropPath != null &&
        seriesInfo!.backdropPath!.isNotEmpty) {
      return seriesInfo!.backdropPath;
    }
    final paths = widget.contentItem.seriesStream?.backdropPath;
    if (paths != null && paths.isNotEmpty) {
      return paths.first;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: CircularProgressIndicator(color: Color(0xFFC12CFF)),
        ),
      );
    }

    if (error != null) {
      return Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                Icons.error_outline,
                color: Colors.redAccent,
                size: 64,
              ),
              const SizedBox(height: 16),
              Text(error!, style: const TextStyle(color: Colors.white70)),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _loadSeriesDetails,
                child: const Text('RETRY'),
              ),
            ],
          ),
        ),
      );
    }

    final year = seriesInfo?.releaseDate?.split('-').first ?? '';
    String title = (seriesInfo?.name ?? widget.contentItem.name).trim();
    if (title.startsWith('"') && title.endsWith('"')) {
      title = title.substring(1, title.length - 1).trim();
    }
    if (year.isNotEmpty) {
      final yearPattern = '($year)';
      if (title.endsWith(yearPattern)) {
        title = title.substring(0, title.length - yearPattern.length).trim();
      }
      if (title.endsWith(year)) {
        title = title.substring(0, title.length - year.length).trim();
        if (title.endsWith('(')) {
          title = title.substring(0, title.length - 1).trim();
        }
      }
    }
    if (title.endsWith('"')) {
      title = title.substring(0, title.length - 1).trim();
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // BACKGROUND
          _buildBackground(),

          // CONTENT
          Positioned.fill(
            child: CustomScrollView(
              controller: _scrollController,
              slivers: [
                SliverToBoxAdapter(
                  child: Container(
                    // Force details to fill viewport height
                    constraints: BoxConstraints(
                      minHeight: MediaQuery.of(context).size.height,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(height: MediaQuery.of(context).padding.top),
                        _buildHeader(title, year),
                        _buildTwoColumnLayout(title, year),
                      ],
                    ),
                  ),
                ),

                if (_activeTab == 'episodes')
                  _buildEpisodeSliver()
                else
                  _buildCastSliver(),

                SliverToBoxAdapter(
                  child: SizedBox(
                    height: 32 + MediaQuery.of(context).padding.bottom,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBackground() {
    final url = _backdropUrl ?? _posterUrl;
    return Stack(
      fit: StackFit.expand,
      children: [
        if (url != null)
          CachedNetworkImage(
            imageUrl: url,
            width: double.infinity,
            height: double.infinity,
            fit: BoxFit.cover,
            errorWidget: (ctx, err, st) => Container(color: Colors.black),
          )
        else
          Container(color: Colors.black),

        BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: Container(
            width: double.infinity,
            height: double.infinity,
            color: Colors.black.withValues(alpha: 0.75),
          ),
        ),
      ],
    );
  }

  Widget _buildHeader(String title, String year) {
    return Container(
      height: 90, // Reverted to stable height (Requirement: ROLL BACK)
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                icon: const Icon(
                  Icons.arrow_back,
                  color: Colors.white,
                  size: 28,
                ), // Restored size
                onPressed: () => Navigator.of(context).pop(),
                visualDensity: VisualDensity.compact,
                padding: EdgeInsets.zero,
              ),
              const SizedBox(width: 12),
              Image.asset(
                'assets/images/App_Logo.png',
                height: 64, // Kept larger logo as requested
                fit: BoxFit.contain,
                errorBuilder: (ctx, err, st) => const Text(
                  'WATCHIO',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1.0,
                  ),
                ),
              ),
            ],
          ),
          const Spacer(),
          Text(
            year.isNotEmpty ? '$title ($year)' : title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.0,
            ),
          ),
          const Spacer(),
          _buildPopupMenu(),
        ],
      ),
    );
  }

  Widget _buildPopupMenu() {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_vert, color: Colors.white, size: 28),
      onSelected: (value) {
        if (value == 'favorite') {
          _toggleFavorite();
        } else if (value == 'trailer') {
          _openTrailer();
        }
      },
      itemBuilder: (context) => [
        PopupMenuItem(
          value: 'favorite',
          child: Row(
            children: [
              Icon(
                _isFavorite ? Icons.favorite : Icons.favorite_border,
                color: _isFavorite ? Colors.redAccent : Colors.black,
              ),
              const SizedBox(width: 10),
              Text(_isFavorite ? 'Remove Favorite' : 'Add Favorite'),
            ],
          ),
        ),
        if (_tmdbTrailerKey != null ||
            (seriesInfo?.youtubeTrailer?.isNotEmpty ?? false))
          PopupMenuItem(
            value: 'trailer',
            child: Row(
              children: const [
                Icon(Icons.play_circle_outline, color: Colors.black),
                SizedBox(width: 10),
                Text('Watch Trailer'),
              ],
            ),
          ),
      ],
    );
  }

  Future<void> _openTrailer() async {
    final trailerKey = seriesInfo?.youtubeTrailer ?? _tmdbTrailerKey;
    if (trailerKey == null || trailerKey.isEmpty) return;
    final urlString = "https://www.youtube.com/watch?v=$trailerKey";
    try {
      await launchUrl(
        Uri.parse(urlString),
        mode: LaunchMode.externalApplication,
      );
    } catch (_) {}
  }

  Widget _buildTwoColumnLayout(String title, String year) {
    final rating =
        double.tryParse(seriesInfo?.rating?.toString() ?? '0') ?? 0.0;

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        12.0,
        0.0,
        12.0,
        0.0,
      ), // Restored padding (Requirement: ROLL BACK)
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // LEFT COLUMN: Poster -> Stars -> Tabs
          SizedBox(
            width: 210,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildPoster(),
                const SizedBox(height: 8), // Requirement: Directly underneath
                _buildStars(rating),
                const SizedBox(height: 12), // Requirement: Directly underneath
                _buildHorizontalTabs(),
              ],
            ),
          ),

          const SizedBox(width: 24),

          // RIGHT COLUMN: Metadata -> Plot -> Buttons
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildMetadataRow(
                  'Director:',
                  seriesInfo?.director ?? 'Unknown',
                ),
                _buildMetadataRow(
                  'Release Date:',
                  seriesInfo?.releaseDate ?? 'Unknown',
                ),
                _buildMetadataRow('Genre:', seriesInfo?.genre ?? 'Unknown'),
                _buildMetadataRow(
                  'Cast:',
                  seriesInfo?.cast ?? 'Unknown',
                  maxLines: 1,
                ),
                _buildMetadataRow(
                  'Rating:',
                  seriesInfo?.rating?.toString() ?? '0.0',
                ),

                const SizedBox(height: 6), // Reduced gap (Requirement 1, 2)
                const Text(
                  'Plot:',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                  ),
                ),
                const SizedBox(height: 2), // Reduced gap (Requirement 1, 2)
                GestureDetector(
                  onTap: () => _showFullPlotDialog(
                    seriesInfo?.plot ??
                        widget.contentItem.description ??
                        'No description available.',
                  ),
                  child: RichText(
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                    text: TextSpan(
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                        height: 1.3,
                      ),
                      children: [
                        TextSpan(
                          text:
                              seriesInfo?.plot ??
                              widget.contentItem.description ??
                              'No description available.',
                        ),
                        const TextSpan(
                          text: ' ...Read More',
                          style: TextStyle(
                            color: Color(0xFFC12CFF),
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 10), // Reduced gap (Requirement 1, 2)
                _buildActionRow(),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPoster() {
    // Requirement 1: Reduce height by ~15% (230 -> 195), Keep width ratio
    return Container(
      width: 130,
      height: 195,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.white24, width: 1),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.5), blurRadius: 10),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: _posterUrl != null
            ? CachedNetworkImage(
                imageUrl: _posterUrl!,
                fit: BoxFit.cover,
                placeholder: (ctx, url) => Container(color: Colors.white10),
                errorWidget: (ctx, url, err) =>
                    Container(color: Colors.white10),
              )
            : Container(color: Colors.white10),
      ),
    );
  }

  Widget _buildHorizontalTabs() {
    final filteredCount = episodes
        .where((e) => e.season == _selectedSeason?.seasonNumber)
        .length;
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _TabItem(
          label: 'Episodes ($filteredCount)',
          icon: Icons.movie_outlined,
          isActive: _activeTab == 'episodes',
          onTap: () => setState(() => _activeTab = 'episodes'),
        ),
        const SizedBox(width: 16),
        _TabItem(
          label: 'Cast',
          icon: Icons.people_outline,
          isActive: _activeTab == 'cast',
          onTap: () => setState(() => _activeTab = 'cast'),
        ),
      ],
    );
  }

  Widget _buildMetadataRow(String label, String value, {int maxLines = 1}) {
    return Padding(
      padding: const EdgeInsets.only(
        bottom: 2.0,
      ), // Reduced row spacing (Requirement 2)
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.white54,
                fontWeight: FontWeight.bold,
                fontSize: 13,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value.isEmpty ? 'Unknown' : value,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
                fontSize: 13,
              ),
              maxLines: maxLines,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionRow() {
    final epToPlay =
        _lastOpenedEpisode ??
        (episodes.isNotEmpty
            ? episodes.firstWhere(
                (e) => e.season == (_selectedSeason?.seasonNumber ?? 1),
                orElse: () => episodes.first,
              )
            : null);
    final epText = epToPlay != null
        ? ' - S${epToPlay.season}:E${epToPlay.episodeNum}'
        : '';
    final seasonNum = _selectedSeason?.seasonNumber ?? 1;

    return SizedBox(
      height: 54, // Restored height
      child: Row(
        children: [
          // PLAY BUTTON
          Expanded(
            flex: 3,
            child: FocusWrapper(
              onPressed: epToPlay == null
                  ? null
                  : () {
                      _openEpisode(epToPlay);
                    },
              borderRadius: BorderRadius.circular(6),
              child: Material(
                color: const Color(0xFFC12CFF),
                borderRadius: BorderRadius.circular(6),
                child: InkWell(
                  onTap: epToPlay == null
                      ? null
                      : () {
                          _openEpisode(epToPlay);
                        },
                  borderRadius: BorderRadius.circular(6),
                  child: Center(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(
                          Icons.play_arrow,
                          color: Colors.white,
                          size: 22,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Play$epText',
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w900,
                            fontSize: 15,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          // SEASON SELECTOR
          Expanded(
            flex: 4,
            child: FocusWrapper(
              onPressed: _showSeasonSelectionDialog,
              borderRadius: BorderRadius.circular(6),
              child: Material(
                color: Colors.white.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(6),
                child: InkWell(
                  onTap: _showSeasonSelectionDialog,
                  borderRadius: BorderRadius.circular(6),
                  child: Center(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.list, color: Colors.white, size: 18),
                        const SizedBox(width: 8),
                        Text(
                          'Season - $seasonNum ▼',
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 15,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showSeasonSelectionDialog() {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text(
            'Seasons',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900),
          ),
          backgroundColor: const Color(0xFF1A1D29),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: const BorderSide(color: Colors.white10),
          ),
          content: SizedBox(
            width: 300,
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: seasons.length,
              itemBuilder: (context, index) {
                final s = seasons[index];
                final isSelected =
                    _selectedSeason?.seasonNumber == s.seasonNumber;
                return ListTile(
                  title: Text(
                    s.name,
                    style: TextStyle(
                      color: isSelected
                          ? const Color(0xFFC12CFF)
                          : Colors.white70,
                      fontWeight: isSelected
                          ? FontWeight.w900
                          : FontWeight.normal,
                    ),
                  ),
                  onTap: () {
                    setState(() => _selectedSeason = s);
                    Navigator.pop(context);
                  },
                  trailing: isSelected
                      ? const Icon(Icons.check_circle, color: Color(0xFFC12CFF))
                      : null,
                );
              },
            ),
          ),
        );
      },
    );
  }

  void _showFullPlotDialog(String plot) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1A1D29),
        title: const Text(
          'Plot',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: SingleChildScrollView(
          child: Text(
            plot,
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 16,
              height: 1.5,
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(
              'CLOSE',
              style: TextStyle(color: Color(0xFFC12CFF)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEpisodeSliver() {
    final filtered = episodes
        .where((e) => e.season == _selectedSeason?.seasonNumber)
        .toList();
    if (filtered.isEmpty) {
      return const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.all(64.0),
          child: Center(
            child: Text(
              'No episodes found for this season',
              style: TextStyle(color: Colors.white38, fontSize: 18),
            ),
          ),
        ),
      );
    }
    return SliverPadding(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate((context, index) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: _EpisodeRow(
              episode: filtered[index],
              seriesName: seriesInfo?.name ?? widget.contentItem.name,
              onTap: () => _openEpisode(filtered[index]),
            ),
          );
        }, childCount: filtered.length),
      ),
    );
  }

  Widget _buildCastSliver() {
    final castString =
        seriesInfo?.cast ?? widget.contentItem.seriesStream?.cast ?? '';
    if (castString.isEmpty || castString == 'Unknown') {
      return const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.all(64.0),
          child: Center(
            child: Text(
              'No cast information available',
              style: TextStyle(color: Colors.white38, fontSize: 18),
            ),
          ),
        ),
      );
    }
    final castList = castString
        .split(',')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();

    return SliverPadding(
      padding: const EdgeInsets.all(24.0),
      sliver: SliverToBoxAdapter(
        child: Wrap(
          spacing: 12,
          runSpacing: 12,
          children: castList
              .map(
                (name) => Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.05),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Colors.white10),
                  ),
                  child: Text(
                    name,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                    ),
                  ),
                ),
              )
              .toList(),
        ),
      ),
    );
  }

  Widget _buildStars(double rating) {
    final starRating = (rating / 2.0).clamp(0.0, 5.0);
    return Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(5, (index) {
        if (index < starRating.floor()) {
          return const Icon(Icons.star_rounded, color: Colors.amber, size: 16);
        } else if (index < starRating && (starRating - index) >= 0.5) {
          return const Icon(
            Icons.star_half_rounded,
            color: Colors.amber,
            size: 16,
          );
        } else {
          return const Icon(
            Icons.star_outline_rounded,
            color: Colors.white24,
            size: 16,
          );
        }
      }),
    );
  }
}

class _TabItem extends StatelessWidget {
  final String label;
  final bool isActive;
  final VoidCallback onTap;
  final IconData icon;

  const _TabItem({
    required this.label,
    required this.isActive,
    required this.onTap,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return FocusWrapper(
      onPressed: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Icon(
                icon,
                size: 17,
                color: isActive ? Colors.white : Colors.white38,
              ),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  color: isActive ? Colors.white : Colors.white38,
                  fontWeight: FontWeight.bold,
                  fontSize: 17,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Container(
            height: 4.0,
            width: 36,
            decoration: BoxDecoration(
              color: isActive ? const Color(0xFFC12CFF) : Colors.transparent,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
        ],
      ),
    );
  }
}

class _EpisodeRow extends StatefulWidget {
  final EpisodesData episode;
  final String seriesName;
  final VoidCallback onTap;
  const _EpisodeRow({
    required this.episode,
    required this.seriesName,
    required this.onTap,
  });
  @override
  State<_EpisodeRow> createState() => _EpisodeRowState();
}

class _EpisodeRowState extends State<_EpisodeRow> {
  bool _isFocused = false;
  @override
  Widget build(BuildContext context) {
    final seasonStr = widget.episode.season.toString().padLeft(2, '0');
    final episodeStr = widget.episode.episodeNum.toString().padLeft(2, '0');
    final displayTitle = 'S${seasonStr}E$episodeStr • ${widget.episode.title}';
    final rating = widget.episode.rating ?? 0.0;

    return FocusableActionDetector(
      onFocusChange: (v) => setState(() => _isFocused = v),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.gameButtonA): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedScale(
          scale: _isFocused ? 1.03 : 1.0,
          duration: const Duration(milliseconds: 200),
          child: Container(
            height: 145,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: _isFocused
                    ? const Color(0xFFC12CFF)
                    : Colors.transparent,
                width: 3,
              ),
              boxShadow: _isFocused
                  ? [
                      BoxShadow(
                        color: const Color(0xFFC12CFF).withValues(alpha: 0.3),
                        blurRadius: 15,
                        spreadRadius: 2,
                      ),
                    ]
                  : [],
            ),
            child: Row(
              children: [
                _buildThumbnail(),
                const SizedBox(width: 20),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        displayTitle,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 6),
                      _buildStars(rating),
                      const SizedBox(height: 10),
                      if (widget.episode.duration != null &&
                          widget.episode.duration!.isNotEmpty)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            widget.episode.duration!,
                            style: const TextStyle(
                              color: Colors.white70,
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildThumbnail() {
    return Container(
      width: 250,
      height: 145,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        image:
            widget.episode.movieImage != null &&
                widget.episode.movieImage!.isNotEmpty
            ? DecorationImage(
                image: CachedNetworkImageProvider(widget.episode.movieImage!),
                fit: BoxFit.cover,
              )
            : null,
        color: Colors.white.withValues(alpha: 0.05),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: Stack(
          children: [
            Center(
              child: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.5),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.play_arrow_rounded,
                  color: Colors.white,
                  size: 36,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStars(double rating) {
    final starRating = (rating / 2.0).clamp(0.0, 5.0);
    return Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(5, (index) {
        if (index < starRating.floor()) {
          return const Icon(Icons.star_rounded, color: Colors.amber, size: 14);
        } else if (index < starRating && (starRating - index) >= 0.5) {
          return const Icon(
            Icons.star_half_rounded,
            color: Colors.amber,
            size: 14,
          );
        } else {
          return const Icon(
            Icons.star_outline_rounded,
            color: Colors.white24,
            size: 14,
          );
        }
      }),
    );
  }
}
