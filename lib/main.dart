import 'dart:async';

import 'package:watchio/core/theme/theme_manager.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:watchio/services/service_locator.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';
import 'controllers/locale_provider.dart';
import 'controllers/playlist_controller.dart';
import 'controllers/branding_controller.dart';
import 'controllers/update_controller.dart';
import 'screens/app_initializer_screen.dart';
import 'services/cache_policy_service.dart';
import 'services/performance_service.dart';
import 'services/config_service.dart';
import 'services/input_mode_controller.dart';
import 'services/announcement_service.dart';
import 'shared/widgets/watchio_focus_action.dart';
import 'widgets/maintenance_banner.dart';
import 'widgets/update_startup_check.dart';
import 'l10n/app_localizations.dart';
import 'package:media_kit/media_kit.dart';
import 'l10n/supported_languages.dart';
import 'package:window_manager/window_manager.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  CachePolicyService.configureImageCache(tvMode: true);
  MediaKit.ensureInitialized();
  if (!kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.windows ||
          defaultTargetPlatform == TargetPlatform.linux ||
          defaultTargetPlatform == TargetPlatform.macOS)) {
    await windowManager.ensureInitialized();
  }

  // Watchio is a horizontal-first TV/video app across supported form factors.
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  await PerformanceService.track('startup_setup', setupServiceLocator);
  unawaited(CachePolicyService().cleanupTemporaryCache());

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => LocaleProvider()),
        ChangeNotifierProvider(create: (_) => PlaylistController()),
        ChangeNotifierProvider(create: (_) => ThemeManager()),
        ChangeNotifierProvider(create: (_) => BrandingController()..load()),
        ChangeNotifierProvider(create: (_) => ConfigService()..initialize()),
        ChangeNotifierProvider(
          create: (_) => AnnouncementService()..initialize(),
        ),
        ChangeNotifierProvider(create: (_) => UpdateController()..loadState()),
        ChangeNotifierProvider(create: (_) => InputModeController()..load()),
      ],
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    final localeProvider = Provider.of<LocaleProvider>(context);
    final themeManager = Provider.of<ThemeManager>(context);
    final config = context.watch<ConfigService>().config;

    return MaterialApp(
      locale: localeProvider.locale,
      supportedLocales: supportedLanguages
          .map((lang) => Locale(lang['code']))
          .toList(),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      title: config.branding.appName,
      theme: themeManager.currentThemeData,
      themeMode: ThemeMode.dark,
      builder: (context, child) {
        final inputMode = context.watch<InputModeController>();
        CachePolicyService.configureImageCache(
          tvMode: !inputMode.isLoaded || inputMode.isTvMode,
        );
        Widget content = FocusTraversalGroup(
          policy: ReadingOrderTraversalPolicy(),
          child: child ?? const SizedBox.shrink(),
        );

        if (!inputMode.allowPointerInput) {
          content = AbsorbPointer(absorbing: true, child: content);
        }

        return Shortcuts(
          shortcuts: const {
            ...WatchioFocusAction.activationShortcuts,
            ...WatchioFocusAction.dismissShortcuts,
          },
          child: Actions(
            actions: {
              DismissIntent: CallbackAction<DismissIntent>(
                onInvoke: (_) {
                  final navigator = Navigator.maybeOf(context);
                  navigator?.maybePop();
                  return null;
                },
              ),
            },
            child: content,
          ),
        );
      },
      home: UpdateStartupCheck(
        child: MaintenanceBanner(child: const AppInitializerScreen()),
      ),
      debugShowCheckedModeBanner: false,
    );
  }
}
