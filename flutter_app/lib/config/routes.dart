import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../screens/home_screen.dart';
import '../screens/login_screen.dart';
import '../screens/notifications_screen.dart';
import '../screens/profile_screen.dart';
import '../screens/report_detail_screen.dart';
import '../screens/report_form_screen.dart';
import '../screens/report_timeline_screen.dart';
import '../screens/sighting_detail_screen.dart';
import '../screens/splash_screen.dart';
import 'constants.dart';
import 'theme.dart';

/// Notifier adapter converting Riverpod state changes into a Listenable for GoRouter
class RouterNotifier extends ChangeNotifier {
  final Ref _ref;

  RouterNotifier(this._ref) {
    _ref.listen<AuthState>(
      authProvider,
      (_, __) => notifyListeners(),
    );
  }
}

final routerNotifierProvider = Provider<RouterNotifier>((ref) {
  return RouterNotifier(ref);
});

/// Declarative GoRouter Provider with reactive authentication state redirection
final routerProvider = Provider<GoRouter>((ref) {
  final notifier = ref.watch(routerNotifierProvider);

  return GoRouter(
    initialLocation: AppRoutes.splash,
    refreshListenable: notifier,
    debugLogDiagnostics: false,
    redirect: (context, state) {
      final authState = ref.read(authProvider);
      final isSplash = state.matchedLocation == AppRoutes.splash;
      final isLogin = state.matchedLocation == AppRoutes.login;

      // During initial startup, allow splash screen to render
      if (authState.isInitial || (authState.isLoading && isSplash)) {
        return null;
      }

      final isAuthenticated = authState.isAuthenticated;

      // If not logged in and attempting to access protected screens
      if (!isAuthenticated && !isLogin && !isSplash) {
        return AppRoutes.login;
      }

      // If logged in and on splash or login screen, route to dashboard
      if (isAuthenticated && (isLogin || isSplash)) {
        return AppRoutes.home;
      }

      return null;
    },
    routes: [
      GoRoute(
        path: AppRoutes.splash,
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: AppRoutes.login,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: AppRoutes.home,
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: AppRoutes.profile,
        builder: (context, state) => const ProfileScreen(),
      ),
      GoRoute(
        path: AppRoutes.newReport,
        builder: (context, state) => const ReportFormScreen(),
      ),
      GoRoute(
        path: AppRoutes.reportDetail,
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return ReportDetailScreen(reportId: id);
        },
      ),
      GoRoute(
        path: AppRoutes.notifications,
        builder: (context, state) => const NotificationsScreen(),
      ),
      GoRoute(
        path: AppRoutes.reportTimeline,
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return ReportTimelineScreen(reportId: id);
        },
      ),
      GoRoute(
        path: AppRoutes.sightingDetail,
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return SightingDetailScreen(sightingId: id);
        },
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      backgroundColor: AppTheme.darkBackground,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64, color: AppTheme.errorRed),
            const SizedBox(height: 16),
            const Text(
              '404 — Page Not Found',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: AppTheme.darkTextPrimary,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              state.error?.message ?? 'The requested page does not exist.',
              style: const TextStyle(color: AppTheme.darkTextSecondary),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => context.go(AppRoutes.home),
              child: const Text('Return Home'),
            ),
          ],
        ),
      ),
    ),
  );
});
