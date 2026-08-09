import 'package:watchio/database/database.dart';
import 'package:watchio/models/category.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/live_stream.dart';
import 'package:watchio/models/m3u_series.dart';
import 'package:watchio/services/app_state.dart';

import '../models/m3u_item.dart';
import '../models/series.dart';
import '../models/vod_streams.dart';
import '../services/service_locator.dart';

class M3uRepository {
  final String _playlistId = AppState.currentPlaylist!.id;
  final _database = getIt<AppDatabase>();

  M3uRepository();

  Future<List<Category>?> getCategories() async {
    return await _database.getCategoriesByPlaylist(_playlistId);
  }

  Future<List<M3uItem>?> getM3uItemsByCategoryId({
    required String categoryId,
    int? top,
    int offset = 0,
    ContentType? contentType,
  }) async {
    var liveStreams = await _database.getM3uItemsByCategoryId(
      _playlistId,
      categoryId,
      top: top,
      offset: offset,
      contentType: contentType,
    );

    if (liveStreams.isNotEmpty) {
      return liveStreams;
    }
    return null;
  }

  Future<M3uItem?> getM3uItemById({required String id}) async {
    var m3uItem = await _database.getM3uItemsByIdAndPlaylist(_playlistId, id);
    return m3uItem;
  }

  Future<M3uItem?> getM3uItemByUrl({required String url}) async {
    var m3uItem = await _database.getM3uItemsByUrlAndPlaylist(_playlistId, url);
    return m3uItem;
  }


  Future<List<M3uItem>?> getM3uItems({
    int? top,
    int offset = 0,
    ContentType? contentType,
  }) async {
    var liveStreams = await _database.getM3uItemsByPlaylist(_playlistId, top: top, offset: offset);

    if (liveStreams.isNotEmpty) {
      return liveStreams;
    }
    return null;
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

  Future<List<SeriesStream>> searchSeries(String query, {int limit = 20}) async {
    return await _database.searchSeries(_playlistId, query, limit: limit);
  }

  Future<List<M3uSerie>?> getSeriesByCategoryId({
    required String categoryId,
    int? top,
    int offset = 0,
  }) async {
    var liveStreams = await _database.getM3uSeriesByCategoryId(
      _playlistId,
      categoryId,
      top: top,
      offset: offset,
    );

    if (liveStreams.isNotEmpty) {
      return liveStreams;
    }
    return null;
  }

  Future<List<M3uEpisode>?> getM3uEpisodesBySeriesId({
    required String seriesId,
  }) async {
    var episodes = await _database.getM3uEpisodesBySeriesId(
      _playlistId,
      seriesId,
    );

    if (episodes.isNotEmpty) {
      return episodes;
    }
    return null;
  }
}
