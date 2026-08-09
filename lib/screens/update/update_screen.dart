import 'package:watchio/controllers/branding_controller.dart';
import 'package:watchio/controllers/update_controller.dart';
import 'package:watchio/services/update_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class UpdateScreen extends StatelessWidget {
  const UpdateScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<UpdateController>();
    final branding = context.watch<BrandingController>();
    final result = controller.result;

    return Scaffold(
      appBar: AppBar(title: const Text('Updates')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Column(
              children: [
                _row(
                  'Current Version',
                  result?.currentVersion ??
                      controller.currentVersion ??
                      'Unknown',
                ),
                const Divider(height: 1),
                _row(
                  'Latest Version',
                  result?.updateInfo.latestVersion ??
                      controller.lastKnownVersion ??
                      'Unknown',
                ),
                const Divider(height: 1),
                _row('Last Check', _format(controller.lastCheckTime)),
                const Divider(height: 1),
                _row('Update Channel', 'Stable'),
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (result != null) _releaseCard(context, controller, result),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: controller.isChecking
                ? null
                : () => controller.checkForUpdates(
                    remoteUpdateInfo: branding.updateInfo,
                  ),
            icon: const Icon(Icons.system_update_alt),
            label: Text(
              controller.isChecking ? 'Checking...' : 'Check For Updates',
            ),
          ),
          if (controller.error != null) ...[
            const SizedBox(height: 12),
            Card(
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Text(controller.error!),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _releaseCard(
    BuildContext context,
    UpdateController controller,
    UpdateCheckResult result,
  ) {
    final release = result.release;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              result.forceRequired
                  ? 'Update Required'
                  : (result.updateAvailable
                        ? 'Update Available'
                        : 'Up to date'),
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text('Version: ${result.updateInfo.latestVersion}'),
            if (release?.publishedAt != null)
              Text('Published: ${_format(release!.publishedAt)}'),
            if (result.forceRequired)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text('Required update before continuing.'),
              ),
            if ((result.updateInfo.releaseNotes ?? '').isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(result.updateInfo.releaseNotes!),
            ],
            if (release?.downloadUrl != null) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: FilledButton(
                      onPressed: controller.isDownloading
                          ? null
                          : controller.downloadUpdate,
                      child: Text(
                        controller.isDownloading
                            ? 'Downloading...'
                            : 'Download Latest APK',
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => _showChangelog(context, result),
                      child: const Text('Show Changelog'),
                    ),
                  ),
                ],
              ),
              if (controller.downloadedInstallerPath != null) ...[
                const SizedBox(height: 8),
                Text('Downloaded: ${controller.downloadedInstallerPath}'),
                if (controller.installPermissionRequired)
                  const Text(
                    'Enable Install Unknown Apps for Watchio, then press Install APK.',
                  )
                else
                  const Text('Installer should open automatically.'),
                OutlinedButton.icon(
                  onPressed: controller.installDownloadedUpdate,
                  icon: const Icon(Icons.install_mobile),
                  label: const Text('Install APK'),
                ),
                if (controller.installPermissionRequired)
                  TextButton.icon(
                    onPressed: controller.openUnknownSourcesSettings,
                    icon: const Icon(Icons.settings),
                    label: const Text('Open Unknown Sources Settings'),
                  ),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value) {
    return ListTile(title: Text(label), subtitle: Text(value), dense: true);
  }

  Future<void> _showChangelog(
    BuildContext context,
    UpdateCheckResult result,
  ) async {
    final notes = (result.updateInfo.releaseNotes ?? '').trim();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('Changelog v${result.updateInfo.latestVersion}'),
        content: SizedBox(
          width: 520,
          child: SingleChildScrollView(
            child: SelectableText(
              notes.isEmpty ? 'No changelog available.' : notes,
            ),
          ),
        ),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('CLOSE'),
          ),
        ],
      ),
    );
  }

  String _format(DateTime? value) {
    if (value == null) return 'Never';
    return '${value.year}-${value.month.toString().padLeft(2, '0')}-'
        '${value.day.toString().padLeft(2, '0')} '
        '${value.hour.toString().padLeft(2, '0')}:'
        '${value.minute.toString().padLeft(2, '0')}';
  }
}
