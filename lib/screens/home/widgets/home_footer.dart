import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../utils/responsive_helper.dart';

class HomeFooter extends StatelessWidget {
  final String username;
  final String expiryDate;
  final String version;

  const HomeFooter({
    super.key,
    required this.username,
    required this.expiryDate,
    required this.version,
  });

  String _formatExpiry(String date) {
    if (date.isEmpty || date.toLowerCase() == 'n/a') return 'N/A';

    final timestamp = int.tryParse(date);
    if (timestamp != null) {
      final dt = DateTime.fromMillisecondsSinceEpoch(timestamp * 1000);
      return DateFormat('dd MMM yyyy').format(dt);
    }

    return date;
  }

  @override
  Widget build(BuildContext context) {
    final String formattedExpiry = _formatExpiry(expiryDate);
    final deviceType = ResponsiveHelper.getDeviceType(context);
    final isDesktop = deviceType == DeviceType.desktop;

    double fontSize = isDesktop ? 14 : 12;
    double iconSize = isDesktop ? 20 : 18;

    return Row(
      children: [
        // LEFT: Expiration
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.verified_outlined,
              color: const Color(0xFF20D9D2),
              size: iconSize,
            ),
            const SizedBox(width: 8),
            Text(
              'Expiration: $formattedExpiry',
              style: TextStyle(
                color: Colors.white70,
                fontSize: fontSize,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),

        const Spacer(),

        // CENTER: Version
        Text(
          'v$version',
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.5),
            fontSize: fontSize,
            fontWeight: FontWeight.w600,
          ),
        ),

        const Spacer(),

        // RIGHT: Profile
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.person_rounded,
              color: const Color(0xFFFF3D9A),
              size: iconSize,
            ),
            const SizedBox(width: 8),
            Text(
              'Logged In: $username',
              style: TextStyle(
                color: Colors.white70,
                fontSize: fontSize,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ],
    );
  }
}
