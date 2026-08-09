import 'dart:ui';
import 'package:another_iptv_player/models/api_configuration_model.dart';
import 'package:another_iptv_player/models/playlist_content_model.dart';
import 'package:another_iptv_player/repositories/iptv_repository.dart';
import 'package:another_iptv_player/services/app_state.dart';
import 'package:another_iptv_player/utils/get_playlist_type.dart';
import 'package:another_iptv_player/screens/player/unified_player_screen.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../controllers/favorites_controller.dart';
import '../../../services/tmdb_service.dart';
import '../../../shared/widgets/focus_wrapper.dart';
import '../../../shared/widgets/watchio_focus_action.dart';

class MovieDetailsScreen extends StatefulWidget {
  final ContentItem contentItem;
  const MovieDetailsScreen({super.key, required this.contentItem});

  @override
  State<MovieDetailsScreen> createState() => _MovieDetailsScreenState();
}

class _MovieDetailsScreenState extends State<MovieDetailsScreen> {
  static const Color _movieAccent = Color(0xFFA855F7);
  late final FavoritesController _favoritesController;
  late final IptvRepository? _repository;

  Map<String, dynamic>? _vodInfo;
  bool _isLoading = true;
  bool _isFavorite = false;
  String? _tmdbTrailerKey;

  final FocusNode _playFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _favoritesController = FavoritesController();

    if (isXtreamCode && AppState.currentPlaylist != null) {
      _repository = IptvRepository(
        ApiConfig(
          baseUrl: AppState.currentPlaylist!.url!,
          username: AppState.currentPlaylist!.username!,
          password: AppState.currentPlaylist!.password!,
        ),
        AppState.currentPlaylist!.id,
      );
    } else {
      _repository = null;
    }

