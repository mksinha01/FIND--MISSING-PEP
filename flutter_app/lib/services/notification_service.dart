import 'dart:async';
import 'dart:convert';
import 'dart:developer' as developer;
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../config/constants.dart';
import 'api_service.dart';
import '../providers/auth_provider.dart';

/// Top-level background message handler for Firebase Cloud Messaging.
/// Must be annotated with @pragma('vm:entry-point') to prevent tree-shaking by the AOT compiler.
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  try {
    await Firebase.initializeApp();
  } catch (_) {
    // If Firebase is already initialized or running in test harness, continue
  }
  developer.log(
    'FCM Background message received: id=${message.messageId}, type=${message.data['type']}, sighting=${message.data['sighting_id']}',
    name: 'FCMBackground',
  );
}

/// Channel metadata for high-importance sighting alerts on Android
class NotificationChannelConfig {
  static const String id = 'find_missing_pep_alerts';
  static const String name = 'Sighting Alerts / साइटिंग अलर्ट्स';
  static const String description = 'High-priority notifications for CCTV missing person biometric matches';
}

/// Service managing FCM Push Notifications and Foreground Local Notifications
class NotificationService {
  final FirebaseMessaging _firebaseMessaging;
  final FlutterLocalNotificationsPlugin _localNotifications;
  final ApiService? _apiService;

  final StreamController<String> _onNotificationTapController =
      StreamController<String>.broadcast();

  /// Stream of target routes or sighting IDs when a notification is clicked
  Stream<String> get onNotificationTap => _onNotificationTapController.stream;

  bool _isInitialized = false;
  String? _fcmToken;

  NotificationService({
    FirebaseMessaging? firebaseMessaging,
    FlutterLocalNotificationsPlugin? localNotifications,
    ApiService? apiService,
  })  : _firebaseMessaging = firebaseMessaging ?? FirebaseMessaging.instance,
        _localNotifications = localNotifications ?? FlutterLocalNotificationsPlugin(),
        _apiService = apiService;

  String? get fcmToken => _fcmToken;
  bool get isInitialized => _isInitialized;

  /// Initialize push notifications, local notifications, and event listeners
  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      // 1. Request notification permissions on iOS & Android 13+
      final settings = await _firebaseMessaging.requestPermission(
        alert: true,
        announcement: false,
        badge: true,
        carPlay: false,
        criticalAlert: true,
        provisional: false,
        sound: true,
      );

      developer.log(
        'Notification permission status: ${settings.authorizationStatus}',
        name: 'NotificationService',
      );

      // 2. Initialize Flutter Local Notifications for foreground display
      const androidInitSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
      const darwinInitSettings = DarwinInitializationSettings(
        requestAlertPermission: true,
        requestBadgePermission: true,
        requestSoundPermission: true,
      );
      const initSettings = InitializationSettings(
        android: androidInitSettings,
        iOS: darwinInitSettings,
      );

      await _localNotifications.initialize(
        initSettings,
        onDidReceiveNotificationResponse: _onLocalNotificationTap,
      );

      // 3. Create High Importance Notification Channel for Android
      const androidChannel = AndroidNotificationChannel(
        NotificationChannelConfig.id,
        NotificationChannelConfig.name,
        description: NotificationChannelConfig.description,
        importance: Importance.max,
        playSound: true,
        enableVibration: true,
      );

      final androidImplementation = _localNotifications
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
      if (androidImplementation != null) {
        await androidImplementation.createNotificationChannel(androidChannel);
      }

      // 4. Register top-level FCM background handler
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

      // 5. Handle foreground notifications (present local heads-up notification)
      FirebaseMessaging.onMessage.listen(_handleForegroundMessage);

      // 6. Handle notification click when app opened from background/terminated state
      FirebaseMessaging.onMessageOpenedApp.listen(_handleMessageOpenedApp);

      // 7. Check if app was launched directly by tapping a notification from terminated state
      final initialMessage = await _firebaseMessaging.getInitialMessage();
      if (initialMessage != null) {
        _handleInitialMessage(initialMessage);
      }

      // 8. Fetch and synchronize FCM device token
      await refreshDeviceToken();

      // 9. Listen for token refreshes
      _firebaseMessaging.onTokenRefresh.listen(_onTokenRefresh);

