import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';
import 'package:watchio/services/app_state.dart';

import '../models/content_type.dart';
import '../models/playlist_content_model.dart';
import '../models/playlist_model.dart';

String buildMediaUrl(ContentItem contentItem) {
  final playlist = AppState.currentPlaylist;
  if (playlist == null) {
    debugPrint(
      'buildMediaUrl: CRITICAL ERROR - AppState.currentPlaylist is NULL',
    );
    return '';
  }

  String baseUrl = playlist.url ?? '';
  if (baseUrl.isEmpty) {
    debugPrint('buildMediaUrl: CRITICAL ERROR - playlist.url is EMPTY');
    return '';
  }

  // Robust Sanitization: Extract just the protocol, domain and port
  try {
    final uri = Uri.parse(baseUrl.trim());
    baseUrl = '${uri.scheme}://${uri.host}${uri.hasPort ? ":${uri.port}" : ""}';
  } catch (e) {
    debugPrint('buildMediaUrl: URI Parse Warning: $e');
    // Fallback to basic string cleaning
    baseUrl = baseUrl.trim();
    if (baseUrl.contains('/player_api.php')) {
      baseUrl = baseUrl.split('/player_api.php')[0];
    }
    if (baseUrl.contains('/xmltv.php')) {
      baseUrl = baseUrl.split('/xmltv.php')[0];
    }
    if (baseUrl.contains('/enigma2.php')) {
      baseUrl = baseUrl.split('/enigma2.php')[0];
    }
    if (baseUrl.contains('/m3u_plus')) {
      baseUrl = baseUrl.split('/m3u_plus')[0];
    }

    while (baseUrl.endsWith('/')) {
      baseUrl = baseUrl.substring(0, baseUrl.length - 1);
    }

    if (!baseUrl.startsWith('http://') && !baseUrl.startsWith('https://')) {
      baseUrl = 'http://$baseUrl';
    }
  }

  final username = playlist.username ?? '';
  final password = playlist.password ?? '';

  if (playlist.type == PlaylistType.xtream &&
      (username.isEmpty || password.isEmpty)) {
    debugPrint(
      'buildMediaUrl: CRITICAL ERROR - username or password is EMPTY for Xtream playlist: ${playlist.name}',
    );
    return '';
  }

  String finalUrl = '';

  // Handle Catchup first
  if (contentItem.catchupStartTime != null &&
      contentItem.catchupDurationMinutes != null) {
    final startTimeStr = DateFormat(
      'yyyy-MM-dd:HH-mm',
    ).format(contentItem.catchupStartTime!);
    final duration = contentItem.catchupDurationMinutes!;
    finalUrl =
        '$baseUrl/timeshift/$username/$password/$duration/$startTimeStr/${contentItem.id}.ts';
  } else {
    switch (contentItem.contentType) {
      case ContentType.liveStream:
        if (playlist.type == PlaylistType.xtream) {
          finalUrl = '$baseUrl/live/$username/$password/${contentItem.id}.ts';
        } else if (playlist.type == PlaylistType.stalker) {
          // Typical Stalker live stream format
          finalUrl = '$baseUrl/live/${contentItem.id}';
        } else {
          finalUrl = contentItem.m3uItem?.url ?? contentItem.id;
        }
        break;
      case ContentType.vod:
        final ext = contentItem.containerExtension;
        final suffix = (ext != null && ext.isNotEmpty) ? '.$ext' : '';
        if (playlist.type == PlaylistType.xtream) {
          finalUrl =
              '$baseUrl/movie/$username/$password/${contentItem.id}$suffix';
        } else if (playlist.type == PlaylistType.stalker) {
          finalUrl = '$baseUrl/vod/${contentItem.id}$suffix';
        } else {
          finalUrl = contentItem.m3uItem?.url ?? contentItem.id;
        }
        break;
      case ContentType.series:
        final ext = contentItem.containerExtension;
        final suffix = (ext != null && ext.isNotEmpty) ? '.$ext' : '';
        if (playlist.type == PlaylistType.xtream) {
          finalUrl =
              '$baseUrl/series/$username/$password/${contentItem.id}$suffix';
        } else if (playlist.type == PlaylistType.stalker) {
          finalUrl = '$baseUrl/series/${contentItem.id}$suffix';
        } else {
          finalUrl = contentItem.m3uItem?.url ?? contentItem.id;
        }
        break;
    }
  }

  debugPrint(
    'buildMediaUrl: URL generated for ${contentItem.contentType.name} '
    'stream ${contentItem.id}',
  );
  return finalUrl;
}
