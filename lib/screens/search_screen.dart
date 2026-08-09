import 'dart:async';

import 'package:watchio/l10n/localization_extension.dart';
import 'package:flutter/material.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/playlist_content_model.dart';
import 'package:watchio/repositories/search_repository.dart';
import 'package:watchio/services/app_state.dart';
import 'package:watchio/services/input_mode_controller.dart';
import 'package:watchio/utils/navigate_by_content_type.dart';
import 'package:watchio/utils/responsive_helper.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../widgets/content_card.dart';
import '../../widgets/tv_focusable.dart';
import 'package:provider/provider.dart';

class SearchScreen extends StatefulWidget {
  final ContentType? contentType;

  const SearchScreen({super.key, this.contentType});

  @override
  SearchScreenState createState() => SearchScreenState();
}

class SearchScreenState extends State<SearchScreen> {
  bool isSearching = false;
  bool isLoading = false;
  String? errorMessage;
  bool isSearched = false;
  TextEditingController searchController = TextEditingController();
  FocusNode searchFocusNode = FocusNode();
  List<ContentItem> contentItems = [];
  List<ContentItem> liveItems = [];
  List<ContentItem> movieItems = [];
  List<ContentItem> seriesItems = [];
  List<ContentItem> programItems = [];
  ContentItem? selectedProgram;
  int _globalTabIndex = 0;
  final SearchRepository repository = SearchRepository();
  Timer? _debounce;
  int _searchToken = 0;
  final Map<String, List<ContentItem>> _resultCache = {};
  final Map<String, Future<_SearchDisplayData>> _displayCache = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (context.read<InputModeController>().isMobileMode) {
        startSearch();
      }
    });
  }

  @override
  void dispose() {
    searchController.dispose();
    searchFocusNode.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  String _getSearchHint(BuildContext context) {
    switch (widget.contentType) {
      case null:
        return 'Search channels, movies, series, EPG';
      case ContentType.liveStream:
        return context.loc.search_live_stream;
      case ContentType.vod:
        return context.loc.search_movie;
      case ContentType.series:
        return context.loc.search_series;
    }
  }

  String _getScreenTitle(BuildContext context) {
    switch (widget.contentType) {
      case null:
        return 'Global Search';
      case ContentType.liveStream:
        return context.loc.search_live_stream;
      case ContentType.vod:
        return context.loc.search_movie;
      case ContentType.series:
        return context.loc.search_series;
    }
  }

  void startSearch() {
    setState(() {
      isSearching = true;
      isSearched = true;
    });
    Future.delayed(Duration(milliseconds: 100), () {
      searchFocusNode.requestFocus();
    });
  }

  void stopSearch() {
    setState(() {
      isSearching = false;
      searchController.clear();
      contentItems = [];
    });
    searchFocusNode.unfocus();
  }

  Future<void> _performSearch(String value) async {
    _debounce?.cancel();
    _debounce = Timer(
      const Duration(milliseconds: 300),
      () => _runSearch(value),
    );
  }

  Future<void> _runSearch(String value) async {
    final normalized = value.trim().toLowerCase();
    if (value.isEmpty || value.trim().isEmpty) {
      setState(() {
        isLoading = false;
        errorMessage = null;
        contentItems = [];
        liveItems = [];
        movieItems = [];
        seriesItems = [];
        programItems = [];
        selectedProgram = null;
      });
      return;
    }

    final cacheKey = '${widget.contentType?.name ?? 'global'}:$normalized';
    final cached = widget.contentType == null ? null : _resultCache[cacheKey];
    if (cached != null) {
      setState(() {
        contentItems = cached;
        isLoading = false;
      });
      return;
    }

    final token = ++_searchToken;

    setState(() {
      isLoading = true;
      errorMessage = null;
      contentItems = [];
      if (widget.contentType == null) {
        liveItems = [];
        movieItems = [];
        seriesItems = [];
        programItems = [];
        selectedProgram = null;
      }
    });

    try {
      if (widget.contentType == null) {
        final playlistId = AppState.currentPlaylist!.id;
        final results = await Future.wait([
          repository.search(
            playlistId,
            value,
            contentType: ContentType.liveStream,
            limit: 40,
          ),
          repository.search(
            playlistId,
            value,
            contentType: ContentType.vod,
            limit: 60,
          ),
          repository.search(
            playlistId,
            value,
            contentType: ContentType.series,
            limit: 60,
          ),
        ]);

        if (token != _searchToken || !mounted) return;
        setState(() {
          liveItems = results[0].items;
          movieItems = results[1].items;
          seriesItems = results[2].items;
          contentItems = [...liveItems, ...movieItems, ...seriesItems];
          isLoading = false;
        });
        unawaited(_loadGlobalEpgResults(playlistId, value, token));
        return;
      }

      final page = await repository.search(
        AppState.currentPlaylist!.id,
        value,
        contentType: widget.contentType,
        limit: 50,
      );
      final searchResults = page.items;

      if (token != _searchToken || !mounted) return;
      _resultCache[cacheKey] = searchResults;
      if (_resultCache.length > 20) {
        _resultCache.remove(_resultCache.keys.first);
      }

      setState(() {
        contentItems = searchResults;
        isLoading = false;
      });
    } catch (e) {
      if (token != _searchToken || !mounted) return;
      setState(() {
        errorMessage = e.toString();
        isLoading = false;
      });
    }
  }

  Future<void> _loadGlobalEpgResults(
    String playlistId,
    String value,
    int token,
  ) async {
    try {
      final epgResults = await repository.searchEpgPrograms(
        playlistId,
        value,
        limit: 60,
      );

      if (token != _searchToken || !mounted) return;
      setState(() {
        programItems = epgResults;
        selectedProgram = epgResults.isNotEmpty ? epgResults.first : null;
        contentItems = [
          ...liveItems,
          ...movieItems,
          ...seriesItems,
          ...programItems,
        ];
      });
    } catch (_) {
      if (token != _searchToken || !mounted) return;
      setState(() {
        programItems = [];
        selectedProgram = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final inputMode = context.watch<InputModeController>();

    if (widget.contentType == null) {
      return _buildGlobalSearchScaffold(context, inputMode);
    }

    return Scaffold(
      appBar: AppBar(
        title: isSearching
            ? TextField(
                controller: searchController,
                focusNode: searchFocusNode,
                decoration: InputDecoration(
                  hintText: _getSearchHint(context),
                  border: InputBorder.none,
                ),
                autofocus: inputMode.showKeyboardOnFocus,
                onChanged: _performSearch,
              )
            : SelectableText(
                _getScreenTitle(context),
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
        actions: [
          if (isSearching)
            IconButton(icon: Icon(Icons.clear), onPressed: stopSearch)
          else
            IconButton(icon: Icon(Icons.search), onPressed: startSearch),
        ],
      ),
      body: _buildBody(context),
    );
  }

  Widget _buildGlobalSearchScaffold(
    BuildContext context,
    InputModeController inputMode,
  ) {
    final query = searchController.text.trim();

    return Scaffold(
      backgroundColor: Colors.black,
      resizeToAvoidBottomInset: false,
      body: Container(
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment.topCenter,
            radius: 1.0,
            colors: [Color(0xFF1D2D44), Color(0xFF02030A), Colors.black],
          ),
        ),
        child: SafeArea(
          child: Column(
            children: [
              SizedBox(
                height: 132,
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    const micSize = 64.0;
                    const micTop = 24.0;
                    const searchGap = 42.0;
                    const searchRight = 52.0;
                    final micLeft = constraints.maxWidth / 2 - micSize / 2;
                    final searchLeft = micLeft + micSize + searchGap;
                    final searchWidth =
                        (constraints.maxWidth - searchLeft - searchRight).clamp(
                          260.0,
                          460.0,
                        );
                    return Stack(
                      children: [
                        Positioned(
                          left: 52,
                          top: 18,
                          child: Row(
                            children: [
                              Container(
                                width: 56,
                                height: 56,
                                decoration: BoxDecoration(
                                  color: Colors.white.withValues(alpha: 0.06),
                                  borderRadius: BorderRadius.circular(18),
                                ),
                                child: IconButton(
                                  icon: const Icon(
                                    Icons.arrow_back_rounded,
                                    color: Colors.white,
                                    size: 34,
                                  ),
                                  onPressed: () => Navigator.maybePop(context),
                                ),
                              ),
                              const SizedBox(width: 26),
                              SizedBox(
                                height: 60,
                                width: 170,
                                child: OverflowBox(
                                  maxHeight: 110,
                                  child: Image.asset(
                                    'assets/images/App_Logo.png',
                                    height: 110,
                                    fit: BoxFit.contain,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        Positioned(
                          left: searchLeft,
                          top: 26,
                          child: SizedBox(
                            width: searchWidth,
                            height: 62,
                            child: TextField(
                              controller: searchController,
                              focusNode: searchFocusNode,
                              autofocus: inputMode.showKeyboardOnFocus,
                              onChanged: _performSearch,
                              style: const TextStyle(
                                color: Color(0xFF25252A),
                                fontSize: 21,
                                fontWeight: FontWeight.w500,
                              ),
                              decoration: InputDecoration(
                                filled: true,
                                fillColor: const Color(0xFFE4E4E8),
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(4),
                                  borderSide: BorderSide.none,
                                ),
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 30,
                                  vertical: 18,
                                ),
                                hintText: 'Speak to search',
                                hintStyle: const TextStyle(
                                  color: Color(0xFF66666A),
                                ),
                              ),
                            ),
                          ),
                        ),
                        Positioned(
                          left: micLeft,
                          top: micTop,
                          child: Container(
                            width: micSize,
                            height: micSize,
                            decoration: const BoxDecoration(
                              shape: BoxShape.circle,
                              color: Color(0xFFD8D8DB),
                            ),
                            child: const Icon(
                              Icons.mic_rounded,
                              color: Color(0xFF4A4A4D),
                              size: 32,
                            ),
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
              if (query.isEmpty)
                Divider(height: 1, color: Colors.white.withValues(alpha: 0.34))
              else
                _globalContentFrame(
                  child: _GlobalSearchTabs(
                    currentIndex: _globalTabIndex,
                    onChanged: (index) =>
                        setState(() => _globalTabIndex = index),
                  ),
                ),
              Expanded(
                child: isLoading
                    ? const Center(
                        child: CircularProgressIndicator(
                          color: Color(0xFFC12CFF),
                        ),
                      )
                    : errorMessage != null
                    ? _buildErrorState()
                    : query.isEmpty
                    ? _buildGlobalEmptyPrompt()
                    : _buildGlobalTabContent(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildGlobalEmptyPrompt() {
    return Align(
      alignment: Alignment.topCenter,
      child: Padding(
        padding: const EdgeInsets.only(top: 32),
        child: Text(
          'Search any Channel, Movies and Series',
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.9),
            fontSize: 20,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }

  Widget _buildGlobalTabContent() {
    switch (_globalTabIndex) {
      case 0:
        return _buildGlobalGrid(liveItems, live: true);
      case 1:
        return _buildGlobalGrid(movieItems);
      case 2:
        return _buildGlobalGrid(seriesItems);
      case 3:
        return _buildProgramsTab();
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _buildGlobalGrid(List<ContentItem> items, {bool live = false}) {
    if (items.isEmpty) return _buildEmptyState();

    return _globalContentFrame(
      fillHeight: true,
      child: GridView.builder(
        padding: const EdgeInsets.fromLTRB(0, 24, 0, 32),
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: live ? 7 : 7,
          childAspectRatio: live ? 0.88 : 0.58,
          crossAxisSpacing: 14,
          mainAxisSpacing: 18,
        ),
        itemCount: items.length,
        itemBuilder: (context, index) => SizedBox.expand(
          child: _GlobalPosterResultCard(
            item: items[index],
            live: live,
            loadDisplayData: _loadDisplayData,
            onTap: () => navigateByContentType(context, items[index]),
          ),
        ),
      ),
    );
  }

  Widget _buildProgramsTab() {
    if (programItems.isEmpty) return _buildEmptyState();

    final selected = selectedProgram ?? programItems.first;
    final detail = _ProgramDisplayData.fromContent(selected);

    return _globalContentFrame(
      fillHeight: true,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(0, 24, 0, 42),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              flex: 2,
              child: ListView.builder(
                itemCount: programItems.length,
                itemBuilder: (context, index) {
                  final item = programItems[index];
                  final data = _ProgramDisplayData.fromContent(item);
                  final selected =
                      item == selectedProgram ||
                      (selectedProgram == null && index == 0);
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: _ProgramResultRow(
                      data: data,
                      selected: selected,
                      onTap: () => setState(() => selectedProgram = item),
                      onPlay: () => navigateByContentType(context, item),
                    ),
                  );
                },
              ),
            ),
            const SizedBox(width: 14),
            Expanded(child: _ProgramDetailPanel(data: detail)),
          ],
        ),
      ),
    );
  }

  Widget _globalContentFrame({required Widget child, bool fillHeight = false}) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final sideGap = constraints.maxWidth < 900
            ? 28.0
            : (constraints.maxWidth * 0.068).clamp(44.0, 92.0);
        final leftGap = constraints.maxWidth < 900
            ? sideGap
            : (sideGap - 40).clamp(44.0, sideGap);
        final rightGap = sideGap;

        return Padding(
          padding: EdgeInsets.only(left: leftGap, right: rightGap),
          child: SizedBox(
            height: fillHeight ? constraints.maxHeight : null,
            child: child,
          ),
        );
      },
    );
  }

  Widget _buildBody(BuildContext context) {
    if (isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (errorMessage != null) {
      return _buildErrorState();
    }

    return _buildContentGrid(context);
  }

  Widget _buildContentGrid(BuildContext context) {
    if (contentItems.isEmpty &&
        isSearched &&
        searchController.text.isNotEmpty) {
      return _buildEmptyState();
    }

    if (contentItems.isEmpty) {
      return _buildInitialState();
    }

    if (widget.contentType == null) {
      return ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: contentItems.length,
        separatorBuilder: (_, _) => const SizedBox(height: 10),
        itemBuilder: (context, index) =>
            _buildGlobalResultTile(context, contentItems[index]),
      );
    }

    return GridView.builder(
      padding: const EdgeInsets.all(16),
      gridDelegate: _buildGridDelegate(context),
      itemCount: contentItems.length,
      itemBuilder: (context, index) =>
          _buildContentItem(context, index, contentItems),
    );
  }

  SliverGridDelegateWithFixedCrossAxisCount _buildGridDelegate(
    BuildContext context,
  ) {
    return SliverGridDelegateWithFixedCrossAxisCount(
      crossAxisCount: ResponsiveHelper.getCrossAxisCount(context),
      childAspectRatio: 0.65,
      crossAxisSpacing: 8,
      mainAxisSpacing: 8,
    );
  }

  Widget _buildContentItem(
    BuildContext context,
    int index,
    List<ContentItem> contentItems,
  ) {
    final contentItem = contentItems[index];

    return ContentCard(
      content: contentItem,
      width: 150,
      onTap: () => navigateByContentType(context, contentItem),
    );
  }

  Widget _buildGlobalResultTile(BuildContext context, ContentItem contentItem) {
    final initialData = _SearchDisplayData.fromContent(contentItem);
    final cacheKey = '${contentItem.contentType.name}:${contentItem.id}';
    final future = _displayCache.putIfAbsent(
      cacheKey,
      () => _loadDisplayData(contentItem),
    );

    return FutureBuilder<_SearchDisplayData>(
      future: future,
      initialData: initialData,
      builder: (context, snapshot) {
        final data = snapshot.data ?? initialData;
        final color = _typeColor(contentItem);

        return TvFocusable(
          onPressed: () => navigateByContentType(context, contentItem),
          borderRadius: BorderRadius.circular(18),
          child: Card(
            margin: EdgeInsets.zero,
            color: const Color(0xFF17112A).withValues(alpha: 0.94),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(18),
              side: BorderSide(color: color.withValues(alpha: 0.45)),
            ),
            child: InkWell(
              borderRadius: BorderRadius.circular(18),
              onTap: () => navigateByContentType(context, contentItem),
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Row(
                  children: [
                    _SearchPoster(data: data, icon: _typeIcon(contentItem)),
                    const SizedBox(width: 18),
                    Expanded(
                      child: _SearchInfo(data: data, accent: color),
                    ),
                    const SizedBox(width: 12),
                    _TypeBadge(
                      label: data.isEpg
                          ? 'EPG'
                          : _typeLabel(contentItem).toUpperCase(),
                      color: color,
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Future<_SearchDisplayData> _loadDisplayData(ContentItem item) async {
    final local = _SearchDisplayData.fromContent(item);
    final repo = AppState.xtreamCodeRepository;
    if (repo == null || item.description?.startsWith('EPG •') == true) {
      return local;
    }
    if (!local.needsRemoteDetails) return local;

    try {
      switch (item.contentType) {
        case ContentType.vod:
          final info = await repo.getVodInfo(item.id);
          final details = info?['info'];
          if (details is! Map) return local;
          return local.copyWith(
            imageUrl: _firstString([
              details['movie_image'],
              details['cover_big'],
              details['cover'],
              details['image'],
            ]),
            description: _firstString([
              details['plot'],
              details['description'],
            ]),
            genre: _firstString([details['genre'], local.genre]),
            rating: _firstString([details['rating'], local.rating]),
            year: _yearFrom(
              _firstString([
                details['releasedate'],
                details['releaseDate'],
                details['year'],
              ]),
            ),
          );
        case ContentType.series:
          final response = await repo.getSeriesInfo(item.id);
          final info = response?.seriesInfo;
          if (info == null) return local;
          return local.copyWith(
            imageUrl: info.cover,
            description: info.plot,
            genre: info.genre,
            rating: info.rating,
            year: _yearFrom(info.releaseDate),
          );
        case ContentType.liveStream:
          return local;
      }
    } catch (_) {
      return local;
    }
  }

  String? _firstString(Iterable<dynamic> values) {
    for (final value in values) {
      final text = value?.toString().trim();
      if (text != null && text.isNotEmpty && text.toLowerCase() != 'null') {
        return text;
      }
    }
    return null;
  }

  String? _yearFrom(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final match = RegExp(r'(19|20)\d{2}').firstMatch(value);
    return match?.group(0);
  }

  Widget _buildInitialState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.search, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          // Burası zaten yorum satırında
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(_getEmptyStateIcon(), size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text(
            _getEmptyStateMessage(),
            style: TextStyle(fontSize: 16, color: Colors.grey),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  IconData _getEmptyStateIcon() {
    switch (widget.contentType) {
      case null:
        return Icons.travel_explore_outlined;
      case ContentType.liveStream:
        return Icons.live_tv_outlined;
      case ContentType.vod:
        return Icons.movie_outlined;
      case ContentType.series:
        return Icons.tv_outlined;
    }
  }

  String _getEmptyStateMessage() {
    switch (widget.contentType) {
      case null:
        return 'No channels, movies, series, or EPG found';
      case ContentType.liveStream:
        return context.loc.live_stream_not_found;
      case ContentType.vod:
        return context.loc.movie_not_found;
      case ContentType.series:
        return 'Dizi bulunamadı'; // Bu için localization key'ine ihtiyaç var
    }
  }

  IconData _typeIcon(ContentItem item) {
    if (item.description?.startsWith('EPG •') == true) {
      return Icons.calendar_month_rounded;
    }
    return switch (item.contentType) {
      ContentType.liveStream => Icons.live_tv_rounded,
      ContentType.vod => Icons.movie_rounded,
      ContentType.series => Icons.tv_rounded,
    };
  }

  Color _typeColor(ContentItem item) {
    if (item.description?.startsWith('EPG •') == true) {
      return const Color(0xFF00B7FF);
    }
    return switch (item.contentType) {
      ContentType.liveStream => const Color(0xFFC12CFF),
      ContentType.vod => Colors.orange,
      ContentType.series => const Color(0xFF00B7FF),
    };
  }

  String _typeLabel(ContentItem item) {
    return switch (item.contentType) {
      ContentType.liveStream => 'Live TV',
      ContentType.vod => 'Movie',
      ContentType.series => 'Series',
    };
  }

  Widget _buildErrorState() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64, color: Colors.red),
            const SizedBox(height: 16),
            Text(
              '${context.loc.error_occurred}: $errorMessage',
              style: const TextStyle(fontSize: 16, color: Colors.red),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

class _SearchDisplayData {
  final ContentType type;
  final String title;
  final String imageUrl;
  final String? description;
  final String? genre;
  final String? rating;
  final String? year;
  final bool isEpg;

  const _SearchDisplayData({
    required this.type,
    required this.title,
    required this.imageUrl,
    this.description,
    this.genre,
    this.rating,
    this.year,
    this.isEpg = false,
  });

  factory _SearchDisplayData.fromContent(ContentItem item) {
    final isEpg = item.description?.startsWith('EPG •') == true;
    final rating = switch (item.contentType) {
      ContentType.vod => _cleanRating(
        item.vodStream?.rating,
        item.vodStream?.rating5based,
      ),
      ContentType.series => _cleanRating(
        item.seriesStream?.rating,
        item.seriesStream?.rating5based,
      ),
      ContentType.liveStream => null,
    };
    return _SearchDisplayData(
      type: item.contentType,
      title: item.name,
      imageUrl: item.imagePath,
      description:
          item.description ?? item.seriesStream?.plot ?? item.vodStream?.genre,
      genre: item.seriesStream?.genre ?? item.vodStream?.genre,
      rating: rating,
      year: _yearFromStatic(item.seriesStream?.releaseDate ?? item.name),
      isEpg: isEpg,
    );
  }

  bool get needsRemoteDetails {
    if (type == ContentType.liveStream || isEpg) return false;
    return imageUrl.trim().isEmpty ||
        description == null ||
        description!.trim().isEmpty ||
        rating == null ||
        rating!.trim().isEmpty;
  }

  _SearchDisplayData copyWith({
    String? imageUrl,
    String? description,
    String? genre,
    String? rating,
    String? year,
  }) {
    return _SearchDisplayData(
      type: type,
      title: title,
      imageUrl: _prefer(imageUrl, this.imageUrl),
      description: _prefer(description, this.description),
      genre: _prefer(genre, this.genre),
      rating: _cleanRating(_prefer(rating, this.rating), null),
      year: _prefer(year, this.year),
      isEpg: isEpg,
    );
  }

  static String _prefer(String? fresh, String? fallback) {
    final trimmed = fresh?.trim();
    if (trimmed != null &&
        trimmed.isNotEmpty &&
        trimmed.toLowerCase() != 'null') {
      return trimmed;
    }
    return fallback ?? '';
  }

  static String? _cleanRating(String? raw, double? fallback) {
    final text = raw?.trim();
    if (text != null && text.isNotEmpty && text.toLowerCase() != 'null') {
      final rating = double.tryParse(text);
      if (rating != null && rating > 0) return rating.toStringAsFixed(1);
      return text;
    }
    if (fallback != null && fallback > 0) return fallback.toStringAsFixed(1);
    return null;
  }

  static String? _yearFromStatic(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final match = RegExp(r'(19|20)\d{2}').firstMatch(value);
    return match?.group(0);
  }
}

class _SearchPoster extends StatelessWidget {
  final _SearchDisplayData data;
  final IconData icon;

  const _SearchPoster({required this.data, required this.icon});

  @override
  Widget build(BuildContext context) {
    final isLive = data.type == ContentType.liveStream;
    final width = isLive ? 100.0 : 86.0;
    final height = isLive ? 72.0 : 118.0;
    final radius = BorderRadius.circular(14);

    return ClipRRect(
      borderRadius: radius,
      child: SizedBox(
        width: width,
        height: height,
        child: data.imageUrl.trim().isNotEmpty
            ? CachedNetworkImage(
                imageUrl: data.imageUrl,
                fit: isLive ? BoxFit.contain : BoxFit.cover,
                memCacheWidth: 260,
                maxWidthDiskCache: 500,
                errorWidget: (_, _, _) => _PosterFallback(icon: icon),
                placeholder: (_, _) => _PosterFallback(icon: icon),
              )
            : _PosterFallback(icon: icon),
      ),
    );
  }
}

class _PosterFallback extends StatelessWidget {
  final IconData icon;

  const _PosterFallback({required this.icon});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF2B1748), Color(0xFF112D62)],
        ),
      ),
      child: Center(
        child: Icon(icon, color: const Color(0xFF00B7FF), size: 34),
      ),
    );
  }
}

class _SearchInfo extends StatelessWidget {
  final _SearchDisplayData data;
  final Color accent;

  const _SearchInfo({required this.data, required this.accent});

  @override
  Widget build(BuildContext context) {
    final description = data.description?.trim();
    final chips = <Widget>[
      _InfoChip(label: _typeName(data.type), color: accent),
      if (data.year != null)
        _InfoChip(label: data.year!, color: Colors.white70),
      if (data.genre != null && data.genre!.trim().isNotEmpty)
        _InfoChip(label: data.genre!.trim(), color: Colors.white70),
      if (data.rating != null) _RatingChip(rating: data.rating!),
    ];

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          data.title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 18,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 8),
        Wrap(spacing: 8, runSpacing: 6, children: chips),
        const SizedBox(height: 10),
        Text(
          description != null && description.isNotEmpty
              ? description
              : 'No description available yet',
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.68),
            fontSize: 13,
            height: 1.25,
          ),
        ),
      ],
    );
  }

  static String _typeName(ContentType type) {
    return switch (type) {
      ContentType.liveStream => 'Live TV',
      ContentType.vod => 'Movie',
      ContentType.series => 'Series',
    };
  }
}

class _InfoChip extends StatelessWidget {
  final String label;
  final Color color;

  const _InfoChip({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w800),
    );
  }
}

class _RatingChip extends StatelessWidget {
  final String rating;

  const _RatingChip({required this.rating});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.star_rounded, color: Color(0xFFFFC857), size: 15),
        const SizedBox(width: 3),
        Text(
          rating,
          style: const TextStyle(
            color: Color(0xFFFFC857),
            fontSize: 12,
            fontWeight: FontWeight.w900,
          ),
        ),
      ],
    );
  }
}

class _TypeBadge extends StatelessWidget {
  final String label;
  final Color color;

  const _TypeBadge({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: color.withValues(alpha: 0.18),
        border: Border.all(color: color.withValues(alpha: 0.34)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

class _GlobalSearchTabs extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onChanged;

  const _GlobalSearchTabs({
    required this.currentIndex,
    required this.onChanged,
  });

  static const _tabs = ['Live Channels', 'Movies', 'Series', 'Programs'];

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            for (var i = 0; i < _tabs.length; i++)
              Expanded(
                child: TvFocusable(
                  onPressed: () => onChanged(i),
                  borderRadius: BorderRadius.circular(0),
                  child: SizedBox(
                    height: 54,
                    child: InkWell(
                      onTap: () => onChanged(i),
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: currentIndex == i
                              ? const Color(0xFF2C313B)
                              : const Color(0xFF202228),
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.08),
                          ),
                        ),
                        child: Stack(
                          alignment: Alignment.center,
                          children: [
                            Text(
                              _tabs[i],
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 17,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            if (currentIndex == i)
                              Positioned(
                                left: 0,
                                right: 0,
                                bottom: 0,
                                height: 4,
                                child: DecoratedBox(
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFE37BFF),
                                    boxShadow: [
                                      BoxShadow(
                                        color: const Color(
                                          0xFFE37BFF,
                                        ).withValues(alpha: 0.55),
                                        blurRadius: 10,
                                      ),
                                    ],
                                  ),
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
        Divider(height: 1, color: Colors.white.withValues(alpha: 0.42)),
      ],
    );
  }
}

class _GlobalPosterResultCard extends StatelessWidget {
  final ContentItem item;
  final bool live;
  final Future<_SearchDisplayData> Function(ContentItem item) loadDisplayData;
  final VoidCallback onTap;

  const _GlobalPosterResultCard({
    required this.item,
    required this.live,
    required this.loadDisplayData,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final initialData = _SearchDisplayData.fromContent(item);
    return FutureBuilder<_SearchDisplayData>(
      future: loadDisplayData(item),
      initialData: initialData,
      builder: (context, snapshot) {
        final data = snapshot.data ?? initialData;
        return SizedBox.expand(
          child: TvFocusable(
            onPressed: onTap,
            borderRadius: BorderRadius.circular(6),
            child: SizedBox.expand(
              child: InkWell(
                onTap: onTap,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      data.imageUrl.trim().isNotEmpty
                          ? CachedNetworkImage(
                              imageUrl: data.imageUrl,
                              fit: live ? BoxFit.contain : BoxFit.cover,
                              width: double.infinity,
                              height: double.infinity,
                              memCacheWidth: 320,
                              maxWidthDiskCache: 600,
                              placeholder: (_, _) => _PosterFallback(
                                icon: live
                                    ? Icons.live_tv_rounded
                                    : Icons.movie_rounded,
                              ),
                              errorWidget: (_, _, _) => _PosterFallback(
                                icon: live
                                    ? Icons.live_tv_rounded
                                    : Icons.movie_rounded,
                              ),
                            )
                          : _PosterFallback(
                              icon: live
                                  ? Icons.live_tv_rounded
                                  : item.contentType == ContentType.series
                                  ? Icons.tv_rounded
                                  : Icons.movie_rounded,
                            ),
                      Positioned.fill(
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                Colors.transparent,
                                Colors.black.withValues(alpha: 0.12),
                                Colors.black.withValues(alpha: 0.82),
                              ],
                            ),
                          ),
                        ),
                      ),
                      if (data.rating != null)
                        Positioned(
                          left: 6,
                          top: 6,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 7,
                              vertical: 3,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFF168ACB),
                              borderRadius: BorderRadius.circular(5),
                            ),
                            child: Text(
                              data.rating!,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ),
                      Positioned(
                        left: 8,
                        right: 8,
                        bottom: 10,
                        child: Text(
                          _titleWithYear(data),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 14,
                            height: 1.05,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  String _titleWithYear(_SearchDisplayData data) {
    if (data.year == null || data.title.contains(data.year!)) {
      return data.title;
    }
    return '${data.title}\n(${data.year})';
  }
}

class _ProgramDisplayData {
  final String channel;
  final String title;
  final String time;
  final String description;
  final String imageUrl;

  const _ProgramDisplayData({
    required this.channel,
    required this.title,
    required this.time,
    required this.description,
    required this.imageUrl,
  });

  factory _ProgramDisplayData.fromContent(ContentItem item) {
    final parts = (item.description ?? '').split(' • ');
    final channel = parts.length > 1 ? parts[1] : 'Live TV';
    final time = parts.length > 2 ? parts[2] : '';
    final description = parts.length > 3
        ? parts.sublist(3).join(' • ')
        : 'No programme description available.';
    return _ProgramDisplayData(
      channel: channel,
      title: item.name,
      time: time,
      description: description,
      imageUrl: item.imagePath,
    );
  }
}

class _ProgramResultRow extends StatelessWidget {
  final _ProgramDisplayData data;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onPlay;

  const _ProgramResultRow({
    required this.data,
    required this.selected,
    required this.onTap,
    required this.onPlay,
  });

  @override
  Widget build(BuildContext context) {
    return TvFocusable(
      onPressed: onPlay,
      borderRadius: BorderRadius.circular(2),
      child: InkWell(
        onTap: onTap,
        onDoubleTap: onPlay,
        child: Container(
          height: 74,
          decoration: BoxDecoration(
            color: selected ? const Color(0xFF9A061A) : const Color(0xFF17191E),
          ),
          child: Row(
            children: [
              SizedBox(
                width: 148,
                child: Padding(
                  padding: const EdgeInsets.all(8),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: data.imageUrl.trim().isNotEmpty
                        ? CachedNetworkImage(
                            imageUrl: data.imageUrl,
                            fit: BoxFit.contain,
                          )
                        : const _PosterFallback(icon: Icons.live_tv_rounded),
                  ),
                ),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 10),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        data.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        data.time,
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.72),
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ProgramDetailPanel extends StatelessWidget {
  final _ProgramDisplayData data;

  const _ProgramDetailPanel({required this.data});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(color: Color(0xFF17191E)),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(18),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 116),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                data.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 17,
                  height: 1.15,
                  fontWeight: FontWeight.w800,
                ),
              ),
              if (data.time.trim().isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  data.time,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.72),
                    fontSize: 12,
                  ),
                ),
              ],
              const SizedBox(height: 6),
              Divider(color: Colors.white.withValues(alpha: 0.25), height: 1),
              const SizedBox(height: 10),
              Text(
                data.channel,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Color(0xFFE37BFF),
                  fontSize: 13,
                  height: 1.15,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                data.description,
                maxLines: 4,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.72),
                  fontSize: 12,
                  height: 1.25,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
