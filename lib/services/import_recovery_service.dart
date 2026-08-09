import 'dart:convert';

import 'package:watchio/models/import_session_model.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ImportRecoveryService {
  static const _sessionsKey = 'Watchio IPTV.import_sessions.v1';

  Future<List<ImportSessionModel>> loadSessions() async {
    final prefs = await SharedPreferences.getInstance();
    final encoded = prefs.getString(_sessionsKey);
    if (encoded == null || encoded.isEmpty) return [];
    final decoded = jsonDecode(encoded) as List<dynamic>;
    return decoded
        .map((item) => _fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<void> saveSession(ImportSessionModel session) async {
    final sessions = await loadSessions();
    final index = sessions.indexWhere((item) => item.id == session.id);
    if (index == -1) {
      sessions.add(session);
    } else {
      sessions[index] = session;
    }
    await _saveAll(sessions);
  }

  Future<List<ImportSessionModel>> activeSessions() async {
    final sessions = await loadSessions();
    return sessions
        .where((session) =>
            session.status == ImportSessionStatus.pending ||
            session.status == ImportSessionStatus.running)
        .toList();
  }

  Future<void> markCompleted(String id) async {
    await _mark(id, ImportSessionStatus.completed);
  }

  Future<void> markFailed(String id, String reason) async {
    await _mark(id, ImportSessionStatus.failed, failureReason: reason);
  }

  Future<void> markCancelled(String id) async {
    await _mark(id, ImportSessionStatus.cancelled);
  }

  Future<void> rollback(String id) async {
    final sessions = await loadSessions();
    sessions.removeWhere((session) => session.id == id);
    await _saveAll(sessions);
  }

  Future<void> _mark(
    String id,
    ImportSessionStatus status, {
    String? failureReason,
  }) async {
    final sessions = await loadSessions();
    final index = sessions.indexWhere((session) => session.id == id);
    if (index == -1) return;
    sessions[index] = sessions[index].copyWith(
      status: status,
      finishedAt: DateTime.now(),
      failureReason: failureReason,
    );
    await _saveAll(sessions);
  }

  Future<void> _saveAll(List<ImportSessionModel> sessions) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _sessionsKey,
      jsonEncode(sessions.map(_toJson).toList()),
    );
  }

  Map<String, dynamic> _toJson(ImportSessionModel session) {
    return {
      'id': session.id,
      'providerId': session.providerId,
      'type': session.type,
      'status': session.status.name,
      'startedAt': session.startedAt.toIso8601String(),
      'finishedAt': session.finishedAt?.toIso8601String(),
      'failureReason': session.failureReason,
    };
  }

  ImportSessionModel _fromJson(Map<String, dynamic> json) {
    return ImportSessionModel(
      id: json['id'] as String,
      providerId: json['providerId'] as String,
      type: json['type'] as String,
      status: ImportSessionStatus.values.firstWhere(
        (status) => status.name == json['status'],
        orElse: () => ImportSessionStatus.failed,
      ),
      startedAt: DateTime.tryParse(json['startedAt'] as String? ?? '') ??
          DateTime.now(),
      finishedAt: DateTime.tryParse(json['finishedAt'] as String? ?? ''),
      failureReason: json['failureReason'] as String?,
    );
  }
}
