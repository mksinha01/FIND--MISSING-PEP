import 'package:flutter/material.dart';
import '../config/theme.dart';
import '../models/enums.dart';

enum StatusBadgeSize { small, medium, large }

/// Reusable Color-Coded Status Badge Widget
class StatusBadge extends StatelessWidget {
  final String status;
  final StatusBadgeSize size;
  final bool showIcon;
  final bool isHindi;

  const StatusBadge({
    super.key,
    required this.status,
    this.size = StatusBadgeSize.medium,
    this.showIcon = true,
    this.isHindi = false,
  });

  factory StatusBadge.fromReportStatus({
    Key? key,
    required ReportStatus status,
    StatusBadgeSize size = StatusBadgeSize.medium,
    bool showIcon = true,
    bool isHindi = false,
  }) {
    return StatusBadge(
      key: key,
      status: status.value,
      size: size,
      showIcon: showIcon,
      isHindi: isHindi,
    );
  }

  factory StatusBadge.fromSightingStatus({
    Key? key,
    required SightingStatus status,
    StatusBadgeSize size = StatusBadgeSize.medium,
    bool showIcon = true,
    bool isHindi = false,
  }) {
    return StatusBadge(
      key: key,
      status: status.value,
      size: size,
      showIcon: showIcon,
      isHindi: isHindi,
    );
  }

  @override
  Widget build(BuildContext context) {
    final normalized = status.toUpperCase();
    final color = _getStatusColor(normalized);
    final iconData = _getStatusIcon(normalized);
    final label = _getStatusLabel(normalized);

    final double horizontalPadding;
    final double verticalPadding;
    final double fontSize;
    final double iconSize;

    switch (size) {
      case StatusBadgeSize.small:
        horizontalPadding = 8;
        verticalPadding = 3;
        fontSize = 11;
        iconSize = 12;
        break;
      case StatusBadgeSize.medium:
        horizontalPadding = 12;
        verticalPadding = 6;
        fontSize = 12;
        iconSize = 14;
        break;
      case StatusBadgeSize.large:
        horizontalPadding = 16;
        verticalPadding = 8;
        fontSize = 14;
        iconSize = 16;
        break;
    }

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: horizontalPadding,
        vertical: verticalPadding,
      ),
      decoration: BoxDecoration(
        color: color.withOpacity(0.14),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: color.withOpacity(0.45),
          width: 1.2,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (showIcon) ...[
            Icon(
              iconData,
              color: color,
              size: iconSize,
            ),
            SizedBox(width: size == StatusBadgeSize.small ? 4 : 6),
          ],
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: fontSize,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.3,
            ),
          ),
        ],
      ),
    );
  }

  Color _getStatusColor(String status) {
    switch (status) {
      case 'ACTIVE':
        return AppTheme.successGreen;
      case 'FOUND':
        return AppTheme.primaryLight;
      case 'PROCESSING':
        return AppTheme.accentAmber;
      case 'CLOSED':
        return const Color(0xFF94A3B8); // Slate 400
      case 'CONFIRMED':
        return AppTheme.successGreen;
      case 'REJECTED':
      case 'REJECTED_NO_FACE':
        return AppTheme.errorRed;
      case 'PENDING':
        return AppTheme.accentAmber;
      default:
        return AppTheme.primaryBlue;
    }
  }

  IconData _getStatusIcon(String status) {
    switch (status) {
      case 'ACTIVE':
        return Icons.radar;
      case 'FOUND':
        return Icons.check_circle_outline;
      case 'PROCESSING':
        return Icons.hourglass_top_outlined;
      case 'CLOSED':
        return Icons.archive_outlined;
      case 'CONFIRMED':
        return Icons.verified_outlined;
      case 'REJECTED':
      case 'REJECTED_NO_FACE':
        return Icons.cancel_outlined;
      case 'PENDING':
        return Icons.pending_outlined;
      default:
        return Icons.info_outline;
    }
  }

  String _getStatusLabel(String status) {
    if (isHindi) {
      switch (status) {
        case 'ACTIVE':
          return 'सक्रिय';
        case 'FOUND':
          return 'मिल गया';
        case 'PROCESSING':
          return 'प्रक्रियाधीन';
        case 'CLOSED':
          return 'केस बंद';
        case 'CONFIRMED':
          return 'सत्यापित मैच';
        case 'REJECTED':
          return 'खारिज';
        case 'REJECTED_NO_FACE':
          return 'चेहरा नहीं मिला';
        case 'PENDING':
          return 'प्रतीक्षारत';
        default:
          return status;
      }
    } else {
      switch (status) {
        case 'ACTIVE':
          return 'ACTIVE';
        case 'FOUND':
          return 'FOUND';
        case 'PROCESSING':
          return 'PROCESSING';
        case 'CLOSED':
          return 'CLOSED';
        case 'CONFIRMED':
          return 'CONFIRMED';
        case 'REJECTED':
          return 'REJECTED';
        case 'REJECTED_NO_FACE':
          return 'NO FACE FOUND';
        case 'PENDING':
          return 'PENDING';
        default:
          return status;
      }
    }
  }
}
