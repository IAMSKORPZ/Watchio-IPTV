import 'package:watchio/models/category_view_model.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/paged_result.dart';
import 'package:watchio/models/playlist_content_model.dart';
import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/repositories/m3u_repository.dart';
import 'package:watchio/services/app_state.dart';
import 'package:watchio/services/performance_service.dart';
import '../models/category_type.dart';

class ContentService {
  static const int defaultPageSize = 60;

  Future<List<ContentItem>> fetchContentByCategory(
    CategoryViewModel category,
  ) async {
    final page = await fetchContentPageByCategory(category);
    return page.items;
  }

  Future<PagedResult<ContentItem>> fetchContentPageByCategory(
    CategoryViewModel category, {
    int page = 0,
    int pageSize = defaultPageSize,
  }) async {
    final categoryId = category.category.categoryId;
    return PerformanceService.track(
      'category_load',
      () async {
        final items = switch (AppState.currentPlaylist!.type) {
          PlaylistType.xtream => await _fetchXtreamContent(
              category.category.type,
              categoryId,
              page: page,
              pageSize: pageSize,
            ),
          PlaylistType.m3u => await _fetchM3uContent(
              category.category.type,
              categoryId,
              page: page,
              pageSize: pageSize,
            ),
          PlaylistType.stalker =>
            <ContentItem>[], // TODO: Implement Stalker content fetching
        };
        return PagedResult(
          items: items,
          page: page,
          pageSize: pageSize,
          hasNextPage: items.length == pageSize,
        );
      },
      metadata: {
        'categoryId': categoryId,
        'page': page,
        'pageSize': pageSize,
      },
    );
  }

  Future<List<ContentItem>> _fetchXtreamContent(
    CategoryType type,
    String categoryId, {
    required int page,
    required int pageSize,
  }) async {
    final repository = AppState.xtreamCodeRepository!;
    final offset = page * pageSize;
    switch (type) {
      case CategoryType.live:
        return _fetchGenericContent(
          () => repository.getLiveChannelsByCategoryId(
            categoryId: categoryId,
            top: pageSize,
            offset: offset,
          ),
          (item) => ContentItem(
            item.streamId,
            item.name,
            item.streamIcon,
            ContentType.liveStream,
            liveStream: item,
          ),
          'Canli kanallar yuklenirken hata',
        );
      case CategoryType.vod:
        return _fetchGenericContent(
          () => repository.getMovies(
            categoryId: categoryId,
            top: pageSize,
            offset: offset,
          ),
          (item) => ContentItem(
            item.streamId,
            item.name,
            item.streamIcon,
            ContentType.vod,
            containerExtension: item.containerExtension,
            vodStream: item,
          ),
          'Filmler yuklenirken hata',
        );
      case CategoryType.series:
        return _fetchGenericContent(
          () => repository.getSeries(
            categoryId: categoryId,
            top: pageSize,
            offset: offset,
          ),
          (item) => ContentItem(
            item.seriesId,
            item.name,
            item.cover ?? '',
            ContentType.series,
            seriesStream: item,
          ),
          'Diziler yuklenirken hata',
        );
    }
  }

  Future<List<ContentItem>> _fetchM3uContent(
    CategoryType type,
    String categoryId, {
    required int page,
    required int pageSize,
  }) async {
    final repository = M3uRepository();
    final offset = page * pageSize;
    switch (type) {
      case CategoryType.live:
        return _fetchGenericContent(
          () => repository.getM3uItemsByCategoryId(
            categoryId: categoryId,
            top: pageSize,
            offset: offset,
            contentType: ContentType.liveStream,
          ),
          (item) => ContentItem(
            item.url,
            item.name ?? 'NO NAME',
            item.tvgLogo ?? '',
            ContentType.liveStream,
            m3uItem: item,
          ),
          'M3U canli kanallar yuklenirken hata',
        );
      case CategoryType.vod:
        return _fetchGenericContent(
          () => repository.getM3uItemsByCategoryId(
            categoryId: categoryId,
            top: pageSize,
            offset: offset,
            contentType: ContentType.vod,
          ),
          (item) => ContentItem(
            item.url,
            item.name ?? 'NO NAME',
            item.tvgLogo ?? '',
            ContentType.vod,
            m3uItem: item,
          ),
          'M3U filmler yuklenirken hata',
        );
      case CategoryType.series:
        return _fetchGenericContent(
          () => repository.getSeriesByCategoryId(
            categoryId: categoryId,
            top: pageSize,
            offset: offset,
          ),
          (item) => ContentItem(
            item.seriesId,
            item.name,
            '',
            ContentType.series,
          ),
          'M3U diziler yuklenirken hata',
        );
    }
  }

  Future<List<ContentItem>> _fetchGenericContent<T>(
    Future<List<T>?> Function() fetchFunction,
    ContentItem Function(T) mapper,
    String errorMessage,
  ) async {
    try {
      final result = await fetchFunction();
      if (result == null) return <ContentItem>[];
      return result.map(mapper).toList();
    } catch (e) {
      throw Exception('$errorMessage: $e');
    }
  }
}
