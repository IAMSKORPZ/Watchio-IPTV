import 'package:watchio/controllers/provider_controller.dart';
import 'package:watchio/models/provider_model.dart';
import 'package:watchio/services/secure_storage_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class ProviderFormScreen extends StatefulWidget {
  final IptvProvider? provider;

  const ProviderFormScreen({super.key, this.provider});

  @override
  State<ProviderFormScreen> createState() => _ProviderFormScreenState();
}

class _ProviderFormScreenState extends State<ProviderFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _serverUrlController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _playlistUrlController = TextEditingController();
  final _localFilePathController = TextEditingController();
  final _epgUrlController = TextEditingController();
  final _portalUrlController = TextEditingController();
  final _macAddressController = TextEditingController();
  final _deviceIdController = TextEditingController();
  final _serialNumberController = TextEditingController();
  final _userAgentController = TextEditingController();

  late IptvProviderType _type;

  bool get _isEdit => widget.provider != null;

  @override
  void initState() {
    super.initState();
    final provider = widget.provider;
    _type = provider?.type ?? IptvProviderType.xtreamCodes;
    _nameController.text = provider?.name ?? '';
    _serverUrlController.text = provider?.serverUrl ?? '';
    _usernameController.text = provider?.username ?? '';
    _passwordController.text = provider?.password ?? '';
    _playlistUrlController.text = provider?.playlistUrl ?? '';
    _localFilePathController.text = provider?.localFilePath ?? '';
    _epgUrlController.text = provider?.epgUrl ?? '';
    _portalUrlController.text =
        provider?.providerConfig['portalUrl'] as String? ?? '';
    _deviceIdController.text =
        provider?.providerConfig['deviceId'] as String? ?? '';
    _serialNumberController.text =
        provider?.providerConfig['serialNumber'] as String? ?? '';
    _userAgentController.text =
        provider?.providerConfig['userAgentOverride'] as String? ?? '';
    _loadStalkerMac();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _serverUrlController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    _playlistUrlController.dispose();
    _localFilePathController.dispose();
    _epgUrlController.dispose();
    _portalUrlController.dispose();
    _macAddressController.dispose();
    _deviceIdController.dispose();
    _serialNumberController.dispose();
    _userAgentController.dispose();
    super.dispose();
  }

  Future<void> _loadStalkerMac() async {
    final provider = widget.provider;
    if (provider == null || provider.type != IptvProviderType.stalker) return;
    _macAddressController.text =
        await SecureStorageService.instance.readProviderSecret(
          provider.id,
          'stalker_mac',
        ) ??
        '';
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => ProviderController(),
      child: Scaffold(
        appBar: AppBar(title: Text(_isEdit ? 'Edit Provider' : 'Add Provider')),
        body: Consumer<ProviderController>(
          builder: (context, controller, child) {
            return SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildTypeSelector(),
                    const SizedBox(height: 16),
                    _field(
                      controller: _nameController,
                      label: 'Provider Name',
                      icon: Icons.badge_outlined,
                      validator: _required,
                    ),
                    const SizedBox(height: 12),
                    ..._typeFields(),
                    const SizedBox(height: 12),
                    if (_type != IptvProviderType.xtreamCodes)
                      _field(
                        controller: _epgUrlController,
                        label: 'EPG URL',
                        icon: Icons.calendar_month_outlined,
                        validator: _optionalUrl,
                      ),
                    if (controller.error != null) ...[
                      const SizedBox(height: 12),
                      _errorCard(controller.error!),
                    ],
                    const SizedBox(height: 20),
                    FilledButton.icon(
                      onPressed: controller.isLoading
                          ? null
                          : () => _save(context, controller),
                      icon: const Icon(Icons.save),
                      label: Text(controller.isLoading ? 'Saving...' : 'Save'),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildTypeSelector() {
    return DropdownButtonFormField<IptvProviderType>(
      initialValue: _type,
      decoration: const InputDecoration(
        labelText: 'Provider Type',
        prefixIcon: Icon(Icons.hub_outlined),
        border: OutlineInputBorder(),
      ),
      items: IptvProviderType.values
          .map((type) => DropdownMenuItem(value: type, child: Text(type.label)))
          .toList(),
      onChanged: _isEdit
          ? null
          : (value) {
              if (value == null) return;
              setState(() => _type = value);
            },
    );
  }

  List<Widget> _typeFields() {
    switch (_type) {
      case IptvProviderType.xtreamCodes:
        return [
          _field(
            controller: _serverUrlController,
            label: 'Server URL',
            icon: Icons.link,
            keyboardType: TextInputType.url,
            validator: _requiredUrl,
          ),
          const SizedBox(height: 12),
          _field(
            controller: _usernameController,
            label: 'Username',
            icon: Icons.person_outline,
            validator: _required,
          ),
          const SizedBox(height: 12),
          _field(
            controller: _passwordController,
            label: 'Password',
            icon: Icons.lock_outline,
            obscureText: true,
            validator: _required,
          ),
        ];
      case IptvProviderType.m3uUrl:
        return [
          _field(
            controller: _playlistUrlController,
            label: 'Playlist URL',
            icon: Icons.link,
            keyboardType: TextInputType.url,
            validator: _requiredUrl,
          ),
        ];
      case IptvProviderType.m3uFile:
        return [
          TextFormField(
            controller: _localFilePathController,
            readOnly: true,
            decoration: InputDecoration(
              labelText: 'Local File Path',
              prefixIcon: const Icon(Icons.folder_outlined),
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: const Icon(Icons.file_open),
                onPressed: _pickFile,
              ),
            ),
            validator: _required,
          ),
        ];
      case IptvProviderType.stalker:
        return [
          _field(
            controller: _portalUrlController,
            label: 'Portal URL',
            icon: Icons.link,
            keyboardType: TextInputType.url,
            validator: _requiredUrl,
          ),
          const SizedBox(height: 12),
          _field(
            controller: _macAddressController,
            label: 'MAC Address',
            icon: Icons.security,
            validator: _required,
          ),
          const SizedBox(height: 12),
          _field(
            controller: _deviceIdController,
            label: 'Device ID',
            icon: Icons.devices,
          ),
          const SizedBox(height: 12),
          _field(
            controller: _serialNumberController,
            label: 'Serial Number',
            icon: Icons.confirmation_number_outlined,
          ),
          const SizedBox(height: 12),
          _field(
            controller: _userAgentController,
            label: 'User Agent Override',
            icon: Icons.http,
          ),
        ];
    }
  }

  Widget _field({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    TextInputType? keyboardType,
    bool obscureText = false,
    String? Function(String?)? validator,
  }) {
    return TextFormField(
      controller: controller,
      keyboardType: keyboardType,
      obscureText: obscureText,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
        border: const OutlineInputBorder(),
      ),
      validator: validator,
    );
  }

  Widget _errorCard(String message) {
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      color: colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Text(
          message,
          style: TextStyle(color: colorScheme.onErrorContainer),
        ),
      ),
    );
  }

  Future<void> _pickFile() async {
    final result = await FilePicker.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['m3u', 'm3u8'],
      allowMultiple: false,
    );
    final path = result?.files.single.path;
    if (path != null) {
      _localFilePathController.text = path;
    }
  }

  Future<void> _save(
    BuildContext context,
    ProviderController controller,
  ) async {
    if (!_formKey.currentState!.validate()) return;

    final now = DateTime.now();
    final existing = widget.provider;
    final provider = IptvProvider(
      id: existing?.id ?? '${now.millisecondsSinceEpoch}',
      type: _type,
      name: _nameController.text.trim(),
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      lastUsed: existing?.lastUsed,
      lastConnected: existing?.lastConnected,
      enabled: existing?.enabled ?? true,
      isDefault: existing?.isDefault ?? false,
      status: existing?.status ?? ProviderStatus.unknown,
      lastFailureReason: existing?.lastFailureReason,
      serverUrl: _serverUrlController.text.trim().isEmpty
          ? null
          : _serverUrlController.text.trim(),
      username: _usernameController.text.trim().isEmpty
          ? null
          : _usernameController.text.trim(),
      password: _passwordController.text.trim().isEmpty
          ? null
          : _passwordController.text.trim(),
      playlistUrl: _playlistUrlController.text.trim().isEmpty
          ? null
          : _playlistUrlController.text.trim(),
      localFilePath: _localFilePathController.text.trim().isEmpty
          ? null
          : _localFilePathController.text.trim(),
      epgUrl: _epgUrlController.text.trim().isEmpty
          ? null
          : _epgUrlController.text.trim(),
      providerConfig: {
        if (_portalUrlController.text.trim().isNotEmpty)
          'portalUrl': _portalUrlController.text.trim(),
        if (_deviceIdController.text.trim().isNotEmpty)
          'deviceId': _deviceIdController.text.trim(),
        if (_serialNumberController.text.trim().isNotEmpty)
          'serialNumber': _serialNumberController.text.trim(),
        if (_userAgentController.text.trim().isNotEmpty)
          'userAgentOverride': _userAgentController.text.trim(),
      },
    );

    final ok = await controller.saveProvider(provider, isEdit: _isEdit);
    if (ok && _type == IptvProviderType.stalker) {
      await SecureStorageService.instance.saveProviderSecret(
        provider.id,
        'stalker_mac',
        _macAddressController.text,
      );
    }
    if (ok && context.mounted) Navigator.pop(context, true);
  }

  String? _required(String? value) {
    return value == null || value.trim().isEmpty ? 'Required.' : null;
  }

  String? _requiredUrl(String? value) {
    final required = _required(value);
    if (required != null) return required;
    return _validateUrl(value);
  }

  String? _optionalUrl(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    return _validateUrl(value);
  }

  String? _validateUrl(String? value) {
    final uri = Uri.tryParse(value!.trim());
    if (uri == null ||
        !uri.hasScheme ||
        !uri.hasAuthority ||
        !['http', 'https'].contains(uri.scheme)) {
      return 'Enter a valid http or https URL.';
    }
    return null;
  }
}
