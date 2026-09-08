import 'dart:developer' as developer;
import 'package:dio/dio.dart';
import '../config/constants.dart';
import '../models/user_model.dart';
import '../models/missing_person_model.dart';
import '../models/sighting_model.dart';
import '../models/notification_model.dart';
import 'auth_service.dart';

/// Custom Exception for Backend API errors
class ApiException implements Exception {
  final String message;
  final int? statusCode;
  final dynamic data;

  ApiException(this.message, {this.statusCode, this.data});

  @override
  String toString() => 'ApiException [$statusCode]: $message';
}

/// Dio Interceptor that automatically injects Firebase ID tokens and handles 401 token refreshes
class AuthInterceptor extends QueuedInterceptor {
  final IAuthService _authService;
  final Dio _dio;

  AuthInterceptor({
    required IAuthService authService,
    required Dio dio,
  })  : _authService = authService,
        _dio = dio;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    try {
      final token = await _authService.getIdToken();
      if (token != null && token.isNotEmpty) {
        options.headers['Authorization'] = 'Bearer $token';
      }
      return handler.next(options);
    } catch (e) {
      developer.log('AuthInterceptor onRequest error: $e', name: 'AuthInterceptor');
      return handler.next(options);
    }
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    // Check if 401 Unauthorized occurs and request has not already been retried
    if (err.response?.statusCode == 401 &&
        err.requestOptions.extra['token_refreshed'] != true) {
      developer.log(
        '401 Unauthorized encountered on ${err.requestOptions.path}. Forcing token refresh...',
        name: 'AuthInterceptor',
      );

      try {
        // Mark request as retried to prevent infinite retry loops
        err.requestOptions.extra['token_refreshed'] = true;

        // Force Firebase ID token refresh
        final freshToken = await _authService.getIdToken(forceRefresh: true);

        if (freshToken != null && freshToken.isNotEmpty) {
          developer.log('Token successfully refreshed. Retrying request...', name: 'AuthInterceptor');

          // Update Authorization header on original request
          err.requestOptions.headers['Authorization'] = 'Bearer $freshToken';

          // Re-dispatch original request with new token
          final response = await _dio.fetch(err.requestOptions);
          return handler.resolve(response);
        } else {
          developer.log('Failed to obtain fresh token (user signed out).', name: 'AuthInterceptor');
        }
      } catch (e) {
        developer.log('Token refresh retry failed: $e', name: 'AuthInterceptor');
      }
    }

    return handler.next(err);
  }
}

/// Centralized API Service for communicating with FastAPI backend
class ApiService {
  late final Dio dio;
  final IAuthService authService;

