import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../providers/auth_provider.dart';

/// User Profile, Preferences & System Status Screen
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  void _showLogoutDialog(BuildContext context, WidgetRef ref) {
    final isHindi = ref.read(currentLanguageProvider) == 'hi';

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(isHindi ? 'लॉग आउट' : 'Sign Out'),
        content: Text(
          isHindi
              ? 'क्या आप वाकई अपने खाते से लॉग आउट करना चाहते हैं?'
              : 'Are you sure you want to sign out of your account?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(isHindi ? 'रद्द करें' : 'Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.errorRed,
            ),
            onPressed: () async {
              Navigator.pop(ctx);
              await ref.read(authProvider.notifier).signOut();
              if (context.mounted) {
                context.go(AppRoutes.login);
              }
            },
            child: Text(isHindi ? 'लॉग आउट करें' : 'Sign Out'),
          ),
        ],
      ),
    );
  }

  void _showEditProfileDialog(BuildContext context, WidgetRef ref) {
    final user = ref.read(currentUserProvider);
    if (user == null) return;
    final isHindi = ref.read(currentLanguageProvider) == 'hi';

    final nameController = TextEditingController(text: user.name);
    final phoneController = TextEditingController(text: user.phone ?? '');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(isHindi ? 'प्रोफ़ाइल संपादित करें' : 'Edit Profile'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameController,
                decoration: InputDecoration(
                  labelText: isHindi ? 'पूरा नाम' : 'Full Name',
                  prefixIcon: const Icon(Icons.person_outline),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: phoneController,
                keyboardType: TextInputType.phone,
                decoration: InputDecoration(
                  labelText: isHindi ? 'फ़ोन नंबर' : 'Phone Number',
                  prefixIcon: const Icon(Icons.phone_outlined),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(isHindi ? 'रद्द करें' : 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              final newName = nameController.text.trim();
              final newPhone = phoneController.text.trim();
              Navigator.pop(ctx);

              final success = await ref.read(authProvider.notifier).updateProfile(
                name: newName.isNotEmpty ? newName : null,
                phone: newPhone.isNotEmpty ? newPhone : null,
              );

              if (context.mounted && success) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      isHindi ? 'प्रोफ़ाइल सफलतापूर्वक अपडेट की गई' : 'Profile updated successfully',
                    ),
                    backgroundColor: AppTheme.successGreen,
                  ),
                );
              }
            },
            child: Text(isHindi ? 'सहेजें' : 'Save Changes'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final currentLang = ref.watch(currentLanguageProvider);
    final themeMode = ref.watch(themeModeProvider);
    final isHindi = currentLang == 'hi';

    return Scaffold(
      appBar: AppBar(
        title: Text(isHindi ? 'प्रोफ़ाइल और सेटिंग्स' : 'Profile & Settings'),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit_outlined),
            tooltip: isHindi ? 'संपादित करें' : 'Edit',
            onPressed: () => _showEditProfileDialog(context, ref),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        child: Column(
          children: [
            // User Header Card
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppTheme.darkCard,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppTheme.darkCardBorder),
              ),
              child: Row(
                children: [
                  // Avatar
                  Container(
                    width: 64,
                    height: 64,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: const LinearGradient(
                        colors: [AppTheme.primaryBlue, AppTheme.secondaryCyan],
                      ),
                      border: Border.all(color: AppTheme.primaryLight, width: 2),
                    ),
                    child: Center(
                      child: Text(
                        user?.initials ?? 'U',
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),

                  // Info
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user?.name ?? 'Anonymous User',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: AppTheme.darkTextPrimary,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          user?.email ?? 'No email associated',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppTheme.darkTextSecondary,
                          ),
                        ),
                        if (user?.phone != null && user!.phone!.isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(
                            user.phone!,
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppTheme.primaryLight,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Preferences Section
            _buildSectionHeader(isHindi ? 'प्राथमिकताएं' : 'Preferences'),
            const SizedBox(height: 8),

            // Language Selector Tile
            Container(
              decoration: BoxDecoration(
                color: AppTheme.darkSurface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppTheme.darkCardBorder),
              ),
              child: ListTile(
                leading: const Icon(Icons.language, color: AppTheme.primaryLight),
                title: Text(isHindi ? 'भाषा (Language)' : 'Language'),
                subtitle: Text(isHindi ? 'हिंदी' : 'English'),
                trailing: DropdownButton<String>(
                  value: currentLang,
                  dropdownColor: AppTheme.darkCard,
                  underline: const SizedBox(),
                  items: const [
                    DropdownMenuItem(value: 'en', child: Text('English')),
                    DropdownMenuItem(value: 'hi', child: Text('हिंदी (Hindi)')),
                  ],
                  onChanged: (val) {
                    if (val != null) {
                      ref.read(authProvider.notifier).changeLanguage(val);
                    }
                  },
                ),
              ),
            ),
            const SizedBox(height: 12),

            // Theme Mode Selector Tile
            Container(
              decoration: BoxDecoration(
                color: AppTheme.darkSurface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppTheme.darkCardBorder),
              ),
              child: ListTile(
                leading: Icon(
                  themeMode == ThemeMode.dark ? Icons.dark_mode : Icons.light_mode,
                  color: AppTheme.secondaryCyan,
                ),
                title: Text(isHindi ? 'थीम मोड' : 'Theme Mode'),
                subtitle: Text(themeMode == ThemeMode.dark ? 'Dark Mode' : 'Light Mode'),
                trailing: Switch(
                  value: themeMode == ThemeMode.dark,
                  activeColor: AppTheme.primaryLight,
                  onChanged: (val) {
                    ref.read(themeModeProvider.notifier).state =
                        val ? ThemeMode.dark : ThemeMode.light;
                  },
                ),
              ),
            ),
            const SizedBox(height: 24),

            // System Information Section
            _buildSectionHeader(isHindi ? 'सिस्टम स्थिति' : 'System & Connection'),
            const SizedBox(height: 8),

            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.darkSurface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppTheme.darkCardBorder),
              ),
              child: Column(
                children: [
                  _buildInfoRow('Backend API', AppConstants.apiBaseUrl),
                  const Divider(height: 16),
                  _buildInfoRow('Auth Engine', 'Firebase Auth + JWT'),
                  const Divider(height: 16),
                  _buildInfoRow('Map Engine', 'FlutterMap (OpenStreetMap)'),
                  const Divider(height: 16),
                  _buildInfoRow('App Version', 'v1.0.0 (Build 10)'),
                ],
              ),
            ),
            const SizedBox(height: 32),

            // Sign Out Button
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppTheme.errorRed,
                  side: const BorderSide(color: AppTheme.errorRed),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                icon: const Icon(Icons.logout, size: 20),
                label: Text(
                  isHindi ? 'लॉग आउट करें' : 'Sign Out',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                onPressed: () => _showLogoutDialog(context, ref),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.0,
          color: AppTheme.darkTextSecondary,
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 13, color: AppTheme.darkTextSecondary),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.right,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: AppTheme.darkTextPrimary,
            ),
          ),
        ),
      ],
    );
  }
}
