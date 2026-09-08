import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../models/missing_person_model.dart';
import '../models/sighting_model.dart';
import '../providers/auth_provider.dart';
import '../providers/reports_provider.dart';
import '../providers/sightings_provider.dart';
import '../widgets/similarity_gauge.dart';
import '../widgets/status_badge.dart';

/// Screen for forensic evidence review of a specific CCTV sighting
class SightingDetailScreen extends ConsumerStatefulWidget {
  final String sightingId;

  const SightingDetailScreen({
    super.key,
    required this.sightingId,
  });

  @override
  ConsumerState<SightingDetailScreen> createState() => _SightingDetailScreenState();
}

class _SightingDetailScreenState extends ConsumerState<SightingDetailScreen> {
  final TransformationController _transformationController = TransformationController();

  Future<void> _refresh() async {
    ref.invalidate(sightingDetailProvider(widget.sightingId));
  }

  Future<void> _showConfirmDialog(
    BuildContext context,
    SightingModel sighting,
    bool isHindi,
  ) async {
    final notesController = TextEditingController();
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
            const Icon(Icons.verified, color: AppTheme.successGreen),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                isHindi ? 'मैच की पुष्टि करें?' : 'Confirm Verified Match?',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              isHindi
                  ? 'क्या आप पुष्टि करते हैं कि यह सीसीटीवी फुटेज लापता व्यक्ति से मेल खाता है?'
                  : 'Are you sure you want to verify and confirm this CCTV sighting match?',
              style: const TextStyle(color: AppTheme.darkTextSecondary, height: 1.4),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: notesController,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: isHindi ? 'समीक्षा टिप्पणी (वैकल्पिक)' : 'Review Notes (Optional)',
                hintText: isHindi ? 'जैसे कपड़े, पहचान चिह्न...' : 'e.g. Clothes matched, spotted moving east...',
              ),
            ),
          ],
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
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.successGreen),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(isHindi ? 'हाँ, पुष्टि करें' : 'Confirm Match'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        await ref.read(sightingActionsProvider.notifier).confirmSighting(
              widget.sightingId,
              reviewNotes: notesController.text.trim().isNotEmpty ? notesController.text.trim() : null,
              personId: sighting.personId,
            );

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                isHindi ? 'साइटिंग सफलतापूर्वक सत्यापित की गई!' : 'Sighting confirmed and verified!',
              ),
              backgroundColor: AppTheme.darkCard,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(isHindi ? 'त्रुटि: $e' : 'Failed to confirm match: $e'),
              backgroundColor: AppTheme.errorRed,
            ),
          );
        }
      }
    }
  }

  Future<void> _showRejectDialog(
    BuildContext context,
    SightingModel sighting,
    bool isHindi,
  ) async {
    final notesController = TextEditingController();
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
            const Icon(Icons.cancel_outlined, color: AppTheme.errorRed),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                isHindi ? 'गलत मैच खारिज करें?' : 'Dismiss / Reject Match?',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              isHindi
                  ? 'क्या आप इस साइटिंग को गलत पहचान (फॉल्स पॉजिटिव) मानकर खारिज करना चाहते हैं?'
                  : 'Are you sure you want to dismiss this sighting as a false alarm / non-match?',
              style: const TextStyle(color: AppTheme.darkTextSecondary, height: 1.4),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: notesController,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: isHindi ? 'खारिज करने का कारण (वैकल्पिक)' : 'Rejection Reason (Optional)',
                hintText: isHindi ? 'जैसे भिन्न चेहरा, गलत व्यक्ति...' : 'e.g. Different person, different height...',
              ),
            ),
          ],
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
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.errorRed),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(isHindi ? 'खारिज करें' : 'Reject Match'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        await ref.read(sightingActionsProvider.notifier).rejectSighting(
              widget.sightingId,
              reviewNotes: notesController.text.trim().isNotEmpty ? notesController.text.trim() : null,
              personId: sighting.personId,
            );

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                isHindi ? 'साइटिंग खारिज कर दी गई।' : 'Sighting dismissed as false positive.',
              ),
              backgroundColor: AppTheme.darkCard,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(isHindi ? 'त्रुटि: $e' : 'Failed to reject match: $e'),
              backgroundColor: AppTheme.errorRed,
            ),
          );
        }
      }
    }
  }

  void _openFullFrameViewer(BuildContext context, String fullFrameUrl) {
    showDialog(
      context: context,
      builder: (ctx) => Dialog.fullscreen(
        backgroundColor: Colors.black,
        child: Stack(
          children: [
            Center(
              child: InteractiveViewer(
                transformationController: _transformationController,
                minScale: 0.5,
                maxScale: 6.0,
                child: CachedNetworkImage(
                  imageUrl: fullFrameUrl,
                  fit: BoxFit.contain,
                  placeholder: (_, __) => const Center(
                    child: CircularProgressIndicator(color: AppTheme.primaryLight),
                  ),
                  errorWidget: (_, __, ___) => const Center(
                    child: Icon(Icons.broken_image, color: Colors.white54, size: 64),
                  ),
                ),
              ),
            ),
            Positioned(
              top: 40,
              right: 20,
              child: IconButton(
                icon: const Icon(Icons.close, color: Colors.white, size: 30),
                onPressed: () => Navigator.pop(ctx),
              ),
            ),
            Positioned(
              bottom: 30,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  decoration: BoxDecoration(
                    color: Colors.black.withOpacity(0.7),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Text(
                    'Pinch to Zoom • Drag to Pan',
                    style: TextStyle(color: Colors.white70, fontSize: 13),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';
    final sightingAsync = ref.watch(sightingDetailProvider(widget.sightingId));

    return Scaffold(
      appBar: AppBar(
        title: Text(isHindi ? 'साइटिंग साक्ष्य समीक्षा' : 'Sighting Evidence Review'),
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
      body: sightingAsync.when(
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
                  isHindi ? 'साइटिंग लोड करने में त्रुटि' : 'Failed to Load Sighting',
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
        data: (sighting) => _buildContent(context, sighting, isHindi),
      ),
    );
  }

  Widget _buildContent(BuildContext context, SightingModel sighting, bool isHindi) {
    // Optionally fetch associated person report for side-by-side primary photo
    final personReportAsync = ref.watch(reportDetailProvider(sighting.personId));
    final String? registeredPhotoUrl = personReportAsync.asData?.value.primaryPhotoUrl;

    final formattedTime = DateFormat('EEEE, dd MMM yyyy, hh:mm:ss a').format(sighting.detectedAt.toLocal());
    final isActionLoading = ref.watch(sightingActionsProvider).isLoading;

    return RefreshIndicator(
      color: AppTheme.primaryLight,
      backgroundColor: AppTheme.darkSurface,
      onRefresh: _refresh,
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 40),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── TOP COMPARISON CARD: REGISTERED PORTRAIT VS CCTV FACE CROP ──
            _buildComparisonSection(sighting, registeredPhotoUrl, isHindi),
            const SizedBox(height: 20),

            // ── SIMILARITY GAUGE & CONFIDENCE BANNER ──
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppTheme.darkCard,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppTheme.darkCardBorder),
              ),
              child: Row(
                children: [
                  SimilarityGauge(
                    similarityScore: sighting.similarityScore,
                    size: SimilarityGaugeSize.medium,
                    isHindi: isHindi,
                  ),
                  const SizedBox(width: 20),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          isHindi ? 'बायोमेट्रिक मैच स्कोर' : 'Biometric Match Score',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                            color: AppTheme.darkTextPrimary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          isHindi
                              ? '512-आयामी ArcFace एम्बेडिंग कोसाइन समानता पर आधारित।'
                              : 'Calculated using 512-D ArcFace facial embedding cosine distance.',
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppTheme.darkTextSecondary,
                            height: 1.3,
                          ),
                        ),
                        const SizedBox(height: 8),
                        if (sighting.numFramesMatched != null)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: AppTheme.primaryBlue.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              '${sighting.numFramesMatched} ${isHindi ? "लगातार फ्रेम सत्यापित" : "temporal frames confirmed"}',
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: AppTheme.primaryLight,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // ── FULL CCTV FRAME VIEWER WITH PINCH-TO-ZOOM ──
            _buildFullFrameSection(context, sighting, isHindi),
            const SizedBox(height: 20),

            // ── CAMERA & METADATA SECTION ──
            _buildMetadataSection(sighting, formattedTime, isHindi),
            const SizedBox(height: 20),

            // ── TIMELINE ROUTE SHORTCUT BUTTON ──
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                minimumSize: const Size(double.infinity, 48),
                side: const BorderSide(color: AppTheme.secondaryCyan),
                foregroundColor: AppTheme.secondaryCyan,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              icon: const Icon(Icons.alt_route),
              label: Text(
                isHindi ? 'आवागमन टाइमलाइन और मैप देखें' : 'View Sighting Trail on Map',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              onPressed: () {
                context.push(AppRoutes.reportTimelinePath(sighting.personId));
              },
            ),
            const SizedBox(height: 24),

            // ── OPERATOR VERIFICATION ACTIONS ──
            if (sighting.status == SightingStatus.pending) ...[
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  minimumSize: const Size(double.infinity, 50),
                  backgroundColor: AppTheme.successGreen,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                icon: isActionLoading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                      )
                    : const Icon(Icons.check_circle),
                label: Text(
                  isHindi ? 'मैच की पुष्टि करें (सत्यापित)' : 'Confirm Verified Match',
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                ),
                onPressed: isActionLoading ? null : () => _showConfirmDialog(context, sighting, isHindi),
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
                icon: const Icon(Icons.cancel_outlined),
                label: Text(
                  isHindi ? 'गलत पहचान खारिज करें' : 'Dismiss / Reject Match',
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                ),
                onPressed: isActionLoading ? null : () => _showRejectDialog(context, sighting, isHindi),
              ),
            ] else ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: sighting.statusColor.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: sighting.statusColor.withOpacity(0.4)),
                ),
                child: Row(
                  children: [
                    Icon(
                      sighting.status == SightingStatus.confirmed ? Icons.verified : Icons.cancel,
                      color: sighting.statusColor,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            sighting.status == SightingStatus.confirmed
                                ? (isHindi ? 'सत्यापित मैच' : 'Confirmed Match')
                                : (isHindi ? 'खारिज किया गया' : 'Dismissed / Rejected'),
                            style: TextStyle(
                              color: sighting.statusColor,
                              fontWeight: FontWeight.bold,
                              fontSize: 14,
                            ),
                          ),
                          if (sighting.reviewNotes != null && sighting.reviewNotes!.isNotEmpty) ...[
                            const SizedBox(height: 4),
                            Text(
                              '${isHindi ? "नोट्स: " : "Notes: "}${sighting.reviewNotes}',
                              style: const TextStyle(color: AppTheme.darkTextSecondary, fontSize: 12),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildComparisonSection(
    SightingModel sighting,
    String? registeredPhotoUrl,
    bool isHindi,
  ) {
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
          Text(
            isHindi ? 'चेहरे की तुलना (साइड-बाय-साइड)' : 'Side-by-Side Photo Comparison',
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppTheme.darkTextPrimary,
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              // Left: Registered Person Photo
              Expanded(
                child: Column(
                  children: [
                    Container(
                      height: 150,
                      decoration: BoxDecoration(
                        color: AppTheme.darkSurface,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppTheme.darkCardBorder),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: registeredPhotoUrl != null
                          ? CachedNetworkImage(
                              imageUrl: registeredPhotoUrl,
                              fit: BoxFit.cover,
                              placeholder: (_, __) => const Center(
                                child: CircularProgressIndicator(strokeWidth: 2),
                              ),
                              errorWidget: (_, __, ___) => const Center(
                                child: Icon(Icons.person, color: AppTheme.darkTextSecondary, size: 40),
                              ),
                            )
                          : const Center(
                              child: Icon(Icons.person, color: AppTheme.darkTextSecondary, size: 40),
                            ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      isHindi ? 'दर्ज संदर्भ फोटो' : 'Registered Reference',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.darkTextSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 14),

              // VS Divider Icon
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: AppTheme.darkSurface,
                  shape: BoxShape.circle,
                  border: Border.all(color: AppTheme.darkCardBorder),
                ),
                child: const Text(
                  'VS',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w900,
                    color: AppTheme.primaryLight,
                  ),
                ),
              ),
              const SizedBox(width: 14),

              // Right: CCTV Cropped Face
              Expanded(
                child: Column(
                  children: [
                    Container(
                      height: 150,
                      decoration: BoxDecoration(
                        color: AppTheme.darkSurface,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppTheme.primaryLight, width: 1.5),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: sighting.faceCropUrl != null
                          ? CachedNetworkImage(
                              imageUrl: sighting.faceCropUrl!,
                              fit: BoxFit.cover,
                              placeholder: (_, __) => const Center(
                                child: CircularProgressIndicator(strokeWidth: 2),
                              ),
                              errorWidget: (_, __, ___) => const Center(
                                child: Icon(Icons.videocam_outlined, color: AppTheme.darkTextSecondary, size: 40),
                              ),
                            )
                          : const Center(
                              child: Icon(Icons.videocam_outlined, color: AppTheme.darkTextSecondary, size: 40),
                            ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      isHindi ? 'सीसीटीवी फेस क्रॉप' : 'CCTV Face Crop',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.primaryLight,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFullFrameSection(
    BuildContext context,
    SightingModel sighting,
    bool isHindi,
  ) {
    final fullFrameUrl = sighting.fullFrameUrl;

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
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                isHindi ? 'पूर्ण सीसीटीवी दृश्य (ज़ूम करने योग्य)' : 'Full CCTV Scene (Zoomable)',
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.darkTextPrimary,
                ),
              ),
              if (fullFrameUrl != null)
                TextButton.icon(
                  icon: const Icon(Icons.fullscreen, size: 18),
                  label: Text(isHindi ? 'बड़ा करें' : 'Fullscreen'),
                  onPressed: () => _openFullFrameViewer(context, fullFrameUrl),
                ),
            ],
          ),
          const SizedBox(height: 10),

          // Zoomable Frame Preview Container
          Container(
            height: 220,
            width: double.infinity,
            decoration: BoxDecoration(
              color: Colors.black,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.darkCardBorder),
            ),
            clipBehavior: Clip.antiAlias,
            child: fullFrameUrl != null
                ? Stack(
                    fit: StackFit.expand,
                    children: [
                      InteractiveViewer(
                        minScale: 1.0,
                        maxScale: 4.0,
                        child: CachedNetworkImage(
                          imageUrl: fullFrameUrl,
                          fit: BoxFit.contain,
                          placeholder: (_, __) => const Center(
                            child: CircularProgressIndicator(color: AppTheme.primaryLight),
                          ),
                          errorWidget: (_, __, ___) => const Center(
                            child: Icon(Icons.broken_image, color: Colors.white38, size: 40),
                          ),
                        ),
                      ),
                      Positioned(
                        bottom: 8,
                        right: 8,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.black.withOpacity(0.7),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.pinch, size: 12, color: Colors.white70),
                              const SizedBox(width: 4),
                              Text(
                                isHindi ? 'ज़ूम करें' : 'Pinch to Zoom',
                                style: const TextStyle(color: Colors.white70, fontSize: 10),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  )
                : const Center(
                    child: Text(
                      'No full scene capture available',
                      style: TextStyle(color: AppTheme.darkTextSecondary),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetadataSection(
    SightingModel sighting,
    String formattedTime,
    bool isHindi,
  ) {
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
              const Icon(Icons.info_outline, size: 18, color: AppTheme.primaryLight),
              const SizedBox(width: 8),
              Text(
                isHindi ? 'कैमरा व स्थान मेटाडेटा' : 'Camera & Detection Metadata',
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.darkTextPrimary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          _buildMetaRow(
            isHindi ? 'कैमरा' : 'Camera Name',
            sighting.cameraLocation ?? 'CCTV Camera #${sighting.cameraId.substring(0, 8)}',
            Icons.videocam,
          ),
          const Divider(height: 16),
          _buildMetaRow(
            isHindi ? 'डिटेक्शन समय' : 'Detection Timestamp',
            formattedTime,
            Icons.access_time,
          ),
          if (sighting.latitude != null && sighting.longitude != null) ...[
            const Divider(height: 16),
            _buildMetaRow(
              isHindi ? 'जीपीएस निर्देशांक' : 'GPS Coordinates',
              '${sighting.latitude!.toStringAsFixed(6)}, ${sighting.longitude!.toStringAsFixed(6)}',
              Icons.location_on,
            ),
          ],
          const Divider(height: 16),
          _buildMetaRow(
            isHindi ? 'एज एजेंट आईडी' : 'Edge Agent Node',
            sighting.agentId,
            Icons.memory,
          ),
        ],
      ),
    );
  }

  Widget _buildMetaRow(String label, String value, IconData icon) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 16, color: AppTheme.darkTextSecondary),
        const SizedBox(width: 8),
        Text(
          label,
          style: const TextStyle(fontSize: 13, color: AppTheme.darkTextSecondary),
        ),
        const Spacer(),
        Expanded(
          flex: 2,
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: AppTheme.darkTextPrimary,
            ),
          ),
        ),
      ],
    );
  }
}
