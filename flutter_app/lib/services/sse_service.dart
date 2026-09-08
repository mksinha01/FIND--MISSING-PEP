import 'dart:async';
import 'dart:convert';
import 'dart:developer' as developer;
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/constants.dart';
import 'auth_service.dart';
import '../providers/auth_provider.dart';

/// Typed representation of a Server-Sent Event from the backend
class SseEvent {
  final String event;
  final Map<String, dynamic> data;
  final String? id;
  final DateTime receivedAt;

  const SseEvent({
    required this.event,
    required this.data,
    this.id,
    required this.receivedAt,
  });

  String? get sightingId => data['sighting_id'] as String?;
  String? get notificationId => data['notification_id'] as String?;
  String? get title => data['title'] as String?;
  String? get body => data['body'] as String?;
  double? get similarityScore => (data['similarity_score'] as num?)?.toDouble() ??
      (data['similarity'] != null ? double.tryParse(data['similarity'].toString()) : null);

  factory SseEvent.fromRaw({
    required String eventName,
    required String rawData,
    String? id,
  }) {
    Map<String, dynamic> parsedData = {};
    if (rawData.isNotEmpty) {
      try {
        parsedData = jsonDecode(rawData) as Map<String, dynamic>;
      } catch (e) {
        parsedData = {'raw': rawData};
      }
    }
    return SseEvent(
      event: eventName.isEmpty ? 'message' : eventName,
      data: parsedData,
      id: id,
      receivedAt: DateTime.now(),
    );
  }

  @override
  String toString() => 'SseEvent(event: $event, data: $data, receivedAt: $receivedAt)';
}

/// Service managing the Server-Sent Events (SSE) live connection to the backend
class SseService {
  final IAuthService _authService;
  final String _baseUrl;

  final StreamController<SseEvent> _eventController =
      StreamController<SseEvent>.broadcast();

  Stream<SseEvent> get eventStream => _eventController.stream;

  bool _isDisposed = false;
  bool _isConnected = false;
  bool get isConnected => _isConnected;

  Dio? _dio;
  CancelToken? _cancelToken;
  Timer? _reconnectTimer;
  int _retryAttempt = 0;
  static const int _maxRetryDelaySeconds = 30;

  SseService({
    required IAuthService authService,
    String? baseUrl,
  })  : _authService = authService,
        _baseUrl = baseUrl ?? AppConstants.apiBaseUrl;

  /// Start the SSE listening loop
  void connect() {
    if (_isDisposed) return;
    _retryAttempt = 0;
    _startStream();
  }

  Future<void> _startStream() async {
    if (_isDisposed) return;

    _reconnectTimer?.cancel();
    _cancelToken?.cancel();
    _cancelToken = CancelToken();

    try {
      final token = await _authService.getIdToken();
      if (token == null || token.isEmpty) {
        developer.log('SSE connection skipped: No auth token available.', name: 'SSEService');
        _scheduleReconnect();
        return;
      }

      final url = '$_baseUrl${AppConstants.endpointEventsStream}';
      developer.log('Connecting to SSE stream at $url', name: 'SSEService');

      _dio = Dio(
        BaseOptions(
          responseType: ResponseType.stream,
          connectTimeout: const Duration(seconds: 20),
          receiveTimeout: const Duration(minutes: 10), // Long receive timeout for SSE
          headers: {
            'Accept': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Authorization': 'Bearer $token',
          },
        ),
      );

      final response = await _dio!.get<ResponseBody>(
        url,
        cancelToken: _cancelToken,
        queryParameters: {'token': token},
      );

      _isConnected = true;
      _retryAttempt = 0;
      developer.log('SSE stream connected successfully.', name: 'SSEService');

      final stream = response.data?.stream;
      if (stream == null) {
        throw Exception('SSE response stream is null');
      }

      String buffer = '';
      String currentEvent = 'message';
      String currentData = '';
      String? currentId;

      await for (final chunk in stream) {
        if (_isDisposed) break;

        final text = utf8.decode(chunk);
        buffer += text;

        final lines = buffer.split('\n');
        buffer = lines.removeLast(); // Keep incomplete trailing chunk

        for (final rawLine in lines) {
          final line = rawLine.trim();

          // Keepalive / Ping comment check
          if (line.startsWith(':')) {
            developer.log('SSE heartbeat: $line', name: 'SSEService');
            continue;
          }

          // Empty line indicates event dispatch boundary
          if (line.isEmpty) {
            if (currentData.isNotEmpty) {
              final event = SseEvent.fromRaw(
                eventName: currentEvent,
                rawData: currentData,
                id: currentId,
              );
              developer.log('SSE Event received: ${event.event}', name: 'SSEService');
              _eventController.add(event);

              // Reset for next event
              currentEvent = 'message';
              currentData = '';
              currentId = null;
            }
            continue;
          }

          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            final dataSlice = line.substring(5).trim();
            if (currentData.isEmpty) {
              currentData = dataSlice;
            } else {
              currentData += '\n$dataSlice';
            }
          } else if (line.startsWith('id:')) {
            currentId = line.substring(3).trim();
          }
        }
      }
    } catch (e) {
      _isConnected = false;
      if (!_isDisposed) {
        developer.log('SSE stream disconnected / error: $e', name: 'SSEService');
        _scheduleReconnect();
      }
    }
  }

  void _scheduleReconnect() {
    if (_isDisposed) return;
    _isConnected = false;
    _retryAttempt++;
    // Exponential backoff: 2, 4, 8, 16... capped at 30 seconds
    final delaySeconds = (1 << _retryAttempt).clamp(2, _maxRetryDelaySeconds);
    developer.log(
      'Scheduling SSE reconnect attempt $_retryAttempt in $delaySeconds seconds...',
      name: 'SSEService',
    );
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(Duration(seconds: delaySeconds), () {
      _startStream();
    });
  }

  /// Disconnect and release all resources
  void dispose() {
    _isDisposed = true;
    _isConnected = false;
    _reconnectTimer?.cancel();
    _cancelToken?.cancel();
    _eventController.close();
  }
}

/// Riverpod provider for SseService instance
final sseServiceProvider = Provider<SseService>((ref) {
  final authService = ref.watch(authServiceProvider);
  final sse = SseService(authService: authService);

  // Auto-connect if user is currently authenticated
  final authState = ref.watch(authProvider);
  if (authState.isAuthenticated) {
    sse.connect();
  }

  ref.onDispose(() => sse.dispose());
  return sse;
});

/// Stream provider for live SseEvent stream
final sseEventsProvider = StreamProvider<SseEvent>((ref) {
  final sseService = ref.watch(sseServiceProvider);
  return sseService.eventStream;
});
