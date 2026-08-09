import 'package:flutter/foundation.dart' hide Category;
import 'dart:convert';
import 'package:drift/drift.dart' as drift;
import 'package:http/http.dart' as http;
import 'package:watchio/database/database.dart';
import 'package:watchio/models/api_configuration_model.dart';
import 'package:watchio/models/api_response.dart';
import 'package:watchio/models/category.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/live_stream.dart';
import 'package:watchio/models/series_response.dart';
import 'package:watchio/models/vod_streams.dart';
import 'package:watchio/models/series.dart';
import 'package:watchio/utils/type_convertions.dart';
import '../models/import_progress_model.dart';
import '../models/category_type.dart';
import '../services/xtream_streaming_import_service.dart';
import 'package:watchio/services/service_locator.dart';

class IptvRepository {
  static const String virtualAll = 'virtual_all';
  static const String virtualFavorites = 'virtual_favorites';
  static const String virtualHistory = 'virtual_history';

  final ApiConfig _config;
  final String _playlistId;
  final _database = getIt<AppDatabase>();

  IptvRepository(this._config, this._playlistId);

  String get playlistId => _playlistId;

  Future<void> importLiveStreamsStreamed({
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    await XtreamStreamingImportService(database: _database).importLiveStreams(
      config: _config,
      playlistId: _playlistId,
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<void> importMoviesStreamed({
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    await XtreamStreamingImportService(database: _database).importMovies(
      config: _config,
      playlistId: _playlistId,
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<void> importSeriesStreamed({
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    await XtreamStreamingImportService(database: _database).importSeries(
      config: _config,
      playlistId: _playlistId,
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<http.Response> _makeRequest(
    String endpoint, {
    Map<String, String>? additionalParams,
    bool cacheBuster = false, // only add timestamp when true
  }) async {
    final params = Map<String, String>.from(_config.baseParams);
    if (additionalParams != null) {
      params.addAll(additionalParams);
    }

    if (cacheBuster) {
      params['_t'] = DateTime.now().millisecondsSinceEpoch.toString();
    }

    final uri = Uri.parse(
      '${_config.baseUrl}/$endpoint',
    ).replace(queryParameters: params);

    return await http.get(uri, headers: {'Content-Type': 'application/json'});
  }

  Future<ApiResponse?> getPlayerInfo({bool forceRefresh = false}) async {
    try {
      if (!forceRefresh) {
        final cached = await getCachedPlayerInfo();
        if (cached != null) return cached;
      }

      final response = await _makeRequest('player_api.php', cacheBuster: false);

      if (response.statusCode == 200) {
        final jsonData = json.decode(response.body);
        var apiResponse = ApiResponse.fromJson(jsonData, _playlistId);

        await _database.insertOrUpdateUserInfo(apiResponse.userInfo);
        await _database.insertOrUpdateServerInfo(apiResponse.serverInfo);

        return apiResponse;
      } else {
        throw Exception(
          'HTTP ${response.statusCode}: ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      debugPrint('Player Info Error: $e');
      return null;
    }
  }

  Future<ApiResponse?> getCachedPlayerInfo() async {
    final userInfo = await _database.getUserInfoByPlaylistId(_playlistId);
    final serverInfo = await _database.getServerInfoByPlaylistId(_playlistId);

    if (userInfo == null || serverInfo == null) return null;

    return ApiResponse(
      userInfo: userInfo,
      serverInfo: serverInfo,
      playlistId: _playlistId,
    );
  }

  Future<List<Category>> getCachedCategories(CategoryType type) {
    return _database.getCategoriesByTypeAndPlaylist(_playlistId, type);
  }

  Future<List<LiveStream>?> getLiveChannelsFromApi({
    String? categoryId,
    bool forceRefresh = false,
  }) async {
    try {
      final additionalParams = <String, String>{'action': 'get_live_streams'};

      if (categoryId != null) {
        additionalParams['category_id'] = categoryId;
      }

      final response = await _makeRequest(
        'player_api.php',
        additionalParams: additionalParams,
        cacheBuster: true,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonData = json.decode(response.body);
        var liveStreams = jsonData
            .map((json) => LiveStream.fromJson(json, _playlistId))
            .toList();

        await _database.deleteLiveStreamsByPlaylistId(_playlistId);
        await _database.insertLiveStreams(liveStreams);
        return liveStreams;
      } else {
        throw Exception(
          'HTTP ${response.statusCode}: ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      debugPrint('Live Channels Error: $e');
      return null;
    }
  }

  Future<List<LiveStream>?> getLiveChannels({
    String? categoryId,
    bool forceRefresh = false,
  }) async {
    try {
      var liveStreams = await _database.getLiveStreams(_playlistId);

      if (liveStreams.isNotEmpty) {
        return liveStreams;
      }
    } catch (e) {
      return null;
    }
    return null;
  }

  Future<List<LiveStream>?> getLiveChannelsByCategoryId({
    required String categoryId,
    bool forceRefresh = false,
    int? top,
    int offset = 0,
  }) async {
    try {
      if (categoryId == virtualAll) {
        return await _database.getLiveStreams(
          _playlistId,
          top: top,
          offset: offset,
        );
      } else if (categoryId == virtualFavorites) {
        final favorites = await _database.getFavoritesByContentType(
          _playlistId,
          ContentType.liveStream,
        );
        final streams = <LiveStream>[];
        for (var fav in favorites) {
          final s = await findLiveStreamById(fav.streamId);
          if (s != null) streams.add(s);
        }
        return streams;
      } else if (categoryId == virtualHistory) {
        final history = await _database.getWatchHistoriesByContentType(
          _playlistId,
          ContentType.liveStream,
        );
        final streams = <LiveStream>[];
        for (var h in history) {
          final s = await findLiveStreamById(h.streamId);
          if (s != null) streams.add(s);
        }
        return streams;
      }

      var liveStreams = await _database.getLiveStreamsByCategoryId(
        _playlistId,
        categoryId,
        top: top,
        offset: offset,
      );

      if (liveStreams.isNotEmpty) {
        return liveStreams;
      }
    } catch (e) {
      return null;
    }
    return null;
  }

  Future<int> getItemCountByCategory(
    String categoryId,
    CategoryType type,
  ) async {
    if (categoryId == virtualAll) {
      switch (type) {
        case CategoryType.live:
          return await _database.getTotalLiveStreamCount(_playlistId);
        case CategoryType.vod:
          return await _database.getTotalVodStreamCount(_playlistId);
        case CategoryType.series:
          return await _database.getTotalSeriesStreamCount(_playlistId);
      }
    } else if (categoryId == virtualFavorites) {
      return await _database.getFavoriteCountByContentType(
        _playlistId,
        _getContentTypeFromCategoryType(type),
      );
    } else if (categoryId == virtualHistory) {
      return await _database.getWatchHistoryCountByContentType(
        _playlistId,
        _getContentTypeFromCategoryType(type),
      );
    }

    switch (type) {
      case CategoryType.live:
        return await _database.getLiveStreamCountByCategoryId(
          _playlistId,
          categoryId,
        );
      case CategoryType.vod:
        return await _database.getVodStreamCountByCategoryId(
          _playlistId,
          categoryId,
        );
      case CategoryType.series:
        return await _database.getSeriesStreamCountByCategoryId(
          _playlistId,
          categoryId,
        );
    }
  }

  Future<int> getCachedItemCount(CategoryType type) {
    return getItemCountByCategory(virtualAll, type);
  }

  ContentType _getContentTypeFromCategoryType(CategoryType type) {
    switch (type) {
      case CategoryType.live:
        return ContentType.liveStream;
      case CategoryType.vod:
        return ContentType.vod;
      case CategoryType.series:
        return ContentType.series;
    }
  }

  Future<Map<String, int>> getAllCategoryCounts(CategoryType type) async {
    final counts = <String, int>{};

    // Virtual All
    counts[virtualAll] = await getItemCountByCategory(virtualAll, type);

    // Virtual Favorites
    counts[virtualFavorites] = await getItemCountByCategory(
      virtualFavorites,
      type,
    );

    // Virtual History
    counts[virtualHistory] = await getItemCountByCategory(virtualHistory, type);

    // Provider categories
    Map<String, int> providerCounts;
    switch (type) {
      case CategoryType.live:
        providerCounts = await _database.getLiveStreamCounts(_playlistId);
        break;
      case CategoryType.vod:
        providerCounts = await _database.getVodStreamCounts(_playlistId);
        break;
      case CategoryType.series:
        providerCounts = await _database.getSeriesStreamCounts(_playlistId);
        break;
    }
    counts.addAll(providerCounts);

    return counts;
  }

  Future<LiveStream?> findLiveStreamById(String streamId) async {
    try {
      var liveStream = await _database.findLiveStreamById(
        streamId,
        _playlistId,
      );
      return liveStream;
    } catch (e) {
      debugPrint('Live Channels Error: $e');
      return null;
    }
  }

  Future<VodStream?> findMovieById(String streamId) async {
    return await _database.findMovieById(streamId, _playlistId);
  }

  Future<List<VodStream>?> getMoviesFromApi({
    String? categoryId,
    bool forceRefresh = false,
    int? top,
  }) async {
    try {
      final additionalParams = <String, String>{'action': 'get_vod_streams'};

      if (categoryId != null) {
        additionalParams['category_id'] = categoryId;
      }

      final response = await _makeRequest(
        'player_api.php',
        additionalParams: additionalParams,
        cacheBuster: true,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonData = json.decode(response.body);
        var vodStreams = jsonData.indexed
            .map(
              (entry) => VodStream.fromJson(
                entry.$2,
                _playlistId,
                serverOrder: entry.$1,
              ),
            )
            .toList();

        await _database.deleteVodStreamsByPlaylistId(_playlistId);
        await _database.insertVodStreams(vodStreams);

        return vodStreams;
      } else {
        throw Exception(
          'HTTP ${response.statusCode}: ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      debugPrint('Movies Error: $e');
      return null;
    }
  }

  Future<List<VodStream>?> getMovies({
    String? categoryId,
    bool forceRefresh = false,
    int? top,
    int offset = 0,
  }) async {
    try {
      if (categoryId == virtualAll) {
        final vodStreams = await _database.getVodStreamsByPlaylistId(
          _playlistId,
          top: top,
          offset: offset,
        );
        return await _refreshMoviesIfMissingServerOrder(
          vodStreams,
          top,
          offset,
        );
      } else if (categoryId == virtualFavorites) {
        final favorites = await _database.getFavoritesByContentType(
          _playlistId,
          ContentType.vod,
        );
        final movies = <VodStream>[];
        for (var fav in favorites) {
          final m = await findMovieById(fav.streamId);
          if (m != null) movies.add(m);
        }
        return movies;
      } else if (categoryId == virtualHistory) {
        final history = await _database.getWatchHistoriesByContentType(
          _playlistId,
          ContentType.vod,
        );
        final movies = <VodStream>[];
        for (var h in history) {
          final m = await findMovieById(h.streamId);
          if (m != null) movies.add(m);
        }
        return movies;
      }

      if (categoryId != null) {
        var vodStreams = await _database.getVodStreamsByCategoryAndPlaylistId(
          categoryId: categoryId,
          playlistId: _playlistId,
          top: top,
          offset: offset,
        );

        if (vodStreams.isNotEmpty) {
          if (_needsServerOrderRefresh(vodStreams)) {
            await getMoviesFromApi();
            vodStreams = await _database.getVodStreamsByCategoryAndPlaylistId(
              categoryId: categoryId,
              playlistId: _playlistId,
              top: top,
              offset: offset,
            );
          }
          return vodStreams;
        }
      } else {
        var vodStreams = await _database.getVodStreamsByPlaylistId(
          _playlistId,
          top: top,
          offset: offset,
        );

        if (vodStreams.isNotEmpty) {
          vodStreams = await _refreshMoviesIfMissingServerOrder(
            vodStreams,
            top,
            offset,
          );
          return vodStreams;
        }
      }
    } catch (e) {
      return null;
    }
    return null;
  }

  Future<List<SeriesStream>?> getSeriesFromApi({
    String? categoryId,
    bool forceRefresh = false,
    int? top,
  }) async {
    try {
      final additionalParams = <String, String>{'action': 'get_series'};

      if (categoryId != null) {
        additionalParams['category_id'] = categoryId;
      }

      final response = await _makeRequest(
        'player_api.php',
        additionalParams: additionalParams,
        cacheBuster: true,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonData = json.decode(response.body);
        var series = jsonData.indexed
            .map(
              (entry) => SeriesStream.fromJson(
                entry.$2,
                _playlistId,
                serverOrder: entry.$1,
              ),
            )
            .toList();

        await _database.deleteSeriesStreamsByPlaylistId(_playlistId);
        await _database.insertSeriesStreams(series);

        return series;
      } else {
        throw Exception(
          'HTTP ${response.statusCode}: ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      debugPrint('Series Error: $e');
      return null;
    }
  }

  Future<List<SeriesStream>?> getSeries({
    String? categoryId,
    bool forceRefresh = false,
    int? top,
    int offset = 0,
  }) async {
    try {
      if (categoryId == virtualAll) {
        final series = await _database.getSeriesStreamsByPlaylistId(
          _playlistId,
          top: top,
          offset: offset,
        );
        return await _refreshSeriesIfMissingServerOrder(series, top, offset);
      } else if (categoryId == virtualFavorites) {
        final favorites = await _database.getFavoritesByContentType(
          _playlistId,
          ContentType.series,
        );
        final series = <SeriesStream>[];
        for (var fav in favorites) {
          final s = await findSeriesStreamById(fav.streamId);
          if (s != null) series.add(s);
        }
        return series;
      } else if (categoryId == virtualHistory) {
        final history = await _database.getWatchHistoriesByContentType(
          _playlistId,
          ContentType.series,
        );
        final series = <SeriesStream>[];
        for (var h in history) {
          final s = await findSeriesStreamById(h.streamId);
          if (s != null) series.add(s);
        }
        return series;
      }

      if (categoryId != null) {
        var series = await _database.getSeriesStreamsByCategoryAndPlaylistId(
          categoryId: categoryId,
          playlistId: _playlistId,
          top: top,
          offset: offset,
        );

        if (series.isNotEmpty) {
          if (_needsServerOrderRefresh(series)) {
            await getSeriesFromApi();
            series = await _database.getSeriesStreamsByCategoryAndPlaylistId(
              categoryId: categoryId,
              playlistId: _playlistId,
              top: top,
              offset: offset,
            );
          }
          return series;
        }
      } else {
        var series = await _database.getSeriesStreamsByPlaylistId(
          _playlistId,
          top: top,
          offset: offset,
        );

        if (series.isNotEmpty) {
          series = await _refreshSeriesIfMissingServerOrder(
            series,
            top,
            offset,
          );
          return series;
        }
      }
    } catch (e) {
      return null;
    }
    return null;
  }

  Future<List<VodStream>> _refreshMoviesIfMissingServerOrder(
    List<VodStream> vodStreams,
    int? top,
    int offset,
  ) async {
    if (!_needsServerOrderRefresh(vodStreams)) return vodStreams;
    await getMoviesFromApi();
    return _database.getVodStreamsByPlaylistId(
      _playlistId,
      top: top,
      offset: offset,
    );
  }

  Future<List<SeriesStream>> _refreshSeriesIfMissingServerOrder(
    List<SeriesStream> series,
    int? top,
    int offset,
  ) async {
    if (!_needsServerOrderRefresh(series)) return series;
    await getSeriesFromApi();
    return _database.getSeriesStreamsByPlaylistId(
      _playlistId,
      top: top,
      offset: offset,
    );
  }

  bool _needsServerOrderRefresh(List<dynamic> items) {
    return items.length > 1 &&
        items.every((item) {
          final serverOrder = item.serverOrder;
          return serverOrder is int && serverOrder == 0;
        });
  }

  Future<SeriesStream?> findSeriesStreamById(String seriesId) async {
    return await _database.findSeriesStreamById(seriesId, _playlistId);
  }

  /// Fetch VOD movie info from API
  Future<Map<String, dynamic>?> getVodInfo(String vodId) async {
    try {
      final response = await _makeRequest(
        'player_api.php',
        additionalParams: {'action': 'get_vod_info', 'vod_id': vodId},
        cacheBuster: true,
      );

      if (response.statusCode == 200) {
        final jsonData = json.decode(response.body);
        if (jsonData is Map<String, dynamic>) {
          return jsonData;
        } else if (jsonData is Map) {
          return Map<String, dynamic>.from(jsonData);
        }
        return null;
      } else {
        throw Exception(
          'HTTP ${response.statusCode}: ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      debugPrint('VOD Info Error: $e');
      return null;
    }
  }

  Future<List<Category>?> getLiveCategories({bool forceRefresh = false}) async {
    return _getCategories(
      CategoryType.live,
      'get_live_categories',
      forceRefresh,
    );
  }

  Future<List<Category>?> getVodCategories({bool forceRefresh = false}) async {
    return _getCategories(CategoryType.vod, 'get_vod_categories', forceRefresh);
  }

  Future<List<Category>?> getSeriesCategories({
    bool forceRefresh = false,
  }) async {
    return _getCategories(
      CategoryType.series,
      'get_series_categories',
      forceRefresh,
    );
  }

  Future<List<Category>?> _getCategories(
    CategoryType type,
    String action,
    bool forceRefresh,
  ) async {
    try {
      if (!forceRefresh) {
        final cachedCategories = await _database.getCategoriesByTypeAndPlaylist(
          _playlistId,
          type,
        );
        if (cachedCategories.isNotEmpty) {
          return cachedCategories;
        }
      }

      final additionalParams = <String, String>{'action': action};

      final response = await _makeRequest(
        'player_api.php',
        additionalParams: additionalParams,
        cacheBuster: true,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonData = json.decode(response.body);
        final categories = jsonData
            .map((json) => Category.fromJson(json, _playlistId, type))
            .toList();

        await _database.deleteCategoriesByTypeAndPlaylist(_playlistId, type);
        await _database.insertCategories(categories);

        return categories;
      } else {
        throw Exception(
          'HTTP ${response.statusCode}: ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      debugPrint('${type.value} Categories Error: $e');
      // Hata durumunda cache'den dön
      final cachedCategories = await _database.getCategoriesByTypeAndPlaylist(
        _playlistId,
        type,
      );
      return cachedCategories.isNotEmpty ? cachedCategories : null;
    }
  }

  Future<Map<CategoryType, List<Category>>?> getAllCategories({
    bool forceRefresh = false,
  }) async {
    try {
      final results = await Future.wait([
        getLiveCategories(forceRefresh: forceRefresh),
        getVodCategories(forceRefresh: forceRefresh),
        getSeriesCategories(forceRefresh: forceRefresh),
      ]);

      return {
        CategoryType.live: results[0] ?? [],
        CategoryType.vod: results[1] ?? [],
        CategoryType.series: results[2] ?? [],
      };
    } catch (e) {
      debugPrint('Get All Categories Error: $e');
      return await _database.getAllCategoriesByPlaylist(_playlistId);
    }
  }

  Future<void> clearCategoriesCache({CategoryType? type}) async {
    if (type != null) {
      await _database.deleteCategoriesByTypeAndPlaylist(_playlistId, type);
    } else {
      await _database.deleteAllCategoriesByPlaylist(_playlistId);
    }
  }

  Future<List<Category>> searchCategories(
    CategoryType type,
    String query,
  ) async {
    return await _database.searchCategories(_playlistId, type, query);
  }

  Future<List<LiveStream>> searchLiveStreams(
    String query, {
    int limit = 20,
  }) async {
    return await _database.searchLiveStreams(_playlistId, query, limit: limit);
  }

  Future<List<VodStream>> searchMovies(String query, {int limit = 20}) async {
    return await _database.searchMovie(_playlistId, query, limit: limit);
  }

  Future<List<SeriesStream>> searchSeries(
    String query, {
    int limit = 20,
  }) async {
    return await _database.searchSeries(_playlistId, query, limit: limit);
  }

  Future<SeriesDetailResponse?> getSeriesInfo(
    String seriesId, {
    bool forceRefresh = false,
  }) async {
    try {
      // --- SMART CACHE: check cached series + lastModified + episodes presence ---
      if (!forceRefresh) {
        final seriesInfo = await _database.getSeriesInfo(seriesId, _playlistId);

        if (seriesInfo != null) {
          // fetch main list to compare lastModified
          final allSeries = await _database.getSeriesStreamsByPlaylistId(
            _playlistId,
          );

          bool isStale = false;
          try {
            final seriesItem = allSeries.firstWhere(
              (s) => s.seriesId == seriesId,
            );

            final cachedLast = (seriesItem.lastModified ?? '')
                .toString()
                .trim();
            final infoLast = (seriesInfo.lastModified ?? '').toString().trim();

            if (cachedLast.isEmpty ||
                infoLast.isEmpty ||
                cachedLast != infoLast) {
              isStale = true;
            }
          } catch (e) {
            // series not found in list -> conservative: treat as stale
            isStale = true;
          }

          if (!isStale) {
            final seasons = await _database.getSeasonsBySeriesId(
              seriesId,
              _playlistId,
            );
            final episodes = await _database.getEpisodesBySeriesId(
              seriesId,
              _playlistId,
            );

            if (seasons.isNotEmpty && episodes.isNotEmpty) {
              return SeriesDetailResponse(
                seriesInfo: seriesInfo,
                seasons: seasons,
                episodes: episodes,
                playlistId: _playlistId,
              );
            }
          }
        }
      }

      final response = await _makeRequest(
        'player_api.php',
        additionalParams: {'action': 'get_series_info', 'series_id': seriesId},
        cacheBuster: true,
      );

      if (response.statusCode == 200) {
        final jsonData = json.decode(response.body);

        await _saveSeriesDataToDatabase(seriesId, jsonData);

        final seriesInfo = await _database.getSeriesInfo(seriesId, _playlistId);
        final seasons = await _database.getSeasonsBySeriesId(
          seriesId,
          _playlistId,
        );
        final episodes = await _database.getEpisodesBySeriesId(
          seriesId,
          _playlistId,
        );

        return SeriesDetailResponse(
          seriesInfo: seriesInfo!,
          seasons: seasons,
          episodes: episodes,
          playlistId: _playlistId,
        );
      } else {
        throw Exception(
          'HTTP ${response.statusCode}: ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      debugPrint('Series Info Error: $e');
      return null;
    }
  }

  Future<List<EpisodesData>> getSeriesEpisodesBySeason(
    String seriesId,
    int seasonNumber, {
    bool forceRefresh = false,
  }) async {
    try {
      if (!forceRefresh) {
        final episodes = await _database.getEpisodesBySeason(
          seriesId,
          seasonNumber,
          _playlistId,
        );
        if (episodes.isNotEmpty) {
          return episodes;
        }
      }

      // Eğer episodlar yok ise series info'yu çek
      await getSeriesInfo(seriesId, forceRefresh: true);

      return await _database.getEpisodesBySeason(
        seriesId,
        seasonNumber,
        _playlistId,
      );
    } catch (e) {
      debugPrint('Get Episodes By Season Error: $e');
      return [];
    }
  }

  Future<void> _saveSeriesDataToDatabase(
    String seriesId,
    Map<String, dynamic> data,
  ) async {
    try {
      await _database.clearSeriesData(seriesId, _playlistId);

      final info = data['info'];
      if (info != null) {
        final seriesInfoCompanion = SeriesInfosCompanion(
          seriesId: drift.Value(seriesId),
          playlistId: drift.Value(_playlistId),
          name: drift.Value(safeString(info['name'])),
          cover: drift.Value(safeString(info['cover'])),
          plot: drift.Value(safeString(info['plot'])),
          cast: drift.Value(safeString(info['cast'])),
          director: drift.Value(safeString(info['director'])),
          genre: drift.Value(safeString(info['genre'])),
          releaseDate: drift.Value(safeString(info['releaseDate'])),
          lastModified: drift.Value(safeString(info['last_modified'])),
          rating: drift.Value(safeString(info['rating'])),
          rating5based: drift.Value(safeInt(info['rating_5based'])),
          backdropPath: drift.Value(
            getFirstBackdropPath(info['backdrop_path']),
          ),
          youtubeTrailer: drift.Value(safeString(info['youtube_trailer'])),
          episodeRunTime: drift.Value(safeString(info['episode_run_time'])),
          categoryId: drift.Value(safeString(info['category_id'])),
          tmdbId: drift.Value(safeInt(info['tmdb_id'])),
        );

        await _database.insertSeriesInfo(seriesInfoCompanion);
      }

      final seasons = data['seasons'];
      if (seasons is List && seasons.isNotEmpty) {
        for (final season in seasons) {
          final seasonCompanion = SeasonsCompanion(
            seriesId: drift.Value(seriesId),
            playlistId: drift.Value(_playlistId),
            airDate: drift.Value(safeString(season['air_date'])),
            episodeCount: drift.Value(safeInt(season['episode_count'])),
            seasonId: drift.Value(safeInt(season['id'])),
            name: drift.Value(safeString(season['name'])),
            overview: drift.Value(safeString(season['overview'])),
            seasonNumber: drift.Value(safeInt(season['season_number'])),
            voteAverage: drift.Value(safeInt(season['vote_average'])),
            cover: drift.Value(safeString(season['cover'])),
            coverBig: drift.Value(safeString(season['cover_big'])),
          );

          await _database.insertSeason(seasonCompanion);
        }
      }

      final episodes = data['episodes'];
      Set<int> seasonNumbersFromEpisodes = {};

      if (episodes is Map<String, dynamic>) {
        for (final seasonKey in episodes.keys) {
          final seasonEpisodes = episodes[seasonKey];

          if (seasonEpisodes is List) {
            for (final episode in seasonEpisodes) {
              dynamic info = episode['info'];

              // Get season number from episodes, fallback to seasonKey if needed
              int seasonNumber = safeInt(episode['season']);
              if (seasonNumber == 0) {
                seasonNumber = safeInt(seasonKey);
                if (seasonNumber == 0) seasonNumber = 1;
              }
              seasonNumbersFromEpisodes.add(seasonNumber);

              final episodeCompanion = EpisodesCompanion(
                seriesId: drift.Value(seriesId),
                playlistId: drift.Value(_playlistId),
                episodeId: drift.Value(safeString(episode['id'])),
                episodeNum: drift.Value(safeInt(episode['episode_num'])),
                title: drift.Value(safeString(episode['title'])),
                containerExtension: drift.Value(
                  safeString(episode['container_extension']),
                ),
                season: drift.Value(seasonNumber),
                customSid: drift.Value(safeString(episode['custom_sid'])),
                added: drift.Value(safeString(episode['added'])),
                directSource: drift.Value(safeString(episode['direct_source'])),

                tmdbId: drift.Value(
                  safeInt(info is Map ? info['tmdb_id'] : null),
                ),
                releasedate: drift.Value(
                  safeString(info is Map ? info['releasedate'] : null),
                ),
                plot: drift.Value(
                  safeString(info is Map ? info['plot'] : null),
                ),
                durationSecs: drift.Value(
                  safeInt(info is Map ? info['duration_secs'] : null),
                ),
                duration: drift.Value(
                  safeString(info is Map ? info['duration'] : null),
                ),
                movieImage: drift.Value(
                  safeString(info is Map ? info['movie_image'] : null),
                ),
                bitrate: drift.Value(
                  safeInt(info is Map ? info['bitrate'] : null),
                ),
                rating: drift.Value(
                  safeDouble(info is Map ? info['rating'] : null),
                ),
              );

              try {
                await _database.insertEpisode(episodeCompanion);
              } catch (e) {
                debugPrint(
                  'Error inserting episode ${episode['id']} for series $seriesId: $e',
                );
              }
            }
          }
        }
      }

      // Compare season numbers from episodes with existing seasons
      // Create missing seasons
      if (seasonNumbersFromEpisodes.isNotEmpty) {
        // Get existing seasons
        final existingSeasons = await _database.getSeasonsBySeriesId(
          seriesId,
          _playlistId,
        );
        final existingSeasonNumbers = existingSeasons
            .map((s) => s.seasonNumber)
            .toSet();

        // Compare season numbers from episodes with existing seasons
        for (final seasonNumber in seasonNumbersFromEpisodes) {
          if (!existingSeasonNumbers.contains(seasonNumber)) {
            // Calculate episode count for this season
            int episodeCount = 0;
            if (episodes is Map<String, dynamic>) {
              for (final seasonKey in episodes.keys) {
                final seasonEpisodes = episodes[seasonKey];
                if (seasonEpisodes is List) {
                  for (final episode in seasonEpisodes) {
                    int epSeason = safeInt(episode['season']);
                    if (epSeason == 0) {
                      epSeason = safeInt(seasonKey);
                      if (epSeason == 0) epSeason = 1;
                    }
                    if (epSeason == seasonNumber) {
                      episodeCount++;
                    }
                  }
                }
              }
            }

            final seasonCompanion = SeasonsCompanion(
              seriesId: drift.Value(seriesId),
              playlistId: drift.Value(_playlistId),
              airDate: drift.Value(null),
              episodeCount: drift.Value(episodeCount),
              seasonId: drift.Value(0),
              name: drift.Value('$seasonNumber'),
              overview: drift.Value(null),
              seasonNumber: drift.Value(seasonNumber),
              voteAverage: drift.Value(null),
              cover: drift.Value(null),
              coverBig: drift.Value(null),
            );

            await _database.insertSeason(seasonCompanion);
            debugPrint(
              'Created missing season: $seasonNumber with $episodeCount episodes',
            );
          }
        }
      }

      debugPrint('Series data saved to database successfully');
    } catch (e) {
      debugPrint('Save series data to database error: $e');
      rethrow;
    }
  }
}
