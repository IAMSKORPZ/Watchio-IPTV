import 'dart:async';

import 'package:watchio/models/api_response.dart';
import 'package:flutter/material.dart';
import 'package:watchio/l10n/localization_extension.dart';
import 'package:watchio/models/category.dart';
import 'package:watchio/models/category_type.dart';
import 'package:watchio/models/category_view_model.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/playlist_content_model.dart';
import 'package:watchio/repositories/iptv_repository.dart';
import 'package:watchio/services/app_state.dart';
import 'package:watchio/services/cache_metadata_service.dart';
import '../screens/xtream-codes/xtream_code_data_loader_screen.dart';

class XtreamCodeHomeController extends ChangeNotifier {
  late PageController _pageController;
  final IptvRepository? _repository;

  ApiResponse? _userInfo;
  int _currentIndex = 0;
  final bool _isLoading = false;

  final List<CategoryViewModel> _liveCategories = [];
  final List<CategoryViewModel> _movieCategories = [];
  final List<CategoryViewModel> _seriesCategories = [];

  final Set<String> _hiddenMovieCategoryIds = {};
  final Set<String> _hiddenSeriesCategoryIds = {};
  final Set<CategoryType> _refreshingTypes = {};
  final Map<CategoryType, DateTime> _lastUpdatedByType = {};
  final Map<CategoryType, int> _cachedItemCountByType = {};
  final Map<CategoryType, String?> _lastErrorByType = {};
  final Map<CategoryType, double> _refreshProgressByType = {};
  final CacheMetadataService _cacheMetadataService = CacheMetadataService();

  ApiResponse? get userInfo => _userInfo;
  Set<String> get hiddenMovieCategoryIds => _hiddenMovieCategoryIds;
  Set<String> get hiddenSeriesCategoryIds => _hiddenSeriesCategoryIds;

  void toggleMovieCategoryVisibility(String categoryId) {
    if (_hiddenMovieCategoryIds.contains(categoryId)) {
      _hiddenMovieCategoryIds.remove(categoryId);
    } else {
      _hiddenMovieCategoryIds.add(categoryId);
    }
    notifyListeners();
  }

  void toggleSeriesCategoryVisibility(String categoryId) {
    if (_hiddenSeriesCategoryIds.contains(categoryId)) {
      _hiddenSeriesCategoryIds.remove(categoryId);
    } else {
      _hiddenSeriesCategoryIds.add(categoryId);
    }
    notifyListeners();
  }

  List<CategoryViewModel> get visibleMovieCategories => _movieCategories
      .where((c) => !_hiddenMovieCategoryIds.contains(c.category.categoryId))
      .toList();

  List<CategoryViewModel> get visibleSeriesCategories => _seriesCategories
      .where((c) => !_hiddenSeriesCategoryIds.contains(c.category.categoryId))
      .toList();

  PageController get pageController => _pageController;
  int get currentIndex => _currentIndex;
  bool get isLoading => _isLoading;
  List<CategoryViewModel>? get liveCategories => _liveCategories;
  List<CategoryViewModel> get movieCategories => _movieCategories;
  List<CategoryViewModel> get seriesCategories => _seriesCategories;
  bool isRefreshing(CategoryType type) => _refreshingTypes.contains(type);
  DateTime? lastUpdated(CategoryType type) => _lastUpdatedByType[type];
  int cachedItemCount(CategoryType type) => _cachedItemCountByType[type] ?? 0;
  String? lastRefreshError(CategoryType type) => _lastErrorByType[type];
  bool isCacheStale(CategoryType type) {
    final metadata = CacheMetadata(
      playlistId: _repository?.playlistId ?? '',
      section: CacheSectionMapping.fromCategoryType(type),
      lastUpdated: _lastUpdatedByType[type],
      lastSuccess: _lastUpdatedByType[type],
      itemCount: cachedItemCount(type),
      lastError: _lastErrorByType[type],
    );
    return _cacheMetadataService.isStale(metadata);
  }

  double refreshProgress(CategoryType type) =>
      _refreshProgressByType[type] ?? 0;

