import 'package:watchio/screens/m3u/series/m3u_series_screen.dart';
import 'package:watchio/screens/player/unified_player_screen.dart';
import 'package:watchio/utils/get_playlist_type.dart';
import 'package:flutter/material.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/models/playlist_content_model.dart';
import '../screens/movies/movie_details_screen.dart';
import '../screens/series/series_details_screen.dart';

void navigateByContentType(BuildContext context, ContentItem content) {
  if (content.contentType == ContentType.liveStream) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => UnifiedPlayerScreen(contentItem: content),
      ),
    );
    return;
  }

  switch (content.contentType) {
    case ContentType.vod:
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => MovieDetailsScreen(contentItem: content),
        ),
      );
    case ContentType.series:
      if (isXtreamCode) {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => SeriesDetailsScreen(contentItem: content),
          ),
        );
      } else {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => M3uSeriesScreen(contentItem: content),
          ),
        );
      }
    default:
      // Fallback for any other type
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => UnifiedPlayerScreen(contentItem: content),
        ),
      );
  }
}
