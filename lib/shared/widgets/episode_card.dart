import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'focus_wrapper.dart';

class EpisodeCard extends StatelessWidget {
  final String title;
  final String episodeNumber;
  final String? imageUrl;
  final String? duration;
  final String? description;
  final VoidCallback onTap;
  final double? progress;

  const EpisodeCard({
    super.key,
    required this.title,
    required this.episodeNumber,
    this.imageUrl,
    this.duration,
    this.description,
    required this.onTap,
    this.progress,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return FocusWrapper(
      onPressed: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Stack(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: imageUrl != null && imageUrl!.isNotEmpty
                    ? CachedNetworkImage(
                        imageUrl: imageUrl!,
                        width: 140,
                        height: 80,
                        fit: BoxFit.cover,
                        placeholder: (context, url) => Container(
                          width: 140,
                          height: 80,
                          color: Colors.grey.withValues(alpha: 0.1),
                          child: const Center(
                            child: CircularProgressIndicator(),
                          ),
                        ),
                        errorWidget: (context, url, error) => Container(
                          width: 140,
                          height: 80,
                          color: Colors.grey.withValues(alpha: 0.1),
                          child: const Icon(Icons.play_circle_outline),
                        ),
                      )
                    : Container(
                        width: 140,
                        height: 80,
                        color: Colors.grey.withValues(alpha: 0.1),
                        child: const Icon(Icons.play_circle_outline),
                      ),
              ),
              if (progress != null)
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: Container(
                    height: 4,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.2),
                      borderRadius: const BorderRadius.vertical(
                        bottom: Radius.circular(12),
                      ),
                    ),
                    child: FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: progress!.clamp(0.0, 1.0),
                      child: Container(
                        decoration: BoxDecoration(
                          color: colorScheme.primary,
                          borderRadius: const BorderRadius.vertical(
                            bottom: Radius.circular(12),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              const Positioned.fill(
                child: Center(
                  child: Icon(Icons.play_arrow, color: Colors.white, size: 30),
                ),
              ),
            ],
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$episodeNumber. $title',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (duration != null)
                  Text(
                    duration!,
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.white.withValues(alpha: 0.5),
                    ),
                  ),
                if (description != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 4.0),
                    child: Text(
                      description!,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 13,
                        color: Colors.white.withValues(alpha: 0.7),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
