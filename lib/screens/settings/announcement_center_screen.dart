import 'package:watchio/controllers/branding_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class AnnouncementCenterScreen extends StatelessWidget {
  const AnnouncementCenterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<BrandingController>();
    final announcements = controller.activeAnnouncements;

    return Scaffold(
      appBar: AppBar(title: const Text('Announcements')),
      body: announcements.isEmpty
          ? const Center(child: Text('No active announcements.'))
          : ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: announcements.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (context, index) {
                final item = announcements[index];
                return Card(
                  child: ListTile(
                    leading: CircleAvatar(child: Text('${item.priority}')),
                    title: Text(item.title),
                    subtitle: Text(
                      '${item.body}\nCreated: ${_format(item.createdAt)}',
                    ),
                    isThreeLine: true,
                  ),
                );
              },
            ),
    );
  }

  String _format(DateTime value) {
    return '${value.year}-${value.month.toString().padLeft(2, '0')}-'
        '${value.day.toString().padLeft(2, '0')}';
  }
}
