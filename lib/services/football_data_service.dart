import 'dart:convert';
import 'package:another_iptv_player/database/database.dart';
import 'package:another_iptv_player/services/service_locator.dart';
import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/football_models.dart';

class FootballDataService {
  static const String _baseUrl = 'https://api.football-data.org/v4';
  static const String _apiKey = 'b6e92a7b9a4047879aac3f80110156d6';

  final AppDatabase _db = getIt<AppDatabase>();
  static const Duration _cacheDuration = Duration(minutes: 15);

  final List<String> _competitions = [
    'PL',
    'CL',
    'EL',
    'PD',
    'BL1',
    'SA',
    'FL1',
    'DED',
    'PPL',
    'WC',
    'EC',
  ];

  Future<List<FootballMatch>> getMatches({
    required String dateFrom,
    required String dateTo,
  }) async {
    final cacheKey = 'matches_${dateFrom}_$dateTo';

    // 1. Safe Cache Fallback: Try reading from DB, skip on any error
    FootballCacheData? cached;
    try {
      cached = await (_db.select(
        _db.footballCaches,
      )..where((t) => t.cacheKey.equals(cacheKey))).getSingleOrNull();

      if (cached != null) {
        final now = DateTime.now().millisecondsSinceEpoch;
        if (now < cached.expiresAt) {
          debugPrint('Sports Hub: Returning valid cache for $cacheKey');
          final List<dynamic> decoded = json.decode(cached.responseJson);
          return decoded.map((json) => FootballMatch.fromJson(json)).toList();
        } else {
          debugPrint('Sports Hub: Cache expired for $cacheKey');
        }
      }
    } catch (e) {
      debugPrint('Sports Hub Cache Error (Ignored): $e');
      // Continue to API fetch if cache read fails
    }

    debugPrint(
      'Sports Hub: Fetching fresh data from API for $dateFrom to $dateTo',
    );

    List<FootballMatch> allMatches = [];
    final Map<String, String> headers = {
      'X-Auth-Token': _apiKey,
      'Content-Type': 'application/json',
    };

    bool apiSuccess = false;

    for (String code in _competitions) {
      final uri = Uri.parse(
        '$_baseUrl/competitions/$code/matches',
      ).replace(queryParameters: {'dateFrom': dateFrom, 'dateTo': dateTo});

      try {
        debugPrint('Sports Hub API Request: $uri');
        final response = await http.get(uri, headers: headers);
        debugPrint(
          'Sports Hub API Response Status [$code]: ${response.statusCode}',
        );

        if (response.statusCode == 200) {
          final Map<String, dynamic> data = json.decode(response.body);
          final List<dynamic> matchesJson = data['matches'] ?? [];
          allMatches.addAll(
            matchesJson.map((json) => FootballMatch.fromJson(json)),
          );
          apiSuccess = true;
        } else if (response.statusCode == 403) {
          debugPrint('Sports Hub: Competition $code restricted on this plan.');
        } else if (response.statusCode == 429) {
          debugPrint('Sports Hub: Rate limit hit (429).');
          break;
        } else if (response.statusCode == 401) {
          debugPrint('Sports Hub: Unauthorized (401). Check API Key.');
          break;
        }
      } catch (e) {
        debugPrint('Sports Hub API Error for $code: $e');
      }

      // Respect rate limits
      await Future.delayed(const Duration(milliseconds: 200));
    }

    if (apiSuccess) {
      // Sort and Cache the successful result
      allMatches.sort((a, b) => a.utcDate.compareTo(b.utcDate));

      try {
        final now = DateTime.now();
        await _db
            .into(_db.footballCaches)
            .insertOnConflictUpdate(
              FootballCachesCompanion(
                cacheKey: Value(cacheKey),
                responseJson: Value(
                  json.encode(allMatches.map((m) => _toJson(m)).toList()),
                ),
                cachedAt: Value(now.millisecondsSinceEpoch),
                expiresAt: Value(
                  now.add(_cacheDuration).millisecondsSinceEpoch,
                ),
              ),
            );
        debugPrint('Sports Hub: Cache updated for $cacheKey');
      } catch (e) {
        debugPrint('Sports Hub: Error writing cache (Ignored): $e');
      }

      return allMatches;
    } else if (cached != null) {
      // Fallback to expired cache if API completely fails
      debugPrint('Sports Hub: API failed, falling back to expired cache');
      try {
        final List<dynamic> decoded = json.decode(cached.responseJson);
        return decoded.map((json) => FootballMatch.fromJson(json)).toList();
      } catch (_) {
        return [];
      }
    }

    return [];
  }

  Future<List<FootballMatch>> getTodayMatches() async {
    final now = DateTime.now();
    final today = _formatDate(now);
    return getMatches(dateFrom: today, dateTo: today);
  }

  Future<List<FootballMatch>> getCurrentAndNextMatches() async {
    final now = DateTime.now();
    final today = _formatDate(now);
    final tomorrow = _formatDate(now.add(const Duration(days: 1)));
    return getMatches(dateFrom: today, dateTo: tomorrow);
  }

  Future<List<FootballMatch>> getUpcomingMatches() async {
    final now = DateTime.now();
    final today = _formatDate(now);
    final future = _formatDate(now.add(const Duration(days: 14)));
    return getMatches(dateFrom: today, dateTo: future);
  }

  String _formatDate(DateTime date) {
    return '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
  }

  Map<String, dynamic> _toJson(FootballMatch m) {
    return {
      'id': m.id,
      'status': m.status,
      'utcDate': m.utcDate.toIso8601String(),
      'competition': {'name': m.competitionName},
      'area': {'name': m.areaName},
      'stage': m.stage,
      'group': m.group,
      'matchday': m.matchday,
      'lastUpdated': m.lastUpdated?.toIso8601String(),
      'homeTeam': {
        'id': m.homeTeam.id,
        'name': m.homeTeam.name,
        'shortName': m.homeTeam.shortName,
        'tla': m.homeTeam.tla,
        'crest': m.homeTeam.crest,
      },
      'awayTeam': {
        'id': m.awayTeam.id,
        'name': m.awayTeam.name,
        'shortName': m.awayTeam.shortName,
        'tla': m.awayTeam.tla,
        'crest': m.awayTeam.crest,
      },
      'score': {
        'winner': m.winner,
        'duration': m.duration,
        'fullTime': {'home': m.score.homeScore, 'away': m.score.awayScore},
        'halfTime': {'home': m.halfTimeHomeScore, 'away': m.halfTimeAwayScore},
      },
      'referees': [
        if (m.refereeName != null)
          {'name': m.refereeName, 'nationality': m.refereeNationality},
      ],
    };
  }
}
