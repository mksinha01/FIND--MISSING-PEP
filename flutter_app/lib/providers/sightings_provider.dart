import 'dart:developer' as developer;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/sighting_model.dart';
import '../services/api_service.dart';
import '../services/sse_service.dart';
import 'auth_provider.dart';

/// Provider for listing all sightings for a specific missing person report
final reportSightingsProvider =
    FutureProvider.family<List<SightingModel>, String>((ref, reportId) async {
  final apiService = ref.watch(apiServiceProvider);

  // Listen to SSE stream to auto-refresh sightings on live event
  ref.listen<AsyncValue<SseEvent>>(sseEventsProvider, (_, next) {
    next.whenData((event) {
      if (event.event == 'sighting' &&
          event.data['person_id'] == reportId) {
        developer.log('Live SSE sighting detected for $reportId. Refreshing sightings...', name: 'SightingsProvider');
        ref.invalidateSelf();
      }
    });
  });

  return await apiService.getSightingsForReport(reportId);
});

/// Provider for chronological movement timeline and GPS breadcrumbs of a report
final reportTimelineProvider =
    FutureProvider.family<PersonTimelineModel, String>((ref, reportId) async {
  final apiService = ref.watch(apiServiceProvider);

  // Listen to SSE stream to auto-refresh timeline on live event
  ref.listen<AsyncValue<SseEvent>>(sseEventsProvider, (_, next) {
    next.whenData((event) {
      if (event.event == 'sighting' &&
          event.data['person_id'] == reportId) {
        developer.log('Live SSE sighting for $reportId. Refreshing timeline...', name: 'TimelineProvider');
        ref.invalidateSelf();
      }
    });
  });

  return await apiService.getTimelineForReport(reportId);
});

/// Provider for detailed inspection of a single sighting
final sightingDetailProvider =
    FutureProvider.family<SightingModel, String>((ref, sightingId) async {
  final apiService = ref.watch(apiServiceProvider);
  return await apiService.getSightingById(sightingId);
});

/// State of an active sighting verification/review action
class SightingActionState {
  final bool isLoading;
  final String? successMessage;
  final String? errorMessage;
  final SightingModel? updatedSighting;

  const SightingActionState({
    this.isLoading = false,
    this.successMessage,
    this.errorMessage,
    this.updatedSighting,
  });

  SightingActionState copyWith({
    bool? isLoading,
    String? successMessage,
    String? errorMessage,
    SightingModel? updatedSighting,
  }) {
    return SightingActionState(
      isLoading: isLoading ?? this.isLoading,
      successMessage: successMessage,
      errorMessage: errorMessage,
      updatedSighting: updatedSighting ?? this.updatedSighting,
    );
  }
}

/// Notifier handling operator verification (confirm/reject) actions on sightings
class SightingActionsNotifier extends StateNotifier<SightingActionState> {
  final Ref _ref;

  SightingActionsNotifier(this._ref) : super(const SightingActionState());

  ApiService get _api => _ref.read(apiServiceProvider);

  /// Confirm a sighting match
  Future<SightingModel?> confirmSighting(
    String sightingId, {
    String? reviewNotes,
    String? personId,
  }) async {
    state = state.copyWith(isLoading: true, errorMessage: null, successMessage: null);
    try {
      final updated = await _api.confirmSighting(sightingId, reviewNotes: reviewNotes);
      state = SightingActionState(
        isLoading: false,
        successMessage: 'Sighting verified and confirmed successfully.',
        updatedSighting: updated,
      );

      // Invalidate relevant cached providers
      _ref.invalidate(sightingDetailProvider(sightingId));
      if (personId != null && personId.isNotEmpty) {
        _ref.invalidate(reportSightingsProvider(personId));
        _ref.invalidate(reportTimelineProvider(personId));
      }

      return updated;
    } catch (e) {
      state = SightingActionState(
        isLoading: false,
        errorMessage: e.toString(),
      );
      rethrow;
    }
  }

  /// Reject a false-positive sighting
  Future<SightingModel?> rejectSighting(
    String sightingId, {
    String? reviewNotes,
    String? personId,
  }) async {
    state = state.copyWith(isLoading: true, errorMessage: null, successMessage: null);
    try {
      final updated = await _api.rejectSighting(sightingId, reviewNotes: reviewNotes);
      state = SightingActionState(
        isLoading: false,
        successMessage: 'Sighting dismissed / marked as false positive.',
        updatedSighting: updated,
      );

      // Invalidate relevant cached providers
      _ref.invalidate(sightingDetailProvider(sightingId));
      if (personId != null && personId.isNotEmpty) {
        _ref.invalidate(reportSightingsProvider(personId));
        _ref.invalidate(reportTimelineProvider(personId));
      }

      return updated;
    } catch (e) {
      state = SightingActionState(
        isLoading: false,
        errorMessage: e.toString(),
      );
      rethrow;
    }
  }
}

/// Provider for SightingActionsNotifier
final sightingActionsProvider =
    StateNotifierProvider<SightingActionsNotifier, SightingActionState>((ref) {
  return SightingActionsNotifier(ref);
});
