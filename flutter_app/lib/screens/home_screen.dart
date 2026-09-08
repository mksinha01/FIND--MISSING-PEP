import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../providers/auth_provider.dart';
import '../providers/notifications_provider.dart';
import '../providers/reports_provider.dart';
import 'my_reports_screen.dart';
import 'notifications_screen.dart';
import 'profile_screen.dart';

/// Main Application Scaffold Shell with 4-Tab Navigation
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(currentUserProvider);
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';
    final unreadCount = ref.watch(unreadNotificationsCountProvider);

    final screens = [
      _DashboardView(user: user, isHindi: isHindi),
      const MyReportsScreen(),
      const NotificationsScreen(),
      const ProfileScreen(),
    ];

    return Scaffold(
      body: screens[_currentIndex],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (idx) {
          setState(() => _currentIndex = idx);
        },
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.home_outlined),
            selectedIcon: const Icon(Icons.home),
            label: isHindi ? 'होम' : 'Home',
          ),
          NavigationDestination(
            icon: const Icon(Icons.assignment_outlined),
            selectedIcon: const Icon(Icons.assignment),
            label: isHindi ? 'रिपोर्ट्स' : 'Reports',
          ),
          NavigationDestination(
            icon: unreadCount > 0
                ? Badge(
                    label: Text('$unreadCount'),
                    child: const Icon(Icons.notifications_outlined),
                  )
                : const Icon(Icons.notifications_outlined),
            selectedIcon: unreadCount > 0
                ? Badge(
                    label: Text('$unreadCount'),
                    child: const Icon(Icons.notifications),
                  )
                : const Icon(Icons.notifications),
            label: isHindi ? 'अलर्ट्स' : 'Alerts',
          ),
          NavigationDestination(
            icon: const Icon(Icons.person_outline),
            selectedIcon: const Icon(Icons.person),
            label: isHindi ? 'प्रोफ़ाइल' : 'Profile',
          ),
        ],
      ),
    );
  }
}

/// Dashboard Overview Tab
class _DashboardView extends ConsumerWidget {
  final dynamic user;
  final bool isHindi;

  const _DashboardView({required this.user, required this.isHindi});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final stats = ref.watch(reportStatsProvider);
    final activeCasesCount = stats['active'] ?? 0;
    final foundCasesCount = stats['found'] ?? 0;
    final totalCasesCount = stats['total'] ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: Text(isHindi ? 'डैशबोर्ड' : 'Dashboard'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_circle_outline),
            tooltip: isHindi ? 'नई रिपोर्ट दर्ज करें' : 'New Report',
            onPressed: () => context.push(AppRoutes.newReport),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Welcome Card
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [AppTheme.primaryBlue, Color(0xFF0369A1)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: AppTheme.primaryBlue.withOpacity(0.3),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.security, color: Colors.white, size: 28),
                      const SizedBox(width: 10),
                      Text(
                        isHindi ? 'सक्रिय एआई निगरानी' : 'Active AI Monitoring',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    isHindi
                        ? 'नमस्ते, ${user?.name ?? 'यूज़र'}! सिस्टम सक्रिय रिपोर्टों के लिए सीसीटीवी फुटेज स्कैन कर रहा है।'
                        : 'Welcome back, ${user?.name ?? 'User'}! Edge AI nodes are actively scanning connected CCTV streams.',
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 14,
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Quick Stats Row
            Row(
              children: [
                _buildStatCard(
                  title: isHindi ? 'सक्रिय खोजें' : 'Active Cases',
                  value: activeCasesCount.toString(),
                  icon: Icons.person_search,
                  color: AppTheme.primaryLight,
                ),
                const SizedBox(width: 12),
                _buildStatCard(
                  title: isHindi ? 'कुल रिपोर्टें' : 'Total Reports',
                  value: totalCasesCount.toString(),
                  icon: Icons.assignment_outlined,
                  color: AppTheme.secondaryCyan,
                ),
                const SizedBox(width: 12),
                _buildStatCard(
                  title: isHindi ? 'मिल गए' : 'Found Cases',
                  value: foundCasesCount.toString(),
                  icon: Icons.verified_outlined,
                  color: AppTheme.successGreen,
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Quick Action Buttons
            Text(
              (isHindi ? 'त्वरित कार्य' : 'Quick Actions').toUpperCase(),
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.0,
                color: AppTheme.darkTextSecondary,
              ),
            ),
            const SizedBox(height: 12),

            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                minimumSize: const Size(double.infinity, 50),
                backgroundColor: AppTheme.primaryBlue,
              ),
              icon: const Icon(Icons.add),
              label: Text(
                isHindi ? 'लापता व्यक्ति रिपोर्ट दर्ज करें' : 'Submit Missing Person Report',
                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
              ),
              onPressed: () => context.push(AppRoutes.newReport),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatCard({
    required String title,
    required String value,
    required IconData icon,
    required Color color,
  }) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
        decoration: BoxDecoration(
          color: AppTheme.darkCard,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppTheme.darkCardBorder),
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 8),
            Text(
              value,
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: AppTheme.darkTextPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 11,
                color: AppTheme.darkTextSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

