import 'package:watchio/models/update_info_model.dart';
import 'package:watchio/services/apk_installer_service.dart';
import 'package:watchio/services/github_release_service.dart';
import 'package:watchio/services/update_service.dart';
import 'package:flutter/material.dart';

class UpdateController extends ChangeNotifier {
  final UpdateService service;
  final ApkInstallerService installerService;

  UpdateController({
    UpdateService? service,
    ApkInstallerService? installerService,
  }) : service = service ?? UpdateService(),
       installerService = installerService ?? ApkInstallerService();

  UpdateCheckResult? result;
  UpdateChannel channel = UpdateChannel.stable;
  String? currentVersion;
  DateTime? lastCheckTime;
  String? lastKnownVersion;
  String? downloadedInstallerPath;
  bool installPermissionRequired = false;
  bool isChecking = false;
  bool isDownloading = false;
  String? error;

  bool get updateAvailable => result?.updateAvailable ?? false;
  bool get forceRequired => result?.forceRequired ?? false;

  Future<void> loadState() async {
    channel = await service.getChannel();
    currentVersion = await service.getCurrentVersion();
    lastCheckTime = await service.getLastCheckTime();
    lastKnownVersion = await service.getLastKnownVersion();
    notifyListeners();
  }

  Future<void> setChannel(UpdateChannel _) async {
    channel = UpdateChannel.stable;
    await service.setChannel(UpdateChannel.stable);
    notifyListeners();
  }

  Future<void> checkForUpdates({
    UpdateInfoModel? remoteUpdateInfo,
    bool isStartup = false,
    bool scheduledOnly = false,
  }) async {
    if (scheduledOnly && !await service.shouldRunScheduledCheck()) return;

    isChecking = true;
    error = null;
    notifyListeners();

    try {
      result = await service.checkForUpdates(
        remoteUpdateInfo: remoteUpdateInfo,
        allowCache: true,
      );
      currentVersion = result!.currentVersion;
      lastCheckTime = result!.checkedAt;
      lastKnownVersion = result!.updateInfo.latestVersion;
    } catch (e) {
      error = e.toString();
    } finally {
      isChecking = false;
      notifyListeners();
    }
  }

  Future<void> downloadUpdate() async {
    final release = result?.release;
    if (release == null) {
      error = 'No release asset available.';
      notifyListeners();
      return;
    }

    isDownloading = true;
    error = null;
    notifyListeners();

    try {
      downloadedInstallerPath = await service.downloadInstaller(release);
      await installDownloadedUpdate();
    } catch (e) {
      error = e.toString();
    } finally {
      isDownloading = false;
      notifyListeners();
    }
  }

  Future<void> installDownloadedUpdate() async {
    final path = downloadedInstallerPath;
    if (path == null || path.isEmpty) {
      error = 'No downloaded APK found.';
      notifyListeners();
      return;
    }

    try {
      final canInstall = await installerService.canInstallPackages();
      if (!canInstall) {
        installPermissionRequired = true;
        await installerService.openUnknownSourcesSettings();
        notifyListeners();
        return;
      }
      installPermissionRequired = false;
      await installerService.installApk(path);
    } catch (e) {
      error = e.toString();
      notifyListeners();
    }
  }

  Future<void> openUnknownSourcesSettings() async {
    await installerService.openUnknownSourcesSettings();
  }
}
