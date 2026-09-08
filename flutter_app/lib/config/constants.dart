/// Application Constants and Configuration Settings
class AppConstants {
  // API Configuration
  static const String defaultApiBaseUrl = 'http://localhost:8000/api';
  static const String defaultUploadsBaseUrl = 'http://localhost:8000/uploads';

  // Configurable via compile-time environment or runtime settings
  static String apiBaseUrl = const String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: defaultApiBaseUrl,
  );

  static String uploadsBaseUrl = const String.fromEnvironment(
    'UPLOADS_BASE_URL',
    defaultValue: defaultUploadsBaseUrl,
  );

  // Networking Timeouts
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 30);
  static const Duration sendTimeout = Duration(seconds: 30);

  // API Endpoints
  static const String endpointUsers = '/users';
  static const String endpointUsersMe = '/users/me';
  static const String endpointReports = '/reports';
  static const String endpointSightings = '/sightings';
  static const String endpointNotifications = '/notifications';
  static const String endpointNotificationsUnreadCount = '/notifications/unread-count';
  static const String endpointNotificationsReadAll = '/notifications/read-all';
  static const String endpointEventsStream = '/events/stream';

  // Storage Keys
  static const String keyAuthToken = 'auth_token';
  static const String keySelectedLanguage = 'selected_language';
  static const String keyThemeMode = 'theme_mode';
  static const String keyFcmToken = 'fcm_device_token';

  // AI & Detection Thresholds
  static const double candidateCutoffThreshold = 0.50;
  static const double matchAlertThreshold = 0.60;

  // Pagination Defaults
  static const int defaultPageSize = 20;

  // Supported Locales
  static const String langEn = 'en';
  static const String langHi = 'hi';
}

/// Declarative Route Path Definitions
class AppRoutes {
  static const String splash = '/';
  static const String login = '/login';
  static const String home = '/home';
  static const String reports = '/reports';
  static const String newReport = '/reports/new';
  static const String reportDetail = '/reports/:id';
  static const String reportTimeline = '/reports/:id/timeline';
  static const String notifications = '/notifications';
  static const String sightingDetail = '/sightings/:id';
  static const String profile = '/profile';

  static String reportDetailPath(String id) => '/reports/$id';
  static String reportTimelinePath(String id) => '/reports/$id/timeline';
  static String sightingDetailPath(String id) => '/sightings/$id';
}
