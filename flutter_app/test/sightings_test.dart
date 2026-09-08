import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_app/config/constants.dart';
import 'package:flutter_app/config/theme.dart';
import 'package:flutter_app/models/enums.dart';
import 'package:flutter_app/models/notification_model.dart';
import 'package:flutter_app/models/sighting_model.dart';
import 'package:flutter_app/providers/notifications_provider.dart';
import 'package:flutter_app/providers/sightings_provider.dart';
import 'package:flutter_app/services/sse_service.dart';
import 'package:flutter_app/widgets/sighting_card.dart';
import 'package:flutter_app/widgets/similarity_gauge.dart';
import 'package:flutter_app/widgets/timeline_widget.dart';

void main() {
  group('Story 12 Models & URL Resolvers Test', () {
    test('SightingModel resolves relative evidence media URLs to AppConstants.uploadsBaseUrl', () {
      final sighting = SightingModel(
        id: 'sight-1',
        personId: 'pers-1',
        agentId: 'agent-1',
        cameraId: 'cam-1',
        similarityScore: 0.874,
        confidenceLevel: ConfidenceLevel.confirmed,
        faceCropPath: 'evidence/crops/crop_01.jpg',
        fullFramePath: '/evidence/frames/frame_01.jpg',
        videoClipPath: 'evidence/clips/clip_01.mp4',
        cameraLocation: 'Gate 4 North',
        latitude: 28.5355,
        longitude: 77.3910,
        detectedAt: DateTime(2026, 9, 6, 14, 30),
        status: SightingStatus.confirmed,
      );

      expect(
        sighting.faceCropUrl,
        equals('${AppConstants.uploadsBaseUrl}/evidence/crops/crop_01.jpg'),
      );
      expect(
        sighting.fullFrameUrl,
        equals('${AppConstants.uploadsBaseUrl}/evidence/frames/frame_01.jpg'),
      );
      expect(
        sighting.videoClipUrl,
        equals('${AppConstants.uploadsBaseUrl}/evidence/clips/clip_01.mp4'),
      );
      expect(sighting.similarityPercentage, equals('87.4%'));
      expect(sighting.statusColor, equals(AppTheme.successGreen));
    });

    test('SightingModel preserves absolute HTTP/HTTPS media URLs', () {
      final sighting = SightingModel(
        id: 'sight-2',
        personId: 'pers-2',
        agentId: 'agent-1',
        cameraId: 'cam-2',
        similarityScore: 0.65,
        faceCropPath: 'https://cdn.example.com/crops/face.jpg',
        fullFramePath: 'https://cdn.example.com/frames/full.jpg',
        detectedAt: DateTime.now(),
      );

      expect(sighting.faceCropUrl, equals('https://cdn.example.com/crops/face.jpg'));
      expect(sighting.fullFrameUrl, equals('https://cdn.example.com/frames/full.jpg'));
    });

    test('PersonTimelineModel and TimelineEntryModel parse JSON correctly', () {
      final json = {
        'person_id': 'person-100',
        'person_name': 'Aarav Sharma',
        'entries': [
          {
            'sighting_id': 's1',
            'camera_name': 'Metro Cam 1',
            'camera_location': 'Sector 18 Gate 1',
            'latitude': 28.5700,
            'longitude': 77.3200,
            'detected_at': '2026-09-06T10:00:00.000Z',
            'similarity_score': 0.78,
            'status': 'CONFIRMED',
          },
          {
            'sighting_id': 's2',
            'camera_name': 'Metro Cam 2',
            'camera_location': 'Sector 18 Platform',
            'latitude': 28.5710,
            'longitude': 77.3210,
            'detected_at': '2026-09-06T10:05:00.000Z',
            'similarity_score': 0.85,
            'status': 'CONFIRMED',
          }
        ]
      };

      final timeline = PersonTimelineModel.fromJson(json);
      expect(timeline.personId, equals('person-100'));
      expect(timeline.personName, equals('Aarav Sharma'));
      expect(timeline.entries.length, equals(2));
      expect(timeline.entries[0].sightingId, equals('s1'));
      expect(timeline.entries[0].latitude, equals(28.5700));
      expect(timeline.entries[1].similarityScore, equals(0.85));
      expect(timeline.entries[1].status, equals(SightingStatus.confirmed));
    });
  });

  group('SSE & FCM Payload Parsing Tests', () {
    test('SseEvent parses wire format and extracts sighting attributes', () {
      const rawData = '{"event":"sighting","notification_id":"notif-1","sighting_id":"sight-99","title":"Match Spotted","body":"Detected at Gate 2","similarity_score":0.825}';
      final sseEvent = SseEvent.fromRaw(
        eventName: 'sighting',
        rawData: rawData,
        id: 'msg-1',
      );

      expect(sseEvent.event, equals('sighting'));
      expect(sseEvent.sightingId, equals('sight-99'));
      expect(sseEvent.notificationId, equals('notif-1'));
      expect(sseEvent.title, equals('Match Spotted'));
      expect(sseEvent.body, equals('Detected at Gate 2'));
      expect(sseEvent.similarityScore, equals(0.825));
    });

    test('NotificationModel converts to/from JSON with Sighting references', () {
      final notif = NotificationModel(
        id: 'notif-10',
        type: NotificationType.sighting,
        title: 'Possible Match: Rohan',
        body: 'Spotted on CCTV Camera #4',
        sightingId: 'sight-10',
        isRead: false,
        createdAt: DateTime(2026, 9, 6, 12, 0),
      );

      final json = notif.toJson();
      expect(json['id'], equals('notif-10'));
      expect(json['type'], equals('SIGHTING'));
      expect(json['sighting_id'], equals('sight-10'));
      expect(json['is_read'], isFalse);

      final reconstructed = NotificationModel.fromJson(json);
      expect(reconstructed.id, equals('notif-10'));
      expect(reconstructed.type, equals(NotificationType.sighting));
      expect(reconstructed.sightingId, equals('sight-10'));
    });
  });

  group('SimilarityGauge Widget Tests', () {
    testWidgets('Renders similarity percentage and confidence label correctly', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SimilarityGauge(
              similarityScore: 0.845,
              size: SimilarityGaugeSize.medium,
              animate: false,
              isHindi: false,
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('84.5%'), findsOneWidget);
      expect(find.text('SIMILARITY'), findsOneWidget);
      expect(find.text('PROBABLE MATCH'), findsOneWidget);
    });

    testWidgets('Renders Hindi confidence label when isHindi is true', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SimilarityGauge(
              similarityScore: 0.92,
              size: SimilarityGaugeSize.large,
              animate: false,
              isHindi: true,
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('92.0%'), findsOneWidget);
      expect(find.text('समानता'), findsOneWidget);
      expect(find.text('उच्च मिलान'), findsOneWidget);
    });
  });

  group('SightingCard Widget Tests', () {
    testWidgets('Renders sighting metadata, similarity pill, and triggers tap', (tester) async {
      bool tapped = false;
      final sighting = SightingModel(
        id: 's-card-1',
        personId: 'p-1',
        agentId: 'a-1',
        cameraId: 'cam-north',
        similarityScore: 0.765,
        numFramesMatched: 3,
        cameraLocation: 'North Concourse Cam #2',
        latitude: 28.6139,
        longitude: 77.2090,
        detectedAt: DateTime(2026, 9, 6, 16, 45),
        status: SightingStatus.confirmed,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SightingCard(
              sighting: sighting,
              isHindi: false,
              onTap: () => tapped = true,
            ),
          ),
        ),
      );

      expect(find.text('CONFIRMED MATCH'), findsOneWidget);
      expect(find.text('76.5% Match'), findsOneWidget);
      expect(find.text('North Concourse Cam #2'), findsOneWidget);
      expect(find.text('3 frames matched'), findsOneWidget);

      await tester.tap(find.byType(SightingCard));
      expect(tapped, isTrue);
    });
  });

  group('TimelineWidget Structure Tests', () {
    testWidgets('Renders empty state when no timeline entries exist', (tester) async {
      const emptyTimeline = PersonTimelineModel(
        personId: 'p-empty',
        personName: 'Test Person',
        entries: [],
      );

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: TimelineWidget(
              timeline: emptyTimeline,
              isHindi: false,
            ),
          ),
        ),
      );

      expect(find.text('No Sighting Records Yet'), findsOneWidget);
    });

    testWidgets('Renders interactive map and summary pill when GPS entries exist', (tester) async {
      final entries = [
        TimelineEntryModel(
          sightingId: 's-1',
          cameraName: 'Gate 1',
          cameraLocation: 'Main Gate',
          latitude: 28.6139,
          longitude: 77.2090,
          detectedAt: DateTime(2026, 9, 6, 10, 0),
          similarityScore: 0.80,
          status: SightingStatus.confirmed,
        ),
        TimelineEntryModel(
          sightingId: 's-2',
          cameraName: 'Gate 2',
          cameraLocation: 'Exit Gate',
          latitude: 28.6150,
          longitude: 77.2100,
          detectedAt: DateTime(2026, 9, 6, 10, 15),
          similarityScore: 0.88,
          status: SightingStatus.confirmed,
        ),
      ];

      final timeline = PersonTimelineModel(
        personId: 'p-1',
        personName: 'Rohan Gupta',
        entries: entries,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TimelineWidget(
              timeline: timeline,
              isHindi: false,
            ),
          ),
        ),
      );

      expect(find.text('Rohan Gupta'), findsOneWidget);
      expect(find.text('2 sightings recorded • 2 GPS mapped'), findsOneWidget);
      expect(find.text('#1 Stop'), findsOneWidget);
      expect(find.text('#2 Stop'), findsOneWidget);
    });
  });

  group('NotificationsState & Filter Tests', () {
    final n1 = NotificationModel(
      id: '1',
      type: NotificationType.sighting,
      title: 'Alert 1',
      isRead: false,
    );
    final n2 = NotificationModel(
      id: '2',
      type: NotificationType.confirmation,
      title: 'Alert 2',
      isRead: true,
    );

    test('Filters unread notifications properly', () {
      final state = NotificationsState(
        notifications: [n1, n2],
        unreadCount: 1,
        unreadOnly: false,
      );

      expect(state.filteredNotifications.length, equals(2));

      final unreadOnlyState = state.copyWith(unreadOnly: true);
      expect(unreadOnlyState.filteredNotifications.length, equals(1));
      expect(unreadOnlyState.filteredNotifications.first.id, equals('1'));
    });
  });
}
