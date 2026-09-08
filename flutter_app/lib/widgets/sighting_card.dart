import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../models/sighting_model.dart';

/// Card widget rendering a CCTV Sighting event summary with face crop and metadata
class SightingCard extends StatelessWidget {
  final SightingModel sighting;
  final String? registeredPhotoUrl;
  final bool isHindi;
  final VoidCallback? onTap;

  const SightingCard({
    super.key,
    required this.sighting,
    this.registeredPhotoUrl,
    this.isHindi = false,
    this.onTap,
  });

  Color _getScoreColor(double score) {
    if (score >= 0.75) return AppTheme.successGreen;
    if (score >= 0.60) return AppTheme.accentAmber;
    return AppTheme.errorRed;
  }

  String _getStatusText(SightingStatus status, bool isHindi) {
    switch (status) {
      case SightingStatus.confirmed:
        return isHindi ? 'सत्यापित मैच' : 'CONFIRMED MATCH';
      case SightingStatus.rejected:
        return isHindi ? 'खारिज' : 'REJECTED';
      case SightingStatus.pending:
      default:
        return isHindi ? 'सत्यापन बाकी' : 'PENDING REVIEW';
    }
  }

  Color _getStatusColor(SightingStatus status) {
    switch (status) {
      case SightingStatus.confirmed:
        return AppTheme.successGreen;
      case SightingStatus.rejected:
        return AppTheme.errorRed;
      case SightingStatus.pending:
      default:
        return AppTheme.accentAmber;
    }
  }

  @override
  Widget build(BuildContext context) {
    final scoreColor = _getScoreColor(sighting.similarityScore);
    final statusColor = _getStatusColor(sighting.status);
    final formattedTime = DateFormat('dd MMM yyyy, hh:mm a').format(sighting.detectedAt.toLocal());

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6, horizontal: 0),
      color: AppTheme.darkCard,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: sighting.status == SightingStatus.confirmed
              ? AppTheme.successGreen.withOpacity(0.5)
              : AppTheme.darkCardBorder,
          width: sighting.status == SightingStatus.confirmed ? 1.5 : 1.0,
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap ?? () => context.push(AppRoutes.sightingDetailPath(sighting.id)),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top Header: Status Tag & Similarity Score Pill
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  // Status Tag
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: statusColor.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: statusColor.withOpacity(0.4)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          sighting.status == SightingStatus.confirmed
                              ? Icons.verified
                              : (sighting.status == SightingStatus.rejected
                                  ? Icons.cancel_outlined
                                  : Icons.hourglass_top),
                          size: 13,
                          color: statusColor,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          _getStatusText(sighting.status, isHindi),
                          style: TextStyle(
                            fontSize: 10.5,
                            fontWeight: FontWeight.w700,
                            color: statusColor,
                            letterSpacing: 0.4,
                          ),
                        ),
                      ],
                    ),
                  ),

                  // Similarity Score Badge
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: scoreColor.withOpacity(0.18),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: scoreColor.withOpacity(0.5)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.bolt, color: scoreColor, size: 14),
                        const SizedBox(width: 2),
                        Text(
                          '${(sighting.similarityScore * 100).toStringAsFixed(1)}% ${isHindi ? 'मैच' : 'Match'}',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w800,
                            color: scoreColor,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // Main Row: Image Thumbnails + CCTV Metadata
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Sighting CCTV Face Crop Thumbnail
                  Container(
                    width: 76,
                    height: 76,
                    decoration: BoxDecoration(
                      color: AppTheme.darkSurface,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppTheme.darkCardBorder),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: sighting.faceCropUrl != null
                        ? CachedNetworkImage(
                            imageUrl: sighting.faceCropUrl!,
                            fit: BoxFit.cover,
                            placeholder: (_, __) => Container(
                              color: AppTheme.darkSurface,
                              child: const Center(
                                child: SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                ),
                              ),
                            ),
                            errorWidget: (_, __, ___) => const Center(
                              child: Icon(Icons.broken_image, color: AppTheme.darkTextSecondary, size: 28),
                            ),
                          )
                        : const Center(
                            child: Icon(Icons.videocam_outlined, color: AppTheme.darkTextSecondary, size: 30),
                          ),
                  ),
                  const SizedBox(width: 12),

                  // Sighting Metadata
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Camera Location / Name
                        Row(
                          children: [
                            const Icon(Icons.videocam, size: 15, color: AppTheme.primaryLight),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                sighting.cameraLocation ??
                                    'Camera #${sighting.cameraId.length > 8 ? sighting.cameraId.substring(0, 8) : sighting.cameraId}',
                                style: const TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w700,
                                  color: AppTheme.darkTextPrimary,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),

                        // Timestamp
                        Row(
                          children: [
                            const Icon(Icons.access_time, size: 13, color: AppTheme.darkTextSecondary),
                            const SizedBox(width: 6),
                            Text(
                              formattedTime,
                              style: const TextStyle(
                                fontSize: 11.5,
                                color: AppTheme.darkTextSecondary,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),

                        // Frames Matched & GPS Pill Tags
                        Wrap(
                          spacing: 6,
                          runSpacing: 4,
                          children: [
                            if (sighting.numFramesMatched != null)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppTheme.primaryBlue.withOpacity(0.12),
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text(
                                  '${sighting.numFramesMatched} ${isHindi ? 'फ्रेम सत्यापित' : 'frames matched'}',
                                  style: const TextStyle(
                                    color: AppTheme.primaryLight,
                                    fontSize: 10,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            if (sighting.latitude != null && sighting.longitude != null)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppTheme.secondaryCyan.withOpacity(0.12),
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    const Icon(Icons.location_on, size: 10, color: AppTheme.secondaryCyan),
                                    const SizedBox(width: 2),
                                    Text(
                                      '${sighting.latitude!.toStringAsFixed(4)}, ${sighting.longitude!.toStringAsFixed(4)}',
                                      style: const TextStyle(
                                        color: AppTheme.secondaryCyan,
                                        fontSize: 10,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // Forward Navigation Chevron
                  const Align(
                    alignment: Alignment.center,
                    child: Icon(
                      Icons.chevron_right,
                      color: AppTheme.darkTextSecondary,
                      size: 20,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
