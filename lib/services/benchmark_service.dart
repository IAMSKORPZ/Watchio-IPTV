import 'dart:convert';
import 'dart:io';

import 'package:watchio/models/benchmark_result_model.dart';
import 'package:watchio/services/performance_service.dart';
import 'package:path_provider/path_provider.dart';

class BenchmarkService {
  final List<BenchmarkResultModel> _results = [];

  List<BenchmarkResultModel> get results => List.unmodifiable(_results);

  Future<T> measure<T>(
    String category,
    Future<T> Function() action, {
    String device = BenchmarkResultModel.pendingRealDeviceTesting,
    String notes = BenchmarkResultModel.pendingRealDeviceTesting,
  }) async {
    final stopwatch = Stopwatch()..start();
    try {
      return await PerformanceService.track('benchmark_$category', action);
    } finally {
      stopwatch.stop();
      _results.add(
        BenchmarkResultModel(
          id: '${category}_${DateTime.now().microsecondsSinceEpoch}',
          category: category,
          capturedAt: DateTime.now(),
          duration: stopwatch.elapsed,
          device: device,
          notes: notes,
        ),
      );
    }
  }

  void addPendingHook(String category) {
    _results.add(
      BenchmarkResultModel(
        id: '${category}_${DateTime.now().microsecondsSinceEpoch}',
        category: category,
        capturedAt: DateTime.now(),
      ),
    );
  }

  String exportJson() {
    return const JsonEncoder.withIndent('  ')
        .convert(_results.map((result) => result.toJson()).toList());
  }

  Future<File> exportJsonFile({String fileName = 'Watchio IPTV_benchmarks.json'}) async {
    final directory = await getApplicationDocumentsDirectory();
    final file = File('${directory.path}${Platform.pathSeparator}$fileName');
    return file.writeAsString(exportJson());
  }

  void clear() {
    _results.clear();
  }
}
