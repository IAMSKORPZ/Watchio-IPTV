import 'package:watchio/controllers/branding_controller.dart';
import 'package:watchio/controllers/update_controller.dart';
import 'package:watchio/widgets/update_available_dialog.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class UpdateStartupCheck extends StatefulWidget {
  final Widget child;

  const UpdateStartupCheck({super.key, required this.child});

  @override
  State<UpdateStartupCheck> createState() => _UpdateStartupCheckState();
}

class _UpdateStartupCheckState extends State<UpdateStartupCheck> {
  bool _checked = false;
  bool _shown = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_checked) return;
    _checked = true;
    WidgetsBinding.instance.addPostFrameCallback((_) => _runCheck());
  }

  Future<void> _runCheck() async {
    if (!mounted) return;
    final updates = context.read<UpdateController>();
    final branding = context.read<BrandingController>();
    await updates.loadState();
    await updates.checkForUpdates(
      remoteUpdateInfo: branding.updateInfo,
      isStartup: true,
      scheduledOnly: true,
    );
    if (!mounted ||
        _shown ||
        (!updates.updateAvailable && !updates.forceRequired)) {
      return;
    }
    _shown = true;
    await UpdateAvailableDialog.show(context);
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
