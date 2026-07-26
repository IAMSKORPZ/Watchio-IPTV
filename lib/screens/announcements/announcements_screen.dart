import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../models/announcement_v2_model.dart';
import '../../services/announcement_service.dart';
import '../settings/widgets/watchio_settings_scaffold.dart';

class WatchioAnnouncementsScreen extends StatelessWidget {
  const WatchioAnnouncementsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final service = context.watch<AnnouncementService>();
    return WatchioSettingsScaffold(
      title: 'ANNOUNCEMENTS',
      onBack: () => Navigator.pop(context),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: service.isLoading && service.announcements.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : service.announcements.isEmpty
              ? const Center(child: Text('No announcements available'))
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
                  itemCount: service.announcements.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final item = service.announcements[index];
                    return _AnnouncementListCard(
                      announcement: item,
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => WatchioAnnouncementDetailsScreen(
                            announcement: item,
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ),
    );
  }
}

class _AnnouncementListCard extends StatefulWidget {
  const _AnnouncementListCard({
    required this.announcement,
    required this.onTap,
  });

  final AnnouncementV2Model announcement;
  final VoidCallback onTap;

  @override
  State<_AnnouncementListCard> createState() => _AnnouncementListCardState();
}

class _AnnouncementListCardState extends State<_AnnouncementListCard> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    return FocusableActionDetector(
      onFocusChange: (value) => setState(() => _focused = value),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.gameButtonA): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(16),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          constraints: const BoxConstraints(minHeight: 72),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: _focused ? 0.14 : 0.07),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: _focused ? accent : Colors.white12,
              width: _focused ? 2 : 1,
            ),
            boxShadow: _focused
                ? [
                    BoxShadow(
                      color: accent.withValues(alpha: 0.25),
                      blurRadius: 14,
                    ),
                  ]
                : null,
          ),
          child: Row(
            children: [
              Icon(Icons.campaign_outlined, color: accent, size: 24),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.announcement.title,
                      maxLines: 2,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      widget.announcement.date,
                      style: const TextStyle(
                        color: Colors.white38,
                        fontSize: 11,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: Colors.white38, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}

class WatchioAnnouncementDetailsScreen extends StatelessWidget {
  const WatchioAnnouncementDetailsScreen({
    super.key,
    required this.announcement,
  });

  final AnnouncementV2Model announcement;

  @override
  Widget build(BuildContext context) {
    return WatchioSettingsScaffold(
      title: 'ANNOUNCEMENT',
      onBack: () => Navigator.pop(context),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 700),
          child: Container(
            margin: const EdgeInsets.fromLTRB(24, 6, 24, 20),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
            ),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    announcement.title,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    announcement.date,
                    style: const TextStyle(color: Colors.white38, fontSize: 11),
                  ),
                  const Divider(color: Colors.white10, height: 28),
                  Text(
                    announcement.message,
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 15,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
