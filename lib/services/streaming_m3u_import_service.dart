import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:watchio/database/database.dart'
    hide M3uEpisodes, M3uSeries;
import 'package:watchio/models/category.dart';
import 'package:watchio/models/category_type.dart';
import 'package:watchio/models/category_with_content_type.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/import_progress_model.dart';
import 'package:watchio/models/import_session_model.dart';
import 'package:watchio/models/m3u_item.dart';
import 'package:watchio/models/m3u_series.dart';
import 'package:watchio/services/import_recovery_service.dart';
import 'package:watchio/services/m3u_parser.dart';
import 'package:watchio/repositories/search_repository.dart';
import 'package:collection/collection.dart';
import 'package:uuid/uuid.dart';

class StreamingM3uImportService {
  static const int defaultBatchSize = 500;

  final AppDatabase database;
  final int batchSize;
  final Uuid _uuid = Uuid();
  final ImportRecoveryService recoveryService;
  final SearchRepository searchRepository;

  StreamingM3uImportService({
    required this.database,
    this.batchSize = defaultBatchSize,
    ImportRecoveryService? recoveryService,
    SearchRepository? searchRepository,
  }) : recoveryService = recoveryService ?? ImportRecoveryService(),
       searchRepository =
           searchRepository ?? SearchRepository(database: database);

