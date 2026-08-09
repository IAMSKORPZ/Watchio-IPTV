import 'package:watchio/models/epg_models.dart';

abstract class EpgRepository {
  Future<EpgProgram?> getCurrentProgram(String channelId);

  Future<EpgProgram?> getNextProgram(String channelId);

  Future<EpgSchedule> getScheduleWindow({
    required String channelId,
    required DateTime from,
    required DateTime to,
  });

  Future<void> upsertPrograms(List<EpgProgram> programs);

  Future<void> cleanupExpired();
}

class InMemoryEpgRepository implements EpgRepository {
  final Duration maxAge;
  final int maxPrograms;
  final Map<String, List<EpgProgram>> _programsByChannel = {};
  final Map<String, EpgSchedule> _windowCache = {};

  InMemoryEpgRepository({
    this.maxAge = const Duration(days: 2),
    this.maxPrograms = 50000,
  });

  @override
  Future<EpgProgram?> getCurrentProgram(String channelId) async {
    final now = DateTime.now();
    final schedule = await getScheduleWindow(
      channelId: channelId,
      from: now.subtract(const Duration(minutes: 10)),
      to: now.add(const Duration(hours: 1)),
    );
    return schedule.currentProgram;
  }

  @override
  Future<EpgProgram?> getNextProgram(String channelId) async {
    final now = DateTime.now();
    final schedule = await getScheduleWindow(
      channelId: channelId,
      from: now,
      to: now.add(const Duration(hours: 6)),
    );
    return schedule.nextProgram;
  }

  @override
  Future<EpgSchedule> getScheduleWindow({
    required String channelId,
    required DateTime from,
    required DateTime to,
  }) async {
    final cacheKey = _cacheKey(channelId, from, to);
    final cached = _windowCache[cacheKey];
    if (cached != null && DateTime.now().difference(cached.cachedAt) < maxAge) {
      return cached;
    }

    final programs = (_programsByChannel[channelId] ?? [])
        .where((program) => program.overlaps(from, to))
        .toList()
      ..sort((a, b) => a.startAt.compareTo(b.startAt));
    final schedule = EpgSchedule(
      channelId: channelId,
      windowStart: from,
      windowEnd: to,
      programs: programs,
      cachedAt: DateTime.now(),
    );
    _windowCache[cacheKey] = schedule;
    return schedule;
  }

  @override
  Future<void> upsertPrograms(List<EpgProgram> programs) async {
    for (final program in programs) {
      final channelPrograms =
          _programsByChannel.putIfAbsent(program.channelId, () => []);
      channelPrograms.removeWhere((item) => item.id == program.id);
      channelPrograms.add(program);
      channelPrograms.sort((a, b) => a.startAt.compareTo(b.startAt));
    }
    _trimIfNeeded();
    _windowCache.clear();
  }

  @override
  Future<void> cleanupExpired() async {
    final cutoff = DateTime.now().subtract(maxAge);
    for (final entry in _programsByChannel.entries) {
      entry.value.removeWhere((program) => program.endAt.isBefore(cutoff));
    }
    _programsByChannel.removeWhere((_, programs) => programs.isEmpty);
    _windowCache.removeWhere(
      (_, schedule) => DateTime.now().difference(schedule.cachedAt) > maxAge,
    );
  }

  String _cacheKey(String channelId, DateTime from, DateTime to) {
    return '$channelId:${from.millisecondsSinceEpoch}:${to.millisecondsSinceEpoch}';
  }

  void _trimIfNeeded() {
    final all = _programsByChannel.values.expand((items) => items).toList();
    if (all.length <= maxPrograms) return;
    all.sort((a, b) => a.endAt.compareTo(b.endAt));
    final removeCount = all.length - maxPrograms;
    final removeIds = all.take(removeCount).map((item) => item.id).toSet();
    for (final entry in _programsByChannel.entries) {
      entry.value.removeWhere((program) => removeIds.contains(program.id));
    }
  }
}