  ApiService({
    required this.authService,
    String? baseUrl,
    Dio? customDio,
  }) {
    if (customDio != null) {
      dio = customDio;
    } else {
      dio = Dio(
        BaseOptions(
          baseUrl: baseUrl ?? AppConstants.apiBaseUrl,
          connectTimeout: AppConstants.connectTimeout,
          receiveTimeout: AppConstants.receiveTimeout,
          sendTimeout: AppConstants.sendTimeout,
          headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
          },
          responseType: ResponseType.json,
        ),
      );

      // Attach token injection & auto-refresh interceptor
      dio.interceptors.add(
        AuthInterceptor(authService: authService, dio: dio),
      );

      // Logging interceptor for debugging
      dio.interceptors.add(
        LogInterceptor(
          request: true,
          requestHeader: false,
          requestBody: true,
          responseHeader: false,
          responseBody: true,
          error: true,
          logPrint: (obj) => developer.log(obj.toString(), name: 'DioClient'),
        ),
      );
    }
  }

  // ── USER PROFILE ENDPOINTS ──

  /// Register or sync user profile upon Firebase registration/login
  Future<UserModel> registerOrSyncUser(UserCreateRequest request) async {
    try {
      final response = await dio.post(
        AppConstants.endpointUsers,
        data: request.toJson(),
      );
      return UserModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Fetch currently authenticated user's profile
  Future<UserModel> getProfile() async {
    try {
      final response = await dio.get(AppConstants.endpointUsersMe);
      return UserModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Update user profile attributes
  Future<UserModel> updateProfile(UserUpdateRequest request) async {
    try {
      final response = await dio.put(
        AppConstants.endpointUsersMe,
        data: request.toJson(),
      );
      return UserModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  // ── MISSING PERSON REPORT ENDPOINTS ──

  /// Create a new missing person report atomically with multipart photos
  Future<MissingPersonModel> createReportAtomic(FormData formData) async {
    try {
      final response = await dio.post(
        AppConstants.endpointReports,
        data: formData,
        options: Options(
          contentType: 'multipart/form-data',
        ),
      );
      return MissingPersonModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// List reports filed by the authenticated user with optional status filter
  Future<MissingPersonListResponse> getMyReports({
    String? statusFilter,
    int page = 1,
    int limit = AppConstants.defaultPageSize,
  }) async {
    try {
      final queryParams = <String, dynamic>{
        'page': page,
        'limit': limit,
      };
      if (statusFilter != null && statusFilter.isNotEmpty && statusFilter != 'ALL') {
        queryParams['status'] = statusFilter;
      }

      final response = await dio.get(
        AppConstants.endpointReports,
        queryParameters: queryParams,
      );
      return MissingPersonListResponse.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Get comprehensive details of a specific report by ID
  Future<MissingPersonModel> getReportById(String reportId) async {
    try {
      final response = await dio.get('${AppConstants.endpointReports}/$reportId');
      return MissingPersonModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Update metadata for an existing report
  Future<MissingPersonModel> updateReport(
    String reportId,
    Map<String, dynamic> updateData,
  ) async {
    try {
      final response = await dio.put(
        '${AppConstants.endpointReports}/$reportId',
        data: updateData,
      );
      return MissingPersonModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Soft-delete / close a missing person report
  Future<void> closeReport(String reportId) async {
    try {
      await dio.delete('${AppConstants.endpointReports}/$reportId');
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Upload an additional photo to an existing report
  Future<PhotoModel> uploadAdditionalPhoto(
    String reportId,
    FormData formData,
  ) async {
    try {
      final response = await dio.post(
        '${AppConstants.endpointReports}/$reportId/photos',
        data: formData,
        options: Options(
          contentType: 'multipart/form-data',
        ),
      );
      return PhotoModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  // ── SIGHTINGS & TIMELINE ENDPOINTS ──

  /// List all sightings detected for a specific missing person report
  Future<List<SightingModel>> getSightingsForReport(String reportId) async {
    try {
      final response = await dio.get('${AppConstants.endpointReports}/$reportId/sightings');
      final list = response.data as List<dynamic>? ?? [];
      return list.map((e) => SightingModel.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Get chronological movement timeline with camera GPS breadcrumbs
  Future<PersonTimelineModel> getTimelineForReport(String reportId) async {
    try {
      final response = await dio.get('${AppConstants.endpointReports}/$reportId/timeline');
      return PersonTimelineModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Get single sighting details including evidence media paths
  Future<SightingModel> getSightingById(String sightingId) async {
    try {
      final response = await dio.get('${AppConstants.endpointSightings}/$sightingId');
      return SightingModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Confirm a sighting match (operator or user review)
  Future<SightingModel> confirmSighting(
    String sightingId, {
    String? reviewNotes,
  }) async {
    try {
      final response = await dio.put(
        '${AppConstants.endpointSightings}/$sightingId/confirm',
        data: reviewNotes != null ? {'review_notes': reviewNotes} : null,
      );
      return SightingModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Reject a false positive sighting (operator or user review)
  Future<SightingModel> rejectSighting(
    String sightingId, {
    String? reviewNotes,
  }) async {
    try {
      final response = await dio.put(
        '${AppConstants.endpointSightings}/$sightingId/reject',
        data: reviewNotes != null ? {'review_notes': reviewNotes} : null,
      );
      return SightingModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  // ── NOTIFICATIONS ENDPOINTS ──

  /// List user notifications with optional unread filter
  Future<NotificationListResponse> getNotifications({
    bool unreadOnly = false,
    int limit = 50,
  }) async {
    try {
      final response = await dio.get(
        AppConstants.endpointNotifications,
        queryParameters: {
          'unread_only': unreadOnly,
          'limit': limit,
        },
      );
      return NotificationListResponse.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Fetch unread notification count
  Future<int> getUnreadNotificationCount() async {
    try {
      final response = await dio.get(AppConstants.endpointNotificationsUnreadCount);
      final data = response.data as Map<String, dynamic>;
      return data['unread_count'] as int? ?? 0;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Mark single notification as read
  Future<NotificationModel> markNotificationRead(String notificationId) async {
    try {
      final response = await dio.put('${AppConstants.endpointNotifications}/$notificationId/read');
      return NotificationModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Mark all notifications as read for current user
  Future<int> markAllNotificationsRead() async {
    try {
      final response = await dio.put(AppConstants.endpointNotificationsReadAll);
      final data = response.data as Map<String, dynamic>;
      return data['updated_count'] as int? ?? 0;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Update FCM device token on user profile
  Future<UserModel> updateFcmToken(String fcmToken) async {
    try {
      return await updateProfile(UserUpdateRequest(fcmToken: fcmToken));
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  ApiException _handleDioError(DioException e) {
    String message = 'A network error occurred. Please check your connection.';
    int? statusCode = e.response?.statusCode;
    dynamic responseData = e.response?.data;

    if (e.response != null && e.response!.data is Map) {
      final data = e.response!.data as Map<String, dynamic>;
      if (data.containsKey('detail')) {
        final detail = data['detail'];
        message = detail is String ? detail : detail.toString();
      } else if (data.containsKey('message')) {
        message = data['message'].toString();
      }
    } else if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout) {
      message = 'Connection timed out. Please try again.';
    } else if (e.type == DioExceptionType.connectionError) {
      message = 'Unable to reach backend server. Please verify network settings.';
    }

    return ApiException(message, statusCode: statusCode, data: responseData);
  }
}
