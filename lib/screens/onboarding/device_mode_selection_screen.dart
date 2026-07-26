import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../services/config_service.dart';
import '../../services/input_mode_controller.dart';
import '../../shared/widgets/watchio_focus_action.dart';
import '../../utils/firestick_performance.dart';

class DeviceModeSelectionScreen extends StatefulWidget {
  const DeviceModeSelectionScreen({super.key});

  @override
  State<DeviceModeSelectionScreen> createState() =>
      _DeviceModeSelectionScreenState();
}

class _DeviceModeSelectionScreenState extends State<DeviceModeSelectionScreen> {
  DeviceInputMode? _selectedMode;

  Future<void> _save() async {
    final mode = _selectedMode;
    if (mode == null) return;
    await context.read<InputModeController>().setMode(mode);
  }

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    final bg = config.backgrounds.login;

    return Scaffold(
      backgroundColor: const Color(0xFF050816),
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: BoxDecoration(
          image: DecorationImage(
            image: bg.isNotEmpty
                ? perfNetworkImage(bg)
                : const AssetImage('assets/images/background.png')
                      as ImageProvider,
            fit: BoxFit.cover,
            colorFilter: ColorFilter.mode(
              Colors.black.withValues(alpha: 0.46),
              BlendMode.darken,
            ),
          ),
        ),
        child: SafeArea(
          child: LayoutBuilder(
            builder: (context, constraints) {
              _selectedMode ??= constraints.maxWidth >= 900
                  ? DeviceInputMode.tv
                  : DeviceInputMode.mobile;

              final compact = constraints.maxHeight < 560;
              final logoHeight = compact ? 68.0 : 90.0;
              final panelWidth = (constraints.maxWidth * 0.52).clamp(
                460.0,
                620.0,
              );

              return Stack(
                children: [
                  Positioned(
                    left: 28,
                    top: compact ? 10 : 18,
                    child: Image.asset(
                      'assets/images/App_Logo.png',
                      height: logoHeight,
                      fit: BoxFit.contain,
                      errorBuilder: (_, _, _) => Icon(
                        Icons.live_tv_rounded,
                        color: const Color(0xFF00B7FF),
                        size: logoHeight,
                      ),
                    ),
                  ),
                  Center(
                    child: SingleChildScrollView(
                      physics: const ClampingScrollPhysics(),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 24,
                        vertical: 18,
                      ),
                      child: ConstrainedBox(
                        constraints: BoxConstraints(maxWidth: panelWidth),
                        child: _DeviceOptionPanel(
                          compact: compact,
                          selectedMode: _selectedMode!,
                          onChanged: (mode) =>
                              setState(() => _selectedMode = mode),
                          onSave: _save,
                        ),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _DeviceOptionPanel extends StatelessWidget {
  const _DeviceOptionPanel({
    required this.compact,
    required this.selectedMode,
    required this.onChanged,
    required this.onSave,
  });

  final bool compact;
  final DeviceInputMode selectedMode;
  final ValueChanged<DeviceInputMode> onChanged;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            const Color(0xFF26124C).withValues(alpha: 0.94),
            const Color(0xFF1B2B62).withValues(alpha: 0.92),
          ],
        ),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.12),
          width: 1.2,
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFFC12CFF).withValues(alpha: 0.18),
            blurRadius: 34,
            spreadRadius: 2,
          ),
        ],
      ),
      child: Padding(
        padding: EdgeInsets.fromLTRB(50, compact ? 22 : 30, 50, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Device Option',
              style: TextStyle(
                color: Colors.white,
                fontSize: compact ? 19 : 21,
                fontWeight: FontWeight.w900,
              ),
            ),
            SizedBox(height: compact ? 14 : 18),
            Text(
              'Watchio detected your device type is '
              '${selectedMode == DeviceInputMode.tv ? 'TV' : 'Mobile'}',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.82),
                fontSize: compact ? 13 : 15,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Please choose correct one for better performance',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.82),
                fontSize: compact ? 13 : 15,
                fontWeight: FontWeight.w600,
              ),
            ),
            SizedBox(height: compact ? 18 : 22),
            Align(
              alignment: Alignment.centerLeft,
              child: FocusTraversalGroup(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _ModeRadioRow(
                      autofocus: selectedMode == DeviceInputMode.mobile,
                      selected: selectedMode == DeviceInputMode.mobile,
                      label: 'Mobile',
                      mode: DeviceInputMode.mobile,
                      onChanged: onChanged,
                    ),
                    SizedBox(height: compact ? 8 : 12),
                    _ModeRadioRow(
                      autofocus: selectedMode == DeviceInputMode.tv,
                      selected: selectedMode == DeviceInputMode.tv,
                      label: 'TV',
                      mode: DeviceInputMode.tv,
                      onChanged: onChanged,
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(height: compact ? 18 : 22),
            _SaveButton(onPressed: onSave),
          ],
        ),
      ),
    );
  }
}

class _ModeRadioRow extends StatefulWidget {
  const _ModeRadioRow({
    required this.selected,
    required this.label,
    required this.mode,
    required this.onChanged,
    this.autofocus = false,
  });

  final bool selected;
  final String label;
  final DeviceInputMode mode;
  final ValueChanged<DeviceInputMode> onChanged;
  final bool autofocus;

  @override
  State<_ModeRadioRow> createState() => _ModeRadioRowState();
}

class _ModeRadioRowState extends State<_ModeRadioRow> {
  bool _focused = false;
  bool _hovered = false;

  bool get _active => _focused || _hovered;

  void _select() => widget.onChanged(widget.mode);

  @override
  Widget build(BuildContext context) {
    return WatchioFocusAction(
      autofocus: widget.autofocus,
      onActivate: _select,
      onFocusChange: (value) => setState(() => _focused = value),
      mouseCursor: SystemMouseCursors.click,
      child: MouseRegion(
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: GestureDetector(
          onTap: _select,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 120),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: _active
                  ? Colors.white.withValues(alpha: 0.08)
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: _focused ? const Color(0xFF00B7FF) : Colors.transparent,
                width: 1.5,
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  widget.selected
                      ? Icons.radio_button_checked_rounded
                      : Icons.radio_button_unchecked_rounded,
                  color: widget.selected
                      ? const Color(0xFF00B7FF)
                      : Colors.white,
                  size: 22,
                ),
                const SizedBox(width: 14),
                Text(
                  widget.label,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SaveButton extends StatefulWidget {
  const _SaveButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  State<_SaveButton> createState() => _SaveButtonState();
}

class _SaveButtonState extends State<_SaveButton> {
  bool _focused = false;
  bool _hovered = false;

  bool get _active => _focused || _hovered;

  @override
  Widget build(BuildContext context) {
    return WatchioFocusAction(
      onActivate: widget.onPressed,
      onFocusChange: (value) => setState(() => _focused = value),
      mouseCursor: SystemMouseCursors.click,
      child: MouseRegion(
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: GestureDetector(
          onTap: widget.onPressed,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 120),
            width: 130,
            height: 40,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: _active
                    ? const [Color(0xFF2EE7FF), Color(0xFFC12CFF)]
                    : const [Color(0xFF2EC7FF), Color(0xFF722DFF)],
              ),
              border: Border.all(color: Colors.white70, width: 1.2),
              boxShadow: [
                if (_active)
                  BoxShadow(
                    color: const Color(0xFFC12CFF).withValues(alpha: 0.42),
                    blurRadius: 18,
                    spreadRadius: 1,
                  ),
              ],
            ),
            child: const Center(
              child: Text(
                'SAVE',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
