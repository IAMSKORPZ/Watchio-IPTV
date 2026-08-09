import 'dart:convert';
import 'package:watchio/database/database.dart';
import 'package:watchio/services/service_locator.dart';
import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class TmdbService {
  // Placeholder API key - should ideally be in config
  static const String _apiKey = '4e968a24cbba47311b6ec1e5b1efdbd6';
  static const String _baseUrl = 'https://api.themoviedb.org/3';

  static final TmdbService _instance = TmdbService._internal();
  factory TmdbService() => _instance;
  TmdbService._internal();

  final _db = getIt<AppDatabase>();

  Future<String?> getPoster(int tmdbId, {required bool isSeries}) async {
    final details = await getMediaDetails(tmdbId, isSeries: isSeries);
    return details?['poster_url']?.toString();
  }

  Future<Map<String, dynamic>?> getMediaDetails(
    int tmdbId, {
    required bool isSeries,
  }) async {
    try {
      final type = isSeries ? 'tv' : 'movie';
      final response = await http.get(
        Uri.parse('$_baseUrl/$type/$tmdbId?api_key=$_apiKey'),
      );
      if (response.statusCode != 200) return null;
      final data = json.decode(response.body) as Map<String, dynamic>;
      final path = data['poster_path']?.toString();
      return {
        ...data,
        if (path != null && path.isNotEmpty)
          'poster_url': 'https://image.tmdb.org/t/p/w500$path',
      };
    } catch (_) {
      return null;
    }
  }

  Future<String?> getMovieTrailer(int tmdbId) async {
    // 1. Check Cache first
    final cached =
        await (_db.select(_db.tmdbTrailerCaches)..where(
              (t) =>
                  t.tmdbId.equals(tmdbId.toString()) & t.type.equals('movie'),
            ))
            .getSingleOrNull();

    if (cached != null) {
      // Check age - e.g. 30 days
      if (DateTime.now().difference(cached.cachedAt).inDays < 30) {
        debugPrint('TMDB: Using cached movie trailer for $tmdbId');
        return cached.trailerKey;
      }
    }

    // 2. Fetch from API
    try {
      debugPrint('TMDB: Fetching movie trailer for $tmdbId from API');
      final url = Uri.parse('$_baseUrl/movie/$tmdbId/videos?api_key=$_apiKey');
      final response = await http.get(url);

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        final List results = data['results'];

        // Prefer YouTube Trailer
        final trailer = results.firstWhere(
          (v) =>
              v['site'] == 'YouTube' &&
              v['type'] == 'Trailer' &&
              v['official'] == true,
          orElse: () => results.firstWhere(
            (v) => v['site'] == 'YouTube' && v['type'] == 'Trailer',
            orElse: () => results.firstWhere(
              (v) => v['site'] == 'YouTube',
              orElse: () => null,
            ),
          ),
        );

        if (trailer != null) {
          final key = trailer['key'];
          // Save to Cache
          await _db
              .into(_db.tmdbTrailerCaches)
              .insertOnConflictUpdate(
                TmdbTrailerCachesCompanion.insert(
                  tmdbId: tmdbId.toString(),
                  type: 'movie',
                  trailerKey: key,
                  cachedAt: Value(DateTime.now()),
                ),
              );
          return key;
        }
      }
    } catch (e) {
      debugPrint('TMDB Movie Trailer Error: $e');
    }
    return null;
  }

  Future<String?> getTvShowTrailer(int tmdbId) async {
    // 1. Check Cache first
    final cached =
        await (_db.select(_db.tmdbTrailerCaches)..where(
              (t) => t.tmdbId.equals(tmdbId.toString()) & t.type.equals('tv'),
            ))
            .getSingleOrNull();

    if (cached != null) {
      if (DateTime.now().difference(cached.cachedAt).inDays < 30) {
        debugPrint('TMDB: Using cached TV trailer for $tmdbId');
        return cached.trailerKey;
      }
    }

    // 2. Fetch from API
    try {
      debugPrint('TMDB: Fetching TV trailer for $tmdbId from API');
      final url = Uri.parse('$_baseUrl/tv/$tmdbId/videos?api_key=$_apiKey');
      final response = await http.get(url);

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        final List results = data['results'];

        // Prefer YouTube Trailer
        final trailer = results.firstWhere(
          (v) =>
              v['site'] == 'YouTube' &&
              v['type'] == 'Trailer' &&
              v['official'] == true,
          orElse: () => results.firstWhere(
            (v) => v['site'] == 'YouTube' && v['type'] == 'Trailer',
            orElse: () => results.firstWhere(
              (v) => v['site'] == 'YouTube',
              orElse: () => null,
            ),
          ),
        );

        if (trailer != null) {
          final key = trailer['key'];
          // Save to Cache
          await _db
              .into(_db.tmdbTrailerCaches)
              .insertOnConflictUpdate(
                TmdbTrailerCachesCompanion.insert(
                  tmdbId: tmdbId.toString(),
                  type: 'tv',
                  trailerKey: key,
                  cachedAt: Value(DateTime.now()),
                ),
              );
          return key;
        }
      }
    } catch (e) {
      debugPrint('TMDB TV Trailer Error: $e');
    }
    return null;
  }
}