      _isInitialized = true;
      developer.log('NotificationService initialized successfully.', name: 'NotificationService');
    } catch (e) {
      developer.log(
        'NotificationService initialization fallback/error: $e',
        name: 'NotificationService',
      );
    }
  }

  /// Fetch current FCM token and sync to backend
  Future<String?> refreshDeviceToken() async {
    try {
      _fcmToken = await _firebaseMessaging.getToken();
      if (_fcmToken != null) {
        developer.log('FCM Device Token: $_fcmToken', name: 'NotificationService');
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString(AppConstants.keyFcmToken, _fcmToken!);

        // Sync with backend if API service available
        if (_apiService != null) {
          try {
            await _apiService.updateFcmToken(_fcmToken!);
          } catch (err) {
            developer.log('Failed to sync FCM token with backend: $err', name: 'NotificationService');
          }
        }
      }
      return _fcmToken;
    } catch (e) {
      developer.log('Failed to obtain FCM token: $e', name: 'NotificationService');
      return null;
    }
  }

  void _onTokenRefresh(String newToken) async {
    _fcmToken = newToken;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(AppConstants.keyFcmToken, newToken);
    if (_apiService != null) {
      try {
        await _apiService.updateFcmToken(newToken);
      } catch (err) {
        developer.log('Failed to sync refreshed FCM token: $err', name: 'NotificationService');
      }
    }
  }

  /// Handle incoming foreground FCM message by presenting a local notification
  Future<void> _handleForegroundMessage(RemoteMessage message) async {
    developer.log('Foreground FCM message received: ${message.data}', name: 'NotificationService');

    final notification = message.notification;
    final title = notification?.title ?? message.data['title'] ?? 'Sighting Alert';
    final body = notification?.body ?? message.data['body'] ?? 'A possible match was spotted on CCTV.';
    final sightingId = message.data['sighting_id'] as String?;

    final androidDetails = AndroidNotificationDetails(
      NotificationChannelConfig.id,
      NotificationChannelConfig.name,
      channelDescription: NotificationChannelConfig.description,
      importance: Importance.max,
      priority: Priority.high,
      icon: '@mipmap/ic_launcher',
      color: const Color(0xFF0284C7),
      playSound: true,
      enableVibration: true,
    );

    const darwinDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    final notificationDetails = NotificationDetails(
      android: androidDetails,
      iOS: darwinDetails,
    );

    final payload = jsonEncode({
      'sighting_id': sightingId,
      'data': message.data,
    });

    final id = DateTime.now().millisecondsSinceEpoch.remainder(100000);
    await _localNotifications.show(
      id,
      title,
      body,
      notificationDetails,
      payload: payload,
    );
  }

  /// Handle tap on local notification displayed in foreground
  void _onLocalNotificationTap(NotificationResponse response) {
    if (response.payload == null || response.payload!.isEmpty) return;
    try {
      final decoded = jsonDecode(response.payload!) as Map<String, dynamic>;
      final sightingId = decoded['sighting_id'] as String?;
      if (sightingId != null && sightingId.isNotEmpty) {
        _onNotificationTapController.add(sightingId);
      }
    } catch (e) {
      developer.log('Error parsing notification tap payload: $e', name: 'NotificationService');
    }
  }

  /// Handle tap on FCM notification when app is brought to foreground from background
  void _handleMessageOpenedApp(RemoteMessage message) {
    final sightingId = message.data['sighting_id'] as String?;
    if (sightingId != null && sightingId.isNotEmpty) {
      _onNotificationTapController.add(sightingId);
    }
  }

  /// Handle tap on FCM notification that launched the app from terminated state
  void _handleInitialMessage(RemoteMessage message) {
    final sightingId = message.data['sighting_id'] as String?;
    if (sightingId != null && sightingId.isNotEmpty) {
      // Delay briefly to allow router and UI initialization
      Future.delayed(const Duration(milliseconds: 500), () {
        _onNotificationTapController.add(sightingId);
      });
    }
  }

  /// Programmatically show an immediate local alert (e.g. from SSE event stream)
  Future<void> showLocalAlert({
    required String title,
    required String body,
    String? sightingId,
    Map<String, dynamic>? extraData,
  }) async {
    final androidDetails = AndroidNotificationDetails(
      NotificationChannelConfig.id,
      NotificationChannelConfig.name,
      channelDescription: NotificationChannelConfig.description,
      importance: Importance.max,
      priority: Priority.high,
      icon: '@mipmap/ic_launcher',
      color: const Color(0xFF0284C7),
      playSound: true,
      enableVibration: true,
    );

    const darwinDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    final notificationDetails = NotificationDetails(
      android: androidDetails,
      iOS: darwinDetails,
    );

    final payload = jsonEncode({
      'sighting_id': sightingId,
      'data': extraData ?? {},
    });

    final id = DateTime.now().millisecondsSinceEpoch.remainder(100000);
    await _localNotifications.show(
      id,
      title,
      body,
      notificationDetails,
      payload: payload,
    );
  }

  void dispose() {
    _onNotificationTapController.close();
  }
}

/// Riverpod provider for NotificationService
final notificationServiceProvider = Provider<NotificationService>((ref) {
  final apiService = ref.watch(apiServiceProvider);
  final service = NotificationService(apiService: apiService);
  service.initialize();
  ref.onDispose(() => service.dispose());
  return service;
});

/// Stream provider for notification tap events
final notificationTapStreamProvider = StreamProvider<String>((ref) {
  final service = ref.watch(notificationServiceProvider);
  return service.onNotificationTap;
});
