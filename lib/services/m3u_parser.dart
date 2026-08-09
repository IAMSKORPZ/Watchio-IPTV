import 'package:flutter/foundation.dart';
import 'dart:convert' show utf8;
import 'dart:io' show File, HttpClient;
import 'package:watchio/models/content_type.dart';
import 'package:uuid/uuid.dart';
import '../models/m3u_item.dart';

class M3uParser {
  static Future<List<M3uItem>> parseM3uFile(Map<String, String> params) async {
    return await M3uParser.parseFile(params['id']!, params['filePath']!);
  }

  static Future<List<M3uItem>> parseFile(
    String playlistId,
    String filePath,
  ) async {
    try {
      final file = File(filePath);
      final content = await file.readAsString(encoding: utf8);
      return parseM3u(playlistId, content);
    } catch (e) {
      debugPrint('M3U file parse error: $e');
      throw Exception('M3U dosyası okunamadı: ${e.toString()}');
    }
  }

  static Future<List<M3uItem>> parseM3uUrl(Map<String, String> params) async {
    return await M3uParser.parseUrl(params['id']!, params['url']!);
  }

  static Future<List<M3uItem>> parseUrl(String playlistId, String url) async {
    try {
      final client = HttpClient();
      final request = await client.getUrl(Uri.parse(url));
      final response = await request.close();

      if (response.statusCode != 200) {
        throw Exception(
          'HTTP ${response.statusCode}: M3U URL\'sine erişilemedi',
        );
      }

      final content = await response.transform(utf8.decoder).join();
      client.close();
      return parseM3u(playlistId, content);
    } catch (e) {
      debugPrint('M3U URL parse error: $e');
      throw Exception('M3U URL\'si okunamadı: ${e.toString()}');
    }
  }

  static Map<String, List<M3uItem>> groupChannels(List<M3uItem> channels) {
    final grouped = <String, List<M3uItem>>{};

    for (final channel in channels) {
      final group = channel.groupTitle ?? 'Diğer';
      grouped.putIfAbsent(group, () => []).add(channel);
    }

    final sortedKeys = grouped.keys.toList()..sort();
    final sortedGrouped = <String, List<M3uItem>>{};

    for (final key in sortedKeys) {
      sortedGrouped[key] = grouped[key]!;
    }

    return sortedGrouped;
  }

  static List<M3uItem> parseM3u(String playlistId, String content) {
    final uuid = Uuid();

    final lines = content.split('\n').map((e) => e.trim()).toList();
    final List<M3uItem> items = [];

    Map<String, String?> currentMeta = {};
    String? currentName;

    for (int i = 0; i < lines.length; i++) {
      final line = lines[i];

      if (line.startsWith('#EXTINF')) {
        final commaIndex = line.indexOf(',');
        final metadataPart = (commaIndex != -1)
            ? line.substring(0, commaIndex)
            : line;

        currentName = (commaIndex != -1)
            ? line.substring(commaIndex + 1).trim()
            : null;

        currentMeta = {
          'tvg-id': _extractAttribute(metadataPart, 'tvg-id'),
          'tvg-name': _extractAttribute(metadataPart, 'tvg-name'),
          'tvg-logo': _extractAttribute(metadataPart, 'tvg-logo'),
          'tvg-url': _extractAttribute(metadataPart, 'tvg-url'),
          'tvg-rec': _extractAttribute(metadataPart, 'tvg-rec'),
          'tvg-shift': _firstNonBlank(
            _extractAttribute(metadataPart, 'tvg-shift'),
            _extractAttribute(metadataPart, 'timeshift'),
          ),
          'group-title': _extractAttribute(metadataPart, 'group-title'),
          'user-agent': _extractAttribute(metadataPart, 'user-agent'),
          'http-user-agent': _extractAttribute(metadataPart, 'http-user-agent'),
          'referrer': _firstNonBlank(
            _extractAttribute(metadataPart, 'referrer'),
            _extractAttribute(metadataPart, 'http-referrer'),
          ),
          'catchup': _extractAttribute(metadataPart, 'catchup'),
          'catchup-source': _extractAttribute(metadataPart, 'catchup-source'),
          'catchup-days': _extractAttribute(metadataPart, 'catchup-days'),
          'tvg-chno': _firstNonBlank(
            _extractAttribute(metadataPart, 'tvg-chno'),
            _extractAttribute(metadataPart, 'channel-number'),
          ),
        };
      } else if (line.startsWith('#EXTGRP:')) {
        currentMeta['group-name'] = line.substring(8).trim();
      } else if (line.isNotEmpty && !line.startsWith('#')) {
        final url = line;
        final name = _firstNonBlank(
          currentName,
          currentMeta['tvg-name'],
          currentMeta['tvg-id'],
          _filenameFromUrl(url),
        );
        final groupTitle = _firstNonBlank(
          currentMeta['group-title'],
          currentMeta['group-name'],
          'Diğer',
        );

        items.add(
          M3uItem(
            id: uuid.v4(),
            playlistId: playlistId,
            url: url,
            contentType: _detectContentType(url),
            name: name,
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
          ),
        );

        currentMeta.clear();
        currentName = null;
      }
    }

    return items;
  }

