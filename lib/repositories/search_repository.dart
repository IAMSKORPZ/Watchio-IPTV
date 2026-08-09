import 'package:watchio/database/database.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/live_stream.dart';
import 'package:watchio/models/paged_result.dart';
import 'package:watchio/models/playlist_content_model.dart';
import 'package:watchio/models/series.dart';
import 'package:watchio/models/vod_streams.dart';
import 'package:watchio/services/performance_service.dart';
import 'package:watchio/services/service_locator.dart';
import 'package:drift/drift.dart';

class SearchRepository {
  static const int defaultLimit = 50;

  final AppDatabase database;

  SearchRepository({AppDatabase? database})
    : database = database ?? getIt<AppDatabase>();

  Future<void> ensureSearchSchema() async {
    await database.customStatement('''
CREATE VIRTUAL TABLE IF NOT EXISTS content_search_fts USING fts5(
  playlist_id UNINDEXED,
  content_type UNINDEXED,
  content_id UNINDEXED,
  name,
  image_url UNINDEXED,
  tokenize = 'unicode61'
)
''');
    await database.customStatement(
      'CREATE INDEX IF NOT EXISTS idx_live_playlist_name ON live_streams(playlist_id, name)',
    );
    await database.customStatement(
      'CREATE INDEX IF NOT EXISTS idx_vod_playlist_name ON vod_streams(playlist_id, name)',
    );
    await database.customStatement(
      'CREATE INDEX IF NOT EXISTS idx_series_playlist_name ON series_streams(playlist_id, name)',
    );
  }

  Future<void> rebuildProviderIndex(String playlistId) async {
    await ensureSearchSchema();
    await database.transaction(() async {
      await database.customStatement(
        'DELETE FROM content_search_fts WHERE playlist_id = ?',
        [playlistId],
      );
      await database.customStatement(
        '''
INSERT INTO content_search_fts(playlist_id, content_type, content_id, name, image_url)
SELECT playlist_id, 'live', stream_id, name, stream_icon
FROM live_streams WHERE playlist_id = ?
''',
        [playlistId],
      );
      await database.customStatement(
        '''
INSERT INTO content_search_fts(playlist_id, content_type, content_id, name, image_url)
SELECT playlist_id, 'vod', stream_id, name, stream_icon
FROM vod_streams WHERE playlist_id = ?
''',
        [playlistId],
      );
      await database.customStatement(
        '''
INSERT INTO content_search_fts(playlist_id, content_type, content_id, name, image_url)
SELECT playlist_id, 'series', series_id, name, COALESCE(cover, '')
FROM series_streams WHERE playlist_id = ?
''',
        [playlistId],
      );
    });
  }

  Future<PagedResult<ContentItem>> search(
    String playlistId,
    String query, {
    ContentType? contentType,
    int page = 0,
    int limit = defaultLimit,
  }) {
    return PerformanceService.track('search', () async {
      final trimmed = query.trim();
      if (trimmed.isEmpty) {
        return PagedResult(
          items: const [],
          page: page,
          pageSize: limit,
          hasNextPage: false,
        );
      }

      await ensureSearchSchema();
      final likeItems = await _searchLike(
        playlistId,
        trimmed,
        page,
        limit,
        contentType: contentType,
      );
      if (likeItems.isNotEmpty && contentType != null) {
        return PagedResult(
          items: likeItems,
          page: page,
          pageSize: limit,
          hasNextPage: likeItems.length == limit,
        );
      }

      final fallback = likeItems.isNotEmpty
          ? likeItems
          : await _searchFts(
              playlistId,
              trimmed,
              page,
              limit,
              contentType: contentType,
            );
      final results = contentType == null
          ? await _mergeGlobalResults(playlistId, trimmed, fallback, limit)
          : fallback;
      return PagedResult(
        items: results,
        page: page,
        pageSize: limit,
        hasNextPage: results.length == limit,
      );
    }, metadata: {'playlistId': playlistId, 'page': page, 'limit': limit});
  }

  Future<List<ContentItem>> _searchFts(
    String playlistId,
    String query,
    int page,
    int limit, {
    ContentType? contentType,
  }) async {
    final typeName = _contentTypeName(contentType);
    final rows = await database
        .customSelect(
          '''
SELECT content_id, content_type, name, image_url
  , NULL AS description
  , NULL AS category_id
  , NULL AS epg_channel_id
  , NULL AS rating
  , NULL AS rating5based
  , NULL AS container_extension
  , NULL AS genre
  , NULL AS plot
  , NULL AS release_date
FROM content_search_fts
WHERE playlist_id = ? AND content_search_fts MATCH ?
  AND (? IS NULL OR content_type = ?)
LIMIT ? OFFSET ?
''',
          variables: [
            Variable.withString(playlistId),
            Variable.withString(_ftsQuery(query)),
            Variable<String>(typeName),
            Variable<String>(typeName),
            Variable.withInt(limit),
            Variable.withInt(page * limit),
          ],
        )
        .get();
    return rows.map(_rowToContentItem).toList();
  }

