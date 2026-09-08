import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../models/missing_person_model.dart';
import 'status_badge.dart';

/// Clean, elevated card widget displaying summary of a Missing Person Report
class ReportCard extends StatelessWidget {
  final MissingPersonModel report;
  final VoidCallback? onTap;
  final bool isHindi;

  const ReportCard({
    super.key,
    required this.report,
    this.onTap,
    this.isHindi = false,
  });

  @override
  Widget build(BuildContext context) {
    final primaryPhoto = report.primaryFaceCropUrl ?? report.primaryPhotoUrl;

    return Card(
      elevation: 0,
      margin: const EdgeInsets.symmetric(vertical: 6, horizontal: 0),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppTheme.darkCardBorder, width: 1),
      ),
      color: AppTheme.darkCard,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Photo Thumbnail / Avatar Placeholder
              _buildPhotoThumbnail(primaryPhoto),
              const SizedBox(width: 14),

              // Report Metadata Column
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Name & Status Badge Row
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            report.fullName,
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                              color: AppTheme.darkTextPrimary,
                              letterSpacing: -0.2,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        StatusBadge(
                          status: report.status.value,
                          size: StatusBadgeSize.small,
                          isHindi: isHindi,
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),

                    // Age & Gender Tag
                    Row(
                      children: [
                        if (report.age != null) ...[
                          _buildMetadataTag(
                            icon: Icons.cake_outlined,
                            text: isHindi ? '${report.age} वर्ष' : '${report.age} yrs',
                          ),
                          const SizedBox(width: 8),
                        ],
                        if (report.gender != null) ...[
                          _buildMetadataTag(
                            icon: Icons.person_outline,
                            text: isHindi
                                ? report.gender!.displayNameHi
                                : report.gender!.displayName,
                          ),
                          const SizedBox(width: 8),
                        ],
                        if (report.photos.isNotEmpty)
                          _buildMetadataTag(
                            icon: Icons.photo_library_outlined,
                            text: isHindi
                                ? '${report.photos.length} फोटो'
                                : '${report.photos.length} ${report.photos.length == 1 ? 'photo' : 'photos'}',
                          ),
                      ],
                    ),
                    const SizedBox(height: 8),

                    // Last Seen Location
                    if (report.lastSeenLocation != null &&
                        report.lastSeenLocation!.isNotEmpty) ...[
                      Row(
                        children: [
                          const Icon(
                            Icons.location_on_outlined,
                            size: 14,
                            color: AppTheme.primaryLight,
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              report.lastSeenLocation!,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppTheme.darkTextSecondary,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                    ],

                    // Last Seen Time or Reported Date
                    if (report.lastSeenTime != null || report.createdAt != null) ...[
                      Row(
                        children: [
                          const Icon(
                            Icons.access_time,
                            size: 13,
                            color: AppTheme.darkTextSecondary,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            _formatDateTime(report.lastSeenTime ?? report.createdAt!),
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppTheme.darkTextSecondary,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),

              // Trailing Chevron
              const SizedBox(width: 6),
              const Icon(
                Icons.chevron_right,
                size: 20,
                color: AppTheme.darkTextSecondary,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPhotoThumbnail(String? photoUrl) {
    const double size = 70.0;

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: AppTheme.darkSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.darkCardBorder, width: 1),
      ),
      clipBehavior: Clip.antiAlias,
      child: photoUrl != null && photoUrl.isNotEmpty
          ? CachedNetworkImage(
              imageUrl: photoUrl,
              fit: BoxFit.cover,
              placeholder: (context, url) => Container(
                color: AppTheme.darkSurface,
                child: const Center(
                  child: SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppTheme.primaryLight,
                    ),
                  ),
                ),
              ),
              errorWidget: (context, url, error) => _buildInitialsPlaceholder(),
            )
          : _buildInitialsPlaceholder(),
    );
  }

  Widget _buildInitialsPlaceholder() {
    final initials = report.fullName.isNotEmpty
        ? report.fullName
            .trim()
            .split(' ')
            .take(2)
            .map((e) => e.isNotEmpty ? e[0].toUpperCase() : '')
            .join()
        : '?';

    return Container(
      color: AppTheme.primaryBlue.withOpacity(0.25),
      child: Center(
        child: Text(
          initials,
          style: const TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: AppTheme.primaryLight,
          ),
        ),
      ),
    );
  }

  Widget _buildMetadataTag({required IconData icon, required String text}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: AppTheme.darkSurface,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppTheme.darkCardBorder.withOpacity(0.6)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 11, color: AppTheme.darkTextSecondary),
          const SizedBox(width: 3),
          Text(
            text,
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: AppTheme.darkTextSecondary,
            ),
          ),
        ],
      ),
    );
  }

  String _formatDateTime(DateTime dt) {
    try {
      final formatter = DateFormat('dd MMM yyyy, hh:mm a');
      return formatter.format(dt.toLocal());
    } catch (_) {
      return dt.toIso8601String().substring(0, 16);
    }
  }
}
