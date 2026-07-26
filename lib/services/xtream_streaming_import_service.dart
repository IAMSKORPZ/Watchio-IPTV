import 'dart:convert';

import 'package:another_iptv_player/database/database.dart';
import 'package:another_iptv_player/models/api_configuration_model.dart';
import 'package:another_iptv_player/models/import_progress_model.dart';
import 'package:another_iptv_player/models/live_stream.dart';
import 'package:another_iptv_player/models/series.dart';
import 'package:another_iptv_player/models/vod_streams.dart';
import 'package:another_iptv_player/models/import_session_model.dart';
import 'package:another_iptv_player/services/import_recovery_service.dart';
import 'package:another_iptv_player/repositories/search_repository.dart';
import 'package:another_iptv_player/services/streaming_json_array_decoder.dart';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';

class XtreamStreamingImportService {
  static const int defaultBatchSize = 200;

  final AppDatabase database;
  final http.Client client;
  final int batchSize;
  final StreamingJsonArrayDecoder _decoder = StreamingJsonArrayDecoder();
  final ImportRecoveryService recoveryService;
  final SearchRepository searchRepository;
  final Uuid _uuid = const Uuid();

  XtreamStreamingImportService({
    required this.database,
    http.Client? client,
    this.batchSize = defaultBatchSize,
    ImportRecoveryService? recoveryService,
    SearchRepository? searchRepository,
  }) : client = client ?? http.Client(),
       recoveryService = recoveryService ?? ImportRecoveryService(),
       searchRepository =
           searchRepository ?? SearchRepository(database: database);

  Future<ImportProgressModel> importLiveStreams({
    required ApiConfig config,
    required String playlistId,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) {
    return _import(
      config: config,
      playlistId: playlistId,
      action: 'get_live_streams',
      idField: 'stream_id',
      tableName: 'live_streams',
      idColumn: 'stream_id',
      writeJson: (items) {
        final rows = items
            .map((json) => LiveStream.fromJson(json, playlistId))
            .toList();
        return database.insertLiveStreams(rows);
      },
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<ImportProgressModel> importMovies({
    required ApiConfig config,
    required String playlistId,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) {
    return _import(
      config: config,
      playlistId: playlistId,
      action: 'get_vod_streams',
      idField: 'stream_id',
      tableName: 'vod_streams',
      idColumn: 'stream_id',
      writeJson: (items) {
        final rows = items
            .map((json) => VodStream.fromJson(json, playlistId))
            .toList();
        return database.insertVodStreams(rows);
      },
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<ImportProgressModel> importSeries({
    required ApiConfig config,
    required String playlistId,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) {
    return _import(
      config: config,
      playlistId: playlistId,
      action: 'get_series',
      idField: 'series_id',
      tableName: 'series_streams',
      idColumn: 'series_id',
      writeJson: (items) {
        final rows = items
            .map((json) => SeriesStream.fromJson(json, playlistId))
            .toList();
        return database.insertSeriesStreams(rows);
      },
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<ImportProgressModel> _import({
    required ApiConfig config,
    required String playlistId,
    required String action,
    required String idField,
    required String tableName,
    required String idColumn,
    required Future<void> Function(List<Map<String, dynamic>> items) writeJson,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    final startedAt = DateTime.now();
    final session = ImportSessionModel(
      id: _uuid.v4(),
      providerId: playlistId,
      type: 'xtream:$action',
      status: ImportSessionStatus.running,
      startedAt: startedAt,
    );
    await recoveryService.saveSession(session);
    final params = Map<String, String>.from(config.baseParams)
      ..['action'] = action
      ..['_t'] = DateTime.now().millisecondsSinceEpoch.toString();
    final uri = Uri.parse(
      '${config.baseUrl}/player_api.php',
    ).replace(queryParameters: params);
    final request = http.Request('GET', uri)
      ..headers['Content-Type'] = 'application/json';
    final response = await client
        .send(request)
        .timeout(const Duration(minutes: 2));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      await recoveryService.markFailed(
        session.id,
        'HTTP ${response.statusCode}: Xtream import failed',
      );
      throw Exception('HTTP ${response.statusCode}: Xtream import failed');
    }

    final batch = <Map<String, dynamic>>[];
    var processed = 0;
    final textStream = response.stream.transform(utf8.decoder);
    await _ensureSeenTable();

    try {
      await for (final item in _decoder.decodeObjects(textStream)) {
        cancellationToken?.throwIfCancelled();
        batch.add(item);
        processed++;

        // Provide feedback every 50 items or when batch is full
        if (processed % 50 == 0 || batch.length >= batchSize) {
          if (batch.length >= batchSize) {
            final copy = List<Map<String, dynamic>>.from(batch);
            await _recordSeen(session.id, copy, idField);
            await writeJson(copy);
            batch.clear();
          }

          onProgress?.call(
            ImportProgressModel(
              currentItem: action,
              processedItems: processed,
              startedAt: startedAt,
            ),
          );
        }
      }

      if (batch.isNotEmpty) {
        await _recordSeen(session.id, batch, idField);
        await writeJson(batch);
      }
      await _deleteMissingRows(
        playlistId: playlistId,
        sessionId: session.id,
        tableName: tableName,
        idColumn: idColumn,
      );
    } catch (e) {
      if (e is ImportCancelledException) {
        await recoveryService.markCancelled(session.id);
      } else {
        await recoveryService.markFailed(session.id, e.toString());
      }
      rethrow;
    } finally {
      await _clearSeenIds(session.id);
    }
    final done = ImportProgressModel(
      currentItem: action,
      processedItems: processed,
      startedAt: startedAt,
    );
    await recoveryService.markCompleted(session.id);
    await searchRepository.rebuildProviderIndex(playlistId);
    onProgress?.call(done);
    return done;
  }

  Future<void> _ensureSeenTable() {
    return database.customStatement('''
CREATE TEMP TABLE IF NOT EXISTS xtream_import_seen(
  import_session_id TEXT NOT NULL,
  item_id TEXT NOT NULL,
  PRIMARY KEY(import_session_id, item_id)
)
''');
  }

  Future<void> _recordSeen(
    String sessionId,
    List<Map<String, dynamic>> items,
    String idField,
  ) async {
    await database.batch((batch) {
      for (final item in items) {
        final id = item[idField]?.toString();
        if (id == null || id.isEmpty) continue;
        batch.customStatement(
          '''
INSERT OR IGNORE INTO xtream_import_seen(import_session_id, item_id)
VALUES (?, ?)
''',
          [sessionId, id],
        );
      }
    });
  }

  Future<void> _deleteMissingRows({
    required String playlistId,
    required String sessionId,
    required String tableName,
    required String idColumn,
  }) {
    return database.customStatement(
      '''
DELETE FROM $tableName
WHERE playlist_id = ?
  AND NOT EXISTS (
    SELECT 1
    FROM xtream_import_seen seen
    WHERE seen.import_session_id = ?
      AND seen.item_id = $tableName.$idColumn
  )
''',
      [playlistId, sessionId],
    );
  }

  Future<void> _clearSeenIds(String sessionId) {
    return database.customStatement(
      'DELETE FROM xtream_import_seen WHERE import_session_id = ?',
      [sessionId],
    );
  }
}