  Future<List<ContentItem>> _searchLike(
    String playlistId,
    String query,
    int page,
    int limit, {
    ContentType? contentType,
  }) async {
    final like = '%${query.replaceAll('%', r'\%').replaceAll('_', r'\_')}%';
    final clauses = <String>[];
    final variables = <Variable>[
      Variable.withString(playlistId),
      Variable.withString(like),
      Variable.withString(playlistId),
      Variable.withString(like),
      Variable.withString(playlistId),
      Variable.withString(like),
    ];

    if (contentType == null || contentType == ContentType.liveStream) {
      clauses.add('''
SELECT stream_id AS content_id, 'live' AS content_type, name, stream_icon AS image_url
  , NULL AS description
  , category_id
  , epg_channel_id
  , NULL AS rating
  , NULL AS rating5based
  , NULL AS container_extension
  , NULL AS genre
  , NULL AS plot
  , NULL AS release_date
FROM live_streams WHERE playlist_id = ? AND name LIKE ? ESCAPE '\\'
''');
    }
    if (contentType == null || contentType == ContentType.vod) {
      clauses.add('''
SELECT stream_id AS content_id, 'vod' AS content_type, name, stream_icon AS image_url
  , genre AS description
  , category_id
  , NULL AS epg_channel_id
  , rating
  , rating5based
  , container_extension
  , genre
  , NULL AS plot
  , NULL AS release_date
FROM vod_streams WHERE playlist_id = ? AND name LIKE ? ESCAPE '\\'
''');
    }
    if (contentType == null || contentType == ContentType.series) {
      clauses.add('''
SELECT series_id AS content_id, 'series' AS content_type, name, COALESCE(cover, '') AS image_url
  , COALESCE(plot, genre, '') AS description
  , category_id
  , NULL AS epg_channel_id
  , rating
  , rating5based
  , NULL AS container_extension
  , genre
  , plot
  , release_date
FROM series_streams WHERE playlist_id = ? AND name LIKE ? ESCAPE '\\'
''');
    }

    final activeVariables = switch (contentType) {
      ContentType.liveStream => variables.sublist(0, 2),
      ContentType.vod => variables.sublist(2, 4),
      ContentType.series => variables.sublist(4, 6),
      null => variables,
    };
    final rows = await database
        .customSelect(
          '''
${clauses.join('\nUNION ALL\n')}
LIMIT ? OFFSET ?
''',
          variables: [
            ...activeVariables,
            Variable.withInt(limit),
            Variable.withInt(page * limit),
          ],
        )
        .get();
    return rows.map(_rowToContentItem).toList();
  }

  Future<List<ContentItem>> _mergeGlobalResults(
    String playlistId,
    String query,
    List<ContentItem> contentItems,
    int limit,
  ) async {
    final merged = <ContentItem>[...contentItems];
    final seen = merged
        .map((item) => '${item.contentType.name}:${item.id}')
        .toSet();
    final epgLimit = (limit ~/ 3).clamp(8, 20);
    final epgItems = await _searchEpg(playlistId, query, epgLimit);

    for (final item in epgItems) {
      final key = 'epg:${item.id}:${item.name}';
      if (seen.add(key)) {
        merged.add(item);
      }
      if (merged.length >= limit) break;
    }

    return merged.take(limit).toList();
  }

  Future<List<ContentItem>> searchEpgPrograms(
    String playlistId,
    String query, {
    int limit = defaultLimit,
  }) {
    return PerformanceService.track(
      'search_epg_programs',
      () => _searchEpg(playlistId, query, limit),
      metadata: {'playlistId': playlistId, 'limit': limit},
    );
  }

