import 'package:watchio/controllers/update_controller.dart';
import 'package:watchio/screens/update/update_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class UpdateAvailableDialog {
  static Future<void> show(BuildContext context) async {
    final controller = context.read<UpdateController>();
    final result = controller.result;
    if (result == null || (!result.updateAvailable && !result.forceRequired)) {
      return;
    }

    await showDialog<void>(
      context: context,
      barrierDismissible: !result.forceRequired,
      builder: (context) => AlertDialog(
        title: Text(result.forceRequired ? 'Update Required' : 'Update Available'),
        content: Text(
          'Version ${result.updateInfo.latestVersion} is available.\n\n'
          '${result.updateInfo.releaseNotes ?? ''}',
        ),
        actions: [
          if (!result.forceRequired)
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Later'),
            ),
          FilledButton(
            autofocus: true,
            onPressed: () {
              Navigator.pop(context);
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const UpdateScreen()),
              );
            },
            child: const Text('View Update'),
          ),
        ],
      ),
    );
  }
}
