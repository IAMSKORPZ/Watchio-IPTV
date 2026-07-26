import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../services/input_mode_controller.dart';
import '../../../shared/widgets/watchio_focus_action.dart';
import '../widgets/watchio_settings_scaffold.dart';

class InputModePage extends StatelessWidget {
  const InputModePage({super.key});

  @override
  Widget build(BuildContext context) {
    final inputMode = context.watch<InputModeController>();

    return WatchioSettingsScaffold(
      title: 'INPUT MODE',
      onBack: () => Navigator.of(context).maybePop(),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(48, 18, 48, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Choose how Watchio handles input.',
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.72),
                fontSize: 18,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: _InputModeOption(
                    autofocus: inputMode.mode == DeviceInputMode.mobile,
                    selected: inputMode.mode == DeviceInputMode.mobile,
                    mode: DeviceInputMode.mobile,
                    icon: Icons.phone_android_rounded,
                    title: 'MOBILE / TOUCHSCREEN',
                    subtitle:
                        'Use touch, mouse, taps, swipes, and normal text input.',
                  ),
                ),
                const SizedBox(width: 22),
                Expanded(
                  child: _InputModeOption(
                    autofocus: inputMode.mode == DeviceInputMode.tv,
                    selected: inputMode.mode == DeviceInputMode.tv,
                    mode: DeviceInputMode.tv,
                    icon: Icons.sports_esports_rounded,
                    title: 'TV / REMOTE',
                    subtitle:
                        'Use TV remote, Firestick remote, keyboard, Xbox or PlayStation controller.',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),
            Text(
              inputMode.isTvMode
                  ? 'TV mode blocks touch and mouse clicks. Use remote, controller, or keyboard to navigate.'
                  : 'Mobile mode keeps touchscreen and mouse clicks enabled.',
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.56),
                fontSize: 14,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _InputModeOption extends StatefulWidget {
  const _InputModeOption({
    required this.selected,
    required this.mode,
    required this.icon,
    required this.title,
    required this.subtitle,
    this.autofocus = false,
  });

  final bool selected;
  final DeviceInputMode mode;
  final IconData icon;
  final String title;
  final String subtitle;
  final bool autofocus;

  @override
  State<_InputModeOption> createState() => _InputModeOptionState();
}

class _InputModeOptionState extends State<_InputModeOption> {
  bool _focused = false;

  Future<void> _select() async {
    await context.read<InputModeController>().setMode(widget.mode);
  }

  @override
  Widget build(BuildContext context) {
    final active = _focused || widget.selected;
    final color = widget.mode == DeviceInputMode.tv
        ? const Color(0xFFC12CFF)
        : const Color(0xFF00B7FF);

    return WatchioFocusAction(
      autofocus: widget.autofocus,
      onActivate: _select,
      onFocusChange: (value) => setState(() => _focused = value),
      mouseCursor: SystemMouseCursors.click,
      child: GestureDetector(
        onTap: _select,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 140),
          height: 210,
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(26),
            color: const Color(0xFF111827).withValues(alpha: 0.78),
            border: Border.all(
              color: active ? color : Colors.white24,
              width: 2.5,
            ),
            boxShadow: [
              if (_focused)
                BoxShadow(
                  color: color.withValues(alpha: 0.36),
                  blurRadius: 24,
                  spreadRadius: 1,
                ),
            ],
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                widget.icon,
                color: active ? Colors.white : Colors.white60,
                size: 54,
              ),
              const SizedBox(height: 14),
              Text(
                widget.title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1.1,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                widget.subtitle,
                textAlign: TextAlign.center,
                maxLines: 3,
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.62),
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
