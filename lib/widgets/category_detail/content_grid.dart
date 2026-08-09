import 'package:flutter/material.dart';
import 'package:watchio/models/playlist_content_model.dart';
import 'package:watchio/models/content_type.dart';
import 'package:watchio/shared/widgets/poster_card.dart';
import 'package:watchio/utils/responsive_helper.dart';

import '../content_card.dart';

class ContentGrid extends StatelessWidget {
  final List<ContentItem> items;
  final Function(ContentItem) onItemTap;
  final VoidCallback? onLoadMore;
  final bool isLoadingMore;

  const ContentGrid({
    super.key,
    required this.items,
    required this.onItemTap,
    this.onLoadMore,
    this.isLoadingMore = false,
  });

  @override
  Widget build(BuildContext context) {
    final totalItems = items.length + (isLoadingMore ? 1 : 0);

    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.extentAfter < 800) {
          onLoadMore?.call();
        }
        return false;
      },
      child: GridView.builder(
        padding: const EdgeInsets.all(16),
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: ResponsiveHelper.getCrossAxisCount(context),
          childAspectRatio: 0.65,
          crossAxisSpacing: 8,
          mainAxisSpacing: 8,
        ),
        itemCount: totalItems,
        itemBuilder: (context, index) {
          if (index >= items.length) {
            return const Center(child: CircularProgressIndicator());
          }
          final item = items[index];
          if (item.contentType == ContentType.vod ||
              item.contentType == ContentType.series) {
            final isSeries = item.contentType == ContentType.series;
            return PosterCard(
              title: item.name,
              imageUrl: item.imagePath,
              rating: item.vodStream?.rating.isNotEmpty == true
                  ? item.vodStream!.rating
                  : item.seriesStream?.rating,
              subtitle: isSeries
                  ? _seriesSubtitle(item)
                  : item.vodStream?.genre,
              metaBadge: isSeries ? 'Series' : null,
              onTap: () => onItemTap(item),
            );
          }
          return ContentCard(
            content: item,
            width: 150,
            onTap: () => onItemTap(item),
          );
        },
      ),
    );
  }

  String? _seriesSubtitle(ContentItem item) {
    final releaseDate = item.seriesStream?.releaseDate;
    final genre = item.seriesStream?.genre;
    if (releaseDate != null && releaseDate.trim().isNotEmpty) {
      return releaseDate.trim();
    }
    if (genre != null && genre.trim().isNotEmpty) {
      return genre.trim();
    }
    return null;
  }
}
