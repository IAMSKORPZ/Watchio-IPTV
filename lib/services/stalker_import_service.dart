import 'package:watchio/models/import_progress_model.dart';
import 'package:watchio/models/live_stream.dart';
import 'package:watchio/models/series.dart';
import 'package:watchio/models/stalker_provider_config.dart';
import 'package:watchio/models/vod_streams.dart';
import 'package:watchio/database/database.dart';
import 'package:watchio/repositories/search_repository.dart';
import 'package:watchio/services/service_locator.dart';
import 'package:watchio/services/stalker_api_service.dart';
import 'package:watchio/utils/type_convertions.dart';

class StalkerImportService {
  final StalkerApiService api;
  final AppDatabase database;
  final SearchRepository searchRepository;

  StalkerImportService({
    StalkerApiService? api,
    AppDatabase? database,
    SearchRepository? searchRepository,
  })  : api = api ?? StalkerApiService(),
        database = database ?? getIt<AppDatabase>(),
        searchRepository = searchRepository ?? SearchRepository(database: database);

  Stream<ImportProgressModel> importIncremental({
    required String playlistId,
    required StalkerProviderConfig config,
    required String token,
    required String type,
    String? categoryId,
    int maxPages = 1,
  }) async* {
    var imported = 0;
    for (var page = 1; page <= maxPages; page++) {
      final items = await api.fetchPage(
        config: config,
        token: token,
        type: type,
        page: page,
        categoryId: categoryId,
      );
      await _writePage(playlistId, type, categoryId, items);
      imported += items.length;
      yield ImportProgressModel(
        currentItem: '$type page $page',
        processedItems: imported,
        totalItems: null,
        startedAt: DateTime.now(),
      );
      if (items.isEmpty) break;
    }
    await searchRepository.rebuildProviderIndex(playlistId);
  }

  Future<void> _writePage(
    String playlistId,
    String type,
    String? categoryId,
    List<Map<String, dynamic>> items,
  ) async {
    switch (type) {
      case 'live':
        await database.insertLiveStreams(
          items.map((item) => _liveFromStalker(playlistId, categoryId, item)).toList(),
        );
        break;
      case 'vod':
        await database.insertVodStreams(
          items.map((item) => _vodFromStalker(playlistId, categoryId, item)).toList(),
        );
        break;
      case 'series':
        await database.insertSeriesStreams(
          items.map((item) => _seriesFromStalker(playlistId, categoryId, item)).toList(),
        );
        break;
    }
  }

  LiveStream _liveFromStalker(
    String playlistId,
    String? categoryId,
    Map<String, dynamic> item,
  ) {
    return LiveStream(
      streamId: safeString(item['id'] ?? item['ch_id'] ?? item['cmd']),
      name: safeString(item['name']),
      streamIcon: safeString(item['logo'] ?? item['screenshot_uri']),
      categoryId: safeString(categoryId ?? item['category_id'] ?? item['tv_genre_id']),
      epgChannelId: safeString(item['epg_id'] ?? item['xmltv_id']),
      playlistId: playlistId,
    );
  }

  VodStream _vodFromStalker(
    String playlistId,
    String? categoryId,
    Map<String, dynamic> item,
  ) {
    return VodStream(
      streamId: safeString(item['id'] ?? item['video_id']),
      name: safeString(item['name']),
      streamIcon: safeString(item['screenshot_uri'] ?? item['cover']),
      categoryId: safeString(categoryId ?? item['category_id']),
      rating: safeString(item['rating_imdb'] ?? item['rating']),
      rating5based: safeDouble(item['rating_5based']) ?? 0,
      containerExtension: safeString(item['container_extension'] ?? 'mp4'),
      playlistId: playlistId,
      createdAt: DateTime.now(),
      genre: safeString(item['genre']),
      youtubeTrailer: safeString(item['youtube_trailer']),
    );
  }

  SeriesStream _seriesFromStalker(
    String playlistId,
    String? categoryId,
    Map<String, dynamic> item,
  ) {
    return SeriesStream(
      playlistId: playlistId,
      seriesId: safeString(item['id'] ?? item['video_id']),
      name: safeString(item['name']),
      cover: safeString(item['screenshot_uri'] ?? item['cover']),
      plot: safeString(item['description']),
      genre: safeString(item['genre']),
      rating: safeString(item['rating_imdb'] ?? item['rating']),
      rating5based: safeDouble(item['rating_5based']),
      categoryId: safeString(categoryId ?? item['category_id']),
    );
  }
}