  static String? _extractAttribute(String line, String attribute) {
    final regex = RegExp(
      '\\b${RegExp.escape(attribute)}\\s*=\\s*("([^"]*)"|\'([^\']*)\'|([^\\s,]+))',
      caseSensitive: false,
    );
    final match = regex.firstMatch(line);
    final value = match?.group(2) ?? match?.group(3) ?? match?.group(4);
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }

  static String? _firstNonBlank(
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

  static String? _filenameFromUrl(String url) {
    final path = url.split(RegExp(r'[#?]')).first;
    final parts = path.split('/').where((part) => part.trim().isNotEmpty);
    if (parts.isEmpty) return null;
    return parts.last.trim();
  }

  static ContentType _detectContentType(String url) {
    final lowerUrl = url.toLowerCase();

    if (lowerUrl.contains('movie')) {
      return ContentType.vod;
    } else if (lowerUrl.contains('series')) {
      return ContentType.series;
    } else {
      return ContentType.liveStream;
    }
  }

  static ContentType detectContentType(String url) => _detectContentType(url);
}

class M3uTempSeries {
  final String name;
  final int seasonNumber;
  final int episodeNumber;
  final M3uItem m3uItem;

  M3uTempSeries(this.name, this.seasonNumber, this.episodeNumber, this.m3uItem);

  @override
  String toString() {
    return "$name $seasonNumber $episodeNumber";
  }
}

class SeriesParser {
  static final RegExp _seriesRegex = RegExp(
    r'^(.+?)\s+S(\d{1,2})\s+E(\d{1,3})',
    caseSensitive: false,
  );

  static final RegExp _alternativeRegex = RegExp(
    r'^(.+?)\s+Season\s+(\d{1,2})\s+Episode\s+(\d{1,3})',
    caseSensitive: false,
  );

  static M3uTempSeries? parse(M3uItem item) {
    if (item.name == null) {
      return null;
    }

    RegExpMatch? match = _seriesRegex.firstMatch(item.name!.trim());

    match ??= _alternativeRegex.firstMatch(item.name!.trim());

    if (match != null) {
      final seriesName = match.group(1)?.trim() ?? '';
      final seasonNumber = int.tryParse(match.group(2) ?? '') ?? 0;
      final episodeNumber = int.tryParse(match.group(3) ?? '') ?? 0;

      return M3uTempSeries(seriesName, seasonNumber, episodeNumber, item);
    }

    return null;
  }

  static String generateSeriesId(String playlistId, String seriesName) {
    return '${playlistId}_${seriesName.toLowerCase().replaceAll(' ', '_')}';
  }

  static String generateSeasonId(String seriesId, int seasonNumber) {
    return '${seriesId}_s${seasonNumber.toString().padLeft(2, '0')}';
  }

  static String generateEpisodeId(String seasonId, int episodeNumber) {
    return '${seasonId}_e${episodeNumber.toString().padLeft(2, '0')}';
  }
}
