import 'package:watchio/controllers/branding_controller.dart';
import 'package:watchio/screens/settings/announcement_center_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class RemoteConfigScreen extends StatelessWidget {
  const RemoteConfigScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<BrandingController>();

    return Scaffold(
      appBar: AppBar(title: const Text('Remote Configuration')),
      body: RefreshIndicator(
        onRefresh: () => controller.load(forceRefresh: true),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Column(
                children: [
                  _row('Current Config Source', controller.sourceName),
                  const Divider(height: 1),
                  _row('Last Sync Time', _format(controller.lastSyncTime)),
                  const Divider(height: 1),
                  _row(
                    'Cache Status',
                    controller.hasCache ? 'Available' : 'None',
                  ),
                  const Divider(height: 1),
                  _row(
                    'Mode',
                    controller.usingCache ? 'Cached' : 'Fresh/default',
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.campaign_outlined),
                    title: const Text('Announcement Center'),
                    subtitle: Text(
                      '${controller.activeAnnouncements.length} active',
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const AnnouncementCenterScreen(),
                        ),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.build_circle_outlined),
                    title: Text(controller.maintenance.title),
                    subtitle: Text(
                      controller.maintenance.enabled
                          ? controller.maintenance.message
                          : 'Maintenance mode disabled',
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: controller.isLoading
                  ? null
                  : () => controller.load(forceRefresh: true),
              icon: const Icon(Icons.sync),
              label: Text(
                controller.isLoading ? 'Refreshing...' : 'Refresh Config',
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
      ),
    );
  }

  Widget _row(String label, String value) {
    return ListTile(title: Text(label), subtitle: Text(value), dense: true);
  }

  String _format(DateTime? value) {
    if (value == null) return 'Never';
    return '${value.year}-${value.month.toString().padLeft(2, '0')}-'
        '${value.day.toString().padLeft(2, '0')} '
        '${value.hour.toString().padLeft(2, '0')}:'
        '${value.minute.toString().padLeft(2, '0')}';
  }
}
