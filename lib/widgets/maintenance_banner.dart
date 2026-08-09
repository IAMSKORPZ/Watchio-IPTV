import 'package:watchio/services/config_service.dart';
import 'package:watchio/screens/maintenance/maintenance_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class MaintenanceBanner extends StatelessWidget {
  final Widget child;

  const MaintenanceBanner({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final maintenance = context.watch<ConfigService>().config.maintenance;
    if (!maintenance.enabled) return child;

    // If maintenance is enabled, show the full maintenance screen
    return const MaintenanceScreen();
  }
}
