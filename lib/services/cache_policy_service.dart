import 'dart:io';

import 'package:flutter/painting.dart';
import 'package:path_provider/path_provider.dart';

class CachePolicy {
  final Duration maxAge;
  final int maxBytes;

  const CachePolicy({
    this.maxAge = const Duration(days: 14),
    this.maxBytes = 256 * 1024 * 1024,
  });
}

class CachePolicyService {
  final CachePolicy policy;
  static bool? _lastTvImageCacheMode;

  const CachePolicyService({this.policy = const CachePolicy()});

  static void configureImageCache({required bool tvMode}) {
    if (_lastTvImageCacheMode == tvMode) return;
    _lastTvImageCacheMode = tvMode;

    final cache = PaintingBinding.instance.imageCache;
    cache.maximumSize = tvMode ? 80 : 160;
    cache.maximumSizeBytes = tvMode ? 48 * 1024 * 1024 : 96 * 1024 * 1024;
    cache.clearLiveImages();
  }

  Future<int> cleanupTemporaryCache() async {
    final dir = await getTemporaryDirectory();
    if (!await dir.exists()) return 0;

    final files = await dir
        .list(recursive: true, followLinks: false)
        .where((entity) => entity is File)
        .cast<File>()
        .toList();

    var removed = 0;
    final now = DateTime.now();
    var totalBytes = 0;
    final fileStats = <_CacheFile>[];

    for (final file in files) {
      try {
        final stat = await file.stat();
        totalBytes += stat.size;
        fileStats.add(_CacheFile(file, stat.size, stat.modified));
        if (now.difference(stat.modified) > policy.maxAge) {
          await file.delete();
          removed++;
        }
      } catch (_) {
        // Ignore cache files that disappear during cleanup.
      }
    }

    if (totalBytes <= policy.maxBytes) return removed;

    fileStats.sort((a, b) => a.modified.compareTo(b.modified));
    var currentBytes = totalBytes;
    for (final item in fileStats) {
      if (currentBytes <= policy.maxBytes) break;
      try {
        await item.file.delete();
        currentBytes -= item.size;
        removed++;
      } catch (_) {
        // Ignore cache files that disappear during cleanup.
      }
    }

    return removed;
  }
}

class _CacheFile {
  final File file;
  final int size;
  final DateTime modified;

  const _CacheFile(this.file, this.size, this.modified);
}
