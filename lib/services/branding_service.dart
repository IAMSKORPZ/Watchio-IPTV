import 'package:watchio/models/announcement_model.dart';
import 'package:watchio/models/branding_model.dart';
import 'package:watchio/models/maintenance_model.dart';
import 'package:watchio/models/theme_model.dart';
import 'package:watchio/models/update_info_model.dart';
import 'package:watchio/services/remote_config_service.dart';

class BrandingService {
  final RemoteConfigService remoteConfigService;

  BrandingService({RemoteConfigService? remoteConfigService})
      : remoteConfigService = remoteConfigService ?? RemoteConfigService();

  Future<RemoteConfigSnapshot> loadAll({bool forceRefresh = false}) {
    return remoteConfigService.load(forceRefresh: forceRefresh);
  }

  Future<BrandingModel> loadBranding() async {
    return (await loadAll()).branding;
  }

  Future<RemoteThemeModel> loadTheme() async {
    return (await loadAll()).theme;
  }

  Future<List<AnnouncementModel>> loadAnnouncements() async {
    return (await loadAll()).announcements;
  }

  Future<MaintenanceModel> loadMaintenance() async {
    return (await loadAll()).maintenance;
  }

  Future<UpdateInfoModel> loadUpdateInfo() async {
    return (await loadAll()).updateInfo;
  }
}
