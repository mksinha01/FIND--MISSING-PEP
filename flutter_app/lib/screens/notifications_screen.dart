import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../models/notification_model.dart';
import '../providers/auth_provider.dart';
import '../providers/notifications_provider.dart';

/// User Notifications & Real-Time Alerts Inbox Screen
class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';
    final notifState = ref.watch(notificationsProvider);
    final notifier = ref.read(notificationsProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: Text(isHindi ? 'अलर्ट्स व सूचनाएं' : 'Alerts & Notifications'),
        actions: [
          if (notifState.unreadCount > 0)
            TextButton.icon(
              icon: const Icon(Icons.done_all, size: 18, color: AppTheme.primaryLight),
              label: Text(
                isHindi ? 'सभी पढ़ें' : 'Mark All Read',
                style: const TextStyle(
                  color: AppTheme.primaryLight,
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                ),
              ),
              onPressed: () => notifier.markAllAsRead(),
            ),
        ],
      ),
      body: Column(
        children: [
          // ── FILTER CHIPS BAR: ALL VS UNREAD ──
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            decoration: const BoxDecoration(
              color: AppTheme.darkSurface,
              border: Border(bottom: BorderSide(color: AppTheme.darkCardBorder)),
            ),
            child: Row(
              children: [
                _buildFilterChip(
                  label: isHindi ? 'सभी' : 'All',
                  isSelected: !notifState.unreadOnly,
                  count: notifState.notifications.length,
                  onSelected: () => notifier.setUnreadOnly(false),
                ),
                const SizedBox(width: 8),
                _buildFilterChip(
                  label: isHindi ? 'अपठित' : 'Unread',
                  isSelected: notifState.unreadOnly,
                  count: notifState.unreadCount,
                  badgeColor: AppTheme.primaryLight,
                  onSelected: () => notifier.setUnreadOnly(true),
                ),
              ],
            ),
          ),

          // ── NOTIFICATIONS LIST ──
          Expanded(
            child: notifState.isLoading
                ? const Center(
                    child: CircularProgressIndicator(color: AppTheme.primaryLight),
                  )
                : RefreshIndicator(
                    color: AppTheme.primaryLight,
                    backgroundColor: AppTheme.darkSurface,
                    onRefresh: () => notifier.fetchNotifications(refresh: true),
                    child: notifState.filteredNotifications.isEmpty
                        ? _buildEmptyState(isHindi, notifState.unreadOnly)
                        : ListView.separated(
                            physics: const AlwaysScrollableScrollPhysics(),
                            padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
                            itemCount: notifState.filteredNotifications.length,
                            separatorBuilder: (_, __) => const SizedBox(height: 6),
                            itemBuilder: (context, index) {
                              final item = notifState.filteredNotifications[index];
                              return _NotificationTile(
                                item: item,
                                isHindi: isHindi,
                                onTap: () {
                                  if (!item.isRead) {
                                    notifier.markAsRead(item.id);
                                  }
                                  if (item.sightingId != null && item.sightingId!.isNotEmpty) {
                                    context.push(AppRoutes.sightingDetailPath(item.sightingId!));
                                  } else if (item.data != null && item.data!['person_id'] != null) {
                                    context.push(AppRoutes.reportDetailPath(item.data!['person_id'].toString()));
                                  }
                                },
                              );
                            },
                          ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip({
    required String label,
    required bool isSelected,
    required int count,
    Color? badgeColor,
    required VoidCallback onSelected,
  }) {
    return ChoiceChip(
      selected: isSelected,
      onSelected: (_) => onSelected(),
      backgroundColor: AppTheme.darkCard,
      selectedColor: AppTheme.primaryBlue.withOpacity(0.3),
      side: BorderSide(
        color: isSelected ? AppTheme.primaryLight : AppTheme.darkCardBorder,
      ),
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isSelected ? FontWeight.w700 : FontWeight.normal,
              color: isSelected ? AppTheme.primaryLight : AppTheme.darkTextSecondary,
            ),
          ),
          if (count > 0) ...[
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: isSelected
                    ? (badgeColor ?? AppTheme.primaryLight)
                    : AppTheme.darkCardBorder,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                '$count',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  color: isSelected ? Colors.black : AppTheme.darkTextSecondary,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildEmptyState(bool isHindi, bool unreadOnly) {
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: Container(
          constraints: BoxConstraints(minHeight: constraints.maxHeight),
          alignment: Alignment.center,
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AppTheme.darkCard,
                  shape: BoxShape.circle,
                  border: Border.all(color: AppTheme.darkCardBorder),
                ),
                child: const Icon(
                  Icons.notifications_none_outlined,
                  size: 56,
                  color: AppTheme.darkTextSecondary,
                ),
              ),
              const SizedBox(height: 20),
              Text(
                unreadOnly
                    ? (isHindi ? 'कोई नई अपठित सूचना नहीं है' : 'No Unread Notifications')
                    : (isHindi ? 'कोई सूचना उपलब्ध नहीं है' : 'No Notifications Yet'),
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.darkTextPrimary,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                isHindi
                    ? 'सीसीटीवी नोड्स द्वारा मैच मिलने या केस अपडेट होने पर यहां अलर्ट दिखाई देंगे।'
                    : 'Real-time sighting alerts and case updates will appear here.',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: AppTheme.darkTextSecondary,
                  fontSize: 13,
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Notification item card widget
class _NotificationTile extends StatelessWidget {
  final NotificationModel item;
  final bool isHindi;
  final VoidCallback onTap;

  const _NotificationTile({
    required this.item,
    required this.isHindi,
    required this.onTap,
  });

  IconData _getIcon(NotificationType type) {
    switch (type) {
      case NotificationType.sighting:
        return Icons.radar;
      case NotificationType.confirmation:
        return Icons.verified;
      case NotificationType.statusChange:
        return Icons.info_outline;
    }
  }

  Color _getIconColor(NotificationType type) {
    switch (type) {
      case NotificationType.sighting:
        return AppTheme.primaryLight;
      case NotificationType.confirmation:
        return AppTheme.successGreen;
      case NotificationType.statusChange:
        return AppTheme.secondaryCyan;
    }
  }

  @override
  Widget build(BuildContext context) {
    final icon = _getIcon(item.type);
    final iconColor = _getIconColor(item.type);
    final timeStr = item.createdAt != null
        ? DateFormat('dd MMM, hh:mm a').format(item.createdAt!.toLocal())
        : '';

    return Card(
      margin: EdgeInsets.zero,
      color: item.isRead ? AppTheme.darkCard.withOpacity(0.7) : AppTheme.darkCard,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(
          color: item.isRead ? AppTheme.darkCardBorder : AppTheme.primaryBlue.withOpacity(0.5),
          width: item.isRead ? 1.0 : 1.5,
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Type Icon Badge
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: iconColor.withOpacity(0.15),
                  shape: BoxShape.circle,
                  border: Border.all(color: iconColor.withOpacity(0.3)),
                ),
                child: Icon(icon, color: iconColor, size: 22),
              ),
              const SizedBox(width: 14),

              // Title & Body
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            item.title,
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: item.isRead ? FontWeight.w600 : FontWeight.w800,
                              color: AppTheme.darkTextPrimary,
                            ),
                          ),
                        ),
                        if (!item.isRead)
                          Container(
                            width: 8,
                            height: 8,
                            margin: const EdgeInsets.only(left: 6),
                            decoration: const BoxDecoration(
                              color: AppTheme.primaryLight,
                              shape: BoxShape.circle,
                            ),
                          ),
                      ],
                    ),
                    if (item.body != null && item.body!.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        item.body!,
                        style: const TextStyle(
                          fontSize: 12.5,
                          color: AppTheme.darkTextSecondary,
                          height: 1.3,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                    const SizedBox(height: 8),

                    // Timestamp and Action Hint
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          timeStr,
                          style: const TextStyle(
                            fontSize: 11,
                            color: AppTheme.darkTextSecondary,
                          ),
                        ),
                        if (item.sightingId != null)
                          Row(
                            children: [
                              Text(
                                isHindi ? 'विवरण देखें' : 'View Sighting',
                                style: const TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: AppTheme.primaryLight,
                                ),
                              ),
                              const Icon(
                                Icons.arrow_forward_ios,
                                size: 10,
                                color: AppTheme.primaryLight,
                              ),
                            ],
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
