import 'dart:developer' as developer;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/enums.dart';
import '../models/notification_model.dart';
import '../services/api_service.dart';
import '../services/sse_service.dart';
import '../services/notification_service.dart';
import 'auth_provider.dart';

/// Notifications state model
class NotificationsState {
  final List<NotificationModel> notifications;
  final int unreadCount;
  final bool isLoading;
  final bool isRefreshing;
  final String? errorMessage;
  final bool unreadOnly;

  const NotificationsState({
    this.notifications = const [],
    this.unreadCount = 0,
    this.isLoading = false,
    this.isRefreshing = false,
    this.errorMessage,
    this.unreadOnly = false,
  });

  List<NotificationModel> get filteredNotifications {
    if (unreadOnly) {
      return notifications.where((n) => !n.isRead).toList();
    }
    return notifications;
  }

  NotificationsState copyWith({
    List<NotificationModel>? notifications,
    int? unreadCount,
    bool? isLoading,
    bool? isRefreshing,
    String? errorMessage,
    bool? unreadOnly,
  }) {
    return NotificationsState(
      notifications: notifications ?? this.notifications,
      unreadCount: unreadCount ?? this.unreadCount,
      isLoading: isLoading ?? this.isLoading,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      errorMessage: errorMessage,
      unreadOnly: unreadOnly ?? this.unreadOnly,
    );
  }
}

/// StateNotifier for user notification center and live alert ingestion
class NotificationsNotifier extends StateNotifier<NotificationsState> {
  final Ref _ref;

  NotificationsNotifier(this._ref) : super(const NotificationsState()) {
    _initLiveListener();
    fetchNotifications();
  }

  ApiService get _api => _ref.read(apiServiceProvider);

  void _initLiveListener() {
    // Listen to live SSE events from backend to prepend notifications and update unread count
    _ref.listen<AsyncValue<SseEvent>>(sseEventsProvider, (_, next) {
      next.whenData((event) {
        developer.log('Live SSE event received in NotificationsNotifier: ${event.event}', name: 'NotificationsNotifier');

        final title = event.title ?? 'Sighting Alert';
        final body = event.body ?? 'A possible biometric match was detected.';
        final sightingId = event.sightingId;
        final notificationId = event.notificationId ?? DateTime.now().millisecondsSinceEpoch.toString();

        final liveNotif = NotificationModel(
          id: notificationId,
          type: NotificationType.fromString(event.event),
          title: title,
          body: body,
          sightingId: sightingId,
          data: event.data,
          isRead: false,
          createdAt: event.receivedAt,
        );

        // Prepend to notifications list
        final updatedList = [liveNotif, ...state.notifications];
        state = state.copyWith(
          notifications: updatedList,
          unreadCount: state.unreadCount + 1,
        );

        // Show local foreground notification banner
        try {
          final notificationService = _ref.read(notificationServiceProvider);
          notificationService.showLocalAlert(
            title: title,
            body: body,
            sightingId: sightingId,
            extraData: event.data,
          );
        } catch (e) {
          developer.log('Failed to present local alert banner: $e', name: 'NotificationsNotifier');
        }
      });
    });
  }

  /// Fetch notifications from backend API
  Future<void> fetchNotifications({
    bool refresh = false,
  }) async {
    if (state.isLoading && !refresh) return;

    state = state.copyWith(
      isLoading: !refresh,
      isRefreshing: refresh,
      errorMessage: null,
    );

    try {
      final response = await _api.getNotifications(
        unreadOnly: false,
        limit: 50,
      );

      state = state.copyWith(
        notifications: response.items,
        unreadCount: response.unreadCount,
        isLoading: false,
        isRefreshing: false,
      );
    } catch (e) {
      developer.log('Error loading notifications: $e', name: 'NotificationsNotifier');
      state = state.copyWith(
        isLoading: false,
        isRefreshing: false,
        errorMessage: e.toString(),
      );
    }
  }

  /// Toggle unread filter
  void setUnreadOnly(bool unreadOnly) {
    state = state.copyWith(unreadOnly: unreadOnly);
  }

  /// Mark single notification as read
  Future<void> markAsRead(String notificationId) async {
    // Optimistic UI update
    final updatedList = state.notifications.map((n) {
      if (n.id == notificationId && !n.isRead) {
        return n.copyWith(isRead: true);
      }
      return n;
    }).toList();

    final prevUnread = state.unreadCount;
    state = state.copyWith(
      notifications: updatedList,
      unreadCount: (prevUnread - 1).clamp(0, 9999),
    );

    try {
      await _api.markNotificationRead(notificationId);
    } catch (e) {
      developer.log('Failed to mark notification read on server: $e', name: 'NotificationsNotifier');
    }
  }

  /// Mark all notifications as read
  Future<void> markAllAsRead() async {
    // Optimistic UI update
    final updatedList = state.notifications.map((n) => n.copyWith(isRead: true)).toList();
    state = state.copyWith(
      notifications: updatedList,
      unreadCount: 0,
    );

    try {
      await _api.markAllNotificationsRead();
    } catch (e) {
      developer.log('Failed to mark all read on server: $e', name: 'NotificationsNotifier');
    }
  }

  /// Refresh unread count specifically
  Future<void> refreshUnreadCount() async {
    try {
      final count = await _api.getUnreadNotificationCount();
      state = state.copyWith(unreadCount: count);
    } catch (e) {
      developer.log('Error refreshing unread count: $e', name: 'NotificationsNotifier');
    }
  }
}

/// Provider for user notifications state
final notificationsProvider =
    StateNotifierProvider<NotificationsNotifier, NotificationsState>((ref) {
  return NotificationsNotifier(ref);
});

/// Dedicated provider for the unread badge count
final unreadNotificationsCountProvider = Provider<int>((ref) {
  final state = ref.watch(notificationsProvider);
  return state.unreadCount;
});
