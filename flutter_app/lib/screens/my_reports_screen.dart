import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../providers/auth_provider.dart';
import '../providers/reports_provider.dart';
import '../widgets/report_card.dart';

/// Screen displaying and filtering all Missing Person reports filed by user
class MyReportsScreen extends ConsumerStatefulWidget {
  const MyReportsScreen({super.key});

  @override
  ConsumerState<MyReportsScreen> createState() => _MyReportsScreenState();
}

class _MyReportsScreenState extends ConsumerState<MyReportsScreen> {
  final _searchController = TextEditingController();
  String _selectedStatus = 'ALL';

  final List<String> _statusFilters = [
    'ALL',
    'ACTIVE',
    'FOUND',
    'PROCESSING',
    'CLOSED',
  ];

  @override
  void initState() {
    super.initState();
    // Refresh reports list on screen load
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(reportsProvider.notifier).fetchMyReports(refresh: true);
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  String _getFilterLabel(String filter, bool isHindi) {
    if (isHindi) {
      switch (filter) {
        case 'ALL':
          return 'सभी';
        case 'ACTIVE':
          return 'सक्रिय';
        case 'FOUND':
          return 'मिल गया';
        case 'PROCESSING':
          return 'प्रक्रियाधीन';
        case 'CLOSED':
          return 'बंद';
        default:
          return filter;
      }
    } else {
      switch (filter) {
        case 'ALL':
          return 'All';
        case 'ACTIVE':
          return 'Active';
        case 'FOUND':
          return 'Found';
        case 'PROCESSING':
          return 'Processing';
        case 'CLOSED':
          return 'Closed';
        default:
          return filter;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';
    final reportsState = ref.watch(reportsProvider);
    final filteredList = reportsState.filteredReports;

    return Scaffold(
      appBar: AppBar(
        title: Text(isHindi ? 'मेरी रिपोर्टें' : 'My Reports'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_circle_outline),
            tooltip: isHindi ? 'नई रिपोर्ट' : 'New Report',
            onPressed: () => context.push(AppRoutes.newReport),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: isHindi ? 'रिफ्रेश करें' : 'Refresh',
            onPressed: () {
              ref.read(reportsProvider.notifier).fetchMyReports(refresh: true);
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // ── SEARCH BAR ──
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: isHindi ? 'नाम या स्थान से खोजें...' : 'Search by name or location...',
                prefixIcon: const Icon(Icons.search, color: AppTheme.darkTextSecondary),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _searchController.clear();
                          ref.read(reportsProvider.notifier).setSearchQuery('');
                          setState(() {});
                        },
                      )
                    : null,
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              ),
              onChanged: (val) {
                ref.read(reportsProvider.notifier).setSearchQuery(val);
                setState(() {});
              },
            ),
          ),

          // ── HORIZONTAL STATUS FILTER CHIPS ──
          SizedBox(
            height: 44,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              scrollDirection: Axis.horizontal,
              itemCount: _statusFilters.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final filter = _statusFilters[index];
                final isSelected = _selectedStatus == filter;
                final label = _getFilterLabel(filter, isHindi);

                return ChoiceChip(
                  label: Text(label),
                  selected: isSelected,
                  selectedColor: AppTheme.primaryBlue,
                  backgroundColor: AppTheme.darkCard,
                  labelStyle: TextStyle(
                    color: isSelected ? Colors.white : AppTheme.darkTextSecondary,
                    fontSize: 13,
                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                  ),
                  side: BorderSide(
                    color: isSelected ? AppTheme.primaryLight : AppTheme.darkCardBorder,
                  ),
                  onSelected: (selected) {
                    if (selected) {
                      setState(() => _selectedStatus = filter);
                      ref.read(reportsProvider.notifier).setStatusFilter(filter);
                    }
                  },
                );
              },
            ),
          ),
          const SizedBox(height: 8),

          // ── REPORT LIST / EMPTY STATE / LOADING ──
          Expanded(
            child: RefreshIndicator(
              color: AppTheme.primaryLight,
              backgroundColor: AppTheme.darkSurface,
              onRefresh: () async {
                await ref.read(reportsProvider.notifier).fetchMyReports(refresh: true);
              },
              child: reportsState.isLoading && reportsState.reports.isEmpty
                  ? _buildLoadingState()
                  : filteredList.isEmpty
                      ? _buildEmptyState(isHindi)
                      : ListView.builder(
                          padding: const EdgeInsets.fromLTRB(16, 4, 16, 80),
                          itemCount: filteredList.length,
                          itemBuilder: (context, index) {
                            final report = filteredList[index];
                            return ReportCard(
                              report: report,
                              isHindi: isHindi,
                              onTap: () {
                                context.push(AppRoutes.reportDetailPath(report.id));
                              },
                            );
                          },
                        ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: AppTheme.primaryBlue,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: Text(
          isHindi ? 'नई रिपोर्ट' : 'New Report',
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        onPressed: () => context.push(AppRoutes.newReport),
      ),
    );
  }

  Widget _buildLoadingState() {
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: 4,
      itemBuilder: (context, index) {
        return Container(
          height: 90,
          margin: const EdgeInsets.symmetric(vertical: 6),
          decoration: BoxDecoration(
            color: AppTheme.darkCard.withOpacity(0.5),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.darkCardBorder),
          ),
          child: const Center(
            child: SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: AppTheme.primaryLight,
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildEmptyState(bool isHindi) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryBlue.withOpacity(0.1),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.person_search_outlined,
                        size: 64,
                        color: AppTheme.primaryLight,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      _selectedStatus == 'ALL'
                          ? (isHindi ? 'कोई रिपोर्ट दर्ज नहीं है' : 'No Reports Filed Yet')
                          : (isHindi ? 'इस श्रेणी में कोई रिपोर्ट नहीं है' : 'No reports in this category'),
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.darkTextPrimary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      isHindi
                          ? 'लापता व्यक्तियों के लिए एआई आधारित सीसीटीवी खोज सक्रिय करने हेतु नई रिपोर्ट दर्ज करें।'
                          : 'Submit a missing person report to activate automated Edge AI biometric matching across connected CCTV nodes.',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppTheme.darkTextSecondary,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton.icon(
                      icon: const Icon(Icons.add),
                      label: Text(isHindi ? 'रिपोर्ट दर्ज करें' : 'Submit New Report'),
                      onPressed: () => context.push(AppRoutes.newReport),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
