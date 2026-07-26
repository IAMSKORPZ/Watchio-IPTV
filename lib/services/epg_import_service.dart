import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:another_iptv_player/models/import_progress_model.dart';
import 'package:another_iptv_player/services/cache_metadata_service.dart';
import 'package:another_iptv_player/services/epg_storage_service.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class EpgImportService {
  static const int defaultBatchSize = 500;

  final EpgStorageService storage;
  final int batchSize;

  EpgImportService({
    EpgStorageService? storage,
    this.batchSize = defaultBatchSize,
  }) : storage = storage ?? EpgStorageService();

  Future<ImportProgressModel> importUrl({
    required String playlistId,
    required String url,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    final maskedUrl = url
        .replaceFirst(RegExp(r'password=[^&]+'), 'password=***')
        .replaceFirst(RegExp(r'username=[^&]+'), 'username=***');
    if (kDebugMode) {
      debugPrint('EPG XMLTV download started: $maskedUrl');
    }

    final response = await http.Client().send(
      http.Request('GET', Uri.parse(url)),
    );
    if (response.statusCode >= 400) {
      throw Exception('EPG import failed: HTTP ${response.statusCode}');
    }

    if (kDebugMode) {
      debugPrint('EPG XMLTV response status: ${response.statusCode}');
      debugPrint(
        'EPG XMLTV response content length: ${response.contentLength}',
      );
    }

    // We use a stream for memory efficiency
    return _importLines(
      playlistId: playlistId,
      lines: _xmlElements(response.stream.transform(utf8.decoder)),
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<ImportProgressModel> importFile({
    required String playlistId,
    required String filePath,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) {
    return _importLines(
      playlistId: playlistId,
      lines: _xmlElements(File(filePath).openRead().transform(utf8.decoder)),
      onProgress: onProgress,
      cancellationToken: cancellationToken,
    );
  }

  Future<ImportProgressModel> _importLines({
    required String playlistId,
    required Stream<String> lines,
    ImportProgressCallback? onProgress,
    ImportCancellationToken? cancellationToken,
  }) async {
    await storage.ensureSchema();

    final startedAt = DateTime.now();
    final programs = <_ProgramRow>[];
    int channelsFound = 0;
    int programsFound = 0;
    int programsSkipped = 0;

    String? channelId;
    final buffer = StringBuffer();
    bool isFirstChunk = true;

    Future<void> flushPrograms() async {
      if (programs.isEmpty) return;
      final batchToInsert = List<_ProgramRow>.from(programs);
      programs.clear();

      await storage.database.batch((batch) {
        for (final program in batchToInsert) {
          batch.customStatement(
            '''
INSERT OR REPLACE INTO epg_programs(
  playlist_id, epg_channel_id, program_id, title, description, start_time, end_time, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
''',
            [
              playlistId,
              program.channelId,
              program.programId,
              program.title,
              program.description,
              program.start.millisecondsSinceEpoch,
              program.end.millisecondsSinceEpoch,
              DateTime.now().millisecondsSinceEpoch,
            ],
          );
        }
      });
      if (kDebugMode) {
        debugPrint(
          'EPG: committed batch of ${batchToInsert.length} programmes',
        );
      }
    }

    try {
      await for (final rawLine in lines) {
        cancellationToken?.throwIfCancelled();
        final line = rawLine.trim();
        if (line.isEmpty) continue;

        if (isFirstChunk) {
          final preview = line.length > 500 ? line.substring(0, 500) : line;
          if (kDebugMode) {
            debugPrint('EPG XMLTV response start: $preview');
          }
          isFirstChunk = false;
        }

        if (line.contains('<channel ')) {
          channelId = _attr(line, 'id');
          buffer.clear();
          buffer.write(line);
          if (line.contains('</channel>')) {
            final xml = buffer.toString();
            if (channelId != null) {
              final displayName = _tag(xml, 'display-name') ?? channelId;
              await _upsertChannel(
                playlistId,
                channelId,
                displayName,
                _attr(xml, 'src'),
              );
              channelsFound++;
            }
            channelId = null;
            buffer.clear();
          }
        } else if (channelId != null) {
          buffer.write(line);
          if (line.contains('</channel>')) {
            final xml = buffer.toString();
            final displayName = _tag(xml, 'display-name') ?? channelId;
            await _upsertChannel(
              playlistId,
              channelId,
              displayName,
              _attr(xml, 'src'),
            );
            channelsFound++;
            channelId = null;
            buffer.clear();
          }
        } else if (line.contains('<programme ')) {
          buffer.clear();
          buffer.write(line);
          if (line.contains('</programme>')) {
            final row = _parseProgram(buffer.toString());
            if (row != null) {
              programs.add(row);
              programsFound++;
            } else {
              programsSkipped++;
            }
            buffer.clear();
          }
        } else if (buffer.isNotEmpty) {
          buffer.write(line);
          if (line.contains('</programme>')) {
            final row = _parseProgram(buffer.toString());
            if (row != null) {
              programs.add(row);
              programsFound++;
            } else {
              programsSkipped++;
            }
            buffer.clear();
          }
        }

        if (programs.length >= batchSize) {
          await flushPrograms();
          onProgress?.call(
            ImportProgressModel(
              currentItem: '$channelsFound channels, $programsFound programmes',
              processedItems: programsFound,
              startedAt: startedAt,
            ),
          );
        }
      }

      await flushPrograms();
      await storage.prunePrograms(playlistId: playlistId);
      await CacheMetadataService().markSuccess(
        playlistId: playlistId,
        section: CacheSection.epg,
        itemCount: await storage.getProgramCount(playlistId),
      );

      if (kDebugMode) {
        debugPrint(
          'EPG full import complete. Channels: $channelsFound, Programmes: $programsFound, Skipped: $programsSkipped',
        );
      }

      final done = ImportProgressModel(
        currentItem: '$channelsFound channels, $programsFound programmes',
        processedItems: programsFound,
        startedAt: startedAt,
      );
      onProgress?.call(done);
      return done;
    } catch (e) {
      await CacheMetadataService().markFailure(
        playlistId: playlistId,
        section: CacheSection.epg,
        error: e,
      );
      rethrow;
    }
  }

  Stream<String> _xmlElements(Stream<String> chunks) async* {
    var pending = '';
    await for (final chunk in chunks) {
      pending += chunk;
      while (true) {
        final channelStart = pending.indexOf('<channel ');
        final programmeStart = pending.indexOf('<programme ');
        int start;
        String closingTag;
        if (channelStart >= 0 &&
            (programmeStart < 0 || channelStart < programmeStart)) {
          start = channelStart;
          closingTag = '</channel>';
        } else if (programmeStart >= 0) {
          start = programmeStart;
          closingTag = '</programme>';
        } else {
          if (pending.length > 64) {
            pending = pending.substring(pending.length - 64);
          }
          break;
        }

        final end = pending.indexOf(closingTag, start);
        if (end < 0) {
          if (start > 0) pending = pending.substring(start);
          break;
        }
        final elementEnd = end + closingTag.length;
        yield pending.substring(start, elementEnd);
        pending = pending.substring(elementEnd);
      }
    }
  }

  Future<void> _upsertChannel(
    String playlistId,
    String channelId,
    String displayName,
    String? iconUrl,
  ) {
    return storage.database.customStatement(
      '''
INSERT OR REPLACE INTO epg_channels(
  playlist_id, epg_channel_id, display_name, icon_url, updated_at
) VALUES (?, ?, ?, ?, ?)
''',
      [
        playlistId,
        channelId,
        displayName,
        iconUrl,
        DateTime.now().millisecondsSinceEpoch,
      ],
    );
  }

  _ProgramRow? _parseProgram(String xml) {
    final channel = _attr(xml, 'channel');
    final startRaw = _attr(xml, 'start');
    final stopRaw = _attr(xml, 'stop');
    if (channel == null || startRaw == null || stopRaw == null) return null;
    final start = _parseXmlTvTime(startRaw);
    final stop = _parseXmlTvTime(stopRaw);
    if (start == null || stop == null) return null;
    final title = _tag(xml, 'title') ?? 'Untitled';
    return _ProgramRow(
      channelId: channel,
      programId: '${channel}_${start.millisecondsSinceEpoch}',
      title: title,
      description: _tag(xml, 'desc'),
      start: start,
      end: stop,
    );
  }

  DateTime? _parseXmlTvTime(String value) {
    final match = RegExp(
      r'^(\d{14})(?:\s*([+-])(\d{2})(\d{2}))?',
    ).firstMatch(value);
    if (match == null) return null;
    final raw = match.group(1)!;
    final utc = DateTime.utc(
      int.parse(raw.substring(0, 4)),
      int.parse(raw.substring(4, 6)),
      int.parse(raw.substring(6, 8)),
      int.parse(raw.substring(8, 10)),
      int.parse(raw.substring(10, 12)),
      int.parse(raw.substring(12, 14)),
    );
    final sign = match.group(2);
    if (sign == null) return utc;
    final hours = int.parse(match.group(3)!);
    final minutes = int.parse(match.group(4)!);
    final offset = Duration(hours: hours, minutes: minutes);
    return sign == '+' ? utc.subtract(offset) : utc.add(offset);
  }

  String? _attr(String xml, String name) {
    final match = RegExp(
      '\\b${RegExp.escape(name)}\\s*=\\s*("([^"]*)"|\'([^\']*)\')',
      caseSensitive: false,
    ).firstMatch(xml);
    return match?.group(2) ?? match?.group(3);
  }

  String? _tag(String xml, String name) {
    final value = RegExp(
      '<$name[^>]*>(.*?)</$name>',
      dotAll: true,
    ).firstMatch(xml)?.group(1);
    return value?.replaceAll(RegExp(r'\s+'), ' ').trim();
  }
}

class _ProgramRow {
  final String channelId;
  final String programId;
  final String title;
  final String? description;
  final DateTime start;
  final DateTime end;

  const _ProgramRow({
    required this.channelId,
    required this.programId,
    required this.title,
    required this.start,
    required this.end,
    this.description,
  });
}