  XtreamCodeHomeController([bool _ = false])
    : _repository = AppState.xtreamCodeRepository {
    _pageController = PageController();
    unawaited(_loadCacheMetadata());
    unawaited(_loadAccountInfo());
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void onNavigationTap(int index) {
    _currentIndex = index;
    notifyListeners();
    if (_pageController.hasClients) {
      _pageController.animateToPage(
        index,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    }
  }

  Future<void> openContentSection(int index, CategoryType type) async {
    await loadCachedCategoriesByType(type);
    onNavigationTap(index);
  }

  Future<void> _loadAccountInfo() async {
    if (_repository == null) return;

    final cachedInfo = await _repository.getCachedPlayerInfo();
    if (cachedInfo != null) {
      _userInfo = cachedInfo;
      notifyListeners();
    }

    final freshInfo = await _repository.getPlayerInfo(forceRefresh: true);
    if (freshInfo != null) {
      await _cacheMetadataService.markSuccess(
        playlistId: _repository.playlistId,
        section: CacheSection.account,
        itemCount: 1,
      );
      _userInfo = freshInfo;
      notifyListeners();
    } else {
      await _cacheMetadataService.markFailure(
        playlistId: _repository.playlistId,
        section: CacheSection.account,
        error: 'Account refresh failed',
      );
    }
  }

  Future<void> _loadCacheMetadata() async {
    if (_repository == null) return;

    for (final type in const [
      CategoryType.live,
      CategoryType.vod,
      CategoryType.series,
    ]) {
      final section = CacheSectionMapping.fromCategoryType(type);
      final metadata = await _cacheMetadataService.getSection(
        _repository.playlistId,
        section,
      );
      final dbCount = await _repository.getCachedItemCount(type);
      final count = dbCount > 0 ? dbCount : metadata.itemCount;

      if (metadata.lastUpdated != null) {
        _lastUpdatedByType[type] = metadata.lastUpdated!;
      }
      _cachedItemCountByType[type] = count;
      _lastErrorByType[type] = metadata.lastError;
    }

    notifyListeners();
  }

  Future<void> loadCachedCategoriesByType(CategoryType type) async {
    if (_repository == null) return;

    final cachedCategories = await _repository.getCachedCategories(type);
    final target = switch (type) {
      CategoryType.live => _liveCategories,
      CategoryType.vod => _movieCategories,
      CategoryType.series => _seriesCategories,
    };

    target.clear();
    if (cachedCategories.isNotEmpty) {
      _addVirtualCategories(type, target);
      target.addAll(
        cachedCategories.map(
          (category) =>
              CategoryViewModel(category: category, contentItems: const []),
        ),
      );
    }
    notifyListeners();
  }

  Future<bool> refreshSection(CategoryType type) async {
    if (_repository == null || _refreshingTypes.contains(type)) return false;

    _refreshingTypes.add(type);
    _refreshProgressByType[type] = 0.03;
    notifyListeners();

    try {
      switch (type) {
        case CategoryType.live:
          await _repository.getLiveCategories(forceRefresh: true);
          _setRefreshProgress(type, 0.12);
          await _repository.importLiveStreamsStreamed(
            onProgress: (_) => _advanceRefreshProgress(type),
          );
          break;
        case CategoryType.vod:
          await _repository.getVodCategories(forceRefresh: true);
          _setRefreshProgress(type, 0.12);
          await _repository.importMoviesStreamed(
            onProgress: (_) => _advanceRefreshProgress(type),
          );
          break;
        case CategoryType.series:
          await _repository.getSeriesCategories(forceRefresh: true);
          _setRefreshProgress(type, 0.12);
          await _repository.importSeriesStreamed(
            onProgress: (_) => _advanceRefreshProgress(type),
          );
          break;
      }

      _setRefreshProgress(type, 1);
      final itemCount = await _repository.getCachedItemCount(type);
      await _cacheMetadataService.markSuccess(
        playlistId: _repository.playlistId,
        section: CacheSectionMapping.fromCategoryType(type),
        itemCount: itemCount,
      );
      _lastUpdatedByType[type] = DateTime.now();
      _cachedItemCountByType[type] = itemCount;
      _lastErrorByType.remove(type);
      await loadCachedCategoriesByType(type);
      return true;
    } catch (e) {
      await _cacheMetadataService.markFailure(
        playlistId: _repository.playlistId,
        section: CacheSectionMapping.fromCategoryType(type),
        error: e,
      );
      _lastErrorByType[type] = e.toString();
      debugPrint('Section refresh failed (${type.value}): $e');
      return false;
    } finally {
      _refreshingTypes.remove(type);
      _refreshProgressByType.remove(type);
      notifyListeners();
    }
  }

  void _setRefreshProgress(CategoryType type, double value) {
    _refreshProgressByType[type] = value.clamp(0, 1);
    notifyListeners();
  }

  void _advanceRefreshProgress(CategoryType type) {
    final current = _refreshProgressByType[type] ?? 0.12;
    if (current >= 0.94) return;
    _refreshProgressByType[type] = (current + 0.025).clamp(0, 0.94);
    notifyListeners();
  }

  void onPageChanged(int index) {
    _currentIndex = index;
    notifyListeners();
  }

  String getPageTitle(BuildContext context) {
    try {
      switch (currentIndex) {
        case 0:
          return context.loc.home;
        case 1:
          return context.loc.history;
        case 2:
          return context.loc.live_streams;
        case 3:
          return context.loc.movies;
        case 4:
          return context.loc.series_plural;
        case 5:
          return context.loc.settings;
        default:
          return 'Watchio IPTV';
      }
    } catch (_) {
      return 'Watchio IPTV';
    }
  }

  void _addVirtualCategories(CategoryType type, List<CategoryViewModel> list) {
    final playlistId = AppState.currentPlaylist?.id ?? '';

    // 1. All
    list.add(
      CategoryViewModel(
        category: Category(
          categoryId: IptvRepository.virtualAll,
          categoryName: _getAllLabel(type),
          parentId: 0,
          playlistId: playlistId,
          type: type,
        ),
        contentItems: [],
      ),
    );

    // 2. Favorites
    list.add(
      CategoryViewModel(
        category: Category(
          categoryId: IptvRepository.virtualFavorites,
          categoryName: 'FAVOURITES',
          parentId: 0,
          playlistId: playlistId,
          type: type,
        ),
        contentItems: [],
      ),
    );

    // 3. History
    list.add(
      CategoryViewModel(
        category: Category(
          categoryId: IptvRepository.virtualHistory,
          categoryName: 'HISTORY',
          parentId: 0,
          playlistId: playlistId,
          type: type,
        ),
        contentItems: [],
      ),
    );
  }

  String _getAllLabel(CategoryType type) {
    switch (type) {
      case CategoryType.live:
        return 'ALL CHANNELS';
      case CategoryType.vod:
        return 'ALL MOVIES';
      case CategoryType.series:
        return 'ALL SERIES';
    }
  }

  void refresh() => notifyListeners();

  Future<List<ContentItem>> getCategoryItems(
    Category category, {
    int top = 60,
    int offset = 0,
  }) async {
    if (_repository == null) return [];

    switch (category.type) {
      case CategoryType.live:
        final streams = await _repository.getLiveChannelsByCategoryId(
          categoryId: category.categoryId,
          top: top,
          offset: offset,
        );
        return streams
                ?.map(
                  (x) => ContentItem(
                    x.streamId,
                    x.name,
                    x.streamIcon,
                    ContentType.liveStream,
                    liveStream: x,
                  ),
                )
                .toList() ??
            [];
      case CategoryType.vod:
        final movies = await _repository.getMovies(
          categoryId: category.categoryId,
          top: top,
          offset: offset,
        );
        return movies
                ?.map(
                  (x) => ContentItem(
                    x.streamId,
                    x.name,
                    x.streamIcon,
                    ContentType.vod,
                    containerExtension: x.containerExtension,
                    vodStream: x,
                  ),
                )
                .toList() ??
            [];
      case CategoryType.series:
        final series = await _repository.getSeries(
          categoryId: category.categoryId,
          top: top,
          offset: offset,
        );
        return series
                ?.map(
                  (x) => ContentItem(
                    x.seriesId,
                    x.name,
                    x.cover ?? '',
                    ContentType.series,
                    seriesStream: x,
                  ),
                )
                .toList() ??
            [];
    }
  }

  Future<int> getCategoryItemCount(Category category) async {
    if (_repository == null) return 0;
    return await _repository.getItemCountByCategory(
      category.categoryId,
      category.type,
    );
  }

  Future<Map<String, int>> getAllCategoryCounts(CategoryType type) async {
    if (_repository == null) return {};
    return await _repository.getAllCategoryCounts(type);
  }

  void refreshAllData(BuildContext context) {
    if (AppState.currentPlaylist == null) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (context) => XtreamCodeDataLoaderScreen(
          playlist: AppState.currentPlaylist!,
          refreshAll: true,
        ),
      ),
    );
  }
}