  Future<List<ContentItem>> _searchEpg(
    String playlistId,
    String query,
    int limit,
  ) async {
    final like = '%${query.replaceAll('%', r'\%').replaceAll('_', r'\_')}%';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      final rows = await database
          .customSelect(
            '''
SELECT
  ls.stream_id AS content_id,
  'live' AS content_type,
  ep.title AS name,
  COALESCE(ls.stream_icon, '') AS image_url,
  'EPG • ' || COALESCE(ec.display_name, ls.name) || ' • ' ||
    strftime('%H:%M', ep.start_time / 1000, 'unixepoch', 'localtime') ||
    ' - ' ||
    strftime('%H:%M', ep.end_time / 1000, 'unixepoch', 'localtime') ||
    CASE
      WHEN ep.description IS NOT NULL AND ep.description != '' THEN ' • ' || ep.description
      ELSE ''
    END AS description,
  ls.category_id AS category_id,
  ls.epg_channel_id AS epg_channel_id,
  NULL AS rating,
  NULL AS rating5based,
  NULL AS container_extension,
  NULL AS genre,
  ep.description AS plot,
  NULL AS release_date
FROM epg_programs ep
LEFT JOIN epg_channels ec
  ON ec.playlist_id = ep.playlist_id
 AND ec.epg_channel_id = ep.epg_channel_id
JOIN live_streams ls
  ON ls.playlist_id = ep.playlist_id
 AND (
   ls.epg_channel_id = ep.epg_channel_id
   OR LOWER(ls.name) = LOWER(ec.display_name)
 )
WHERE ep.playlist_id = ?
  AND ep.end_time >= ?
  AND (
    ep.title LIKE ? ESCAPE '\\'
    OR COALESCE(ep.description, '') LIKE ? ESCAPE '\\'
    OR COALESCE(ec.display_name, '') LIKE ? ESCAPE '\\'
  )
GROUP BY ls.stream_id, ep.program_id
ORDER BY ep.start_time ASC
LIMIT ?
''',
            variables: [
              Variable.withString(playlistId),
              Variable.withInt(now),
              Variable.withString(like),
              Variable.withString(like),
              Variable.withString(like),
              Variable.withInt(limit),
            ],
          )
          .get();

      return rows.map(_rowToContentItem).toList();
    } catch (_) {
      return const [];
    }
  }

  String _ftsQuery(String query) {
    return query
        .replaceAll(RegExp(r'[^a-zA-Z0-9\u00C0-\uFFFF]+'), ' ')
        .split(RegExp(r'\s+'))
        .where((part) => part.isNotEmpty)
        .map((part) => '"${part.replaceAll('"', '""')}"*')
        .join(' ');
  }

  String? _contentTypeName(ContentType? type) {
    return switch (type) {
      ContentType.liveStream => 'live',
      ContentType.vod => 'vod',
      ContentType.series => 'series',
      null => null,
    };
  }

  ContentItem _rowToContentItem(QueryRow row) {
    final type = switch (row.read<String>('content_type')) {
      'live' => ContentType.liveStream,
      'vod' => ContentType.vod,
      'series' => ContentType.series,
      _ => ContentType.liveStream,
    };

    final id = row.read<String>('content_id');
    final name = row.read<String>('name');
    final imageUrl = row.read<String>('image_url');
    final categoryId = row.readNullable<String>('category_id') ?? '';
    final rating = row.readNullable<String>('rating') ?? '';
    final rating5based = row.readNullable<double>('rating5based') ?? 0.0;
    final containerExtension =
        row.readNullable<String>('container_extension') ?? '';
    final genre = row.readNullable<String>('genre');
    final plot = row.readNullable<String>('plot');
    final releaseDate = row.readNullable<String>('release_date');

    final liveStream = type == ContentType.liveStream
        ? LiveStream(
            streamId: id,
            name: name,
            streamIcon: imageUrl,
            categoryId: categoryId,
            epgChannelId: row.readNullable<String>('epg_channel_id') ?? '',
          )
        : null;
    final vodStream = type == ContentType.vod
        ? VodStream(
            streamId: id,
            name: name,
            streamIcon: imageUrl,
            categoryId: categoryId,
            rating: rating,
            rating5based: rating5based,
            containerExtension: containerExtension,
            createdAt: null,
            genre: genre,
          )
        : null;
    final seriesStream = type == ContentType.series
        ? SeriesStream(
            playlistId: '',
            seriesId: id,
            name: name,
            cover: imageUrl,
            plot: plot,
            genre: genre,
            releaseDate: releaseDate,
            rating: rating,
            rating5based: rating5based,
            categoryId: categoryId,
          )
        : null;

    return ContentItem(
      id,
      name,
      imageUrl,
      type,
      description: row.readNullable<String>('description'),
      containerExtension: containerExtension,
      liveStream: liveStream,
      vodStream: vodStream,
      seriesStream: seriesStream,
    );
  }
}
