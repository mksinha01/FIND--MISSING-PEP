import 'dart:convert';
import 'dart:developer' as developer;
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/enums.dart';
import '../models/missing_person_model.dart';
import '../services/api_service.dart';
import '../services/image_service.dart';
import 'auth_provider.dart';

/// Provider exposing the central ApiService instance
final apiServiceProvider = Provider<ApiService>((ref) {
  final authService = ref.watch(authServiceProvider);
  return ApiService(authService: authService);
});

/// Immutable State Model for Missing Person Reports
class ReportsState {
  final List<MissingPersonModel> reports;
  final String statusFilter;
  final String searchQuery;
  final bool isLoading;
  final bool isSubmitting;
  final String? errorMessage;
  final int page;
  final int total;
  final bool hasMore;

  const ReportsState({
    this.reports = const [],
    this.statusFilter = 'ALL',
    this.searchQuery = '',
    this.isLoading = false,
    this.isSubmitting = false,
    this.errorMessage,
    this.page = 1,
    this.total = 0,
    this.hasMore = false,
  });

  ReportsState copyWith({
    List<MissingPersonModel>? reports,
    String? statusFilter,
    String? searchQuery,
    bool? isLoading,
    bool? isSubmitting,
    String? errorMessage,
    bool clearError = false,
    int? page,
    int? total,
    bool? hasMore,
  }) {
    return ReportsState(
      reports: reports ?? this.reports,
      statusFilter: statusFilter ?? this.statusFilter,
      searchQuery: searchQuery ?? this.searchQuery,
      isLoading: isLoading ?? this.isLoading,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      page: page ?? this.page,
      total: total ?? this.total,
      hasMore: hasMore ?? this.hasMore,
    );
  }

  /// Filtered reports based on active filter and search text
  List<MissingPersonModel> get filteredReports {
    var list = reports;

    // Status Filter
    if (statusFilter != 'ALL') {
      list = list.where((r) => r.status.value == statusFilter).toList();
    }

    // Search Query Filter
    if (searchQuery.isNotEmpty) {
      final q = searchQuery.toLowerCase();
      list = list.where((r) {
        final matchName = r.fullName.toLowerCase().contains(q);
        final matchLocation =
            r.lastSeenLocation?.toLowerCase().contains(q) ?? false;
        final matchDescription =
            r.description?.toLowerCase().contains(q) ?? false;
        return matchName || matchLocation || matchDescription;
      }).toList();
    }

    return list;
  }
}

/// StateNotifier for Reports CRUD and Atomic Multipart Submissions
class ReportsNotifier extends StateNotifier<ReportsState> {
  final ApiService _apiService;

  ReportsNotifier({required ApiService apiService})
      : _apiService = apiService,
        super(const ReportsState()) {
    fetchMyReports();
  }

