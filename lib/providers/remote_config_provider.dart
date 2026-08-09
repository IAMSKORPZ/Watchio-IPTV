import 'package:watchio/models/announcement_model.dart';
import 'package:watchio/models/branding_model.dart';
import 'package:watchio/models/maintenance_model.dart';
import 'package:watchio/models/theme_model.dart';
import 'package:watchio/models/update_info_model.dart';

abstract class RemoteConfigProvider {
  String get sourceName;

  Future<BrandingModel?> fetchBranding();
  Future<RemoteThemeModel?> fetchTheme();
  Future<List<AnnouncementModel>?> fetchAnnouncements();
  Future<MaintenanceModel?> fetchMaintenance();
  Future<UpdateInfoModel?> fetchUpdateInfo();
}
