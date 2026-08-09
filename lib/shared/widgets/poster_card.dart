import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../utils/firestick_performance.dart';
import 'focus_wrapper.dart';

class PosterCard extends StatefulWidget {
  final String title;
  final String? subtitle;
  final String? imageUrl;
  final String? rating;
  final String? year;
  final String? metaBadge;
  final bool isFavorite;
  final bool showImage;
  final bool showTitle;
  final bool showRating;
  final Color? accentColor;
  final Color? glowColor;
  final VoidCallback onTap;
  final VoidCallback? onFavoriteTap;

  const PosterCard({
    super.key,
    required this.title,
    this.subtitle,
    this.imageUrl,
    this.rating,
    this.year,
    this.metaBadge,
    this.isFavorite = false,
    this.showImage = true,
    this.showTitle = true,
    this.showRating = true,
    this.accentColor,
    this.glowColor,
    required this.onTap,
    this.onFavoriteTap,
  });

  @override
  State<PosterCard> createState() => _PosterCardState();
}

class _PosterCardState extends State<PosterCard> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final accent = widget.accentColor ?? Theme.of(context).colorScheme.primary;
    final glow = widget.glowColor ?? Theme.of(context).colorScheme.secondary;

    return RepaintBoundary(
      child: FocusWrapper(
        onPressed: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        scale: 1.045,
        showGlow: false,
        showBorder: false,
        onFocusChange: (value) => setState(() => _focused = value),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Stack(
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(12),
                      boxShadow: _focused
                          ? [
                              BoxShadow(
                                color: glow.withValues(alpha: 0.38),
                                blurRadius: 14,
                                spreadRadius: 1,
                              ),
                            ]
                          : null,
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: Stack(
                        fit: StackFit.expand,
                        children: [
                          if (widget.showImage &&
                              widget.imageUrl != null &&
                              widget.imageUrl!.isNotEmpty)
                            CachedNetworkImage(
                              imageUrl: widget.imageUrl!,
                              fit: BoxFit.cover,
                              width: double.infinity,
                              height: double.infinity,
                              memCacheWidth: firestickPerformanceMode
                                  ? 260
                                  : 420,
                              maxWidthDiskCache: firestickPerformanceMode
                                  ? 420
                                  : 700,
                              fadeInDuration: Duration.zero,
                              fadeOutDuration: Duration.zero,
                              placeholder: (context, url) => Container(
                                color: Colors.grey.withValues(alpha: 0.1),
                              ),
                              errorWidget: (context, url, error) => Container(
                                color: Colors.grey.withValues(alpha: 0.1),
                                child: const Icon(Icons.movie, size: 50),
                              ),
                            )
                          else
                            Container(
                              color: Colors.grey.withValues(alpha: 0.1),
                              child: const Icon(Icons.movie, size: 50),
                            ),
                          AnimatedOpacity(
                            opacity: _focused ? 1 : 0,
                            duration: const Duration(milliseconds: 180),
                            child: DecoratedBox(
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                  colors: [
                                    accent.withValues(alpha: 0.22),
                                    glow.withValues(alpha: 0.12),
                                  ],
                                ),
                                border: Border.all(color: accent, width: 3),
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  if (widget.showRating && widget.rating != null)
                    Positioned(
                      top: 8,
                      right: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.7),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              Icons.star,
                              color: Colors.amber,
                              size: 12,
                            ),
                            const SizedBox(width: 4),
                            Text(
                              widget.rating!,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (widget.metaBadge != null)
                    Positioned(
                      top: 8,
                      left: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: Theme.of(
                            context,
                          ).primaryColor.withValues(alpha: 0.8),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          widget.metaBadge!,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  if (widget.onFavoriteTap != null)
                    Positioned(
                      bottom: 8,
                      right: 8,
                      child: FocusWrapper(
                        onPressed: widget.onFavoriteTap,
                        borderRadius: BorderRadius.circular(20),
                        scale: 1.1,
                        child: Container(
                          padding: const EdgeInsets.all(6),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.5),
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            widget.isFavorite
                                ? Icons.favorite
                                : Icons.favorite_border,
                            color: widget.isFavorite
                                ? Colors.red
                                : Colors.white,
                            size: 16,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            if (widget.showTitle) ...[
              const SizedBox(height: 8),
              SizedBox(
                height: widget.subtitle != null || widget.year != null
                    ? 34
                    : 18,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        height: 1.15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    if (widget.subtitle != null)
                      Text(
                        widget.subtitle!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          height: 1.1,
                          color: Colors.white.withValues(alpha: 0.5),
                        ),
                      )
                    else if (widget.year != null)
                      Text(
                        widget.year!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          height: 1.1,
                          color: Colors.white.withValues(alpha: 0.5),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