  /// Fetch user reports with pagination and status filter
  Future<void> fetchMyReports({
    String? statusFilter,
    bool refresh = false,
  }) async {
    if (state.isLoading && !refresh) return;

    final targetPage = refresh ? 1 : state.page;
    final filter = statusFilter ?? state.statusFilter;

    state = state.copyWith(
      isLoading: true,
      clearError: true,
      statusFilter: filter,
      page: targetPage,
    );

    try {
      final response = await _apiService.getMyReports(
        statusFilter: filter == 'ALL' ? null : filter,
        page: targetPage,
      );

      final newReports = refresh
          ? response.items
          : [...state.reports, ...response.items];

      state = state.copyWith(
        reports: newReports,
        total: response.total,
        isLoading: false,
        hasMore: newReports.length < response.total,
      );
    } catch (e) {
      developer.log('Error fetching reports: $e', name: 'ReportsNotifier');
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString(),
      );
    }
  }

  /// Search reports locally
  void setSearchQuery(String query) {
    state = state.copyWith(searchQuery: query);
  }

  /// Change status filter
  void setStatusFilter(String filter) {
    state = state.copyWith(statusFilter: filter);
    fetchMyReports(statusFilter: filter, refresh: true);
  }

  /// Submit a Missing Person report atomically in a single multipart POST request
  /// Submits JSON `report_data` + 1-5 binary `photos`
  Future<MissingPersonModel> submitReportAtomic({
    required Map<String, dynamic> metadata,
    required List<CompressedPhotoItem> photos,
  }) async {
    state = state.copyWith(isSubmitting: true, clearError: true);

    try {
      final formData = FormData();

      // 1. Attach JSON report_data string
      formData.fields.add(
        MapEntry('report_data', jsonEncode(metadata)),
      );

      // 2. Attach compressed photo files
      for (final photo in photos) {
        formData.files.add(
          MapEntry(
            'photos',
            photo.toMultipartFile(),
          ),
        );
      }

      developer.log(
        'Dispatching atomic multipart report creation with ${photos.length} photos...',
        name: 'ReportsNotifier',
      );

      final createdReport = await _apiService.createReportAtomic(formData);

      // Insert newly created report at the top of the list
      state = state.copyWith(
        reports: [createdReport, ...state.reports],
        total: state.total + 1,
        isSubmitting: false,
      );

      return createdReport;
    } catch (e) {
      developer.log('Atomic report submission failed: $e', name: 'ReportsNotifier');
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: e.toString(),
      );
      rethrow;
    }
  }

  /// Mark a missing person as FOUND
  Future<void> markAsFound(String reportId) async {
    try {
      final updated = await _apiService.updateReport(
        reportId,
        {'status': ReportStatus.found.value},
      );

      state = state.copyWith(
        reports: state.reports.map((r) => r.id == reportId ? updated : r).toList(),
      );
    } catch (e) {
      developer.log('Error marking report as found: $e', name: 'ReportsNotifier');
      state = state.copyWith(errorMessage: e.toString());
      rethrow;
    }
  }

  /// Soft-delete / close a report (sets status CLOSED, deactivates embeddings)
  Future<void> closeReport(String reportId) async {
    try {
      await _apiService.closeReport(reportId);

      // Update local report status to CLOSED
      state = state.copyWith(
        reports: state.reports.map((r) {
          if (r.id == reportId) {
            return MissingPersonModel(
              id: r.id,
              userId: r.userId,
              fullName: r.fullName,
              age: r.age,
              gender: r.gender,
              heightCm: r.heightCm,
              description: r.description,
              lastSeenLocation: r.lastSeenLocation,
              lastSeenTime: r.lastSeenTime,
              status: ReportStatus.closed,
              contactInfo: r.contactInfo,
              photos: r.photos,
              createdAt: r.createdAt,
              updatedAt: DateTime.now(),
            );
          }
          return r;
        }).toList(),
      );
    } catch (e) {
      developer.log('Error closing report: $e', name: 'ReportsNotifier');
      state = state.copyWith(errorMessage: e.toString());
      rethrow;
    }
  }

  /// Upload an additional photo to an existing report
  Future<PhotoModel> uploadAdditionalPhoto(
    String reportId,
    CompressedPhotoItem photo, {
    bool isPrimary = false,
  }) async {
    try {
      final formData = FormData();
      formData.files.add(
        MapEntry('file', photo.toMultipartFile()),
      );
      formData.fields.add(
        MapEntry('is_primary', isPrimary.toString()),
      );

      final newPhoto = await _apiService.uploadAdditionalPhoto(reportId, formData);

      // Refresh report in list
      final refreshedReport = await _apiService.getReportById(reportId);
      state = state.copyWith(
        reports: state.reports
            .map((r) => r.id == reportId ? refreshedReport : r)
            .toList(),
      );

      return newPhoto;
    } catch (e) {
      developer.log('Error uploading additional photo: $e', name: 'ReportsNotifier');
      rethrow;
    }
  }
}

/// Primary Riverpod StateNotifierProvider for Reports
final reportsProvider =
    StateNotifierProvider<ReportsNotifier, ReportsState>((ref) {
  final apiService = ref.watch(apiServiceProvider);
  return ReportsNotifier(apiService: apiService);
});

/// FutureProvider to fetch single report details by ID
final reportDetailProvider =
    FutureProvider.family<MissingPersonModel, String>((ref, reportId) async {
  final apiService = ref.watch(apiServiceProvider);
  return await apiService.getReportById(reportId);
});

/// Statistics provider calculating counts for Dashboard
final reportStatsProvider = Provider<Map<String, int>>((ref) {
  final reports = ref.watch(reportsProvider).reports;

  int active = 0;
  int found = 0;
  int processing = 0;
  int closed = 0;

  for (final r in reports) {
    switch (r.status) {
      case ReportStatus.active:
        active++;
        break;
      case ReportStatus.found:
        found++;
        break;
      case ReportStatus.processing:
        processing++;
        break;
      case ReportStatus.closed:
        closed++;
        break;
    }
  }

  return {
    'total': reports.length,
    'active': active,
    'found': found,
    'processing': processing,
    'closed': closed,
  };
});
