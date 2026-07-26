import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../controllers/xtream_code_home_controller.dart';
import '../../core/theme/theme_manager.dart';
import '../../models/category_type.dart';
import '../../models/category_view_model.dart';
import '../../models/content_type.dart';
import '../../models/playback_item.dart';
import '../../models/player_engine.dart';
import '../../models/playlist_content_model.dart';
import '../../models/playlist_model.dart';
import '../../repositories/iptv_repository.dart';
import '../../repositories/user_preferences.dart';
import '../../services/app_state.dart';
import '../../services/config_service.dart';
import '../../services/epg_storage_service.dart';
import '../../services/player/app_player_controller.dart';
import '../../services/player/player_factory.dart';
import '../../services/playback_url_resolver.dart';
import '../../services/watch_history_service.dart';
import '../../shared/widgets/glass_panel.dart';
import '../../shared/widgets/watchio_focus_action.dart';
import '../../shared/widgets/watchio_header.dart';
import '../../utils/firestick_performance.dart';
import '../player/unified_player_screen.dart';
import '../search_screen.dart';
import '../../services/epg_import_service.dart';
import '../../services/epg_source_service.dart';

class XtreamLiveScreen extends StatefulWidget {
  final Playlist? playlist;
  const XtreamLiveScreen({super.key, this.playlist});

  @override
  State<XtreamLiveScreen> createState() => _XtreamLiveScreenState();
}

