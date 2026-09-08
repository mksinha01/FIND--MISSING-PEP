import 'dart:async';
import 'package:dio/dio.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_app/config/constants.dart';
import 'package:flutter_app/models/enums.dart';
import 'package:flutter_app/models/user_model.dart';
import 'package:flutter_app/models/missing_person_model.dart';
import 'package:flutter_app/models/sighting_model.dart';
import 'package:flutter_app/models/notification_model.dart';
import 'package:flutter_app/services/api_service.dart';
import 'package:flutter_app/services/auth_service.dart';
import 'package:flutter_app/providers/auth_provider.dart';

/// Fake In-Memory Auth Service for Unit Testing
class FakeAuthService implements IAuthService {
  final _controller = StreamController<User?>.broadcast();
  User? _user;
  String? currentToken = 'initial_test_token_123';
  bool forceRefreshCalled = false;

  @override
  Stream<User?> get authStateChanges => _controller.stream;

  @override
  User? get currentUser => _user;

  @override
  Future<String?> getIdToken({bool forceRefresh = false}) async {
    if (forceRefresh) {
      forceRefreshCalled = true;
      currentToken = 'refreshed_test_token_456';
    }
    return currentToken;
  }

  @override
  Future<UserCredential> signInWithEmailAndPassword(String email, String password) async {
    if (password == 'wrongpassword') {
      throw AuthException('Invalid email or password.', 'wrong-password');
    }
    return _createMockCredential(email);
  }

  @override
  Future<UserCredential> signUpWithEmailAndPassword(
    String email,
    String password,
    String name,
  ) async {
    return _createMockCredential(email);
  }

  @override
  Future<UserCredential> signInWithGoogle() async {
    return _createMockCredential('googleuser@gmail.com');
  }

  @override
  Future<void> signOut() async {
    _user = null;
    currentToken = null;
    _controller.add(null);
  }

  @override
  Future<void> sendPasswordResetEmail(String email) async {}

  UserCredential _createMockCredential(String email) {
    // In unit test without binary Firebase bindings, simulate successful auth flow
    currentToken = 'valid_jwt_token_for_$email';
    return _MockUserCredential();
  }

  void emitUser(User? user) {
    _user = user;
    _controller.add(user);
  }

  void dispose() {
    _controller.close();
  }
}

class _MockUserCredential implements UserCredential {
  @override
  AdditionalUserInfo? get additionalUserInfo => null;
  @override
  AuthCredential? get credential => null;
  @override
  User? get user => null;
}

