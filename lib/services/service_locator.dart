import 'package:watchio/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:watchio/database/database.dart';
import 'package:watchio/utils/audio_handler.dart';
import 'package:media_kit/media_kit.dart';

GetIt getIt = GetIt.instance;

Future<void> setupServiceLocator() async {
  WidgetsFlutterBinding.ensureInitialized();

  getIt.registerSingleton<MyAudioHandler>(await initAudioService());
  getIt.registerSingleton<AppDatabase>(AppDatabase());

  MediaKit.ensureInitialized();
}

void setupLocator(BuildContext context) {
  getIt.registerSingleton<AppLocalizations>(AppLocalizations.of(context)!);
}
