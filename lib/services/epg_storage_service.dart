import 'package:another_iptv_player/database/database.dart';
import 'package:another_iptv_player/services/service_locator.dart';
import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';

class EpgStorageService {
  final AppDatabase database;

  EpgStorageService({AppDatabase? database})
    : database = database ?? getIt<AppDatabase>();

  Future<void> ensureSchema() async {
    if (kDebugMode) {
      debugPrint('EPG Storage: Using milliseconds for timestamps');
    }
    await database.customStatement('''
CREATE TABLE IF NOT EXISTS epg_channels(
  playlist_id TEXT NOT NULL,
  epg_channel_id TEXT NOT NULL,
  display_name TEXT NOT NULL,
  icon_url TEXT,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(playlist_id, epg_channel_id)
)
''');
    await database.customStatement('''
CREATE TABLE IF NOT EXISTS epg_programs(
  playlist_id TEXT NOT NULL,
  epg_channel_id TEXT NOT NULL,
  program_id TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  start_time INTEGER NOT NULL,
  end_time INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(playlist_id, epg_channel_id, program_id)
)
''');
    await database.customStatement(
      'CREATE INDEX IF NOT EXISTS idx_epg_program_window ON epg_programs(playlist_id, epg_channel_id, start_time, end_time)',
    );
    await database.customStatement(
      'CREATE INDEX IF NOT EXISTS idx_epg_channel_name ON epg_channels(playlist_id, display_name)',
    );
  }

  Future<List<EpgProgramWindow>> getProgramsForWindow({
    required String playlistId,
    required String epgChannelId,
    required DateTime start,
    required DateTime end,
    int limit = 200,
  }) async {
    await ensureSchema();
    final rows = await database
        .customSelect(
          '''
SELECT program_id, title, description, start_time, end_time
FROM epg_programs
WHERE playlist_id = ? AND epg_channel_id = ?
  AND start_time < ? AND end_time > ?
ORDER BY start_time ASC
LIMIT ?
''',
          variables: [
            Variable.withString(playlistId),
            Variable.withString(epgChannelId),
            Variable.withInt(end.millisecondsSinceEpoch),
            Variable.withInt(start.millisecondsSinceEpoch),
            Variable.withInt(limit),
          ],
        )
        .get();

    if (kDebugMode) {
      debugPrint(
        'EPG Storage: Found ${rows.length} programs for $epgChannelId using milliseconds',
      );
    }

    return rows
        .map(
          (row) => EpgProgramWindow(
            programId: row.read<String>('program_id'),
            title: row.read<String>('title'),
            description: row.readNullable<String>('description'),
            start: DateTime.fromMillisecondsSinceEpoch(
              row.read<int>('start_time'),
              isUtc: true,
            ).toLocal(),
            end: DateTime.fromMillisecondsSinceEpoch(
              row.read<int>('end_time'),
              isUtc: true,
            ).toLocal(),
          ),
        )
        .toList();
  }

  Future<List<EpgProgramWindow>> getProgramsByChannelName({
    required String playlistId,
    required String displayName,
    required DateTime start,
    required DateTime end,
    int limit = 200,
  }) async {
    await ensureSchema();

    final normalized = normalizeName(displayName);
    if (kDebugMode) {
      debugPrint(
        'EPG Storage: Searching by normalized name: "$normalized" (Original: "$displayName")',
      );
    }

    // Try exact match on display_name first
    var channelRows = await database
        .customSelect(
          'SELECT epg_channel_id FROM epg_channels WHERE playlist_id = ? AND display_name = ? LIMIT 1',
          variables: [
            Variable.withString(playlistId),
            Variable.withString(displayName),
          ],
        )
        .get();

    // If no exact match, try normalized matching (this is tricky in SQL without a normalized column)
    // For now, let's fetch all channel names for this playlist and match in memory if needed,
    // or just try common variations.
    if (channelRows.isEmpty) {
      channelRows = await database
          .customSelect(
            '''
SELECT epg_channel_id
FROM epg_channels
WHERE playlist_id = ?
  AND (epg_channel_id = ? COLLATE NOCASE OR display_name = ? COLLATE NOCASE)
LIMIT 1
''',
            variables: [
              Variable.withString(playlistId),
              Variable.withString(displayName),
              Variable.withString(displayName),
            ],
          )
          .get();
    }

    if (channelRows.isEmpty) {
      // Match compact names such as "UK | 4 SEVEN" to "4seven" or
      // "Channel 4Seven" without loading the full guide into memory.
      final compact = normalized.replaceAll(' ', '');
      channelRows = await database
          .customSelect(
            '''
SELECT epg_channel_id
FROM epg_channels
WHERE playlist_id = ?
  AND REPLACE(REPLACE(REPLACE(LOWER(display_name), ' ', ''), '-', ''), '_', '') LIKE ?
LIMIT 1
''',
            variables: [
              Variable.withString(playlistId),
              Variable.withString('%$compact%'),
            ],
          )
          .get();
    }

    if (channelRows.isEmpty) return [];

    final epgChannelId = channelRows.first.read<String>('epg_channel_id');
    return getProgramsForWindow(
      playlistId: playlistId,
      epgChannelId: epgChannelId,
      start: start,
      end: end,
      limit: limit,
    );
  }

