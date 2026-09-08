import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../providers/auth_provider.dart';

/// Authentication Screen supporting Email/Password and Google OAuth Sign-In
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Form Controllers - Login
  final _loginFormKey = GlobalKey<FormState>();
  final _loginEmailController = TextEditingController();
  final _loginPasswordController = TextEditingController();
  bool _loginObscurePassword = true;

  // Form Controllers - Register
  final _registerFormKey = GlobalKey<FormState>();
  final _registerNameController = TextEditingController();
  final _registerEmailController = TextEditingController();
  final _registerPasswordController = TextEditingController();
  final _registerConfirmPasswordController = TextEditingController();
  bool _registerObscurePassword = true;
  bool _registerObscureConfirm = true;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _loginEmailController.dispose();
    _loginPasswordController.dispose();
    _registerNameController.dispose();
    _registerEmailController.dispose();
    _registerPasswordController.dispose();
    _registerConfirmPasswordController.dispose();
    super.dispose();
  }

  void _handleLogin() async {
    if (!_loginFormKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();

    await ref.read(authProvider.notifier).loginWithEmail(
      _loginEmailController.text.trim(),
      _loginPasswordController.text,
    );
  }

  void _handleRegister() async {
    if (!_registerFormKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();

    await ref.read(authProvider.notifier).registerWithEmail(
      _registerEmailController.text.trim(),
      _registerPasswordController.text,
      _registerNameController.text.trim(),
    );
  }

  void _handleGoogleSignIn() async {
    FocusScope.of(context).unfocus();
    await ref.read(authProvider.notifier).loginWithGoogle();
  }

  void _showForgotPasswordDialog() {
    final emailController = TextEditingController(text: _loginEmailController.text);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reset Password'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Enter your registered email address to receive password reset instructions.',
              style: TextStyle(fontSize: 14, color: AppTheme.darkTextSecondary),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: emailController,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(
                labelText: 'Email Address',
                prefixIcon: Icon(Icons.email_outlined),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              final email = emailController.text.trim();
              if (email.isNotEmpty) {
                Navigator.pop(ctx);
                try {
                  await ref.read(authServiceProvider).sendPasswordResetEmail(email);
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Password reset link sent! Check your inbox.'),
                        backgroundColor: AppTheme.successGreen,
                      ),
                    );
                  }
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('Error: ${e.toString()}'),
                        backgroundColor: AppTheme.errorRed,
                      ),
                    );
                  }
                }
              }
            },
            child: const Text('Send Reset Link'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';

    return Scaffold(
      backgroundColor: AppTheme.darkBackground,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          // Language Switcher Menu
          Padding(
            padding: const EdgeInsets.only(right: 16),
            child: PopupMenuButton<String>(
              initialValue: currentLang,
              icon: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.language, size: 20, color: AppTheme.primaryLight),
                  const SizedBox(width: 4),
                  Text(
                    isHindi ? 'हिंदी' : 'EN',
                    style: const TextStyle(
                      color: AppTheme.primaryLight,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
              onSelected: (val) {
                ref.read(authProvider.notifier).changeLanguage(val);
              },
              itemBuilder: (context) => [
                const PopupMenuItem(
                  value: 'en',
                  child: Text('English'),
                ),
                const PopupMenuItem(
                  value: 'hi',
                  child: Text('हिंदी (Hindi)'),
                ),
              ],
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Header Logo & Branding
              Center(
                child: Container(
                  width: 68,
                  height: 68,
                  decoration: BoxDecoration(
                    color: AppTheme.primaryBlue.withOpacity(0.15),
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: AppTheme.primaryLight.withOpacity(0.5),
                      width: 1.5,
                    ),
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.person_search_rounded,
                      size: 34,
                      color: AppTheme.primaryLight,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              Text(
                isHindi ? 'लापता व्यक्ति ट्रैकिंग' : 'FIND-MISSING-PEP',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                  color: AppTheme.darkTextPrimary,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                isHindi
                    ? 'रीयल-टाइम एआई आधारित सीसीटीवी निगरानी प्रणाली'
                    : 'Real-Time Edge AI Sighting & Monitoring',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 13,
                  color: AppTheme.darkTextSecondary,
                ),
              ),
              const SizedBox(height: 28),

              // Error Banner
              if (authState.errorMessage != null) ...[
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.errorRed.withOpacity(0.4)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline, color: AppTheme.errorRed, size: 20),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          authState.errorMessage!,
                          style: const TextStyle(
                            color: AppTheme.errorRed,
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, size: 18, color: AppTheme.errorRed),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        onPressed: () => ref.read(authProvider.notifier).clearError(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // Tab Bar (Sign In / Register)
              Container(
                decoration: BoxDecoration(
                  color: AppTheme.darkSurface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppTheme.darkCardBorder),
                ),
                child: TabBar(
                  controller: _tabController,
                  indicatorSize: TabBarIndicatorSize.tab,
                  indicator: BoxDecoration(
                    color: AppTheme.primaryBlue,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  labelColor: Colors.white,
                  unselectedLabelColor: AppTheme.darkTextSecondary,
                  labelStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  tabs: [
                    Tab(text: isHindi ? 'साइन इन' : 'Sign In'),
                    Tab(text: isHindi ? 'नया खाता' : 'Create Account'),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Tab Views
              SizedBox(
                height: 380,
                child: TabBarView(
                  controller: _tabController,
                  children: [
                    _buildLoginForm(isHindi, authState.isLoading),
                    _buildRegisterForm(isHindi, authState.isLoading),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // Divider "OR"
              Row(
                children: [
                  const Expanded(child: Divider(color: AppTheme.darkCardBorder)),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(
                      isHindi ? 'या' : 'OR',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppTheme.darkTextSecondary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  const Expanded(child: Divider(color: AppTheme.darkCardBorder)),
                ],
              ),
              const SizedBox(height: 16),

              // Google Sign-In Button
              OutlinedButton.icon(
                onPressed: authState.isLoading ? null : _handleGoogleSignIn,
                style: OutlinedButton.styleFrom(
                  backgroundColor: AppTheme.darkSurface,
                  side: const BorderSide(color: AppTheme.darkCardBorder),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                icon: const Icon(
                  Icons.g_mobiledata_rounded,
                  size: 28,
                  color: Colors.white,
                ),
                label: Text(
                  isHindi ? 'गूगल के साथ जारी रखें' : 'Continue with Google',
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.darkTextPrimary,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLoginForm(bool isHindi, bool isLoading) {
    return Form(
      key: _loginFormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Email
          TextFormField(
            controller: _loginEmailController,
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.next,
            decoration: InputDecoration(
              labelText: isHindi ? 'ईमेल पता' : 'Email Address',
              prefixIcon: const Icon(Icons.email_outlined),
            ),
            validator: (value) {
              if (value == null || value.trim().isEmpty) {
                return isHindi ? 'ईमेल अनिवार्य है' : 'Email is required';
              }
              if (!RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$').hasMatch(value.trim())) {
                return isHindi ? 'अमान्य ईमेल पता' : 'Enter a valid email address';
              }
              return null;
            },
          ),
          const SizedBox(height: 14),

          // Password
          TextFormField(
            controller: _loginPasswordController,
            obscureText: _loginObscurePassword,
            textInputAction: TextInputAction.done,
            onFieldSubmitted: (_) => _handleLogin(),
            decoration: InputDecoration(
              labelText: isHindi ? 'पासवर्ड' : 'Password',
              prefixIcon: const Icon(Icons.lock_outline),
              suffixIcon: IconButton(
                icon: Icon(
                  _loginObscurePassword ? Icons.visibility_off : Icons.visibility,
                  size: 20,
                  color: AppTheme.darkTextSecondary,
                ),
                onPressed: () {
                  setState(() => _loginObscurePassword = !_loginObscurePassword);
                },
              ),
            ),
            validator: (value) {
              if (value == null || value.isEmpty) {
                return isHindi ? 'पासवर्ड अनिवार्य है' : 'Password is required';
              }
              return null;
            },
          ),
          const SizedBox(height: 6),

          // Forgot Password
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: _showForgotPasswordDialog,
              style: TextButton.styleFrom(
                padding: EdgeInsets.zero,
                visualDensity: VisualDensity.compact,
              ),
              child: Text(
                isHindi ? 'पासवर्ड भूल गए?' : 'Forgot Password?',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppTheme.primaryLight,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Submit Button
          ElevatedButton(
            onPressed: isLoading ? null : _handleLogin,
            child: isLoading
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  )
                : Text(isHindi ? 'साइन इन करें' : 'Sign In'),
          ),
        ],
      ),
    );
  }

  Widget _buildRegisterForm(bool isHindi, bool isLoading) {
    return Form(
      key: _registerFormKey,
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Full Name
            TextFormField(
              controller: _registerNameController,
              keyboardType: TextInputType.name,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: isHindi ? 'पूरा नाम' : 'Full Name',
                prefixIcon: const Icon(Icons.person_outline),
              ),
              validator: (value) {
                if (value == null || value.trim().isEmpty) {
                  return isHindi ? 'नाम अनिवार्य है' : 'Name is required';
                }
                return null;
              },
            ),
            const SizedBox(height: 12),

            // Email
            TextFormField(
              controller: _registerEmailController,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: isHindi ? 'ईमेल पता' : 'Email Address',
                prefixIcon: const Icon(Icons.email_outlined),
              ),
              validator: (value) {
                if (value == null || value.trim().isEmpty) {
                  return isHindi ? 'ईमेल अनिवार्य है' : 'Email is required';
                }
                if (!RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$').hasMatch(value.trim())) {
                  return isHindi ? 'अमान्य ईमेल पता' : 'Enter a valid email address';
                }
                return null;
              },
            ),
            const SizedBox(height: 12),

            // Password
            TextFormField(
              controller: _registerPasswordController,
              obscureText: _registerObscurePassword,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: isHindi ? 'पासवर्ड (कम से कम 6 अक्षर)' : 'Password (min 6 characters)',
                prefixIcon: const Icon(Icons.lock_outline),
                suffixIcon: IconButton(
                  icon: Icon(
                    _registerObscurePassword ? Icons.visibility_off : Icons.visibility,
                    size: 20,
                    color: AppTheme.darkTextSecondary,
                  ),
                  onPressed: () {
                    setState(() => _registerObscurePassword = !_registerObscurePassword);
                  },
                ),
              ),
              validator: (value) {
                if (value == null || value.length < 6) {
                  return isHindi
                      ? 'पासवर्ड कम से कम 6 अक्षरों का होना चाहिए'
                      : 'Password must be at least 6 characters';
                }
                return null;
              },
            ),
            const SizedBox(height: 12),

            // Confirm Password
            TextFormField(
              controller: _registerConfirmPasswordController,
              obscureText: _registerObscureConfirm,
              textInputAction: TextInputAction.done,
              onFieldSubmitted: (_) => _handleRegister(),
              decoration: InputDecoration(
                labelText: isHindi ? 'पासवर्ड की पुष्टि करें' : 'Confirm Password',
                prefixIcon: const Icon(Icons.lock_reset),
                suffixIcon: IconButton(
                  icon: Icon(
                    _registerObscureConfirm ? Icons.visibility_off : Icons.visibility,
                    size: 20,
                    color: AppTheme.darkTextSecondary,
                  ),
                  onPressed: () {
                    setState(() => _registerObscureConfirm = !_registerObscureConfirm);
                  },
                ),
              ),
              validator: (value) {
                if (value != _registerPasswordController.text) {
                  return isHindi ? 'पासवर्ड मेल नहीं खाते' : 'Passwords do not match';
                }
                return null;
              },
            ),
            const SizedBox(height: 16),

            // Register Submit Button
            ElevatedButton(
              onPressed: isLoading ? null : _handleRegister,
              child: isLoading
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      ),
                    )
                  : Text(isHindi ? 'खाता बनाएं' : 'Create Account'),
            ),
          ],
        ),
      ),
    );
  }
}
