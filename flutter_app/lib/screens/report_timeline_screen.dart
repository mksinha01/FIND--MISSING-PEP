import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../config/theme.dart';
import '../providers/auth_provider.dart';
import '../providers/sightings_provider.dart';
import '../widgets/timeline_widget.dart';

/// Screen hosting the interactive Map Timeline for a specific Missing Person Report
class ReportTimelineScreen extends ConsumerWidget {
  final String reportId;

  const ReportTimelineScreen({
    super.key,
    required this.reportId,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';
    final timelineAsync = ref.watch(reportTimelineProvider(reportId));

    return Scaffold(
      appBar: AppBar(
        title: Text(isHindi ? 'मूवमेंट टाइमलाइन व मैप' : 'Movement Timeline & Map'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: isHindi ? 'रिफ्रेश' : 'Refresh',
            onPressed: () => ref.invalidate(reportTimelineProvider(reportId)),
          ),
        ],
      ),
      body: timelineAsync.when(
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppTheme.primaryLight),
        ),
        error: (err, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 54, color: AppTheme.errorRed),
                const SizedBox(height: 16),
                Text(
                  isHindi ? 'टाइमलाइन लोड करने में विफल' : 'Failed to Load Timeline',
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
                  onPressed: () => ref.invalidate(reportTimelineProvider(reportId)),
                  icon: const Icon(Icons.refresh),
                  label: Text(isHindi ? 'पुनः प्रयास करें' : 'Retry'),
                ),
              ],
            ),
          ),
        ),
        data: (timeline) => TimelineWidget(
          timeline: timeline,
          isHindi: isHindi,
        ),
      ),
    );
  }
}