class _XtreamLiveScreenState extends State<XtreamLiveScreen>
    with WidgetsBindingObserver {
  CategoryViewModel? _selectedCategory;
  ContentItem? _focusedChannel;
  ContentItem? _detailsChannel;
  ContentItem? _previewChannel;
  final List<ContentItem> _currentItems = [];
  bool _isMoreLoading = false;
  bool _hasMore = true;
  int _currentOffset = 0;
  static const int _pageSize = 60;
  final Map<String, int> _categoryCounts = {};
  String _categoryQuery = '';
  String _channelSortOrder = 'server';

  final ScrollController _categoryScrollController = ScrollController();
  final ScrollController _channelScrollController = ScrollController();
  final FocusNode _categorySearchButtonFocusNode = FocusNode(
    debugLabel: 'liveCategorySearchButton',
  );
  final FocusNode _categorySearchInputFocusNode = FocusNode(
    debugLabel: 'liveCategorySearchInput',
  );
  final TextEditingController _categorySearchController =
      TextEditingController();
  bool _categorySearchEditing = false;
  bool _categorySearchFocused = false;

  List<EpgProgramWindow> _epgPrograms = [];
  final _epgService = EpgStorageService();
  final Map<String, EpgProgramWindow?> _rowProgramCache = {};
  final Set<String> _rowProgramLoads = {};
  final Set<String> _hiddenLiveCategoryIds = {};
  bool _showChannelNumbers = true;
  bool _showChannelIcons = true;
  bool _showChannelNames = true;
  bool _showCurrentProgram = true;
  String _liveRowSize = 'normal';

  AppPlayerController? _previewController;
  Timer? _previewDebounce;
  Timer? _detailsDebounce;
  final FocusNode _previewFocusNode = FocusNode(debugLabel: 'livePreviewPanel');
  bool _previewFocused = false;
  bool _hasPreviewStarted = false;
  bool _previewSawPlayback = false;
  XtreamCodeHomeController? _homeController;
  int _previewLoadRequestId = 0;
  bool _isReconnecting = false;
  Timer? _epgUpdateTimer;
  Timer? _backHoldTimer;
  bool _backHoldTriggered = false;
  int _epgRequestId = 0;
  String? _lastPreviewOverlayState;
  final Map<String, FocusNode> _channelFocusNodes = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // BUG FIX: Removed _initPreviewController() and _startEpgTimer() from initState
    // Player and EPG should only be initialized when entering the Live TV tab
    _channelScrollController.addListener(_scrollListener);
    EpgSourceService.revision.addListener(_onEpgUpdated);

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _loadLiveSetupPreferences();
      if (!mounted) return;
      _homeController = Provider.of<XtreamCodeHomeController>(
        context,
        listen: false,
      );
      _homeController?.addListener(_handleTabChange);

      final controller = _homeController!;
      final categories = _visibleLiveCategories(controller);
      if (categories.isNotEmpty) {
        final preferredCategory = categories
            .cast<CategoryViewModel?>()
            .firstWhere(
              (category) => category!.category.categoryName
                  .toUpperCase()
                  .replaceAll(RegExp(r'[^A-Z0-9]+'), ' ')
                  .contains('UK FREE TO AIR'),
              orElse: () => categories.first,
            )!;
        // Load all category counts in bulk
        final counts = await controller.getAllCategoryCounts(CategoryType.live);
        if (mounted) {
          setState(() {
            _categoryCounts.addAll(counts);
          });
          await _onCategorySelected(preferredCategory);
        }
      }
    });
  }

  Future<void> _loadLiveSetupPreferences() async {
    final playlist = widget.playlist ?? AppState.currentPlaylist;
    final hidden = playlist == null
        ? <String>[]
        : await UserPreferences.getLiveHiddenCategoryIds(playlist.id);
    final showNumbers = await UserPreferences.getLiveShowChannelNumbers();
    final showIcons = await UserPreferences.getLiveShowChannelIcons();
    final showNames = await UserPreferences.getLiveShowChannelNames();
    final showProgram = await UserPreferences.getLiveShowCurrentProgram();
    final rowSize = await UserPreferences.getLiveRowSize();
    final sortOrder = await UserPreferences.getLiveSortOrder();
    if (!mounted) return;
    setState(() {
      _hiddenLiveCategoryIds
        ..clear()
        ..addAll(hidden);
      _showChannelNumbers = showNumbers;
      _showChannelIcons = showIcons;
      _showChannelNames = showNames;
      _showCurrentProgram = showProgram;
      _liveRowSize = rowSize;
      if (sortOrder != null) _channelSortOrder = sortOrder;
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    debugPrint('XtreamLiveScreen: App lifecycle state changed -> $state');
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.detached) {
      debugPrint('XtreamLiveScreen: Playback Stopped (App Background)');
      _previewDebounce?.cancel();
      _previewController?.pause();
    } else if (state == AppLifecycleState.resumed) {
      if (_homeController?.currentIndex == 2) {
        debugPrint(
          'XtreamLiveScreen: App resumed on Live TV, resuming preview',
        );
        _previewController?.play();
      }
    }
  }

  Future<void> _initPreviewController() async {
    if (_previewController != null) return;

    debugPrint('XtreamLiveScreen: Player Created (Preview)');
    final engineStr = await UserPreferences.getPlayerEngine();
    final engine = PlayerEngine.values.firstWhere(
      (e) => e.name == engineStr,
      orElse: () => PlayerEngine.auto,
    );
    _previewController = PlayerFactory.create(
      engine,
      contentType: ContentType.liveStream,
    );
    await _previewController!.initialize();
    _previewController!.addListener(_onPreviewStateChanged);
  }

  void _onPreviewStateChanged() {
    if (!mounted) return;

    final controller = _previewController;
    if (controller != null &&
        (controller.isPlaying ||
            controller.hasRenderedFirstFrame ||
            controller.position > const Duration(seconds: 2))) {
      _previewSawPlayback = true;
    }

    if (controller?.error != null && !_isPreviewAudioPlaying) {
      debugPrint('XtreamLiveScreen: Playback Error -> ${controller?.error}');
    }

    // Check if a stream stopped after it actually started. Do not reconnect
    // during the initial Media3 prepare/connect phase.
    if (controller?.currentItem != null &&
        _previewSawPlayback &&
        !controller!.isPlaying &&
        !controller.isBuffering &&
        !_isReconnecting &&
        controller.error == null) {
      // Potential unexpected stop
      debugPrint(
        'XtreamLiveScreen: Playback Stopped (Unexpected) - Reason: EOF or Server Disconnect',
      );
      _handleReconnect();
    }

    if (_previewOverlayState != _lastPreviewOverlayState) {
      setState(() {});
    }
  }

  bool get _isPreviewAudioPlaying => _previewController?.isPlaying ?? false;

  bool get _hasFatalPreviewError =>
      _previewController?.error != null && !_isPreviewAudioPlaying;

  String get _previewOverlayState {
    final controller = _previewController;
    if (controller == null) return _hasPreviewStarted ? 'connecting' : 'idle';
    if (_hasFatalPreviewError) return 'failed';
    if (controller.isBuffering) return 'buffering';
    if (controller.isPlaying &&
        !controller.hasRenderedFirstFrame &&
        !controller.hasVideoTrack) {
      return 'buffering-video';
    }
    if (_hasPreviewStarted &&
        !controller.isPlaying &&
        controller.error == null &&
        !controller.hasRenderedFirstFrame) {
      return 'connecting';
    }
    return 'ready';
  }

  void _logPreviewOverlayState() {
    final state = _previewOverlayState;
    if (_lastPreviewOverlayState == state) return;
    final controller = _previewController;
    debugPrint(
      'XtreamLiveScreen Overlay: ${_lastPreviewOverlayState ?? "none"} -> $state '
      '(playing=${controller?.isPlaying ?? false}, buffering=${controller?.isBuffering ?? false}, '
      'audio=${controller?.hasAudioTrack ?? false}, video=${controller?.hasVideoTrack ?? false}, '
      'firstFrame=${controller?.hasRenderedFirstFrame ?? false}, error=${controller?.error ?? "none"})',
    );
    _lastPreviewOverlayState = state;
  }

  Future<void> _handleReconnect() async {
    final channel = _previewChannel;
    if (channel == null || _isReconnecting) return;

    _isReconnecting = true;
    debugPrint('XtreamLiveScreen: Attempting one-time reconnection in 1s...');
    await Future.delayed(const Duration(seconds: 1));

    if (mounted && _previewChannel?.id == channel.id) {
      debugPrint('XtreamLiveScreen: Retrying playback for ${channel.name}');
      _onChannelFocused(channel, immediate: true);
    }

    await Future.delayed(const Duration(seconds: 2));
    _isReconnecting = false;
  }

  void _handleTabChange() {
    if (_homeController == null) return;

    // Live TV is index 2
    if (_homeController!.currentIndex != 2) {
      _epgUpdateTimer?.cancel();
      debugPrint('EPG timer cancelled');
      if (mounted) {
        setState(() {
          _hasPreviewStarted = false;
          _previewFocused = false;
        });
      }
      if (_previewController != null) {
        debugPrint('XtreamLiveScreen: Playback Stopped (Tab Changed)');
        debugPrint('XtreamLiveScreen: Player Disposed (Tab Changed)');
        _previewDebounce?.cancel();
        _previewController?.removeListener(_onPreviewStateChanged);
        _previewController?.pause();
        _previewController?.dispose();
        _previewController = null;
      }
    } else {
      _startEpgTimer();
      if (_previewController == null) {
        debugPrint(
          'XtreamLiveScreen: Returned to Live TV tab, re-initializing player',
        );
        _restorePreview();
      }
    }
  }

  Future<void> _restorePreview() async {
    await _initPreviewController();
    // BUG FIX: Removed automatic playback on tab restore to ensure
    // stream only starts when user explicitly selects/focuses a channel
  }

  @override
  void dispose() {
    debugPrint('XtreamLiveScreen: Playback Stopped (Exiting Screen)');
    debugPrint('XtreamLiveScreen: Player Disposed');
    _epgUpdateTimer?.cancel();
    debugPrint('EPG timer cancelled');
    WidgetsBinding.instance.removeObserver(this);
    _homeController?.removeListener(_handleTabChange);
    _backHoldTimer?.cancel();
    _previewDebounce?.cancel();
    _detailsDebounce?.cancel();
    _previewController?.removeListener(_onPreviewStateChanged);
    _previewController?.pause();
    _previewController?.dispose();
    _previewController = null;
    _previewFocusNode.dispose();
    _channelScrollController.removeListener(_scrollListener);
    EpgSourceService.revision.removeListener(_onEpgUpdated);
    _categorySearchButtonFocusNode.dispose();
    _categorySearchInputFocusNode.dispose();
    _categorySearchController.dispose();
    _categoryScrollController.dispose();
    _channelScrollController.dispose();
    _disposeChannelFocusNodes();
    super.dispose();
  }

  FocusNode _channelFocusNodeFor(ContentItem channel) {
    return _channelFocusNodes.putIfAbsent(
      channel.id,
      () => FocusNode(debugLabel: 'liveChannel:${channel.id}'),
    );
  }

  void _disposeChannelFocusNodes() {
    for (final node in _channelFocusNodes.values) {
      node.dispose();
    }
    _channelFocusNodes.clear();
  }

  void _requestChannelFocus(ContentItem channel) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _channelFocusNodes[channel.id]?.requestFocus();
    });
  }

  void _onEpgUpdated() {
    if (!mounted) return;
    setState(() {
      _rowProgramCache.clear();
      _rowProgramLoads.clear();
    });
    if (_detailsChannel != null) _fetchEpg(_detailsChannel!);
  }

  void _scrollListener() {
    if (_channelScrollController.position.pixels >=
        _channelScrollController.position.maxScrollExtent - 400) {
      if (!_isMoreLoading && _hasMore) {
        _loadMoreItems();
      }
    }
  }

  Future<void> _onCategorySelected(CategoryViewModel category) async {
    if (_selectedCategory?.category.categoryId ==
        category.category.categoryId) {
      return;
    }

    _detailsDebounce?.cancel();
    _previewDebounce?.cancel();
    await _previewController?.stop();
    _disposeChannelFocusNodes();

    setState(() {
      _selectedCategory = category;
      _currentItems.clear();
      _currentOffset = 0;
      _hasMore = true;
      _isMoreLoading = true;
      _focusedChannel = null;
      _detailsChannel = null;
      _previewChannel = null;
      _hasPreviewStarted = false;
      _epgPrograms = [];
      _rowProgramCache.clear();
      _rowProgramLoads.clear();
    });

    await _loadMoreItems();

    if (_currentItems.isNotEmpty && mounted) {
      // BUG FIX: Only set focused channel state, do not trigger playback automatically
      final firstChannel = _currentItems.first;
      setState(() {
        _focusedChannel = firstChannel;
        _detailsChannel = firstChannel;
      });
      _fetchEpg(firstChannel);
      _requestChannelFocus(firstChannel);
    }
  }

  Future<void> _loadMoreItems() async {
    if (_selectedCategory == null) return;

    setState(() => _isMoreLoading = true);

    try {
      final controller = Provider.of<XtreamCodeHomeController>(
        context,
        listen: false,
      );
      final newItems = await controller.getCategoryItems(
        _selectedCategory!.category,
        top: _pageSize,
        offset: _currentOffset,
      );

      if (mounted) {
        setState(() {
          _currentItems.addAll(newItems);
          _sortLoadedChannels();
          _currentOffset += newItems.length;
          _isMoreLoading = false;
          if (newItems.length < _pageSize) {
            _hasMore = false;
          }
        });
        _primeCurrentProgramCache(newItems.take(16));
      }
    } catch (e) {
      if (mounted) setState(() => _isMoreLoading = false);
    }
  }

  void _sortLoadedChannels() {
    int channelNumber(ContentItem item) => int.tryParse(item.id) ?? 0;
    switch (_channelSortOrder) {
      case 'server':
        break;
      case 'az':
        _currentItems.sort(
          (a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()),
        );
        break;
      case 'za':
        _currentItems.sort(
          (a, b) => b.name.toLowerCase().compareTo(a.name.toLowerCase()),
        );
        break;
      case 'number':
        _currentItems.sort(
          (a, b) => channelNumber(a).compareTo(channelNumber(b)),
        );
        break;
      case 'recent':
        _currentItems.sort(
          (a, b) => channelNumber(b).compareTo(channelNumber(a)),
        );
        break;
      default:
        break;
    }
  }

  Future<void> _showLiveSetupDialog() async {
    final playlist = widget.playlist ?? AppState.currentPlaylist;
    final controller = _homeController;
    if (playlist == null || controller == null) return;

    final categories = (controller.liveCategories ?? const [])
        .where((category) => _canHideCategory(category.category.categoryId))
        .toList();
    final hidden = Set<String>.from(_hiddenLiveCategoryIds);
    var showNumbers = _showChannelNumbers;
    var showIcons = _showChannelIcons;
    var showNames = _showChannelNames;
    var showProgram = _showCurrentProgram;
    var rowSize = _liveRowSize;
    var sortOrder = _channelSortOrder;

    final saved = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          insetPadding: const EdgeInsets.symmetric(
            horizontal: 24,
            vertical: 16,
          ),
          titlePadding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
          contentPadding: const EdgeInsets.symmetric(horizontal: 12),
          actionsPadding: const EdgeInsets.fromLTRB(16, 6, 16, 12),
          backgroundColor: const Color(0xFF111525),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: const BorderSide(color: Color(0xFFC12CFF), width: 1.4),
          ),
          title: const Row(
            children: [
              Icon(Icons.tune_rounded, color: Color(0xFFC12CFF)),
              SizedBox(width: 10),
              Text(
                'Live TV Setup',
                style: TextStyle(color: Colors.white, fontSize: 20),
              ),
            ],
          ),
          content: SizedBox(
            width: MediaQuery.sizeOf(context).width * 0.62,
            height: MediaQuery.sizeOf(context).height * 0.68,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _setupSwitchTile(
                          title: 'Show channel numbers',
                          value: showNumbers,
                          onChanged: (value) =>
                              setDialogState(() => showNumbers = value),
                        ),
                        _setupSwitchTile(
                          title: 'Show channel icons',
                          value: showIcons,
                          onChanged: (value) =>
                              setDialogState(() => showIcons = value),
                        ),
                        _setupSwitchTile(
                          title: 'Show channel names',
                          value: showNames,
                          onChanged: (value) =>
                              setDialogState(() => showNames = value),
                        ),
                        _setupSwitchTile(
                          title: 'Show current programme',
                          value: showProgram,
                          onChanged: (value) =>
                              setDialogState(() => showProgram = value),
                        ),
                        const Divider(color: Colors.white12, height: 18),
                        const Align(
                          alignment: Alignment.centerLeft,
                          child: Padding(
                            padding: EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            child: Text(
                              'Channel row size',
                              style: TextStyle(
                                color: Colors.white70,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ),
                        for (final option in const [
                          ('compact', 'Compact'),
                          ('normal', 'Normal'),
                          ('large', 'Large'),
                        ])
                          ListTile(
                            dense: true,
                            visualDensity: const VisualDensity(vertical: -2),
                            leading: Icon(
                              rowSize == option.$1
                                  ? Icons.radio_button_checked_rounded
                                  : Icons.radio_button_off_rounded,
                              color: rowSize == option.$1
                                  ? const Color(0xFFC12CFF)
                                  : Colors.white54,
                            ),
                            title: Text(
                              option.$2,
                              style: const TextStyle(color: Colors.white),
                            ),
                            onTap: () =>
                                setDialogState(() => rowSize = option.$1),
                          ),
                        const Divider(color: Colors.white12, height: 18),
                        const Align(
                          alignment: Alignment.centerLeft,
                          child: Padding(
                            padding: EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            child: Text(
                              'Sort order',
                              style: TextStyle(
                                color: Colors.white70,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ),
                        for (final option in const [
                          ('server', 'Provider order'),
                          ('recent', 'Recently added'),
                          ('az', 'A-Z'),
                          ('za', 'Z-A'),
                          ('number', 'Channel number'),
                        ])
                          ListTile(
                            dense: true,
                            visualDensity: const VisualDensity(vertical: -2),
                            leading: Icon(
                              sortOrder == option.$1
                                  ? Icons.radio_button_checked_rounded
                                  : Icons.radio_button_off_rounded,
                              color: sortOrder == option.$1
                                  ? const Color(0xFFC12CFF)
                                  : Colors.white54,
                            ),
                            title: Text(
                              option.$2,
                              style: const TextStyle(color: Colors.white),
                            ),
                            onTap: () =>
                                setDialogState(() => sortOrder = option.$1),
                          ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    children: [
                      const Align(
                        alignment: Alignment.centerLeft,
                        child: Padding(
                          padding: EdgeInsets.fromLTRB(8, 0, 8, 8),
                          child: Text(
                            'Visible categories',
                            style: TextStyle(
                              color: Colors.white70,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      ),
                      Expanded(
                        child: ListView.builder(
                          itemCount: categories.length,
                          itemBuilder: (context, index) {
                            final category = categories[index].category;
                            final visible = !hidden.contains(
                              category.categoryId,
                            );
                            return CheckboxListTile(
                              dense: true,
                              value: visible,
                              activeColor: const Color(0xFFC12CFF),
                              checkColor: Colors.white,
                              title: Text(
                                category.categoryName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(color: Colors.white),
                              ),
                              onChanged: (value) {
                                setDialogState(() {
                                  if (value == true) {
                                    hidden.remove(category.categoryId);
                                  } else {
                                    hidden.add(category.categoryId);
                                  }
                                });
                              },
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('CANCEL'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('SAVE'),
            ),
          ],
        ),
      ),
    );

    if (saved != true || !mounted) return;

    await Future.wait([
      UserPreferences.setLiveHiddenCategoryIds(playlist.id, hidden.toList()),
      UserPreferences.setLiveShowChannelNumbers(showNumbers),
      UserPreferences.setLiveShowChannelIcons(showIcons),
      UserPreferences.setLiveShowChannelNames(showNames),
      UserPreferences.setLiveShowCurrentProgram(showProgram),
      UserPreferences.setLiveRowSize(rowSize),
      UserPreferences.setLiveSortOrder(sortOrder),
    ]);
    if (!mounted) return;

    setState(() {
      _hiddenLiveCategoryIds
        ..clear()
        ..addAll(hidden);
      _showChannelNumbers = showNumbers;
      _showChannelIcons = showIcons;
      _showChannelNames = showNames;
      _showCurrentProgram = showProgram;
      _liveRowSize = rowSize;
      _channelSortOrder = sortOrder;
      _sortLoadedChannels();
    });

    if (_selectedCategory != null &&
        hidden.contains(_selectedCategory!.category.categoryId)) {
      final visibleCategories = _visibleLiveCategories(controller);
      final next = visibleCategories.isEmpty ? null : visibleCategories.first;
      if (next != null) await _onCategorySelected(next);
    }
  }

  Widget _setupSwitchTile({
    required String title,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return SwitchListTile(
      dense: true,
      value: value,
      activeThumbColor: const Color(0xFFC12CFF),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      onChanged: onChanged,
    );
  }

  bool _canHideCategory(String categoryId) {
    return categoryId != IptvRepository.virtualAll &&
        categoryId != IptvRepository.virtualFavorites &&
        categoryId != IptvRepository.virtualHistory;
  }

  List<CategoryViewModel> _visibleLiveCategories(
    XtreamCodeHomeController controller,
  ) {
    return (controller.liveCategories ?? const [])
        .where(
          (category) =>
              !_canHideCategory(category.category.categoryId) ||
              !_hiddenLiveCategoryIds.contains(category.category.categoryId),
        )
        .toList();
  }

  Future<void> _clearLiveHistory() async {
    final playlist = widget.playlist ?? AppState.currentPlaylist;
    if (playlist == null) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Clear Live TV History'),
        content: const Text('Remove every channel from Live TV history?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('CANCEL'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('CLEAR'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await WatchHistoryService().clearHistoryByContentType(
      playlist.id,
      ContentType.liveStream,
    );
    if (!mounted) return;
    _detailsDebounce?.cancel();
    _previewDebounce?.cancel();
    _disposeChannelFocusNodes();
    setState(() {
      _currentItems.clear();
      _currentOffset = 0;
      _hasMore = false;
      _focusedChannel = null;
      _detailsChannel = null;
      _previewChannel = null;
      _hasPreviewStarted = false;
      _epgPrograms = [];
      _categoryCounts[IptvRepository.virtualHistory] = 0;
    });
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Live TV history cleared.')));
  }

  void _startEpgTimer() {
    _epgUpdateTimer?.cancel();
    _epgUpdateTimer = Timer.periodic(const Duration(seconds: 60), (timer) {
      if (_detailsChannel != null) {
        _fetchEpg(_detailsChannel!);
      }
    });
  }

  bool get _epgAuditEnabled => false;

  Future<void> _fetchEpg(ContentItem channel) async {
    final requestId = ++_epgRequestId;
    final now = DateTime.now();
    final audit = _epgAuditEnabled;
    if (audit) {
      debugPrint('--- EPG AUDIT START ---');
      debugPrint('Channel Name: ${channel.name}');
      debugPrint('Stream ID: ${channel.id}');
      debugPrint('tvg_id: ${channel.m3uItem?.tvgId}');
      debugPrint('epg_channel_id: ${channel.liveStream?.epgChannelId}');
      debugPrint('Category ID: ${channel.liveStream?.categoryId}');
      final normalized = _epgService.normalizeName(channel.name);
      debugPrint('Normalized Name: $normalized');
    }

    final liveStream = channel.liveStream;
    final m3uItem = channel.m3uItem;

    String? playlistId = liveStream?.playlistId ?? m3uItem?.playlistId;
    if (playlistId == null) {
      final currentPlaylist = widget.playlist ?? AppState.currentPlaylist;
      playlistId = currentPlaylist?.id;
    }

    if (playlistId == null) {
      if (audit) debugPrint('EPG Audit: No playlist ID found');
      if (mounted && requestId == _epgRequestId) {
        setState(() => _epgPrograms = []);
      }
      return;
    }

    if (audit) {
      final channelCount = await _epgService.getChannelCount(playlistId);
      final programCount = await _epgService.getProgramCount(playlistId);
      debugPrint('Total EPG Channels in DB: $channelCount');
      debugPrint('Total EPG Programmes in DB: $programCount');
    }

    // Identifiers to try in order: tvg-id, epg_channel_id, stream_id, name
    final searchStrategies = [
      {'label': 'tvg_id', 'id': m3uItem?.tvgId},
      {'label': 'epg_channel_id', 'id': liveStream?.epgChannelId},
      {'label': 'stream_id', 'id': liveStream?.streamId},
      {'label': 'm3u_id', 'id': m3uItem?.id},
    ];

    try {
      List<EpgProgramWindow> programs = [];
      String? matchedId;
      String? matchedStrategy;

      for (final strategy in searchStrategies) {
        final id = strategy['id'];
        if (id == null || id.isEmpty) continue;

        programs = await _epgService.getProgramsForWindow(
          playlistId: playlistId,
          epgChannelId: id,
          start: now.subtract(const Duration(minutes: 5)),
          end: now.add(const Duration(hours: 12)),
          limit: 3,
        );

        if (programs.isNotEmpty) {
          matchedId = id;
          matchedStrategy = strategy['label'];
          if (audit) {
            debugPrint('EPG Match Found using $matchedStrategy: $matchedId');
          }
          break;
        }
      }

      // Fallback: Name matching if still empty
      if (programs.isEmpty) {
        if (audit) debugPrint('EPG: Trying name-based matching fallback...');
        programs = await _epgService.getProgramsByChannelName(
          playlistId: playlistId,
          displayName: channel.name,
          start: now.subtract(const Duration(minutes: 5)),
          end: now.add(const Duration(hours: 12)),
          limit: 3,
        );
        if (programs.isNotEmpty) {
          if (audit) debugPrint('EPG Match Found using Normalized Name');
          matchedStrategy = 'normalized_name';
        }
      }

      if (audit) {
        debugPrint(
          'EPG Matching Status: ${programs.isNotEmpty ? "SUCCESS" : "FAILED"}',
        );
        debugPrint('Lookup Timestamp (Local): $now');
        debugPrint('Lookup Timestamp (UTC): ${now.toUtc()}');

        if (programs.isEmpty) {
          debugPrint('EPG audit: No programmes found for this channel');
        } else {
          debugPrint('Number of programmes found: ${programs.length}');
          final current = programs.firstWhere(
            (p) =>
                (p.start.isBefore(now) || p.start.isAtSameMomentAs(now)) &&
                p.end.isAfter(now),
            orElse: () => programs.first,
          );

          bool isAiringNow =
              (current.start.isBefore(now) ||
                  current.start.isAtSameMomentAs(now)) &&
              current.end.isAfter(now);

          debugPrint(
            'Current Programme: ${current.title} (${current.start} - ${current.end})',
          );
          if (programs.length > 1) {
            debugPrint('Next Programme: ${programs[1].title}');
          }

          if (!isAiringNow) {
            debugPrint(
              'EPG channel matched but no current programme at this time (Current time: $now)',
            );
          }
        }
        debugPrint('--- EPG AUDIT END ---');
      }

      if (mounted && requestId == _epgRequestId) {
        setState(() => _epgPrograms = programs);
      }
    } catch (e) {
      if (audit) debugPrint('EPG Error: $e');
      if (mounted && requestId == _epgRequestId) {
        setState(() => _epgPrograms = []);
      }
    }
  }

  void _onChannelFocused(ContentItem channel, {bool immediate = false}) {
    // This is the preview-start path, not the D-pad highlight path.
    if (_previewChannel?.id == channel.id &&
        _previewController?.currentItem?.id == channel.id &&
        _previewController?.error == null) {
      return;
    }

    debugPrint('XtreamLiveScreen: Channel Selected -> ${channel.name}');

    // BUG FIX: Clear error state and cancel old requests immediately
    _detailsDebounce?.cancel();
    _previewController?.stop();

    setState(() {
      _focusedChannel = channel;
      _detailsChannel = channel;
      _previewChannel = channel;
      _hasPreviewStarted = true;
      _epgPrograms = [];
    });
    _fetchEpg(channel);

    // Debounce preview playback to prevent rapid stream switching while scrolling
    _previewDebounce?.cancel();

    final requestId = ++_previewLoadRequestId;

    Future<void> startPlayback() async {
      if (!mounted || requestId != _previewLoadRequestId) return;

      // BUG FIX: Strictly suppress playback if not on the Live TV tab
      if (_homeController?.currentIndex != 2) {
        debugPrint('XtreamLiveScreen: Suppressing playback, tab is not active');
        return;
      }

      // BUG #1 & #5 FIX: Check if we are already playing this exact source
      final currentSourceId = _previewController?.currentItem?.id;
      if (currentSourceId == channel.id) {
        debugPrint(
          'XtreamLiveScreen: Skipping Playback Requested, source already loaded.',
        );
        return;
      }

      if (_previewController == null) {
        debugPrint('XtreamLiveScreen: Player Created');
        await _initPreviewController();
      }

      if (!mounted || requestId != _previewLoadRequestId) return;

      final playlist = widget.playlist ?? AppState.currentPlaylist;
      if (playlist == null) {
        debugPrint(
          'XtreamLiveScreen: CRITICAL - No playlist context available',
        );
        return;
      }

      debugPrint(
        'XtreamLiveScreen: Playback Requested -> ${channel.name} (Req: $requestId)',
      );
      final resolvedUrl = await PlaybackUrlResolver.resolveUrl(
        item: channel,
        playlist: playlist,
      );

      if (!mounted || requestId != _previewLoadRequestId) return;

      if (resolvedUrl == null || resolvedUrl.isEmpty) {
        debugPrint(
          'XtreamLiveScreen: Playback Error -> Failed to resolve URL for ${channel.name}',
        );
        return;
      }

      debugPrint('XtreamLiveScreen: Player Opened -> ${channel.name}');
      final playbackItem = PlaybackItem.fromContentItem(
        channel,
      ).copyWith(url: resolvedUrl);

      _previewSawPlayback = false;
      await _previewController!.setDataSource(playbackItem);
      debugPrint('XtreamLiveScreen: Playback Started -> ${channel.name}');
    }

    if (immediate) {
      startPlayback();
    } else {
      // Increased debounce to 500ms for stability on remotes
      _previewDebounce = Timer(
        const Duration(milliseconds: 500),
        startPlayback,
      );
    }
  }

  void _onChannelHighlighted(ContentItem channel) {
    if (_focusedChannel?.id == channel.id) return;
    _focusedChannel = channel;
    _scheduleDetailsUpdate(channel);
  }

  void _scheduleDetailsUpdate(ContentItem channel) {
    _detailsDebounce?.cancel();
    if (_detailsChannel?.id == channel.id) return;
    if (_isPreviewAudioPlaying) return;

    _detailsDebounce = Timer(const Duration(milliseconds: 250), () {
      if (!mounted || _focusedChannel?.id != channel.id) return;
      if (_isPreviewAudioPlaying) return;
      setState(() {
        _detailsChannel = channel;
        _epgPrograms = [];
      });
      _fetchEpg(channel);
    });
  }

  void _enterFullscreen() {
    final channel = _previewChannel ?? _focusedChannel;
    if (channel == null || _previewController == null) return;

    debugPrint('XtreamLiveScreen: Pausing preview for fullscreen');

    _previewDebounce?.cancel();
    // Stop monitoring preview state while in fullscreen
    _previewController?.removeListener(_onPreviewStateChanged);
    _previewController?.stop();
    _previewController?.dispose();
    _previewController = null;

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => UnifiedPlayerScreen(
          contentItem: channel,
          queue: _currentItems,
          // Use a separate controller for fullscreen as per Requirement #10
        ),
      ),
    ).then((_) {
      debugPrint(
        'XtreamLiveScreen: Returned from fullscreen, resuming preview and EPG',
      );
      if (mounted) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            _channelFocusNodes[channel.id]?.requestFocus();
          }
        });
        _focusedChannel = channel;
        _detailsChannel = channel;
        _fetchEpg(channel);
        _onChannelFocused(channel, immediate: true);
      }
    });
  }

  void _activatePreviewPanel() {
    final channel = _focusedChannel ?? _detailsChannel ?? _previewChannel;
    if (channel == null) return;

    final previewReadyForChannel =
        _hasPreviewStarted &&
        _previewChannel?.id == channel.id &&
        _previewController?.currentItem?.id == channel.id &&
        _previewController?.error == null;

    if (previewReadyForChannel) {
      _enterFullscreen();
    } else {
      _onChannelFocused(channel, immediate: true);
    }
  }

  KeyEventResult _handleLiveKeyEvent(FocusNode node, KeyEvent event) {
    final isBackKey =
        event.logicalKey == LogicalKeyboardKey.goBack ||
        event.logicalKey == LogicalKeyboardKey.escape;

    if (!isBackKey) return KeyEventResult.ignored;

    if (event is KeyDownEvent && _backHoldTimer == null) {
      if (_categorySearchEditing) {
        setState(() => _categorySearchEditing = false);
        _categorySearchInputFocusNode.unfocus();
        _categorySearchButtonFocusNode.requestFocus();
        return KeyEventResult.handled;
      }

      _backHoldTriggered = false;
      _backHoldTimer = Timer(const Duration(milliseconds: 650), () {
        _backHoldTimer = null;
        _backHoldTriggered = true;
        if (mounted) _enterFullscreen();
      });
    } else if (event is KeyUpEvent) {
      _backHoldTimer?.cancel();
      _backHoldTimer = null;
      if (!_backHoldTriggered) {
        _homeController?.onNavigationTap(0);
      }
      _backHoldTriggered = false;
    }

    return KeyEventResult.handled;
  }

  Future<void> _forceRefreshEpg() async {
    final playlist = widget.playlist ?? AppState.currentPlaylist;
    if (playlist == null) return;

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Starting EPG Refresh...'),
        backgroundColor: Color(0xFFC12CFF),
      ),
    );

    try {
      String? xmltvUrl;
      if (playlist.type == PlaylistType.xtream) {
        // Construct Xtream XMLTV URL
        String baseUrl = playlist.url ?? '';
        if (baseUrl.contains('/player_api.php')) {
          baseUrl = baseUrl.split('/player_api.php')[0];
        }
        xmltvUrl =
            '$baseUrl/xmltv.php?username=${playlist.username}&password=${playlist.password}';
      }

      if (xmltvUrl == null) {
        throw Exception('EPG URL not available for this playlist type');
      }

      final importService = EpgImportService(storage: _epgService);
      final result = await importService.importUrl(
        playlistId: playlist.id,
        url: xmltvUrl,
      );
      EpgSourceService.notifyUpdated();

      debugPrint('EPG Refresh Complete: ${result.currentItem}');

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('EPG Refresh Complete: ${result.currentItem}'),
            backgroundColor: Colors.green,
          ),
        );
        if (_detailsChannel != null) {
          _fetchEpg(_detailsChannel!);
        }
      }
    } catch (e) {
      debugPrint('EPG Refresh Error: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('EPG Refresh Failed: $e'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    _logPreviewOverlayState();
    final config = context.watch<ConfigService>().config;
    final themeManager = context.watch<ThemeManager>();
    final homeBg = config.backgrounds.home;

    return Focus(
      autofocus: true,
      onKeyEvent: _handleLiveKeyEvent,
      child: Consumer<XtreamCodeHomeController>(
        builder: (context, controller, child) {
          return Container(
            color: const Color(0xFF050812),
            child: Container(
              width: double.infinity,
              height: double.infinity,
              decoration: BoxDecoration(
                color: const Color(0xFF050812),
                image: DecorationImage(
                  image: (themeManager.showBackgroundImage && homeBg.isNotEmpty)
                      ? perfNetworkImage(homeBg)
                      : const AssetImage('assets/images/background.png')
                            as ImageProvider,
                  fit: BoxFit.cover,
                ),
              ),
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      const Color(0xFF050812).withValues(alpha: 0.2),
                      const Color(0xFF050812).withValues(alpha: 0.6),
                      const Color(0xFF050812).withValues(alpha: 0.9),
                    ],
                  ),
                ),
                child: Column(
                  children: [
                    // HEADER
                    WatchioHeader(
                      isCompact: true,
                      customLogoHeight: 90,
                      sectionTitle: 'Live TV',
                      onBack: () => controller.onNavigationTap(0),
                      onSearch: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) =>
                              SearchScreen(contentType: ContentType.liveStream),
                        ),
                      ),
                      onSettings: () => controller.onNavigationTap(5),
                      onSetup: _showLiveSetupDialog,
                      onRefresh: _showLiveSetupDialog,
                      onRefreshEpg: _forceRefreshEpg,
                      onClearHistory:
                          _selectedCategory?.category.categoryId ==
                              IptvRepository.virtualHistory
                          ? _clearLiveHistory
                          : null,
                    ),

                    if (controller.liveCategories?.isEmpty ?? true)
                      Expanded(child: _buildLibraryNotLoaded(controller))
                    else
                      // MAIN CONTENT (3 Columns)
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                          child: Row(
                            children: [
                              // LEFT PANEL (22%) - Categories
                              Expanded(
                                flex: 24,
                                child: GlassPanel(
                                  opacity: 0.1,
                                  blur: 20,
                                  gradient: contentPanelGradientOf(context),
                                  child: _buildCategoryPanel(controller),
                                ),
                              ),
                              const SizedBox(width: 16),

                              // CENTER PANEL (33%) - Channels
                              Expanded(
                                flex: 28,
                                child: GlassPanel(
                                  opacity: 0.1,
                                  blur: 20,
                                  gradient: contentPanelGradientOf(context),
                                  child: _buildChannelPanel(),
                                ),
                              ),
                              const SizedBox(width: 16),

                              // RIGHT PANEL (45%) - Preview & EPG
                              Expanded(
                                flex: 48,
                                child: FocusTraversalGroup(
                                  policy: ReadingOrderTraversalPolicy(),
                                  child: _buildPreviewPanel(),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildLibraryNotLoaded(XtreamCodeHomeController controller) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.cloud_download_outlined,
              color: Color(0xFF00B7FF),
              size: 56,
            ),
            const SizedBox(height: 16),
            const Text(
              'Live TV library not loaded yet',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white,
                fontSize: 22,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Use library refresh when it is added. Home now opens without preloading channels.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white60),
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () => controller.onNavigationTap(0),
              icon: const Icon(Icons.home_rounded),
              label: const Text('BACK HOME'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCategoryPanel(XtreamCodeHomeController controller) {
    final allCategories = _visibleLiveCategories(controller);
    final query = _categoryQuery.trim().toLowerCase();
    final categories = query.isEmpty
        ? allCategories
        : allCategories
              .where(
                (category) => category.category.categoryName
                    .toLowerCase()
                    .contains(query),
              )
              .toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Category Search
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 6),
          child: SizedBox(height: 42, child: _buildCategorySearchField()),
        ),
        const Divider(color: Colors.white10, height: 1),
        Expanded(
          child: FocusTraversalGroup(
            policy: ReadingOrderTraversalPolicy(),
            child: ListView.separated(
              controller: _categoryScrollController,
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 3),
              itemCount: categories.length,
              separatorBuilder: (_, _) => const Divider(
                color: Colors.white10,
                height: 1,
                indent: 8,
                endIndent: 8,
              ),
              itemBuilder: (context, index) {
                final cat = categories[index];
                final isSelected =
                    _selectedCategory?.category.categoryId ==
                    cat.category.categoryId;

                return _CategoryItem(
                  icon: _getCategoryIcon(cat.category.categoryId),
                  label: cat.category.categoryName.toUpperCase(),
                  count: _categoryCounts[cat.category.categoryId] ?? 0,
                  isSelected: isSelected,
                  onTap: () {
                    if (!isSelected) {
                      _onCategorySelected(cat);
                      _channelScrollController.jumpTo(0);
                    }
                  },
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCategorySearchField() {
    final borderRadius = BorderRadius.circular(10);
    final decoration = InputDecoration(
      isDense: true,
      hintText: 'Search in categories',
      hintStyle: const TextStyle(color: Colors.white38, fontSize: 11),
      prefixIcon: const Icon(Icons.search, color: Colors.white54, size: 17),
      prefixIconConstraints: const BoxConstraints(minWidth: 38),
      suffixIcon: _categoryQuery.isEmpty
          ? null
          : IconButton(
              padding: EdgeInsets.zero,
              visualDensity: VisualDensity.compact,
              icon: const Icon(Icons.close_rounded, color: Colors.white54),
              onPressed: () {
                _categorySearchController.clear();
                setState(() {
                  _categoryQuery = '';
                  _categorySearchEditing = false;
                });
                _categorySearchInputFocusNode.unfocus();
                _categorySearchButtonFocusNode.requestFocus();
              },
            ),
      filled: true,
      fillColor: Colors.black.withValues(alpha: 0.18),
      contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
      border: OutlineInputBorder(borderRadius: borderRadius),
      enabledBorder: OutlineInputBorder(
        borderRadius: borderRadius,
        borderSide: const BorderSide(color: Colors.white10),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: borderRadius,
        borderSide: const BorderSide(color: Color(0xFFC12CFF), width: 2),
      ),
    );

    if (_categorySearchEditing) {
      return TextField(
        focusNode: _categorySearchInputFocusNode,
        controller: _categorySearchController,
        style: const TextStyle(color: Colors.white, fontSize: 12),
        decoration: decoration,
        textInputAction: TextInputAction.search,
        onChanged: (value) => setState(() => _categoryQuery = value),
        onSubmitted: (_) {
          setState(() => _categorySearchEditing = false);
          _categorySearchInputFocusNode.unfocus();
          _categorySearchButtonFocusNode.requestFocus();
        },
      );
    }

    return WatchioFocusAction(
      focusNode: _categorySearchButtonFocusNode,
      onFocusChange: (focused) =>
          setState(() => _categorySearchFocused = focused),
      onActivate: _activateCategorySearch,
      child: InkWell(
        onTap: _activateCategorySearch,
        borderRadius: borderRadius,
        child: AnimatedContainer(
          duration: perfDuration(const Duration(milliseconds: 160)),
          decoration: BoxDecoration(
            borderRadius: borderRadius,
            border: Border.all(
              color: _categorySearchFocused
                  ? const Color(0xFFC12CFF)
                  : Colors.transparent,
              width: 2,
            ),
            boxShadow: firestickPerformanceMode || !_categorySearchFocused
                ? []
                : [
                    BoxShadow(
                      color: const Color(0xFFC12CFF).withValues(alpha: 0.35),
                      blurRadius: 12,
                    ),
                  ],
          ),
          child: IgnorePointer(
            child: TextField(
              controller: _categorySearchController,
              canRequestFocus: false,
              readOnly: true,
              style: const TextStyle(color: Colors.white, fontSize: 12),
              decoration: decoration.copyWith(
                fillColor: _categorySearchFocused
                    ? const Color(0xFFC12CFF).withValues(alpha: 0.14)
                    : Colors.black.withValues(alpha: 0.18),
                enabledBorder: OutlineInputBorder(
                  borderRadius: borderRadius,
                  borderSide: BorderSide(
                    color: _categorySearchFocused
                        ? const Color(0xFFC12CFF)
                        : Colors.white10,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _activateCategorySearch() {
    setState(() => _categorySearchEditing = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _categorySearchInputFocusNode.requestFocus();
      SystemChannels.textInput.invokeMethod<void>('TextInput.show');
    });
  }

  Widget _buildChannelPanel() {
    return Column(
      children: [
        Expanded(
          child: FocusTraversalGroup(
            policy: ReadingOrderTraversalPolicy(),
            child: ListView.separated(
              controller: _channelScrollController,
              padding: const EdgeInsets.all(8),
              scrollCacheExtent: const ScrollCacheExtent.pixels(520),
              itemCount: _currentItems.length + (_isMoreLoading ? 1 : 0),
              separatorBuilder: (_, _) =>
                  const SizedBox(height: 4), // Reduced from 6
              itemBuilder: (context, index) {
                if (index < _currentItems.length) {
                  final channel = _currentItems[index];
                  final isPreviewed = _previewChannel?.id == channel.id;
                  final currentProgram = _currentProgramFromCache(channel);

                  return _ChannelItem(
                    key: ValueKey('live-channel-${channel.id}'),
                    focusNode: _channelFocusNodeFor(channel),
                    channel: channel,
                    index: index + 1,
                    isFocused: isPreviewed,
                    currentProgram: currentProgram,
                    showChannelNumbers: _showChannelNumbers,
                    showChannelIcons: _showChannelIcons,
                    showChannelNames: _showChannelNames,
                    showCurrentProgram: _showCurrentProgram,
                    rowSize: _liveRowSize,
                    onFocus: () => _onChannelHighlighted(channel),
                    onMoveRight: () => _previewFocusNode.requestFocus(),
                    onTap: () {
                      final previewReadyForChannel =
                          _hasPreviewStarted &&
                          _previewChannel?.id == channel.id &&
                          _previewController?.currentItem?.id == channel.id &&
                          _previewController?.error == null;

                      if (previewReadyForChannel) {
                        _enterFullscreen();
                      } else {
                        _onChannelFocused(channel, immediate: true);
                      }
                    },
                  );
                } else {
                  return const Center(
                    child: Padding(
                      padding: EdgeInsets.all(8.0),
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Color(0xFFC12CFF),
                      ),
                    ),
                  );
                }
              },
            ),
          ),
        ),
      ],
    );
  }

  void _primeCurrentProgramCache(Iterable<ContentItem> channels) {
    for (final channel in channels) {
      _cachedCurrentProgram(channel);
    }
  }

  EpgProgramWindow? _cachedCurrentProgram(ContentItem channel) {
    final key = _rowProgramKey(channel);
    if (key == null) return null;
    if (!_rowProgramCache.containsKey(key) && !_rowProgramLoads.contains(key)) {
      _loadCurrentProgram(channel, key);
    }
    return _rowProgramCache[key];
  }

  EpgProgramWindow? _currentProgramFromCache(ContentItem channel) {
    final key = _rowProgramKey(channel);
    if (key == null) return null;
    return _rowProgramCache[key];
  }

  String? _rowProgramKey(ContentItem channel) {
    final liveStream = channel.liveStream;
    if (liveStream == null) return null;
    final playlistId = liveStream.playlistId;
    final epgId = liveStream.epgChannelId;
    if (playlistId == null || epgId.isEmpty) return null;
    return '$playlistId::$epgId';
  }

  Future<void> _loadCurrentProgram(ContentItem channel, String key) async {
    _rowProgramLoads.add(key);
    try {
      final liveStream = channel.liveStream;
      if (liveStream == null || liveStream.playlistId == null) return;
      final programs = await _epgService.getProgramsForWindow(
        playlistId: liveStream.playlistId!,
        epgChannelId: liveStream.epgChannelId,
        start: DateTime.now(),
        end: DateTime.now().add(const Duration(minutes: 5)),
        limit: 1,
      );
      if (mounted && !_isPreviewAudioPlaying) {
        setState(() {
          _rowProgramCache[key] = programs.isNotEmpty ? programs.first : null;
        });
      } else {
        _rowProgramCache[key] = programs.isNotEmpty ? programs.first : null;
      }
    } catch (_) {
      if (mounted && !_isPreviewAudioPlaying) {
        setState(() => _rowProgramCache[key] = null);
      } else {
        _rowProgramCache[key] = null;
      }
    } finally {
      _rowProgramLoads.remove(key);
    }
  }

  Widget _buildPreviewPanel() {
    final displayChannel =
        _detailsChannel ?? _focusedChannel ?? _previewChannel;
    if (displayChannel == null) return const SizedBox.shrink();

    return Column(
      children: [
        // PREVIEW AREA
        Expanded(
          flex: 5,
          child: Row(
            children: [
              Expanded(
                flex: 3,
                child: Focus(
                  focusNode: _previewFocusNode,
                  onFocusChange: (v) => setState(() => _previewFocused = v),
                  onKeyEvent: (node, event) {
                    if (event is KeyDownEvent &&
                        event.logicalKey == LogicalKeyboardKey.arrowLeft) {
                      final channel =
                          _focusedChannel ?? _detailsChannel ?? _previewChannel;
                      if (channel != null) {
                        _channelFocusNodes[channel.id]?.requestFocus();
                        return KeyEventResult.handled;
                      }
                    }
                    if (event is KeyDownEvent &&
                        WatchioFocusAction.activationShortcuts.keys.any(
                          (shortcut) =>
                              shortcut is SingleActivator &&
                              shortcut.trigger == event.logicalKey,
                        )) {
                      _activatePreviewPanel();
                      return KeyEventResult.handled;
                    }
                    return KeyEventResult.ignored;
                  },
                  child: GestureDetector(
                    onTap: _activatePreviewPanel,
                    onDoubleTap: _activatePreviewPanel,
                    child: AnimatedContainer(
                      duration: perfDuration(const Duration(milliseconds: 200)),
                      width: double.infinity,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(24),
                        border: Border.all(
                          color: _previewFocused
                              ? const Color(0xFFC12CFF)
                              : const Color(0xFFC12CFF).withValues(alpha: 0.3),
                          width: _previewFocused ? 4 : 2,
                        ),
                        boxShadow: firestickPerformanceMode
                            ? []
                            : [
                                BoxShadow(
                                  color: const Color(0xFFC12CFF).withValues(
                                    alpha: _previewFocused ? 0.3 : 0.1,
                                  ),
                                  blurRadius: _previewFocused ? 30 : 20,
                                  spreadRadius: 5,
                                ),
                              ],
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(24),
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            // Video Preview
                            if (!_hasPreviewStarted)
                              Center(
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Image.asset(
                                      'assets/images/App_Logo.png',
                                      width: 155,
                                      fit: BoxFit.contain,
                                    ),
                                    const Text(
                                      'Click a channel to start preview',
                                      style: TextStyle(
                                        color: Colors.white70,
                                        fontSize: 10,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ],
                                ),
                              )
                            else if (_previewController != null)
                              _previewController!.buildPlayerView(
                                context,
                                fit: BoxFit.cover,
                              )
                            else if (displayChannel.imagePath.isNotEmpty)
                              Image.network(
                                displayChannel.imagePath,
                                fit: BoxFit.cover,
                                cacheWidth: firestickPerformanceMode
                                    ? 640
                                    : null,
                                cacheHeight: firestickPerformanceMode
                                    ? 360
                                    : null,
                                errorBuilder: (ctx, err, st) => const Center(
                                  child: Icon(
                                    Icons.live_tv,
                                    size: 80,
                                    color: Colors.white10,
                                  ),
                                ),
                              ),

                            // Loading indicator for preview
                            if (_previewOverlayState == 'buffering')
                              const Center(
                                child: CircularProgressIndicator(
                                  color: Color(0xFFC12CFF),
                                ),
                              ),

                            if (_previewOverlayState == 'connecting' ||
                                _previewOverlayState == 'buffering-video')
                              Container(
                                color: Colors.black.withValues(alpha: 0.72),
                                child: LayoutBuilder(
                                  builder: (context, constraints) {
                                    final showLabel =
                                        constraints.maxHeight >= 105;
                                    final spinnerSize =
                                        constraints.maxHeight < 90
                                        ? 16.0
                                        : 20.0;

                                    return Center(
                                      child: Padding(
                                        padding: const EdgeInsets.all(8),
                                        child: FittedBox(
                                          fit: BoxFit.scaleDown,
                                          child: SizedBox(
                                            width: constraints.maxWidth.clamp(
                                              96.0,
                                              190.0,
                                            ),
                                            child: Column(
                                              mainAxisSize: MainAxisSize.min,
                                              children: [
                                                Image.asset(
                                                  'assets/images/App_Logo.png',
                                                  width: constraints.maxWidth
                                                      .clamp(90.0, 150.0),
                                                  fit: BoxFit.contain,
                                                ),
                                                const SizedBox(height: 2),
                                                SizedBox.square(
                                                  dimension: spinnerSize,
                                                  child:
                                                      const CircularProgressIndicator(
                                                        strokeWidth: 2,
                                                        color: Color(
                                                          0xFFA855F7,
                                                        ),
                                                      ),
                                                ),
                                                if (showLabel) ...[
                                                  const SizedBox(height: 6),
                                                  Text(
                                                    _previewOverlayState ==
                                                            'connecting'
                                                        ? 'Connecting...'
                                                        : 'Buffering Video...',
                                                    maxLines: 1,
                                                    overflow:
                                                        TextOverflow.ellipsis,
                                                    style: const TextStyle(
                                                      color: Colors.white70,
                                                      fontSize: 11,
                                                      fontWeight:
                                                          FontWeight.w600,
                                                    ),
                                                  ),
                                                ],
                                              ],
                                            ),
                                          ),
                                        ),
                                      ),
                                    );
                                  },
                                ),
                              ),

                            // Error message for preview
                            if (_hasFatalPreviewError)
                              Container(
                                color: Colors.black.withValues(alpha: 0.9),
                                padding: const EdgeInsets.all(8),
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    const Icon(
                                      Icons.error_outline_rounded,
                                      color: Colors.redAccent,
                                      size: 32,
                                    ),
                                    const SizedBox(height: 4),
                                    const Text(
                                      'Stream unavailable',
                                      style: TextStyle(
                                        color: Colors.white,
                                        fontSize: 14,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    const Text(
                                      'This channel is offline or not responding.',
                                      style: TextStyle(
                                        color: Colors.white70,
                                        fontSize: 11,
                                      ),
                                      textAlign: TextAlign.center,
                                    ),
                                    const SizedBox(height: 4),
                                    ElevatedButton.icon(
                                      onPressed: () {
                                        final retryChannel =
                                            _previewChannel ?? displayChannel;
                                        if (retryChannel.id.isNotEmpty) {
                                          debugPrint('Manual retry requested');
                                          _onChannelFocused(
                                            retryChannel,
                                            immediate: true,
                                          );
                                        }
                                      },
                                      icon: const Icon(
                                        Icons.refresh_rounded,
                                        size: 18,
                                      ),
                                      label: const Text('Retry'),
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: const Color(
                                          0xFFC12CFF,
                                        ),
                                        foregroundColor: Colors.white,
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 16,
                                          vertical: 6,
                                        ),
                                        visualDensity: VisualDensity.compact,
                                        tapTargetSize:
                                            MaterialTapTargetSize.shrinkWrap,
                                        shape: RoundedRectangleBorder(
                                          borderRadius: BorderRadius.circular(
                                            12,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),

                            // Glass Overlay
                            Positioned.fill(
                              child: Container(
                                decoration: BoxDecoration(
                                  gradient: LinearGradient(
                                    begin: Alignment.topCenter,
                                    end: Alignment.bottomCenter,
                                    colors: [
                                      Colors.transparent,
                                      Colors.black.withValues(alpha: 0.6),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(flex: 2, child: _buildChannelInfoCard(displayChannel)),
            ],
          ),
        ),

        const SizedBox(height: 16),

        // EPG TIMELINE SECTION
        Expanded(
          flex: 5,
          child: GlassPanel(
            opacity: 0.1,
            blur: 20,
            gradient: contentPanelGradientOf(context),
            padding: const EdgeInsets.all(8),
            child: _buildEpgContent(),
          ),
        ),
      ],
    );
  }

  Widget _buildChannelInfoCard(ContentItem channel) {
    final accent = Theme.of(context).colorScheme.primary;
    final now = DateTime.now();
    EpgProgramWindow? currentProgram;
    for (final program in _epgPrograms) {
      if (!program.start.isAfter(now) && program.end.isAfter(now)) {
        currentProgram = program;
        break;
      }
    }

    return GlassPanel(
      blur: 20,
      gradient: contentPanelGradientOf(context),
      padding: const EdgeInsets.all(8),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.75),
              borderRadius: BorderRadius.circular(6),
            ),
            child: const Text(
              'LIVE TV',
              style: TextStyle(
                color: Colors.white,
                fontSize: 11,
                fontWeight: FontWeight.w900,
              ),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            channel.name,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            currentProgram?.title ?? 'No programme information',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: accent,
              fontSize: 10,
              fontWeight: FontWeight.w600,
            ),
          ),
          if (currentProgram != null) ...[
            const SizedBox(height: 3),
            Text(
              '${DateFormat('hh:mm a').format(currentProgram.start)} - ${DateFormat('hh:mm a').format(currentProgram.end)}',
              style: const TextStyle(color: Colors.white54, fontSize: 9),
            ),
          ],
          const SizedBox(height: 4),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 6,
            runSpacing: 4,
            children: [
              _buildInfoTag(
                _selectedCategory?.category.categoryName.replaceAll(
                      RegExp(r'^UK\s*\|\s*'),
                      '',
                    ) ??
                    'LIVE',
                accent,
              ),
              _buildInfoTag(
                channel.name.toUpperCase().contains('4K')
                    ? '4K'
                    : channel.name.toUpperCase().contains('HD')
                    ? 'HD'
                    : 'LIVE',
                const Color(0xFF06B6D4),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildInfoTag(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.7)),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          color: color,
          fontSize: 9,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }

  Widget _buildEpgContent() {
    if (_epgPrograms.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              'No EPG Information Available',
              style: TextStyle(
                color: Colors.white38,
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 4),
            TextButton.icon(
              onPressed: _forceRefreshEpg,
              style: TextButton.styleFrom(
                visualDensity: VisualDensity.compact,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
              ),
              icon: const Icon(
                Icons.refresh_rounded,
                size: 18,
                color: Color(0xFFC12CFF),
              ),
              label: const Text(
                'Refresh EPG',
                style: TextStyle(color: Colors.white70, fontSize: 12),
              ),
            ),
          ],
        ),
      );
    }

    final now = DateTime.now();
    // Try to find the program that is currently airing
    final currentProgramIndex = _epgPrograms.indexWhere(
      (p) =>
          (p.start.isBefore(now) || p.start.isAtSameMomentAs(now)) &&
          p.end.isAfter(now),
    );

    if (currentProgramIndex == -1) {
      // If we have programs but none are "now", check if the first one is in the future
      if (_epgPrograms.first.start.isAfter(now)) {
        return SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'No current programme available',
                style: TextStyle(
                  color: Colors.white38,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const Divider(color: Colors.white10, height: 32),
              _buildEpgSection('UP NEXT', _epgPrograms.first),
            ],
          ),
        );
      } else {
        // Programmes exist but they might all be in the past or data is stale
        return const Center(
          child: Text(
            'No current programme available',
            style: TextStyle(
              color: Colors.white38,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
        );
      }
    }

    final timeline = _epgPrograms.skip(currentProgramIndex).take(2).toList();
    return Column(
      children: List.generate(
        timeline.length,
        (index) => Expanded(
          flex: index == 0 ? 4 : 3,
          child: _buildTimelineItem(
            index == 0 ? 'NOW PLAYING' : 'UP NEXT',
            timeline[index],
            index: index,
            isNow: index == 0,
            isLast: index == timeline.length - 1,
          ),
        ),
      ),
    );
  }

  Widget _buildTimelineItem(
    String label,
    EpgProgramWindow program, {
    required int index,
    required bool isNow,
    required bool isLast,
  }) {
    final accent = Theme.of(context).colorScheme.primary;
    final runtime = program.end.difference(program.start).inMinutes;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          width: 28,
          child: Stack(
            alignment: Alignment.topCenter,
            children: [
              if (!isLast)
                Positioned(
                  top: 18,
                  bottom: 0,
                  child: Container(
                    width: 2,
                    color: accent.withValues(alpha: 0.35),
                  ),
                ),
              Container(
                width: 20,
                height: 20,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: const Color(0xFF101426),
                  shape: BoxShape.circle,
                  border: Border.all(color: accent),
                ),
                child: Text(
                  isNow ? '▶' : '${index + 1}',
                  style: const TextStyle(color: Colors.white, fontSize: 8),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(6, 0, 8, 3),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      label,
                      style: TextStyle(
                        color: accent,
                        fontSize: 9,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 0.8,
                      ),
                    ),
                    const Spacer(),
                    Text(
                      '${DateFormat('HH:mm').format(program.start)} - ${DateFormat('HH:mm').format(program.end)}  •  $runtime min',
                      style: const TextStyle(
                        color: Colors.white38,
                        fontSize: 8,
                      ),
                    ),
                  ],
                ),
                Text(
                  program.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  program.description?.isNotEmpty == true
                      ? program.description!
                      : 'No programme description available',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Colors.white54, fontSize: 9),
                ),
                if (isNow) ...[
                  const SizedBox(height: 2),
                  _buildEpgProgressBar(program),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildEpgSection(
    String header,
    EpgProgramWindow program, {
    bool isNow = false,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              header,
              style: const TextStyle(
                color: Color(0xFF00B7FF),
                fontSize: 11,
                fontWeight: FontWeight.w900,
                letterSpacing: 1.2,
              ),
            ),
            const Spacer(),
            Text(
              '${DateFormat('HH:mm').format(program.start)} - ${DateFormat('HH:mm').format(program.end)}',
              style: const TextStyle(
                color: Colors.white38,
                fontSize: 11,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          program.title,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 18,
            fontWeight: FontWeight.w900,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        if (program.description != null && program.description!.isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(
            program.description!,
            style: const TextStyle(
              color: Colors.white60,
              fontSize: 13,
              height: 1.4,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ],
        if (isNow) ...[
          const SizedBox(height: 12),
          _buildEpgProgressBar(program),
        ],
      ],
    );
  }

  Widget _buildEpgProgressBar(EpgProgramWindow program) {
    final now = DateTime.now();
    final total = program.end.difference(program.start).inSeconds;
    final elapsed = now.difference(program.start).inSeconds;
    final progress = (elapsed / total).clamp(0.0, 1.0);

    return Container(
      height: 3,
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white10,
        borderRadius: BorderRadius.circular(2),
      ),
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft,
        widthFactor: progress,
        child: Container(
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFFC12CFF), Color(0xFF00B7FF)],
            ),
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ),
    );
  }

  IconData _getCategoryIcon(String categoryId) {
    if (categoryId == IptvRepository.virtualAll) return Icons.list_rounded;
    if (categoryId == IptvRepository.virtualFavorites) {
      return Icons.favorite_rounded;
    }
    if (categoryId == IptvRepository.virtualHistory) {
      return Icons.history_rounded;
    }
    return Icons.live_tv_rounded;
  }
}

class _CategoryItem extends StatefulWidget {
  final String label;
  final int count;
  final bool isSelected;
  final VoidCallback onTap;
  final IconData icon;

  const _CategoryItem({
    required this.label,
    required this.count,
    required this.isSelected,
    required this.onTap,
    required this.icon,
  });

  @override
  State<_CategoryItem> createState() => _CategoryItemState();
}

class _CategoryItemState extends State<_CategoryItem> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    final active = _isFocused || widget.isSelected;
    final accent = Theme.of(context).colorScheme.primary;

    return FocusableActionDetector(
      onFocusChange: (v) => setState(() => _isFocused = v),
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
      child: AnimatedScale(
        scale: _isFocused ? perfScale(1.02) : 1.0,
        duration: perfDuration(const Duration(milliseconds: 200)),
        child: InkWell(
          onTap: widget.onTap,
          borderRadius: BorderRadius.circular(12),
          child: AnimatedContainer(
            duration: perfDuration(const Duration(milliseconds: 200)),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            decoration: BoxDecoration(
              color: active ? null : Colors.transparent,
              gradient: active ? contentPanelGradientOf(context) : null,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: active ? accent : Colors.transparent,
                width: active ? 1.5 : 1,
              ),
              boxShadow: firestickPerformanceMode
                  ? []
                  : active
                  ? [
                      BoxShadow(
                        color: accent.withValues(alpha: 0.35),
                        blurRadius: 10,
                      ),
                    ]
                  : [],
            ),
            child: Row(
              children: [
                Icon(
                  widget.icon,
                  color: active ? accent : Colors.white70,
                  size: 18,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    widget.label,
                    style: TextStyle(
                      color: active ? Colors.white : Colors.white70,
                      fontWeight: active ? FontWeight.w900 : FontWeight.w600,
                      fontSize: 12,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Text(
                  '${widget.count}',
                  style: TextStyle(
                    color: active
                        ? Colors.white.withValues(alpha: 0.7)
                        : Colors.white24,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
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

class _LiveMoveRightIntent extends Intent {
  const _LiveMoveRightIntent();
}

class _ChannelItem extends StatefulWidget {
  final FocusNode focusNode;
  final ContentItem channel;
  final int index;
  final bool isFocused;
  final EpgProgramWindow? currentProgram;
  final bool showChannelNumbers;
  final bool showChannelIcons;
  final bool showChannelNames;
  final bool showCurrentProgram;
  final String rowSize;
  final VoidCallback onFocus;
  final VoidCallback? onMoveRight;
  final VoidCallback onTap;

  const _ChannelItem({
    super.key,
    required this.focusNode,
    required this.channel,
    required this.index,
    required this.isFocused,
    required this.currentProgram,
    required this.showChannelNumbers,
    required this.showChannelIcons,
    required this.showChannelNames,
    required this.showCurrentProgram,
    required this.rowSize,
    required this.onFocus,
    this.onMoveRight,
    required this.onTap,
  });

  @override
  State<_ChannelItem> createState() => _ChannelItemState();
}

class _ChannelItemState extends State<_ChannelItem> {
  bool _uiFocused = false;

  @override
  Widget build(BuildContext context) {
    final active = _uiFocused || widget.isFocused;
    final currentProgram = widget.currentProgram;
    final compact = widget.rowSize == 'compact';
    final large = widget.rowSize == 'large';
    final verticalPadding = compact ? 3.0 : (large ? 8.0 : 4.0);
    final iconWidth = compact ? 34.0 : (large ? 48.0 : 40.0);
    final iconHeight = compact ? 24.0 : (large ? 34.0 : 28.0);
    final titleSize = compact ? 12.0 : (large ? 15.0 : 13.0);
    final programSize = compact ? 9.0 : (large ? 11.0 : 10.0);
    final showProgram = widget.showCurrentProgram;
    final showName = widget.showChannelNames;
    final fallbackTitle = currentProgram?.title ?? widget.channel.name;

    return FocusableActionDetector(
      focusNode: widget.focusNode,
      onFocusChange: (v) {
        setState(() => _uiFocused = v);
        if (v) widget.onFocus();
      },
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.gameButtonA): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.arrowRight): _LiveMoveRightIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
        _LiveMoveRightIntent: CallbackAction<_LiveMoveRightIntent>(
          onInvoke: (_) {
            widget.onMoveRight?.call();
            return null;
          },
        ),
      },
      child: AnimatedScale(
        scale: _uiFocused ? perfScale(1.03) : 1.0,
        duration: perfDuration(const Duration(milliseconds: 200)),
        child: InkWell(
          onTap: widget.onTap,
          borderRadius: BorderRadius.circular(12),
          child: AnimatedContainer(
            duration: perfDuration(const Duration(milliseconds: 200)),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            constraints: BoxConstraints(
              minHeight: compact
                  ? 44
                  : large
                  ? 68
                  : 54,
            ),
            decoration: BoxDecoration(
              color: active
                  ? const Color(0xAA4A3D6A)
                  : Colors.white.withValues(alpha: 0.03),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: active ? const Color(0xFFC12CFF) : Colors.white10,
                width: active ? 2 : 1,
              ),
              boxShadow: firestickPerformanceMode
                  ? []
                  : active
                  ? [
                      BoxShadow(
                        color: const Color(0xFFC12CFF).withValues(alpha: 0.3),
                        blurRadius: 10,
                      ),
                    ]
                  : [],
            ),
            child: Row(
              children: [
                if (widget.showChannelNumbers) ...[
                  SizedBox(
                    width: 35,
                    child: Text(
                      widget.channel.liveStream?.streamId ?? '${widget.index}',
                      style: const TextStyle(
                        color: Colors.white24,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                if (widget.showChannelIcons) ...[
                  Container(
                    width: iconWidth,
                    height: iconHeight,
                    decoration: BoxDecoration(
                      color: Colors.black26,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: widget.channel.imagePath.isNotEmpty
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: Image.network(
                              widget.channel.imagePath,
                              fit: BoxFit.contain,
                              cacheWidth: firestickPerformanceMode ? 80 : null,
                              cacheHeight: firestickPerformanceMode ? 56 : null,
                              errorBuilder: (_, _, _) => const Icon(
                                Icons.live_tv,
                                size: 16,
                                color: Colors.white24,
                              ),
                            ),
                          )
                        : const Icon(
                            Icons.live_tv,
                            size: 16,
                            color: Colors.white24,
                          ),
                  ),
                  const SizedBox(width: 12),
                ],
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: verticalPadding),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          showName ? widget.channel.name : fallbackTitle,
                          style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: titleSize,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (showProgram && showName) ...[
                          const SizedBox(height: 1),
                          Text(
                            currentProgram?.title ?? 'No Programme Info',
                            style: TextStyle(
                              color: active ? Colors.white70 : Colors.white38,
                              fontSize: programSize,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                        if (showProgram && currentProgram != null) ...[
                          SizedBox(height: compact ? 2 : 3),
                          _buildProgressBar(currentProgram),
                        ],
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildProgressBar(EpgProgramWindow program) {
    final now = DateTime.now();
    final total = program.end.difference(program.start).inSeconds;
    final elapsed = now.difference(program.start).inSeconds;
    final progress = (elapsed / total).clamp(0.0, 1.0);

    return Container(
      height: 2,
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white10,
        borderRadius: BorderRadius.circular(1),
      ),
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft,
        widthFactor: progress,
        child: Container(
          decoration: BoxDecoration(
            color: const Color(0xFFC12CFF),
            borderRadius: BorderRadius.circular(1),
          ),
        ),
      ),
    );
  }
}
