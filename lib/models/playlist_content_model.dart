import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/live_stream.dart';
import 'package:watchio/models/m3u_item.dart';
import 'package:watchio/models/series.dart';
import 'package:watchio/models/vod_streams.dart';
import 'package:watchio/utils/build_media_url.dart';
import 'package:watchio/utils/get_playlist_type.dart';

class ContentItem {
  final String id;
  final String name;
  final String imagePath;
  final String? description;
  final Duration? duration;
  final String? coverPath;
  final String? containerExtension;
  final ContentType contentType;
  final LiveStream? liveStream;
  final VodStream? vodStream;
  final SeriesStream? seriesStream;
  final int? season;
  final M3uItem? m3uItem;
  final DateTime? catchupStartTime;
  final int? catchupDurationMinutes;

  ContentItem(
    this.id,
    this.name,
    this.imagePath,
    this.contentType, {
    this.description,
    this.duration,
    this.coverPath,
    this.containerExtension,
    this.liveStream,
    this.vodStream,
    this.seriesStream,
    this.season,
    this.m3uItem,
    this.catchupStartTime,
    this.catchupDurationMinutes,
  });

  String get url {
    if (isM3u) {
      return m3uItem?.url ?? id;
    }
    if (isXtreamCode) {
      return buildMediaUrl(this);
    }
    if (isStalker) {
      // Assuming buildMediaUrl can handle stalker or needs a separate builder
      // For now, if buildMediaUrl doesn't support it, we might need a separate call.
      return buildMediaUrl(this);
    }
    return m3uItem?.url ?? id;
  }

  ContentItem copyWith({
    String? id,
    String? name,
    String? imagePath,
    ContentType? contentType,
    String? description,
    Duration? duration,
    String? coverPath,
    String? containerExtension,
    LiveStream? liveStream,
    VodStream? vodStream,
    SeriesStream? seriesStream,
    int? season,
    M3uItem? m3uItem,
    DateTime? catchupStartTime,
    int? catchupDurationMinutes,
  }) {
    return ContentItem(
      id ?? this.id,
      name ?? this.name,
      imagePath ?? this.imagePath,
      contentType ?? this.contentType,
      description: description ?? this.description,
      duration: duration ?? this.duration,
      coverPath: coverPath ?? this.coverPath,
      containerExtension: containerExtension ?? this.containerExtension,
      liveStream: liveStream ?? this.liveStream,
      vodStream: vodStream ?? this.vodStream,
      seriesStream: seriesStream ?? this.seriesStream,
      season: season ?? this.season,
      m3uItem: m3uItem ?? this.m3uItem,
      catchupStartTime: catchupStartTime ?? this.catchupStartTime,
      catchupDurationMinutes: catchupDurationMinutes ?? this.catchupDurationMinutes,
    );
  }
}
