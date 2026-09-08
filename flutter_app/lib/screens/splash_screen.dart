import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../providers/auth_provider.dart';

/// Animated Splash Screen with AI Security Scanner Visuals
class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _scaleAnimation;
  late Animation<double> _opacityAnimation;

  @override
  void initState() {
    super.initState();

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat(reverse: true);

    _scaleAnimation = Tween<double>(begin: 0.92, end: 1.08).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    _opacityAnimation = Tween<double>(begin: 0.6, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    _checkInitialState();
  }

  void _checkInitialState() async {
    // Artificial minimum delay for smooth visual transition
    await Future.delayed(const Duration(milliseconds: 1400));
    if (!mounted) return;

    final authState = ref.read(authProvider);
    if (authState.status == AuthStatus.authenticated) {
      context.go(AppRoutes.home);
    } else if (authState.status == AuthStatus.unauthenticated ||
        authState.status == AuthStatus.error) {
      context.go(AppRoutes.login);
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Listen to state changes while on splash screen
    ref.listen<AuthState>(authProvider, (previous, next) {
      if (next.status == AuthStatus.authenticated) {
        context.go(AppRoutes.home);
      } else if (next.status == AuthStatus.unauthenticated) {
        context.go(AppRoutes.login);
      }
    });

    return Scaffold(
      backgroundColor: AppTheme.darkBackground,
      body: Stack(
        children: [
          // Background ambient gradient glow
          Positioned(
            top: -100,
            right: -100,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AppTheme.primaryBlue.withOpacity(0.15),
              ),
            ),
          ),
          Positioned(
            bottom: -100,
            left: -100,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AppTheme.secondaryCyan.withOpacity(0.12),
              ),
            ),
          ),

          // Central Visuals & Typography
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Animated Radar / Face Detection Beacon
                AnimatedBuilder(
                  animation: _pulseController,
                  builder: (context, child) {
                    return Transform.scale(
                      scale: _scaleAnimation.value,
                      child: Opacity(
                        opacity: _opacityAnimation.value,
                        child: Container(
                          width: 120,
                          height: 120,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            gradient: RadialGradient(
                              colors: [
                                AppTheme.primaryLight.withOpacity(0.35),
                                AppTheme.primaryBlue.withOpacity(0.1),
                                Colors.transparent,
                              ],
                              stops: const [0.3, 0.7, 1.0],
                            ),
                            border: Border.all(
                              color: AppTheme.primaryLight.withOpacity(0.6),
                              width: 2,
                            ),
                          ),
                          child: const Center(
                            child: Icon(
                              Icons.center_focus_strong_rounded,
                              size: 56,
                              color: AppTheme.primaryLight,
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 36),

                // System Brand Name
                const Text(
                  'FIND-MISSING-PEP',
                  style: TextStyle(
                    fontSize: 26,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 2.0,
                    color: AppTheme.darkTextPrimary,
                  ),
                ),
                const SizedBox(height: 8),

                // Subtitle
                const Text(
                  'AI Real-Time CCTV Sighting System',
                  style: TextStyle(
                    fontSize: 14,
                    color: AppTheme.darkTextSecondary,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 48),

                // Status Indicator
                SizedBox(
                  width: 200,
                  child: Column(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: const LinearProgressIndicator(
                          backgroundColor: AppTheme.darkCard,
                          valueColor: AlwaysStoppedAnimation<Color>(AppTheme.primaryLight),
                          minHeight: 3,
                        ),
                      ),
                      const SizedBox(height: 12),
                      const Text(
                        'Initializing secure session...',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppTheme.darkTextSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Bottom Version Tag
          const Positioned(
            bottom: 24,
            left: 0,
            right: 0,
            child: Text(
              'v1.0.0 • Edge AI & Cloud Sync',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 11,
                color: AppTheme.darkTextSecondary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
