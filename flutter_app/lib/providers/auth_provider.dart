import 'dart:async';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/user_model.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';

/// Authentication Status Lifecycle
enum AuthStatus {
  initial,
  loading,
  authenticated,
  unauthenticated,
  error,
}

/// Immutable Authentication State
class AuthState {
  final AuthStatus status;
  final UserModel? user;
  final User? firebaseUser;
  final String? errorMessage;
  final String language;

  const AuthState({
    this.status = AuthStatus.initial,
    this.user,
    this.firebaseUser,
    this.errorMessage,
    this.language = 'en',
  });

  bool get isAuthenticated => status == AuthStatus.authenticated && user != null;
  bool get isLoading => status == AuthStatus.loading;
  bool get isInitial => status == AuthStatus.initial;

  AuthState copyWith({
    AuthStatus? status,
    UserModel? user,
    User? firebaseUser,
    String? errorMessage,
    String? language,
    bool clearError = false,
  }) {
    return AuthState(
      status: status ?? this.status,
      user: user ?? this.user,
      firebaseUser: firebaseUser ?? this.firebaseUser,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      language: language ?? this.language,
    );
  }
}

/// Dependency Injection Providers
final authServiceProvider = Provider<IAuthService>((ref) {
  return AuthService();
});

final apiServiceProvider = Provider<ApiService>((ref) {
  final authService = ref.watch(authServiceProvider);
  return ApiService(authService: authService);
});

final themeModeProvider = StateProvider<ThemeMode>((ref) => ThemeMode.dark);

/// AuthState Notifier managing user sessions and backend synchronization
class AuthNotifier extends StateNotifier<AuthState> {
  final IAuthService _authService;
  final ApiService _apiService;
  StreamSubscription<User?>? _authSubscription;

  AuthNotifier({
    required IAuthService authService,
    required ApiService apiService,
  })  : _authService = authService,
        _apiService = apiService,
        super(const AuthState()) {
    _init();
  }

  void _init() {
    _authSubscription = _authService.authStateChanges.listen((firebaseUser) {
      _handleFirebaseAuthState(firebaseUser);
    });
  }

  Future<void> _handleFirebaseAuthState(User? firebaseUser) async {
    if (firebaseUser == null) {
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        user: null,
        firebaseUser: null,
        clearError: true,
      );
      return;
    }

    state = state.copyWith(
      status: AuthStatus.loading,
      firebaseUser: firebaseUser,
      clearError: true,
    );

    try {
      // Fetch user profile from backend
      UserModel userProfile;
      try {
        userProfile = await _apiService.getProfile();
      } catch (e) {
        // If profile doesn't exist yet on backend, auto-provision
        final createReq = UserCreateRequest(
          firebaseUid: firebaseUser.uid,
          name: firebaseUser.displayName ?? firebaseUser.email?.split('@').first ?? 'User',
          email: firebaseUser.email,
          phone: firebaseUser.phoneNumber,
          avatarUrl: firebaseUser.photoURL,
          language: state.language,
        );
        userProfile = await _apiService.registerOrSyncUser(createReq);
      }

      state = state.copyWith(
        status: AuthStatus.authenticated,
        user: userProfile,
        firebaseUser: firebaseUser,
        language: userProfile.language,
      );
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        errorMessage: 'Failed to synchronize user session with server: $e',
      );
    }
  }

  /// Sign In with Email & Password
  Future<bool> loginWithEmail(String email, String password) async {
    state = state.copyWith(status: AuthStatus.loading, clearError: true);
    try {
      final credential = await _authService.signInWithEmailAndPassword(email, password);
      await _handleFirebaseAuthState(credential.user);
      return true;
    } on AuthException catch (e) {
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        errorMessage: e.message,
      );
      return false;
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        errorMessage: 'Login failed. Please check your credentials.',
      );
      return false;
    }
  }

  /// Sign Up with Email & Password
  Future<bool> registerWithEmail(String email, String password, String name) async {
    state = state.copyWith(status: AuthStatus.loading, clearError: true);
    try {
      final credential = await _authService.signUpWithEmailAndPassword(email, password, name);
      await _handleFirebaseAuthState(credential.user);
      return true;
    } on AuthException catch (e) {
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        errorMessage: e.message,
      );
      return false;
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        errorMessage: 'Registration failed. Please try again.',
      );
      return false;
    }
  }

  /// Sign In with Google OAuth
  Future<bool> loginWithGoogle() async {
    state = state.copyWith(status: AuthStatus.loading, clearError: true);
    try {
      final credential = await _authService.signInWithGoogle();
      await _handleFirebaseAuthState(credential.user);
      return true;
    } on AuthException catch (e) {
      // Don't show error if user deliberately cancelled
      if (e.code == 'SIGN_IN_CANCELED') {
        state = state.copyWith(
          status: AuthStatus.unauthenticated,
          clearError: true,
        );
        return false;
      }
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        errorMessage: e.message,
      );
      return false;
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        errorMessage: 'Google Sign-In failed. Please try again.',
      );
      return false;
    }
  }

  /// Sign Out current user
  Future<void> signOut() async {
    state = state.copyWith(status: AuthStatus.loading);
    try {
      await _authService.signOut();
    } catch (_) {}
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  /// Change active language (en/hi)
  Future<void> changeLanguage(String newLanguage) async {
    state = state.copyWith(language: newLanguage);
    if (state.user != null) {
      try {
        final updated = await _apiService.updateProfile(
          UserUpdateRequest(language: newLanguage),
        );
        state = state.copyWith(user: updated);
      } catch (_) {}
    }
  }

  /// Update user profile details
  Future<bool> updateProfile({String? name, String? phone}) async {
    if (state.user == null) return false;
    try {
      final updated = await _apiService.updateProfile(
        UserUpdateRequest(name: name, phone: phone),
      );
      state = state.copyWith(user: updated);
      return true;
    } catch (e) {
      state = state.copyWith(errorMessage: 'Failed to update profile: $e');
      return false;
    }
  }

  /// Clear any active error banner
  void clearError() {
    state = state.copyWith(clearError: true);
  }

  @override
  void dispose() {
    _authSubscription?.cancel();
    super.dispose();
  }
}

/// Global Auth Provider
final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final authService = ref.watch(authServiceProvider);
  final apiService = ref.watch(apiServiceProvider);
  return AuthNotifier(authService: authService, apiService: apiService);
});

/// Convenience Providers
final currentUserProvider = Provider<UserModel?>((ref) {
  return ref.watch(authProvider).user;
});

final isAuthenticatedProvider = Provider<bool>((ref) {
  return ref.watch(authProvider).isAuthenticated;
});

final currentLanguageProvider = Provider<String>((ref) {
  return ref.watch(authProvider).language;
});