void main() {
  group('Story 10 — Domain Models Unit Tests', () {
    test('UserModel JSON Serialization & Deserialization', () {
      final json = {
        'id': 'u-123e4567-e89b-12d3-a456-426614174000',
        'firebase_uid': 'firebase_uid_test',
        'name': 'Aarav Sharma',
        'email': 'aarav@example.com',
        'phone': '+919876543210',
        'avatar_url': 'https://example.com/avatar.jpg',
        'language': 'hi',
        'created_at': '2026-09-06T12:00:00.000Z',
      };

      final user = UserModel.fromJson(json);

      expect(user.id, 'u-123e4567-e89b-12d3-a456-426614174000');
      expect(user.firebaseUid, 'firebase_uid_test');
      expect(user.name, 'Aarav Sharma');
      expect(user.email, 'aarav@example.com');
      expect(user.phone, '+919876543210');
      expect(user.language, 'hi');
      expect(user.initials, 'AS');

      final serialized = user.toJson();
      expect(serialized['firebase_uid'], 'firebase_uid_test');
      expect(serialized['name'], 'Aarav Sharma');
      expect(serialized['language'], 'hi');
    });

    test('MissingPersonModel & PhotoModel URL resolution & Status checks', () {
      final json = {
        'id': 'mp-100',
        'user_id': 'u-200',
        'full_name': 'Rohan Gupta',
        'age': 14,
        'gender': 'male',
        'height_cm': 160,
        'description': 'Wearing blue shirt and black pants',
        'last_seen_location': 'Sector 18 Metro Station',
        'status': 'ACTIVE',
        'photos': [
          {
            'id': 'p-1',
            'original_path': 'photos/rohan.jpg',
            'face_crop_path': 'crops/rohan_aligned.jpg',
            'is_primary': true,
            'processing_status': 'SUCCESS',
          }
        ]
      };

      final mp = MissingPersonModel.fromJson(json);
      expect(mp.fullName, 'Rohan Gupta');
      expect(mp.gender, Gender.male);
      expect(mp.status, ReportStatus.active);
      expect(mp.photos.length, 1);
      expect(mp.photos.first.originalUrl, '${AppConstants.uploadsBaseUrl}/photos/rohan.jpg');
      expect(mp.photos.first.faceCropUrl, '${AppConstants.uploadsBaseUrl}/crops/rohan_aligned.jpg');
      expect(mp.primaryPhotoUrl, '${AppConstants.uploadsBaseUrl}/photos/rohan.jpg');
    });

    test('SightingModel & PersonTimelineModel calculation', () {
      final json = {
        'id': 's-500',
        'person_id': 'mp-100',
        'agent_id': 'agent-01',
        'camera_id': 'cam-uuid-01',
        'similarity_score': 0.874,
        'confidence_level': 'CONFIRMED',
        'num_frames_matched': 3,
        'camera_location': 'Gate 2 Exit',
        'detected_at': '2026-09-06T14:30:00.000Z',
        'status': 'CONFIRMED',
      };

      final sighting = SightingModel.fromJson(json);
      expect(sighting.similarityScore, 0.874);
      expect(sighting.similarityPercentage, '87.4%');
      expect(sighting.confidenceLevel, ConfidenceLevel.confirmed);
      expect(sighting.status, SightingStatus.confirmed);
    });

    test('NotificationModel parsing', () {
      final json = {
        'id': 'n-999',
        'type': 'SIGHTING',
        'title': 'New Sighting Detected',
        'body': 'Match confidence 87.4% at Gate 2',
        'is_read': false,
        'created_at': '2026-09-06T14:35:00.000Z',
      };

      final notif = NotificationModel.fromJson(json);
      expect(notif.type, NotificationType.sighting);
      expect(notif.isRead, false);
      expect(notif.title, 'New Sighting Detected');
    });
  });

  group('Story 10 — Dio AuthInterceptor Tests', () {
    late Dio testDio;
    late FakeAuthService fakeAuthService;

    setUp(() {
      fakeAuthService = FakeAuthService();
      testDio = Dio();
    });

    tearDown(() {
      fakeAuthService.dispose();
      testDio.close();
    });

    test('AuthInterceptor injects Bearer token into request headers', () async {
      final interceptor = AuthInterceptor(
        authService: fakeAuthService,
        dio: testDio,
      );

      final options = RequestOptions(path: '/api/users/me');
      final handler = RequestInterceptorHandler();

      interceptor.onRequest(options, handler);

      expect(options.headers['Authorization'], 'Bearer initial_test_token_123');
    });

    test('AuthInterceptor handles 401 token refresh on error', () async {
      final dio = Dio();
      final interceptor = AuthInterceptor(
        authService: fakeAuthService,
        dio: dio,
      );

      // Simulate a 401 error response
      final requestOptions = RequestOptions(path: '/api/users/me');
      final dioException = DioException(
        requestOptions: requestOptions,
        response: Response(
          requestOptions: requestOptions,
          statusCode: 401,
        ),
        type: DioExceptionType.badResponse,
      );

      final errorHandler = ErrorInterceptorHandler();

      interceptor.onError(dioException, errorHandler);

      // Give async token refresh event loop time to run
      await Future.delayed(const Duration(milliseconds: 50));

      expect(fakeAuthService.forceRefreshCalled, true);
      expect(requestOptions.extra['token_refreshed'], true);
    });
  });

  group('Story 10 — Riverpod AuthState & Logic Tests', () {
    test('AuthState initial state and properties', () {
      const state = AuthState();
      expect(state.status, AuthStatus.initial);
      expect(state.isAuthenticated, false);
      expect(state.isLoading, false);
      expect(state.isInitial, true);
      expect(state.language, 'en');
    });

    test('AuthState copyWith transitions correctly', () {
      const state = AuthState();
      final user = UserModel(
        id: 'u-1',
        firebaseUid: 'fb-1',
        name: 'Test User',
        email: 'test@example.com',
      );

      final loggedInState = state.copyWith(
        status: AuthStatus.authenticated,
        user: user,
      );

      expect(loggedInState.status, AuthStatus.authenticated);
      expect(loggedInState.isAuthenticated, true);
      expect(loggedInState.user?.name, 'Test User');

      final loggedOutState = loggedInState.copyWith(
        status: AuthStatus.unauthenticated,
        user: null,
      );

      expect(loggedOutState.isAuthenticated, false);
      expect(loggedOutState.user, null);
    });
  });
}
