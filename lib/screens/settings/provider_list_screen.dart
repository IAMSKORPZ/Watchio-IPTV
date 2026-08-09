import 'package:watchio/controllers/provider_controller.dart';
import 'package:watchio/models/provider_model.dart';
import 'package:watchio/models/stalker_provider_config.dart';
import 'package:watchio/screens/m3u/m3u_home_screen.dart';
import 'package:watchio/screens/settings/provider_form_screen.dart';
import 'package:watchio/screens/stalker/stalker_home_screen.dart';
import 'package:watchio/screens/xtream-codes/xtream_code_home_screen.dart';
import 'package:watchio/widgets/tv_focusable.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class ProviderListScreen extends StatelessWidget {
  const ProviderListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => ProviderController()..loadProviders(),
      child: Scaffold(
        appBar: AppBar(title: const Text('Providers')),
        body: Consumer<ProviderController>(
          builder: (context, controller, child) {
            if (controller.isLoading && controller.providers.isEmpty) {
              return const Center(child: CircularProgressIndicator());
            }
            if (controller.providers.isEmpty) {
              return const Center(child: Text('No providers yet.'));
            }
            return RefreshIndicator(
              onRefresh: controller.loadProviders,
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: controller.providers.length,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (context, index) {
                  final provider = controller.providers[index];
                  return _ProviderTile(provider: provider);
                },
              ),
            );
          },
        ),
        floatingActionButton: Builder(
          builder: (context) => FloatingActionButton(
            onPressed: () async {
              final changed = await Navigator.push<bool>(
                context,
                MaterialPageRoute(builder: (_) => const ProviderFormScreen()),
              );
              if (changed == true && context.mounted) {
                await context.read<ProviderController>().loadProviders();
              }
            },
            child: const Icon(Icons.add),
          ),
        ),
      ),
    );
  }
}

class _ProviderTile extends StatelessWidget {
  final IptvProvider provider;

  const _ProviderTile({required this.provider});

  @override
  Widget build(BuildContext context) {
    final statusColor = switch (provider.status) {
      ProviderStatus.online => Colors.green,
      ProviderStatus.offline => Colors.red,
      ProviderStatus.authFailed => Colors.orange,
      ProviderStatus.unknown => Colors.grey,
    };

    return TvFocusable(
      onPressed: () => _handleAction(context, 'switch'),
      child: Card(
        child: ListTile(
          leading: CircleAvatar(
            backgroundColor: statusColor.withValues(alpha: 0.15),
            child: Icon(Icons.hub_outlined, color: statusColor),
          ),
          title: Row(
            children: [
              Expanded(child: Text(provider.name)),
              if (provider.isDefault)
                const Padding(
                  padding: EdgeInsets.only(left: 8),
                  child: Icon(Icons.star, size: 18),
                ),
            ],
          ),
          subtitle: Text(
            '${provider.type.label} • ${provider.status.label}\n'
            'Last connected: ${_formatDate(provider.lastConnected)}',
          ),
          isThreeLine: true,
          trailing: PopupMenuButton<String>(
            onSelected: (value) => _handleAction(context, value),
            itemBuilder: (context) => [
              const PopupMenuItem(value: 'switch', child: Text('Switch')),
              const PopupMenuItem(value: 'check', child: Text('Check Status')),
              const PopupMenuItem(value: 'edit', child: Text('Edit')),
              PopupMenuItem(
                value: provider.enabled ? 'disable' : 'enable',
                child: Text(provider.enabled ? 'Disable' : 'Enable'),
              ),
              const PopupMenuItem(value: 'default', child: Text('Set Default')),
              const PopupMenuItem(value: 'delete', child: Text('Delete')),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _handleAction(BuildContext context, String action) async {
    final controller = context.read<ProviderController>();
    switch (action) {
      case 'switch':
        final ok = await controller.switchProvider(provider.id);
        if (ok && context.mounted) {
          final playlist = provider.toPlaylist();
          Navigator.pushAndRemoveUntil(
            context,
            MaterialPageRoute(
              builder: (_) {
                switch (provider.type) {
                  case IptvProviderType.xtreamCodes:
                    return XtreamCodeHomeScreen(playlist: playlist);
                  case IptvProviderType.m3uUrl:
                  case IptvProviderType.m3uFile:
                    return M3UHomeScreen(playlist: playlist);
                  case IptvProviderType.stalker:
                    return StalkerHomeScreen(
                      playlist: playlist,
                      config: StalkerProviderConfig.fromJson(
                        provider.providerConfig,
                      ),
                    );
                }
              },
            ),
            (_) => false,
          );
        }
        break;
      case 'check':
        await controller.checkStatus(provider.id);
        break;
      case 'edit':
        final changed = await Navigator.push<bool>(
          context,
          MaterialPageRoute(
            builder: (_) => ProviderFormScreen(provider: provider),
          ),
        );
        if (changed == true && context.mounted) {
          await controller.loadProviders();
        }
        break;
      case 'enable':
        await controller.setEnabled(provider.id, true);
        break;
      case 'disable':
        await controller.setEnabled(provider.id, false);
        break;
      case 'default':
        await controller.setDefaultProvider(provider.id);
        break;
      case 'delete':
        if (context.mounted) await _confirmDelete(context, controller);
        break;
    }
  }

  Future<void> _confirmDelete(
    BuildContext context,
    ProviderController controller,
  ) async {
    final shouldDelete = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Provider'),
        content: Text('Delete ${provider.name}?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (shouldDelete == true) await controller.deleteProvider(provider.id);
  }

  String _formatDate(DateTime? value) {
    if (value == null) return 'Never';
    return '${value.year}-${value.month.toString().padLeft(2, '0')}-'
        '${value.day.toString().padLeft(2, '0')} '
        '${value.hour.toString().padLeft(2, '0')}:'
        '${value.minute.toString().padLeft(2, '0')}';
  }
}