  Future<ImportProgressModel> importFile({
    required String playlistId,
    required String filePath,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    final file = File(filePath);
    final totalBytes = await file.length();
    final lines = file
        .openRead()
        .transform(utf8.decoder)
        .transform(const LineSplitter());
    return _importLines(
      playlistId: playlistId,
      lines: lines,
      totalItems: totalBytes,
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<ImportProgressModel> importUrl({
    required String playlistId,
    required String url,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    final client = HttpClient();
    try {
      final request = await client.getUrl(Uri.parse(url));
      final response = await request.close();
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw Exception('HTTP ${response.statusCode}: M3U URL unavailable');
      }
      final lines = response
          .transform(utf8.decoder)
          .transform(const LineSplitter());
      return _importLines(
        playlistId: playlistId,
        lines: lines,
        totalItems: response.contentLength > 0 ? response.contentLength : null,
        onProgress: onProgress,
        cancellationToken: cancellationToken,
      );
    } finally {
      client.close(force: true);
    }
  }

  Future<ImportProgressModel> _importLines({
    required String playlistId,
    required Stream<String> lines,
    required int? totalItems,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    final startedAt = DateTime.now();
    final session = ImportSessionModel(
      id: _uuid.v4(),
      providerId: playlistId,
      type: 'm3u',
      status: ImportSessionStatus.running,
      startedAt: startedAt,
    );
    await recoveryService.saveSession(session);
    var processed = 0;
    final categories = <CategoryWithContentType, String>{};
    final batch = <M3uItem>[];
    final seriesItems = <M3uTempSeries>[];
    var currentMeta = <String, String?>{};
    String? currentName;

    await database.deleteAllM3uItems(playlistId);
    await database.deleteAllCategoriesByPlaylist(playlistId);

    Future<void> flush() async {
      if (batch.isEmpty) return;
      await database.insertM3uItems(List<M3uItem>.from(batch));
      batch.clear();
    }

    try {
      await for (final rawLine in lines) {
        cancellationToken?.throwIfCancelled();
        processed++;
        final line = rawLine.trim();

        if (line.startsWith('#EXTINF')) {
          final commaIndex = line.indexOf(',');
          final metadataPart = commaIndex != -1
              ? line.substring(0, commaIndex)
              : line;
          currentName = commaIndex != -1
              ? line.substring(commaIndex + 1).trim()
              : null;
          currentMeta = _readMeta(metadataPart);
        } else if (line.startsWith('#EXTGRP:')) {
          currentMeta['group-name'] = line.substring(8).trim();
        } else if (line.isNotEmpty && !line.startsWith('#')) {
          final itemName = _firstNonBlank(
            currentName,
            currentMeta['tvg-name'],
            currentMeta['tvg-id'],
            _filenameFromUrl(line),
          );
          final groupTitle = _firstNonBlank(
            currentMeta['group-title'],
            currentMeta['group-name'],
            'not_categorized',
          );
          final item = M3uItem(
            id: _uuid.v4(),
            playlistId: playlistId,
            url: line,
            contentType: M3uParser.detectContentType(line),
            name: itemName,
            tvgId: currentMeta['tvg-id'],
            tvgName: currentMeta['tvg-name'],
            tvgLogo: currentMeta['tvg-logo'],
            tvgUrl: currentMeta['tvg-url'],
            tvgRec: currentMeta['tvg-rec'],
            tvgShift: currentMeta['tvg-shift'],
            groupTitle: groupTitle,
            groupName: currentMeta['group-name'],
            userAgent: _firstNonBlank(
              currentMeta['user-agent'],
              currentMeta['http-user-agent'],
            ),
            referrer: currentMeta['referrer'],
          );
          final key = CategoryWithContentType(
            categoryName: item.groupTitle ?? 'not_categorized',
            contentType: item.contentType,
          );
          final categoryId = categories.putIfAbsent(key, _uuid.v4);
          item.categoryId = categoryId;
          batch.add(item);

          final parsedSeries = SeriesParser.parse(item);
          if (parsedSeries != null) seriesItems.add(parsedSeries);

          if (batch.length >= batchSize) await flush();
          currentMeta.clear();
          currentName = null;
        }

        if (processed % batchSize == 0) {
          onProgress?.call(
            ImportProgressModel(
              currentItem: line,
              processedItems: processed,
              totalItems: totalItems,
              startedAt: startedAt,
            ),
          );
        }
      }

      await flush();
      await _writeCategories(playlistId, categories);
      await _writeSeries(playlistId, seriesItems);
    } catch (e) {
      await database.deleteAllM3uItems(playlistId);
      await database.deleteAllCategoriesByPlaylist(playlistId);
      if (e is ImportCancelledException) {
        await recoveryService.markCancelled(session.id);
      } else {
        await recoveryService.markFailed(session.id, e.toString());
      }
      rethrow;
    }

    final done = ImportProgressModel(
      currentItem: 'complete',
      processedItems: processed,
      totalItems: totalItems,
      startedAt: startedAt,
    );
    await recoveryService.markCompleted(session.id);
    await searchRepository.rebuildProviderIndex(playlistId);
    onProgress?.call(done);
    return done;
  }

  Map<String, String?> _readMeta(String line) {
    return {
      'tvg-id': _extractAttribute(line, 'tvg-id'),
      'tvg-name': _extractAttribute(line, 'tvg-name'),
      'tvg-logo': _extractAttribute(line, 'tvg-logo'),
      'tvg-url': _extractAttribute(line, 'tvg-url'),
      'tvg-rec': _extractAttribute(line, 'tvg-rec'),
      'tvg-shift': _firstNonBlank(
        _extractAttribute(line, 'tvg-shift'),
        _extractAttribute(line, 'timeshift'),
      ),
      'group-title': _extractAttribute(line, 'group-title'),
      'user-agent': _extractAttribute(line, 'user-agent'),
      'http-user-agent': _extractAttribute(line, 'http-user-agent'),
      'referrer': _firstNonBlank(
        _extractAttribute(line, 'referrer'),
        _extractAttribute(line, 'http-referrer'),
      ),
      'catchup': _extractAttribute(line, 'catchup'),
      'catchup-source': _extractAttribute(line, 'catchup-source'),
      'catchup-days': _extractAttribute(line, 'catchup-days'),
      'tvg-chno': _firstNonBlank(
        _extractAttribute(line, 'tvg-chno'),
        _extractAttribute(line, 'channel-number'),
      ),
    };
  }

  String? _extractAttribute(String line, String attribute) {
    final regex = RegExp(
      '\\b${RegExp.escape(attribute)}\\s*=\\s*("([^"]*)"|\'([^\']*)\'|([^\\s,]+))',
      caseSensitive: false,
    );
    final match = regex.firstMatch(line);
    final value = match?.group(2) ?? match?.group(3) ?? match?.group(4);
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }

  String? _firstNonBlank(
    String? first, [
    String? second,
    String? third,
    String? fourth,
  ]) {
    for (final value in [first, second, third, fourth]) {
      final trimmed = value?.trim();
      if (trimmed != null && trimmed.isNotEmpty) return trimmed;
    }
    return null;
  }

  String? _filenameFromUrl(String url) {
    final path = url.split(RegExp(r'[#?]')).first;
    final parts = path.split('/').where((part) => part.trim().isNotEmpty);
    if (parts.isEmpty) return null;
    return parts.last.trim();
  }

  Future<void> _writeCategories(
    String playlistId,
    Map<CategoryWithContentType, String> categories,
  ) async {
    final rows = categories.entries.map((entry) {
      return Category(
        categoryId: entry.value,
        categoryName: entry.key.categoryName,
        type: _categoryTypeFor(entry.key.contentType),
        parentId: 0,
        playlistId: playlistId,
      );
    }).toList();
    await database.insertCategories(rows);
  }

  CategoryType _categoryTypeFor(ContentType type) {
    return switch (type) {
      ContentType.liveStream => CategoryType.live,
      ContentType.vod => CategoryType.vod,
      ContentType.series => CategoryType.series,
    };
  }

  Future<void> _writeSeries(
    String playlistId,
    List<M3uTempSeries> items,
  ) async {
    if (items.isEmpty) return;
    final grouped = groupBy(items, (item) => item.name);
    final series = grouped.entries.map((entry) {
      return M3uSerie(
        playlistId: playlistId,
        seriesId: SeriesParser.generateSeriesId(playlistId, entry.key),
        name: entry.key,
        cover: entry.value.first.m3uItem.tvgLogo,
        categoryId: entry.value.first.m3uItem.categoryId,
      );
    }).toList();
    final episodes = items.map((item) {
      return M3uEpisode(
        playlistId: playlistId,
        seriesId: SeriesParser.generateSeriesId(playlistId, item.name),
        seasonNumber: item.seasonNumber,
        episodeNumber: item.episodeNumber,
        name: item.m3uItem.name!,
        url: item.m3uItem.url,
        cover: item.m3uItem.tvgLogo,
      );
    }).toList();

    await database.insertM3uSeries(
      series.map((item) => item.toCompanion()).toList(),
    );
    await database.insertM3uEpisodes(
      episodes.map((item) => item.toCompanion()).toList(),
    );
  }
}
