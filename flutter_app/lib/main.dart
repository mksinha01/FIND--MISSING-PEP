import 'dart:developer' as developer;
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'config/routes.dart';
import 'config/theme.dart';
import 'providers/auth_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Firebase with fallback resilience
  try {
    await Firebase.initializeApp();
    developer.log('Firebase initialized successfully.', name: 'Bootstrap');
  } catch (e) {
    developer.log(
      'Firebase initialization skipped or running with mock credentials: $e',
      name: 'Bootstrap',
    );
  }

  runApp(
    const ProviderScope(
      child: FindMissingPersonApp(),
    ),
  );
}

/// Root Application Widget
class FindMissingPersonApp extends ConsumerWidget {
  const FindMissingPersonApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    final themeMode = ref.watch(themeModeProvider);
    final currentLang = ref.watch(currentLanguageProvider);

    return MaterialApp.router(
      title: 'FIND-MISSING-PEP',
      debugShowCheckedModeBanner: false,

      // Routing Configuration
      routerConfig: router,

      // Theme Configuration
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeMode,

      // Bilingual Localization Configuration (English & Hindi)
      locale: Locale(currentLang),
      supportedLocales: const [
        Locale('en', 'US'),
        Locale('hi', 'IN'),
      ],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
    );
  }
}
