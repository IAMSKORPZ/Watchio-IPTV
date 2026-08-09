import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:provider/provider.dart';
import '../../controllers/xtream_code_home_controller.dart';
import '../../core/theme/theme_manager.dart';
import '../../models/category_type.dart';
import '../../models/category_view_model.dart';
import '../../models/content_type.dart';
import '../../models/playlist_content_model.dart';
import '../../repositories/iptv_repository.dart';
import '../../repositories/user_preferences.dart';
import '../../services/app_state.dart';
import '../../services/config_service.dart';
import '../../shared/widgets/glass_panel.dart';
import '../../shared/widgets/sidebar_item.dart';
import '../../shared/widgets/poster_card.dart';
import '../../shared/widgets/watchio_header.dart';
import '../../utils/navigate_by_content_type.dart';
import '../../utils/responsive_helper.dart';
import '../../utils/firestick_performance.dart';
import '../search_screen.dart';
import '../shared/catalog_setup_dialog.dart';

class XtreamMoviesScreen extends StatefulWidget {
  const XtreamMoviesScreen({super.key});

  @override
  State<XtreamMoviesScreen> createState() => _XtreamMoviesScreenState();
}

class _XtreamMoviesScreenState extends State<XtreamMoviesScreen> {
  CategoryViewModel? _selectedCategory;
  final List<ContentItem> _currentItems = [];
  bool _isMoreLoading = false;
  bool _hasMore = true;
  int _currentOffset = 0;
  static const int _pageSize = 60;
  final Map<String, int> _categoryCounts = {};
  final Set<String> _hiddenCategoryIds = {};
  String _sortOrder = 'server';
  bool _showPoster = true;
  bool _showTitle = true;
  bool _showRating = true;
  String _posterSize = 'normal';

  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_scrollListener);

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final controller = Provider.of<XtreamCodeHomeController>(
        context,
        listen: false,
      );
      await _loadSetupPreferences();
      if (!mounted) return;
      final categories = _visibleCategories(controller);
      if (categories.isNotEmpty) {
        final initialCategory = _findReleasedCategory(categories);
        // Load counts in bulk
        final counts = await controller.getAllCategoryCounts(CategoryType.vod);
        if (mounted) {
          final savedSort = await UserPreferences.getCatalogSortOrder('movies');
          if (!mounted) return;
          setState(() {
            _categoryCounts.addAll(counts);
            _sortOrder = savedSort ?? 'server';
          });
          await _onCategorySelected(initialCategory);
        }
      }
    });
  }

  Future<void> _loadSetupPreferences() async {
    final playlist = AppState.currentPlaylist;
    final hidden = playlist == null
        ? <String>[]
        : await UserPreferences.getCatalogHiddenCategoryIds(
            'movies',
            playlist.id,
          );
    final showPoster = await UserPreferences.getCatalogShowPoster('movies');
    final showTitle = await UserPreferences.getCatalogShowTitle('movies');
    final showRating = await UserPreferences.getCatalogShowRating('movies');
    final posterSize = await UserPreferences.getCatalogPosterSize('movies');
    if (!mounted) return;
    setState(() {
      _hiddenCategoryIds
        ..clear()
        ..addAll(hidden);
      _showPoster = showPoster;
      _showTitle = showTitle;
      _showRating = showRating;
      _posterSize = posterSize;
    });
  }

  @override
  void dispose() {
    _scrollController.removeListener(_scrollListener);
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollListener() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      if (!_isMoreLoading && _hasMore) {
        _loadMoreItems();
      }
    }
  }

  Future<void> _onCategorySelected(CategoryViewModel category) async {
    setState(() {
      _selectedCategory = category;
      _currentItems.clear();
      _currentOffset = 0;
      _hasMore = true;
      _isMoreLoading = true;
    });

    await _loadMoreItems();
  }

  Future<void> _loadMoreItems() async {
    if (_selectedCategory == null) return;

    setState(() => _isMoreLoading = true);

    try {
      final controller = Provider.of<XtreamCodeHomeController>(
        context,
        listen: false,
      );
      final newItems = await controller.getCategoryItems(
        _selectedCategory!.category,
        top: _pageSize,
        offset: _currentOffset,
      );

      if (mounted) {
        setState(() {
          _currentItems.addAll(newItems);
          _sortLoadedItems();
          _currentOffset += newItems.length;
          _isMoreLoading = false;
          if (newItems.length < _pageSize) {
            _hasMore = false;
          }
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isMoreLoading = false);
    }
  }

  void _sortLoadedItems() {
    int itemNumber(ContentItem item) => int.tryParse(item.id) ?? 0;
    double ratingValue(ContentItem item) => item.vodStream?.rating5based ?? 0;
    int yearValue(ContentItem item) => RegExp(r'(19|20)\d{2}')
        .allMatches(item.name)
        .map((match) => int.tryParse(match.group(0) ?? '') ?? 0)
        .fold<int>(0, (best, year) => year > best ? year : best);
    switch (_sortOrder) {
      case 'server':
        break;
      case 'az':
        _currentItems.sort(
          (a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()),
        );
        break;
      case 'za':
        _currentItems.sort(
          (a, b) => b.name.toLowerCase().compareTo(a.name.toLowerCase()),
        );
        break;
      case 'recent':
        _currentItems.sort((a, b) => itemNumber(b).compareTo(itemNumber(a)));
        break;
      case 'rating':
        _currentItems.sort((a, b) => ratingValue(b).compareTo(ratingValue(a)));
        break;
      case 'year':
        _currentItems.sort((a, b) => yearValue(b).compareTo(yearValue(a)));
        break;
      default:
        break;
    }
  }

  CategoryViewModel _findReleasedCategory(List<CategoryViewModel> categories) {
    return categories.firstWhere(
      (category) => _categoryNameHas(category, const ['HOLLYWOOD', 'RELEASED']),
      orElse: () => categories.firstWhere(
        (category) => _categoryNameHas(category, const ['RELEASED']),
        orElse: () => categories.first,
      ),
    );
  }

  bool _categoryNameHas(CategoryViewModel category, List<String> terms) {
    final name = category.category.categoryName.toUpperCase();
    return terms.every(name.contains);
  }

  Future<void> _showSetupDialog() async {
    final playlist = AppState.currentPlaylist;
    if (playlist == null) return;
    final controller = Provider.of<XtreamCodeHomeController>(
      context,
      listen: false,
    );
    final settings = await showCatalogSetupDialog(
      context: context,
      title: 'Movies',
      categories: controller.movieCategories,
      initialSettings: CatalogSetupSettings(
        hiddenCategoryIds: _hiddenCategoryIds,
        showPoster: _showPoster,
        showTitle: _showTitle,
        showRating: _showRating,
        posterSize: _posterSize,
        sortOrder: _sortOrder,
      ),
    );
    if (settings == null || !mounted) return;
    await Future.wait([
      UserPreferences.setCatalogHiddenCategoryIds(
        'movies',
        playlist.id,
        settings.hiddenCategoryIds.toList(),
      ),
      UserPreferences.setCatalogShowPoster('movies', settings.showPoster),
      UserPreferences.setCatalogShowTitle('movies', settings.showTitle),
      UserPreferences.setCatalogShowRating('movies', settings.showRating),
      UserPreferences.setCatalogPosterSize('movies', settings.posterSize),
      UserPreferences.setCatalogSortOrder('movies', settings.sortOrder),
    ]);
    if (!mounted) return;
    setState(() {
      _hiddenCategoryIds
        ..clear()
        ..addAll(settings.hiddenCategoryIds);
      _showPoster = settings.showPoster;
      _showTitle = settings.showTitle;
      _showRating = settings.showRating;
      _posterSize = settings.posterSize;
      _sortOrder = settings.sortOrder;
      _sortLoadedItems();
    });

    if (_selectedCategory != null &&
        _hiddenCategoryIds.contains(_selectedCategory!.category.categoryId)) {
      final visible = _visibleCategories(controller);
      if (visible.isNotEmpty) await _onCategorySelected(visible.first);
    }
  }

  List<CategoryViewModel> _visibleCategories(
    XtreamCodeHomeController controller,
  ) {
    return controller.movieCategories
        .where(
          (category) =>
              !_canHideCategory(category.category.categoryId) ||
              !_hiddenCategoryIds.contains(category.category.categoryId),
        )
        .toList();
  }

  bool _canHideCategory(String categoryId) {
    return categoryId != IptvRepository.virtualAll &&
        categoryId != IptvRepository.virtualFavorites &&
        categoryId != IptvRepository.virtualHistory;
  }

  int _gridCrossAxisCount() {
    return switch (_posterSize) {
      'compact' => 6,
      'large' => 4,
      _ => 5,
    };
  }

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    final themeManager = context.watch<ThemeManager>();
    final homeBg = config.backgrounds.home;
    const moviesAccent = Color(0xFFA855F7);
    const moviesGlow = Color(0xFFC45CFF);

    return Consumer<XtreamCodeHomeController>(
      builder: (context, controller, child) {
        if (controller.movieCategories.isEmpty) {
          return _buildLibraryNotLoaded(controller);
        }

        final deviceType = ResponsiveHelper.getDeviceType(context);
        final isDesktop = deviceType == DeviceType.desktop;

        return Scaffold(
          backgroundColor: const Color(0xFF050812),
          body: Container(
            width: double.infinity,
            height: double.infinity,
            decoration: BoxDecoration(
              color: const Color(0xFF050812),
              image: DecorationImage(
                image: (themeManager.showBackgroundImage && homeBg.isNotEmpty)
                    ? perfNetworkImage(homeBg)
                    : const AssetImage('assets/images/background.png')
                          as ImageProvider,
                fit: BoxFit.cover,
              ),
            ),
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    const Color(0xFF050812).withValues(alpha: 0.2),
                    const Color(0xFF050812).withValues(alpha: 0.6),
                    const Color(0xFF050812).withValues(alpha: 0.9),
                  ],
                ),
              ),
              child: Column(
                children: [
                  WatchioHeader(
                    isCompact: true,
                    accentColor: moviesAccent,
                    onBack: () => controller.onNavigationTap(0),
                    onSearch: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) =>
                            SearchScreen(contentType: ContentType.vod),
                      ),
                    ),
                    onSettings: () => controller.onNavigationTap(5),
                    onSetup: _showSetupDialog,
                    onRefresh: _showSetupDialog,
                  ),
                  Expanded(
                    child: Row(
                      children: [
                        // Left Sidebar: Categories
                        Container(
                          width: isDesktop ? 200 : 250,
                          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                          child: GlassPanel(
                            opacity: 0.1,
                            blur: 20,
                            gradient: contentPanelGradientOf(context),
                            child: ListView.separated(
                              padding: const EdgeInsets.all(8),
                              itemCount: _visibleCategories(controller).length,
                              separatorBuilder: (_, _) =>
                                  const SizedBox(height: 4),
                              itemBuilder: (context, index) {
                                final category = _visibleCategories(
                                  controller,
                                )[index];
                                final isSelected =
                                    _selectedCategory?.category.categoryId ==
                                    category.category.categoryId;
                                return SidebarItem(
                                  icon: _getCategoryIcon(
                                    category.category.categoryId,
                                  ),
                                  label: category.category.categoryName,
                                  selected: isSelected,
                                  accentColor: moviesAccent,
                                  count:
                                      _categoryCounts[category
                                          .category
                                          .categoryId],
                                  onTap: () {
                                    if (!isSelected) {
                                      _onCategorySelected(category);
                                      _scrollController.jumpTo(0);
                                    }
                                  },
                                );
                              },
                            ),
                          ),
                        ),

                        // Right Grid: Content
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.fromLTRB(0, 0, 16, 16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Padding(
                                  padding: const EdgeInsets.only(
                                    left: 8.0,
                                    bottom: 12,
                                  ),
                                  child: Text(
                                    _selectedCategory?.category.categoryName ??
                                        '',
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleLarge
                                        ?.copyWith(
                                          fontWeight: FontWeight.w900,
                                          color: Colors.white,
                                          letterSpacing: 1.1,
                                        ),
                                  ),
                                ),
                                Expanded(
                                  child: LayoutBuilder(
                                    builder: (context, constraints) {
                                      final crossAxisCount =
                                          _gridCrossAxisCount();

                                      return GridView.builder(
                                        controller: _scrollController,
                                        padding: const EdgeInsets.fromLTRB(
                                          10,
                                          12,
                                          10,
                                          24,
                                        ),
                                        scrollCacheExtent:
                                            const ScrollCacheExtent.pixels(900),
                                        gridDelegate:
                                            SliverGridDelegateWithFixedCrossAxisCount(
                                              crossAxisCount: crossAxisCount,
                                              childAspectRatio:
                                                  0.58, // poster + title room
                                              crossAxisSpacing: 16,
                                              mainAxisSpacing: 20,
                                            ),
                                        itemCount:
                                            _currentItems.length +
                                            (_isMoreLoading ? 1 : 0),
                                        itemBuilder: (context, index) {
                                          if (index < _currentItems.length) {
                                            final item = _currentItems[index];
                                            return KeyedSubtree(
                                              key: ValueKey(item.id),
                                              child: PosterCard(
                                                title: item.name,
                                                imageUrl: item.imagePath,
                                                rating: _showRating
                                                    ? item.vodStream?.rating
                                                    : null,
                                                showImage: _showPoster,
                                                showTitle: _showTitle,
                                                showRating: _showRating,
                                                accentColor: moviesAccent,
                                                glowColor: moviesGlow,
                                                onTap: () =>
                                                    navigateByContentType(
                                                      context,
                                                      item,
                                                    ),
                                              ),
                                            );
                                          } else {
                                            return Center(
                                              child: CircularProgressIndicator(
                                                color: moviesAccent,
                                              ),
                                            );
                                          }
                                        },
                                      );
                                    },
                                  ),
                                ),
                              ],
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
        );
      },
    );
  }

  Widget _buildLibraryNotLoaded(XtreamCodeHomeController controller) {
    return Scaffold(
      backgroundColor: const Color(0xFF050812),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.cloud_download_outlined,
                color: Color(0xFF00B7FF),
                size: 56,
              ),
              const SizedBox(height: 16),
              const Text(
                'Movie library not loaded yet',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 22,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Use library refresh when it is added. Home now opens without preloading movies.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white60),
              ),
              const SizedBox(height: 20),
              ElevatedButton.icon(
                onPressed: () => controller.onNavigationTap(0),
                icon: const Icon(Icons.home_rounded),
                label: const Text('BACK HOME'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  IconData _getCategoryIcon(String categoryId) {
    if (categoryId == IptvRepository.virtualAll) {
      return Icons.all_inclusive_rounded;
    }
    if (categoryId == IptvRepository.virtualFavorites) {
      return Icons.favorite_rounded;
    }
    if (categoryId == IptvRepository.virtualHistory) {
      return Icons.history_rounded;
    }
    return Icons.movie_outlined;
  }
}