    _loadAllData();
  }

  @override
  void dispose() {
    _playFocusNode.dispose();
    super.dispose();
  }

  Future<void> _loadAllData() async {
    setState(() => _isLoading = true);
    await Future.wait([_loadVodInfo(), _checkFavoriteStatus()]);

    // Try fallback TMDB trailer if provider trailer is missing
    if (_vodInfo != null) {
      final providerTrailer =
          widget.contentItem.vodStream?.youtubeTrailer ??
          _vodInfo!['info']?['youtube_trailer'];
      if (providerTrailer == null || providerTrailer.toString().isEmpty) {
        final tmdbIdStr = _vodInfo!['info']?['tmdb_id'];
        if (tmdbIdStr != null) {
          final tmdbId = int.tryParse(tmdbIdStr.toString());
          if (tmdbId != null) {
            _tmdbTrailerKey = await TmdbService().getMovieTrailer(tmdbId);
          }
        }
      }
    }

    if (mounted) {
      setState(() => _isLoading = false);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _playFocusNode.requestFocus();
      });
    }
  }

  Future<void> _checkFavoriteStatus() async {
    final fav = await _favoritesController.isFavorite(
      widget.contentItem.id,
      widget.contentItem.contentType,
    );
    if (mounted) setState(() => _isFavorite = fav);
  }

  Future<void> _toggleFavorite() async {
    final result = await _favoritesController.toggleFavorite(
      widget.contentItem,
    );
    if (mounted) {
      setState(() => _isFavorite = result);
    }
  }

  Future<void> _loadVodInfo() async {
    if (!isXtreamCode || _repository == null) return;
    try {
      final info = await _repository.getVodInfo(widget.contentItem.id);
      if (mounted) _vodInfo = info;
    } catch (_) {}
  }

  String? get _posterUrl {
    if (_vodInfo != null) {
      final cover =
          _vodInfo!['info']?['movie_image'] ??
          _vodInfo!['info']?['cover_big'] ??
          _vodInfo!['info']?['cover'];
      if (cover is String && cover.isNotEmpty) return cover;
    }
    return widget.contentItem.coverPath?.isNotEmpty == true
        ? widget.contentItem.coverPath
        : widget.contentItem.imagePath.isNotEmpty
        ? widget.contentItem.imagePath
        : widget.contentItem.vodStream?.streamIcon;
  }

  String? get _backdropUrl {
    if (_vodInfo != null) {
      final backdrop = _vodInfo!['info']?['backdrop_path'];
      if (backdrop is List && backdrop.isNotEmpty) {
        return backdrop.first.toString();
      }
      if (backdrop is String && backdrop.isNotEmpty) {
        return backdrop;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: CircularProgressIndicator(color: Color(0xFFC12CFF)),
        ),
      );
    }

    final info = _vodInfo?['info'] ?? {};
    final year =
        info['releasedate']?.toString().split('-').first ??
        info['year']?.toString() ??
        '';
    final rating = double.tryParse(info['rating']?.toString() ?? '0') ?? 0.0;

    // Title cleanup: Remove duplicated years and quotes
    String title = widget.contentItem.name.trim();
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

    // Defensively handle metadata values
    final String director = _getSafeValue(
      info['director'],
      fallback: 'Unknown',
    );
    final String releaseDate = _getSafeValue(
      info['releasedate'] ?? info['releaseDate'] ?? info['year'],
      fallback: 'Unknown',
    );
    final String duration = _getSafeValue(
      info['duration'],
      fallback: 'Unknown',
    );
    final String genre = _getSafeValue(
      info['genre'] ?? widget.contentItem.vodStream?.genre,
      fallback: 'Unknown',
    );
    final String cast = _getSafeValue(info['cast'], fallback: 'Unknown');
    final String plot = _getSafeValue(
      info['plot'] ?? widget.contentItem.description,
      fallback: 'Unknown',
    );

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // BACKGROUND
          _buildBackground(),

          // CONTENT
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 32.0,
                vertical: 8.0,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // TOP BAR: [Back] [Logo] [Center Title]
                  _buildTopBar(title, year),

                  const SizedBox(height: 12),

                  // MAIN ROW (2 Columns: Poster, Metadata/Actions)
                  Expanded(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // LEFT COLUMN (Poster + Stars)
                        _buildPosterColumn(rating),

                        const SizedBox(width: 32),

                        // RIGHT COLUMN (Metadata + Buttons + Description)
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              if (director != 'Unknown')
                                _buildMetadataRow('Directed By:', director),
                              if (releaseDate != 'Unknown')
                                _buildMetadataRow('Release Date:', releaseDate),
                              if (duration != 'Unknown')
                                _buildMetadataRow(
                                  'Duration:',
                                  duration,
                                  isBadge: true,
                                ),
                              if (genre != 'Unknown')
                                _buildMetadataRow('Genre:', genre),
                              if (cast != 'Unknown')
                                _buildMetadataRow('Cast:', cast, maxLines: 1),

                              const SizedBox(height: 12),

                              // BUTTONS
                              Row(
                                children: [
                                  _buildPlayButton(),
                                  const SizedBox(width: 12),
                                  _buildTrailerButton(),
                                ],
                              ),

                              const SizedBox(height: 12),

                              // DESCRIPTION (max 2 lines) - respect width of metadata column
                              _buildDescription(plot),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          // PINNED ACTION COLUMN (Top Right)
          Positioned(
            top: 10, // Requirement 4: Pinned to top-right, moved upward
            right: 32,
            child: _buildRightActionColumn(),
          ),
        ],
      ),
    );
  }

  String _getSafeValue(dynamic val, {String fallback = 'Unknown'}) {
    if (val == null) return fallback;
    final s = val.toString().trim();
    if (s.isEmpty || s.toLowerCase() == 'n/a' || s == '0') return fallback;
    return s;
  }

  Widget _buildBackground() {
    final url = _backdropUrl ?? _posterUrl;
    return Stack(
      fit: StackFit.expand,
      children: [
        if (url != null)
          CachedNetworkImage(
            imageUrl: url,
            fit: BoxFit.cover,
            errorWidget: (ctx, err, st) => Container(color: Colors.black),
          )
        else
          Container(color: Colors.black),

        BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Container(color: Colors.black.withValues(alpha: 0.7)),
        ),
      ],
    );
  }

  Widget _buildTopBar(String title, String year) {
    return SizedBox(
      height: 80, // Increased height for 64px logo
      child: Stack(
        children: [
          // Left part: Back + Logo
          Align(
            alignment: Alignment.centerLeft,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment:
                  CrossAxisAlignment.center, // Vertically center items
              children: [
                FocusWrapper(
                  onPressed: () => Navigator.of(context).pop(),
                  borderRadius: BorderRadius.circular(12),
                  accentColor: _movieAccent,
                  child: IconButton(
                    icon: const Icon(
                      Icons.arrow_back,
                      color: Colors.white,
                      size: 32,
                    ),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ),
                const SizedBox(width: 20), // 20px spacing
                // WATCHIO LOGO - Fixed 64px height
                Image.asset(
                  'assets/images/App_Logo.png',
                  height: 64,
                  fit: BoxFit.contain,
                  errorBuilder: (ctx, err, st) => const Text(
                    'WATCHIO',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.0,
                    ),
                  ),
                ),
              ],
            ),
          ),

          // Center part: Title
          Align(
            alignment: Alignment.center,
            child: Padding(
              padding: const EdgeInsets.only(top: 16.0), // Maintained alignment
              child: Text(
                year.isNotEmpty ? '$title ($year)' : title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPosterColumn(double rating) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 140,
          height: 210,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: Colors.white24, width: 1),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.5),
                blurRadius: 10,
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
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
        ),
        const SizedBox(height: 4),
        _buildStars(rating),
      ],
    );
  }

  Widget _buildStars(double rating) {
    final starRating = (rating / 2.0).clamp(0.0, 5.0);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: List.generate(5, (index) {
        if (index < starRating.floor()) {
          return const Padding(
            padding: EdgeInsets.symmetric(horizontal: 1.0),
            child: Icon(Icons.star, color: Colors.amber, size: 16),
          );
        } else if (index < starRating && (starRating - index) >= 0.5) {
          return const Padding(
            padding: EdgeInsets.symmetric(horizontal: 1.0),
            child: Icon(Icons.star_half, color: Colors.amber, size: 16),
          );
        } else {
          return const Padding(
            padding: EdgeInsets.symmetric(horizontal: 1.0),
            child: Icon(Icons.star, color: Colors.white24, size: 16),
          );
        }
      }),
    );
  }

  Widget _buildMetadataRow(
    String label,
    String value, {
    bool isBadge = false,
    int maxLines = 1,
  }) {
    return SizedBox(
      height: 28,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // FIXED-WIDTH LABEL COLUMN
          SizedBox(
            width: 140,
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          // VALUE COLUMN - Expanded will take remaining width (Requirement 3)
          Expanded(
            child: isBadge
                ? Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white10,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        value,
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  )
                : Text(
                    value,
                    style: const TextStyle(color: Colors.white70, fontSize: 16),
                    maxLines: maxLines,
                    overflow: TextOverflow.ellipsis,
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildRightActionColumn() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        FocusWrapper(
          onPressed: () {},
          borderRadius: BorderRadius.circular(12),
          accentColor: _movieAccent,
          child: IconButton(
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
            icon: const Icon(Icons.more_vert, color: Colors.white, size: 32),
            onPressed: () {},
          ),
        ),
        const SizedBox(height: 12), // Requirement 5: 12px spacing
        FocusWrapper(
          onPressed: _toggleFavorite,
          borderRadius: BorderRadius.circular(12),
          accentColor: _movieAccent,
          child: IconButton(
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
            icon: Icon(
              _isFavorite ? Icons.favorite : Icons.favorite_border,
              color: _isFavorite ? Colors.redAccent : Colors.white,
              size: 32,
            ),
            onPressed: _toggleFavorite,
          ),
        ),
      ],
    );
  }

  Widget _buildPlayButton() {
    return FocusWrapper(
      onPressed: _openPlayer,
      borderRadius: BorderRadius.circular(6),
      accentColor: _movieAccent,
      child: Material(
        color: Colors.white.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(6),
        child: WatchioFocusAction(
          focusNode: _playFocusNode,
          onActivate: _openPlayer,
          child: InkWell(
            onTap: _openPlayer,
            borderRadius: BorderRadius.circular(6),
            child: Container(
              width: 140,
              height: 46,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white12),
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Text(
                'Play',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTrailerButton() {
    final trailerKey =
        widget.contentItem.vodStream?.youtubeTrailer ??
        _vodInfo!['info']?['youtube_trailer'] ??
        _tmdbTrailerKey;
    if (trailerKey == null || trailerKey.toString().isEmpty) {
      return const SizedBox.shrink();
    }

    return FocusWrapper(
      onPressed: () => _openTrailer(context),
      borderRadius: BorderRadius.circular(6),
      accentColor: _movieAccent,
      child: Material(
        color: Colors.white.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(6),
        child: WatchioFocusAction(
          onActivate: () => _openTrailer(context),
          child: InkWell(
            onTap: () => _openTrailer(context),
            borderRadius: BorderRadius.circular(6),
            child: Container(
              width: 150,
              height: 46, // Height matching Play
              alignment: Alignment.center,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white12),
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Text(
                'Watch Trailer',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDescription(String plot) {
    return Text(
      plot,
      style: const TextStyle(color: Colors.white70, fontSize: 15, height: 1.3),
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    );
  }

  Future<void> _openTrailer(BuildContext context) async {
    final trailerKey =
        widget.contentItem.vodStream?.youtubeTrailer ??
        _vodInfo!['info']?['youtube_trailer'] ??
        _tmdbTrailerKey;
    final urlString = (trailerKey is String && trailerKey.isNotEmpty)
        ? 'https://www.youtube.com/watch?v=$trailerKey'
        : 'https://www.youtube.com/results?search_query=${Uri.encodeQueryComponent('${widget.contentItem.name} trailer')}';
    try {
      await launchUrl(
        Uri.parse(urlString),
        mode: LaunchMode.externalApplication,
      );
    } catch (_) {}
  }

  void _openPlayer() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => UnifiedPlayerScreen(
          contentItem: widget.contentItem,
          queue: [widget.contentItem],
        ),
      ),
    );
  }
}