  String normalizeName(String name) {
    String normalized = name.toUpperCase();

    // Remove specific prefixes and tags
    final toRemove = [
      'UK |',
      'US |',
      'CA |',
      'FR |',
      'DE |',
      'ES |',
      'IT |',
      'TR |',
      'AR |',
      'FHD',
      'UHD',
      'HD',
      'SD',
      'VM',
      'VIP',
      '4K',
      'BACKUP',
      'RAW',
    ];

    for (var tag in toRemove) {
      normalized = normalized.replaceAll(tag, '');
    }

    // Remove punctuation
    normalized = normalized.replaceAll(RegExp(r'[^\w\s]'), '');

    // Remove double spaces
    normalized = normalized.replaceAll(RegExp(r'\s+'), ' ');

    return normalized.trim().toLowerCase();
  }

  Future<List<EpgProgramWindow>> getProgramsByChannelKeys({
    required String playlistId,
    required Iterable<String?> keys,
    required DateTime start,
    required DateTime end,
    int limit = 200,
  }) async {
    for (final key in keys) {
      final trimmed = key?.trim();
      if (trimmed == null || trimmed.isEmpty) continue;
      final programs = await getProgramsByChannelName(
        playlistId: playlistId,
        displayName: trimmed,
        start: start,
        end: end,
        limit: limit,
      );
      if (programs.isNotEmpty) return programs;
    }
    return [];
  }

  Future<int> getChannelCount(String playlistId) async {
    final row = await database
        .customSelect(
          'SELECT COUNT(*) as cnt FROM epg_channels WHERE playlist_id = ?',
          variables: [Variable.withString(playlistId)],
        )
        .getSingle();
    return row.read<int>('cnt');
  }

  Future<int> getProgramCount(String playlistId) async {
    final row = await database
        .customSelect(
          'SELECT COUNT(*) as cnt FROM epg_programs WHERE playlist_id = ?',
          variables: [Variable.withString(playlistId)],
        )
        .getSingle();
    return row.read<int>('cnt');
  }

  Future<void> clearEpgData(String playlistId) async {
    await database.customStatement(
      'DELETE FROM epg_channels WHERE playlist_id = ?',
      [playlistId],
    );
    await database.customStatement(
      'DELETE FROM epg_programs WHERE playlist_id = ?',
      [playlistId],
    );
    if (kDebugMode) {
      debugPrint('EPG Storage: Cleared EPG data for playlist $playlistId');
    }
  }

  Future<int> prunePrograms({
    required String playlistId,
    Duration keepPast = const Duration(hours: 48),
    Duration keepFuture = const Duration(hours: 72),
  }) async {
    await ensureSchema();
    final now = DateTime.now().toUtc();
    final minEnd = now.subtract(keepPast).millisecondsSinceEpoch;
    final maxStart = now.add(keepFuture).millisecondsSinceEpoch;
    final removed = await database.customUpdate(
      '''
DELETE FROM epg_programs
WHERE playlist_id = ?
  AND (end_time < ? OR start_time > ?)
''',
      variables: [
        Variable.withString(playlistId),
        Variable.withInt(minEnd),
        Variable.withInt(maxStart),
      ],
      updates: const {},
    );
    if (kDebugMode) {
      debugPrint('EPG Storage: Pruned $removed old/future programmes');
    }
    return removed;
  }
}

class EpgProgramWindow {
  final String programId;
  final String title;
  final String? description;
  final DateTime start;
  final DateTime end;

  const EpgProgramWindow({
    required this.programId,
    required this.title,
    required this.start,
    required this.end,
    this.description,
  });
}
