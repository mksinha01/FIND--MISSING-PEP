import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../models/missing_person_model.dart';
import '../providers/auth_provider.dart';
import '../providers/reports_provider.dart';
import '../services/image_service.dart';
import '../widgets/status_badge.dart';

/// Screen displaying complete details for a specific Missing Person Report
class ReportDetailScreen extends ConsumerStatefulWidget {
  final String reportId;

  const ReportDetailScreen({
    super.key,
    required this.reportId,
  });

  @override
  ConsumerState<ReportDetailScreen> createState() => _ReportDetailScreenState();
}

class _ReportDetailScreenState extends ConsumerState<ReportDetailScreen> {
  int _selectedPhotoIndex = 0;
  bool _isUploadingPhoto = false;

  Future<void> _refresh() async {
    ref.invalidate(reportDetailProvider(widget.reportId));
  }

  Future<void> _showMarkAsFoundDialog(BuildContext context, bool isHindi) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.darkSurface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppTheme.darkCardBorder),
        ),
        title: Row(
          children: [
            const Icon(Icons.check_circle, color: AppTheme.successGreen),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                isHindi ? 'मिल गया चिह्नित करें?' : 'Mark as Found?',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
        content: Text(
          isHindi
              ? 'क्या आप पुष्टि करते हैं कि यह व्यक्ति सुरक्षित मिल गया है? स्टेटस को "मिल गया" में अपडेट किया जाएगा।'
              : 'Are you sure you want to mark this individual as FOUND? The case status will be updated accordingly.',
          style: const TextStyle(color: AppTheme.darkTextSecondary, height: 1.4),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(
              isHindi ? 'रद्द करें' : 'Cancel',
              style: const TextStyle(color: AppTheme.darkTextSecondary),
            ),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.successGreen,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(isHindi ? 'हाँ, मिल गया' : 'Yes, Mark Found'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        await ref.read(reportsProvider.notifier).markAsFound(widget.reportId);
        ref.invalidate(reportDetailProvider(widget.reportId));
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                isHindi
                    ? 'केस स्टेटस को "मिल गया" में अपडेट किया गया!'
                    : 'Report status updated to FOUND!',
              ),
              backgroundColor: AppTheme.darkCard,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                isHindi ? 'त्रुटि हुई: $e' : 'Failed to update status: $e',
              ),
              backgroundColor: AppTheme.errorRed,
            ),
          );
        }
      }
    }
  }

  Future<void> _showCloseCaseDialog(BuildContext context, bool isHindi) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.darkSurface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppTheme.darkCardBorder),
        ),
        title: Row(
          children: [
            const Icon(Icons.archive_outlined, color: AppTheme.accentAmber),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                isHindi ? 'केस बंद करें?' : 'Close Case?',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
        content: Text(
          isHindi
              ? 'क्या आप इस केस को बंद करना चाहते हैं? इससे सभी सीसीटीवी कैमरा नोड्स पर बायोमेट्रिक एम्बेडिंग्स निष्क्रिय हो जाएंगी।'
              : 'Are you sure you want to close this case? Biometric embeddings will be deactivated across all CCTV edge nodes.',
          style: const TextStyle(color: AppTheme.darkTextSecondary, height: 1.4),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(
              isHindi ? 'रद्द करें' : 'Cancel',
              style: const TextStyle(color: AppTheme.darkTextSecondary),
            ),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.errorRed,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(isHindi ? 'केस बंद करें' : 'Close Case'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        await ref.read(reportsProvider.notifier).closeReport(widget.reportId);
        ref.invalidate(reportDetailProvider(widget.reportId));
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                isHindi
                    ? 'केस बंद कर दिया गया और एम्बेडिंग्स निष्क्रिय की गईं।'
                    : 'Case closed and biometric embeddings deactivated.',
              ),
              backgroundColor: AppTheme.darkCard,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                isHindi ? 'त्रुटि हुई: $e' : 'Failed to close report: $e',
              ),
              backgroundColor: AppTheme.errorRed,
            ),
          );
        }
      }
    }
  }

  Future<void> _addAdditionalPhoto(BuildContext context, bool isHindi) async {
    final imageService = ImageService();
    try {
      final photo = await imageService.pickAndCompressFromCamera();
      if (photo == null) return;

      setState(() => _isUploadingPhoto = true);

      await ref.read(reportsProvider.notifier).uploadAdditionalPhoto(
            widget.reportId,
            photo,
          );

      ref.invalidate(reportDetailProvider(widget.reportId));

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              isHindi
                  ? 'अतिरिक्त फोटो सफलतापूर्वक अपलोड की गई!'
                  : 'Additional photo uploaded and processed!',
            ),
            backgroundColor: AppTheme.darkCard,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              isHindi ? 'फोटो अपलोड विफल: $e' : 'Failed to upload photo: $e',
            ),
            backgroundColor: AppTheme.errorRed,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isUploadingPhoto = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';
    final reportAsync = ref.watch(reportDetailProvider(widget.reportId));

    return Scaffold(
      appBar: AppBar(
        title: Text(isHindi ? 'रिपोर्ट का विवरण' : 'Report Details'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: isHindi ? 'रिफ्रेश करें' : 'Refresh',
            onPressed: _refresh,
          ),
        ],
      ),
      body: reportAsync.when(
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppTheme.primaryLight),
        ),
        error: (err, stack) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 54, color: AppTheme.errorRed),
                const SizedBox(height: 16),
                Text(
                  isHindi ? 'डेटा लोड करने में त्रुटि' : 'Failed to Load Report',
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                Text(
                  err.toString(),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppTheme.darkTextSecondary, fontSize: 13),
                ),
                const SizedBox(height: 20),
                ElevatedButton.icon(
                  onPressed: _refresh,
                  icon: const Icon(Icons.refresh),
                  label: Text(isHindi ? 'पुनः प्रयास करें' : 'Retry'),
                ),
              ],
            ),
          ),
        ),
        data: (report) => _buildContent(context, report, isHindi),
      ),
    );
  }

  Widget _buildContent(BuildContext context, MissingPersonModel report, bool isHindi) {
    return RefreshIndicator(
      color: AppTheme.primaryLight,
      backgroundColor: AppTheme.darkSurface,
      onRefresh: _refresh,
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── PHOTO CAROUSEL & PREVIEW STRIP ──
            _buildPhotoGallery(report, isHindi),
            const SizedBox(height: 20),

            // ── HEADER: NAME & STATUS BADGE ──
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        report.fullName,
                        style: const TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                          color: AppTheme.darkTextPrimary,
                          letterSpacing: -0.4,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'ID: ${report.id.length > 8 ? report.id.substring(0, 8).toUpperCase() : report.id}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppTheme.darkTextSecondary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
                StatusBadge(
                  status: report.status.value,
                  size: StatusBadgeSize.medium,
                  isHindi: isHindi,
                ),
              ],
            ),
            const SizedBox(height: 16),

            // ── STATUS DESCRIPTION BANNER ──
            _buildStatusDescriptionBanner(report.status, isHindi),
            const SizedBox(height: 20),

            // ── SECTION 1: PERSONAL DETAILS ──
            _buildCardSection(
              title: isHindi ? 'व्यक्तिगत जानकारी' : 'Personal Details',
              icon: Icons.person_outline,
              child: Column(
                children: [
                  _buildDetailRow(
                    isHindi ? 'उम्र' : 'Age',
                    report.age != null
                        ? (isHindi ? '${report.age} वर्ष' : '${report.age} years old')
                        : '—',
                  ),
                  _buildDivider(),
                  _buildDetailRow(
                    isHindi ? 'लिंग' : 'Gender',
                    report.gender != null
                        ? (isHindi ? report.gender!.displayNameHi : report.gender!.displayName)
                        : '—',
                  ),
                  _buildDivider(),
                  _buildDetailRow(
                    isHindi ? 'कद' : 'Height',
                    report.heightCm != null ? '${report.heightCm} cm' : '—',
                  ),
                  if (report.description != null && report.description!.isNotEmpty) ...[
                    _buildDivider(),
                    _buildDetailRow(
                      isHindi ? 'विवरण एवं कपड़े' : 'Distinctive Features',
                      report.description!,
                      isMultiline: true,
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 16),

            // ── SECTION 2: INCIDENT DETAILS ──
            _buildCardSection(
              title: isHindi ? 'घटना का विवरण' : 'Incident Details',
              icon: Icons.access_time_filled,
              child: Column(
                children: [
                  _buildDetailRow(
                    isHindi ? 'अंतिम देखा गया स्थान' : 'Last Seen Location',
                    report.lastSeenLocation ?? '—',
                    icon: Icons.location_on_outlined,
                  ),
                  _buildDivider(),
                  _buildDetailRow(
                    isHindi ? 'अंतिम देखा गया समय' : 'Last Seen Time',
                    report.lastSeenTime != null
                        ? DateFormat('EEEE, dd MMM yyyy, hh:mm a').format(report.lastSeenTime!.toLocal())
                        : '—',
                    icon: Icons.access_time,
                  ),
                  if (report.createdAt != null) ...[
                    _buildDivider(),
                    _buildDetailRow(
                      isHindi ? 'रिपोर्ट दर्ज दिनांक' : 'Report Filed On',
                      DateFormat('dd MMM yyyy, hh:mm a').format(report.createdAt!.toLocal()),
                      icon: Icons.calendar_today_outlined,
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 16),

            // ── SECTION 3: EMERGENCY CONTACT ──
            if (report.contactInfo != null && report.contactInfo!.isNotEmpty)
              _buildCardSection(
                title: isHindi ? 'आपातकालीन संपर्क' : 'Emergency Contact',
                icon: Icons.phone_in_talk,
                child: _buildDetailRow(
                  isHindi ? 'संपर्क नंबर' : 'Phone Number',
                  report.contactInfo!,
                  icon: Icons.phone,
                ),
              ),
            const SizedBox(height: 24),

            // ── SECTION 4: SIGHTINGS TIMELINE BUTTON ──
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                minimumSize: const Size(double.infinity, 50),
                side: const BorderSide(color: AppTheme.secondaryCyan),
                foregroundColor: AppTheme.secondaryCyan,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              icon: const Icon(Icons.map_outlined),
              label: Text(
                isHindi ? 'साइटिंग टाइमलाइन व मैप देखें' : 'View Sighting Timeline & Map',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
              ),
              onPressed: () {
                context.push(AppRoutes.reportTimelinePath(report.id));
              },
            ),
            const SizedBox(height: 16),

            // ── CASE ACTION BUTTONS ──
            if (report.status == ReportStatus.active || report.status == ReportStatus.processing) ...[
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  minimumSize: const Size(double.infinity, 50),
                  backgroundColor: AppTheme.successGreen,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                icon: const Icon(Icons.check_circle_outline),
                label: Text(
                  isHindi ? 'मिल गया चिह्नित करें' : 'Mark Person as Found',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
                onPressed: () => _showMarkAsFoundDialog(context, isHindi),
              ),
              const SizedBox(height: 10),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  minimumSize: const Size(double.infinity, 50),
                  backgroundColor: AppTheme.darkCard,
                  foregroundColor: AppTheme.errorRed,
                  side: const BorderSide(color: AppTheme.errorRed),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                icon: const Icon(Icons.archive_outlined),
                label: Text(
                  isHindi ? 'केस बंद करें (एम्बेडिंग्स निष्क्रिय करें)' : 'Close Case & Deactivate Biometrics',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                onPressed: () => _showCloseCaseDialog(context, isHindi),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildPhotoGallery(MissingPersonModel report, bool isHindi) {
    if (report.photos.isEmpty) {
      return Container(
        height: 200,
        width: double.infinity,
        decoration: BoxDecoration(
          color: AppTheme.darkCard,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.darkCardBorder),
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.no_photography_outlined, size: 48, color: AppTheme.darkTextSecondary),
              const SizedBox(height: 8),
              Text(
                isHindi ? 'कोई तस्वीर संलग्न नहीं' : 'No photos attached',
                style: const TextStyle(color: AppTheme.darkTextSecondary),
              ),
            ],
          ),
        ),
      );
    }

    final currentIndex = _selectedPhotoIndex.clamp(0, report.photos.length - 1);
    final activePhoto = report.photos[currentIndex];
    final displayUrl = activePhoto.faceCropUrl ?? activePhoto.originalUrl;

    return Column(
      children: [
        // Main Active Photo Display with Hero View
        Container(
          height: 240,
          width: double.infinity,
          decoration: BoxDecoration(
            color: AppTheme.darkCard,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: activePhoto.isPrimary ? AppTheme.primaryLight : AppTheme.darkCardBorder,
              width: activePhoto.isPrimary ? 2 : 1,
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: Stack(
            fit: StackFit.expand,
            children: [
              CachedNetworkImage(
                imageUrl: displayUrl,
                fit: BoxFit.contain,
                placeholder: (context, url) => Container(
                  color: AppTheme.darkSurface,
                  child: const Center(
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppTheme.primaryLight,
                    ),
                  ),
                ),
                errorWidget: (context, url, error) => Container(
                  color: AppTheme.darkSurface,
                  child: const Center(
                    child: Icon(Icons.broken_image, color: AppTheme.darkTextSecondary, size: 40),
                  ),
                ),
              ),

              // Top Primary Badge
              if (activePhoto.isPrimary)
                Positioned(
                  top: 10,
                  left: 10,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppTheme.accentAmber,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.star, size: 14, color: Colors.black),
                        const SizedBox(width: 4),
                        Text(
                          isHindi ? 'मुख्य संदर्भ' : 'PRIMARY REFERENCE',
                          style: const TextStyle(
                            color: Colors.black,
                            fontSize: 10,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

              // Bottom Biometric Status Indicator
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  color: Colors.black.withOpacity(0.75),
                  child: Row(
                    children: [
                      _buildBiometricStatusIcon(activePhoto.processingStatus),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          _getBiometricStatusLabel(activePhoto.processingStatus, isHindi),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      if (activePhoto.faceCropPath != null)
                        Text(
                          isHindi ? 'फेस क्रॉप उपलब्ध' : 'Face Cropped',
                          style: const TextStyle(color: AppTheme.secondaryCyan, fontSize: 10),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),

        // Thumbnail Strip & Add Photo Slot
        SizedBox(
          height: 70,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: report.photos.length + (report.photos.length < 5 ? 1 : 0),
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              if (index < report.photos.length) {
                final photo = report.photos[index];
                final isSelected = index == _selectedPhotoIndex;
                final thumbUrl = photo.faceCropUrl ?? photo.originalUrl;

                return GestureDetector(
                  onTap: () => setState(() => _selectedPhotoIndex = index),
                  child: Container(
                    width: 70,
                    decoration: BoxDecoration(
                      color: AppTheme.darkCard,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: isSelected ? AppTheme.primaryLight : AppTheme.darkCardBorder,
                        width: isSelected ? 2.0 : 1.0,
                      ),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: CachedNetworkImage(
                      imageUrl: thumbUrl,
                      fit: BoxFit.cover,
                      placeholder: (_, __) => Container(color: AppTheme.darkSurface),
                      errorWidget: (_, __, ___) => const Icon(Icons.broken_image, size: 20),
                    ),
                  ),
                );
              } else {
                // Add Photo Button
                return GestureDetector(
                  onTap: _isUploadingPhoto ? null : () => _addAdditionalPhoto(context, isHindi),
                  child: Container(
                    width: 70,
                    decoration: BoxDecoration(
                      color: AppTheme.darkCard.withOpacity(0.6),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: AppTheme.primaryBlue.withOpacity(0.5)),
                    ),
                    child: Center(
                      child: _isUploadingPhoto
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2, color: AppTheme.primaryLight),
                            )
                          : const Icon(Icons.add_a_photo, color: AppTheme.primaryLight, size: 22),
                    ),
                  ),
                );
              }
            },
          ),
        ),
      ],
    );
  }

  Widget _buildBiometricStatusIcon(PhotoProcessingStatus status) {
    switch (status) {
      case PhotoProcessingStatus.success:
        return const Icon(Icons.verified, color: AppTheme.successGreen, size: 14);
      case PhotoProcessingStatus.pending:
        return const Icon(Icons.hourglass_empty, color: AppTheme.accentAmber, size: 14);
      case PhotoProcessingStatus.noFace:
      case PhotoProcessingStatus.failed:
        return const Icon(Icons.error_outline, color: AppTheme.errorRed, size: 14);
    }
  }

  String _getBiometricStatusLabel(PhotoProcessingStatus status, bool isHindi) {
    switch (status) {
      case PhotoProcessingStatus.success:
        return isHindi ? 'बायोमेट्रिक 512-D एम्बेडिंग तैयार' : 'Biometric 512-D Embedding Active';
      case PhotoProcessingStatus.pending:
        return isHindi ? 'प्रोसेसिंग जारी...' : 'Processing Facial Biometrics...';
      case PhotoProcessingStatus.noFace:
        return isHindi ? 'तस्वीर में कोई स्पष्ट चेहरा नहीं मिला' : 'No Clear Face Detected';
      case PhotoProcessingStatus.failed:
        return isHindi ? 'बायोमेट्रिक निष्कर्षण विफल' : 'Biometric Extraction Failed';
    }
  }

  Widget _buildStatusDescriptionBanner(ReportStatus status, bool isHindi) {
    final String text;
    final Color color;
    final IconData icon;

    switch (status) {
      case ReportStatus.active:
        text = isHindi
            ? 'सक्रिय: सभी सीसीटीवी कैमरा एज नोड्स इस व्यक्ति के लिए लाइव बायोमेट्रिक स्कैनिंग कर रहे हैं।'
            : 'Active: Edge CCTV nodes are scanning live video streams against biometric embeddings.';
        color = AppTheme.successGreen;
        icon = Icons.radar;
        break;
      case ReportStatus.found:
        text = isHindi
            ? 'मिल गया: यह व्यक्ति सुरक्षित मिल गया है। केस सफलतापूर्वक सुलझा लिया गया है।'
            : 'Found: The person has been located and confirmed safe.';
        color = AppTheme.primaryLight;
        icon = Icons.check_circle_outline;
        break;
      case ReportStatus.processing:
        text = isHindi
            ? 'प्रक्रियाधीन: तस्वीरों से चेहरे की पहचान और एम्बेडिंग्स निकाली जा रही हैं।'
            : 'Processing: Biometric embeddings are being generated from uploaded photos.';
        color = AppTheme.accentAmber;
        icon = Icons.hourglass_top_outlined;
        break;
      case ReportStatus.closed:
        text = isHindi
            ? 'केस बंद: इस मामले की जांच बंद कर दी गई है और बायोमेट्रिक स्कैनिंग निष्क्रिय है।'
            : 'Closed: This case investigation has ended and biometric monitoring is deactivated.';
        color = const Color(0xFF94A3B8);
        icon = Icons.archive_outlined;
        break;
    }

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                color: color,
                fontSize: 12.5,
                height: 1.4,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCardSection({
    required String title,
    required IconData icon,
    required Widget child,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.darkCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.darkCardBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 18, color: AppTheme.primaryLight),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.darkTextPrimary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          child,
        ],
      ),
    );
  }

  Widget _buildDetailRow(
    String label,
    String value, {
    IconData? icon,
    bool isMultiline = false,
  }) {
    return Row(
      crossAxisAlignment:
          isMultiline ? CrossAxisAlignment.start : CrossAxisAlignment.center,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 14, color: AppTheme.darkTextSecondary),
          const SizedBox(width: 6),
        ],
        SizedBox(
          width: 110,
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 13,
              color: AppTheme.darkTextSecondary,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              fontSize: 13.5,
              fontWeight: FontWeight.w600,
              color: AppTheme.darkTextPrimary,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildDivider() {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Divider(
        height: 1,
        color: AppTheme.darkCardBorder.withOpacity(0.5),
      ),
    );
  }
}
