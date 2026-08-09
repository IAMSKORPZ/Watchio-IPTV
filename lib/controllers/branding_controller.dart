import 'package:watchio/models/announcement_model.dart';
import 'package:watchio/models/branding_model.dart';
import 'package:watchio/models/maintenance_model.dart';
import 'package:watchio/models/theme_model.dart';
import 'package:watchio/models/update_info_model.dart';
import 'package:watchio/services/branding_service.dart';
import 'package:watchio/services/remote_config_service.dart';
import 'package:flutter/material.dart';

class BrandingController extends ChangeNotifier {
  final BrandingService service;

  BrandingController({BrandingService? service})
      : service = service ?? BrandingService();

  BrandingModel branding = BrandingModel.defaults;
  RemoteThemeModel remoteTheme = RemoteThemeModel.defaults;
  List<AnnouncementModel> announcements = const [];
  MaintenanceModel maintenance = MaintenanceModel.defaults;
  UpdateInfoModel updateInfo = UpdateInfoModel.defaults;
  String sourceName = 'Built-in defaults';
  DateTime? lastSyncTime;
  bool usingCache = false;
  bool isLoading = false;
  String? error;

  List<AnnouncementModel> get activeAnnouncements =>
      announcements.where((item) => !item.isExpired).toList()
        ..sort((a, b) => b.priority.compareTo(a.priority));

  bool get hasCache => usingCache || lastSyncTime != null;

  Future<void> load({bool forceRefresh = false}) async {
    isLoading = true;
    error = null;
    notifyListeners();

    try {
      final snapshot = await service.loadAll(forceRefresh: forceRefresh);
      _apply(snapshot);
    } catch (e) {
      error = e.toString();
    } finally {
      isLoading = false;
      notifyListeners();
    }
  }

  ThemeData applyRemoteTheme(ThemeData base) {
    return remoteTheme.applyTo(base);
  }

  void _apply(RemoteConfigSnapshot snapshot) {
    branding = snapshot.branding;
    remoteTheme = snapshot.theme;
    announcements = snapshot.announcements;
    maintenance = snapshot.maintenance;
    updateInfo = snapshot.updateInfo;
    sourceName = snapshot.sourceName;
    lastSyncTime = snapshot.lastSyncTime;
    usingCache = snapshot.usingCache;
  }
}
